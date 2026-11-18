package com.termux.app.terminal;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.termux.shared.logger.Logger;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.termux.R;
import com.termux.app.SnippetManager;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.theme.Theme;
import com.termux.app.terminal.theme.ThemePreset;
import com.termux.app.terminal.theme.ThemeStore;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * ★ 左抽屉面板（Tab 化 + 界面主题管理器 + 收藏夹 + 更多菜单）
 *
 * 外观只调三类语义入口，不判壁纸、不判亮度、不写死色值/圆角/时长：
 *   mTheme.color(角色)              —— 配色
 *   mTheme.panel / surface / button —— 形状 + 材质 + 壁纸，全部由 Theme 决定
 *   mTheme.radius / stroke / pillButtons / motion —— 皮肤参数
 *
 * ★ Tab 图标：TextView + compound drawable，与 SnippetManager.setBtnIcon 同一路径。
 * ★ Tab 栏自适应：≤ MAX_FIT_TABS 时 weight 均分铺满；超出自动横滑。
 *
 * ★★ 收藏夹只在此处挂载（buildSnippetPage），TermuxActivity 不再拆装视图树；
 *    SnippetManager 由构造期注入，不再通过 Activity 反向 getter 获取。
 */
public final class DrawerPanel {

    private static final int TAB_ICON_SIZE_DP = 18;
    private static final int TAB_PAD_H_DP     = 8;
    private static final int TAB_PAD_V_DP     = 9;
    private static final int TAB_MIN_W_DP     = 44;

    /** ★ 不超过这个数量就均分铺满、不启用横滑 */
    private static final int MAX_FIT_TABS     = 5;

    /** 层级基准：控件只说「我是哪一层」，圆润倍率由皮肤决定 */
    private static final float RADIUS_PAGE_DP = 14f;
    private static final float RADIUS_TAB_DP  = 10f;
    private static final float RADIUS_BTN_DP  = 8f;

    private static final int ICON_THEME   = R.drawable.ic_drawer_theme;
    private static final int ICON_MORE    = R.drawable.ic_drawer_more;
    private static final int ICON_SNIPPET = R.drawable.ic_drawer_snippet;
    private static final int ICON_ABOUT   = R.drawable.ic_drawer_about;

    private static final String LOG_TAG = "DrawerPanel";

    /* ================= Tab 定义（数组驱动） ================= */

    private static final class TabDef {
        final String title;
        final String key;
        final int icon;
        final Runnable builder;

        TabDef(String title, String key, int icon, Runnable builder) {
            this.title = title;
            this.key = key;
            this.icon = icon;
            this.builder = builder;
        }
    }

    /** 增删 Tab 只改这里：≤5 个自动均分铺满，>5 个自动变横滑 */
    private TabDef[] tabs() {
        return new TabDef[] {
                new TabDef("主题",   "theme",   ICON_THEME,   this::buildThemePage),
                new TabDef("设置",   "more",    ICON_MORE,    this::buildMorePage),
                new TabDef("收藏夹", "snippet", ICON_SNIPPET, this::buildSnippetPage),
                new TabDef("关于",   "about",   ICON_ABOUT,   this::buildAboutPage),
        };
    }

    /* ================= 成员 ================= */

    private final TermuxActivity mActivity;
    private final View mRoot;
    private final LinearLayout mTabContainer;
    private final FrameLayout mContent;

    /** ★ 构造期注入，不再反向依赖 Activity.getSnippetManager() */
    private final SnippetManager mSnippet;

    private Theme mTheme;
    private int mColorBg;
    private int mColorSurface;
    private int mColorText;
    private int mColorTextSec;
    private int mColorIcon;
    private int mColorAccent;
    private int mColorOutline;
    private int mPressDuration;

    private View mSelectedTab;
    private ActivityResultLauncher<String> mPickImage;

    private boolean mSnippetTabActive = false;

    private String mCurrentTabKey = null;
    private Runnable mOnTabChanged;

