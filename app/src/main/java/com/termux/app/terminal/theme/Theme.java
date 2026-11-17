package com.termux.app.terminal.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import java.util.ArrayList;

/**
 * 已解析主题：不可变快照。控件只调语义入口，不做任何推导：
 *   color(角色) / panel() / surface() / button() / radius() / stroke() / pillButtons()
 *
 * ★ 全项目唯一的推导、壁纸态分支、毛玻璃实现都在这里。控件不判壁纸、不判亮度、不混色。
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

    /** 圆角层级基准由控件给，皮肤只给倍率 */
    public static final float RADIUS_NONE = 0f, RADIUS_SMALL = 6f, RADIUS_MEDIUM = 12f, RADIUS_LARGE = 20f;

    /** ★ 修复：材质格子基准尺寸(dp)，皮肤可用 ThemeShape.textureTileDp 覆盖 */
    private static final float TEXTURE_TILE_DP = 24f;

    /* ================= 字段 ================= */

    private final ThemePreset preset;
    private final ThemeShape shape;
    private final ThemeTexture texture;
    private final ThemeMotion motion;

    private final boolean dark;
    private final int bg;
    private final int surface;
    private final int accent;
    private final int text;
    private int scrim;
    private final String wallpaper;

    /** ★ 壁纸解码缓存：只在本类内部流转，控件永远拿不到 Bitmap */
    private Bitmap wallpaperBitmap;

    public Theme(ThemePreset p, String wallpaper) {
        this.preset = p != null ? p : ThemePreset.ALL[0];
        this.shape = this.preset.shape != null ? this.preset.shape : ThemeShape.classic();
        this.texture = this.preset.texture;
        this.motion = this.preset.motion != null ? this.preset.motion : ThemeMotion.standard();
        this.wallpaper = wallpaper;

        this.bg = this.preset.bg;
        this.surface = this.preset.surface != 0 ? this.preset.surface : this.preset.bg;
        this.accent = this.preset.accent;

        int s = this.preset.scrim;
        this.scrim = s == 0 ? (isDark(this.bg) ? 0x59000000 : 0xe6000000) : (s | 0xff000000);

        // ★ 唯一的壁纸态分支：亮度与 SCRIM 按壁纸「左上 1/4」判定
        boolean hasWp = wallpaper != null && !wallpaper.isEmpty();
        if (hasWp) {
            Bitmap wp = decodeScaled(wallpaper, 1080, 1920);
            if (wp != null) {
                this.wallpaperBitmap = wp;
                this.dark = isDark(avgColorLeftTop(wp));
                this.scrim = this.dark ? 0x59000000 : 0xa6000000;
            } else {
                this.dark = isDark(this.bg);
            }
        } else {
            this.dark = isDark(this.bg);
        }
        this.text = this.dark ? 0xffe6e6e6 : ink(this.surface);
    }

    public Theme(ThemePreset p) { this(p, null); }

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
            case WALLPAPER:   return 0;
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

    /* ================= 绘制入口 ================= */

    /**
     * ★ 根背景：
     *   壁纸态 → [毛玻璃]壁纸 → SCRIM → 描边层
     *   纯色态 → 不透明面板 → 材质 → 描边层   ← 材质必须在面板之上，否则被完全遮住
     */
    public Drawable panel(Context ctx, float radiusBaseDp) {
        if (wallpaperBitmap != null) {
            ArrayList<Drawable> layers = new ArrayList<>();
            if (blurEnabled()) {
                layers.add(blurredWallpaper(ctx, 1.0f, 4));
            } else {
                BitmapDrawable wd = new BitmapDrawable(ctx.getResources(), wallpaperBitmap);
                wd.setTileModeXY(Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                layers.add(wd);
            }
            layers.add(new ColorDrawable(scrim));
            layers.add(outlineOnly(ctx, radiusBaseDp));
            return new LayerDrawable(layers.toArray(new Drawable[0]));
        }
        ArrayList<Drawable> layers = new ArrayList<>();
        layers.add(solidSurface(ctx, radiusBaseDp));          // 底：不透明面板
        Bitmap tile = textureTile(ctx);                       // ★ 修复
        if (tile != null) layers.add(tileDrawable(ctx, tile)); // ★ 修复：材质压在面板上
        layers.add(outlineOnly(ctx, radiusBaseDp));           // 顶：描边
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    /** ★ 页面/卡片表面：壁纸态半透明 + 轻模糊，纯色态不透明面板 + 材质 */
    public Drawable surface(Context ctx, float radiusBaseDp) {
        if (wallpaperBitmap != null) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setColor(alpha(surface, dark ? 0x59 : 0xa6));
            g.setCornerRadius(radius(ctx, radiusBaseDp));
            int sw = stroke(ctx);
            if (sw > 0) g.setStroke(sw, color(OUTLINE));
            if (blurEnabled()) {
                return new LayerDrawable(new Drawable[]{
                        blurredWallpaper(ctx, 0.5f, 6), g});
            }
            return g;
        }
        ArrayList<Drawable> layers = new ArrayList<>();       // ★ 修复
        layers.add(solidSurface(ctx, radiusBaseDp));
        Bitmap tile = textureTile(ctx);
        if (tile != null) layers.add(tileDrawable(ctx, tile));
        if (stroke(ctx) > 0) layers.add(outlineOnly(ctx, radiusBaseDp));
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    /** ★ 按钮背景：形态与透明度由皮肤决定 */
    public Drawable button(Context ctx, float radiusBaseDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        if (wallpaperBitmap != null) {
            g.setColor(dark ? 0x33ffffff : 0x22000000);
        } else {
            g.setColor(color(SURFACE, STATE_PRESSED));
        }
        // ★ 修复：纯色态也尊重 pillButtons()，原先只在壁纸态生效
        if (pillButtons()) {
            g.setCornerRadius(100f * density(ctx));
        } else {
            g.setCornerRadius(radius(ctx, radiusBaseDp));
        }
        int sw = stroke(ctx);
        if (sw > 0) g.setStroke(sw, color(OUTLINE));
        Bitmap tile = textureTile(ctx);                       // ★ 修复：按钮也铺材质
        if (tile == null) return g;
        return new LayerDrawable(new Drawable[]{g, tileDrawable(ctx, tile)});
    }

    /** ★ 修复：材质小图必须平铺，否则被拉伸成糊图 */
    private static BitmapDrawable tileDrawable(Context ctx, Bitmap tile) {
        BitmapDrawable td = new BitmapDrawable(ctx.getResources(), tile);
        td.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return td;
    }

    private Drawable solidSurface(Context ctx, float radiusBaseDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color(SURFACE));
        g.setCornerRadius(radius(ctx, radiusBaseDp));
        int sw = stroke(ctx);
        if (sw > 0) g.setStroke(sw, color(OUTLINE));
        return g;
    }

    private Drawable outlineOnly(Context ctx, float radiusBaseDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(0x00000000);
        g.setCornerRadius(radius(ctx, radiusBaseDp));
        int sw = stroke(ctx);
        if (sw > 0) g.setStroke(sw, color(OUTLINE));
        return g;
    }

    /* ================= 毛玻璃 ================= */

    /** 皮肤决定是否开毛玻璃：blurRadiusDp > 0 即开启 */
    private boolean blurEnabled() {
        return shape.blurRadiusDp > 0f && wallpaperBitmap != null && !wallpaperBitmap.isRecycled();
    }

    /**
     * ★ 毛玻璃层：壁纸降采样 → BlurMaskFilter 模糊 → 放大回原尺寸。
     *   scale 控制模糊强度倍率（根背景 1.0，页面 0.5），
     *   div 控制降采样分母（越大越快越糊）。
     */
    private Drawable blurredWallpaper(Context ctx, float scale, int div) {
        Bitmap src = wallpaperBitmap;
        int radius = Math.round(shape.blurRadiusDp * scale * density(ctx));
        if (radius < 1) radius = 1;
        if (div < 2) div = 2;
        int sw = Math.max(1, src.getWidth() / div);
        int sh = Math.max(1, src.getHeight() / div);
        Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, false);
        Bitmap out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        p.setMaskFilter(new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL));
        c.drawBitmap(small, 0, 0, p);
        small.recycle();
        BitmapDrawable d = new BitmapDrawable(ctx.getResources(), out);
        d.setTileModeXY(Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        return d;
    }

    /* ================= 材质 / 动效 ================= */

    public ThemeTexture texture() { return texture; }

    public int textureFg() {
        return texture == null ? 0 : alpha(text, dark ? 0x14 : 0x10);
    }

    /** ★ 修复：原先误把 bg 颜色当尺寸传给 tile()，导致材质永远不显示 */
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
    public boolean hasWallpaper()  { return wallpaper != null && !wallpaper.isEmpty(); }
    public String  wallpaperPath() { return wallpaper; }
    public int     scrimAlpha()    { return scrim; }
    public ThemePreset preset()    { return preset; }

    public Bitmap decodeWallpaper(String path) {
        if (wallpaperBitmap != null) return wallpaperBitmap;
        return decodeScaled(path, 1080, 1920);
    }

    /** 释放壁纸缓存；主题切换或销毁时调用 */
    public void release() {
        if (wallpaperBitmap != null && !wallpaperBitmap.isRecycled()) {
            wallpaperBitmap.recycle();
        }
        wallpaperBitmap = null;
    }

    private static Bitmap decodeScaled(String path, int maxW, int maxH) {
        if (path == null || path.isEmpty()) return null;
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, probe);
            int w = probe.outWidth, h = probe.outHeight;
            if (w <= 0 || h <= 0) return null;
            int sample = 1;
            while (w / sample > maxW || h / sample > maxH) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, d);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int avgColorLeftTop(Bitmap bmp) {
        try {
            Bitmap s = Bitmap.createScaledBitmap(bmp, 8, 8, false);
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    int c = s.getPixel(x, y);
                    r += Color.red(c);
                    g += Color.green(c);
                    b += Color.blue(c);
                    n++;
                }
            }
            s.recycle();
            if (n == 0) return 0xff000000;
            return 0xff000000
                    | ((int) (r / n)) << 16
                    | ((int) (g / n)) << 8
                    | (int) (b / n);
        } catch (Throwable ignored) {
            return 0xff000000;
        }
    }

    /* ================= 推导规则 ================= */

    private static int ink(int surface) {
        float k = 0.14f, t = 0x08;
        return 0xff000000
                | ((int) (Color.red(surface) * k + t * (1 - k))) << 16
                | ((int) (Color.green(surface) * k + t * (1 - k))) << 8
                | (int) (Color.blue(surface) * k + t * (1 - k));
    }

    private static int inkOn(int accent) {
        return lum(Color.red(accent), Color.green(accent), Color.blue(accent)) < 0x60
                ? 0xffffffff : 0xff1f2328;
    }

    private static boolean isDark(int c) {
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

    private static float lum(int r, int g, int b) {
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    private static int mix(int a, int b, float k) {
        return 0xff000000
                | ((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * k)) << 16
                | ((int) (Color.green(a) + (Color.green(b) - Color.green(a)) * k)) << 8
                | (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k);
    }

    static int alpha(int c, int a) {
        return (c & 0x00ffffff) | (a << 24);
    }

    private static float density(Context ctx) {
        return ctx != null ? ctx.getResources().getDisplayMetrics().density : 1f;
    }
}
