package com.termux.app.terminal;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.SnippetManager;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * ★ 左抽屉面板（Tab 化 + 界面主题管理器 + 收藏夹 + 更多菜单）
 *
 * 配色约定同 SessionTabBar：背景用 current()，前景用 ThemeManager.foregroundTheme()。
 */
public final class DrawerPanel {

    private final TermuxActivity mActivity;
    private final View mRoot;
    private final LinearLayout mTabContainer;
    private final FrameLayout mContent;

    private int mColorBg;
    private int mColorTextPrimary;
    private int mColorTextSecondary;
    private int mColorAccent;

    private View mSelectedTab;
    private ActivityResultLauncher<String> mPickImage;

    private SnippetManager mSnippet;
    private boolean mSnippetTabActive = false;

    private String mCurrentTabKey = null;
    private Runnable mOnTabChanged;

    public DrawerPanel(@NonNull TermuxActivity activity) {
        mActivity = activity;

        applyColors();

        mRoot = LayoutInflater.from(activity).inflate(R.layout.drawer_panel, null, false);
        mTabContainer = mRoot.findViewById(R.id.drawer_tab_container);
        mContent = mRoot.findViewById(R.id.drawer_content);

        registerPickImage();
        applyWallpaper();

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

    /* ================= 配色 / 壁纸 ================= */

    private void applyColors() {
        ThemeSpec t = ThemeManager.current(mActivity);
        // ★ 前景色走底衬预设，绕开 wallpaper() 强制白字
        ThemeSpec base = ThemeManager.foregroundTheme(mActivity);
        mColorBg = t.bgColor;                       // 背景仍用当前主题
        mColorTextPrimary = base.textPrimary;
        mColorTextSecondary = base.textSecondary;
        mColorAccent = base.accent;
    }

    private void applyWallpaper() {
        ThemeSpec t = ThemeManager.current(mActivity);
        if (t.wallpaperPath != null) {
            try {
                Drawable d = Drawable.createFromPath(t.wallpaperPath);
                if (d != null) {
                    mRoot.setBackground(d);
                    // ★ 浅底衬（配深字）时把壁纸压暗，避免暖白区白上白
                    if (!t.useLightTextOnWallpaper()) {
                        mRoot.getBackground().setColorFilter(0x73FFFFFF, PorterDuff.Mode.MULTIPLY);
                    } else {
                        mRoot.getBackground().clearColorFilter();
                    }
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        mRoot.setBackgroundColor(t.bgColor);
    }

    /** ★ 供 TermuxActivity 切主题后调用：重新取色并重绘整个抽屉 */
    public void reloadTheme() {
        applyColors();
        applyWallpaper();
        setupVersion();
        buildTabs();
        if (mTabContainer.getChildCount() > 0) {
            mTabContainer.getChildAt(0).performClick();
        }
        if (mSnippetTabActive && mSnippet != null) {
            buildSnippetPage();
        }
    }

    private void registerPickImage() {
        try {
            mPickImage = mActivity.registerForActivityResult(
                    new ActivityResultContracts.GetContent(), uri -> {
                        if (uri != null) {
                            String path = copyImageToPrivate(uri);
                            if (path != null) {
                                ThemeManager.setWallpaper(mActivity, path);
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
            File dst = new File(dir, "wallpaper.img");
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
        version.setTextColor(mColorTextSecondary);
    }

    private void buildTabs() {
        mTabContainer.removeAllViews();
        mSelectedTab = null;
        mCurrentTabKey = null;
        addTab("主题", "theme", this::buildThemePage);
        addTab("设置", "more", this::buildMorePage);
        addTab("收藏夹", "snippet", this::buildSnippetPage);
        addTab("关于", "about", this::buildAboutPage);
    }

    private void addTab(@NonNull String title, @NonNull String key, @NonNull Runnable pageBuilder) {
        TextView tab = new TextView(mActivity);
        tab.setText(title);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tab.setGravity(Gravity.CENTER);
        tab.setMinWidth(dp(64));
        tab.setPadding(dp(10), dp(9), dp(10), dp(9));
        tab.setTextColor(mColorTextSecondary);
        tab.setBackgroundResource(getSelectableItemBackground());
        tab.setIncludeFontPadding(false);
        tab.setTag(key);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(4));
        params.setMarginEnd(dp(4));

        final String tabKey = key;
        tab.setOnClickListener(v -> {
            mSnippetTabActive = "snippet".equals(tabKey);
            selectTab(tab);
            pageBuilder.run();
            notifyTabChanged(tabKey);
        });
        tab.setLayoutParams(params);
        mTabContainer.addView(tab, params);
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
            ((TextView) mSelectedTab).setTextColor(mColorTextSecondary);
            ((TextView) mSelectedTab).setTypeface(null, Typeface.NORMAL);
        }
        mSelectedTab = tab;
        tab.setSelected(true);
        ((TextView) tab).setTextColor(mColorAccent);
        ((TextView) tab).setTypeface(null, Typeface.BOLD);

        tab.post(() -> {
            int target = Math.max(tab.getLeft() - dp(16), 0);
            View parent = tab.getParent() instanceof View ? (View) tab.getParent() : null;
            View scroller = parent != null && parent.getParent() instanceof View
                    ? (View) parent.getParent() : null;
            if (scroller instanceof InterceptHorizontalScrollView) {
                ((InterceptHorizontalScrollView) scroller).smoothScrollTo(target, 0);
            }
        });
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
        for (int i = 0; i < ThemeSpec.presetCount(); i++) {
            options.addView(makeThemeOption(i, ThemeSpec.presetName(i)));
        }
        page.addView(options);

        page.addView(makeButton("从相册选择背景图", v -> pickWallpaper()));
        if (ThemeManager.hasWallpaper(mActivity)) {
            page.addView(makeButton("清除背景图", v -> {
                ThemeManager.clearWallpaper(mActivity);
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
        tv.setTextColor(mColorTextPrimary);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        option.addView(tv);

        View indicator = new View(mActivity);
        int size = dp(18);
        indicator.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(dp(2), mColorAccent);
        g.setColor(ThemeManager.isPresetSelected(mActivity, index) ? mColorAccent : 0);
        indicator.setBackground(g);
        option.addView(indicator);

        option.setOnClickListener(v -> {
            if (ThemeManager.isPresetSelected(mActivity, index)) return;
            ThemeManager.selectPreset(mActivity, index);
            mActivity.applyThemeToUiFromDrawer();
        });
        return option;
    }

    private void buildSnippetPage() {
        try {
            if (mSnippet == null) {
                mSnippet = mActivity.getSnippetManager();
                mSnippet.setTabMode(true);
            }
            View panel = mSnippet.buildPanel();
            if (panel.getParent() instanceof ViewGroup) {
                ((ViewGroup) panel.getParent()).removeView(panel);
            }
            panel.setVisibility(View.VISIBLE);
            mSnippet.expand();
            mSnippetTabActive = true;
            mContent.removeAllViews();
            mContent.addView(panel);
        } catch (Throwable t) {
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
        view.setTextColor(mColorTextPrimary);
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
        view.setBackgroundResource(getSelectableItemBackground());
        view.setOnClickListener(listener);
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
