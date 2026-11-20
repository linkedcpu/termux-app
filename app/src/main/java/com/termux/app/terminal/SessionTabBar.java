package com.termux.app.terminal;

import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.theme.Theme;
import com.termux.app.terminal.theme.ThemeMotion;
import com.termux.app.terminal.theme.ThemeMotionPlayer;
import com.termux.app.terminal.theme.ThemeStore;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.List;

/**
 * ★ Session 栏：会话标签 + ＋/☆/≡ 三个动作按钮。
 *
 * <p>三个按钮<b>完全等价</b>：长按弹出<b>同一份</b>菜单（新建会话 / 抽屉页 / 更多功能），
 * 可从同一张表里任选一项绑定；图标、单击行为、高亮规则、动效、持久化全部同源。
 * 唯一差别是<b>出厂默认绑定</b>不同：＋ 新建会话、☆ 收藏夹、≡ 更多——否则三个键无从区分。
 *
 * <p>★★ 核心不变式：<b>槽位永不为 null</b>，默认态也存真实对象。
 * 图标（{@link #applyShortcutIcon}）与行为（{@link #runSlot}）都只从槽位对象推导，
 * 不存在「null 走另一条分支」的双路径。
 *
 * <p>★★ 显隐不变式：<b>内存 mVisible 与磁盘 KEY_VISIBLE 永远同值</b>。
 * 所有写入只经 {@link #setVisibleNow}，{@link #ensureVisible} 只读不写、且内部一致性校正不落盘，
 * 避免「回前台把磁盘状态带偏」导致的重启后自动展开。
 */
public final class SessionTabBar {

    private static final int DRAWER_GAP_DP = 6;
    static final int TAG_WIDTH_PX = 288;

    private static final String LOG_TAG = "SessionTabBar";
    private static final String PREFS_NAME = "session_tab_bar";
    private static final String KEY_ADD_ID = "add_shortcut_id";
    private static final String KEY_SNIPPET_ID = "snippet_shortcut_id";
    private static final String KEY_MENU_ID = "menu_shortcut_id";
    /** ★ Session 栏显隐持久化：默认展开，与键栏 shouldShowTerminalToolbar 同构 */
    private static final String KEY_VISIBLE = "session_tab_bar_visible";

    /** ★ 抽屉 Tab 常量，与 DrawerPanel.tabs() 的 key 对齐 */
    public static final String TAB_SNIPPET = "snippet";
    public static final String TAB_MORE = "more";
    public static final String TAB_THEME = "theme";
    public static final String TAB_ABOUT = "about";

    /** 槽位目标：抽屉 Tab（tab:*）或内置动作（act:*） */
    private static final class Shortcut {
        final String id;
        final String label;
        final int icon;

        Shortcut(String id, String label, int icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }
    }

    /** 长按选择器里的一个可选项 */
    private static final class ActionDef {
        final Shortcut shortcut;
        /** 分组标题，null 表示不显示分组 */
        final String section;
        /** 需要当前会话才可执行 */
        final boolean needSession;
        final int defIcon;

        ActionDef(String section, Shortcut s, boolean needSession, int defIcon) {
            this.section = section;
            this.shortcut = s;
            this.needSession = needSession;
            this.defIcon = defIcon;
        }

        /** ★ 3 参工厂：defIcon 传 0，图标统一由 applyShortcutIcon() 按 id 解析 */
        static ActionDef of(String section, Shortcut s, boolean needSession) {
            return new ActionDef(section, s, needSession, 0);
        }
    }

    /* ===================== 三张表：合成同一份菜单 ===================== */

    private static final Shortcut S_NEW = new Shortcut("act:new_session", "新建会话", R.drawable.ic_add);
    private static final Shortcut S_FAILSAFE = new Shortcut("act:new_failsafe", "新建会话（安全模式）", R.drawable.ic_add);
    private static final Shortcut S_NAMED = new Shortcut("act:new_named", "新建命名会话", R.drawable.ic_add);

    private static final Shortcut[] TAB_SHORTCUTS = {
            new Shortcut("tab:" + TAB_SNIPPET, "收藏夹", R.drawable.ic_drawer_snippet),
            new Shortcut("tab:" + TAB_MORE,    "更多",   R.drawable.ic_drawer_more),
            new Shortcut("tab:" + TAB_THEME,   "主题",   R.drawable.ic_drawer_theme),
            new Shortcut("tab:" + TAB_ABOUT,   "关于",   R.drawable.ic_drawer_about),
    };

