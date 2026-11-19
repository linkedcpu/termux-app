# 主题系统接入规范（AI 操作手册）

> 适用范围：`com.termux.app.terminal.theme` 包下的 `Theme` / `ThemePreset` / `ThemeStore` / `ThemeRenderer` / `ThemeMotion` / `ThemeMotionPlayer`。
> 本文面向**阅读本文件后要为控件添加主题的 AI**。目标是：新增任意控件时，不写死色值、圆角、时长、亮度判断，全部走语义入口。

---

## 0. 三十秒速记（照抄即可）

```java
// 字段
private Theme mTheme;

// 取主题（不可变快照，可缓存到字段）
mTheme = ThemeStore.get(context);

// 背景：四个无参语义入口，禁止传魔法数字
root.setBackground(mTheme.page(context));    // 根容器
view.setBackground(mTheme.card(context));    // 卡片 / 页面块
view.setBackground(mTheme.tab(context));     // 标签栏 / 分段控件
view.setBackground(mTheme.button(context));  // 按钮

// 文字
tv.setTextColor(mTheme.textPrimary());
tv.setTextColor(mTheme.textSecondary());

// 动效：一行，删掉所有手写 setOnTouchListener
ThemeMotionPlayer.attach(view, mTheme);
```

**只有这三件事。任何超出这三行的需求，先回到本文查对应章节，不要自己推导。**

---

## 1. 核心契约（违反即算 bug）

| 契约 | 说明 |
|---|---|
| **C1 不写死色值** | 禁止 `0xff000000` / `Color.WHITE` / `R.color.*`。一律 `mTheme.color(角色)` 或语义方法。 |
| **C2 不写死圆角** | 禁止 `setCornerRadius(8 * density)`。一律四个无参入口；非标准圆角才用 `mTheme.surface(ctx, dp)`。 |
| **C3 不写死时长** | 禁止 `animate().setDuration(90)`。用 `mTheme.motion().pressDurationMs` / `themeSwitchDurationMs`。 |
| **C4 不判亮度** | 禁止自己判断深色/浅色。需要分支时问 `mTheme.isDark()`，且仅限确实无法用角色表达时。 |
| **C5 不自己混色** | 禁止手写 alpha 叠加 / 颜色插值。用 `mTheme.color(role, state)` 派生状态色。 |
| **C6 不自己造 Drawable** | 背景一律从 `Theme` 拿。唯一构造 Drawable 的地方是 `ThemeRenderer`。 |
| **C7 主题不可变** | `Theme` 是快照，无 setter。切主题后必须重新 `ThemeStore.get()` 并刷新视图。 |
| **C8 不引入壁纸维度** | `WALLPAPER` 角色恒返回 `0`（透明）。任何代码不得依赖壁纸 Bitmap。 |

---

## 2. 角色表（color 的唯一入参）

`mTheme.color(int role)` 只接受以下常量：

```
BG         根底色              SURFACE    卡片底色
RAISED     浮起层（下拉/弹窗）  OVERLAY    覆盖层（对话框/遮罩）
TEXT       主文本              TEXT_SEC   次要文本
TEXT_DIS   禁用文本            ICON       普通图标
ICON_ACTIVE 激活图标           ACCENT     强调色
ACCENT_SOFT 强调色低透明底      ON_ACCENT  accent 底上的文字/图标
DIVIDER    分隔线              OUTLINE    描边
WALLPAPER  @Deprecated 恒为 0   SCRIM      压暗蒙层
DANGER     危险操作
```

**语义快捷方法（优先用这些，比 `color(ROLE)` 更安全）**：

```java
mTheme.textPrimary()    // == color(TEXT)
mTheme.textSecondary()  // == color(TEXT_SEC)
mTheme.accent()         // == color(ACCENT)
mTheme.onAccent()       // == color(ON_ACCENT)，放在 accent 底上的文字
mTheme.icon()           // == color(ICON)
mTheme.iconActive()     // == color(ICON_ACTIVE)，选中态图标
```

### 状态派生

```java
mTheme.color(Theme.SURFACE, Theme.STATE_SELECTED);  // 选中
mTheme.color(Theme.SURFACE, Theme.STATE_PRESSED);   // 按下
mTheme.color(Theme.TEXT,    Theme.STATE_DISABLED);  // 禁用
```

派生规则内置在 `Theme` 里：SELECTED 向 accent 混合、PRESSED 向黑/白混合、DISABLED 降透明度。**不要自己实现这套逻辑。**


