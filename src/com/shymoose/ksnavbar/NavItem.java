// SPDX-License-Identifier: Apache-2.0
package com.shymoose.ksnavbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One navbar entry: a page, an action, or a menu of pages and actions. */
final class NavItem {
    enum Type { PAGE, ACTION, MENU }

    /** The Opens choices in settings, in the order they are offered. */
    static final String PAGE = "Page";
    static final String PANEL = "Built-in page";
    static final String CAMERA = "Camera view";
    static final String CALENDAR = "Calendar";
    static final String MENU = "Menu";
    static final String ACTION = "Kiosk action";
    static final String WEB = "Web address";
    static final String APP = "App";

    static final String DEFAULT_ICON = "mdi:circle-outline";
    static final String CAMERA_ICON = "mdi:cctv";
    static final int MAX_CHILDREN = 16;

    final Type type;
    final String label;
    final String icon;
    final String iconSelected;
    /**
     * Where a tap goes. Null for a menu, unless the menu has a Tap opens page: then a tap opens the
     * page and a long press opens the menu.
     */
    final NavTarget target;
    /** Empty unless this is a menu. */
    final List<NavItem> children;

    NavItem(Type type, String label, String icon, String iconSelected, NavTarget target, List<NavItem> children) {
        this.type = type;
        this.label = label;
        this.icon = icon;
        this.iconSelected = iconSelected;
        this.target = target;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    /**
     * An item from one row of the Items list, or an entry from a menu's Entries list. KS fills in
     * every field, including hidden ones, so only the fields {@code opens} selects are read.
     */
    static NavItem fromRow(Map<?, ?> row, boolean allowMenu) {
        String label = text(row, "label");
        if (label.isEmpty()) throw new IllegalArgumentException("needs a label");
        String opens = text(row, "opens");
        if (opens.isEmpty()) opens = PAGE;
        if (MENU.equals(opens)) {
            if (!allowMenu) throw new IllegalArgumentException("a menu entry cannot open another menu");
            NavItem menu = menu(label, text(row, "icon"), text(row, "iconSelected"), rows(row.get("entries")));
            String tapPage = text(row, "tapPage");
            if (tapPage.isEmpty()) return menu;
            return new NavItem(Type.MENU, menu.label, menu.icon, menu.iconSelected, NavTarget.page(tapPage), menu.children);
        }
        NavTarget target;
        switch (opens) {
            case PAGE: target = NavTarget.page(text(row, "page")); break;
            case PANEL: target = NavTarget.panel(text(row, "panel"), text(row, "panelPath")); break;
            case CAMERA: target = NavTarget.camera(text(row, "camera")); break;
            case CALENDAR: target = NavTarget.calendar(text(row, "calendarView")); break;
            case ACTION: target = NavTarget.action(text(row, "action")); break;
            case WEB: target = NavTarget.url(text(row, "url")); break;
            case APP: target = NavTarget.app(text(row, "app")); break;
            default: throw new IllegalArgumentException("unknown Opens choice '" + opens + "'");
        }
        return leaf(label, text(row, "icon"), text(row, "iconSelected"), target);
    }

    /** A page or action with optional icons. A blank icon is picked from the label or the target. */
    static NavItem leaf(String label, String icon, String selectedIcon, NavTarget target) {
        String fallback = fallbackIcon(label, targetIcon(target));
        String[] icons = icons(icon, selectedIcon, fallback);
        Type type = target.isPage() ? Type.PAGE : Type.ACTION;
        return new NavItem(type, label, icons[0], icons[1], target, Collections.<NavItem>emptyList());
    }

    /** A menu of entry rows. */
    static NavItem menu(String label, String icon, String selectedIcon, List<Map<?, ?>> entries) {
        String[] icons = icons(icon, selectedIcon, fallbackIcon(label, DEFAULT_ICON));
        List<NavItem> children = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            String childLabel = text(entry, "label");
            try {
                children.add(fromRow(entry, false));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException((childLabel.isEmpty() ? "an entry" : childLabel) + " " + error.getMessage());
            }
        }
        if (children.isEmpty()) throw new IllegalArgumentException("menu has no entries");
        if (children.size() > MAX_CHILDREN) throw new IllegalArgumentException("menu has more than " + MAX_CHILDREN + " entries");
        return new NavItem(Type.MENU, label, icons[0], icons[1], null, children);
    }

