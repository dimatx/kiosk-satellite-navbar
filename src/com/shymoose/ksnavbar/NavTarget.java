// SPDX-License-Identifier: Apache-2.0
package com.shymoose.ksnavbar;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Where a navbar item goes: a dashboard view, a built-in page, a camera view, the calendar, a kiosk action or a web address. */
final class NavTarget {
    enum Kind { PAGE, PANEL, URL, APP, CAMERA, CALENDAR, MUSIC, NOW_PLAYING, SCREENSAVER, APPS, RELOAD, HOME }

    /** The Kiosk action choices in settings, in the order they are offered. */
    static final String[] ACTIONS = {"Music", "Now playing", "Screensaver", "App launcher", "Reload", "Start page"};

    /** The built-in Home Assistant pages offered in settings, with the path each one opens. */
    static final Map<String, String> PANELS = new LinkedHashMap<>();
    static {
        PANELS.put("Energy", "energy");
        PANELS.put("Map", "map");
        PANELS.put("Logbook", "logbook");
        PANELS.put("History", "history");
        PANELS.put("To-do", "todo");
        PANELS.put("Media", "media-browser");
    }

    /** The choice that opens a path typed in settings, for a page no list names. */
    static final String OTHER_PANEL = "Other";

    /** The calendar view choices in settings. Default leaves the view to the kiosk's Calendar settings. */
    static final String[] CALENDAR_VIEWS = {"Default", "Day", "Week", "Month", "Schedule"};

    /** A KS host command and its arguments. */
    static final class Command {
        final String name;
        final Map<String, Object> arguments;

        Command(String name, Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }

    final Kind kind;
    /** The path for PAGE, the URL for URL, the view ID for CAMERA and the calendar view for CALENDAR. Empty otherwise. */
    final String value;

    private NavTarget(Kind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    /** A dashboard view, as the dashboard picker stores it ({@code lovelace/0}), with or without a leading slash. */
    static NavTarget page(String path) {
        String text = path == null ? "" : path.trim();
        while (text.startsWith("/")) text = text.substring(1);
        text = Paths.stripTrailingSlashes(text);
        if (text.isEmpty()) throw new IllegalArgumentException("pick a page");
        if (text.contains("://")) throw new IllegalArgumentException("a page is a dashboard view, not a web address");
        return new NavTarget(Kind.PAGE, text);
    }

    /**
     * A page Home Assistant provides itself, such as Energy, which no dashboard list shows. The choice
     * is one of {@link #PANELS}, or {@link #OTHER_PANEL} with the path to open.
     */
    static NavTarget panel(String choice, String otherPath) {
        String text = choice == null ? "" : choice.trim();
        if (OTHER_PANEL.equals(text)) {
            String path = otherPath == null ? "" : otherPath.trim();
            while (path.startsWith("/")) path = path.substring(1);
            path = Paths.stripTrailingSlashes(path);
            if (path.isEmpty()) throw new IllegalArgumentException("needs a path");
            if (path.contains("://")) throw new IllegalArgumentException("a built-in page is a path, not a web address");
            return new NavTarget(Kind.PANEL, path);
        }
        String path = PANELS.get(text);
        if (path == null) throw new IllegalArgumentException("pick a built-in page");
        return new NavTarget(Kind.PANEL, path);
    }

    /** An Android app from the kiosk's App Launcher, by package name. Needs Kiosk Satellite 2026.10.39 or newer. */
    static NavTarget app(String packageName) {
        String text = packageName == null ? "" : packageName.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("pick an app");
        if (!isPackage(text)) throw new IllegalArgumentException("is not an app");
        return new NavTarget(Kind.APP, text);
    }

    /** Whether {@code text} reads as an Android package name, such as {@code com.spotify.music}. */
    static boolean isPackage(String text) {
        return text.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }

    /** A camera view ID, as the camera view picker stores it. */
    static NavTarget camera(String viewId) {
        String id = viewId == null ? "" : viewId.trim();
        if (id.isEmpty()) throw new IllegalArgumentException("pick a camera view");
        return new NavTarget(Kind.CAMERA, id);
    }

    /**
     * The kiosk's calendar on one of {@link #CALENDAR_VIEWS}. Default, or nothing, opens the view the
     * kiosk's Calendar settings name. Needs Kiosk Satellite 2026.10.33 or newer.
     */
    static NavTarget calendar(String view) {
        String text = view == null ? "" : view.trim();
        if (text.isEmpty() || "Default".equals(text)) return new NavTarget(Kind.CALENDAR, "");
        for (String known : CALENDAR_VIEWS) {
            if (known.equals(text)) return new NavTarget(Kind.CALENDAR, text.toLowerCase(Locale.ROOT));
        }
        throw new IllegalArgumentException("pick a calendar view");
    }

    static NavTarget url(String address) {
        String text = address == null ? "" : address.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("a web address starts with https:// or http://");
        }
        if (text.contains("@")) throw new IllegalArgumentException("web addresses cannot contain credentials");
        return new NavTarget(Kind.URL, text);
    }

    /** One of {@link #ACTIONS}. */
    static NavTarget action(String name) {
        String text = name == null ? "" : name.trim();
        switch (text) {
            case "Music": return new NavTarget(Kind.MUSIC, "");
            case "Now playing": return new NavTarget(Kind.NOW_PLAYING, "");
            case "Screensaver": return new NavTarget(Kind.SCREENSAVER, "");
            case "App launcher": return new NavTarget(Kind.APPS, "");
            case "Reload": return new NavTarget(Kind.RELOAD, "");
            case "Start page": return new NavTarget(Kind.HOME, "");
            default: throw new IllegalArgumentException("pick a kiosk action");
        }
    }

    /** The normalized dashboard path this target highlights, or null when it never matches a page. */
    String matchPath() {
        return isPage() ? Paths.normalize(value) : null;
    }

    /** A dashboard view or a built-in page: the targets that light up while their page is shown. */
    boolean isPage() {
        return kind == Kind.PAGE || kind == Kind.PANEL;
    }

    /**
     * Whether {@code path} is this target's page. A built-in page also owns its sub-pages, so Energy
     * stays lit on its Electricity and Gas tabs. A dashboard view matches only itself.
     */
    boolean shows(String path) {
        String own = matchPath();
        if (own == null || path == null) return false;
        String here = Paths.normalize(path);
        if (own.equals(here)) return true;
        return kind == Kind.PANEL && here.startsWith(own + "/");
    }

    /** The KS command that performs this target. */
    Command command() {
        switch (kind) {
            // haNavigate takes a query string and fragment too, and stays in the page.
            case PAGE:
            case PANEL: return new Command("haNavigate", args("path", value));
            case URL: return new Command("loadUrl", args("url", value));
            case APP: return new Command("launchApp", args("package", value));
            case CAMERA: return new Command("showCameraView", args("viewId", value));
            case CALENDAR: return new Command("openCalendar", value.isEmpty() ? Collections.<String, Object>emptyMap() : args("view", value));
            case MUSIC: return new Command("showMusicAssistant", Collections.<String, Object>emptyMap());
            case NOW_PLAYING: return new Command("showNowPlaying", Collections.<String, Object>emptyMap());
            case SCREENSAVER: return new Command("startScreensaver", Collections.<String, Object>emptyMap());
            case APPS: return new Command("showAppLauncher", Collections.<String, Object>emptyMap());
            case RELOAD: return new Command("reload", Collections.<String, Object>emptyMap());
            case HOME: return new Command("loadStartUrl", Collections.<String, Object>emptyMap());
            default: throw new IllegalStateException("Unhandled target " + kind);
        }
    }

    private static Map<String, Object> args(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}