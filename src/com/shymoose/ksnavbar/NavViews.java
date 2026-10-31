// SPDX-License-Identifier: Apache-2.0
package com.shymoose.ksnavbar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import me.jxl.kiosk.plugins.KsTheme;

/**
 * The navbar in KS's design language. The bar and its tab share one overlay; menus have their own.
 * Selection changes update existing views rather than replacing an expensive native overlay.
 */
final class NavViews {
    private NavViews() {}

    /** What the plugin hears from the bar. All calls arrive on the main thread. */
    interface Listener {
        void onTarget(NavTarget target);
        void onCollapse();
        void onExpand();
        /** Any tap, to restart the auto-collapse countdown. */
        void onInteraction();
        /**
         * How late a tap reached the view, in ms. Touches pass through Flutter before reaching a
         * plugin view, so this is KS's share of any lag.
         */
        void onLatency(long downMs, long upMs);
        /** A menu item was tapped. {@code offsetDp} is its distance from the screen's center along the bar. */
        void onMenu(int index, int offsetDp, int barDepthDp);
        /** The view's depth from its edge changed, in dp, for example when the bar hides behind its tab. */
        void onDepth(int depthDp);
        /** The Close button that shows over a camera view was tapped. */
        void onCloseCamera();
    }

    /** A Material 3 role with a fallback for KS versions that do not provide it. */
    static int color(KsTheme theme, String role, String fallback) {
        try {
            return theme.color(role);
        } catch (IllegalArgumentException missing) {
            return theme.color(fallback);
        }
    }

    /** The KS card background at the configured opacity. Content drawn on it stays opaque. */
    static GradientDrawable surface(KsTheme theme, NavConfig config) {
        return surface(theme, config.opacity);
    }

    static GradientDrawable surface(KsTheme theme, float opacity) {
        GradientDrawable card = theme.card();
        card.setAlpha(Math.round(255 * opacity));
        return card;
    }

    /** Menus cover more of the dashboard than the bar, so they stay nearly opaque to keep entries readable. */
    static final float MENU_MIN_OPACITY = 0.95f;

    /**
     * Pressed feedback without animation: an onSurface wash that appears on touch down and goes on
     * release. A ripple animates for about 300 ms, and every one of its frames has to cross KS's
     * overlay texture, which arrives late on a low-power panel. This costs one frame each way.
     */
    static Drawable pressable(KsTheme theme, Drawable content, float radiusPx) {
        return pressable(theme, content, new float[] {radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx});
    }

    static Drawable pressable(KsTheme theme, Drawable content, float[] radii) {
        int ink = theme.color("onSurface");
        GradientDrawable wash = new GradientDrawable();
        wash.setCornerRadii(radii);
        wash.setColor(Color.argb(36, Color.red(ink), Color.green(ink), Color.blue(ink)));
        StateListDrawable states = new StateListDrawable();
        states.setEnterFadeDuration(0);
        states.setExitFadeDuration(0);
        states.addState(new int[] {android.R.attr.state_pressed},
            content == null ? wash : new LayerDrawable(new Drawable[] {content, wash}));
        states.addState(new int[0], content == null ? new ColorDrawable(Color.TRANSPARENT) : content);
        return states;
    }

    /** Points away from the bar's edge, the way the bar opens, or toward it. */
    static String chevron(NavConfig config, boolean towardEdge) {
        String away;
        switch (config.edge) {
            case TOP: away = towardEdge ? "up" : "down"; break;
            case LEFT: away = towardEdge ? "left" : "right"; break;
            case RIGHT: away = towardEdge ? "right" : "left"; break;
            default: away = towardEdge ? "down" : "up"; break;
        }
        return "mdi:chevron-" + away;
    }

    /**
     * Corner radii for a shape attached to the bar's edge: the corners against the edge are square,
     * so it reads as part of the edge.
     */
    static float[] edgeRadii(NavConfig config, float r) {
        switch (config.edge) {
            case TOP: return new float[] {0, 0, 0, 0, r, r, r, r};
            case LEFT: return new float[] {0, 0, r, r, r, r, 0, 0};
            case RIGHT: return new float[] {r, r, 0, 0, 0, 0, r, r};
            default: return new float[] {r, r, r, r, 0, 0, 0, 0};
        }
    }

    static ImageView icon(Context context) {
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return icon;
    }

