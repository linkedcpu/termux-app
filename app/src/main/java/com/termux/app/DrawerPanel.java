package com.termux.app.terminal;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.TermuxConstants;

/**
 * ★ 左抽屉面板（独立控件）
 *
 * <p>菜单：帮助 / 设置 / 终端样式 / 关于 已移除：测试按钮、清空收藏夹
 */
public final class DrawerPanel {

    private final TermuxActivity mActivity;
    private final View mRoot;
    private final LinearLayout mMenuContainer;

    public DrawerPanel(@NonNull TermuxActivity activity) {
        mActivity = activity;

        mRoot = LayoutInflater.from(activity).inflate(R.layout.drawer_panel, null, false);

        mMenuContainer = mRoot.findViewById(R.id.drawer_menu_container);

        setupHeader();
        buildMenu();
        setupVersion();
    }

    @NonNull
    public View getView() {
        return mRoot;
    }

    /* ================= 装配 ================= */

    private void setupHeader() {
        TextView title = mRoot.findViewById(R.id.drawer_title);
        if (title != null) title.setText("Termux");
    }

    private void buildMenu() {
        mMenuContainer.removeAllViews();
        addItem("帮助", this::openHelp);
        addItem("设置", this::openSettings);
        addItem("终端样式", this::openStyling);
        addDivider();
        addItem("关于", this::showAbout);
    }

    private void setupVersion() {
        TextView ver = mRoot.findViewById(R.id.drawer_version);
        if (ver == null) return;
        String v = "Termux";
        try {
            v +=
                    " · "
                            + mActivity
                                    .getPackageManager()
                                    .getPackageInfo(mActivity.getPackageName(), 0)
                                    .versionName;
        } catch (Exception ignored) {
        }
        ver.setText(v);
    }

    /* ================= 菜单动作 ================= */

    private void openHelp() {
        closeDrawer();
        mActivity.startActivity(new Intent(mActivity, HelpActivity.class));
    }

    private void openSettings() {
        closeDrawer();
        mActivity.startActivity(new Intent(mActivity, SettingsActivity.class));
    }

    private void openStyling() {
        closeDrawer();
        Intent i = new Intent();
        i.setClassName(
                TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
                TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            mActivity.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(mActivity, "未安装 Termux:Styling", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAbout() {
        closeDrawer();
        new AlertDialog.Builder(mActivity)
                .setTitle("关于 Termux")
                .setMessage("终端 · Session 标签栏 · 收藏夹\n\n定制构建")
                .setPositiveButton("确定", null)
                .show();
    }

    private void closeDrawer() {
        if (mActivity.getDrawer() != null) mActivity.getDrawer().closeDrawers();
    }

    /* ================= UI 零件 ================= */

    private void addItem(@NonNull String title, @NonNull Runnable action) {
        TextView tv = new TextView(mActivity);
        tv.setText(title);
        tv.setTextSize(14f);
        tv.setTextColor(mActivity.getResources().getColor(android.R.color.black));
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        tv.setBackgroundResource(getResIdFromAttr(android.R.attr.selectableItemBackground));
        tv.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mMenuContainer.addView(tv, lp);
    }

    private void addDivider() {
        View d = new View(mActivity);
        d.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(4), dp(8), dp(4), dp(8));
        mMenuContainer.addView(d, lp);
    }

    /** ★ 把 ?attr/selectableItemBackground 解析成 res id，避免引用不存在的 drawable */
    private int getResIdFromAttr(int attr) {
        android.util.TypedValue out = new android.util.TypedValue();
        mActivity.getTheme().resolveAttribute(attr, out, true);
        return out.resourceId;
    }

    private int dp(int v) {
        return Math.round(v * mActivity.getResources().getDisplayMetrics().density);
    }
}
