package com.termux.app.terminal.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一套皮肤的原始数据。新增皮肤 = ALL 里加一行，控件零改动。
 *
 * <p>★ 哨兵约定（与 {@link Theme} 保持一致）：
 *
 * <ul>
 *   <li>{@link #UNSET} —— 「未设置」，交由 Theme 自动推导（surface 回退 bg；scrim 按明暗兜底）
 *   <li>{@link #SCRIM_NONE} —— 显式「不要蒙层」（全透明），等同于传入 0x00000000
 * </ul>
 *
 * 不再用 0 表示「自动」，避免与「全透明」歧义。
 */
public final class ThemePreset {

    /** 未设置哨兵：与 Theme.UNSET 同值 */
    static final int UNSET = 0xff000000;

    /** 显式「不要蒙层」：全透明黑 */
    public static final int SCRIM_NONE = 0x00000000;

    public final String name;
    public final int bg;
    public final int surface;
    public final int accent;

    /** SCRIM 蒙层色；UNSET=自动按 bg 明暗推导；SCRIM_NONE=不要蒙层；其它值按传入（支持半透明） */
    public final int scrim;

    public final String terminalScheme;
    public final ThemeShape shape;
    public final ThemeTexture texture;
    public final ThemeMotion motion;

    /** null=按 bg 亮度自动判定；true/false=强制深/浅，供全透明主题兜底 */
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
        this.name = name != null ? name : "theme";
        this.bg = bg;
        // 不再吞掉 0：保留皮肤意图；「未设置」由 UNSET 表达
        this.surface = surface;
        this.accent = accent;
        // 历史调用习惯传 0 表示「自动」，归一为 UNSET；SCRIM_NONE / 半透值原样保留
        this.scrim = (scrim == 0) ? UNSET : scrim;
        this.terminalScheme = terminalScheme;
        this.shape = shape != null ? shape : ThemeShape.classic();
        this.texture = texture != null ? texture : ThemeTexture.none();
        this.motion = motion != null ? motion : ThemeMotion.standard();
        this.forceDark = forceDark;
    }

    /** 常用：名称 + 底色 + 强调色 + 蒙层 + 终端配色；surface 走自动（=bg） */
    public ThemePreset(String name, int bg, int accent, int scrim, String terminalScheme) {
        this(name, bg, UNSET, accent, scrim, terminalScheme, null, null, null);
    }

    /** 最简：名称 + 底色 + 强调色 */
    public ThemePreset(String name, int bg, int accent) {
        this(name, bg, UNSET, accent, 0, null, null, null, null);
    }

    /* ============================================================
     * ★ 最优预设组：日用 8 + 特色 4 + 全透明 1 + Ubuntu 1
     *   + Apple 2 + Windows 2 + Windows XP 1 = 19 套
     *   · 无重名；index 稳定；只追加不插队
     *   · 浓度 0.04~0.10（日用）/ 0.18~0.30（验证）；格子 20~40dp
     *   · 描边 ≤0.5dp（日用）/ 1~2.5dp（漫画风）
     *   · accent 与 bg 明度差足；forceDark 仅全透明需要
     * ============================================================ */
    @SuppressWarnings("SpellCheckingInspection")
    public static final ThemePreset[] ALL = {
        /* ---- 日用 · 浅色 4 ---- */
        // 0 浅色：最中性，无材质，日常默认
        new ThemePreset(
                "浅色",
                0xfff7f7f5,
                0xffffffff,
                0xff3a8b52,
                0xe6000000,
                "light",
                ThemeShape.classic(),
                ThemeTexture.none(),
                ThemeMotion.standard()),

        // 1 纸感浅色：暖白底 + 淡点阵，长时间阅读不刺眼
        new ThemePreset(
                "纸感浅色",
                0xfff7f5f0,
                0xfffdfbf7,
                0xff4a7a6f,
                0xe6000000,
                "light",
                new ThemeShape(1.0f, 0.5f, false, 0f, 32f),
                ThemeTexture.dot(0.05f),
                ThemeMotion.standard()),

        // 2 奶油纸面：马卡龙奶油底 + 网格，浅色里最温润
        new ThemePreset(
                "奶油纸面",
                0xfff7f1e4,
                0xfffdf9f0,
                0xffc9962f,
                0xeb000000,
                "light",
                new ThemeShape(0.6f, 0.5f, false, 0f, 36f),
                ThemeTexture.grid(0.05f),
                ThemeMotion.standard()),

        // 3 薄荷噪点：胶片颗粒感，浓度压到最低才不脏
        new ThemePreset(
                "薄荷噪点",
                0xffeef4ec,
                0xfff6faf6,
                0xff4f8a63,
                0xeb000000,
                "light",
                new ThemeShape(1.0f, 0f, false, 0f, 20f),
                ThemeTexture.noise(0.06f),
                ThemeMotion.standard()),

        /* ---- 日用 · 暗色 4 ---- */
        // 4 深色：中性暗底，日常默认
        new ThemePreset(
                "深色",
                0xff1e1f22,
                0xff2a2c31,
                0xff5cc97a,
                0x4d000000,
                "dark",
                ThemeShape.classic(),
                ThemeTexture.none(),
                ThemeMotion.standard()),

        // 5 夜读：暗色日用，大格子淡点阵，盯屏不累
        new ThemePreset(
                "夜读",
                0xff1b1e24,
                0xff262a32,
                0xff6cb6ff,
                0x4d000000,
                "dark",
                new ThemeShape(1.0f, 0f, false, 0f, 40f),
                ThemeTexture.dot(0.05f),
                ThemeMotion.standard()),

        // 6 暖夜：暖暗底 + 同心环，氛围感强
        new ThemePreset(
                "暖夜",
                0xff241f1c,
                0xff302823,
                0xffd68755,
                0x59000000,
                "warm",
                new ThemeShape(0.8f, 0f, false, 0f, 36f),
                ThemeTexture.circles(0.08f),
                ThemeMotion.standard()),

        // 7 墨蓝：冷暗底 + 波纹，最柔和的流动感
        new ThemePreset(
                "墨蓝",
                0xff17202a,
                0xff1f2b37,
                0xff6cb6ff,
                0x59000000,
                "ink-blue",
                new ThemeShape(1.0f, 0f, false, 0f, 40f),
                ThemeTexture.wave(0.10f),
                ThemeMotion.standard()),

        /* ---- 特色 · 形状演示 3 ---- */
        // 8 漫画 / 果冻粉 / 旋涡青 / 干脆黑白，见下
        // 8 漫画：直角 + 粗描边 + 硬投影 + 点阵纸纹，辨识度最高
        new ThemePreset(
                "漫画",
                0xfff2e8d0,
                0xfffdf8ec,
                0xffd0202a,
                0xe6000000,
                "light",
                ThemeShape.manga(),
                ThemeTexture.dot(0.10f),
                ThemeMotion.snappy()),

        // 9 果冻粉：胶囊 + 果冻动效，按下/回弹最明显
        new ThemePreset(
                "果冻粉",
                0xfff6eff3,
                0xfffdf6fa,
                0xffe0608c,
                0xe6000000,
                "light",
                ThemeShape.pill(),
                ThemeTexture.none(),
                ThemeMotion.jelly()),

        // 10 旋涡青：胶囊 + 松手转一圈
        new ThemePreset(
                "旋涡青",
                0xffe6f6f6,
                0xfff0faf9,
                0xff00a89c,
                0xeb000000,
                "light",
                ThemeShape.pill(),
                ThemeTexture.none(),
                ThemeMotion.spin()),

        // 11 干脆黑白：扁平 + 无动效 + 高对比，对照组
        new ThemePreset(
                "干脆黑白",
                0xfffafafa,
                0xffffffff,
                0xff111111,
                0xe6000000,
                "light",
                ThemeShape.flat(),
                ThemeTexture.none(),
                ThemeMotion.snappy()),

        /* ---- 特殊 · 全透明 1（追加在末尾，不动已有 index） ---- */
        // 12 全透明：bg 全透，surface 极淡半透白；forceDark 强制浅字
        new ThemePreset(
                "全透明",
                0x00000000,
                0x14ffffff,
                0xff5cc97a,
                ThemePreset.SCRIM_NONE,
                "dark",
                ThemeShape.flat(),
                ThemeTexture.none(),
                ThemeMotion.standard(),
                Boolean.TRUE),

        /* ---- 特色 · Ubuntu 1（追加在末尾，不动已有 index） ---- */
        // 13 Ubuntu：经典紫底 + 橙强调，经典主题风格
        new ThemePreset(
                "Ubuntu",
                0xff300a24,
                0xff3b0d2e,
                0xffe95400,
                0x59000000,
                "ubuntu",
                ThemeShape.classic(),
                ThemeTexture.none(),
                ThemeMotion.standard()),

        /* ---- 特色 · Apple 2（追加在末尾，不动已有 index） ---- */
        // 14 苹果浅色：HIG 分组浅灰底 #F2F2F7 + 白卡面 + 系统蓝 #007AFF，squircle 大圆角
        new ThemePreset(
                "苹果浅色",
                0xfff2f2f7,
                0xffffffff,
                0xff007aff,
                0x14000000,
                "apple-light",
                new ThemeShape(1.0f, 0.5f, false, 0f, 20f),
                ThemeTexture.none(),
                ThemeMotion.standard()),

        // 15 苹果深色：OLED 黑底 #000000 + 抬升层 #1C1C1E + 深模态蓝 #0A84FF，克制无材质
        new ThemePreset(
                "苹果深色",
                0xff000000,
                0xff1c1c1e,
                0xff0a84ff,
                0x59000000,
                "apple-dark",
                new ThemeShape(1.0f, 0.5f, false, 0f, 20f),
                ThemeTexture.none(),
                ThemeMotion.standard()),

        /* ---- 特色 · Windows 2（追加在末尾，不动已有 index） ---- */
        // 16 Windows 浅色：Fluent 浅灰底 #F3F3F3 + 白卡面 + 主题蓝 #0067C0，细微圆角 + Mica 淡噪点
        new ThemePreset(
                "Windows 浅色",
                0xfff3f3f3,
                0xffffffff,
                0xff0067c0,
                0x14000000,
                "windows-light",
                new ThemeShape(1.0f, 0.5f, false, 0f, 8f),
                ThemeTexture.noise(0.04f),
                ThemeMotion.standard()),

        // 17 Windows 深色：深灰底 #202020 + 抬升层 #2B2B2B + 亮蓝 #4CC2FF，Acrylic 感半透蒙层
        new ThemePreset(
                "Windows 深色",
                0xff202020,
                0xff2b2b2b,
                0xff4cc2ff,
                0x59000000,
                "windows-dark",
                new ThemeShape(1.0f, 0.5f, false, 0f, 8f),
                ThemeTexture.noise(0.05f),
                ThemeMotion.standard()),

        /* ---- 特色 · Windows XP 1（追加在末尾，不动已有 index） ---- */
        // 18 Windows XP：经典蓝天白云底 #58A4E0 + 银灰卡面 #ECE9DD + 作品蓝 #0054E3
        new ThemePreset(
                "Windows XP",
                0xff58a4e0,
                0xffece9dd,
                0xff0054e3,
                0x14000000,
                "windows-xp",
                new ThemeShape(1.0f, 0.5f, false, 0f, 6f),
                ThemeTexture.none(),
                ThemeMotion.standard()),
    };

    /** 不可变视图，防止外部改数组元素 */
    public static List<ThemePreset> all() {
        List<ThemePreset> l = new ArrayList<>(ALL.length);
        Collections.addAll(l, ALL);
        return Collections.unmodifiableList(l);
    }

    public static ThemePreset get(int index) {
        if (index < 0 || index >= ALL.length) {
            android.util.Log.w(
                    "ThemePreset",
                    "index " + index + " out of [" + 0 + "," + (ALL.length - 1) + "], clamped");
            index = Math.max(0, Math.min(index, ALL.length - 1));
        }
        return ALL[index];
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
