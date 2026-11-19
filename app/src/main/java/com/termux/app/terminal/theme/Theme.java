package com.termux.app.terminal.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/**
 * 已解析主题：不可变快照。控件只调语义入口，不做任何推导：
 *   color(角色) / page() / card() / tab() / button() / radius() / stroke()
 *
 * ★ 理论最优形态：
 *   1. 解析与渲染分离 —— 本类只持「结论」（色值/明暗/动效），Drawable 构造交给 ThemeRenderer；
 *   2. 层级基准收口 —— R_PAGE/CARD/TAB/BTN 私有常量，皮肤只调 radiusScale；
 *   3. 渲染缓存 —— 同快照下同名层级的 Drawable 复用，滚动不重复构造；
 *   4. 明暗可覆盖 —— preset.forceDark 兜底全透明主题；
 *   5. 无壁纸维度 —— dark/scrim/text 只由 preset 决定，单一维度、无 Bitmap 生命周期。
 */
public final class Theme {

    /* ================= 颜色角色 ================= */

    public static final int
            BG = 0, SURFACE = 1, RAISED = 2, OVERLAY = 3,
            TEXT = 4, TEXT_SEC = 5, TEXT_DIS = 6,
            ICON = 7, ICON_ACTIVE = 8,
            ACCENT = 9, ACCENT_SOFT = 10, ON_ACCENT = 11,
            DIVIDER = 12, OUTLINE = 13,
            WALLPAPER = 14, SCRIM = 15,
            DANGER = 16;

    /* ================= 状态 ================= */

    public static final int
            STATE_NONE = 0, STATE_SELECTED = 1, STATE_PRESSED = 2, STATE_DISABLED = 3;

    /** 圆角层级基准 dp：控件只说「我是哪一层」，皮肤只给倍率 */
    private static final float
            R_PAGE_DP = 14f, R_CARD_DP = 12f, R_TAB_DP = 10f, R_BTN_DP = 8f;

    /**
     * 公开圆角别名：控件若想自己挑档位（如 pill 切换）用这两个，
     * 常规场景仍走 page()/card()/tab()/button() 无参入口。
     */
    public static final float RADIUS_LARGE = R_PAGE_DP;
    public static final float RADIUS_SMALL = R_BTN_DP;

    /** 材质格子基准尺寸(dp)，皮肤可用 ThemeShape.textureTileDp 覆盖 */
    private static final float TEXTURE_TILE_DP = 24f;

    /* ================= 字段（全不可变） ================= */

    private final ThemePreset preset;
    private final ThemeShape shape;
    private final ThemeTexture texture;
    private final ThemeMotion motion;
    private final ThemeRenderer renderer;

    private final boolean dark;
    private final int bg;
    private final int surface;
    private final int accent;
    private final int text;
    private final int scrim;

    public Theme(ThemePreset p) {
        this(p, ThemeRenderer.INSTANCE);
    }

    /** 测试/替换渲染实现用；常规调用走单参构造 */
    Theme(ThemePreset p, ThemeRenderer renderer) {
        this.preset = p != null ? p : ThemePreset.ALL[0];
        this.shape = this.preset.shape != null ? this.preset.shape : ThemeShape.classic();
        this.texture = this.preset.texture;
        this.motion = this.preset.motion != null ? this.preset.motion : ThemeMotion.standard();
        this.renderer = renderer != null ? renderer : ThemeRenderer.INSTANCE;

        this.bg = this.preset.bg;
        this.surface = this.preset.surface != 0 ? this.preset.surface : this.preset.bg;
        this.accent = this.preset.accent;

        int s = this.preset.scrim;
        this.scrim = s == 0 ? (isDark(this.bg) ? 0x59000000 : 0xe6000000) : (s | 0xff000000);

        // ★ 明暗：preset.forceDark 可显式覆盖，兜底全透明/半透明主题
        Boolean fd = this.preset.forceDark;
        this.dark = fd != null ? fd : isDark(this.bg);

        this.text = this.dark ? 0xffe6e6e6 : ink(this.surface);
    }

    /* ================= 颜色 ================= */

    public int color(int role) {
        switch (role) {
            case BG:          return bg;
            case SURFACE:     return surface;
            case RAISED:      return mix(surface, dark ? 0x141414 : 0xf2f2f2, 0.5f);
            case OVERLAY:     return mix(surface, dark ? 0x202020 : 0xe8e8e8, 0.5f);
            case TEXT:        return text;
            case TEXT_SEC:    return alpha(text, 0xcc);
            case TEXT_DIS:    return alpha(text, 0x66);
            case ICON:        return alpha(text, 0xcc);
            case ICON_ACTIVE: return accent;
            case ACCENT:      return accent;
            case ACCENT_SOFT: return alpha(accent, dark ? 0x33 : 0x22);
            case ON_ACCENT:   return inkOn(accent);
            case DIVIDER:     return alpha(text, dark ? 0x22 : 0x18);
            case OUTLINE:     return alpha(text, dark ? 0x40 : 0x30);
            case SCRIM:       return scrim;
            case WALLPAPER:   return 0;  // @deprecated 壁纸已移除，恒透明
            case DANGER:      return 0xffe05050;
            default:          return surface;
        }
    }

    public int color(int role, int state) {
        int base = color(role);
        switch (state) {
            case STATE_SELECTED: return mix(base, accent, dark ? 0.18f : 0.12f);
            case STATE_PRESSED:  return mix(base, dark ? 0xffffff : 0x000000, 0.08f);
            case STATE_DISABLED: return alpha(base, 0x66);
            default:             return base;
        }
    }

