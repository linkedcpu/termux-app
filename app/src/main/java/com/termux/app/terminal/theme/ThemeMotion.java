package com.termux.app.terminal.theme;

/** 动效层：皮肤只声明「用哪种效果 + 多强」，算法统一在 ThemeMotionPlayer */
public final class ThemeMotion {

    public enum PressEffect {
        NONE,       // 干脆：不接管、不动画
        ALPHA,      // 透明度（最省、最稳）
        SCALE,      // 缩放
        JELLY,      // 果冻：按下过冲 + 抬起回弹
        STRETCH,    // 拉伸：纵向压扁回弹
        ROTATE,     // 旋转：按下倾斜，抬起归位（小角度摇摆）
        BOUNCE,     // 弹跳：下沉 + 回位
        SPIN,       // 旋转整圈：按下轻微缩小，抬起转 spinDeg 再归零
        RIPPLE      // 波纹（暂降级为 ALPHA，真波纹由 RippleDrawable 承担）
    }

    public final PressEffect pressEffect;
    public final int pressDurationMs;
    public final int themeSwitchDurationMs;
    public final float pressedScale;
    public final float pressedAlpha;
    public final float jellyOvershoot;
    public final float stretchRatio;
    public final float rotateDeg;
    public final float bounceDip;
    public final float spinDeg;
    public final float rippleAlpha;
    public final boolean animateThemeSwitch;

    public ThemeMotion(
            PressEffect pressEffect,
            int pressDurationMs,
            int themeSwitchDurationMs,
            float pressedScale,
            float pressedAlpha,
            float jellyOvershoot,
            float stretchRatio,
            float rotateDeg,
            float bounceDip,
            float spinDeg,
            float rippleAlpha,
            boolean animateThemeSwitch) {
        this.pressEffect = pressEffect == null ? PressEffect.ALPHA : pressEffect;
        this.pressDurationMs = Math.max(0, pressDurationMs);
        this.themeSwitchDurationMs = Math.max(0, themeSwitchDurationMs);
        this.pressedScale = pressedScale <= 0 ? 0.97f : pressedScale;
        this.pressedAlpha = (pressedAlpha <= 0f || pressedAlpha > 1f) ? 0.6f : pressedAlpha;
        this.jellyOvershoot = jellyOvershoot <= 0 ? 1f : jellyOvershoot;
        this.stretchRatio = stretchRatio <= 0 ? 1f : stretchRatio;
        this.rotateDeg = rotateDeg;
        this.bounceDip = bounceDip;
        this.spinDeg = spinDeg == 0f ? 360f : spinDeg;
        this.rippleAlpha = rippleAlpha;
        this.animateThemeSwitch = animateThemeSwitch;
    }

    /** 旧 11 参签名兼容（无 spinDeg）：既有 preset 一行不用改，spinDeg 取 360 */
    public ThemeMotion(
            PressEffect pressEffect,
            int pressDurationMs,
            int themeSwitchDurationMs,
            float pressedScale,
            float pressedAlpha,
            float jellyOvershoot,
            float stretchRatio,
            float rotateDeg,
            float bounceDip,
            float rippleAlpha,
            boolean animateThemeSwitch) {
        this(pressEffect, pressDurationMs, themeSwitchDurationMs, pressedScale,
                pressedAlpha, jellyOvershoot, stretchRatio, rotateDeg,
                bounceDip, 360f, rippleAlpha, animateThemeSwitch);
    }

    /** 旧 5 参签名兼容：既有 preset 一行不用改 */
    public ThemeMotion(
            int pressDurationMs,
            int themeSwitchDurationMs,
            float pressedScale,
            float rippleAlpha,
            boolean animateThemeSwitch) {
        this(PressEffect.ALPHA, pressDurationMs, themeSwitchDurationMs,
                pressedScale, 0.6f, 1.0f, 0.96f, 0f, 0f, 360f, rippleAlpha, animateThemeSwitch);
    }

    public static ThemeMotion standard() {
        return new ThemeMotion(PressEffect.SCALE, 90, 220, 0.97f,
                0.6f, 1.0f, 0.96f, 0f, 0f, 360f, 0.10f, true);
    }

    public static ThemeMotion snappy() {
        return new ThemeMotion(PressEffect.NONE, 0, 0, 1.0f,
                1f, 1.0f, 1.0f, 0f, 0f, 360f, 0f, false);
    }

    public static ThemeMotion jelly() {
        return new ThemeMotion(PressEffect.JELLY, 140, 260, 0.90f,
                0.6f, 1.14f, 0.96f, 0f, 0f, 360f, 0.10f, true);
    }

    public static ThemeMotion stretch() {
        return new ThemeMotion(PressEffect.STRETCH, 110, 220, 0.97f,
                0.6f, 1.0f, 0.88f, 0f, 0f, 360f, 0.10f, true);
    }

    public static ThemeMotion rotate() {
        return new ThemeMotion(PressEffect.ROTATE, 110, 220, 0.97f,
                0.6f, 1.0f, 0.96f, 2.5f, 0f, 360f, 0.10f, true);
    }

    public static ThemeMotion bounce() {
        return new ThemeMotion(PressEffect.BOUNCE, 110, 220, 0.97f,
                0.6f, 1.0f, 0.96f, 0f, 4f, 360f, 0.10f, true);
    }

    /** ★ 旋转整圈：按下轻微缩小，抬起转满 spinDeg（默认 360）再归零 */
    public static ThemeMotion spin() {
        return new ThemeMotion(PressEffect.SPIN, 120, 360, 0.94f,
                0.6f, 1.0f, 0.96f, 0f, 0f, 360f, 0.10f, true);
    }

    /** 转两圈（720°），更夸张 */
    public static ThemeMotion spinDouble() {
        return new ThemeMotion(PressEffect.SPIN, 140, 480, 0.92f,
                0.6f, 1.0f, 0.96f, 0f, 0f, 720f, 0.10f, true);
    }

    public static ThemeMotion alphaOnly() {
        return new ThemeMotion(PressEffect.ALPHA, 90, 220, 1.0f,
                0.6f, 1.0f, 1.0f, 0f, 0f, 360f, 0.10f, true);
    }
}
