package com.termux.app.terminal.theme;

/**
 * 皮肤的形状档位。控件只读字段，不做推导。
 *
 * <p>★ blurRadiusDp：毛玻璃模糊半径(dp)，0 表示不开。 开不开、糊多少，全由皮肤决定，控件完全不知道毛玻璃的存在。
 */
public final class ThemeShape {

    /** 圆角倍率：1 = 基准圆角，0 = 直角 */
    public final float radiusScale;

    /** 描边宽度(dp)；0 = 无描边 */
    public final float strokeDp;

    /** 按钮是否胶囊形态 */
    public final boolean pillButtons;

    /** ★ 毛玻璃模糊半径(dp)；0 = 关闭 */
    public final float blurRadiusDp;

    /** ★ 材质格子尺寸(dp)；0 = 用 Theme 内置默认 */
    public final float textureTileDp;

    public ThemeShape(float radiusScale, float strokeDp, boolean pillButtons,
                      float blurRadiusDp, float textureTileDp) {
        this.radiusScale = Math.max(0f, radiusScale);
        this.strokeDp = Math.max(0f, strokeDp);
        this.pillButtons = pillButtons;
        this.blurRadiusDp = Math.max(0f, blurRadiusDp);
        this.textureTileDp = Math.max(0f, textureTileDp);
    }

    public ThemeShape(float radiusScale, float strokeDp, boolean pillButtons, float blurRadiusDp) {
        this(radiusScale, strokeDp, pillButtons, blurRadiusDp, 0f);
    }

    public ThemeShape(float radiusScale, float strokeDp, boolean pillButtons) {
        this(radiusScale, strokeDp, pillButtons, 0f, 0f);
    }

    /* ================= 内置档位 ================= */

    /** 经典：中等圆角、细描边、非胶囊、无毛玻璃 */
    public static ThemeShape classic() {
        return new ThemeShape(1.0f, 1f, false, 0f);
    }

    /** 漫画：直角、粗描边、非胶囊、无毛玻璃 */
    public static ThemeShape manga() {
        return new ThemeShape(0.0f, 2.5f, false, 0f);
    }

    /** 扁平：直角、无描边、非胶囊、无毛玻璃 */
    public static ThemeShape flat() {
        return new ThemeShape(0.0f, 0f, false, 0f);
    }

    /** 胶囊：大圆角、无描边、胶囊按钮、无毛玻璃 */
    public static ThemeShape pill() {
        return new ThemeShape(1.6f, 0f, true, 0f);
    }

    /** ★ 毛玻璃档：中等圆角、细描边、非胶囊、开模糊 */
    public static ThemeShape glass(float blurDp) {
        return new ThemeShape(1.0f, 1f, false, blurDp);
    }

    /** ★ 深糊档：大圆角、开重模糊 */
    public static ThemeShape deepGlass(float blurDp) {
        return new ThemeShape(1.4f, 1f, false, blurDp);
    }
}
