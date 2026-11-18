package com.termux.app.terminal;

import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.theme.Theme;
import com.termux.app.terminal.theme.ThemeStore;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.List;

public final class SessionTabBar {

    private static final int DRAWER_GAP_DP = 6;
    private static final float RADIUS_TAB_DP = 10f;
    private static final float RADIUS_POPUP_DP = 12f;
    static final int TAG_WIDTH_PX = 288;

    private static final String LOG_TAG = "SessionTabBar";

    /** ★ 抽屉 Tab 常量，与 TermuxActivity 对齐 */
    public static final String TAB_SNIPPET = "snippet";
    public static final String TAB_MORE = "more";

    private final TermuxActivity mActivity;
    private final TerminalView mTerminalView;
    private final View mRoot;
    private final LinearLayout mContainer;
    private final LinearLayout mActionGroup;

    private boolean mVisible = true;
    private int mOriginalHeight = 0;
    private PopupWindow mPopup;

    private ImageView mBtnAdd;
    private ImageView mBtnSnippet;
    private ImageView mBtnMenu;

    public SessionTabBar(TermuxActivity activity,
                         TerminalView terminalView,
                         View root,
                         LinearLayout container,
                         LinearLayout actionGroup) {
        mActivity = activity;
        mTerminalView = terminalView;
        mRoot = root;
        mContainer = container;
        mActionGroup = actionGroup;
    }

    /* ================= 公开 API ================= */

    public void init() {
        mRoot.post(this::captureOriginalHeight);
        installActionButtons();
        syncHighlights();
    }

    public void render() {
        List<?> sessions = sessionList();
        boolean isEmpty = sessions == null || sessions.isEmpty();
        applyVisibility(isEmpty);
        if (isEmpty) {
            syncHighlights();
            return;
        }

        mRoot.setBackground(theme().surface(mActivity, RADIUS_TAB_DP));
        mContainer.removeAllViews();
        syncHighlights();

        TerminalSession current = mActivity.getCurrentSession();
        int gap = 4;
        int tabW = TAG_WIDTH_PX - gap;

        for (int i = 0; i < sessions.size(); i++) {
            Object obj = sessions.get(i);
            if (!(obj instanceof TermuxSession)) continue;
            TerminalSession session = ((TermuxSession) obj).getTerminalSession();
            if (session == null) continue;
            bindTag(session, i, current, tabW, gap);
        }
    }

    /**
     * ★ 供 Activity 在抽屉开合 / Tab 切换 / 换肤时调用。
     *    ☆ 与 ≡ 逻辑完全合并：只看抽屉是否开着且停在该 Tab，不再校验收藏面板展开。
     */
    public void syncHighlights() {
        if (mBtnSnippet == null || mBtnMenu == null) return;
        tint(mBtnSnippet, mActivity.isDrawerOnTabPublic(TAB_SNIPPET));
        tint(mBtnMenu, mActivity.isDrawerOnTabPublic(TAB_MORE));
        tint(mBtnAdd, false);
    }

    public void dismissPopup() {
        if (mPopup != null && mPopup.isShowing()) mPopup.dismiss();
        mPopup = null;
    }

    public boolean onBackPressed() {
        if (mPopup != null && mPopup.isShowing()) {
            dismissPopup();
            return true;
        }
        return false;
    }

    public void ensureVisible() {
        List<?> sessions = sessionList();
        if (sessions == null || sessions.isEmpty()) return;
        if (!mVisible) {
            mVisible = true;
            applyVisibility(false);
            render();
        }
    }

    public boolean toggle() {
        mVisible = !mVisible;
        if (mVisible) render();
        else applyVisibility(false);
        mActivity.showToast("Session 栏：" + (mVisible ? "显示" : "隐藏"), false);
        return mVisible;
    }

    public boolean isVisibleNow() { return mVisible; }

    public void detach() { dismissPopup(); }

    /* ================= ★ 三个按钮：从 session_action_bar.xml 取值绑定 ================= */

