package com.termux.app.terminal.theme;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;

/** 材质层：底色之上再铺一层纹理。纯色最省，点阵/网格/斜纹程序化生成，不占资源 */
public abstract class ThemeTexture {

    public static final int NONE = 0, DOT = 1, GRID = 2, DIAGONAL = 3;

    public abstract int kind();

    public abstract float alpha();

    public static ThemeTexture none() {
        return new ThemeTexture() {
            @Override public int kind() { return NONE; }
            @Override public float alpha() { return 0f; }
        };
    }

    public static ThemeTexture dot(float alpha) {
        return new Pattern(DOT, alpha);
    }

    public static ThemeTexture grid(float alpha) {
        return new Pattern(GRID, alpha);
    }

    public static ThemeTexture diagonal(float alpha) {
        return new Pattern(DIAGONAL, alpha);
    }

    private static final class Pattern extends ThemeTexture {
        private final int mKind;
        private final float mAlpha;

        Pattern(int kind, float alpha) {
            mKind = kind;
            mAlpha = alpha;
        }

        @Override public int kind() { return mKind; }
        @Override public float alpha() { return mAlpha; }
    }

    /** 生成平铺小图；纹路色由调用方按前景色 + alpha 决定 */
    public Bitmap tile(int fgColor, int sizePx) {
        if (kind() == NONE || sizePx <= 0) return null;
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        int a = Math.max(0, Math.min(255, Math.round(alpha() * 255)));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor((fgColor & 0x00ffffff) | (a << 24));
        p.setStrokeWidth(Math.max(1f, sizePx / 14f));
        switch (kind()) {
            case DOT:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 7f, p);
                break;
            case GRID:
                p.setStyle(Paint.Style.STROKE);
                c.drawRect(0, 0, sizePx, sizePx, p);
                break;
            case DIAGONAL:
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(0, sizePx, sizePx, 0, p);
                c.drawLine(0, 0, sizePx, sizePx, p);
                break;
            default:
                return null;
        }
        return bmp;
    }

    /** 平铺着色器；纯色返回 null */
    public Shader shader(int fgColor, int tilePx) {
        Bitmap b = tile(fgColor, tilePx);
        if (b == null) return null;
        return new android.graphics.BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }
}
