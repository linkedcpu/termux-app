package com.termux.app.terminal.theme;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * 材质：皮肤给一个 kind + 浓度，控件完全不知道它的存在。
 *
 * <p>所有材质都生成一张 sizePx x sizePx 的<b>可无缝平铺</b>小图，由
 * Theme.tileDrawable() 以 REPEAT 模式铺满面板。新增材质只需：
 *   1) 加一个 kind 常量  2) 加一个工厂方法  3) 在 tile() 的 switch 里加一个 case
 */
public final class ThemeTexture {

    /* ================= 材质种类 ================= */

    public static final int NONE = 0;
    /** 圆点阵 */
    public static final int DOT = 1;
    /** 方格网 */
    public static final int GRID = 2;
    /** 45 度单斜线 */
    public static final int DIAGONAL = 3;
    /** 棋盘格 */
    public static final int CHECKER = 4;
    /** 细噪点（确定性伪随机，不闪烁） */
    public static final int NOISE = 5;
    /** 同心圆环阵 */
    public static final int CIRCLES = 6;
    /** 砖墙错缝 */
    public static final int BRICK = 7;
    /** 加号阵 */
    public static final int CROSS = 8;
    /** 双向交叉影线 */
    public static final int HASH = 9;
    /** 网格交点带圆点（点格纸） */
    public static final int DOTGRID = 10;
    /** 水平正弦波纹 */
    public static final int WAVE = 11;
    /** 等边三角网 */
    public static final int TRIANGLE = 12;

    /** 材质种数（不含 NONE），供设置页遍历 */
    public static final int KIND_COUNT = 12;

    private final int kind;
    private final float strength;

    private ThemeTexture(int kind, float strength) {
        this.kind = kind;
        this.strength = Math.max(0f, Math.min(1f, strength));
    }

    /* ================= 工厂 ================= */

    public static ThemeTexture none()      { return new ThemeTexture(NONE, 0f); }
    public static ThemeTexture dot(float s)       { return new ThemeTexture(DOT, s); }
    public static ThemeTexture grid(float s)      { return new ThemeTexture(GRID, s); }
    public static ThemeTexture diagonal(float s)  { return new ThemeTexture(DIAGONAL, s); }
    public static ThemeTexture checker(float s)   { return new ThemeTexture(CHECKER, s); }
    public static ThemeTexture noise(float s)     { return new ThemeTexture(NOISE, s); }
    public static ThemeTexture circles(float s)   { return new ThemeTexture(CIRCLES, s); }
    public static ThemeTexture brick(float s)     { return new ThemeTexture(BRICK, s); }
    public static ThemeTexture cross(float s)     { return new ThemeTexture(CROSS, s); }
    public static ThemeTexture hash(float s)      { return new ThemeTexture(HASH, s); }
    public static ThemeTexture dotgrid(float s)   { return new ThemeTexture(DOTGRID, s); }
    public static ThemeTexture wave(float s)      { return new ThemeTexture(WAVE, s); }
    public static ThemeTexture triangle(float s)  { return new ThemeTexture(TRIANGLE, s); }

    public int kind() { return kind; }
    public float strength() { return strength; }

    /** 可读性名称，设置页直接展示 */
    public static String nameOf(int kind) {
        switch (kind) {
            case DOT:       return "点阵";
            case GRID:      return "网格";
            case DIAGONAL:  return "斜纹";
            case CHECKER:   return "棋盘";
            case NOISE:     return "噪点";
            case CIRCLES:   return "环阵";
            case BRICK:     return "砖墙";
            case CROSS:     return "加号阵";
            case HASH:      return "影线";
            case DOTGRID:   return "点格";
            case WAVE:      return "波纹";
            case TRIANGLE:  return "三角网";
            default:        return "无";
        }
    }

    /* ================= 生成 ================= */

