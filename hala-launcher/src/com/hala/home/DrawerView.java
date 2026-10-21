package com.hala.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The app drawer (the Apps screen): a search field in a rounded block at the top and every app
 * in a grid, sorted by name. It rises from the bottom and follows the finger; swiping down
 * from the top of the list puts it away.
 */
final class DrawerView extends FrameLayout {
    interface Host {
        void launch(AppInfo app, View from);

        void longPress(AppIconView v, int from, int index, float x, float y);

        /** The drawer was swiped down by `dy` pixels (finger still down, or released). */
        void drawerDragged(float dy, boolean released, float velocityY);
    }

    private final Host host;
    private final EditText search;
    private final GridView grid;
    private final TextView empty;
    private final Adapter adapter = new Adapter();
    private List<AppInfo> all = new ArrayList<AppInfo>();
    private List<AppInfo> shown = new ArrayList<AppInfo>();
    private int iconPx;
    private boolean labels = true;
    private final int touchSlop;
    private float downY;
    private float downX;
    private boolean closing;
    private android.view.VelocityTracker velocity;

    DrawerView(Context c, Host host) {
        super(c);
        this.host = host;
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        setBackgroundColor(Ui.screen());
        setClickable(true);

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // A handle shows the screen can be pulled back down.
        View handle = new View(c);
        handle.setBackground(Ui.rounded(Ui.subText(), 2));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = Ui.dp(10);
        hp.bottomMargin = Ui.dp(14);
        column.addView(handle, hp);

        search = new EditText(c);
        search.setHint(R.string.search_apps);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(Ui.text());
        search.setHintTextColor(Ui.subText());
        search.setBackground(Ui.rounded(Ui.block(), Ui.RADIUS_BLOCK));
        search.setPadding(Ui.dp(20), Ui.dp(12), Ui.dp(20), Ui.dp(12));
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (!shown.isEmpty()) {
                    DrawerView.this.host.launch(shown.get(0), v);
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sp.leftMargin = sp.rightMargin = Ui.dp(Ui.MARGIN);
        sp.bottomMargin = Ui.dp(12);
        column.addView(search, sp);

        FrameLayout gridFrame = new FrameLayout(c);
        grid = new GridView(c);
        grid.setAdapter(adapter);
        grid.setVerticalSpacing(Ui.dp(18));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new GradientDrawable());
        grid.setClipToPadding(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);
        grid.setPadding(Ui.dp(Ui.MARGIN - 8), Ui.dp(8), Ui.dp(Ui.MARGIN - 8), Ui.dp(24));
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                if (state == SCROLL_STATE_TOUCH_SCROLL) {
                    hideKeyboard();
                }
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {}
        });
        gridFrame.addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        empty = Ui.text(c, c.getString(R.string.no_results), 16, Ui.subText(), false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        gridFrame.addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(120)));
        column.addView(gridFrame, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    void setApps(List<AppInfo> apps, int iconPx, boolean labels) {
        this.all = apps;
        this.iconPx = iconPx;
        this.labels = labels;
        if (getWidth() > 0) {
            onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        }
        filter(search.getText().toString());
    }

    void reset() {
        search.setText("");
        grid.setSelection(0);
        hideKeyboard();
    }

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
        search.clearFocus();
    }

    private void filter(String query) {
        String q = Search.fold(query);
        List<AppInfo> out = new ArrayList<AppInfo>();
        for (AppInfo a : all) {
            if (Search.matches(a, q)) {
                out.add(a);
            }
        }
        shown = out;
        empty.setVisibility(out.isEmpty() && !all.isEmpty() ? VISIBLE : GONE);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int cell = iconPx + Ui.dp(44);
        int usable = w - grid.getPaddingLeft() - grid.getPaddingRight();
        grid.setNumColumns(Math.max(4, usable / Math.max(1, cell)));
    }

    // ---- Swipe down from the top of the list closes the drawer ----

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                downX = e.getX();
                rawDownY = e.getRawY();
                closing = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dy = e.getY() - downY;
                boolean atTop = !grid.canScrollVertically(-1);
                if (dy > touchSlop && dy > Math.abs(e.getX() - downX) && atTop) {
                    closing = true;
                    hideKeyboard();
                    return true;
                }
                break;
            }
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (velocity == null) {
            velocity = android.view.VelocityTracker.obtain();
        }
        // The drawer moves with the finger, so its own coordinates hardly change: track the
        // finger on the screen.
        MotionEvent onScreen = MotionEvent.obtain(e);
        onScreen.setLocation(e.getRawX(), e.getRawY());
        velocity.addMovement(onScreen);
        onScreen.recycle();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                rawDownY = e.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (closing || e.getY() - downY > touchSlop) {
                    closing = true;
                    // The view moves with the finger, so track the raw position.
                    host.drawerDragged(Math.max(0, e.getRawY() - rawDownY), false, 0);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (closing) {
                    velocity.computeCurrentVelocity(1000);
                    host.drawerDragged(Math.max(0, e.getRawY() - rawDownY), true, velocity.getYVelocity());
                }
                closing = false;
                velocity.recycle();
                velocity = null;
                return true;
            default:
                return true;
        }
    }

    /** Where the finger went down, in screen pixels (the drawer itself moves with it). */
    private float rawDownY;

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final AppInfo app = shown.get(position);
            final AppIconView v = new AppIconView(getContext(), app, iconPx, labels, false);
            v.label.setTextColor(Ui.text());
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    host.launch(app, v.icon);
                }
            });
            v.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    host.longPress(v, DragPayload.DRAWER, -1, v.downX, v.downY);
                    return true;
                }
            });
            return v;
        }
    }
}