    /** The icon a target suggests when the label suggests none. */
    static String targetIcon(NavTarget target) {
        if (target.kind == NavTarget.Kind.PANEL) {
            switch (target.value) {
                case "energy": return "mdi:lightning-bolt-outline";
                case "map": return "mdi:map-outline";
                case "logbook": return "mdi:format-list-bulleted-type";
                case "history": return "mdi:chart-box-outline";
                case "todo": return "mdi:clipboard-list-outline";
                case "media-browser": return "mdi:play-box-multiple-outline";
                default: return "mdi:view-dashboard-outline";
            }
        }
        return kindIcon(target.kind);
    }

    /** The icon a target's kind suggests when the label suggests none. */
    static String kindIcon(NavTarget.Kind kind) {
        switch (kind) {
            case CAMERA: return CAMERA_ICON;
            case CALENDAR: return "mdi:calendar-month";
            case MUSIC: return "mdi:music";
            case NOW_PLAYING: return "mdi:play-circle-outline";
            case SCREENSAVER: return "mdi:monitor-shimmer";
            case APPS: return "mdi:apps";
            case RELOAD: return "mdi:refresh";
            case HOME: return "mdi:home-outline";
            case URL: return "mdi:web";
            case APP: return "mdi:application-outline";
            default: return DEFAULT_ICON;
        }
    }

    /** An icon picked from the label when a rule matches, otherwise the given default. */
    static String fallbackIcon(String label, String otherwise) {
        String guessed = IconGuess.forLabel(label);
        return guessed != null ? guessed : otherwise;
    }

    /** Whether this item opens the KS camera view {@code viewId}, or holds a menu entry that does. */
    boolean showsCamera(String viewId) {
        if (viewId == null) return false;
        for (NavItem child : children) if (child.showsCamera(viewId)) return true;
        return target != null && target.kind == NavTarget.Kind.CAMERA && target.value.equals(viewId);
    }
    /** A menu whose tap opens a page, so the menu needs a long press. */
    boolean longPressMenu() { return type == Type.MENU && target != null; }

    /** Whether this item, its Tap opens page or one of its menu entries is the page at {@code path}. */
    boolean matches(String path) {
        if (path == null) return false;
        if (type == Type.MENU) {
            for (NavItem child : children) if (child.matches(path)) return true;
            if (target == null) return false;
        }
        return target.shows(path);
    }

    /** The normal and selected icon. A blank icon uses the fallback, a blank selected icon the normal one. */
    static String[] icons(String icon, String selectedIcon, String fallback) {
        String normal = icon == null || icon.trim().isEmpty() ? fallback : icon.trim();
        String selected = selectedIcon == null || selectedIcon.trim().isEmpty() ? normal : selectedIcon.trim();
        return new String[] {normal, selected};
    }

    /**
     * The Items rows with every empty Icon filled in with the icon the bar draws for it, picked from
     * the label or what the item opens. Saved back, so the settings page shows the icon in use
     * instead of an empty picker. Null when nothing was empty.
     */
    static List<Map<String, Object>> withIcons(Object items) {
        boolean changed = false;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> row : rows(items)) {
            Map<String, Object> copy = copy(row);
            if (row.get("entries") instanceof List) {
                List<Map<String, Object>> entries = new ArrayList<>();
                for (Map<?, ?> entry : rows(row.get("entries"))) {
                    Map<String, Object> entryCopy = copy(entry);
                    changed |= fillIcon(entryCopy, false);
                    entries.add(entryCopy);
                }
                copy.put("entries", entries);
            }
            changed |= fillIcon(copy, true);
            result.add(copy);
        }
        return changed ? result : null;
    }

    private static boolean fillIcon(Map<String, Object> row, boolean allowMenu) {
        if (!text(row, "icon").isEmpty() || text(row, "label").isEmpty()) return false;
        try {
            row.put("icon", fromRow(row, allowMenu).icon);
            return true;
        } catch (IllegalArgumentException unfinished) {
            // An item that cannot be used yet keeps its empty icon until it is fixed.
            return false;
        }
    }

    private static Map<String, Object> copy(Map<?, ?> row) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : row.entrySet()) copy.put(String.valueOf(entry.getKey()), entry.getValue());
        return copy;
    }

    /** The rows of a list setting. Anything that is not a list of maps reads as empty. */
    static List<Map<?, ?>> rows(Object value) {
        List<Map<?, ?>> rows = new ArrayList<>();
        if (value instanceof List) {
            for (Object row : (List<?>) value) if (row instanceof Map) rows.add((Map<?, ?>) row);
        }
        return rows;
    }

    static String text(Map<?, ?> row, String key) {
        Object value = row.get(key);
        return value instanceof String ? ((String) value).trim() : "";
    }
}