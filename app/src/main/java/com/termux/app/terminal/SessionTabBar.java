package com.termux.app.terminal;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.List;

/**
 * ★ 底部 Session 标签栏 + 标签抽屉（PopupWindow）
 *
 * 配色跟随 ThemeManager 的界面主题，不再写死十六进制。
 * 行为与原实现一致：整条可点切换、三点弹抽屉、空列表整栏 GONE。
 */
public final class SessionTabBar {

    /** 抽屉底边与标签顶边之间的呼吸缝（dp）。0 = 无缝。 */
    private static final int DRAWER_GAP_DP = 6;
    /** 标签宽度（px）。 */
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
        List<?> sessions = mActivity.getTermuxService() == null
                ? null : mActivity.getTermuxService().getTermuxSessions();
        boolean isEmpty = sessions == null || sessions.isEmpty();
        applyVisibility(isEmpty);
        if (isEmpty) return;

        mRoot.setBackgroundColor(barBg());          // ★ 跟随主题
        mContainer.removeAllViews();

        TerminalSession current = mActivity.getCurrentSession();
        int tabW = TAG_WIDTH_PX;
        int gap = 0;

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
        List<?> sessions = mActivity.getTermuxService() == null
                ? null : mActivity.getTermuxService().getTermuxSessions();
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
            line.setBackgroundColor(accent());            // ★ 顶边线用强调色
            itemRoot.setBackgroundColor(tabActive());     // ★
            title.setTextColor(titleActive());           // ★
            icon.setColorFilter(accent());               // ★
            more.setColorFilter(accent());               // ★
        } else {
            line.setVisibility(View.GONE);
            itemRoot.setBackgroundColor(tabNormal());    // ★
            title.setTextColor(titleNormal());           // ★
            icon.setColorFilter(iconNormal());           // ★
            more.setColorFilter(iconNormal());           // ★
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

        // ★ 抽屉背景跟随主题：圆角 + 细描边
        GradientDrawable pd = new GradientDrawable();
        pd.setShape(GradientDrawable.RECTANGLE);
        pd.setCornerRadius(dp(12));
        pd.setColor(barBg());
        pd.setStroke(dp(1), withAlpha(theme().textSecondary, 0x30));
        content.setBackground(pd);

        content.measure(
                View.MeasureSpec.makeMeasureSpec(anchorW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupW = anchorW;

        mPopup = new PopupWindow(content, popupW, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        mPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
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

    /* ================= 显隐 / 尺寸 ================= */

    private void captureOriginalHeight() {
        int h = mRoot.getHeight();
        if (h <= 0) h = (int) (40 * mRoot.getResources().getDisplayMetrics().density);
        mOriginalHeight = h;
        boolean isEmpty = mActivity.getTermuxService() == null
                || mActivity.getTermuxService().getTermuxSessions().isEmpty();
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
        input.setTextColor(titleActive());                       // ★
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

    /* ================= 调色：跟随界面主题 ================= */

    private ThemeSpec theme() { return ThemeManager.current(mActivity); }

    /** 栏底：比面板底色再深一档，和抽屉拉开层次 */
    private int barBg()      { return shade(theme().bgColor, -0x10); }
    /** 选中标签：面板底色提亮 */
    private int tabActive()  { return shade(theme().bgColor, +0x14); }
    /** 未选中标签：面板底色压暗 */
    private int tabNormal()  { return shade(theme().bgColor, -0x22); }
    private int titleActive()  { return theme().textPrimary; }
    private int titleNormal()  { return theme().textSecondary; }
    private int accent()       { return theme().accent; }
    private int iconNormal()   { return withAlpha(theme().textSecondary, 0x90); }

    private int shade(int base, int delta) {
        int c = (base == 0) ? 0xFF141414 : base;
        if (Color.alpha(c) != 0xFF) c = blend(0xFF141414, c);   // 半透明主题垫底
        return Color.argb(0xFF,
                clamp(Color.red(c)   + delta),
                clamp(Color.green(c) + delta),
                clamp(Color.blue(c)  + delta));
    }

    private int blend(int back, int front) {
        float a = Color.alpha(front) / 255f;
        return Color.argb(0xFF,
                (int) (Color.red(back)   * (1 - a) + Color.red(front)   * a),
                (int) (Color.green(back) * (1 - a) + Color.green(front) * a),
                (int) (Color.blue(back)  * (1 - a) + Color.blue(front)  * a));
    }

    private int withAlpha(int c, int alpha) {
        return Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c));
    }

    private int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /* ================= 工具 ================= */

    private int dp(int v) {
        return Math.round(v * mRoot.getResources().getDisplayMetrics().density);
    }

    private int statusBarHeight() {
        int resId = mRoot.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? mRoot.getResources().getDimensionPixelSize(resId) : dp(24);
    }
}
