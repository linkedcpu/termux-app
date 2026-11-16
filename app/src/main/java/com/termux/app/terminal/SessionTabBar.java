package com.termux.app.terminal;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.List;

/**
 * ★ 底部 Session 标签栏 + 标签抽屉（PopupWindow）
 *
 * ★ 外观口径与 DrawerPanel / SnippetManager 完全一致：只调 Theme 语义入口，
 *   不判壁纸、不判亮度、不混色、不写死色值与圆角。
 *     颜色 → theme().color(角色)
 *     形状 → theme().surface() / button() / radius() / stroke() / pillButtons()
 */
public final class SessionTabBar {

    private static final int DRAWER_GAP_DP = 6;
    private static final float RADIUS_TAB_DP = 10f;
    private static final float RADIUS_POPUP_DP = 12f;
    static final int TAG_WIDTH_PX = 288;

    private final TermuxActivity mActivity;
    private final TerminalView mTerminalView;
    private final View mRoot;
    private final LinearLayout mContainer;
    private final TextView mAddButton;

    private boolean mVisible = true;
    private int mOriginalHeight = 0;
    private PopupWindow mPopup;

    public SessionTabBar(TermuxActivity activity,
                         TerminalView terminalView,
                         View root,
                         LinearLayout container,
                         TextView addButton) {
        mActivity = activity;
        mTerminalView = terminalView;
        mRoot = root;
        mContainer = container;
        mAddButton = addButton;
    }

    /* ================= 公开 API ================= */

    public void init() {
        mRoot.post(this::captureOriginalHeight);

        mAddButton.setOnClickListener(v -> {
            ensureVisible();
            mActivity.addNewSession(false, null);
        });
        mAddButton.setOnLongClickListener(v -> {
            ensureVisible();
            mActivity.showNamedSessionDialog(false);
            return true;
        });
    }

    public void render() {
        List<?> sessions = sessionList();
        boolean isEmpty = sessions == null || sessions.isEmpty();
        applyVisibility(isEmpty);
        if (isEmpty) return;

        mRoot.setBackground(theme().surface(mActivity, RADIUS_TAB_DP));
        mContainer.removeAllViews();

        TerminalSession current = mActivity.getCurrentSession();
        int gap = 4;
        int tabW = TAG_WIDTH_PX-gap;

        for (int i = 0; i < sessions.size(); i++) {
            Object obj = sessions.get(i);
            if (!(obj instanceof TermuxSession)) continue;
            TerminalSession session = ((TermuxSession) obj).getTerminalSession();
            if (session == null) continue;
            bindTag(session, i, current, tabW, gap);
        }
    }

    public void dismissPopup() {
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
        mPopup = null;
    }

    public boolean onBackPressed() {
        if (mPopup != null && mPopup.isShowing()) {
            dismissPopup();
            mTerminalView.requestFocus();
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

    /* ================= 内部工具 ================= */

    private List<?> sessionList() {
        return mActivity.getTermuxService() == null
                ? null : mActivity.getTermuxService().getTermuxSessions();
    }

    /* ================= 标签项 ================= */

    private void bindTag(TerminalSession session,
                         int index,
                         TerminalSession current,
                         int tabW,
                         int gap) {

        View tab = mActivity.getLayoutInflater().inflate(R.layout.item_session_tab, mContainer, false);
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

        more.setOnClickListener(v -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (current != session) {
                mActivity.setCurrentSession(session);
                render();
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
        int popupW = anchorW;

        mPopup = new PopupWindow(content, popupW, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        mPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        mPopup.setOutsideTouchable(true);
        mPopup.setAnimationStyle(0);
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        mPopup.setOnDismissListener(() -> mPopup = null);

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int gap = dp(DRAWER_GAP_DP);
        int y = loc[1] - content.getMeasuredHeight() - gap;
        if (y < statusBarHeight()) {
            y = loc[1] + anchor.getHeight() + gap;
        }
        mPopup.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0], y);

        content.findViewById(R.id.item_rename).setOnClickListener(v -> {
            dismissPopup(); mTerminalView.requestFocus(); rename(session);
        });
        content.findViewById(R.id.item_reset).setOnClickListener(v -> {
            dismissPopup(); mTerminalView.requestFocus(); mActivity.resetSession(session);
        });

        View kill = content.findViewById(R.id.item_kill);
        kill.setEnabled(session.isRunning());
        kill.setAlpha(session.isRunning() ? 1f : 0.4f);
        kill.setOnClickListener(v -> {
            if (!session.isRunning()) return;
            dismissPopup();
            mTerminalView.requestFocus();
            mActivity.confirmKill(session);
        });

        content.findViewById(R.id.item_close).setOnClickListener(v -> {
            dismissPopup();
            mTerminalView.post(() -> {
                mTerminalView.requestFocus();
                mActivity.closeSession(session);
            });
        });
    }

    /** 把抽屉内所有文字/分隔线染成主题色，覆盖布局与样式里写死的颜色 */
    private void applyDrawerTextColors(View content) {
        if (content == null) return;
        int primary = drawerTextColor();
        int secondary = drawerTextSecondaryColor();

        int[] textIds = {R.id.item_rename, R.id.item_reset, R.id.item_kill, R.id.item_close};
        for (int id : textIds) {
            View v = content.findViewById(id);
            if (v instanceof TextView) {
                ((TextView) v).setTextColor(primary);
            }
        }

        View divider = content.findViewById(R.id.tab_popup_divider);
        if (divider != null) {
            divider.setBackgroundColor(secondary);
        }

        if (content instanceof ViewGroup) {
            tintRemainingTexts((ViewGroup) content, primary);
        }
    }

    private void tintRemainingTexts(ViewGroup group, int color) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(color);
            } else if (child instanceof ViewGroup) {
                tintRemainingTexts((ViewGroup) child, color);
            }
        }
    }

    /* ================= 显隐 / 尺寸 ================= */

    private void captureOriginalHeight() {
        int h = mRoot.getHeight();
        if (h <= 0) h = (int) (40 * mRoot.getResources().getDisplayMetrics().density);
        mOriginalHeight = h;
        List<?> sessions = sessionList();
        boolean isEmpty = sessions == null || sessions.isEmpty();
        applyVisibility(isEmpty);
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

    /* ================= 调色（全部委托 Theme，零推导） ================= */

    private Theme theme() { return ThemeStore.get(mActivity); }

    private int titleActive()  { return theme().color(Theme.TEXT); }
    private int titleNormal()  { return theme().color(Theme.TEXT_SEC); }
    private int accent()       { return theme().color(Theme.ACCENT); }
    private int iconNormal()   { return theme().color(Theme.TEXT_SEC); }

    private int drawerTextColor() {
        return theme().color(Theme.TEXT);
    }

    private int drawerTextSecondaryColor() {
        return theme().color(Theme.DIVIDER);
    }

    /** 抽屉描边：沿用 Theme 的 OUTLINE 角色，不再自算 alpha。 */
    private int drawerStrokeColor() {
        return theme().color(Theme.OUTLINE);
    }

    /* ================= 工具 ================= */

    private int dp(int v) {
        return Math.round(v * mRoot.getResources().getDisplayMetrics().density);
    }

    private int statusBarHeight() {
        int resId = mRoot.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? mRoot.getResources().getDimensionPixelSize(resId) : dp(24);
    }
}
