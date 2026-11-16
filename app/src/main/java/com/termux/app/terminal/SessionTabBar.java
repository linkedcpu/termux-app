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
 * 配色约定：背景色用 ThemeManager.current().bgColor，
 *          所有前景色（文字/图标/描边）一律用 ThemeManager.foregroundTheme()，
 *          绕开壁纸模式下被强制改成白色的 textPrimary。
 */
public final class SessionTabBar {

    private static final int DRAWER_GAP_DP = 6;
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

        mRoot.setBackgroundColor(barBg());
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
            itemRoot.setBackgroundColor(tabActive());
            title.setTextColor(titleActive());
            icon.setColorFilter(accent());
            more.setColorFilter(accent());
        } else {
            line.setVisibility(View.GONE);
            itemRoot.setBackgroundColor(tabNormal());
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

        GradientDrawable pd = new GradientDrawable();
        pd.setShape(GradientDrawable.RECTANGLE);
        pd.setCornerRadius(dp(12));
        pd.setColor(drawerBg());
        pd.setStroke(dp(1), withAlpha(drawerTextSecondaryColor(), 0x30));
        content.setBackground(pd);

        applyDrawerTextColors(content);

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
            divider.setBackgroundColor(withAlpha(secondary, 0x33));
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

    /* ================= 调色 ================= */

    private ThemeSpec theme() { return ThemeManager.current(mActivity); }

    /** ★ 前景色统一走 foregroundTheme，绕开壁纸模式下的强制白字 */
    private ThemeSpec baseTheme() { return ThemeManager.foregroundTheme(mActivity); }

    private int barBg()      { return shade(theme().bgColor, -0x10); }
    private int tabActive()  { return shade(theme().bgColor, +0x14); }
    private int tabNormal()  { return shade(theme().bgColor, -0x22); }

    private int titleActive()  { return baseTheme().textPrimary; }
    private int titleNormal()  { return baseTheme().textSecondary; }
    private int accent()       { return baseTheme().accent; }
    private int iconNormal()   { return withAlpha(baseTheme().textSecondary, 0x90); }

    private int drawerBg() {
        if (ThemeManager.hasWallpaper(mActivity)) {
            return theme().useLightTextOnWallpaper()
                    ? blend(0xFF141414, theme().bgColor)
                    : blend(0xFFF7F7F5, theme().bgColor);
        }
        return barBg();
    }

    private int drawerTextColor() {
        if (ThemeManager.hasWallpaper(mActivity)) {
            return theme().useLightTextOnWallpaper() ? 0xFFFFFFFF : baseTheme().textPrimary;
        }
        return baseTheme().textPrimary;
    }

    private int drawerTextSecondaryColor() {
        if (ThemeManager.hasWallpaper(mActivity)) {
            return theme().useLightTextOnWallpaper() ? 0xCCFFFFFF : baseTheme().textSecondary;
        }
        return baseTheme().textSecondary;
    }

    private int shade(int base, int delta) {
        int c = (base == 0) ? 0xFF141414 : base;
        if (Color.alpha(c) != 0xFF) c = blend(0xFF141414, c);
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