    private static final ActionDef[] NEW_ACTIONS = {
            new ActionDef("新建会话", S_NEW, false, R.drawable.ic_add),
            new ActionDef(null,       S_FAILSAFE, false, R.drawable.ic_add),
            new ActionDef(null,       S_NAMED, false, R.drawable.ic_add),
    };

    private static final ActionDef[] MORE_ACTIONS = {
            ActionDef.of("更多功能", new Shortcut("act:select_url",  "选择 URL",  R.drawable.ic_drawer_more),    true),
            ActionDef.of(null,       new Shortcut("act:share",       "分享转写",  R.drawable.ic_drawer_more),    true),
            ActionDef.of(null,       new Shortcut("act:reset",       "重置终端",  R.drawable.ic_drawer_more),    true),
            ActionDef.of(null,       new Shortcut("act:kill",        "杀掉进程",  R.drawable.ic_drawer_more),    true),
            ActionDef.of(null,       new Shortcut("act:extrakeys",   "快捷键栏",  R.drawable.ic_drawer_more),    false),
            ActionDef.of(null,       new Shortcut("act:tabbar",      "Session 栏", R.drawable.ic_drawer_more),   false),
            ActionDef.of(null,       new Shortcut("act:styling",     "终端样式",  R.drawable.ic_drawer_theme),   false),
            ActionDef.of(null,       new Shortcut("act:keep_screen", "保持亮屏",  R.drawable.ic_drawer_more),    false),
            ActionDef.of(null,       new Shortcut("act:help",        "帮助",      R.drawable.ic_drawer_about),   false),
            ActionDef.of(null,       new Shortcut("act:settings",    "设置",      R.drawable.ic_drawer_more),    false),
            ActionDef.of(null,       new Shortcut("act:report",      "反馈问题",  R.drawable.ic_drawer_about),   false),
    };

    /** ★ 出厂默认：三键各不相同，但都是合法槽位值，永不为 null */
    private static final Shortcut DEFAULT_ADD    = S_NEW;
    private static final Shortcut DEFAULT_SNIPPET =
            new Shortcut("tab:" + TAB_SNIPPET, "收藏夹", R.drawable.ic_drawer_snippet);
    private static final Shortcut DEFAULT_MENU =
            new Shortcut("tab:" + TAB_MORE, "更多", R.drawable.ic_drawer_more);

    private final TermuxActivity mActivity;
    private final TerminalView mTerminalView;
    private final View mRoot;
    private final LinearLayout mContainer;
    private final LinearLayout mActionGroup;

    private boolean mVisible = true;
    private int mOriginalHeight = 0;
    /** 标签 more 菜单 */
    private PopupWindow mPopup;
    /** ☆/≡/＋ 长按选择器 */
    private PopupWindow mPickerPopup;

    private ImageView mBtnAdd;
    private ImageView mBtnSnippet;
    private ImageView mBtnMenu;

    /** ★ 三个槽位：永不为 null，默认态即 DEFAULT_* */
    private Shortcut mAddShortcut = DEFAULT_ADD;
    private Shortcut mSnippetShortcut = DEFAULT_SNIPPET;
    private Shortcut mMenuShortcut = DEFAULT_MENU;

    /** 选择器弹出瞬间记录「当前在配置哪个按钮」，只影响高亮与写入槽位，不影响列表内容 */
    private Shortcut mPickerSlot;
    private String mPickerDefaultId;
    private String mPickerWho;

    private boolean mShortcutRestored = false;

    /** ★ 延迟归位任务：字段先建，避免 render() 早于 init() 时 NPE */
    private final Runnable mResetAnimTask = this::cancelButtonAnim;

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
        restoreShortcut();
        installActionButtons();
        // ★ 启动读回持久化显隐：默认展开，老版本覆盖安装后不消失。
        //   只改内存，不写盘——磁盘此刻就是权威值，再写一遍属无意义 IO。
        mVisible = prefs().getBoolean(KEY_VISIBLE, true);
        if (!mVisible) applyVisibility(false);
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