    /**
     * 生成一张 sizePx x sizePx 的可平铺小图。
     *
     * @param fgColor 材质前景色（已含浓度 alpha），由 Theme.textureFg() 给出
     * @param sizePx  <b>真实像素尺寸</b>；传颜色值会导致负尺寸返回 null 或巨图 OOM
     * @return 材质小图；NONE 或参数非法时返回 null
     */
    public Bitmap tile(int fgColor, int sizePx) {
        if (kind == NONE || sizePx <= 0) return null;
        if (sizePx > 4096) return null;   // 防御：挡住误传颜色值造成的巨图/OOM

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(fgColor);
        final float s = sizePx;

        switch (kind) {
            case DOT:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(s / 2f, s / 2f, stroke(s, 0.16f, strength), p);
                break;

            case GRID:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke(s, 0.04f, strength));
                c.drawRect(0, 0, s, s, p);
                break;

            case DIAGONAL:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke(s, 0.06f, strength));
                c.drawLine(0, s, s, 0, p);
                break;

            case CHECKER:
                p.setStyle(Paint.Style.FILL);
                c.drawRect(0, 0, s / 2f, s / 2f, p);
                c.drawRect(s / 2f, s / 2f, s, s, p);
                break;

            case NOISE: {
                p.setStyle(Paint.Style.FILL);
                int n = Math.max(8, (int) (s * s * 0.10f * (0.4f + strength)));
                long seed = 0x9E3779B97F4A7C15L ^ (long) sizePx;
                float r = Math.max(0.4f, s * 0.012f);
                for (int i = 0; i < n; i++) {
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    float x = ((seed >>> 33) & 0x7fff) / 32767f * s;
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    float y = ((seed >>> 33) & 0x7fff) / 32767f * s;
                    c.drawRect(x, y, x + r, y + r, p);
                }
                break;
            }

            case CIRCLES:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke(s, 0.05f, strength));
                c.drawCircle(s / 2f, s / 2f, s * 0.34f, p);
                if (strength > 0.5f) {
                    c.drawCircle(s / 2f, s / 2f, s * 0.16f, p);
                }
                break;

            case BRICK:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke(s, 0.05f, strength));
                c.drawLine(0, 0, s, 0, p);                   // 上边横缝
                c.drawLine(0, s / 2f, s / 2f, s / 2f, p);   // 左半砖下缝
                c.drawLine(s / 2f, 0, s / 2f, s / 2f, p);   // 左砖竖缝
                c.drawLine(0, 0, 0, s, p);                  // 左边缝，保证上下贯通
                break;

            case CROSS:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke(s, 0.05f, strength));
                c.drawLine(s / 2f, s * 0.28f, s / 2f, s * 0.72f, p);
                c.drawLine(s * 0.28f, s / 2f, s * 0.72f, s / 2f, p);
                break;

            case HASH:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke(s, 0.045f, strength));
                c.drawLine(0, s, s, 0, p);
                c.drawLine(0, 0, s, s, p);
                break;

            case DOTGRID:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke(s, 0.035f, strength));
                c.drawRect(0, 0, s, s, p);
                p.setStyle(Paint.Style.FILL);
                float dr = Math.max(0.5f, s * 0.07f);
                c.drawCircle(0, 0, dr, p);
                c.drawCircle(s, 0, dr, p);
                c.drawCircle(0, s, dr, p);
                c.drawCircle(s, s, dr, p);
                break;

            case WAVE: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke(s, 0.06f, strength));
                int seg = 24;                 // 一整周期，保证左右边界 y 相同 → 无缝
                float amp = s * 0.18f * (0.5f + strength);
                float prevX = 0f, prevY = s / 2f;
                for (int i = 1; i <= seg; i++) {
                    float t = i / (float) seg;
                    float x = t * s;
                    float y = s / 2f - (float) Math.sin(t * 2 * Math.PI) * amp;
                    c.drawLine(prevX, prevY, x, y, p);
                    prevX = x;
                    prevY = y;
                }
                break;
            }

            case TRIANGLE:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke(s, 0.04f, strength));
                c.drawLine(0, 0, s, s, p);
                c.drawLine(0, s, s, 0, p);
                c.drawLine(0, 0, s, 0, p);
                break;

            default:
                return null;
        }
        return bmp;
    }

    /**
     * 线宽/半径随浓度放大，但有下限，避免细线在高分屏消失。
     * ★ 修复：必须是 static 且 strength 走参数——静态方法无法引用实例字段，
     *   原先直接写 strength 会报 "non-static variable strength cannot be referenced from a static context"
     */
    private static float stroke(float s, float base, float strength) {
        return Math.max(0.5f, s * base * (0.6f + 0.8f * strength));
    }
}
