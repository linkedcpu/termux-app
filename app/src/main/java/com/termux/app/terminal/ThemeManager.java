package com.termux.app.terminal;

import android.content.Context;
import android.content.SharedPreferences;

/** 界面主题的持久化：记住当前预设与壁纸路径，与系统 night 模式解耦 */
public final class ThemeManager {

    private static final String PREF = "drawer_theme";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_WALLPAPER = "wallpaper";

    private ThemeManager() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 当前主题：有壁纸则返回壁纸主题，否则返回选中的预设 */
    public static ThemeSpec current(Context c) {
        String wp = prefs(c).getString(KEY_WALLPAPER, null);
        ThemeSpec base = ThemeSpec.preset(prefs(c).getInt(KEY_PRESET, 0));
        if (wp != null && !wp.isEmpty()) {
            return ThemeSpec.wallpaper(base, wp);
        }
        return base;
    }

    public static void selectPreset(Context c, int index) {
        prefs(c).edit().putInt(KEY_PRESET, index).apply();
    }

    public static void setWallpaper(Context c, String path) {
        prefs(c).edit().putString(KEY_WALLPAPER, path).apply();
    }

    public static void clearWallpaper(Context c) {
        prefs(c).edit().remove(KEY_WALLPAPER).apply();
    }

    public static boolean isPresetSelected(Context c, int index) {
        String wp = prefs(c).getString(KEY_WALLPAPER, null);
        return (wp == null || wp.isEmpty()) && prefs(c).getInt(KEY_PRESET, 0) == index;
    }

    public static boolean hasWallpaper(Context c) {
        String wp = prefs(c).getString(KEY_WALLPAPER, null);
        return wp != null && !wp.isEmpty();
    }

    public static int currentPresetIndex(Context c) {
        return prefs(c).getInt(KEY_PRESET, 0);
    }
}
