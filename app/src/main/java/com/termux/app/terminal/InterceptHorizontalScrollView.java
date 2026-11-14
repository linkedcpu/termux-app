package com.termux.app.terminal;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;

import androidx.annotation.Nullable;

public class InterceptHorizontalScrollView extends HorizontalScrollView {

    private static final int DIRECTION_UNKNOWN = 0;
    private static final int DIRECTION_HORIZONTAL = 1;
    private static final int DIRECTION_VERTICAL = 2;

    private final int mTouchSlop;

    private float mDownX;
    private float mDownY;
    private int mDirection = DIRECTION_UNKNOWN;
    private boolean mHasDispatchedScroll;

    public InterceptHorizontalScrollView(Context context) {
        this(context, null);
    }

    public InterceptHorizontalScrollView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InterceptHorizontalScrollView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFillViewport(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                mDirection = DIRECTION_UNKNOWN;
                mHasDispatchedScroll = false;
                setDrawerInterceptEnabled(false);
                super.onInterceptTouchEvent(event);
                return false;

            case MotionEvent.ACTION_MOVE:
                {
                    updateDirection(event);
                    boolean horizontal = mDirection == DIRECTION_HORIZONTAL;
                    setDrawerInterceptEnabled(horizontal);
                    if (horizontal) {
                        return true;
                    }
                    return super.onInterceptTouchEvent(event);
                }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetTouchState();
                break;
        }

        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                mDirection = DIRECTION_UNKNOWN;
                mHasDispatchedScroll = false;
                setDrawerInterceptEnabled(false);
                break;

            case MotionEvent.ACTION_MOVE:
                updateDirection(event);
                if (mDirection == DIRECTION_VERTICAL) {
                    setDrawerInterceptEnabled(false);
                    return false;
                }
                if (mDirection == DIRECTION_HORIZONTAL) {
                    setDrawerInterceptEnabled(true);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetTouchState();
                break;
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (l != oldl && !mHasDispatchedScroll) {
            mHasDispatchedScroll = true;
            // 滑动开始后，即使方向判定未完成，也不让抽屉中断。
            setDrawerInterceptEnabled(true);
        }
    }

    private void updateDirection(MotionEvent event) {
        if (mDirection == DIRECTION_VERTICAL) {
            return;
        }

        float dx = event.getX() - mDownX;
        float dy = event.getY() - mDownY;
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);

        if (absDx < mTouchSlop && absDy < mTouchSlop) {
            return;
        }

        mDirection = absDx > absDy ? DIRECTION_HORIZONTAL : DIRECTION_VERTICAL;
    }

    /** enabled 为 true 表示禁止父 View（DrawerLayout）拦截触摸事件。 */
    private void setDrawerInterceptEnabled(boolean enabled) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(enabled);
        }
    }

    private void resetTouchState() {
        mDirection = DIRECTION_UNKNOWN;
        mHasDispatchedScroll = false;
        setDrawerInterceptEnabled(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setDrawerInterceptEnabled(false);
    }
}