    private void installActionButtons() {
        if (mActionGroup == null) {
            Logger.logWarn(LOG_TAG, "action_group 未找到，＋/☆/≡ 不安装");
            return;
        }
        mBtnAdd = mActionGroup.findViewById(R.id.tab_add_button);
        mBtnSnippet = mActionGroup.findViewById(R.id.snippet_button);
        mBtnMenu = mActionGroup.findViewById(R.id.drawer_menu_button);

        if (mBtnAdd == null || mBtnSnippet == null || mBtnMenu == null) {
            Logger.logWarn(LOG_TAG, "＋/☆/≡ 有缺失，跳过绑定");
            return;
        }

        mBtnAdd.setOnClickListener(v -> {
            ensureVisible();
            mActivity.addNewSession(false, null);
        });
        mBtnAdd.setOnLongClickListener(v -> {
            mActivity.showNamedSessionDialog(false);
            return true;
        });
        mBtnSnippet.setOnClickListener(v -> {
            mActivity.openDrawerTabPublic(TAB_SNIPPET);
            syncHighlights();
        });
        mBtnMenu.setOnClickListener(v -> {
            mActivity.openDrawerTabPublic(TAB_MORE);
            syncHighlights();
        });
    }

    private void tint(ImageView iv, boolean active) {
        if (iv == null) return;
        iv.setColorFilter(active ? accent() : iconNormal());
        iv.invalidate();
    }

    /* ================= 内部工具 ================= */

    private List<?> sessionList() {
        return mActivity.getTermuxService() == null
                ? null : mActivity.getTermuxService().getTermuxSessions();
    }

    /* ================= 标签项 ================= */

    private void bindTag(TerminalSession session, int index,
                         TerminalSession current, int tabW, int gap) {
        View tab = mActivity.getLayoutInflater()
                .inflate(R.layout.item_session_tab, mContainer, false);
        View itemRoot = tab.findViewById(R.id.tab_item_root);
        View line = tab.findViewById(R.id.tab_top_line);
        ImageView icon = tab.findViewById(R.id.tab_icon);
        TextView title = tab.findViewById(R.id.tab_title);
        ImageView more = tab.findViewById(R.id.tab_more);

        String name = session.mSessionName;
        title.setText(name != null && !name.trim().isEmpty() ? name.trim() : "Session " + (index + 1));

        boolean isActive = current != null && current == session;
        if (isActive) {
            line.setVisibility(View.VISIBLE);
            line.setBackgroundColor(accent());
            itemRoot.setBackground(theme().button(mActivity, RADIUS_TAB_DP));
            itemRoot.setActivated(true);
            title.setTextColor(titleActive());
            icon.setColorFilter(accent());
            more.setColorFilter(accent());
        } else {
            line.setVisibility(View.GONE);
            itemRoot.setBackground(theme().button(mActivity, RADIUS_TAB_DP));
            itemRoot.setActivated(false);
            title.setTextColor(titleNormal());
            icon.setColorFilter(iconNormal());
            more.setColorFilter(iconNormal());
        }

        itemRoot.setOnClickListener(v -> {
            dismissPopup();
            mActivity.setCurrentSession(session);
            render();
            ensureVisible();
        });

        // ★ 点击 more：只切会话，不 render()，避免重建标签栏导致键盘/扩展键栏收起
        more.setOnClickListener(v -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (current != session) {
                mActivity.setCurrentSession(session);
            }
            showDrawer(more, session);
        });

        itemRoot.setOnLongClickListener(v -> true);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(tabW, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(gap, 0, 0, 0);
        tab.setLayoutParams(lp);
        mContainer.addView(tab, lp);
    }

    /* ================= 标签抽屉 ================= */

    private void showDrawer(ImageView anchor, TerminalSession session) {
        if (session == null) return;
        View parent = (View) anchor.getParent();
        if (parent == null) return;
        dismissPopup();
        parent.post(() -> showDrawerAbove(parent, session));
    }

