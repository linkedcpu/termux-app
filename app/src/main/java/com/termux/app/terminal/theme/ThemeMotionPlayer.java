package com.termux.app.terminal.theme;

import android.view.MotionEvent;
import android.view.View;

/** 动效执行器：控件只报事件，不写算法 */
public final class ThemeMotionPlayer {

    private ThemeMotionPlayer() { }

    /** 常规接入：动效由当前皮肤决定 */
    public static void attach(View v, Theme t) {
        attach(v, t == null ? null : t.motion());
    }

    /**
     * 指定效果接入（临时覆盖单个控件）。
     * 覆盖式挂载：每次调用都会替换旧 listener，不判重——
     * 因此换肤后重调 attach 即可让新皮肤的动效生效。
     */
    public static void attach(final View v, final ThemeMotion m) {
        if (v == null) return;
        if (m == null || m.pressEffect == ThemeMotion.PressEffect.NONE || m.pressDurationMs <= 0) {
            v.setOnTouchListener(null);   // 干脆模式：不接管，零开销
            return;
        }
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    press(view, m);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    release(view, m);
                    break;
            }
            return false;   // 绝不拦截，onClick 照常触发
        });
    }

    /**
     * 控件已有自己的 OnTouchListener 时用这个：
     * 先跑动效，再把事件原样转交，避免互相覆盖。
     */
    public static void wrap(final View v, final ThemeMotion m,
                            final View.OnTouchListener delegate) {
        if (v == null) return;
        if (m == null || m.pressEffect == ThemeMotion.PressEffect.NONE || m.pressDurationMs <= 0) {
            v.setOnTouchListener(delegate);
            return;
        }
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    press(view, m);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    release(view, m);
                    break;
            }
            return delegate != null && delegate.onTouch(view, e);
        });
    }

    /**
     * ★ 供控件在重建 / 换肤 / 隐藏前主动归位动效属性。
     * 典型用法：SessionTabBar.render() 重建 ＋/☆/≡ 之前调用，
     * 避免正在播的 SPIN / SCALE 把 rotation、alpha 残留到新视图上。
     */
    public static void reset(View v) {
        safeCancel(v);
    }

    /**
     * ★ cancel 只中断播放、不回滚属性，这是「点 ＋ 动画卡在半路」的根因：
     * rotationBy 被掐断时 rotation 停在中间角度（如 137°），
     * scaleX / alpha 同理会停在 0.92 / 0.6。
     * 且 cancel() 时 withEndAction 不保证执行，必须在同一次调用里同步归位，
     * 不能依赖动画结束回调。
     */
    private static void safeCancel(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.clearAnimation();
        v.setRotation(0f);
        v.setRotationX(0f);
        v.setRotationY(0f);
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
    }

    private static void press(View v, ThemeMotion m) {
        safeCancel(v);
        long d = m.pressDurationMs;
        switch (m.pressEffect) {
            case SCALE:
                v.animate().setDuration(d)
                        .scaleX(m.pressedScale).scaleY(m.pressedScale).start();
                break;
            case JELLY:
                // ★ 除零保护：jellyOvershoot 为 0 时 deep 会变 Infinity，按钮直接消失
                float denom = (m.jellyOvershoot > 0.01f) ? m.jellyOvershoot : 1f;
                float deep = m.pressedScale / denom;
                v.animate().setDuration(d).scaleX(deep).scaleY(deep).start();
                break;
            case STRETCH:
                v.animate().setDuration(d).scaleY(m.stretchRatio).start();
                break;
            case ROTATE:
                centerPivot(v);
                v.animate().setDuration(d).rotation(m.rotateDeg).start();
                break;
            case BOUNCE:
                v.animate().setDuration(d)
                        .translationY(m.bounceDip * Theme.density(v.getContext())).start();
                break;
            case SPIN:
                // ★ 按下只给一点缩小反馈，不转；真正的整圈在 release 里做。
                //   起转前先归零 rotation + scale，双保险防止残留累积。
                v.setRotation(0f);
                v.setScaleX(1f);
                v.setScaleY(1f);
                v.animate().setDuration(d)
                        .scaleX(m.pressedScale).scaleY(m.pressedScale).start();
                break;
            case RIPPLE:
            case ALPHA:
            default:
                v.animate().setDuration(d).alpha(m.pressedAlpha).start();
        }
    }

    private static void release(View v, ThemeMotion m) {
        safeCancel(v);
        long d = m.pressDurationMs;
        switch (m.pressEffect) {
            case SCALE:
            case JELLY:
                v.animate().setDuration(d).scaleX(1f).scaleY(1f).start();
                break;
            case STRETCH:
                v.animate().setDuration(d).scaleY(1f).start();
                break;
            case ROTATE:
                v.animate().setDuration(d).rotation(0f).start();
                break;
            case BOUNCE:
                v.animate().setDuration(d).translationY(0f).start();
                break;
            case SPIN:
                // ★ rotationBy 相对累加，每次点击都真的转一整圈；转完复位为 0
                centerPivot(v);
                v.setRotation(0f);
                v.animate()
                        .rotationBy(m.spinDeg)
                        .scaleX(1f).scaleY(1f)
                        .setDuration(Math.max(d, 320))
                        .withEndAction(() -> v.setRotation(0f))
                        .start();
                break;
            case RIPPLE:
            case ALPHA:
            default:
                v.animate().setDuration(d).alpha(1f).start();
        }
    }

    /** 旋转前把轴心复位到中心，防止绕左上角转 */
    private static void centerPivot(View v) {
        if (v == null) return;
        float w = v.getWidth();
        float h = v.getHeight();
        v.setPivotX(w > 0 ? w / 2f : v.getPivotX());
        v.setPivotY(h > 0 ? h / 2f : v.getPivotY());
    }
}