        mRoot.setBackground(theme().tab(mActivity));
        // ★ 注意：这里不能调 cancelButtonAnim()。
        //   点 + 的时序是 ACTION_UP → release() 起转圈(≥320ms) → onClick → addNewSession → render()，
        //   若在此处立即 reset，会把刚起来的转圈当场清掉，表现为「点 + 没动效、只有长按有」（长按不走此路径）。
        //   改为让动画先播完，再延迟归位清理可能的属性残留。
        scheduleButtonAnimReset();
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
     * ★ 高亮唯一判定：抽屉开着 且 停在该槽位代表的抽屉页 → 点亮。
     * 动作类槽位（act:*）不代表抽屉页，恒不点亮。
     */
    public void syncHighlights() {
        if (mBtnSnippet == null || mBtnMenu == null) return;
        String tabKey = null;
        for (Shortcut s : TAB_SHORTCUTS) {
            if (mActivity.isDrawerOnTabPublic(s.id.substring(4))) { tabKey = s.id; break; }
        }
        tint(mBtnSnippet, tabKey != null && tabKey.equals(mSnippetShortcut.id));
        tint(mBtnMenu,    tabKey != null && tabKey.equals(mMenuShortcut.id));
        tint(mBtnAdd,     tabKey != null && tabKey.equals(mAddShortcut.id));
    }