## 3. 背景：四个入口 vs 逃逸口

### 3.1 首选：无参语义入口

| 入口 | 配套圆角常量 | 用在哪 |
|---|---|---|
| `mTheme.page(ctx)` | `R_PAGE_DP = 14` | Activity 根布局、抽屉根、对话框根 |
| `mTheme.card(ctx)` | `R_CARD_DP = 12` | 卡片、页面内容块、列表容器 |
| `mTheme.tab(ctx)` | `R_TAB_DP = 10` | Tab、分段控件、Chip |
| `mTheme.button(ctx)` | `R_BTN_DP = 8` | 所有按钮（含图标按钮） |

**判断口诀**：是根容器 → `page`；是分组内容的块 → `card`；是并列切换项 → `tab`；点了会触发动作 → `button`。

带缓存：同一 `Theme` 快照下同名层级复用同一 Drawable 实例，列表滚动不重复构造。

### 3.2 档位别名（控件挑档位时用）

```java
mTheme.radius(ctx, Theme.RADIUS_LARGE);  // == 14dp，受 radiusScale 缩放
mTheme.radius(ctx, Theme.RADIUS_SMALL);  // == 8dp
```

### 3.3 逃逸口（慎用）

```java
mTheme.surface(ctx, radiusBaseDp);  // 自定义圆角，不入缓存
```

仅在自定义 View 需要非标准圆角时使用。**如果传的值刚好等于 14/12/10/8，应该改用无参入口**，否则会丢掉缓存。

### 3.4 兼容重载（历史遗留，新代码不要用）

`panel(ctx, dp)` / `card(ctx, dp)` / `tab(ctx, dp)` / `button(ctx, dp)` 仍可编译，参数等于层级基准时转发到无参版。新代码一律无参。

### 3.5 accent 底色

```java
view.setBackground(mTheme.accentBg(ctx));  // accent 填充 + BTN 圆角
// 配 mTheme.onAccent() 作为文字色
```

### 3.6 形状参数（自定义 View 画形状时）

```java
float r = mTheme.radius(ctx, 8f);   // 已乘 radiusScale × density
int  sw = mTheme.stroke(ctx);        // 0 表示皮肤不要描边
boolean pill = mTheme.pillButtons(); // true 时按钮强制胶囊，忽略传入圆角
```

---

## 4. 动效：ThemeMotionPlayer 是唯一入口

### 4.1 标准接入

```java
ThemeMotionPlayer.attach(view, mTheme);
```

- 效果类型、时长、强度全部由皮肤 `motion()` 决定，控件零参数。
- 皮肤为 `NONE` 或时长为 0 时**不接管触摸**，零开销。
- 内部恒 `return false`，**不拦截** `onClick`。

### 4.2 已有 OnTouchListener 的控件

```java
ThemeMotionPlayer.wrap(view, mTheme.motion(), 原listener);
```

先跑动效再把事件转交，避免互相覆盖。**不要自己在 listener 里再写 alpha/scale 动画。**

### 4.3 单个控件临时改效果

```java
ThemeMotionPlayer.attach(btn, ThemeMotion.spin());      // 转一圈
ThemeMotionPlayer.attach(btn, ThemeMotion.jelly());     // 果冻
```

### 4.4 八种效果速查

| 效果 | 视觉 | 关键参数 |
|---|---|---|
| `NONE` | 无动画，零开销 | — |
| `ALPHA` | 透明度淡出 | `pressedAlpha`（默认 0.6） |
| `SCALE` | 缩小回弹 | `pressedScale`（0.97） |
| `JELLY` | 压下去再弹过冲 | `pressedScale` / `jellyOvershoot`（1.14） |
| `STRETCH` | 纵向压扁 | `stretchRatio`（0.88） |
| `ROTATE` | 按下倾斜，抬起归位 | `rotateDeg`（2.5°） |
| `BOUNCE` | 整体下沉回位 | `bounceDip`（4dp） |
| `SPIN` | **松手转满一整圈** | `spinDeg`（360，可给 720 转两圈） |
| `RIPPLE` | 暂降级为 ALPHA | `rippleAlpha` |

### 4.5 切主题过渡

```java
ThemeMotion m = mTheme.motion();
if (m.animateThemeSwitch && m.themeSwitchDurationMs > 0) {
    root.animate().cancel();
    root.setAlpha(0.6f);
    root.animate().setDuration(m.themeSwitchDurationMs).alpha(1f).start();
}
```