    /**
     * Draws a Material Design Icon from KS's own set in a color. An unknown name draws a plain
     * circle, so a typo shows as a placeholder rather than an empty space.
     */
    static void setIcon(ImageView view, KsTheme theme, String name, int color) {
        Drawable drawable = null;
        try {
            drawable = theme.icon(name, "onSurface");
            if (drawable == null) drawable = theme.icon(NavItem.DEFAULT_ICON, "onSurface");
        } catch (LinkageError olderHost) {
            // Kiosk Satellite before 2026.10.23 has no icons for plugins. The manifest needs a
            // newer one anyway, so this only guards against a mismatched host.
        }
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(color);
        }
        view.setImageDrawable(drawable);
    }

    /** Sizes an icon view to a square of {@code px}. */
    static void sizeIcon(View view, int px) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            view.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        } else {
            params.width = px;
            params.height = px;
            view.setLayoutParams(params);
        }
    }
    /**
     * Hides an overlay's content while its size is changing, so a frame drawn at the wrong size is
     * never shown. KS draws a plugin overlay into a texture that it resizes only after measuring the
     * view. Whatever the texture held is scaled to the new size until the view redraws, which shows
     * as a stretched copy of the previous state. The gate keeps the content transparent from the
     * moment its size will change until the host surface has exactly the measured size, then
     * reveals it. A timeout reveals it anyway, so a size that never arrives cannot hide the bar.
     */
    static final class SizeGate {
        private static final long TIMEOUT_MS = 900;
        /**
         * How long to stay hidden after the host reaches the right size. The host view is resized
         * before the surface behind it, so a frame drawn at once lands in the old surface and shows
         * stretched. Measured on a ThinkSmart View: drawing at the match left the stretched frame up
         * indefinitely, while redraws 150 ms later always landed.
         */
        private static final long SETTLE_MS = 150;

        private final ViewGroup owner;
        private boolean waiting;
        private int lastHostWidth = -1;
        private int lastHostHeight = -1;
        /** The host's size over the reported size, below 1 when KS shrinks the overlay (SDK gap 26). */
        private float scale = 1f;
        private int contentWidth;
        private int contentHeight;
        private final Runnable timeout = this::open;
        private final Runnable reveal = this::open;
        private final Runnable redraw = () -> invalidateTree(owner());
        private final View.OnLayoutChangeListener hostListener = (view, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) redrawSoon();
            check();
        };

        SizeGate(ViewGroup owner) { this.owner = owner; }

        /**
         * Whether the owner should draw nothing. Owners skip drawing rather than using alpha: on KS's
         * texture-backed overlays an alpha change alone never reached the screen, while an ordinary
         * redraw does.
         */
        boolean hiding() { return waiting; }

        private ViewGroup owner() { return owner; }

        private View host() {
            return owner.getParent() instanceof View ? (View) owner.getParent() : null;
        }

        void attach() {
            View host = host();
            if (host != null) host.addOnLayoutChangeListener(hostListener);
        }

        void detach() {
            View host = host();
            if (host != null) host.removeOnLayoutChangeListener(hostListener);
            owner.removeCallbacks(timeout);
            owner.removeCallbacks(reveal);
            owner.removeCallbacks(redraw);
        }

        /**
         * SDK gap 26: with Kiosk Satellite's UI scale above 100%, KS sizes a wrapped overlay by
         * dividing the measured pixels by its scaled pixel ratio, so the window comes out smaller than
         * the content and cuts it off. Owners call this at the end of onMeasure with their content
         * size and report the result, enlarged by the shrink seen on the host, so the window fits the
         * content exactly. The content is laid out at its own size in the top left corner.
         */
        int[] measured(int width, int height) {
            contentWidth = width;
            contentHeight = height;
            if (scale >= 1f) return new int[] {width, height};
            return new int[] {(int) Math.ceil(width / scale), (int) Math.ceil(height / scale)};
        }

        /** The content's own width, without the enlargement for gap 26. */
        int contentWidth() { return contentWidth > 0 ? contentWidth : owner.getMeasuredWidth(); }

        /** The content's own height, without the enlargement for gap 26. */
        int contentHeight() { return contentHeight > 0 ? contentHeight : owner.getMeasuredHeight(); }

        /**
         * Learns the shrink once, from a host smaller than an unenlarged report by the same ratio on
         * both sides. After that the host lags behind each enlarged report for a moment, and a lagging
         * host is also smaller on both sides, so learning again would enlarge without end: measured on
         * a Fire Max 11 at 115% UI scale. The shrink is dropped only when the host exactly matches an
         * enlarged report, which means KS no longer shrinks the overlay.
         */
        private void learn(int hostWidth, int hostHeight, int width, int height) {
            if (width < 40 || height < 40 || hostWidth < 40 || hostHeight < 40) return;
            float across = hostWidth / (float) width;
            float down = hostHeight / (float) height;
            if (Math.abs(across - down) > 0.02f) return;
            float ratio = (across + down) / 2f;
            if (scale >= 1f) {
                if (ratio < 0.5f || ratio > 0.98f) return;
                scale = ratio;
            } else {
                if (Math.abs(ratio - 1f) > 0.01f) return;
                scale = 1f;
            }
            owner.post(owner::requestLayout);
        }

        /** Redraw after the surface has had time to follow a resize, twice for slow panels. */
        private void redrawSoon() {
            owner.removeCallbacks(redraw);
            owner.postDelayed(redraw, 300);
            owner.postDelayed(redraw, 800);
        }

        /** The owner's size is about to change. Hide it until the host matches. */
        void close() {
            waiting = true;
            owner.invalidate();
            owner.removeCallbacks(timeout);
            owner.postDelayed(timeout, TIMEOUT_MS);
        }

        /**
         * Draw only while the host surface has exactly the owner's measured size. KS can resize the
         * host again after a match (it briefly dropped to 2 x 2 while settling), so this runs on every
         * layout of the owner or the host, hiding and revealing as needed.
         */
        void check() {
            View host = host();
            int width = owner.getMeasuredWidth();
            int height = owner.getMeasuredHeight();
            int hostWidth = host == null ? -1 : host.getWidth();
            int hostHeight = host == null ? -1 : host.getHeight();
            if (host != null) learn(hostWidth, hostHeight, width, height);
            // KS passes sizes through Flutter's logical pixels, so allow a pixel or two of rounding.
            boolean match = host != null && width > 1 && height > 1
                && Math.abs(hostWidth - contentWidth()) <= 2 && Math.abs(hostHeight - contentHeight()) <= 2;
            boolean hostChanged = hostWidth != lastHostWidth || hostHeight != lastHostHeight;
            lastHostWidth = hostWidth;
            lastHostHeight = hostHeight;
            if (match) {
                if (waiting) {
                    owner.removeCallbacks(reveal);
                    owner.postDelayed(reveal, SETTLE_MS);
                }
            } else if (waiting) {
                owner.removeCallbacks(reveal);
            } else if (hostChanged) {
                // Only a new host size hides again, so the timeout cannot start a hide and show loop.
                close();
            }
        }

        private void open() {
            waiting = false;
            owner.removeCallbacks(timeout);
            owner.removeCallbacks(reveal);
            invalidateTree(owner);
            redrawSoon();
        }
    }

    static void invalidateTree(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) invalidateTree(group.getChildAt(i));
        }
    }

    /**
     * The bar and the tab it collapses into, edge last. Menus live in their own overlay, so opening
     * one never resizes this view. Every method runs on the main thread.
     */
    static final class Root extends LinearLayout {
        private final NavConfig config;
        private final Listener listener;
        private final Bar bar;
        private final FrameLayout barViewport;
        private final Tab tab;
        private final SizeGate gate = new SizeGate(this);
        private KsTheme theme;
        private String path;
        /** The open KS camera view, or null. */
        private String camera;
        private int active;
        private boolean collapsed;

        /**
         * {@code overCamera} builds the bar for showing over a camera view, with a Close button at its
         * end. KS needs a new overlay to move the bar above or below camera views, so it is fixed.
         */
        Root(Context context, KsTheme theme, NavConfig config, String path, String camera, boolean overCamera,
             boolean collapsed, Listener listener) {
            super(context);
            this.config = config;
            this.listener = listener;
            this.theme = theme;
            this.path = path;
            this.camera = camera;
            this.active = config.activeIndex(path, camera);
            setOrientation(config.vertical ? HORIZONTAL : VERTICAL);
            setGravity(config.vertical ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
            bar = new Bar(context, theme, config, active, new Bar.Taps() {
                @Override public void onItem(int index) { tapItem(index); }
                @Override public boolean onLongItem(int index) { return longPressItem(index); }
                @Override public void onClose() {
                    listener.onInteraction();
                    listener.onCloseCamera();
                }
            }, overCamera);
            tab = new Tab(context, theme, config, active, view -> {
                listener.onInteraction();
                listener.onExpand();
            });
            barViewport = config.vertical ? new ScrollView(context) : new HorizontalScrollView(context);
            barViewport.setClipToPadding(false);
            barViewport.setHorizontalFadingEdgeEnabled(!config.vertical);
            barViewport.setVerticalFadingEdgeEnabled(config.vertical);
            barViewport.addView(bar, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            addView(barViewport);
            addView(tab);
            this.collapsed = collapsed && config.collapse != NavConfig.Collapse.OFF;
            barViewport.setVisibility(this.collapsed ? GONE : VISIBLE);
            tab.setVisibility(this.collapsed ? VISIBLE : GONE);
            restyle(theme);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            gate.attach();
            // The first frame is drawn before KS knows the size.
            gate.close();
        }

        @Override protected void onDetachedFromWindow() {
            gate.detach();
            removeCallbacks(settle);
            super.onDetachedFromWindow();
        }

        private int reportedDepth = -1;

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            int[] size = gate.measured(getMeasuredWidth(), getMeasuredHeight());
            setMeasuredDimension(size[0], size[1]);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, l + gate.contentWidth(), t + gate.contentHeight());
            gate.check();
            if (changed && !collapsed) bar.reveal(active);
            int depth = depthDp();
            if (depth > 1 && depth != reportedDepth) {
                reportedDepth = depth;
                try { listener.onDepth(depth); } catch (RuntimeException ignored) {}
            }
        }

        /** The page under the bar and the open camera view, or null. */
        void setLocation(String nextPath, String nextCamera) {
            if (java.util.Objects.equals(nextPath, path) && java.util.Objects.equals(nextCamera, camera)) return;
            path = nextPath;
            camera = nextCamera;
            removeCallbacks(settle);
            showActive(config.activeIndex(path, camera));
        }

        /** Falls back to the real page if a tapped page never loads. */
        private final Runnable settle = this::settle;

        private void settle() {
            showActive(config.activeIndex(path, camera));
        }

        /**
         * Highlights a tapped page at once, the way a native tab bar does, instead of waiting for
         * Home Assistant to report the new page. If the page has not changed after a few seconds,
         * the highlight goes back to where the dashboard really is.
         */
        void preview(int index) {
            showActive(index);
            removeCallbacks(settle);
            postDelayed(settle, 5000);
        }

        private void showActive(int index) {
            if (index == active) return;
            active = index;
            bar.setActive(index);
            tab.setActive(index);
            if (!collapsed) bar.reveal(index);
        }

        private long downDelay = -1;

        /** Measures how late touches arrive. The event time is when the finger actually moved. */
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            long now = SystemClock.uptimeMillis();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downDelay = now - event.getEventTime();
                listener.onInteraction();
            } else if (action == MotionEvent.ACTION_UP && downDelay >= 0) {
                long up = now - event.getEventTime();
                long down = downDelay;
                downDelay = -1;
                try { listener.onLatency(down, up); } catch (RuntimeException ignored) {}
            }
            return super.dispatchTouchEvent(event);
        }

        @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
            if (gate.hiding()) return;
            super.dispatchDraw(canvas);
        }

        void setCollapsed(boolean value) {
            boolean next = value && config.collapse != NavConfig.Collapse.OFF;
            if (next == collapsed) return;
            collapsed = next;
            // Swapping the bar for the tab changes the overlay's size.
            gate.close();
            barViewport.setVisibility(collapsed ? GONE : VISIBLE);
            tab.setVisibility(collapsed ? VISIBLE : GONE);
            if (!collapsed) bar.post(() -> bar.reveal(active));
        }

        boolean isCollapsed() { return collapsed; }

        /** Marks the item whose menu is open, or none for -1. */
        /** The theme the bar was last styled with, for building views that match it. */
        KsTheme theme() { return theme; }

        /** The bar's length along its edge, in pixels. */
        int lengthPx() { return config.vertical ? gate.contentHeight() : gate.contentWidth(); }
        void setOpenMenu(int index) { bar.setOpen(index); }

        /** How far this view reaches from its edge, in dp: the bar with its margin, or the tab. */
        int depthDp() {
            float density = getResources().getDisplayMetrics().density;
            return Math.round((config.vertical ? gate.contentWidth() : gate.contentHeight()) / density);
        }

        private void tapItem(int index) {
            listener.onInteraction();
            NavItem item = config.items.get(index);
            if (item.type == NavItem.Type.MENU && !item.longPressMenu()) {
                openMenu(index);
                return;
            }
            if (item.target.isPage()) preview(index);
            listener.onTarget(item.target);
        }

        /** A long press opens an item's menu. Items without one do nothing, so a press still taps. */
        private boolean longPressItem(int index) {
            if (config.items.get(index).type != NavItem.Type.MENU) return false;
            listener.onInteraction();
            openMenu(index);
            return true;
        }

        private void openMenu(int index) {
            // The bar's overlay is centered on its edge like the menu's, so the item's distance
            // from this view's center is its distance from the screen's center.
            float offset = config.vertical
                ? barViewport.getTop() + bar.getTop() + bar.itemCenter(index) - barViewport.getScrollY() - gate.contentHeight() / 2f
                : barViewport.getLeft() + bar.getLeft() + bar.itemCenter(index) - barViewport.getScrollX() - gate.contentWidth() / 2f;
            float density = getResources().getDisplayMetrics().density;
            listener.onMenu(index, Math.round(offset / density), depthDp());
        }

        private float startX;
        private float startY;
        private boolean swiped;

        /**
         * A swipe toward the bar's edge folds it into its tab, and a swipe away from the edge opens
         * it again. The action fires as soon as the swipe is recognized, not on release, so it keeps
         * up with the finger even when touches reach the view late.
         */
        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (config.collapse == NavConfig.Collapse.OFF) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    swiped = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    return !swiped && checkSwipe(event);
                default:
                    return false;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (swiped) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swiped = false;
                return true;
            }
            // A swipe that starts on empty space, where no child takes the touch.
            if (config.collapse != NavConfig.Collapse.OFF) {
                if (action == MotionEvent.ACTION_DOWN) {
                    startX = event.getX();
                    startY = event.getY();
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE && checkSwipe(event)) return true;
            }
            return super.onTouchEvent(event);
        }

        private boolean checkSwipe(MotionEvent event) {
            // Along the axis that leaves the edge: vertical for a top or bottom bar, horizontal for a rail.
            float along = config.vertical ? event.getX() - startX : event.getY() - startY;
            float across = config.vertical ? event.getY() - startY : event.getX() - startX;
            float threshold = theme.px(24 * config.scale);
            if (Math.abs(along) < threshold || Math.abs(along) < 1.5f * Math.abs(across)) return false;
            boolean towardEdge = config.top || config.edge == NavConfig.Edge.LEFT ? along < 0 : along > 0;
            if (towardEdge == collapsed) return false;
            swiped = true;
            listener.onInteraction();
            if (towardEdge) listener.onCollapse();
            else listener.onExpand();
            return true;
        }

        void restyle(KsTheme next) {
            theme = next;
            int edge = theme.px(config.inset);
            bar.restyle(theme);
            tab.restyle(theme);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            // Insets are inside the view: the overlay itself sits flush so the tab can touch the edge.
            switch (config.edge) {
                case TOP: barParams.setMargins(edge, edge, edge, 0); break;
                case LEFT: barParams.setMargins(edge, edge, 0, edge); break;
                case RIGHT: barParams.setMargins(0, edge, edge, edge); break;
                default: barParams.setMargins(edge, 0, edge, edge); break;
            }
            barViewport.setLayoutParams(barParams);
            tab.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
    }

    /**
     * A menu's own overlay: just the menu card. KS places it beside the bar, over its item, and
     * closes it on a tap anywhere else.
     */
    static final class MenuPanel extends FrameLayout {
        private final Menu menu;
        private final SizeGate gate = new SizeGate(this);

        MenuPanel(Context context, KsTheme theme, NavConfig config, int itemIndex, String path, String camera, Menu.Taps taps) {
            super(context);
            menu = new Menu(context, theme, config, itemIndex, path, camera, taps);
            ScrollView scroll = new ScrollView(context);
            scroll.setVerticalFadingEdgeEnabled(true);
            scroll.addView(menu, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            addView(scroll, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }

        void restyle(KsTheme theme) { menu.restyle(theme); }

        @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
            if (!gate.hiding()) super.dispatchDraw(canvas);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            gate.attach();
            gate.close();
        }

        @Override protected void onDetachedFromWindow() {
            gate.detach();
            super.onDetachedFromWindow();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            int[] size = gate.measured(getMeasuredWidth(), getMeasuredHeight());
            setMeasuredDimension(size[0], size[1]);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, l + gate.contentWidth(), t + gate.contentHeight());
            gate.check();
        }
    }
    /**
     * A pill of items, horizontal at the top or bottom and a vertical rail at the sides. Each item is
     * an icon in an indicator above an optional label.
     */
    static final class Bar extends LinearLayout {
        interface Taps {
            void onItem(int index);
            /** A long press. True when it did something, which also suppresses the tap. */
            boolean onLongItem(int index);
            void onClose();
        }

        private final NavConfig config;
        private final List<LinearLayout> cells = new ArrayList<>();
        private final List<FrameLayout> indicators = new ArrayList<>();
        private final List<GradientDrawable> indicatorShapes = new ArrayList<>();
        private final List<ImageView> icons = new ArrayList<>();
        private final List<TextView> labels = new ArrayList<>();
        private final List<String> drawnIcons = new ArrayList<>();
        private KsTheme theme;
        private int active;
        /** The item whose menu is open, or -1. */
        private int open = -1;

        /** The Close button over a camera view, or null. Not an item, so it is never highlighted. */
        private final LinearLayout close;
        private final ImageView closeIcon;
        private final TextView closeLabel;

        Bar(Context context, KsTheme theme, NavConfig config, int active, Taps taps, boolean closeButton) {
            super(context);
            this.config = config;
            this.theme = theme;
            this.active = active;
            setOrientation(config.vertical ? VERTICAL : HORIZONTAL);
            setGravity(config.vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
            for (int i = 0; i < config.items.size(); i++) {
                NavItem item = config.items.get(i);
                LinearLayout cell = new LinearLayout(context);
                cell.setOrientation(VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setContentDescription(item.label);
                FrameLayout indicator = new FrameLayout(context);
                ImageView icon = icon(context);
                indicator.addView(icon, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
                TextView label = new TextView(context);
                label.setText(item.label);
                label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setGravity(Gravity.CENTER);
                label.setIncludeFontPadding(false);
                cell.addView(indicator);
                cell.addView(label);
                final int index = i;
                cell.setOnClickListener(view -> {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    taps.onItem(index);
                });
                cell.setOnLongClickListener(view -> {
                    if (!taps.onLongItem(index)) return false;
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    return true;
                });
                addView(cell);
                cells.add(cell);
                indicators.add(indicator);
                icons.add(icon);
                labels.add(label);
                drawnIcons.add(null);
            }
            if (closeButton) {
                close = new LinearLayout(context);
                close.setOrientation(VERTICAL);
                close.setGravity(Gravity.CENTER);
                close.setContentDescription("Close camera view");
                closeIcon = icon(context);
                closeLabel = new TextView(context);
                closeLabel.setText("Close");
                closeLabel.setSingleLine(true);
                closeLabel.setGravity(Gravity.CENTER);
                closeLabel.setIncludeFontPadding(false);
                close.addView(closeIcon);
                close.addView(closeLabel);
                close.setOnClickListener(view -> {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    taps.onClose();
                });
                addView(close);
            } else {
                close = null;
                closeIcon = null;
                closeLabel = null;
            }
            restyle(theme);
        }

        /** The center of an item along the bar, relative to the bar. */
        float itemCenter(int index) {
            View cell = cells.get(index);
            return config.vertical ? cell.getTop() + cell.getHeight() / 2f : cell.getLeft() + cell.getWidth() / 2f;
        }

        void setActive(int index) {
            if (index == active) return;
            int previous = active;
            active = index;
            applySelection(previous);
            applySelection(active);
        }

        void setOpen(int index) {
            if (index == open) return;
            int previous = open;
            open = index;
            applySelection(previous);
            applySelection(open);
        }

        void reveal(int index) {
            if (index < 0 || index >= cells.size()) return;
            View cell = cells.get(index);
            if (cell.getWidth() > 0) cell.requestRectangleOnScreen(new Rect(0, 0, cell.getWidth(), cell.getHeight()), true);
        }

        void restyle(KsTheme next) {
            theme = next;
            float s = config.scale;
            setBackground(surface(theme, config));
            if (config.vertical) setPadding(px(6 * s), px(8 * s), px(6 * s), px(8 * s));
            else setPadding(px(8 * s), px(6 * s), px(8 * s), px(6 * s));
            indicatorShapes.clear();
            for (int i = 0; i < cells.size(); i++) {
                LinearLayout cell = cells.get(i);
                cell.setBackground(pressable(theme, null, px(theme.radiusRow())));
                cell.setMinimumWidth(px(68 * s));
                cell.setMinimumHeight(px(NavLayout.MIN_TOUCH_DP));
                cell.setPadding(px(6 * s), px(6 * s), px(6 * s), px(6 * s));
                GradientDrawable indicator = new GradientDrawable();
                indicator.setCornerRadius(px(16 * s));
                indicatorShapes.add(indicator);
                FrameLayout frame = indicators.get(i);
                frame.setBackground(indicator);
                frame.setLayoutParams(new LinearLayout.LayoutParams(px(56 * s), px(32 * s)));
                ImageView glyph = icons.get(i);
                FrameLayout.LayoutParams glyphParams = (FrameLayout.LayoutParams) glyph.getLayoutParams();
                glyphParams.width = px(24 * s);
                glyphParams.height = px(24 * s);
                glyph.setLayoutParams(glyphParams);
                TextView label = labels.get(i);
                theme.styleText(label, 12 * s, 500, "onSurfaceVariant");
                label.setMaxWidth(px(96 * s));
                LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                spacing.topMargin = px(4 * s);
                label.setLayoutParams(spacing);
            }
            if (close != null) {
                close.setBackground(pressable(theme, null, px(theme.radiusRow())));
                close.setMinimumWidth(px(68 * s));
                close.setMinimumHeight(px(NavLayout.MIN_TOUCH_DP));
                close.setPadding(px(6 * s), px(6 * s), px(6 * s), px(6 * s));
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(px(24 * s), px(24 * s));
                iconParams.topMargin = px(4 * s);
                iconParams.bottomMargin = px(4 * s);
                closeIcon.setLayoutParams(iconParams);
                setIcon(closeIcon, theme, "mdi:close", theme.color("onSurfaceVariant"));
                theme.styleText(closeLabel, 12 * s, 500, "onSurfaceVariant");
                LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                spacing.topMargin = px(4 * s);
                closeLabel.setLayoutParams(spacing);
                closeLabel.setVisibility(config.labels == NavConfig.Labels.ALWAYS ? VISIBLE
                    : config.labels == NavConfig.Labels.NEVER ? GONE : INVISIBLE);
            }
            for (int i = 0; i < cells.size(); i++) {
                drawnIcons.set(i, null);
                applySelection(i);
            }
        }

        /**
         * Moves the highlight. Only colors and glyphs change, so a tap costs one redraw and no layout
         * pass. The label weight stays the same for the same reason: a bolder label would be wider.
         */
        private void applySelection(int i) {
            if (i < 0 || i >= cells.size()) return;
            int on = color(theme, "onSecondaryContainer", "onSurface");
            int off = theme.color("onSurfaceVariant");
            int pill = color(theme, "secondaryContainer", "primaryContainer");
            int strong = theme.color("onSurface");
            NavItem item = config.items.get(i);
            boolean selected = i == active;
            // An open menu's item gets a lighter pill, so it is clear where the menu came from.
            boolean opened = i == open && !selected;
            int faint = Color.argb(110, Color.red(pill), Color.green(pill), Color.blue(pill));
            indicatorShapes.get(i).setColor(selected ? pill : opened ? faint : Color.TRANSPARENT);
            String name = selected ? item.iconSelected : item.icon;
            ImageView icon = icons.get(i);
            if (!name.equals(drawnIcons.get(i))) {
                setIcon(icon, theme, name, selected || opened ? on : off);
                drawnIcons.set(i, name);
            } else if (icon.getDrawable() != null) {
                icon.getDrawable().setTint(selected || opened ? on : off);
            }
            TextView label = labels.get(i);
            label.setTextColor(selected ? strong : off);
            boolean show = config.labels == NavConfig.Labels.ALWAYS || (config.labels == NavConfig.Labels.ACTIVE && selected);
            // Active only keeps label space, so selection never changes the bar's dimensions.
            int visibility = show ? VISIBLE : config.labels == NavConfig.Labels.NEVER ? GONE : INVISIBLE;
            View cell = cells.get(i);
            if (cell.isSelected() != selected) cell.setSelected(selected);
            if (label.getVisibility() != visibility) label.setVisibility(visibility);
        }

        private int px(float dp) { return theme.px(dp); }
    }

    /**
     * The entries of a menu item on a card: a list of rows, or a grid of tiles for long menus such as
     * cameras. The current page's entry is highlighted.
     */
    static final class Menu extends LinearLayout {
        interface Taps {
            void onChild(int index);
        }

        private final NavConfig config;
        private final NavItem item;
        private final String path;
        private final String camera;
        private final boolean grid;
        private int columns;
        private int tileWidth;
        /** One per entry, in order. Each holds an icon then a label. */
        private final List<LinearLayout> entries = new ArrayList<>();
        private KsTheme theme;

        Menu(Context context, KsTheme theme, NavConfig config, int itemIndex, String path, String camera, Taps taps) {
            super(context);
            this.config = config;
            this.item = config.items.get(itemIndex);
            this.camera = camera;
            this.path = path;
            this.theme = theme;
            int count = item.children.size();
            this.grid = config.grid(item);
            setOrientation(VERTICAL);
            for (int i = 0; i < count; i++) {
                NavItem child = item.children.get(i);
                LinearLayout entry = new LinearLayout(context);
                entry.setOrientation(grid ? VERTICAL : HORIZONTAL);
                entry.setGravity(grid ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
                entry.setContentDescription(child.label);
                entry.addView(icon(context));
                TextView label = new TextView(context);
                label.setText(child.label);
                label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                if (grid) label.setGravity(Gravity.CENTER);
                entry.addView(label);
                final int index = i;
                entry.setOnClickListener(view -> {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    taps.onChild(index);
                });
                entries.add(entry);
                if (!grid) addView(entry);
            }
            if (grid) arrangeGrid(count <= 9 ? 3 : 4);
            restyle(theme);
        }

        private void arrangeGrid(int nextColumns) {
            for (LinearLayout entry : entries) {
                if (entry.getParent() instanceof ViewGroup) ((ViewGroup) entry.getParent()).removeView(entry);
            }
            removeAllViews();
            columns = nextColumns;
            LinearLayout row = null;
            for (int i = 0; i < entries.size(); i++) {
                if (i % columns == 0) {
                    row = new LinearLayout(getContext());
                    row.setOrientation(HORIZONTAL);
                    addView(row);
                }
                row.addView(entries.get(i));
            }
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
                int available = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
                if (grid) {
                    int next = NavLayout.columns(entries.size(), available, px(108 * config.scale));
                    if (columns != next) arrangeGrid(next);
                    int width = NavLayout.tileWidth(available, columns, px(104 * config.scale), px(4 * config.scale));
                    if (tileWidth != width) {
                        tileWidth = width;
                        for (LinearLayout entry : entries) {
                            entry.getLayoutParams().width = width;
                            ((TextView) entry.getChildAt(1)).setMaxWidth(Math.max(1, width - entry.getPaddingLeft() - entry.getPaddingRight()));
                        }
                    }
                } else {
                    for (LinearLayout entry : entries) {
                        TextView label = (TextView) entry.getChildAt(1);
                        label.setMaxWidth(Math.max(1, available - entry.getPaddingLeft() - entry.getPaddingRight()
                            - px(36 * config.scale)));
                    }
                }
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        void restyle(KsTheme next) {
            theme = next;
            float s = config.scale;
            setBackground(surface(theme, Math.max(config.opacity, MENU_MIN_OPACITY)));
            setPadding(px(8 * s), px(8 * s), px(8 * s), px(8 * s));
            tileWidth = 0;
            int pill = color(theme, "secondaryContainer", "primaryContainer");
            int on = color(theme, "onSecondaryContainer", "onSurface");
            for (int i = 0; i < entries.size(); i++) {
                NavItem child = item.children.get(i);
                boolean selected = camera != null ? child.showsCamera(camera) : child.matches(path);
                LinearLayout entry = entries.get(i);
                GradientDrawable background = new GradientDrawable();
                background.setCornerRadius(px(theme.radiusRow()));
                background.setColor(selected ? pill : Color.TRANSPARENT);
                entry.setBackground(pressable(theme, background, px(theme.radiusRow())));
                entry.setMinimumHeight(px(NavLayout.MIN_TOUCH_DP));
                entry.setSelected(selected);
                ImageView icon = (ImageView) entry.getChildAt(0);
                setIcon(icon, theme, selected ? child.iconSelected : child.icon, selected ? on : theme.color("onSurfaceVariant"));
                TextView label = (TextView) entry.getChildAt(1);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                if (grid) {
                    // Tiles: a large icon over a short label, all the same size so the grid lines up.
                    sizeIcon(icon, px(28 * s));
                    theme.styleText(label, 12.5f * s, 500, "onSurface");
                    labelParams.topMargin = px(6 * s);
                    label.setMaxWidth(px(92 * s));
                    entry.setPadding(px(6 * s), px(12 * s), px(6 * s), px(10 * s));
                    LinearLayout.LayoutParams tile = new LinearLayout.LayoutParams(px(104 * s), LayoutParams.WRAP_CONTENT);
                    tile.setMargins(px(2 * s), px(2 * s), px(2 * s), px(2 * s));
                    entry.setLayoutParams(tile);
                } else {
                    sizeIcon(icon, px(22 * s));
                    theme.styleText(label, 15 * s, 500, "onSurface");
                    labelParams.leftMargin = px(14 * s);
                    entry.setPadding(px(14 * s), px(10 * s), px(20 * s), px(10 * s));
                    entry.setMinimumHeight(px(Math.max(NavLayout.MIN_TOUCH_DP, 48 * s)));
                    entry.setMinimumWidth(px(220 * s));
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                    if (i > 0) rowParams.topMargin = px(2 * s);
                    entry.setLayoutParams(rowParams);
                }
                label.setLayoutParams(labelParams);
            }
        }

        private int px(float dp) { return theme.px(dp); }
    }
    /**
     * The collapsed bar: a small tab flush with the edge, showing the current item's icon and a
     * chevron. Tapping it opens the bar.
     */
    static final class Tab extends LinearLayout {
        private final NavConfig config;
        private final ImageView current;
        private final ImageView chevron;
        private int active;
        private KsTheme theme;

        Tab(Context context, KsTheme theme, NavConfig config, int active, View.OnClickListener open) {
            super(context);
            this.config = config;
            this.theme = theme;
            this.active = active;
            // A rail's tab stands upright against the side edge.
            setOrientation(config.vertical ? VERTICAL : HORIZONTAL);
            setGravity(Gravity.CENTER);
            setContentDescription("Open navigation bar");
            current = icon(context);
            chevron = icon(context);
            addView(current);
            addView(chevron);
            setOnClickListener(view -> {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                open.onClick(view);
            });
            restyle(theme);
        }

        void setActive(int index) {
            if (index == active) return;
            active = index;
            updateIcon();
        }

        private void updateIcon() {
            // Keep this slot even on unmatched pages, so selection never resizes the tab.
            current.setVisibility(active >= 0 ? VISIBLE : INVISIBLE);
            if (active >= 0) setIcon(current, theme, config.items.get(active).iconSelected, theme.color("onSurface"));
        }

        void restyle(KsTheme next) {
            theme = next;
            float s = config.scale;
            float r = theme.px(theme.radiusCard());
            float[] radii = edgeRadii(config, r);
            GradientDrawable shape = surface(theme, config);
            shape.setCornerRadii(radii);
            setBackground(pressable(theme, shape, radii));
            int length = theme.px(96 * s);
            int depth = theme.px(Math.max(NavLayout.MIN_TOUCH_DP, 40 * s));
            int lead = theme.px(20 * s);
            int trail = theme.px(16 * s);
            int side = theme.px(6 * s);
            if (config.vertical) {
                setMinimumWidth(depth);
                setMinimumHeight(length);
                setPadding(side, lead, side, trail);
            } else {
                setMinimumWidth(length);
                setMinimumHeight(depth);
                setPadding(lead, side, trail, side);
            }
            updateIcon();
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(theme.px(20 * s), theme.px(20 * s));
            if (config.vertical) gap.bottomMargin = theme.px(8 * s);
            else gap.rightMargin = theme.px(8 * s);
            current.setLayoutParams(gap);
            setIcon(chevron, theme, chevron(config, false), theme.color("onSurfaceVariant"));
            sizeIcon(chevron, theme.px(22 * s));
        }
    }
}
