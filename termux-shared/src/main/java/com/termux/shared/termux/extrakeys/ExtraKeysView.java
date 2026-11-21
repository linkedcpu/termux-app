package com.termux.shared.termux.extrakeys;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.shared.R;
import com.termux.shared.theme.ThemeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A {@link View} showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboards.
 *
 * <p>★ 主题联动约定：控件不判断该不该圆角，只执行 {@link #applyTheme} 传入的
 * radiusPx / pill / strokePx。圆角、胶囊、描边的决策权完全属于 Theme / ThemeShape。
 *
 * <p>★ 造型必须通过 {@link MaterialButton#setCornerRadius(float)} /
 * {@link MaterialButton#setStrokeWidth(int)} 作用在 MaterialButton 自带的 MaterialShapeDrawable 上。
 * <b>禁止</b>调用 {@code setBackground(Drawable)}：Material 1.2.0 起会整体替换掉 MaterialShapeDrawable，
 * 导致圆角 / 描边 / 水波纹全部静默失效。
 *
 * <p>★ 特殊键三态：普通 / 激活(短按) / 锁定(长按)。锁定态有独立配色
 * {@link SpecialButtonState#setLockedColors(int, int)}，且必定落地，不会因透明底色被跳过而"挂不上"。
 *
 * <p>★ FIX-FILL：按钮撑满格子的三要素 —— 两个轴都声明 FILL、height 不写死、minSize/inset 清零。
 * <p>★ FIX-MARGIN：键间距由 Theme 决定，控件只执行；margin 与 FILL 可共存，无需改宽度算法。
 * <p>★ FIX-AUTOSIZE：自动缩放必须在宽高确定之后开启，否则 popup / 首帧拿不到有效约束而失效。
 */
public final class ExtraKeysView extends GridLayout {

    /** The client for the {@link ExtraKeysView}. */
    public interface IExtraKeysView {

        void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button);

        boolean performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button);
    }


    public static final int ATTR_BUTTON_TEXT_COLOR = R.attr.extraKeysButtonTextColor;
    public static final int ATTR_BUTTON_ACTIVE_TEXT_COLOR = R.attr.extraKeysButtonActiveTextColor;
    public static final int ATTR_BUTTON_BACKGROUND_COLOR = R.attr.extraKeysButtonBackgroundColor;
    public static final int ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR = R.attr.extraKeysButtonActiveBackgroundColor;

    public static final int DEFAULT_BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = 0xFF80DEEA;
    public static final int DEFAULT_BUTTON_BACKGROUND_COLOR = 0x00000000;
    public static final int DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = 0xFF7F7F7F;


    public static final int MIN_LONG_PRESS_DURATION = 200;
    public static final int MAX_LONG_PRESS_DURATION = 3000;
    public static final int FALLBACK_LONG_PRESS_DURATION = 400;

    public static final int MIN_LONG_PRESS__REPEAT_DELAY = 5;
    public static final int MAX_LONG_PRESS__REPEAT_DELAY = 2000;
    public static final int DEFAULT_LONG_PRESS_REPEAT_DELAY = 80;

    /** ★ 胶囊形态用的半径上限，MaterialButton 会自行收敛为半圆 */
    private static final float PILL_RADIUS_PX = 1000f;

    /** ★ FIX5: 锁定色相对激活色的加深量，用于主题未显式指定锁定色时自动派生 */
    private static final int LOCKED_COLOR_DARKEN_DELTA = 40;

    /** ★ FIX-AUTOSIZE: 键帽自动缩放字号区间 */
    private static final float AUTO_SIZE_MIN_SP = 3f;
    private static final float AUTO_SIZE_MAX_SP = 14f;
    private static final float AUTO_SIZE_STEP_SP = 1f;


    protected IExtraKeysView mExtraKeysViewClient;

    @NonNull
    protected Map<SpecialButton, SpecialButtonState> mSpecialButtons;

    @NonNull
    protected Set<String> mSpecialButtonsKeys;

    protected List<String> mRepetitiveKeys;

    protected int mButtonTextColor;
    protected int mButtonActiveTextColor;
    protected int mButtonBackgroundColor;
    protected int mButtonActiveBackgroundColor;

    protected boolean mButtonTextAllCaps = true;

    /* ★ 造型状态：全部来自 Theme，控件自身不做任何判断 */
    private float mRadiusPx = 0f;
    private boolean mPill = false;
    private int mStrokePx = 0;
    private int mOutlineColor = 0x00000000;

    /* ★ FIX-MARGIN: 键间距状态，由 Theme / 调用方决定，控件只执行 */
    private int mButtonMarginH = 1;
    private int mButtonMarginV = 1;

    protected int mLongPressTimeout;
    protected int mLongPressRepeatDelay;

    protected PopupWindow mPopupWindow;

    protected ScheduledExecutorService mScheduledExecutor;
    protected Handler mHandler;
    protected SpecialButtonsLongHoldRunnable mSpecialButtonsLongHoldRunnable;

    /** ★ OPT4: 工作线程写、主线程读，加 volatile 保证可见性 */
    protected volatile int mLongPressCount;

    /** ★ FIX4: 本次按下是否已触发过长按，用于抑制随后的 performClick 与误复位 */
    protected volatile boolean mLongPressTriggered;


    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setRepetitiveKeys(ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS);
        setSpecialButtons(getDefaultSpecialButtons(this));

        setButtonColors(
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_TEXT_COLOR, DEFAULT_BUTTON_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_TEXT_COLOR, DEFAULT_BUTTON_ACTIVE_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_BACKGROUND_COLOR, DEFAULT_BUTTON_BACKGROUND_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR, DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR));

        setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
        setLongPressRepeatDelay(DEFAULT_LONG_PRESS_REPEAT_DELAY);

        // ★ 不裁剪，避免圆角被 GridLayout 边缘截断
        setClipToPadding(false);
        // ★ FIX-FILL: GridLayout 自身不留内边距，否则整圈都会露底
        setPadding(0, 0, 0, 0);
    }


    public IExtraKeysView getExtraKeysViewClient() {
        return mExtraKeysViewClient;
    }

    public void setExtraKeysViewClient(IExtraKeysView extraKeysViewClient) {
        mExtraKeysViewClient = extraKeysViewClient;
    }


    public List<String> getRepetitiveKeys() {
        if (mRepetitiveKeys == null) return null;
        return mRepetitiveKeys.stream().map(String::new).collect(Collectors.toList());
    }

    public void setRepetitiveKeys(@NonNull List<String> repetitiveKeys) {
        mRepetitiveKeys = repetitiveKeys;
    }


    /** ★ OPT1: mSpecialButtons 构造期即赋非空值，去掉死分支判空 */
    @NonNull
    public Map<SpecialButton, SpecialButtonState> getSpecialButtons() {
        return mSpecialButtons.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** ★ OPT1: 同上 */
    @NonNull
    public Set<String> getSpecialButtonsKeys() {
        return mSpecialButtonsKeys.stream().map(String::new).collect(Collectors.toSet());
    }

    public void setSpecialButtons(@NonNull Map<SpecialButton, SpecialButtonState> specialButtons) {
        mSpecialButtons = specialButtons;
        mSpecialButtonsKeys = this.mSpecialButtons.keySet().stream().map(SpecialButton::getKey).collect(Collectors.toSet());
    }


    public void setButtonColors(int buttonTextColor, int buttonActiveTextColor,
                                int buttonBackgroundColor, int buttonActiveBackgroundColor) {
        mButtonTextColor = buttonTextColor;
        mButtonActiveTextColor = buttonActiveTextColor;
        mButtonBackgroundColor = buttonBackgroundColor;
        mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
    }


    public int getButtonTextColor() { return mButtonTextColor; }
    public void setButtonTextColor(int buttonTextColor) { mButtonTextColor = buttonTextColor; }

    public int getButtonActiveTextColor() { return mButtonActiveTextColor; }
    public void setButtonActiveTextColor(int buttonActiveTextColor) { mButtonActiveTextColor = buttonActiveTextColor; }

    public int getButtonBackgroundColor() { return mButtonBackgroundColor; }
    public void setButtonBackgroundColor(int buttonBackgroundColor) { mButtonBackgroundColor = buttonBackgroundColor; }

    public int getButtonActiveBackgroundColor() { return mButtonActiveBackgroundColor; }
    public void setButtonActiveBackgroundColor(int buttonActiveBackgroundColor) { mButtonActiveBackgroundColor = buttonActiveBackgroundColor; }

    public void setButtonTextAllCaps(boolean buttonTextAllCaps) {
        mButtonTextAllCaps = buttonTextAllCaps;
    }


    /* ========================================================= */
    /* ★ FIX-MARGIN: 键间距访问入口                                */
    /* ========================================================= */

    public int getButtonMarginHp() { return mButtonMarginH; }
    public int getButtonMarginVp() { return mButtonMarginV; }

    /** dp → px 便捷换算 */
    public int dp2px(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /**
     * 设置键间距（px）。会同步刷新已挂载按钮的 LayoutParams，无需 reload 即时生效。
     */
    public void setButtonMargins(int marginHp, int marginVp) {
        mButtonMarginH = Math.max(0, marginHp);
        mButtonMarginV = Math.max(0, marginVp);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (!(v.getLayoutParams() instanceof GridLayout.LayoutParams)) continue;
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) v.getLayoutParams();
            lp.setMargins(mButtonMarginH, mButtonMarginV, mButtonMarginH, mButtonMarginV);
            v.setLayoutParams(lp);
        }
    }

    /** 同时设置容器内边距（px），想让整个键盘和屏幕边缘留白时用 */
    public void setButtonMargins(int marginHp, int marginVp, boolean applyContainerPadding) {
        setButtonMargins(marginHp, marginVp);
        if (applyContainerPadding) {
            setPadding(mButtonMarginH, mButtonMarginV, mButtonMarginH, mButtonMarginV);
        }
    }


    public int getLongPressTimeout() {
        return mLongPressTimeout;
    }

    public void setLongPressTimeout(int longPressDuration) {
        if (longPressDuration >= MIN_LONG_PRESS_DURATION && longPressDuration <= MAX_LONG_PRESS_DURATION) {
            mLongPressTimeout = longPressDuration;
        } else {
            mLongPressTimeout = FALLBACK_LONG_PRESS_DURATION;
        }
    }

    public int getLongPressRepeatDelay() {
        return mLongPressRepeatDelay;
    }

    public void setLongPressRepeatDelay(int longPressRepeatDelay) {
        if (longPressRepeatDelay >= MIN_LONG_PRESS__REPEAT_DELAY && longPressRepeatDelay <= MAX_LONG_PRESS__REPEAT_DELAY) {
            mLongPressRepeatDelay = longPressRepeatDelay;
        } else {
            mLongPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY;
        }
    }


    @NonNull
    public Map<SpecialButton, SpecialButtonState> getDefaultSpecialButtons(ExtraKeysView extraKeysView) {
        return new HashMap<SpecialButton, SpecialButtonState>() {{
            put(SpecialButton.CTRL, new SpecialButtonState(extraKeysView));
            put(SpecialButton.ALT, new SpecialButtonState(extraKeysView));
            put(SpecialButton.SHIFT, new SpecialButtonState(extraKeysView));
            put(SpecialButton.FN, new SpecialButtonState(extraKeysView));
        }};
    }


    /* ========================================================= */
    /* ★ 主题联动入口：圆角 / 胶囊 / 描边决策权全在 Theme          */
    /* ========================================================= */

    public void applyTheme(int textColor, int activeTextColor,
                           int bgColor, int activeBgColor,
                           float radiusPx, boolean pill,
                           int strokePx, int outlineColor) {
        setButtonColors(textColor, activeTextColor, bgColor, activeBgColor);
        mRadiusPx = Math.max(0f, radiusPx);
        mPill = pill;
        mStrokePx = Math.max(0, strokePx);
        mOutlineColor = outlineColor;

        // ★ FIX5: 锁定态配色由激活色派生并加深，确保与短按激活态可区分、且必定落地
        int lockedBg = deriveLockedColor(activeBgColor);
        for (SpecialButtonState st : mSpecialButtons.values()) {
            st.setLockedColors(activeTextColor, lockedBg);
        }

        refreshButtonColors();
    }

    /** ★ FIX-MARGIN: 带间距的主题入口（重载，不破坏旧调用方） */
    public void applyTheme(int textColor, int activeTextColor,
                           int bgColor, int activeBgColor,
                           float radiusPx, boolean pill,
                           int strokePx, int outlineColor,
                           int marginHp, int marginVp) {
        mButtonMarginH = Math.max(0, marginHp);
        mButtonMarginV = Math.max(0, marginVp);
        applyTheme(textColor, activeTextColor, bgColor, activeBgColor,
                   radiusPx, pill, strokePx, outlineColor);
    }

    /** ★ FIX5: 由激活底色派生锁定底色（加深一档）；透明则直接沿用激活色 */
    private int deriveLockedColor(int activeBgColor) {
        if (activeBgColor == 0) return mButtonActiveBackgroundColor;
        int a = Color.alpha(activeBgColor);
        if (a == 0) return mButtonActiveBackgroundColor;
        int r = Math.max(0, Color.red(activeBgColor) - LOCKED_COLOR_DARKEN_DELTA);
        int g = Math.max(0, Color.green(activeBgColor) - LOCKED_COLOR_DARKEN_DELTA);
        int b = Math.max(0, Color.blue(activeBgColor) - LOCKED_COLOR_DARKEN_DELTA);
        return Color.argb(a, r, g, b);
    }

    /** 按当前四色 + 造型参数重刷已挂载的所有按钮（reload 之后调） */
    public void refreshButtonColors() {
        refreshButtonColors(null);
    }

    /** ★ OPT6: 可只刷单个 special 键，避免全量重刷 */
    public void refreshButtonColors(@Nullable SpecialButton only) {
        if (only == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (!(v instanceof MaterialButton)) continue;
                MaterialButton b = (MaterialButton) v;
                b.setTextColor(mButtonTextColor);
                tintKey(b, mButtonBackgroundColor, false, false);
            }
        }
        for (Map.Entry<SpecialButton, SpecialButtonState> e : mSpecialButtons.entrySet()) {
            if (only != null && e.getKey() != only) continue;
            SpecialButtonState st = e.getValue();
            if (st == null || st.buttons == null) continue;
            // ★ FIX5: 三态取色 —— 锁定 > 激活 > 普通
            boolean active = st.getIsActive();
            boolean locked = st.getIsLocked();
            int color = resolveTextColor(st, active, locked);
            int bg = resolveBgColor(st, active, locked);
            for (MaterialButton b : st.buttons) {
                if (b == null) continue;
                b.setTextColor(color);
                styleKey(b, bg, active, locked);
            }
        }
    }

    /** ★ FIX5: 文字色三态解析 */
    private int resolveTextColor(SpecialButtonState st, boolean active, boolean locked) {
        if (locked && st.lockedTextColor != 0) return st.lockedTextColor;
        if (active) return mButtonActiveTextColor;
        return mButtonTextColor;
    }

    /** ★ FIX5: 底色三态解析；结果为 0 时兜底到激活色，保证锁定态一定有颜色可挂 */
    private int resolveBgColor(SpecialButtonState st, boolean active, boolean locked) {
        if (locked && st.lockedBgColor != 0) return st.lockedBgColor;
        if (active && mButtonActiveBackgroundColor != 0) return mButtonActiveBackgroundColor;
        if (locked) return mButtonActiveBackgroundColor;
        return mButtonBackgroundColor;
    }

    /**
     * ★ 造型落点：只动 MaterialButton 自带的 MaterialShapeDrawable。
     * 绝不调用 setBackground(Drawable)——那会整体替换掉 ShapeDrawable，导致圆角/描边/水波纹失效。
     */
    private void styleKey(@Nullable MaterialButton b, int bgColor, boolean active, boolean locked) {
        if (b == null) return;
        b.setCornerRadius((int) (mPill ? PILL_RADIUS_PX : mRadiusPx));
        b.setStrokeWidth(mStrokePx);
        tintKey(b, bgColor, active, locked);
    }

    /** ★ OPT7 + FIX5: 按下/抬起只换色；锁定态必定落地，不被透明色跳过 */
    private void tintKey(@Nullable MaterialButton b, int bgColor, boolean active, boolean locked) {
        if (b == null) return;
        if (mStrokePx > 0) {
            b.setStrokeColor(ColorStateList.valueOf(
                (active || locked) ? mButtonActiveTextColor : mOutlineColor));
        }
        int resolved = bgColor;
        if (resolved == 0) resolved = (active || locked) ? mButtonActiveBackgroundColor : mButtonBackgroundColor;
        b.setBackgroundTintList(ColorStateList.valueOf(resolved));
        b.invalidate();
    }


    /* ========================================================= */
    /* ★ FIX-FILL / FIX-AUTOSIZE: 按钮尺寸与字号归一化入口         */
    /* ========================================================= */

    /**
     * 去掉 {@code buttonBarButtonStyle} 带来的默认最小尺寸 / inset / 文字折行，
     * 否则按钮会被这些隐形占位顶出留白，撑不满 GridLayout 分给它的格子。
     *
     * <p>★ 注意：这里【不】开启自动缩放。自动缩放必须在宽高确定之后调用，
     * 否则 popup / 首帧测量宽高为 0，TextView 拿不到有效约束而静默失效。
     */
    private static void normalizeButtonSize(@NonNull MaterialButton button) {
        button.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            button.setPaddingRelative(0, 0, 0, 0);
        }
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        // ★ MaterialButton 1.3+ 的额外上下 inset，会吃掉高度
        try {
            button.setInsetTop(0);
            button.setInsetBottom(0);
        } catch (Throwable ignored) {
            // 老版本 Material 无此方法，忽略
        }
        // button.setMaxLines(1);
        // button.setSingleLine(true);
    }

    /**
     * ★ FIX-AUTOSIZE: 开启 6~14sp 自动缩放。
     * 必须在 setWidth/setHeight 之后、或 attach 完成后（post）调用才生效。
     * 与手动 setTextSize 互斥，二者不可同时使用。
     */
    private static void applyAutoSize(@NonNull MaterialButton button) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            button.setAutoSizeTextTypeUniformWithConfiguration(
                Math.round(AUTO_SIZE_MIN_SP),
                Math.round(AUTO_SIZE_MAX_SP),
                Math.round(AUTO_SIZE_STEP_SP),
                TypedValue.COMPLEX_UNIT_SP);
        } else {
            button.setEllipsize(TextUtils.TruncateAt.END);
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    public void reload(ExtraKeysInfo extraKeysInfo, float heightPx) {
        if (extraKeysInfo == null)
            return;

        // ★ OPT3: 先清旧引用再重置，避免 reload 累积持有旧按钮
        for (SpecialButtonState state : mSpecialButtons.values()) {
            if (state.buttons != null) state.buttons.clear();
            state.buttons = new ArrayList<>();
        }

        removeAllViews();

        ExtraKeyButton[][] buttons = extraKeysInfo.getMatrix();

        setRowCount(buttons.length);
        setColumnCount(maximumLength(buttons));

        // ★ FIX-FILL + FIX-MARGIN: 让 GridLayout 总高确定，rowSpec 的 FILL 才有空间可撑；
        // 纵向 margin 会吃掉高度，这里补回来，保证每行净高仍等于 heightPx
        if (heightPx > 0) {
            int totalHeight = Math.round(heightPx * buttons.length)
                            + mButtonMarginV * (buttons.length - 1);
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp != null) {
                lp.height = totalHeight;
                setLayoutParams(lp);
            }
            setMinimumHeight(totalHeight);
        }

        for (int row = 0; row < buttons.length; row++) {
            for (int col = 0; col < buttons[row].length; col++) {
                final ExtraKeyButton buttonInfo = buttons[row][col];

                final MaterialButton button;
                final boolean special = isSpecialButton(buttonInfo);
                if (special) {
                    button = createSpecialButton(buttonInfo.getKey(), true);
                    if (button == null) continue;
                } else {
                    button = new MaterialButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
                }

                button.setText(buttonInfo.getDisplay());
                button.setTextColor(mButtonTextColor);
                button.setAllCaps(mButtonTextAllCaps);
                // ★ FIX-FILL: 统一清掉默认最小尺寸 / inset / 折行
                normalizeButtonSize(button);
                // ★ FIX-AUTOSIZE: 先开一次；宽高要在 layout 后才有，故 post 里再兜一次
                applyAutoSize(button);
                button.post(() -> applyAutoSize(button));
                // ★ 应用当前皮肤造型（圆角/描边/底色），不走 setBackground
                styleKey(button, mButtonBackgroundColor, false, false);

                button.setOnClickListener(view -> {
                    performExtraKeyButtonHapticFeedback(view, buttonInfo, button);
                    onAnyExtraKeyButtonClick(view, buttonInfo, button);
                });

                button.setOnTouchListener((view, event) -> {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // ★ 按下态只 tint，保留圆角（OPT7）
                            tintKey((MaterialButton) view, mButtonActiveBackgroundColor, true, false);
                            startScheduledExecutors(view, buttonInfo, button);
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            // ★ FIX4: 只有带 popup 的键才响应上滑；special 键保持按下态等长按触发
                            if (buttonInfo.getPopup() != null) {
                                if (mPopupWindow == null && event.getY() < 0) {
                                    stopScheduledExecutors();
                                    tintKey((MaterialButton) view, mButtonBackgroundColor, false, false);
                                    showPopup(view, buttonInfo.getPopup());
                                }
                                if (mPopupWindow != null && event.getY() > 0) {
                                    tintKey((MaterialButton) view, mButtonActiveBackgroundColor, true, false);
                                    dismissPopup();
                                }
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            // ★ FIX4 + FIX5: 长按已触发则保留锁定态，不复位
                            if (!mLongPressTriggered) {
                                tintKey((MaterialButton) view, mButtonBackgroundColor, false, false);
                            } else if (special) {
                                applySpecialRestingTint((MaterialButton) view, buttonInfo.getKey());
                            }
                            stopScheduledExecutors();
                            return true;

                        case MotionEvent.ACTION_UP:
                            // ★ FIX5: 锁定中的键抬起后回到锁定色，不被打回普通色
                            if (special && mLongPressTriggered) {
                                applySpecialRestingTint((MaterialButton) view, buttonInfo.getKey());
                            } else {
                                tintKey((MaterialButton) view, mButtonBackgroundColor, false, false);
                            }
                            stopScheduledExecutors();
                            // ★ FIX4: 长按已消费本次按下，抬起时不再翻转
                            if (mLongPressTriggered) {
                                mLongPressTriggered = false;
                                return true;
                            }
                            if (mLongPressCount == 0 || mPopupWindow != null) {
                                if (mPopupWindow != null) {
                                    dismissPopup();
                                    if (buttonInfo.getPopup() != null) {
                                        onAnyExtraKeyButtonClick(view, buttonInfo.getPopup(), button);
                                    }
                                } else {
                                    view.performClick();
                                }
                            }
                            return true;

                        default:
                            return true;
                    }
                });

                // ★ FIX-FILL: 两个轴都声明 FILL —— 少一个轴，那个轴就按 wrap_content 排，留下空白
                // ★ FIX-MARGIN: margin 与 FILL 可共存，按钮自动填满「格子减去 margin」的空间
                LayoutParams param = new GridLayout.LayoutParams();
                param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1f);
                param.rowSpec = GridLayout.spec(row, GridLayout.FILL, 1f);
                param.width = 0;   // 配合 columnSpec 的 FILL+weight，由 GridLayout 分配宽度
                param.height = 0;  // ★ 不要再钉死 heightPx，否则 rowSpec 的 FILL 无空间可拉伸
                param.setMargins(mButtonMarginH, mButtonMarginV, mButtonMarginH, mButtonMarginV);
                button.setLayoutParams(param);

                addView(button);
            }
        }
    }

    /** ★ FIX5: 特殊键抬起后的静止态着色（锁定则锁定色，否则激活/普通色） */
    private void applySpecialRestingTint(@NonNull MaterialButton button, @NonNull String key) {
        SpecialButtonState st = mSpecialButtons.get(SpecialButton.valueOf(key));
        if (st == null) {
            tintKey(button, mButtonBackgroundColor, false, false);
            return;
        }
        boolean active = st.getIsActive();
        boolean locked = st.getIsLocked();
        button.setTextColor(resolveTextColor(st, active, locked));
        tintKey(button, resolveBgColor(st, active, locked), active, locked);
    }


    public void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (mExtraKeysViewClient != null)
            mExtraKeysViewClient.onExtraKeyButtonClick(view, buttonInfo, button);
    }

    public void performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (mExtraKeysViewClient != null) {
            if (mExtraKeysViewClient.performExtraKeyButtonHapticFeedback(view, buttonInfo, button))
                return;
        }

        if (Settings.System.getInt(getContext().getContentResolver(),
            Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {

            if (Build.VERSION.SDK_INT >= 28) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } else {
                if (Settings.Global.getInt(getContext().getContentResolver(), "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            }
        }
    }


    public void onAnyExtraKeyButtonClick(View view, @NonNull ExtraKeyButton buttonInfo, MaterialButton button) {
        if (isSpecialButton(buttonInfo)) {
            if (mLongPressCount > 0) return;
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null) return;

            state.setIsActive(!state.getIsActive());
            if (!state.getIsActive())
                state.setIsLocked(false);
            // ★ FIX: setIsActive / setIsLocked 内部已触发 refreshButtonColors()
        } else {
            onExtraKeyButtonClick(view, buttonInfo, button);
        }
    }


    public void startScheduledExecutors(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        stopScheduledExecutors();
        mLongPressCount = 0;
        mLongPressTriggered = false;   // ★ FIX4
        if (mRepetitiveKeys.contains(buttonInfo.getKey())) {
            mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            mScheduledExecutor.scheduleWithFixedDelay(() -> {
                mLongPressCount++;
                onExtraKeyButtonClick(view, buttonInfo, button);
            }, mLongPressTimeout, mLongPressRepeatDelay, TimeUnit.MILLISECONDS);
        } else if (isSpecialButton(buttonInfo)) {
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null) return;
            if (mHandler == null)
                mHandler = new Handler(Looper.getMainLooper());
            mSpecialButtonsLongHoldRunnable = new SpecialButtonsLongHoldRunnable(state);
            mHandler.postDelayed(mSpecialButtonsLongHoldRunnable, mLongPressTimeout);
        }
    }

    public void stopScheduledExecutors() {
        if (mScheduledExecutor != null) {
            mScheduledExecutor.shutdownNow();
            mScheduledExecutor = null;
        }

        if (mSpecialButtonsLongHoldRunnable != null && mHandler != null) {
            mHandler.removeCallbacks(mSpecialButtonsLongHoldRunnable);
            mSpecialButtonsLongHoldRunnable = null;
        }
    }

    public class SpecialButtonsLongHoldRunnable implements Runnable {
        public final SpecialButtonState mState;

        public SpecialButtonsLongHoldRunnable(SpecialButtonState state) {
            mState = state;
        }

        public void run() {
            // ★ FIX3: 长按 = 在「锁定激活」与「完全关闭」间切换，避免出现 isActive=false 的无高亮态
            boolean activate = !mState.getIsActive();
            mState.setActiveAndLocked(activate, activate);
            mLongPressCount++;
            mLongPressTriggered = true;   // ★ FIX4: 标记长按已消费，抬起时不再翻转
        }
    }


    void showPopup(View view, ExtraKeyButton extraButton) {
        int width = view.getMeasuredWidth();
        int height = view.getMeasuredHeight();
        MaterialButton button;
        if (isSpecialButton(extraButton)) {
            button = createSpecialButton(extraButton.getKey(), false);
            if (button == null) return;
        } else {
            button = new MaterialButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
            button.setTextColor(mButtonTextColor);
        }
        button.setText(extraButton.getDisplay());
        button.setAllCaps(mButtonTextAllCaps);
        normalizeButtonSize(button);
        // ★ FIX-AUTOSIZE: 先定尺寸，再开自动缩放 —— 顺序反了就不生效
        button.setWidth(width);
        button.setHeight(height*2);
        applyAutoSize(button);
        styleKey(button, mButtonActiveBackgroundColor, true, false);

        // ★ 直接以 button 为 contentView，避免测量时把整个 ExtraKeysView 当参考系
        mPopupWindow = new PopupWindow(button,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setFocusable(false);
        mPopupWindow.showAsDropDown(view, 0, -5 * height);

        // ★ 双保险：popup 完成布局后再触发一次缩放计算，此时按钮已有真实宽高
        button.post(() -> applyAutoSize(button));
    }

    /** ★ OPT5: 判空后再 dismiss，避免 showPopup 提前返回时 NPE */
    public void dismissPopup() {
        if (mPopupWindow == null) return;
        mPopupWindow.setContentView(null);
        mPopupWindow.dismiss();
        mPopupWindow = null;
    }


    public boolean isSpecialButton(ExtraKeyButton button) {
        return mSpecialButtonsKeys.contains(button.getKey());
    }

    @Nullable
    public Boolean readSpecialButton(SpecialButton specialButton, boolean autoSetInActive) {
        SpecialButtonState state = mSpecialButtons.get(specialButton);
        if (state == null) return null;

        // ★ OPT2: 统一走 getter，和「状态变更走 setter」的约定一致
        if (!state.getIsCreated() || !state.getIsActive())
            return false;

        if (autoSetInActive && !state.getIsLocked())
            state.setIsActive(false);   // ★ FIX: setIsActive 内部已触发 refreshButtonColors()

        return true;
    }

    public MaterialButton createSpecialButton(String buttonKey, boolean needUpdate) {
        SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonKey));
        if (state == null) return null;
        state.setIsCreated(true);
        MaterialButton button = new MaterialButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
        // ★ FIX-FILL: special 键也清掉默认最小尺寸/inset，否则它会把整行顶出留白
        normalizeButtonSize(button);
        applyAutoSize(button);   // ★ FIX-AUTOSIZE
        boolean active = state.getIsActive();
        boolean locked = state.getIsLocked();
        button.setTextColor(resolveTextColor(state, active, locked));
        styleKey(button, resolveBgColor(state, active, locked), active, locked);
        if (needUpdate) {
            state.buttons.add(button);
        }
        return button;
    }


    /** ★ OPT9: 空行防御 */
    public static int maximumLength(Object[][] matrix) {
        int m = 0;
        for (Object[] row : matrix) {
            if (row != null) m = Math.max(m, row.length);
        }
        return m;
    }


    /* ========================================================= */
    /* ★ SpecialButtonState —— 状态变化即触发宿主重绘              */
    /* ========================================================= */

    /** Holds the state for a {@link SpecialButton}. */
    public static class SpecialButtonState {
        public final ExtraKeysView extraKeysView;
        public boolean isCreated = false;
        public boolean isActive = false;
        public boolean isLocked = false;
        public List<MaterialButton> buttons = new ArrayList<>();

        /** ★ FIX5: 锁定态专属配色；0 表示回退到普通激活色 */
        public int lockedTextColor = 0;
        public int lockedBgColor = 0;

        public SpecialButtonState(ExtraKeysView extraKeysView) {
            this.extraKeysView = extraKeysView;
        }

        public boolean getIsCreated() { return isCreated; }
        public void setIsCreated(boolean value) { this.isCreated = value; }

        public boolean getIsActive() { return isActive; }
        public void setIsActive(boolean value) {
            this.isActive = value;
            // ★ FIX: 状态一变即重绘，调用方无需再记着 refreshButtonColors()
            if (extraKeysView != null) extraKeysView.refreshButtonColors();
        }

        public boolean getIsLocked() { return isLocked; }
        public void setIsLocked(boolean value) {
            this.isLocked = value;
            // ★ FIX: 锁定态变化同样要重绘
            if (extraKeysView != null) extraKeysView.refreshButtonColors();
        }

        /** ★ FIX3: 原子地设置激活与锁定，避免两个 setter 各触发一次中间态重绘 */
        public void setActiveAndLocked(boolean active, boolean locked) {
            this.isActive = active;
            this.isLocked = locked;
            if (extraKeysView != null) extraKeysView.refreshButtonColors();
        }

        /** ★ FIX5: 设置锁定态专属配色 */
        public void setLockedColors(int textColor, int bgColor) {
            this.lockedTextColor = textColor;
            this.lockedBgColor = bgColor;
            if (extraKeysView != null) extraKeysView.refreshButtonColors();
        }
    }

}
