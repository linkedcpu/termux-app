package com.termux.app.terminal.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ★ 主题渲染层：全项目唯一构造 Drawable 的地方。
 *
 * <p>与 {@link Theme} 的分工： Theme —— 不可变数据快照（色值/明暗/形状/材质/动效），不含 Drawable 构造逻辑； ThemeRenderer ——
 * 纯渲染，按层级语义产出 Drawable，并负责复用缓存。
 *
 * <p>缓存键 = 快照身份(Theme 实例) + 层级 + 圆角基准 + 尺寸无关的形状参数。 Theme 不可变 ⇒ 同实例同层级必同结果 ⇒ 可安全缓存；主题切换会 new
 * Theme，缓存自然失效。
 */
public final class ThemeRenderer {

    static final ThemeRenderer INSTANCE = new ThemeRenderer();

    /** 上限保护：单快照最多缓存若干层级，避免异常调用撑爆内存 */
    private static final int MAX_CACHE_ENTRIES = 64;

    private final Map<String, Drawable> cache = new ConcurrentHashMap<>();

    private ThemeRenderer() {}

    /**
     * 取（或构造）层级背景。
     *
     * @param layerTag 层级标识：'p'=page 'c'=card 't'=tab 'b'=button
     * @param radiusBaseDp 该层级圆角基准（Theme 内私有常量传入，控件不可见）
     * @param asButton true 走按钮形态（胶囊皮肤生效）
     */
    Drawable layer(Theme t, Context ctx, char layerTag, float radiusBaseDp, boolean asButton) {
        String key = t.hashCode() + "/" + layerTag;
        Drawable d = cache.get(key);
        if (d != null) return d;
        d = asButton ? buildButton(t, ctx, radiusBaseDp) : buildSurface(t, ctx, radiusBaseDp);
        if (cache.size() < MAX_CACHE_ENTRIES) {
            cache.put(key, d);
        }
        return d;
    }

    /* ================= 构造实现 ================= */

    /** 面板/卡片/标签：实色面板 → 材质 → 描边 */
    Drawable buildSurface(Theme t, Context ctx, float radiusBaseDp) {
        GradientDrawable g = solid(t, ctx, radiusBaseDp, t.color(Theme.SURFACE));
        Bitmap tile = t.textureTile(ctx);
        if (tile == null) return g;
        return new LayerDrawable(new Drawable[] {g, tiled(ctx, tile)});
    }

    /** 按钮：按压态底色 + 胶囊/圆角 + 描边 + 材质 */
    private Drawable buildButton(Theme t, Context ctx, float radiusBaseDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(t.color(Theme.SURFACE, Theme.STATE_PRESSED));
        if (t.pillButtons()) {
            g.setCornerRadius(100f * Theme.density(ctx));
        } else {
            g.setCornerRadius(t.radius(ctx, radiusBaseDp));
        }
        int sw = t.stroke(ctx);
        if (sw > 0) g.setStroke(sw, t.color(Theme.OUTLINE));
        Bitmap tile = t.textureTile(ctx);
        if (tile == null) return g;
        return new LayerDrawable(new Drawable[] {g, tiled(ctx, tile)});
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
