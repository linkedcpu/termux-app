package com.termux.app.terminal;

import android.graphics.Color;

/** 界面主题描述：纯界面层，不涉及终端渲染 */
public final class ThemeSpec {

    public final String name;
    public final int bgColor;
    public final int textPrimary;
    public final int textSecondary;
    public final int accent;
    public final String wallpaperPath; // 可为 null

    public ThemeSpec(String name, int bg, int textPri, int textSec, int accent, String wallpaper) {
        this.name = name;
        this.bgColor = bg;
        this.textPrimary = textPri;
        this.textSecondary = textSec;
        this.accent = accent;
        this.wallpaperPath = wallpaper;
    }

    /**
     * 内置预设：
     * 0 浅色 / 1 深色 / 2 暖夜 / 3 墨蓝 / 4 半透黑
     * bgColor 带 alpha 即为半透明主题。
     */
    public static ThemeSpec preset(int index) {
        switch (index) {
            case 1:
                return new ThemeSpec("深色", 0xff1e1f22, 0xffe6e6e6, 0xff9a9a9a, 0xff5cc97a, null);
            case 2:
                return new ThemeSpec("暖夜", 0xff241f1c, 0xfff0e8e0, 0xffa89888, 0xffd68755, null);
            case 3:
                return new ThemeSpec("墨蓝", 0xff17202a, 0xffe3ecf3, 0xff8a9bab, 0xff6cb6ff, null);
            case 4:
                // 半透黑：alpha 0xE6 ≈ 90%，透出终端但压暗保证文字可读
                return new ThemeSpec("半透黑", 0xe617191c, 0xffeaeaea, 0xffb0b0b0, 0xff5cc97a, null);
            default:
                return new ThemeSpec("浅色", 0xfff7f7f5, 0xff1f2328, 0xff8a8f95, 0xff3a8b52, null);
        }
    }

    public static int presetCount() {
        return 5;
    }

    /** 预设名，供主题页展示 */
    public static String presetName(int index) {
        switch (index) {
            case 1: return "深色";
            case 2: return "暖夜";
            case 3: return "墨蓝";
            case 4: return "半透黑";
            default: return "浅色";
        }
    }

    /** 壁纸主题：以某预设为底色，文字强制白色以叠加在图片上 */
    public static ThemeSpec wallpaper(ThemeSpec base, String path) {
        return new ThemeSpec("自定义壁纸", base.bgColor, 0xffffffff, 0xccffffff, base.accent, path);
    }

    /** 是否半透明（背景 alpha 小于不透明） */
    public boolean isTranslucent() {
        return Color.alpha(bgColor) < 0xff;
    }
}
