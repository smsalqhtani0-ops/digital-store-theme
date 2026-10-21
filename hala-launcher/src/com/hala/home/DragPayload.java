package com.hala.home;

/** What is being dragged: an app, and where it came from. */
final class DragPayload {
    static final int HOME = 0;
    static final int DOCK = 1;
    static final int DRAWER = 2;

    final AppInfo app;
    final int from;
    /** Home slot or dock position it came from (unused for the drawer). */
    final int index;
    /** Where on the screen the drag started (for telling a drag from a long press). */
    final float startX;
    final float startY;
    boolean moved;

    DragPayload(AppInfo app, int from, int index, float startX, float startY) {
        this.app = app;
        this.from = from;
        this.index = index;
        this.startX = startX;
        this.startY = startY;
    }
}
