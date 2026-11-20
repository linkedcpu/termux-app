package com.termux.app.terminal.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

/**
 * 已解析主题：不可变快照。控件只调语义入口，不做任何推导：
 *   color(角色) / page() / card() / tab() / button() / radius() / stroke()
 *
 * ★ 与 ThemePreset 的哨兵约定：{@link #UNSET} = 未设置，交由本类推导；
 *   {@link ThemePreset#SCRIM_NONE} = 显式无蒙层。
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

    /** 圆角层级基准 dp */
    private static final float
            R_PAGE_DP = 14f, R_CARD_DP = 12f, R_TAB_DP = 10f, R_BTN_DP = 8f;

    public static final float RADIUS_LARGE = R_PAGE_DP;
    public static final float RADIUS_SMALL = R_BTN_DP;

    private static final float TEXTURE_TILE_DP = 24f;

    /** 未设置哨兵：与 ThemePreset.UNSET 同值 */
    static final int UNSET = 0xff000000;

    /* ================= 字段（全不可变） ================= */
    private static final java.util.concurrent.atomic.AtomicLong ID_GEN =
            new java.util.concurrent.atomic.AtomicLong();

    private final long id;
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

    private final int raised, overlay, textSec, textDis, icon, accentSoft, divider, outline, onAccent;

    private Bitmap tileCache;
    private int tileCacheFg = 0xffffffff;
    private int tileCachePx = -1;

    public Theme(ThemePreset p) { this(p, ThemeRenderer.INSTANCE); }

    Theme(ThemePreset p, ThemeRenderer renderer) {
        this.id = ID_GEN.incrementAndGet();
        this.preset = p != null ? p : ThemePreset.ALL[0];
        this.shape = this.preset.shape != null ? this.preset.shape : ThemeShape.classic();
        this.texture = this.preset.texture;
        this.motion = this.preset.motion != null ? this.preset.motion : ThemeMotion.standard();
        this.renderer = renderer != null ? renderer : ThemeRenderer.INSTANCE;

        this.bg = this.preset.bg;
        this.surface = (this.preset.surface != UNSET) ? this.preset.surface : this.preset.bg;
        this.accent = this.preset.accent;

        Boolean fd = this.preset.forceDark;
        boolean translucent = Color.alpha(this.bg) < 0xff;
        this.dark = fd != null ? fd : (translucent || isDark(this.bg));

        int s = this.preset.scrim;
        if (s == UNSET) {
            this.scrim = this.dark ? 0x59000000 : 0xe6000000;
        } else if (s == ThemePreset.SCRIM_NONE) {
            this.scrim = 0x00000000;
        } else {
            this.scrim = s; // 尊重皮肤值（含半透明）
        }

        this.text = this.dark ? 0xffe6e6e6 : ink(this.surface);

        this.raised = mix(this.surface, this.dark ? 0x141414 : 0xf2f2f2, 0.5f);
        this.overlay = mix(this.surface, this.dark ? 0x202020 : 0xe8e8e8, 0.5f);
        this.textSec = alpha(this.text, 0xcc);
        this.textDis = alpha(this.text, 0x66);
        this.icon = alpha(this.text, 0xcc);
        this.accentSoft = alpha(this.accent, this.dark ? 0x33 : 0x22);
        this.divider = alpha(this.text, this.dark ? 0x22 : 0x18);
        this.outline = alpha(this.text, this.dark ? 0x40 : 0x30);
        this.onAccent = inkOn(this.accent);
    }

    public long instanceId() { return id; }

    /* ================= 颜色 ================= */
    public int color(int role) {
        switch (role) {
            case BG:          return bg;
            case SURFACE:     return surface;
            case RAISED:      return raised;
            case OVERLAY:     return overlay;
            case TEXT:        return text;
            case TEXT_SEC:    return textSec;
            case TEXT_DIS:    return textDis;
            case ICON:        return icon;
            case ICON_ACTIVE: return accent;
            case ACCENT:      return accent;
            case ACCENT_SOFT: return accentSoft;
            case ON_ACCENT:   return onAccent;
            case DIVIDER:     return divider;
            case OUTLINE:     return outline;
            case SCRIM:       return scrim;
            case WALLPAPER:   return Color.TRANSPARENT;
            case DANGER:      return 0xffe05050;
            default:          return surface;
        }
    }

    public int color(int role, int state) {
        int base = color(role);
        switch (state) {
            case STATE_SELECTED: return mix(base, accent, dark ? 0.18f : 0.12f);
            case STATE_PRESSED:  return tint(base, dark ? 0xffffff : 0x000000, 0.08f);
            case STATE_DISABLED: return alpha(base, 0x66);
            default:             return base;
        }
    }

    public int textPrimary()   { return text; }
    public int textSecondary() { return textSec; }
    public int accent()        { return accent; }
    public int onAccent()      { return onAccent; }
    public int icon()          { return icon; }
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
    public ThemeShape shape()    { return shape; }

    /* ================= 层级背景 ================= */
    public Drawable page(Context ctx)   { return renderer.layer(this, ctx, ThemeRenderer.LAYER_PAGE, R_PAGE_DP, false); }
    public Drawable card(Context ctx)   { return renderer.layer(this, ctx, ThemeRenderer.LAYER_CARD, R_CARD_DP, false); }
    public Drawable tab(Context ctx)    { return renderer.layer(this, ctx, ThemeRenderer.LAYER_TAB, R_TAB_DP,  false); }
    public Drawable button(Context ctx) { return renderer.layer(this, ctx, ThemeRenderer.LAYER_BTN, R_BTN_DP,  true); }

    public Drawable surface(Context ctx, float radiusBaseDp) {
        return renderer.buildSurface(this, ctx, radiusBaseDp);
    }

    public Drawable button(Context ctx, float radiusBaseDp) {
        if (Math.abs(radiusBaseDp - R_BTN_DP) < 0.01f) return button(ctx);
        return renderer.buildButton(this, ctx, radiusBaseDp);
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
        return renderer.layer(this, ctx, ThemeRenderer.LAYER_ACCENT, R_BTN_DP, false);
    }

    /* ================= 材质 / 动效 ================= */
    public ThemeTexture texture() { return texture; }
    public int textureFg()        { return texture == null ? 0 : alpha(text, dark ? 0x14 : 0x10); }

    public Bitmap textureTile(Context ctx) {
        if (texture == null || ctx == null) return null;
        if (texture.kind() == ThemeTexture.NONE) return null;
        float dp = shape.textureTileDp > 0f ? shape.textureTileDp : TEXTURE_TILE_DP;
        int px = Math.round(dp * density(ctx));
        if (px < 8) px = 8;
        if (px > 4096) px = 4096;
        int fg = textureFg();
        if (tileCache != null && tileCacheFg == fg && tileCachePx == px && !tileCache.isRecycled()) {
            return tileCache;
        }
        tileCache = texture.tile(fg, px);
        tileCacheFg = fg;
        tileCachePx = px;
        return tileCache;
    }

    public ThemeMotion motion() { return motion; }

    /* ================= 状态 ================= */
    public boolean isDark()        { return dark; }
    public boolean isTranslucent() { return Color.alpha(bg) < 0xff; }
    public int     scrimAlpha()    { return scrim; }
    public ThemePreset preset()    { return preset; }

    public void release() {
        if (tileCache != null && !tileCache.isRecycled()) tileCache.recycle();
        tileCache = null;
        renderer.clear(id);
    }

    /* ================= 推导规则 ================= */
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
        return lum(r, g, b) < 0x60;
    }

    static float lum(int r, int g, int b) { return 0.299f * r + 0.587f * g + 0.114f * b; }

    static int mix(int a, int b, float k) {
        int aa = Color.alpha(a), ba = Color.alpha(b);
        int mixedA = (int) (aa + (ba - aa) * k);
        return mixedA << 24
                | ((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * k)) << 16
                | ((int) (Color.green(a) + (Color.green(b) - Color.green(a)) * k)) << 8
                | (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k);
    }

    /** 只调 RGB、保留原 alpha：半透明面板按下不会变实 */
    static int tint(int c, int rgb, float k) {
        return (Color.alpha(c) << 24)
                | ((int) (Color.red(c) + (Color.red(rgb) - Color.red(c)) * k)) << 16
                | ((int) (Color.green(c) + (Color.green(rgb) - Color.green(c)) * k)) << 8
                | (int) (Color.blue(c) + (Color.blue(rgb) - Color.blue(c)) * k);
    }

    static int alpha(int c, int a) { return (c & 0x00ffffff) | (a << 24); }

    static float density(Context ctx) {
        return ctx != null ? ctx.getResources().getDisplayMetrics().density : 1f;
    }
}
