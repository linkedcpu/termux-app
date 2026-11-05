package com.termux.app.terminal;

import android.graphics.Color;
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
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.List;

/**
 * ★ 底部 Session 标签栏 + 标签抽屉（PopupWindow）
 *
 * 从 TermuxActivity 拆出，行为与原实现逐条对齐：
 *  - 每个标签 = 整条可点切换会话，右侧三点弹出抽屉
 *  - 抽屉宽 = 标签宽，底边贴标签顶边，留 DRAWER_GAP_DP 呼吸缝
 *  - INPUT_METHOD_NOT_NEEDED + SOFT_INPUT_STATE_UNCHANGED：弹菜单不收软键盘
 *  - 空列表时整条栏 GONE，不露空黑条
 *
 * 所有会改动会话状态的操作都委托回 TermuxActivity（桥接方法），
 * 本类不持有 TermuxService，保持可单测、可替换。
 */
public final class SessionTabBar {

    /** 抽屉底边与标签顶边之间的呼吸缝（dp）。0 = 无缝。 */
    private static final int DRAWER_GAP_DP = 6;
    /** 标签宽度（px）。与原实现写死的 288 保持一致，改一处即可全局生效。 */
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

    /** 绑定 + 号点击、抓取原始高度。须在 root 已布局后（onCreate 末尾）调用。 */
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

    /** 重新铺标签。空列表自动隐藏整条栏。 */
    public void render() {
        List<?> sessions = mActivity.getTermuxService() == null
                ? null : mActivity.getTermuxService().getTermuxSessions();
        boolean isEmpty = sessions == null || sessions.isEmpty();
        applyVisibility(isEmpty);
        if (isEmpty) return;

        mRoot.setBackgroundColor(0xFF141414);
        mContainer.removeAllViews();

        TerminalSession current = mActivity.getCurrentSession();
        float density = mRoot.getResources().getDisplayMetrics().density;
        int tabW = TAG_WIDTH_PX;
        int gap = (int) (0 * density);

        for (int i = 0; i < sessions.size(); i++) {
            Object obj = sessions.get(i);
            if (!(obj instanceof TermuxSession)) continue;
            TerminalSession session = ((TermuxSession) obj).getTerminalSession();
            if (session == null) continue;
            bindTag(session, i, current, tabW, gap);
        }
    }

    /** 收起抽屉（onStop / onDestroy / onBack 时调用）。 */
    public void dismissPopup() {
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
        mPopup = null;
    }

    /**
     * @return true 表示已消费（抽屉被收），TermuxActivity 应直接 return，
     *         并把焦点钉回终端，键盘保持不变。
     */
    public boolean onBackPressed() {
        if (mPopup != null && mPopup.isShowing()) {
            dismissPopup();
            mTerminalView.requestFocus();
            return true;
        }
        return false;
    }

    /** 有会话时被切到可见；空列表不强行拉出空黑条。 */
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

    /** 菜单里切换显隐。 */
    public boolean toggle() {
        mVisible = !mVisible;
        if (mVisible) render();
        else applyVisibility(false);
        mActivity.showToast("Session 栏：" + (mVisible ? "显示" : "隐藏"), false);
        return mVisible;
    }

    public boolean isVisibleNow() { return mVisible; }

    /** 生命周期终结时调用，防止泄露窗口。 */
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
            itemRoot.setBackgroundColor(0xFF1E1E1E);
            title.setTextColor(0xFFFFFFFF);
            icon.setColorFilter(0xFF4ECDC4);
            more.setColorFilter(0xFF4ECDC4);
        } else {
            line.setVisibility(View.GONE);
            itemRoot.setBackgroundColor(0xFF0A0A0A);
            title.setTextColor(0xFF999999);
            icon.setColorFilter(0xFF666666);
            more.setColorFilter(0xFF777777);
        }

        // ★ 整条标签点击 = 切换会话，若抽屉开着先收掉
        itemRoot.setOnClickListener(v -> {
            dismissPopup();
            mActivity.setCurrentSession(session);
            render();
            ensureVisible();
        });

        // ★ 三点 = 弹出等标签宽的抽屉；先把该会话切到前台
        more.setOnClickListener(v -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (current != session) {
                mActivity.setCurrentSession(session);
                render();
            }
            showDrawer(more, session);
        });

        // ★ 长按消费掉，避免与单击打架
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
        // 等标签布局完成再量宽，避免 getWidth() == 0
        parent.post(() -> showDrawerAbove(parent, session));
    }

    private void showDrawerAbove(View anchor, TerminalSession session) {
        int anchorW = anchor.getWidth();
        if (anchorW <= 0) anchorW = dp(200);

        View content = mActivity.getLayoutInflater().inflate(R.layout.popup_tab_menu, null);
        content.measure(
                View.MeasureSpec.makeMeasureSpec(anchorW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupW = anchorW;
        int popupH = content.getMeasuredHeight();

        mPopup = new PopupWindow(content, popupW, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        // ★ 背景必须透明：圆角 + 阴影都在 bg_popup_menu 里画，
        //   塞实色会把圆角裁直角、并叠出双影。
        mPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mPopup.setOutsideTouchable(true);
        mPopup.setAnimationStyle(0); // 无动画 → 像标签栏原地展开，不“砸”下来

        // ★★★ 压住键盘的核心：窗口不碰输入法、键盘状态不被切换
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);

        mPopup.setOnDismissListener(() -> mPopup = null);

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int gap = dp(DRAWER_GAP_DP);
        int y = loc[1] - popupH - gap;
        if (y < statusBarHeight()) {
            // 上方顶出屏幕 → 翻到标签下方，缝留在底边之下，同样呼吸感
            y = loc[1] + anchor.getHeight() + gap;
        }
        mPopup.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0], y);

        /* ---------- 各项点击：先收抽屉，焦点钉回终端，键盘不闪 ---------- */
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
            // 关会话是异步收尾，post 回去把焦点还给终端，键盘不闪
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

    /* ================= 重命名（纯 UI，改完回调 render） ================= */

    private void rename(TerminalSession session) {
        if (session == null) return;
        EditText input = new EditText(mActivity);
        input.setText(session.mSessionName);
        input.setSingleLine(true);
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

    /* ================= 工具 ================= */

    private int dp(int v) {
        return Math.round(v * mRoot.getResources().getDisplayMetrics().density);
    }

    private int statusBarHeight() {
        int resId = mRoot.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? mRoot.getResources().getDimensionPixelSize(resId) : dp(24);
    }
}
