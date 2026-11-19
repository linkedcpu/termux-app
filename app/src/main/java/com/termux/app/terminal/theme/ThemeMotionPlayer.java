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

    /** 指定效果接入（临时覆盖单个控件） */
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

    private static void press(View v, ThemeMotion m) {
        v.animate().cancel();
        long d = m.pressDurationMs;
        switch (m.pressEffect) {
            case SCALE:
                v.animate().setDuration(d)
                        .scaleX(m.pressedScale).scaleY(m.pressedScale).start();
                break;
            case JELLY:
                float deep = m.pressedScale / m.jellyOvershoot;
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
                // ★ 按下只给一点缩小反馈，不转；真正的整圈在 release 里做
                v.setRotation(0f);
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
        v.animate().cancel();
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
