package com.hala.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * One home screen page: a `columns × rows` grid of app icons. While an icon is dragged over it,
 * the cell it would land in is outlined.
 */
final class CellLayout extends ViewGroup {
    interface Host {
        /** Drop the dragged app into home slot `slot`. */
        void dropOnHome(int slot, DragPayload p);

        /** The drag moved to screen point (`x`, `y`). */
        void dragMoved(float x, float y);

        /** The drag rests near an edge of the page: -1 start side, 1 end side, 0 neither. */
        void dragAtEdge(int side);
    }

    final int page;
    private final int columns;
    private final int rows;
    private final Host host;
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int hoverCell = -1;

    CellLayout(Context c, int page, int columns, int rows, Host host) {
        super(c);
        this.page = page;
        this.columns = columns;
        this.rows = rows;
        this.host = host;
        setWillNotDraw(false);
        setClipChildren(false);
        highlight.setColor(0x40FFFFFF);
        setOnDragListener(new OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent e) {
                return handleDrag(e);
            }
        });
    }

    /** Add `child` at `cell` (0 … columns × rows − 1). */
    void addAt(View child, int cell) {
        child.setTag(R_CELL, cell);
        addView(child);
    }

    private static final int R_CELL = 0x7f0b0001;

    int cellAt(float x, float y) {
        int cw = getWidth() / columns;
        int ch = getHeight() / rows;
        if (cw <= 0 || ch <= 0) {
            return -1;
        }
        int col = (int) Math.max(0, Math.min(columns - 1, x / cw));
        int row = (int) Math.max(0, Math.min(rows - 1, y / ch));
        // Right to left, the first column is on the right.
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            col = columns - 1 - col;
        }
        return row * columns + col;
    }

    private boolean handleDrag(DragEvent e) {
        Object state = e.getLocalState();
        if (!(state instanceof DragPayload)) {
            return false;
        }
        DragPayload p = (DragPayload) state;
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_LOCATION: {
                int[] at = new int[2];
                getLocationOnScreen(at);
                host.dragMoved(at[0] + e.getX(), at[1] + e.getY());
                setHover(cellAt(e.getX(), e.getY()));
                float edge = getWidth() * 0.06f;
                boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
                if (e.getX() < edge) {
                    host.dragAtEdge(rtl ? 1 : -1);
                } else if (e.getX() > getWidth() - edge) {
                    host.dragAtEdge(rtl ? -1 : 1);
                } else {
                    host.dragAtEdge(0);
                }
                return true;
            }
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                setHover(-1);
                host.dragAtEdge(0);
                return true;
            case DragEvent.ACTION_DROP: {
                int cell = cellAt(e.getX(), e.getY());
                setHover(-1);
                host.dragAtEdge(0);
                if (cell < 0) {
                    return false;
                }
                host.dropOnHome(page * columns * rows + cell, p);
                return true;
            }
            default:
                return true;
        }
    }

    private void setHover(int cell) {
        if (cell != hoverCell) {
            hoverCell = cell;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        int cw = w / columns;
        int ch = h / rows;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.AT_MOST));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int cw = w / columns;
        int ch = (b - t) / rows;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int cell = (Integer) child.getTag(R_CELL);
            int col = cell % columns;
            int row = cell / columns;
            if (rtl) {
                col = columns - 1 - col;
            }
            int x = col * cw;
            int y = row * ch + (ch - child.getMeasuredHeight()) / 2;
            child.layout(x, y, x + cw, y + child.getMeasuredHeight());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (hoverCell < 0) {
            return;
        }
        int cw = getWidth() / columns;
        int ch = getHeight() / rows;
        int col = hoverCell % columns;
        int row = hoverCell / columns;
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            col = columns - 1 - col;
        }
        float inset = Ui.dp(4);
        RectF r = new RectF(col * cw + inset, row * ch + inset, (col + 1) * cw - inset, (row + 1) * ch - inset);
        float radius = Ui.dp(Ui.RADIUS_TILE);
        canvas.drawRoundRect(r, radius, radius, highlight);
    }
}