## 5. 拿主题与刷新

### 5.1 获取

```java
mTheme = ThemeStore.get(context);   // 带缓存，同皮肤返回同一快照
```

- `Theme` 不可变，**可以安全缓存到字段**，整个生命周期内颜色恒定。
- 不要自己 `new Theme(...)`。

### 5.2 切主题后的刷新链路

```java
ThemeStore.select(context, index);  // 持久化 + 失效缓存
// 然后让所有视图：
mTheme = ThemeStore.get(context);   // 重新取快照
applyColors();                      // 重设文字色 / 图标色 / 分隔线
applyPanel();                       // 重设背景 Drawable
invalidate();                       // 自定义 View 重绘
```

**漏掉任一步都会出现「切了主题但某处没变」。** 自定义 View 必须实现 `reloadTheme()` 并在这条链路上被调用。

### 5.3 常用查询

```java
mTheme.isDark();         // 当前皮肤是否暗色（由 preset.bg 或 forceDark 决定）
mTheme.isTranslucent();  // bg alpha < 0xff
mTheme.scrimAlpha();     // 蒙层颜色值
mTheme.preset();         // 原始 preset，读 name / terminalScheme
mTheme.motion();         // 动效参数包
mTheme.release();        // 空实现，保留仅为兼容，可调可不调
```


## 6. 标准控件模板（照抄）

### 6.1 普通 View 控件

```java
public final class FooView extends LinearLayout {
    private Theme mTheme;

    private void init() {
        mTheme = ThemeStore.get(getContext());
        setOrientation(VERTICAL);
        setBackground(mTheme.card(getContext()));   // ← 层级
        applyColors();
    }

    private void applyColors() {
        mTitle.setTextColor(mTheme.textPrimary());
        mDesc.setTextColor(mTheme.textSecondary());
        mDivider.setBackgroundColor(mTheme.color(Theme.DIVIDER));
    }

    private void applyPanel() {
        setBackground(mTheme.card(getContext()));
    }

    /** 切主题后由外部调用 */
    public void reloadTheme() {
        mTheme = ThemeStore.get(getContext());
        applyColors();
        applyPanel();
        ThemeMotionPlayer.attach(mButton, mTheme);  // 动效重挂（幂等）
        invalidate();
    }

    private TextView makeButton(String text, OnClickListener l) {
        TextView v = new TextView(getContext());
        v.setText(text);
        v.setTextColor(mTheme.onAccent());
        v.setBackground(mTheme.button(getContext()));
        v.setOnClickListener(l);
        ThemeMotionPlayer.attach(v, mTheme);        // ← 唯一一行动效
        return v;
    }
}
```

### 6.2 自定义绘制的 View

```java
@Override
protected void onDraw(Canvas c) {
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(mTheme.color(Theme.SURFACE));
    c.drawRoundRect(rect, mTheme.radius(getContext(), 8f),
                           mTheme.radius(getContext(), 8f), p);

    int sw = mTheme.stroke(getContext());
    if (sw > 0) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(mTheme.color(Theme.OUTLINE));
        c.drawRoundRect(rect, mTheme.radius(getContext(), 8f),
                               mTheme.radius(getContext(), 8f), p);
    }
}
```

需要平铺材质时用 `mTheme.textureTile(ctx)`，返回 `null` 表示皮肤不要材质。

### 6.3 列表 / RecyclerView 条目

```java
@Override
public void onBindViewHolder(VH h, int pos) {
    h.item.setBackground(mTheme.card(itemView.getContext())); // 已缓存，无构造开销
    h.title.setTextColor(mTheme.textPrimary());
    ThemeMotionPlayer.attach(h.item, mTheme);
}
```


## 7. 反例：看到必须改

```java
// ✗ 写死色值
textView.setTextColor(0xff000000);
// ✓
textView.setTextColor(mTheme.textPrimary());

// ✗ 自己判亮度
if (isNightMode()) { bg = darkBg; }
// ✓（真需要分支时）
if (mTheme.isDark()) { ... }

// ✗ 自己算圆角
g.setCornerRadius(8 * 1.5f * density);
// ✓
g.setCornerRadius(mTheme.radius(ctx, 8f));
// ✓✓ 更好
view.setBackground(mTheme.card(ctx));

// ✗ 手写按压动画
view.setOnTouchListener((v, e) -> {
    if (e.getAction() == MotionEvent.ACTION_DOWN)
        v.animate().setDuration(90).alpha(0.6f).start();
    ...
});
// ✓
ThemeMotionPlayer.attach(view, mTheme);

// ✗ 自己混色
int selected = mixColor(surface, accent, 0.18f);
// ✓
int selected = mTheme.color(Theme.SURFACE, Theme.STATE_SELECTED);

// ✗ 缓存 Theme 后切主题不刷新
Theme t = ThemeStore.get(ctx);  // 只取一次，永远不变
// ✓ 每次 reloadTheme 重新 ThemeStore.get()
```