    public DrawerPanel(@NonNull TermuxActivity activity, @Nullable SnippetManager snippet) {
        mActivity = activity;
        mSnippet = snippet;
        if (mSnippet != null) {
            mSnippet.setTabMode(true);
        }

        applyColors();

        mRoot = LayoutInflater.from(activity).inflate(R.layout.drawer_panel, null, false);
        mTabContainer = mRoot.findViewById(R.id.drawer_tab_container);
        mContent = mRoot.findViewById(R.id.drawer_content);

        // ★ 去掉系统阴影，避免圆角外四角露出 scrim
        mRoot.setElevation(0f);

        registerPickImage();
        applyPanel();

        buildTabs();
        setupVersion();

        if (mTabContainer.getChildCount() > 0) {
            mTabContainer.getChildAt(0).performClick();
        }
    }

    @NonNull
    public View getView() {
        return mRoot;
    }

    /* ================= 供 TermuxActivity 查询 / 监听 Tab ================= */

    public void setOnTabChanged(Runnable r) {
        mOnTabChanged = r;
    }

    public boolean isCurrentTab(String tabKey) {
        return tabKey != null && tabKey.equals(mCurrentTabKey);
    }

    public String getCurrentTabKey() {
        return mCurrentTabKey;
    }

    private void notifyTabChanged(String key) {
        mCurrentTabKey = key;
        if (mOnTabChanged != null) {
            mOnTabChanged.run();
        }
    }

    /* ================= 配色 / 外观 ================= */

    private void applyColors() {
        mTheme = ThemeStore.get(mActivity);
        mColorBg         = mTheme.color(Theme.BG);
        mColorSurface    = mTheme.color(Theme.SURFACE);
        mColorText       = mTheme.color(Theme.TEXT);
        mColorTextSec    = mTheme.color(Theme.TEXT_SEC);
        mColorIcon       = mTheme.color(Theme.ICON);
        mColorAccent     = mTheme.color(Theme.ACCENT);
        mColorOutline    = mTheme.color(Theme.OUTLINE);
        mPressDuration   = mTheme.motion().pressDurationMs;
    }

    /** ★ 一行拿到抽屉根背景：壁纸/材质/蒙层/圆角/描边全在 Theme 里决定 */
    private void applyPanel() {
        mRoot.setBackground(mTheme.panel(mActivity, RADIUS_PAGE_DP));
    }

    /** ★ 供 TermuxActivity 切主题后调用：重新取色并重绘整个抽屉 */
    public void reloadTheme() {
        applyColors();
        applyPanel();
        setupVersion();
        buildTabs();
        if (mCurrentTabKey != null) {
            openTab(mCurrentTabKey);
        } else if (mTabContainer.getChildCount() > 0) {
            mTabContainer.getChildAt(0).performClick();
        }
        // ★ 收藏夹常驻抽屉，主题变化后无条件刷新，不再判断 isExpanded()
        if (mSnippet != null) {
            mSnippet.reloadTheme();
        }
    }

    private void registerPickImage() {
        try {
            mPickImage = mActivity.registerForActivityResult(
                    new ActivityResultContracts.GetContent(), uri -> {
                        if (uri != null) {
                            String path = copyImageToPrivate(uri);
                            if (path != null) {
                                ThemeStore.setWallpaper(mActivity, path);
                                reloadTheme();
                            }
                        }
                    });
        } catch (Exception ignored) {
            mPickImage = null;
        }
    }

    private void pickWallpaper() {
        if (mPickImage != null) {
            mPickImage.launch("image/*");
        } else {
            Toast.makeText(mActivity, "当前环境不支持选图", Toast.LENGTH_SHORT).show();
        }
    }