    /** ★ 切主题后由 Activity 调用：重取色、重设背景、重挂动效 */
    public void reloadTheme() {
        Theme t = theme();
        cancelButtonAnim();
        mRoot.setBackground(t.tab(mActivity));
        if (mPickerPopup != null && mPickerPopup.isShowing()) {
            mPickerPopup.getContentView().setBackground(t.card(mActivity));
            applyPickerTextColors(mPickerPopup.getContentView());
        }
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.getContentView().setBackground(t.card(mActivity));
            applyDrawerTextColors(mPopup.getContentView());
        }
        syncHighlights();
        ThemeMotionPlayer.attach(mBtnAdd, t);
        ThemeMotionPlayer.attach(mBtnSnippet, t);
        ThemeMotionPlayer.attach(mBtnMenu, t);
        for (int i = 0; i < mContainer.getChildCount(); i++) {
            View tab = mContainer.getChildAt(i);
            View itemRoot = tab.findViewById(R.id.tab_item_root);
            if (itemRoot != null) ThemeMotionPlayer.attach(itemRoot, t);
        }
        mRoot.invalidate();
    }

    /**
     * ★ 重建/换肤前把三个动作按钮的动效归位。
     * 裸 animate().cancel() 只中断播放不回滚属性，SPIN 转到一半被掐断会让按钮永久歪着。
     * 统一走 ThemeMotionPlayer.reset()，cancel + 属性归位一次做完。
     * 按钮可能为 null（init 之前的早期 render），reset 内部已判空。
     */
    private void cancelButtonAnim() {
        ThemeMotionPlayer.reset(mBtnAdd);
        ThemeMotionPlayer.reset(mBtnSnippet);
        ThemeMotionPlayer.reset(mBtnMenu);
    }

    /**
     * ★ 延迟归位：让 click 触发的转圈动画先完整播完，再清理属性残留。
     * 时长取 pressDurationMs + 380（SPIN 下限），与 ThemeMotionPlayer.release() 保持一致。
     * 用于 render() 这类「动画之后必然发生」的重排场景，避免立即 reset 把动效掐没。
     */
    private void scheduleButtonAnimReset() {
        if (mRoot == null) return;
        long delay;
        try {
            final ThemeMotion m = theme().motion();
            delay = Math.max(m.pressDurationMs, 0) + 380;
        } catch (Throwable ignored) {
            delay = 500;
        }
        mRoot.removeCallbacks(mResetAnimTask);
        mRoot.postDelayed(mResetAnimTask, delay);
    }

    public void dismissPopup() { dismissPopups(); }

    private void dismissPopups() {
        if (mPopup != null) { mPopup.dismiss(); mPopup = null; }
        if (mPickerPopup != null) { mPickerPopup.dismiss(); mPickerPopup = null; }
    }

    public boolean onBackPressed() {
        if (mPopup != null && mPopup.isShowing()) { dismissPopups(); return true; }
        if (mPickerPopup != null && mPickerPopup.isShowing()) { dismissPopups(); return true; }
        return false;
    }

    /**
     * ★ 只读偏好，不写盘。
     * 内部一致性校正（mVisible 与磁盘相左时以磁盘为准）也不落盘——磁盘是权威值，
     * 写回去等于让回前台/服务重连把用户「隐藏」的状态带偏。写入只经 {@link #setVisibleNow}。
     */
    public void ensureVisible() {
        boolean stored = prefs().getBoolean(KEY_VISIBLE, true);
        if (stored != mVisible) {
            mVisible = stored;
            applyVisibility(false);
        }
        if (!stored) return;
        List<?> sessions = sessionList();
        if (sessions == null || sessions.isEmpty()) return;
        if (!mVisible) {
            mVisible = true;
            applyVisibility(false);
            render();
        }
    }

    public boolean toggle() {
        setVisibleNow(!mVisible);
        mActivity.showToast("Session 栏：" + (mVisible ? "显示" : "隐藏"), false);
        return mVisible;
    }

    /** ★ 幂等设置显隐：任何入口（长按菜单 / act:tabbar / 启动读回）都经此落盘，
     *  避免「菜单能记住、快捷键不记得」这类双路径漏改 */
    public void setVisibleNow(boolean show) {
        if (mVisible == show) {
            if (show) render();
            return;
        }
        mVisible = show;
        prefs().edit().putBoolean(KEY_VISIBLE, show).apply();
        if (show) render();
        else applyVisibility(false);
    }

    public boolean isVisibleNow() { return mVisible; }

    public void detach() {
        dismissPopups();
        if (mRoot != null) mRoot.removeCallbacks(mResetAnimTask);
    }

    /* ================= 三个动作按钮 ================= */

    private void installActionButtons() {
        // ★ 首次构建前归位一次：防止重建后的按钮带着上一次的 rotation/scale 残留
        cancelButtonAnim();
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

        // ★ 单击：一律 runSlot(当前槽位)。三个按钮同一入口，行为只由槽位决定
        mBtnAdd.setOnClickListener(v -> { runSlot(mAddShortcut); syncHighlights(); });
        mBtnSnippet.setOnClickListener(v -> { runSlot(mSnippetShortcut); syncHighlights(); });
        mBtnMenu.setOnClickListener(v -> { runSlot(mMenuShortcut); syncHighlights(); });

        // ★ 长按：三个按钮同一份选择器，无参调用
        mBtnAdd.setOnLongClickListener(v -> { showShortcutPicker(v, mAddShortcut, "＋"); return true; });
        mBtnSnippet.setOnLongClickListener(v -> { showShortcutPicker(v, mSnippetShortcut, "☆"); return true; });
        mBtnMenu.setOnLongClickListener(v -> { showShortcutPicker(v, mMenuShortcut, "≡"); return true; });

        Theme t = theme();
        ThemeMotionPlayer.attach(mBtnAdd, t);
        ThemeMotionPlayer.attach(mBtnSnippet, t);
        ThemeMotionPlayer.attach(mBtnMenu, t);

        applyShortcutIcon();
    }

    private void tint(ImageView iv, boolean active) {
        if (iv == null) return;
        iv.setColorFilter(active ? accent() : iconNormal());
        iv.invalidate();
    }

    /* ================= ★ 行为唯一入口：只认槽位，不看 null ================= */

    private void runSlot(Shortcut s) {
        if (s == null) s = DEFAULT_ADD;                 // 防御：理论上不会为 null
        String id = s.id;
        if (id.startsWith("tab:")) {
            mActivity.openDrawerTabPublic(id.substring(4));
            return;
        }
        if (id.equals(S_NEW.id)) {
            ensureVisible();
            mActivity.addNewSession(false, null);
            return;
        }
        if (id.equals(S_FAILSAFE.id)) {
            ensureVisible();
            mActivity.addNewSession(true, null);
            return;
        }
        if (id.equals(S_NAMED.id)) {
            ensureVisible();
            mActivity.showNamedSessionDialog(false);
            return;
        }
        ActionDef def = null;
        for (ActionDef a : MORE_ACTIONS) if (a.shortcut.id.equals(id)) { def = a; break; }
        if (def == null) {
            Logger.logWarn(LOG_TAG, "未知快捷方式 id=" + id + "，回落新建会话");
            ensureVisible();
            mActivity.addNewSession(false, null);
            return;
        }
        if (def.needSession && !mActivity.hasCurrentSession()) {
            mActivity.showToast("还没有会话", false);
            return;
        }
        // ★ 亮屏复用长按菜单 ID_KEEP_SCREEN，由 TerminalContextMenu.toggleKeepScreenOn()
        //   统一走 terminalView.setKeepScreenOn + sessions.saveKeepScreenOn，不自己碰 Window flags
        if ("act:keep_screen".equals(id)) {
            mActivity.dispatchContextMenuAction(TerminalContextMenu.ID_KEEP_SCREEN);
            return;
        }
        // ★ 其余动作一律按菜单 ID 委托，与长按菜单 / 抽屉走同一实现，不另写一份
        int menuId = menuIdOf(id);
        if (menuId < 0) {
            Logger.logWarn(LOG_TAG, "动作无对应菜单 ID，忽略 id=" + id);
            return;
        }
        mActivity.dispatchContextMenuAction(menuId);
    }

    private int menuIdOf(String id) {
        switch (id) {
            case "act:select_url":    return TerminalContextMenu.ID_SELECT_URL;
            case "act:share":         return TerminalContextMenu.ID_SHARE_TRANSCRIPT;
            case "act:reset":         return TerminalContextMenu.ID_RESET;
            case "act:kill":          return TerminalContextMenu.ID_KILL;
            case "act:extrakeys":     return TerminalContextMenu.ID_TOGGLE_EXTRA_KEYS;
            case "act:tabbar":        return TerminalContextMenu.ID_TOGGLE_TAB_BAR;
            case "act:styling":       return TerminalContextMenu.ID_STYLING;
            case "act:help":          return TerminalContextMenu.ID_HELP;
            case "act:settings":      return TerminalContextMenu.ID_SETTINGS;
            case "act:report":        return TerminalContextMenu.ID_REPORT;
            default:                  return -1;   // 调用处判 <0 忽略
        }
    }

    /* ================= ★ 长按 → 同一份选择器 ================= */

    private void showShortcutPicker(View anchor, Shortcut current, String who) {
        dismissPopups();
        mPickerSlot = current;
        mPickerWho = who;
        mPickerDefaultId = "＋".equals(who) ? DEFAULT_ADD.id
                : ("☆".equals(who) ? DEFAULT_SNIPPET.id : DEFAULT_MENU.id);

        List<ActionDef> list = collectShortcuts();
        if (list.isEmpty()) return;

        int w = Math.max(dp(200), anchor.getWidth());
        View content = new LinearLayout(mActivity);
        ((LinearLayout) content).setOrientation(LinearLayout.VERTICAL);
        content.setBackground(theme().card(mActivity));

        ScrollView scroller = new ScrollView(mActivity);
        scroller.setVerticalScrollBarEnabled(true);
        scroller.setScrollbarFadingEnabled(false);

        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));

        TextView title = new TextView(mActivity);
        title.setText("选择快捷操作");
        title.setTextSize(13f);
        title.setTextColor(theme().textSecondary());
        title.setPadding(dp(10), dp(8), dp(10), dp(6));
        box.addView(title);

        bindPickerItems(box, list);
        applyPickerTextColors(content);

        scroller.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout) content).addView(scroller,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        content.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        PopupWindow pop = new PopupWindow(content, w,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setFocusable(true);
        pop.setTouchable(true);
        pop.setOutsideTouchable(true);
        pop.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        pop.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        pop.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        pop.setOnDismissListener(() -> { if (mPickerPopup == pop) mPickerPopup = null; });

        // ★ 高度封顶：超出半屏即可滚动
        int avail = mRoot.getResources().getDisplayMetrics().heightPixels
                - statusBarHeight() - dp(DRAWER_GAP_DP * 2);
        int wanted = content.getMeasuredHeight();
        int maxH = Math.round(avail * 0.6f);
        if (wanted > maxH) {
            ViewGroup.LayoutParams lp = scroller.getLayoutParams();
            lp.height = maxH;
            scroller.setLayoutParams(lp);
        }

        // ★ 向上弹出：以锚点顶部为基准往上排，空间不足才翻转向下
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int gap = dp(DRAWER_GAP_DP);
        int y = loc[1] - Math.min(wanted, maxH) - gap;
        if (y < statusBarHeight()) y = loc[1] + anchor.getHeight() + gap;
        pop.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0], y);
        mPickerPopup = pop;

        // ★ 自动滚到当前绑定项
        final Shortcut cur = current;
        final String defId = mPickerDefaultId;
        scroller.post(() -> {
            View target = box.findViewWithTag(cur == null ? defId : cur.id);
            if (target != null) scroller.smoothScrollTo(0, target.getTop());
        });
    }

    /** ★ 三个按钮共用：菜单内容与顺序完全一致 */
    private List<ActionDef> collectShortcuts() {
        List<ActionDef> out = new ArrayList<>();
        for (ActionDef a : NEW_ACTIONS) out.add(a);
        for (Shortcut s : TAB_SHORTCUTS) out.add(new ActionDef("抽屉页", s, false, s.icon));
        for (ActionDef a : MORE_ACTIONS) out.add(a);
        return out;
    }

    private void bindPickerItems(LinearLayout box, List<ActionDef> list) {
        Theme t = theme();
        Shortcut cur = mPickerSlot;
        String defId = mPickerDefaultId;
        String lastSection = null;
        for (ActionDef a : list) {
            if (a.section != null && !a.section.equals(lastSection)) {
                box.addView(sectionLabel(a.section));
                lastSection = a.section;
            }
            Shortcut s = a.shortcut;
            boolean isCurrent = cur == null ? defId.equals(s.id) : cur.id.equals(s.id);
            box.addView(pickerItem(s, isCurrent, t));
            box.addView(divider());
        }
    }

    private View pickerItem(Shortcut s, boolean isCurrent, Theme t) {
        TextView item = new TextView(mActivity);
        item.setText(s.label);
        item.setTextSize(14f);
        item.setTag(s.id);
        item.setPadding(dp(10), dp(10), dp(10), dp(10));
        item.setTextColor(isCurrent ? t.color(Theme.ON_ACCENT) : t.textPrimary());
        if (isCurrent) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            bg.setColor(t.color(Theme.ACCENT) & 0x33FFFFFF);
            item.setBackground(bg);
        } else {
            item.setBackground(null);
        }
        item.setOnClickListener(v -> {
            dismissPopups();
            applyShortcut(s, mPickerWho);
        });
        return item;
    }

    private View sectionLabel(String text) {
        TextView tv = new TextView(mActivity);
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setTextColor(theme().textSecondary());
        tv.setPadding(dp(10), dp(10), dp(10), dp(4));
        return tv;
    }

    private void applyPickerTextColors(View content) {
        if (!(content instanceof ViewGroup)) return;
        tintRemainingTexts((ViewGroup) content, theme().textPrimary());
    }

    private void tintRemainingTexts(ViewGroup group, int color) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextColor(color);
            else if (child instanceof ViewGroup) tintRemainingTexts((ViewGroup) child, color);
        }
    }

    private View divider() {
        View d = new View(mActivity);
        d.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        d.setBackgroundColor(theme().color(Theme.DIVIDER));
        return d;
    }

    private void applyShortcut(Shortcut s, String who) {
        boolean isDefault = ("＋".equals(who) && DEFAULT_ADD.id.equals(s.id))
                || ("☆".equals(who) && DEFAULT_SNIPPET.id.equals(s.id))
                || ("≡".equals(who) && DEFAULT_MENU.id.equals(s.id));
        if ("＋".equals(who)) {
            mAddShortcut = s;
            prefs().edit().putString(KEY_ADD_ID, isDefault ? "" : s.id).apply();
        } else if ("☆".equals(who)) {
            mSnippetShortcut = s;
            prefs().edit().putString(KEY_SNIPPET_ID, isDefault ? "" : s.id).apply();
        } else {
            mMenuShortcut = s;
            prefs().edit().putString(KEY_MENU_ID, isDefault ? "" : s.id).apply();
        }
        mActivity.showToast(who + (isDefault ? " 已恢复为默认" : " 已替换为「" + s.label + "」"), false);
        applyShortcutIcon();
    }

    /**
     * ★ 图标与行为同源：都读槽位对象本身，不再有「null 走另一条路」。
     * 槽位为 null 时先回落默认，保证任何时刻槽位都是真实值；
     * 按钮为 null（init 之前调用）时只修正槽位、不触碰视图。
     */
    private void applyShortcutIcon() {
        if (mAddShortcut == null) mAddShortcut = DEFAULT_ADD;
        if (mSnippetShortcut == null) mSnippetShortcut = DEFAULT_SNIPPET;
        if (mMenuShortcut == null) mMenuShortcut = DEFAULT_MENU;

        if (mBtnAdd != null) {
            mBtnAdd.setImageResource(mAddShortcut.icon);
            mBtnAdd.setContentDescription(mAddShortcut.label);
        }
        if (mBtnSnippet != null) {
            mBtnSnippet.setImageResource(mSnippetShortcut.icon);
            mBtnSnippet.setContentDescription(mSnippetShortcut.label);
        }
        if (mBtnMenu != null) {
            mBtnMenu.setImageResource(mMenuShortcut.icon);
            mBtnMenu.setContentDescription(mMenuShortcut.label);
        }
        syncHighlights();
    }

    private void restoreShortcut() {
        if (mShortcutRestored) return;
        mShortcutRestored = true;
        mAddShortcut = resolve(prefs().getString(KEY_ADD_ID, null), DEFAULT_ADD);
        mSnippetShortcut = resolve(prefs().getString(KEY_SNIPPET_ID, null), DEFAULT_SNIPPET);
        mMenuShortcut = resolve(prefs().getString(KEY_MENU_ID, null), DEFAULT_MENU);
        applyShortcutIcon();
    }

    /** ★ 单一解析入口：空 / 脏数据一律回落默认，绝不留 null */
    private Shortcut resolve(String id, Shortcut def) {
        if (id != null && !id.isEmpty()) {
            for (ActionDef a : NEW_ACTIONS)
                if (a.shortcut.id.equals(id)) return a.shortcut;
            for (Shortcut s : TAB_SHORTCUTS)
                if (s.id.equals(id)) return s;
            for (ActionDef a : MORE_ACTIONS)
                if (a.shortcut.id.equals(id)) return a.shortcut;
        }
        return def;
    }

    private SharedPreferences prefs() {
        return mActivity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
            itemRoot.setActivated(true);
            title.setTextColor(titleActive());
            icon.setColorFilter(accent());
            more.setColorFilter(accent());
        } else {
            line.setVisibility(View.GONE);
            itemRoot.setActivated(false);
            title.setTextColor(titleNormal());
            icon.setColorFilter(iconNormal());
            more.setColorFilter(iconNormal());
        }
        itemRoot.setBackground(theme().button(mActivity));

        itemRoot.setOnClickListener(v -> {
            dismissPopups();
            mActivity.setCurrentSession(session);
            render();
            ensureVisible();
        });
        ThemeMotionPlayer.attach(itemRoot, theme());

        // ★ 长按标签 = 新建会话
        itemRoot.setLongClickable(true);
        itemRoot.setOnLongClickListener(v -> {
            dismissPopups();
            runSlot(S_NEW);
            return true;
        });

        more.setOnClickListener(v -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (current != session) mActivity.setCurrentSession(session);
            showDrawer(more, session);
        });

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
        dismissPopups();
        parent.post(() -> showDrawerAbove(parent, session));
    }

    private void showDrawerAbove(View anchor, TerminalSession session) {
        int anchorW = anchor.getWidth();
        if (anchorW <= 0) anchorW = dp(200);

        View content = mActivity.getLayoutInflater().inflate(R.layout.popup_tab_menu, null);
        content.setBackground(theme().card(mActivity));
        applyDrawerTextColors(content);

        content.measure(
                View.MeasureSpec.makeMeasureSpec(anchorW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        mPopup = new PopupWindow(content, anchorW,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        mPopup.setFocusable(false);
        mPopup.setTouchable(true);
        mPopup.setOutsideTouchable(true);
        mPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        mPopup.setAnimationStyle(0);
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        mPopup.setOnDismissListener(() -> mPopup = null);

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int gap = dp(DRAWER_GAP_DP);
        int y = loc[1] - content.getMeasuredHeight() - gap;
        if (y < statusBarHeight()) y = loc[1] + anchor.getHeight() + gap;
        mPopup.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0], y);

        content.findViewById(R.id.item_rename).setOnClickListener(v -> {
            dismissPopups();
            rename(session);
        });
        content.findViewById(R.id.item_reset).setOnClickListener(v -> {
            dismissPopups();
            mActivity.resetSession(session);
        });
        View kill = content.findViewById(R.id.item_kill);
        kill.setEnabled(session.isRunning());
        kill.setAlpha(session.isRunning() ? 1f : 0.4f);
        kill.setOnClickListener(v -> {
            if (!session.isRunning()) return;
            dismissPopups();
            mActivity.confirmKill(session);
        });
        content.findViewById(R.id.item_close).setOnClickListener(v -> {
            dismissPopups();
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
    private int titleActive() { return theme().textPrimary(); }
    private int titleNormal() { return theme().textSecondary(); }
    private int accent() { return theme().accent(); }
    private int iconNormal() { return theme().color(Theme.TEXT_SEC); }
    private int drawerTextColor() { return theme().textPrimary(); }
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
