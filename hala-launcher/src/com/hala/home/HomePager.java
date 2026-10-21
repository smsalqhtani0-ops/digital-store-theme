package com.hala.home;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

/**
 * Home screen pages side by side, swiped horizontally and snapped to a page. Right to left,
 * the first page is on the right and the next one comes in from the left.
 */
final class HomePager extends ViewGroup {
    interface Listener {
        void onPageChanged(int page, int count);
    }

    private final OverScroller scroller;
    private final int touchSlop;
    private final int minFling;
    private VelocityTracker velocity;
    private float downX;
    private float downY;
    private float lastX;
    private boolean dragging;
    private int current;
    private Listener listener;

    HomePager(Context c) {
        super(c);
        scroller = new OverScroller(c, new DecelerateInterpolator(2f));
        ViewConfiguration vc = ViewConfiguration.get(c);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
        setClipChildren(false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    int current() {
        return current;
    }

    int count() {
        return getChildCount();
    }

    private boolean rtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    /** Horizontal scroll position at which page `page` fills the view. */
    private int offsetOf(int page) {
        int visual = rtl() ? getChildCount() - 1 - page : page;
        return visual * getWidth();
    }

    private int pageAtScroll(int scroll) {
        int w = Math.max(1, getWidth());
        int visual = Math.round((float) scroll / w);
        visual = Math.max(0, Math.min(getChildCount() - 1, visual));
        return rtl() ? getChildCount() - 1 - visual : visual;
    }

    void snapTo(int page, boolean animate) {
        page = Math.max(0, Math.min(getChildCount() - 1, page));
        int target = offsetOf(page);
        if (!scroller.isFinished()) {
            scroller.abortAnimation();
        }
        if (animate && getWidth() > 0) {
            scroller.startScroll(getScrollX(), 0, target - getScrollX(), 0, 360);
            postInvalidateOnAnimation();
        } else {
            scrollTo(target, 0);
        }
        if (page != current) {
            current = page;
        }
        if (listener != null) {
            listener.onPageChanged(current, getChildCount());
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        for (int i = 0; i < getChildCount(); i++) {
            int visual = rtl() ? getChildCount() - 1 - i : i;
            getChildAt(i).layout(visual * w, 0, visual * w + w, h);
        }
        // Keep the current page in view after a resize (rotation), unless a swipe is under way.
        if (scroller.isFinished() && !dragging) {
            current = Math.min(current, Math.max(0, getChildCount() - 1));
            scrollTo(offsetOf(current), 0);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = !scroller.isFinished();
                if (dragging) {
                    scroller.abortAnimation();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(e.getX() - downX);
                float dy = Math.abs(e.getY() - downY);
                if (!dragging && dx > touchSlop && dx > dy && getChildCount() > 1) {
                    dragging = true;
                    lastX = e.getX();
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            default:
                break;
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (velocity == null) {
            velocity = VelocityTracker.obtain();
        }
        velocity.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!dragging && Math.abs(e.getX() - downX) > touchSlop) {
                    dragging = true;
                }
                if (dragging) {
                    int dx = Math.round(lastX - e.getX());
                    int max = Math.max(0, (getChildCount() - 1) * getWidth());
                    int next = Math.max(-getWidth() / 6, Math.min(max + getWidth() / 6, getScrollX() + dx));
                    scrollTo(next, 0);
                }
                lastX = e.getX();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging) {
                    velocity.computeCurrentVelocity(1000);
                    float vx = velocity.getXVelocity();
                    int page = pageAtScroll(getScrollX());
                    if (Math.abs(vx) > minFling) {
                        // A fling moves one page the way the finger went.
                        int visualNow = rtl() ? getChildCount() - 1 - current : current;
                        int visualNext = visualNow + (vx < 0 ? 1 : -1);
                        visualNext = Math.max(0, Math.min(getChildCount() - 1, visualNext));
                        page = rtl() ? getChildCount() - 1 - visualNext : visualNext;
                    }
                    snapTo(page, true);
                }
                dragging = false;
                velocity.recycle();
                velocity = null;
                return true;
            }
            default:
                return true;
        }
    }

    @Override
    public void addView(View child) {
        super.addView(child);
        requestLayout();
    }
}