    private String copyImageToPrivate(Uri uri) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = mActivity.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            File dir = new File(mActivity.getFilesDir(), "theme");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File dst = new File(dir, "theme_wallpaper.img");
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            return dst.getAbsolutePath();
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
        }
    }

    /* ================= 装配 ================= */

    private void setupVersion() {
        TextView version = mRoot.findViewById(R.id.drawer_version);
        if (version == null) return;
        String text = "Termux";
        try {
            text += " · " + mActivity.getPackageManager()
                    .getPackageInfo(mActivity.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        version.setText(text);
        version.setTextColor(mColorTextSec);
    }

    /**
     * ★ 构建 Tab 栏：
     *   条目 ≤ MAX_FIT_TABS → weight 均分铺满、容器 MATCH_PARENT；
     *   条目 > MAX_FIT_TABS → 容器回退 wrap_content，由外层 HSV 提供横滑。
     */
    private void buildTabs() {
        mTabContainer.removeAllViews();
        mSelectedTab = null;
        mCurrentTabKey = null;

        TabDef[] defs = tabs();
        final boolean fit = defs.length <= MAX_FIT_TABS;

        ViewGroup.LayoutParams containerLp = mTabContainer.getLayoutParams();
        if (containerLp != null) {
            containerLp.width = fit
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : ViewGroup.LayoutParams.WRAP_CONTENT;
            mTabContainer.setLayoutParams(containerLp);
        }
        mTabContainer.setWeightSum(fit ? defs.length : 0f);

        int dp = dp(1);
        for (TabDef def : defs) {
            final TextView tab = new TextView(mActivity);
            tab.setText("");
            tab.setTextSize(13f);
            tab.setGravity(Gravity.CENTER);
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setIncludeFontPadding(false);
            tab.setMinWidth(dp(TAB_MIN_W_DP));
            tab.setPadding(dp(TAB_PAD_H_DP), dp(TAB_PAD_V_DP), dp(TAB_PAD_H_DP), dp(TAB_PAD_V_DP));
            tab.setTag(def.key);
            tab.setContentDescription(def.title);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    fit ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (fit) lp.weight = 1;
            lp.setMarginStart(dp(4));
            lp.setMarginEnd(dp(4));

            final String tabKey = def.key;
            final Runnable pageBuilder = def.builder;
            final int pressMs = mPressDuration;
            tab.setOnClickListener(v -> {
                mSnippetTabActive = "snippet".equals(tabKey);
                selectTab(tab);
                pageBuilder.run();
                notifyTabChanged(tabKey);
            });
            tab.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    v.animate().cancel();
                    v.animate().setDuration(pressMs).alpha(0.5f).start();
                } else if (e.getAction() == MotionEvent.ACTION_UP
                        || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate().cancel();
                    v.animate().setDuration(pressMs).alpha(1f).start();
                }
                return false;
            });

            setTabIcon(tab, def.icon, mColorIcon);
            tab.setBackground(tabBackground(false));
            mTabContainer.addView(tab, lp);
        }
    }

    /** ★ Tab 背景：选中态走 STATE_SELECTED，形状与壁纸适配都在 Theme 内 */
    private Drawable tabBackground(boolean selected) {
        return mTheme.surface(mActivity, RADIUS_TAB_DP);
    }

    /** ★ 与 SnippetManager.setBtnIcon 同一写法 */
    private void setTabIcon(@NonNull TextView tab, int iconRes, int color) {
        Drawable d = ContextCompat.getDrawable(mActivity, iconRes);
        if (d == null) return;
        d = DrawableCompat.wrap(d).mutate();
        DrawableCompat.setTint(d, color);
        int size = dp(TAB_ICON_SIZE_DP);
        d.setBounds(0, 0, size, size);
        tab.setCompoundDrawables(null, null, d, null);
        tab.setCompoundDrawablePadding(0);
    }

    public void openTab(@NonNull String key) {
        for (int i = 0; i < mTabContainer.getChildCount(); i++) {
            View tab = mTabContainer.getChildAt(i);
            if (key.equals(tab.getTag())) {
                tab.performClick();
                return;
            }
        }
        if (mTabContainer.getChildCount() > 0) {
            mTabContainer.getChildAt(0).performClick();
        }
    }

    private void selectTab(@NonNull View tab) {
        if (mSelectedTab != null) {
            mSelectedTab.setSelected(false);
            mSelectedTab.setBackground(tabBackground(false));
            if (mSelectedTab instanceof TextView) {
                setTabIcon((TextView) mSelectedTab, iconForTag(mSelectedTab), mColorIcon);
            }
        }
        mSelectedTab = tab;
        tab.setSelected(true);
        tab.setBackground(tabBackground(true));
        if (tab instanceof TextView) {
            setTabIcon((TextView) tab, iconForTag(tab), mColorAccent);
        }

        tab.post(() -> {
            View parent = tab.getParent() instanceof View ? (View) tab.getParent() : null;
            View scroller = parent != null && parent.getParent() instanceof View
                    ? (View) parent.getParent() : null;
            if (!(scroller instanceof InterceptHorizontalScrollView)) return;
            if (tab.getRight() <= scroller.getWidth()) return;
            int target = Math.max(tab.getLeft() - dp(16), 0);
            ((InterceptHorizontalScrollView) scroller).smoothScrollTo(target, 0);
        });
    }

    private int iconForTag(@NonNull View tab) {
        Object tag = tab.getTag();
        for (TabDef def : tabs()) {
            if (def.key.equals(tag)) return def.icon;
        }
        return ICON_THEME;
    }

    /* ================= 页面 ================= */

    private void buildMorePage() {
        LinearLayout page = newPage();
        boolean has = mActivity.hasCurrentSession();

        page.addView(makeButton("选择 URL", v -> act(TerminalContextMenu.ID_SELECT_URL)));
        page.addView(makeButton("分享转写", v -> act(TerminalContextMenu.ID_SHARE_TRANSCRIPT)));
        page.addView(makeButton("重置终端", v -> act(TerminalContextMenu.ID_RESET)));
        page.addView(makeButton("杀掉进程", v -> act(TerminalContextMenu.ID_KILL)));
        page.addView(makeButton("快捷键栏",
                v -> act(TerminalContextMenu.ID_TOGGLE_EXTRA_KEYS)));
        page.addView(makeButton("Session栏",
                v -> act(TerminalContextMenu.ID_TOGGLE_TAB_BAR)));
        page.addView(makeButton("终端样式", v -> act(TerminalContextMenu.ID_STYLING)));
        page.addView(makeButton("保持亮屏", v -> act(TerminalContextMenu.ID_KEEP_SCREEN)));
        page.addView(makeButton("帮助", v -> act(TerminalContextMenu.ID_HELP)));
        page.addView(makeButton("设置", v -> act(TerminalContextMenu.ID_SETTINGS)));
        page.addView(makeButton("反馈问题", v -> act(TerminalContextMenu.ID_REPORT)));

        if (!has) {
            page.addView(makeText("（当前无会话，部分项不可用）", 12, false));
        }
        showPage(page);
    }

    private void act(int menuId) {
        if (!mActivity.hasCurrentSession()) {
            Toast.makeText(mActivity, "还没有会话", Toast.LENGTH_SHORT).show();
            return;
        }
        closeDrawer();
        mActivity.dispatchContextMenuAction(menuId);
    }

    private void buildThemePage() {
        LinearLayout page = newPage();

        LinearLayout options = new LinearLayout(mActivity);
        options.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < ThemePreset.count(); i++) {
            options.addView(makeThemeOption(i, ThemePreset.get(i).name));
        }
        page.addView(options);

        page.addView(makeButton("从相册选择背景图", v -> pickWallpaper()));
        if (ThemeStore.hasWallpaper(mActivity)) {
            page.addView(makeButton("清除背景图", v -> {
                ThemeStore.clearWallpaper(mActivity);
                reloadTheme();
            }));
        }
        showPage(page);
    }

    private View makeThemeOption(int index, String label) {
        LinearLayout option = new LinearLayout(mActivity);
        option.setOrientation(LinearLayout.HORIZONTAL);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(4), dp(10), dp(4), dp(10));

        TextView tv = new TextView(mActivity);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(mColorText);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        option.addView(tv);

        View indicator = new View(mActivity);
        int size = dp(18);
        indicator.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setStroke(dp(2), mColorAccent);
        g.setColor(ThemeStore.isSelected(mActivity, index) ? mColorAccent : 0);
        indicator.setBackground(g);
        option.addView(indicator);

        option.setOnClickListener(v -> {
            if (ThemeStore.isSelected(mActivity, index)) return;
            ThemeStore.select(mActivity, index);
            mActivity.applyThemeToUiFromDrawer();
        });
        return option;
    }

    /**
     * ★ 收藏夹页：面板由注入的 SnippetManager 提供，
     *   复用同一实例（buildPanel 内部需 return mPanel），切 Tab 不重复建树。
     */
    private void buildSnippetPage() {
        try {
            if (mSnippet == null) {
                throw new IllegalStateException("SnippetManager 未注入");
            }
            View panel = mSnippet.buildPanel();
            if (panel.getParent() instanceof ViewGroup) {
                ((ViewGroup) panel.getParent()).removeView(panel);
            }
            panel.setVisibility(View.VISIBLE);
            mSnippetTabActive = true;

            LinearLayout host = new LinearLayout(mActivity);
            host.setOrientation(LinearLayout.VERTICAL);
            host.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            host.addView(panel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            mContent.removeAllViews();
            mContent.addView(host);
        } catch (Throwable t) {
            Logger.logWarn(LOG_TAG, "收藏夹加载失败: " + t.getMessage());
            mSnippetTabActive = false;
            LinearLayout page = newPage();
            page.addView(makeText("收藏夹", 16, true));
            page.addView(makeText("收藏夹加载失败：\n" + t.getMessage(), 13, false));
            showPage(page);
        }
    }

    private void buildAboutPage() {
        LinearLayout page = newPage();
        page.addView(makeText("关于 Termux", 16, true));
        page.addView(makeText("终端 · Session 标签栏 · 收藏夹\n\n定制构建", 13, false));
        showPage(page);
    }

    /** 保留备用：当前「终端样式」按钮走 act(ID_STYLING)，暂未调用。 */
    @SuppressWarnings("unused")
    private void openStyling() {
        Intent intent = new Intent();
        intent.setClassName(
                TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
                TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            mActivity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(mActivity, "未安装 Termux:Styling", Toast.LENGTH_SHORT).show();
        }
    }

    private void closeDrawer() {
        if (mActivity.getDrawer() != null) {
            mActivity.getDrawer().closeDrawers();
        }
    }

    /* ================= UI 零件 ================= */

    private LinearLayout newPage() {
        LinearLayout page = new LinearLayout(mActivity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(4), dp(4), dp(4), dp(4));
        page.setBackground(mTheme.surface(mActivity, RADIUS_PAGE_DP));   // ★ 一行，无 if
        return page;
    }

    private void showPage(@NonNull View page) {
        ScrollView scroller = new ScrollView(mActivity);
        scroller.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroller.addView(page);
        mContent.removeAllViews();
        mContent.addView(scroller);
    }

    private TextView makeText(String text, float sizeSp, boolean bold) {
        TextView view = new TextView(mActivity);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setLineSpacing(dp(2), 1f);
        view.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setTextColor(mColorText);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private TextView makeButton(String text, View.OnClickListener listener) {
        TextView view = new TextView(mActivity);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setTextColor(mColorAccent);
        view.setBackground(mTheme.button(mActivity, RADIUS_BTN_DP));    // ★ 一行，无 if
        view.setOnClickListener(listener);
        final int pressMs = mPressDuration;
        view.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().setDuration(pressMs).alpha(0.6f).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().setDuration(pressMs).alpha(1f).start();
            }
            return false;
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        view.setLayoutParams(params);
        return view;
    }

    private int getSelectableItemBackground() {
        TypedValue value = new TypedValue();
        if (mActivity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, value, true)) {
            return value.resourceId;
        }
        return 0;
    }

    private int dp(float value) {
        return Math.round(value * mActivity.getResources().getDisplayMetrics().density);
    }
}