---

## 8. 新增皮肤的三种改法

### 8.1 用现有效果工厂（最常用）

```java
new ThemePreset("果冻粉", 0xfff6eff3, 0xfff06292, 0xe6, "light",
        ThemeShape.pill(), ThemeTexture.none(), ThemeMotion.jelly()),
```

`ThemeMotion` 工厂：`standard()` / `snappy()` / `jelly()` / `stretch()` / `rotate()` / `bounce()` / `spin()` / `spinDouble()` / `alphaOnly()`。

### 8.2 自定义参数

```java
new ThemePreset("旋涡暗夜", 0xff12141a, 0xff00e5d0, 0x59, "dark",
        ThemeShape.pill(), ThemeTexture.line(0.10f),
        new ThemeMotion(ThemeMotion.PressEffect.SPIN, 140, 400, 0.92f,
                0.6f, 1.0f, 0.96f, 0f, 0f, 720f, 0.10f, true)),
```

构造参数顺序：
`(PressEffect, pressMs, themeSwitchMs, pressedScale, pressedAlpha, jellyOvershoot, stretchRatio, rotateDeg, bounceDip, spinDeg, rippleAlpha, animateThemeSwitch)`

### 8.3 全透明主题必须开 forceDark

```java
new ThemePreset("全透明", 0x00000000, 0x14ffffff, 0xff5cc97a, 0x00, "dark",
        ThemeShape.flat(), ThemeTexture.none(), ThemeMotion.standard(),
        Boolean.TRUE),   // ← 否则 alpha=0 的 bg 恒判深色，浅色背景上看不清
```

---

## 9. 新增一种点击效果（四步）

1. `ThemeMotion.PressEffect` 枚举加值（如 `SHAKE`）；
2. `ThemeMotion` 加字段 + 工厂方法；
3. `ThemeMotionPlayer.press()` / `release()` 各加一个 `case`；
4. 加一条 preset 验收。

**控件文件一行不用改**——这是这套设计的核心收益。

---

## 10. 常见问题

**Q：切了主题，某处颜色没变？**
A：该视图没实现 `reloadTheme()`，或没被切主题链路调用。检查 `ThemeStore.get()` 是否重新取了快照。

**Q：按钮按压没反应？**
A：检查是否残留了手写 `setOnTouchListener`——它会覆盖 `ThemeMotionPlayer` 的监听器。删掉即可。

**Q：`ROTATE` 转 360 为什么不转？**
A：360° 和 0° 是同一角度，动画走最短路径原地不动。用 `SPIN` 效果（`rotationBy` 累加）。

**Q：列表滚动卡顿？**
A：`page/card/tab/button` 有缓存；若用了 `surface(ctx, dp)` 逃逸口则无缓存，先确认能否换成无参入口。材质 Bitmap 平铺是另一个潜在热点，等实测掉帧再优化。

**Q：想给某个控件用别的皮肤的颜色？**
A：不支持，也不该支持。主题是全局快照，一个 Activity 内只有一套。

**Q：`Theme.release()` 要调吗？**
A：无壁纸实现下是空操作，调不调都一样。保留仅为兼容旧调用方。

---

## 11. 接入检查清单

给控件加完主题后，逐条确认：

- [ ] 没有字面量色值
- [ ] 没有字面量圆角 / density 乘法
- [ ] 没有字面量动画时长
- [ ] 背景走 `page/card/tab/button` 之一
- [ ] 文字走 `textPrimary/textSecondary/color(TEXT_DIS)`
- [ ] 动效走 `ThemeMotionPlayer.attach/wrap`
- [ ] 实现了 `reloadTheme()` 并接入切主题链路
- [ ] `reloadTheme()` 里重新 `ThemeStore.get()`
- [ ] 没有任何壁纸相关代码
- [ ] `import android.view.MotionEvent` 在不再手写按压后可以删除