    /** 语义颜色快捷方法：控件最常引用的四个 */
    public int textPrimary()   { return text; }
    public int textSecondary() { return color(TEXT_SEC); }
    public int accent()        { return accent; }
    public int onAccent()      { return inkOn(accent); }
    public int icon()          { return color(ICON); }
    public int iconActive()    { return accent; }

    /* ================= 形状 ================= */

    public float radius(Context ctx, float baseDp) {
        float dp = baseDp * shape.radiusScale;
        if (dp < 0) dp = 0;
        return dp * density(ctx);
    }

    public int stroke(Context ctx) {
        float dp = shape.strokeDp;
        if (dp <= 0) return 0;
        return Math.round(dp * density(ctx));
    }

    public boolean pillButtons() { return shape.pillButtons; }

    public ThemeShape shape() { return shape; }

    /* ================= 层级背景（无参语义入口，带缓存） ================= */

    public Drawable page(Context ctx)   { return renderer.layer(this, ctx, 'p', R_PAGE_DP, false); }
    public Drawable card(Context ctx)   { return renderer.layer(this, ctx, 'c', R_CARD_DP, false); }
    public Drawable tab(Context ctx)    { return renderer.layer(this, ctx, 't', R_TAB_DP,  false); }
    public Drawable button(Context ctx) { return renderer.layer(this, ctx, 'b', R_BTN_DP,  true); }

    /** 逃逸口：自定义 View 需要非标准圆角时用；不入缓存 */
    public Drawable surface(Context ctx, float radiusBaseDp) {
        return renderer.buildSurface(this, ctx, radiusBaseDp);
    }

    /* ---- 兼容重载：旧调用点仍传 (Context, radiusDp)，转发到无参/逃逸口 ---- */

    public Drawable button(Context ctx, float radiusBaseDp) {
        return Math.abs(radiusBaseDp - R_BTN_DP) < 0.01f
                ? button(ctx) : renderer.buildSurface(this, ctx, radiusBaseDp);
    }

    public Drawable panel(Context ctx, float radiusBaseDp) {
        return Math.abs(radiusBaseDp - R_PAGE_DP) < 0.01f
                ? page(ctx) : renderer.buildSurface(this, ctx, radiusBaseDp);
    }

    public Drawable card(Context ctx, float radiusBaseDp) {
        return Math.abs(radiusBaseDp - R_CARD_DP) < 0.01f
                ? card(ctx) : renderer.buildSurface(this, ctx, radiusBaseDp);
    }

    public Drawable tab(Context ctx, float radiusBaseDp) {
        return Math.abs(radiusBaseDp - R_TAB_DP) < 0.01f
                ? tab(ctx) : renderer.buildSurface(this, ctx, radiusBaseDp);
    }

    public Drawable accentBg(Context ctx) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(accent);
        g.setCornerRadius(radius(ctx, R_BTN_DP));
        return g;
    }

    /* ================= 材质 / 动效 ================= */

    public ThemeTexture texture() { return texture; }

    public int textureFg() {
        return texture == null ? 0 : alpha(text, dark ? 0x14 : 0x10);
    }

    /** 材质小图（已按 textureTileDp × density 生成），平铺由渲染层处理 */
    public Bitmap textureTile(Context ctx) {
        if (texture == null || ctx == null) return null;
        if (texture.kind() == ThemeTexture.NONE) return null;
        float dp = shape.textureTileDp > 0f ? shape.textureTileDp : TEXTURE_TILE_DP;
        int px = Math.round(dp * density(ctx));
        if (px < 8) px = 8;
        if (px > 4096) px = 4096;
        return texture.tile(textureFg(), px);
    }

    public ThemeMotion motion() { return motion; }

    /* ================= 状态查询 ================= */

    public boolean isDark()        { return dark; }
    public boolean isTranslucent() { return Color.alpha(bg) < 0xff; }
    public int     scrimAlpha()    { return scrim; }
    public ThemePreset preset()    { return preset; }

    /** 兼容旧调用方；无壁纸实现下为空操作 */
    public void release() { }

    /* ================= 推导规则（包级可见，供渲染层复用） ================= */

    static int ink(int surface) {
        float k = 0.14f, t = 0x08;
        return 0xff000000
                | ((int) (Color.red(surface) * k + t * (1 - k))) << 16
                | ((int) (Color.green(surface) * k + t * (1 - k))) << 8
                | (int) (Color.blue(surface) * k + t * (1 - k));
    }

    static int inkOn(int accent) {
        return lum(Color.red(accent), Color.green(accent), Color.blue(accent)) < 0x60
                ? 0xffffffff : 0xff1f2328;
    }

    static boolean isDark(int c) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        int a = Color.alpha(c);
        if (a != 0xff) {
            float k = a / 255f;
            r = (int) (r * k + 0x14 * (1 - k));
            g = (int) (g * k + 0x14 * (1 - k));
            b = (int) (b * k + 0x14 * (1 - k));
        }
        // alpha=0 时合成到 0x14 灰底，避免透明/全透明主题恒判深色
        return lum(r, g, b) < 0x60;
    }

    static float lum(int r, int g, int b) {
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    /** ★ 保留源 alpha：半透明色派生不再丢透明度 */
    static int mix(int a, int b, float k) {
        int aa = Color.alpha(a), ba = Color.alpha(b);
        int mixedA = (int) (aa + (ba - aa) * k);
        return mixedA << 24
                | ((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * k)) << 16
                | ((int) (Color.green(a) + (Color.green(b) - Color.green(a)) * k)) << 8
                | (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k);
    }

    static int alpha(int c, int a) {
        return (c & 0x00ffffff) | (a << 24);
    }

    static float density(Context ctx) {
        return ctx != null ? ctx.getResources().getDisplayMetrics().density : 1f;
    }
}
