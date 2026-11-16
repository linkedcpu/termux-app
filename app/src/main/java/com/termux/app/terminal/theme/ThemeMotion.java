package com.termux.app.terminal.theme;

/** 动效层：把「按下去什么感觉」收进皮肤，避免各控件写死时长与缩放 */
public final class ThemeMotion {

    public final int pressDurationMs;
    public final int themeSwitchDurationMs;
    public final float pressedScale;
    public final float rippleAlpha;
    public final boolean animateThemeSwitch;

    public ThemeMotion(
            int pressDurationMs,
            int themeSwitchDurationMs,
            float pressedScale,
            float rippleAlpha,
            boolean animateThemeSwitch) {
        this.pressDurationMs = pressDurationMs;
        this.themeSwitchDurationMs = themeSwitchDurationMs;
        this.pressedScale = pressedScale;
        this.rippleAlpha = rippleAlpha;
        this.animateThemeSwitch = animateThemeSwitch;
    }

    /** 标准 Material 手感 */
    public static ThemeMotion standard() {
        return new ThemeMotion(90, 220, 0.97f, 0.10f, true);
    }

    /** 干脆：无过渡，适合漫画风 */
    public static ThemeMotion snappy() {
        return new ThemeMotion(0, 0, 1.0f, 0f, false);
    }
}
