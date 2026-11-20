package com.termux.app.terminal.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.LruCache;

/**
 * ★ 主题渲染层：全项目唯一构造 Drawable 的地方。
 *
 * <p>缓存键 = 快照身份(Theme.instanceId) + 层级 + 圆角基准 + 形态。
 * 主题切换 new 新 Theme（新 id），旧快照可通过 {@link #clear(long)} 精确回收。
 */
public final class ThemeRenderer {

    static final ThemeRenderer INSTANCE = new ThemeRenderer();

    private static final int MAX_CACHE_ENTRIES = 64;

    static final char LAYER_PAGE = 'p';
    static final char LAYER_CARD = 'c';
    static final char LAYER_TAB  = 't';
    static final char LAYER_BTN  = 'b';
    static final char LAYER_ACCENT = 'a';

    private final LruCache<String, Drawable> cache = new LruCache<>(MAX_CACHE_ENTRIES);

    private ThemeRenderer() {}

    Drawable layer(Theme t, Context ctx, char layerTag, float radiusBaseDp, boolean asButton) {
        String key = keyOf(t, layerTag, radiusBaseDp, asButton);
        synchronized (cache) {
            Drawable hit = cache.get(key);
            if (hit != null) return copy(hit);
        }
        Drawable d;
        if (asButton) {
            d = buildButton(t, ctx, radiusBaseDp);
        } else if (layerTag == LAYER_ACCENT) {
            d = buildAccent(t, ctx, radiusBaseDp);
        } else {
            d = buildSurface(t, ctx, radiusBaseDp);
        }
        synchronized (cache) {
            cache.put(key, d);
        }
        return copy(d);
    }

    void clear(long instanceId) {
        String prefix = instanceId + "/";
        synchronized (cache) {
            java.util.Iterator<String> it = cache.snapshot().keySet().iterator();
            while (it.hasNext()) {
                String k = it.next();
                if (k.startsWith(prefix)) cache.remove(k);
            }
        }
    }

    void clearAll() {
        synchronized (cache) { cache.evictAll(); }
    }

    private static String keyOf(Theme t, char layerTag, float radiusBaseDp, boolean asButton) {
        return t.instanceId() + "/" + layerTag + "/" + radiusBaseDp + "/" + (asButton ? 1 : 0);
    }

    private static Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable() : d;
    }

    /* ================= 构造实现 ================= */

    Drawable buildSurface(Theme t, Context ctx, float radiusBaseDp) {
        GradientDrawable g = solid(t, ctx, radiusBaseDp, t.color(Theme.SURFACE));
        Bitmap tile = t.textureTile(ctx);
        if (tile == null) return g;
        return new LayerDrawable(new Drawable[] {g, tiled(ctx, tile)});
    }

    Drawable buildButton(Theme t, Context ctx, float radiusBaseDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(t.color(Theme.SURFACE, Theme.STATE_PRESSED));
        g.setCornerRadius(corner(t, ctx, radiusBaseDp));
        int sw = t.stroke(ctx);
        if (sw > 0) g.setStroke(sw, t.color(Theme.OUTLINE));
        Bitmap tile = t.textureTile(ctx);
        if (tile == null) return g;
        return new LayerDrawable(new Drawable[] {g, tiled(ctx, tile)});
    }

    private Drawable buildAccent(Theme t, Context ctx, float radiusBaseDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(t.accent());
        g.setCornerRadius(corner(t, ctx, radiusBaseDp));
        Bitmap tile = t.textureTile(ctx);
        if (tile == null) return g;
        return new LayerDrawable(new Drawable[] {g, tiled(ctx, tile)});
    }

    private static float corner(Theme t, Context ctx, float radiusBaseDp) {
        return t.pillButtons() ? 100f * Theme.density(ctx) : t.radius(ctx, radiusBaseDp);
    }

    private static GradientDrawable solid(Theme t, Context ctx, float radiusBaseDp, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(t.radius(ctx, radiusBaseDp));
        int sw = t.stroke(ctx);
        if (sw > 0) g.setStroke(sw, t.color(Theme.OUTLINE));
        return g;
    }

    private static BitmapDrawable tiled(Context ctx, Bitmap tile) {
        BitmapDrawable td = new BitmapDrawable(ctx.getResources(), tile);
        td.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return td;
    }
}
