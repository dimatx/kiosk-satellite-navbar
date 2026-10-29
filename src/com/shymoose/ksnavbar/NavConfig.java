// SPDX-License-Identifier: Apache-2.0
package com.shymoose.ksnavbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The validated navbar configuration, built from the plugin's KS settings. */
final class NavConfig {
    /** The most items a bar holds. More would not fit across a tablet. */
    static final int MAX_ITEMS = 16;
    static final int DEFAULT_INSET = 12;

    /** The screen edge the bar sits against. */
    enum Edge { BOTTOM, TOP, LEFT, RIGHT }

    enum Labels { ALWAYS, ACTIVE, NEVER }

    /** Whether the bar can shrink to a tab at its edge, and how it starts. */
    enum Collapse { OFF, START_OPEN, START_COLLAPSED }

    /** Auto switches to a grid above this many entries. */
    static final int AUTO_GRID_ABOVE = 6;

    final Edge edge;
    /** Whether the bar is against the top edge. */
    final boolean top;
    /** Whether the bar is a vertical rail against the left or right edge. */
    final boolean vertical;
    final Labels labels;
    final float scale;
    final int inset;
    /** Background opacity, 0.2 to 1. Icons and labels stay opaque. */
    final float opacity;
    final Collapse collapse;
    /** Seconds without a tap before an open bar collapses again. 0 never does. */
    final int autoCollapse;
    /** Whether the dashboard keeps clear of the bar. */
    final boolean makeRoom;
    /** Whether the bar stays on screen over an open camera view, with a button to close it. */
    final boolean overCameras;
    final List<String> hideOn;
    final List<NavItem> items;
    /** One line per item that could not be used. */
    final List<String> problems;

    private NavConfig(Edge edge, Labels labels, float scale, int inset, float opacity, Collapse collapse, int autoCollapse, boolean makeRoom, boolean overCameras,
                      List<String> hideOn, List<NavItem> items, List<String> problems) {
        this.edge = edge;
        this.top = edge == Edge.TOP;
        this.vertical = edge == Edge.LEFT || edge == Edge.RIGHT;
        this.labels = labels;
        this.scale = scale;
        this.inset = inset;
        this.opacity = opacity;
        this.collapse = collapse;
        this.autoCollapse = autoCollapse;
        this.makeRoom = makeRoom;
        this.overCameras = overCameras;
        this.hideOn = Collections.unmodifiableList(hideOn);
        this.items = Collections.unmodifiableList(items);
        this.problems = Collections.unmodifiableList(problems);
    }

    static NavConfig parse(Map<String, Object> settings) {
        String position = text(settings, "position");
        Edge edge = "Top".equals(position) ? Edge.TOP : "Left".equals(position) ? Edge.LEFT
            : "Right".equals(position) ? Edge.RIGHT : Edge.BOTTOM;
        String labelSetting = text(settings, "labels");
        Labels labels = "Never".equals(labelSetting) ? Labels.NEVER : "Active only".equals(labelSetting) ? Labels.ACTIVE : Labels.ALWAYS;
        float scale = 1f;
        String scaleText = text(settings, "scale").replace("%", "").trim();
        if (!scaleText.isEmpty()) {
            try { scale = Math.max(50, Math.min(200, Integer.parseInt(scaleText))) / 100f; } catch (NumberFormatException ignored) {}
        }
        // Fixed: the bar sits a comfortable distance from the edge, one setting fewer.
        int inset = DEFAULT_INSET;
        float opacity = number(settings, "opacity", 20, 100, 100) / 100f;
        String collapseSetting = text(settings, "collapse");
        Collapse collapse = "Off".equals(collapseSetting) ? Collapse.OFF
            : "Start hidden".equals(collapseSetting) ? Collapse.START_COLLAPSED : Collapse.START_OPEN;
        int autoCollapse = collapse == Collapse.OFF ? 0 : number(settings, "autoCollapse", 0, 3600, 0);
        // Pages picked with the dashboard picker. Each hides the bar on that view only.
        List<String> hideOn = new ArrayList<>();
        for (Map<?, ?> row : NavItem.rows(settings.get("hideOn"))) {
            String page = NavItem.text(row, "page");
            if (!page.isEmpty()) hideOn.add(Paths.hideEntry(page));
        }
        List<NavItem> items = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<Map<?, ?>> rows = NavItem.rows(settings.get("items"));
        for (int i = 0; i < rows.size() && items.size() < MAX_ITEMS; i++) {
            String label = NavItem.text(rows.get(i), "label");
            String name = label.isEmpty() ? "Item " + (i + 1) : label;
            try {
                items.add(NavItem.fromRow(rows.get(i), true, "items." + i));
            } catch (IllegalArgumentException error) {
                problems.add(name + ": " + error.getMessage());
            }
        }
        boolean makeRoom = !Boolean.FALSE.equals(settings.get("makeRoom"));
        boolean overCameras = !Boolean.FALSE.equals(settings.get("overCameras"));
        return new NavConfig(edge, labels, scale, inset, opacity, collapse, autoCollapse, makeRoom, overCameras, hideOn, items, problems);
    }
    /** Whether the bar belongs on {@code path}. It shows everywhere except the Hide on paths. */
    boolean visibleAt(String path) {
        if (items.isEmpty()) return false;
        if (path == null) return true;
        String here = Paths.normalize(path);
        for (String page : hideOn) if (hides(page, here)) return false;
        return true;
    }

    /** Whether a menu shows its entries as a grid of tiles rather than a list. */
    boolean grid(NavItem menu) {
        return menu.children.size() > AUTO_GRID_ABOVE;
    }

    /**
     * The item to highlight: while a camera view is open, the first item that opens it or holds an
     * entry that does, and otherwise the first item for the page at {@code path}. -1 for none.
     */
    int activeIndex(String path, String camera) {
        if (camera == null) return activeIndex(path);
        for (int i = 0; i < items.size(); i++) if (items.get(i).showsCamera(camera)) return i;
        return -1;
    }

    /** The first item that is the page at {@code path}, or -1. */
    int activeIndex(String path) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).matches(path)) return i;
        return -1;
    }

    /** Whether both configurations draw the same bar. Hide-on pages and the timer are handled separately. */
    boolean sameLook(NavConfig other) {
        return other != null && describe().equals(other.describe());
    }

    String describe() {
        StringBuilder text = new StringBuilder();
        text.append(edge).append('|').append(labels).append('|').append(scale).append('|').append(inset)
            .append('|').append(opacity).append('|').append(collapse).append('|').append(makeRoom).append('|').append(overCameras);
        for (NavItem item : items) appendItem(text, item);
        return text.toString();
    }

    private static void appendItem(StringBuilder text, NavItem item) {
        text.append('[').append(item.type).append(',').append(item.label).append(',').append(item.icon).append(',').append(item.iconSelected);
        if (item.target != null) text.append(',').append(item.target.kind).append(':').append(item.target.value);
        for (NavItem child : item.children) appendItem(text, child);
        text.append(']');
    }

    /** A page matches itself; an entry ending in /* also matches everything below it. */
    static boolean hides(String page, String here) {
        if (page.endsWith("/*")) {
            String base = page.substring(0, page.length() - 2);
            return here.equals(base) || here.startsWith(base + "/");
        }
        return page.equals(here);
    }
    private static int number(Map<String, Object> settings, String key, int min, int max, int fallback) {
        Object value = settings.get(key);
        return value instanceof Number ? Math.max(min, Math.min(max, ((Number) value).intValue())) : fallback;
    }

    private static String text(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        return value instanceof String ? ((String) value).trim() : "";
    }
}
