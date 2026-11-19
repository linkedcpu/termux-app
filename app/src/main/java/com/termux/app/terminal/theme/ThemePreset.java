package com.termux.app.terminal.theme;

/** 一套皮肤的原始数据。新增皮肤 = ALL 里加一行，控件零改动 */
public final class ThemePreset {

    public final String name;
    public final int bg;
    public final int surface;
    public final int accent;

    /** SCRIM 蒙层 alpha（0=自动按 bg 明暗推导）：仅用于压暗叠层，与壁纸无关 */
    public final int scrim;

    public final String terminalScheme; // 终端配色名，可空
    public final ThemeShape shape;
    public final ThemeTexture texture;
    public final ThemeMotion motion;

    /** 明暗覆盖：null=按 bg 亮度自动判定；true/false=强制深/浅，供全透明主题兜底 */
    public final Boolean forceDark;

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
        this(name, bg, surface, accent, scrim, terminalScheme, shape, texture, motion, null);
    }

    /** 完整形态 + 明暗覆盖：forceDark 为 null 时按 bg 自动判定 */
    public ThemePreset(
            String name,
            int bg,
            int surface,
            int accent,
            int scrim,
            String terminalScheme,
            ThemeShape shape,
            ThemeTexture texture,
            ThemeMotion motion,
            Boolean forceDark) {
        this.name = name;
        this.bg = bg;
        this.surface = surface == 0 ? bg : surface;
        this.accent = accent;
        this.scrim = scrim;
        this.terminalScheme = terminalScheme;
        this.shape = shape == null ? ThemeShape.classic() : shape;
        this.texture = texture == null ? ThemeTexture.none() : texture;
        this.motion = motion == null ? ThemeMotion.standard() : motion;
        this.forceDark = forceDark;
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

        /* ============ 日用搭配组 N1~N8（index 11~18，只追加不插队） ============
         * 统一规律：浓度 0.04~0.16（不抢文字）、格子 28~40dp（低频不噪）、
         * 描边 ≤0.5dp、accent 与 bg 拉开明度差。                              */

        // N1 纸感浅色：最保守的日用，点阵几乎看不见，只让底色不发死
        new ThemePreset(
                "N1 纸感浅色",
                0xfff7f5f0,
                0xff4a7a6f,
                0xe6,
                "light",
                new ThemeShape(1.0f, 0.5f, false, 0f, 32f),
                ThemeTexture.dot(0.05f),
                ThemeMotion.standard()),

        // N2 方格笔记：方格纸，复古感最强，本组辨识度最高
        new ThemePreset(
                "N2 方格笔记",
                0xfffbf8f2,
                0xff3f6fa5,
                0xe6,
                "light",
                new ThemeShape(0.4f, 0.5f, false, 0f, 28f),
                ThemeTexture.dotgrid(0.16f),
                ThemeMotion.standard()),

        // N3 夜读：暗色日用，大格子淡点阵，长时间盯屏不累
        new ThemePreset(
                "N3 夜读",
                0xff1b1e24,
                0xff6cb6ff,
                0x4d,
                "dark",
                new ThemeShape(1.0f, 0f, false, 0f, 40f),
                ThemeTexture.dot(0.05f),
                ThemeMotion.standard()),

        // N4 墨夜波纹：波纹最柔和，适合暗色下想要一点流动感
        new ThemePreset(
                "N4 墨夜波纹",
                0xff17202a,
                0xff6cb6ff,
                0x59,
                "ink-blue",
                new ThemeShape(1.0f, 0f, false, 0f, 40f),
                ThemeTexture.wave(0.16f),
                ThemeMotion.standard()),

        // N5 奶油纸面：马卡龙奶油底 + 网格，浅色里最温润
        new ThemePreset(
                "N5 奶油纸面",
                0xfff7f1e4,
                0xffc9962f,
                0xeb,
                "light",
                new ThemeShape(0.6f, 0.5f, false, 0f, 36f),
                ThemeTexture.grid(0.05f),
                ThemeMotion.standard()),

        // N6 薄荷噪点：噪点浓度压到最低才有胶片颗粒感，高了就是脏
        new ThemePreset(
                "N6 薄荷噪点",
                0xffeef4ec,
                0xff4f8a63,
                0xeb,
                "light",
                new ThemeShape(1.0f, 0f, false, 0f, 20f),
                ThemeTexture.noise(0.06f),
                ThemeMotion.standard()),

        // N7 藕粉加号：加号阵最轻，比网格更透气
        new ThemePreset(
                "N7 藕粉加号",
                0xfff6eff3,
                0xffa8607f,
                0xe6,
                "light",
                new ThemeShape(1.2f, 0f, false, 0f, 28f),
                ThemeTexture.cross(0.12f),
                ThemeMotion.standard()),

        // N8 暖夜环阵：同心环在暗底上最显质感，格子要大
        new ThemePreset(
                "N8 暖夜环阵",
                0xff241f1c,
                0xffd68755,
                0x59,
                "warm",
                new ThemeShape(0.8f, 0f, false, 0f, 36f),
                ThemeTexture.circles(0.08f),
                ThemeMotion.standard()),

        /* ============ 验证组 V1~V7（index 19~25） ============ */

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

        // V4 形状-胶囊：pillButtons 纯色态，验证 pillButtons 修复是否生效
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

        // V6 材质-网格：第二种纹路，看接缝是否连续
        new ThemePreset(
                "V6 材质网格",
                0xfff4f5f7,
                0xff3d6fb4,
                0xe6,
                "light",
                new ThemeShape(0.5f, 1f, false, 0f, 24f),
                ThemeTexture.grid(0.22f),
                ThemeMotion.standard()),

        // V7 动效-干脆：无缩放无波纹，与 N1 形成明显手感差
        new ThemePreset(
                "V7 动效干脆",
                0xfff2e8d0,
                0xffd0202a,
                0xe6,
                "light",
                ThemeShape.manga(),
                ThemeTexture.dot(0.28f),
                ThemeMotion.snappy()),

        /* ============ 材质穷举组 T1~T6（index 26~31） ============
         * 浓度刻意拉高到 0.22~0.35，只为肉眼确认平铺是否正确，不适合日用 */

        new ThemePreset(
                "T1 点阵",
                0xfff2e8d0,
                0xffd0202a,
                0xe6,
                "light",
                new ThemeShape(1.0f, 1f, false, 0f, 32f),
                ThemeTexture.dot(0.30f),
                ThemeMotion.standard()),
        new ThemePreset(
                "T2 网格",
                0xfff4f5f7,
                0xff3d6fb4,
                0xe6,
                "light",
                new ThemeShape(0.5f, 1f, false, 0f, 24f),
                ThemeTexture.grid(0.30f),
                ThemeMotion.standard()),
        new ThemePreset(
                "T3 斜纹",
                0xfff6eff3,
                0xffbf8fa3,
                0xe6,
                "light",
                new ThemeShape(0.3f, 1f, false, 0f, 28f),
                ThemeTexture.diagonal(0.30f),
                ThemeMotion.standard()),
        new ThemePreset(
                "T4 棋盘",
                0xffeef4ec,
                0xff6fae7e,
                0xe6,
                "light",
                new ThemeShape(0.0f, 2f, false, 0f, 32f),
                ThemeTexture.checker(0.22f),
                ThemeMotion.standard()),
        new ThemePreset(
                "T5 噪点",
                0xfff0f1f6,
                0xff7f88a8,
                0xe6,
                "light",
                new ThemeShape(1.0f, 0f, false, 0f, 20f),
                ThemeTexture.noise(0.35f),
                ThemeMotion.standard()),
        new ThemePreset(
                "T6 环阵",
                0xfff7f1e4,
                0xffd9b65c,
                0xeb,
                "light",
                new ThemeShape(0.6f, 1f, false, 0f, 36f),
                ThemeTexture.circles(0.30f),
                ThemeMotion.standard()),

        /* ============ 余下材质补测组 M1~M8（index 32~39） ============
         * 同样高浓度，专测 brick / hash / dotgrid / wave / triangle 的接缝 */

        new ThemePreset(
                "M1 点阵",
                0xfff2e8d0,
                0xffd0202a,
                0xe6,
                "light",
                new ThemeShape(1.0f, 1f, false, 0f, 32f),
                ThemeTexture.dot(0.30f),
                ThemeMotion.standard()),
        new ThemePreset(
                "M2 棋盘",
                0xffeef4ec,
                0xff6fae7e,
                0xe6,
                "light",
                new ThemeShape(0.0f, 2f, false, 0f, 32f),
                ThemeTexture.checker(0.22f),
                ThemeMotion.standard()),
        new ThemePreset(
                "M3 噪点",
                0xfff0f1f6,
                0xff7f88a8,
                0xe6,
                "light",
                new ThemeShape(1.0f, 0f, false, 0f, 20f),
                ThemeTexture.noise(0.35f),
                ThemeMotion.standard()),
        new ThemePreset(
                "M4 点格",
                0xfff4f1ea,
                0xff4a6fa5,
                0xe6,
                "light",
                new ThemeShape(0.5f, 1f, false, 0f, 24f),
                ThemeTexture.dotgrid(0.30f),
                ThemeMotion.standard()),
        new ThemePreset(
                "M5 波纹",
                0xfff0f4f8,
                0xff4f86b8,
                0xe6,
                "light",
                new ThemeShape(1.0f, 0f, false, 0f, 40f),
                ThemeTexture.wave(0.34f),
                ThemeMotion.standard()),
        new ThemePreset(
                "M6 砖墙",
                0xfff6eee6,
                0xffa8552f,
                0xe6,
                "light",
                new ThemeShape(0.2f, 2f, false, 0f, 40f),
                ThemeTexture.brick(0.28f),
                ThemeMotion.standard()),
        new ThemePreset(
                "M7 影线",
                0xfff5f2ec,
                0xff8a7f6a,
                0xe6,
                "light",
                new ThemeShape(0.3f, 1f, false, 0f, 32f),
                ThemeTexture.hash(0.24f),
                ThemeMotion.standard()),
        new ThemePreset(
                "M8 三角网",
                0xfff2f3f7,
                0xff3f6f9f,
                0xe6,
                "light",
                new ThemeShape(0.0f, 1f, false, 0f, 36f),
                ThemeTexture.triangle(0.26f),
                ThemeMotion.standard()),

        /* ============ 全透主题（index 40，追加在末尾，不动已有 index） ============
         * bg 全透明，surface 仅留极淡底色保证卡片可读；
         * isDark(全透 bg) 判为暗色，文字自动走浅色，在任意桌面上都能看清。       */

        new ThemePreset(
                "全透明",
                0x00000000, // bg 全透
                0x14ffffff, // surface：半透白，卡片依旧可辨
                0xff5cc97a,
                0x00,
                "dark",
                ThemeShape.flat(),
                ThemeTexture.none(),
                ThemeMotion.standard(),
                Boolean.TRUE), // 透明 bg 无法自判明暗，强制深色配浅字
        new ThemePreset(
                "果冻粉",
                0xfff6eff3,
                0xfff0a8bd,
                0xe6,
                "light",
                ThemeShape.pill(),
                ThemeTexture.none(),
                ThemeMotion.jelly()),
        new ThemePreset(
                "微旋转",
                0xfff2e8d0,
                0xffd0202a,
                0xe6,
                "light",
                ThemeShape.manga(),
                ThemeTexture.dot(0.28f),
                ThemeMotion.rotate()),
        new ThemePreset(
                "干脆黑白",
                0xfffafafa,
                0xff111111,
                0xe6,
                "light",
                ThemeShape.flat(),
                ThemeTexture.none(),
                ThemeMotion.snappy()),
        /* ============ 动效演示预设（index 41+，追加在末尾） ============
         * 每个预设绑定一种 PressEffect，用于直观验收动效接入是否生效。      */

        // 41 果冻粉：按下压到 0.63，抬起弹过 1.14 再归位，抖动最明显
        new ThemePreset(
                "果冻粉",
                0xfff6eff3,
                0xfff06292,
                0xe6,
                "light",
                ThemeShape.pill(),
                ThemeTexture.none(),
                new ThemeMotion(
                        ThemeMotion.PressEffect.JELLY,
                        160,
                        320,
                        0.72f,
                        0.6f,
                        1.18f,
                        0.96f,
                        0f,
                        0f,
                        0.10f,
                        true)),

        // 42 拉伸绿：按下纵向压到 0.82，松手弹回，像被捏扁的橡皮
        new ThemePreset(
                "拉伸绿",
                0xffeef4ec,
                0xff2f9e54,
                0xeb,
                "light",
                ThemeShape.flat(),
                ThemeTexture.dot(0.05f),
                new ThemeMotion(
                        ThemeMotion.PressEffect.STRETCH,
                        140,
                        260,
                        0.97f,
                        0.6f,
                        1.0f,
                        0.82f,
                        0f,
                        0f,
                        0.10f,
                        true)),

        // 43 陀螺红：按下倾 7°，松手转正，旋转幅度一眼可见
        new ThemePreset(
                "陀螺红",
                0xfff7ecec,
                0xffe0202a,
                0xe6,
                "light",
                ThemeShape.manga(),
                ThemeTexture.dot(0.22f),
                new ThemeMotion(
                        ThemeMotion.PressEffect.ROTATE,
                        150,
                        260,
                        0.97f,
                        0.6f,
                        1.0f,
                        0.96f,
                        7f,
                        0f,
                        0.12f,
                        true)),

        // 44 弹跳蓝：按下整体下沉 8dp，松手弹回原位
        new ThemePreset(
                "弹跳蓝",
                0xffeaf1f8,
                0xff1f6fd0,
                0xeb,
                "light",
                ThemeShape.pill(),
                ThemeTexture.none(),
                new ThemeMotion(
                        ThemeMotion.PressEffect.BOUNCE,
                        130,
                        240,
                        0.97f,
                        0.6f,
                        1.0f,
                        0.96f,
                        0f,
                        8f,
                        0.10f,
                        true)),

        // 45 缩放紫：最经典的缩小回弹，压到 0.86
        new ThemePreset(
                "缩放紫",
                0xfff2edf8,
                0xff7b3fbf,
                0xe6,
                "light",
                ThemeShape.flat(),
                ThemeTexture.none(),
                new ThemeMotion(
                        ThemeMotion.PressEffect.SCALE,
                        130,
                        240,
                        0.86f,
                        0.6f,
                        1.0f,
                        0.96f,
                        0f,
                        0f,
                        0.10f,
                        true)),

        // 46 呼吸橙：只做透明度淡出到 0.25，无形变，适合列表密集区
        new ThemePreset(
                "呼吸橙",
                0xfffdf1e6,
                0xffef6a1e,
                0xeb,
                "light",
                ThemeShape.classic(),
                ThemeTexture.none(),
                new ThemeMotion(
                        ThemeMotion.PressEffect.ALPHA,
                        160,
                        260,
                        1.0f,
                        0.25f,
                        1.0f,
                        1.0f,
                        0f,
                        0f,
                        0.12f,
                        true)),

        // 47 干脆黑白：无任何动画，作为对照组，验证 NONE 真的零开销
        new ThemePreset(
                "干脆黑白",
                0xfffafafa,
                0xff111111,
                0xe6,
                "light",
                ThemeShape.flat(),
                ThemeTexture.none(),
                ThemeMotion.snappy()),

        // 48 果冻暗夜：暗色底 + 果冻，验证暗色派生色在动效中不发灰
        // 49 旋涡青：松手转满 360°
        new ThemePreset(
                "旋涡青",
                0xffe6f6f6,
                0xff00a89c,
                0xeb,
                "light",
                ThemeShape.pill(),
                ThemeTexture.none(),
                ThemeMotion.spin()),

        // 50 双旋紫：转两圈 720°
        new ThemePreset(
                "双旋紫",
                0xfff2edf8,
                0xff7b3fbf,
                0xe6,
                "light",
                ThemeShape.flat(),
                ThemeTexture.none(),
                ThemeMotion.spinDouble()),

        // 51 旋涡暗夜：暗色底 + 转一圈，验证暗色派生色在旋转中不发灰
        new ThemePreset(
                "旋涡暗夜",
                0xff12141a,
                0xff00e5d0,
                0x59,
                "dark",
                ThemeShape.pill(),
                ThemeTexture.dot(0.22f),
                new ThemeMotion(
                        ThemeMotion.PressEffect.SPIN,
                        140,
                        400,
                        0.92f,
                        0.6f,
                        1.0f,
                        0.96f,
                        0f,
                        0f,
                        360f,
                        0.10f,
                        true)),
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
