package com.termux.app.terminal.theme;

/** 一套皮肤的原始数据。新增皮肤 = ALL 里加一行，控件零改动 */
public final class ThemePreset {

    public final String name;
    public final int bg;
    public final int surface;
    public final int accent;
    public final int scrim; // 壁纸蒙层 alpha；0=自动
    public final String terminalScheme; // 终端配色名，可空
    public final ThemeShape shape;
    public final ThemeTexture texture;
    public final ThemeMotion motion;

    public ThemePreset(
            String name,
            int bg,
            int surface,
            int accent,
            int scrim,
            String terminalScheme,
            ThemeShape shape,
            ThemeTexture texture,
            ThemeMotion motion) {
        this.name = name;
        this.bg = bg;
        this.surface = surface == 0 ? bg : surface;
        this.accent = accent;
        this.scrim = scrim;
        this.terminalScheme = terminalScheme;
        this.shape = shape == null ? ThemeShape.classic() : shape;
        this.texture = texture == null ? ThemeTexture.none() : texture;
        this.motion = motion == null ? ThemeMotion.standard() : motion;
    }

    /** 常用：名称 + 底色 + 强调色 + 蒙层 + 终端配色，其余取默认 */
    public ThemePreset(String name, int bg, int accent, int scrim, String terminalScheme) {
        this(name, bg, 0, accent, scrim, terminalScheme, null, null, null);
    }

    /** 最简：名称 + 底色 + 强调色 */
    public ThemePreset(String name, int bg, int accent) {
        this(name, bg, 0, accent, 0, null, null, null, null);
    }

    /** 完整形态：指定形状、材质、动效 */
    public ThemePreset(
            String name,
            int bg,
            int accent,
            int scrim,
            String terminalScheme,
            ThemeShape shape,
            ThemeTexture texture,
            ThemeMotion motion) {
        this(name, bg, 0, accent, scrim, terminalScheme, shape, texture, motion);
    }

    // index 为稳定 ID：只追加、不插队，否则旧用户主题会错位
    public static final ThemePreset[] ALL = {
        new ThemePreset("浅色", 0xfff7f7f5, 0xff3a8b52, 0xe6, "light"),
        new ThemePreset("深色", 0xff1e1f22, 0xff5cc97a, 0x4d, "dark"),
        new ThemePreset("暖夜", 0xff241f1c, 0xffd68755, 0x59, "warm"),
        new ThemePreset("墨蓝", 0xff17202a, 0xff6cb6ff, 0x59, "ink-blue"),
        new ThemePreset("半透黑", 0xe617191c, 0xff5cc97a, 0x73, "dark"),
        // 马卡龙四色
        new ThemePreset("奶油黄", 0xfff7f1e4, 0xffd9b65c, 0xeb, "light"),
        new ThemePreset("薄荷绿", 0xffeef4ec, 0xff6fae7e, 0xeb, "light"),
        new ThemePreset("藕紫", 0xfff0f1f6, 0xff7f88a8, 0xe6, "light"),
        new ThemePreset("藕粉", 0xfff6eff3, 0xffbf8fa3, 0xe6, "light"),
        // 完整形状演示：漫画风（直角 + 粗描边 + 硬投影 + 点阵纸纹）
        new ThemePreset(
                "漫画",
                0xfff2e8d0,
                0xffd0202a,
                0xe6,
                "light",
                ThemeShape.manga(),
                ThemeTexture.dot(0.10f),
                ThemeMotion.snappy()),
        // 完整形状演示：扁平风（小圆角 + 细描边 + 网格）
        new ThemePreset(
                "网格",
                0xfff4f5f7,
                0xff3d6fb4,
                0xe6,
                "light",
                ThemeShape.flat(),
                ThemeTexture.grid(0.08f),
                ThemeMotion.standard()),
        /* ============ 主题模块验证组（index 11~17，只追加不插队） ============ */

        // V1 颜色角色：纯色无材质无描边，专看 BG/SURFACE/ACCENT/ON_ACCENT/TEXT 三档
        new ThemePreset(
                "V1 颜色角色",
                0xfff2e8d0,
                0xffd0202a,
                0xe6,
                "light",
                ThemeShape.flat(),
                ThemeTexture.none(),
                ThemeMotion.standard()),

        // V2 形状-圆角：radiusScale 1.8 放大圆角，直角控件也应明显变圆
        new ThemePreset(
                "V2 圆角",
                0xfff4f5f7,
                0xff3d6fb4,
                0xe6,
                "light",
                new ThemeShape(1.8f, 0f, false, 0f, 0f),
                ThemeTexture.none(),
                ThemeMotion.standard()),

        // V3 形状-描边：4dp 粗描边，验证 stroke() 与 density 换算
        new ThemePreset(
                "V3 描边",
                0xfff4f5f7,
                0xff3d6fb4,
                0xe6,
                "light",
                new ThemeShape(0.3f, 4f, false, 0f, 0f),
                ThemeTexture.none(),
                ThemeMotion.standard()),

        // V4 形状-胶囊：pillButtons 纯色态，验证上一轮修复是否生效
        new ThemePreset(
                "V4 胶囊按钮",
                0xfff6eff3,
                0xffbf8fa3,
                0xe6,
                "light",
                ThemeShape.pill(),
                ThemeTexture.none(),
                ThemeMotion.standard()),

        // V5 材质：大格子 + 浓点阵，验证 textureTile 尺寸与平铺
        new ThemePreset(
                "V5 材质点阵",
                0xfff2e8d0,
                0xffd0202a,
                0xe6,
                "light",
                new ThemeShape(1.0f, 1f, false, 0f, 32f),
                ThemeTexture.dot(0.28f),
                ThemeMotion.standard()),

        // V6 材质-网格：验证第二种纹路 + 斜纹叠加不崩
        new ThemePreset(
                "V6 材质网格",
                0xfff4f5f7,
                0xff3d6fb4,
                0xe6,
                "light",
                new ThemeShape(0.5f, 1f, false, 0f, 24f),
                ThemeTexture.grid(0.22f),
                ThemeMotion.standard()),

        // V7 动效-干脆：0ms 无缩放无波纹，按下应与 V5 形成明显手感差
        new ThemePreset(
                "V7 动效干脆",
                0xfff2e8d0,
                0xffd0202a,
                0xe6,
                "light",
                ThemeShape.manga(),
                ThemeTexture.dot(0.28f),
                ThemeMotion.snappy()),
    };

    public static ThemePreset get(int index) {
        return ALL[Math.max(0, Math.min(index, ALL.length - 1))];
    }

    public static int count() {
        return ALL.length;
    }

    public static int indexOf(String name) {
        if (name != null) {
            for (int i = 0; i < ALL.length; i++) {
                if (ALL[i].name.equals(name)) return i;
            }
        }
        return 0;
    }
}
