package com.termux.app.terminal.theme;

import android.content.Context;
import android.content.SharedPreferences;

/** 主题持久化：只负责读写、缓存与联动，不含颜色判断 */
public final class ThemeStore {

    private static final String PREF = "app_theme";
    private static final String KEY_NAME = "name";
    private static final String KEY_WALLPAPER = "wallpaper";

    private static final String OLD_PREF = "drawer_theme";
    private static final String OLD_KEY_PRESET = "preset";

    private static volatile Theme sCache;
    private static volatile String sKey;

    private ThemeStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static Theme get(Context c) {
        SharedPreferences sp = prefs(c);
        migrateIfNeeded(c, sp);
        String name = sp.getString(KEY_NAME, ThemePreset.ALL[0].name);
        Theme t = sCache;
        if (t == null || !name.equals(sKey)) {
            sKey = name;
            sCache = new Theme(ThemePreset.get(ThemePreset.indexOf(name)));
        }
        return sCache;
    }

    public static void select(Context c, int index) {
        ThemePreset p = ThemePreset.get(index);
        prefs(c).edit().putString(KEY_NAME, p.name).apply();
        TerminalColorsWriter.apply(c, p); // 单向联动终端配色
        invalidate();
    }

    public static void reset(Context c) {
        prefs(c).edit().clear().apply();
        TerminalColorsWriter.apply(c, ThemePreset.ALL[0]);
        invalidate();
    }

    public static boolean isSelected(Context c, int index) {
        SharedPreferences sp = prefs(c);
        return ThemePreset.get(index).name.equals(sp.getString(KEY_NAME, ThemePreset.ALL[0].name));
    }

    public static String currentName(Context c) {
        return prefs(c).getString(KEY_NAME, ThemePreset.ALL[0].name);
    }

    public static void invalidate() {
        sCache = null;
    }

    private static void migrateIfNeeded(Context c, SharedPreferences sp) {
        if (sp.contains(KEY_NAME)) {
            // 壁纸功能已移除：顺手清掉历史遗留字段
            if (sp.contains(KEY_WALLPAPER)) {
                sp.edit().remove(KEY_WALLPAPER).apply();
            }
            return;
        }
        SharedPreferences old = c.getSharedPreferences(OLD_PREF, Context.MODE_PRIVATE);
        int idx = old.contains(OLD_KEY_PRESET) ? old.getInt(OLD_KEY_PRESET, 0) : 0;
        sp.edit().putString(KEY_NAME, ThemePreset.get(idx).name).apply();
        old.edit().clear().apply();
    }
}