    private void showDrawerAbove(View anchor, TerminalSession session) {
        int anchorW = anchor.getWidth();
        if (anchorW <= 0) anchorW = dp(200);

        View content = mActivity.getLayoutInflater().inflate(R.layout.popup_tab_menu, null);
        content.setBackground(theme().surface(mActivity, RADIUS_POPUP_DP));
        applyDrawerTextColors(content);

        content.measure(
                View.MeasureSpec.makeMeasureSpec(anchorW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        mPopup = new PopupWindow(content, anchorW,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        // ★ 不抢焦点，避免点击 more 后输入法 / 扩展键栏收起
        mPopup.setFocusable(false);
        mPopup.setTouchable(true);
        mPopup.setOutsideTouchable(true);

        mPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        mPopup.setAnimationStyle(0);

        // ★ 保持当前输入法状态
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);

        mPopup.setOnDismissListener(() -> mPopup = null);

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int gap = dp(DRAWER_GAP_DP);
        int y = loc[1] - content.getMeasuredHeight() - gap;
        if (y < statusBarHeight()) y = loc[1] + anchor.getHeight() + gap;
        mPopup.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0], y);

        // ★ 菜单点击不再 requestFocus()，避免收键盘
        content.findViewById(R.id.item_rename).setOnClickListener(v -> {
            dismissPopup();
            rename(session);
        });
        content.findViewById(R.id.item_reset).setOnClickListener(v -> {
            dismissPopup();
            mActivity.resetSession(session);
        });
        View kill = content.findViewById(R.id.item_kill);
        kill.setEnabled(session.isRunning());
        kill.setAlpha(session.isRunning() ? 1f : 0.4f);
        kill.setOnClickListener(v -> {
            if (!session.isRunning()) return;
            dismissPopup();
            mActivity.confirmKill(session);
        });
        content.findViewById(R.id.item_close).setOnClickListener(v -> {
            dismissPopup();
            mTerminalView.post(() -> mActivity.closeSession(session));
        });
    }

    private void applyDrawerTextColors(View content) {
        if (content == null) return;
        int primary = drawerTextColor();
        int secondary = drawerTextSecondaryColor();
        int[] textIds = {R.id.item_rename, R.id.item_reset, R.id.item_kill, R.id.item_close};
        for (int id : textIds) {
            View v = content.findViewById(id);
            if (v instanceof TextView) ((TextView) v).setTextColor(primary);
        }
        View divider = content.findViewById(R.id.tab_popup_divider);
        if (divider != null) divider.setBackgroundColor(secondary);
        if (content instanceof ViewGroup) tintRemainingTexts((ViewGroup) content, primary);
    }

    private void tintRemainingTexts(ViewGroup group, int color) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextColor(color);
            else if (child instanceof ViewGroup) tintRemainingTexts((ViewGroup) child, color);
        }
    }

    /* ================= 显隐 / 尺寸 ================= */

    private void captureOriginalHeight() {
        int h = mRoot.getHeight();
        if (h <= 0) h = (int) (40 * mRoot.getResources().getDisplayMetrics().density);
        mOriginalHeight = h;
        List<?> sessions = sessionList();
        applyVisibility(sessions == null || sessions.isEmpty());
    }

    private void applyVisibility(boolean isEmpty) {
        mRoot.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (mOriginalHeight == 0) return;
        ViewGroup.LayoutParams lp = mRoot.getLayoutParams();
        lp.height = mVisible ? mOriginalHeight : 0;
        mRoot.setLayoutParams(lp);
    }

    /* ================= 重命名 ================= */

    private void rename(TerminalSession session) {
        if (session == null) return;
        EditText input = new EditText(mActivity);
        input.setText(session.mSessionName);
        input.setSingleLine(true);
        input.setTextColor(titleActive());
        new AlertDialog.Builder(mActivity)
                .setTitle("重命名会话")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        session.mSessionName = name;
                        render();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /* ================= 调色（全部委托 Theme） ================= */

    private Theme theme() { return ThemeStore.get(mActivity); }
    private int titleActive() { return theme().color(Theme.TEXT); }
    private int titleNormal() { return theme().color(Theme.TEXT_SEC); }
    private int accent() { return theme().color(Theme.ACCENT); }
    private int iconNormal() { return theme().color(Theme.TEXT_SEC); }
    private int drawerTextColor() { return theme().color(Theme.TEXT); }
    private int drawerTextSecondaryColor() { return theme().color(Theme.DIVIDER); }

    /* ================= 工具 ================= */

    private int dp(int v) {
        return Math.round(v * mRoot.getResources().getDisplayMetrics().density);
    }

    private int statusBarHeight() {
        int resId = mRoot.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? mRoot.getResources().getDimensionPixelSize(resId) : dp(24);
    }
}
