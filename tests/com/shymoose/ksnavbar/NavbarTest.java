// SPDX-License-Identifier: Apache-2.0
package com.shymoose.ksnavbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JVM tests for everything that does not draw. Run by tools/test.py with -ea. */
public final class NavbarTest {
    private static int passed;
    private static final String CAM = "a1b2c3d4e5f60718293a4b5c6d7e8f90";

    public static void main(String[] args) {
        paths();
        targets();
        commands();
        menus();
        guesses();
        config();
        System.out.println("NavbarTest: " + passed + " checks passed");
    }

    private static void paths() {
        eq("/lovelace", Paths.normalize("/lovelace/0"));
        eq("/lovelace", Paths.normalize("/lovelace/"));
        eq("/lovelace/kitchen", Paths.normalize("/lovelace/kitchen?edit=1#x"));
        eq("/", Paths.normalize(""));
        eq("/a/b", Paths.normalize("a/b"));
        eq("/a/b/0", Paths.normalize("/a/b/0"));

    }

    private static void targets() {
        eq(NavTarget.Kind.PAGE, NavTarget.page("lovelace/0").kind);
        // The dashboard picker stores paths without a leading slash. Both forms work.
        eq("lovelace/kitchen", NavTarget.page("/lovelace/kitchen/").value);
        eq("cameras/0?camera=garage", NavTarget.page("cameras/0?camera=garage").value);
        eq("/lovelace", NavTarget.page("lovelace/0").matchPath());
        eq("/cameras", NavTarget.page("cameras/0?camera=garage").matchPath());
        eq(CAM, NavTarget.camera(" " + CAM + " ").value);
        eq(NavTarget.Kind.URL, NavTarget.url("https://example.com/x").kind);
        for (String action : NavTarget.ACTIONS) check(NavTarget.action(action).value.isEmpty());
        eq(NavTarget.Kind.APPS, NavTarget.action("App launcher").kind);
        eq(NavTarget.Kind.HOME, NavTarget.action("Start page").kind);
        rejects(() -> NavTarget.page(""));
        rejects(() -> NavTarget.page("  /  "));
        rejects(() -> NavTarget.page("https://x.example/a"));
        rejects(() -> NavTarget.camera(""));
        rejects(() -> NavTarget.url("example.com"));
        rejects(() -> NavTarget.url("javascript:alert(1)"));
        rejects(() -> NavTarget.url("https://user:pw@example.com"));
        rejects(() -> NavTarget.action("Dance"));
        check(NavTarget.camera(CAM).matchPath() == null);
        panels();
        apps();
    }

    private static void apps() {
        NavTarget spotify = NavTarget.app(" com.spotify.music ");
        eq(NavTarget.Kind.APP, spotify.kind);
        eq("com.spotify.music", spotify.value);
        eq("launchApp", spotify.command().name);
        eq("com.spotify.music", spotify.command().arguments.get("package"));
        check(spotify.matchPath() == null);
        check(!spotify.isPage());
        rejects(() -> NavTarget.app(""));
        rejects(() -> NavTarget.app("spotify"));
        rejects(() -> NavTarget.app("com.spotify.music; rm"));
        Map<String, Object> row = new HashMap<>();
        row.put("label", "Spotify");
        row.put("opens", "App");
        row.put("app", "com.spotify.music");
        NavItem item = NavItem.fromRow(row, true);
        eq(NavItem.Type.ACTION, item.type);
        eq("mdi:application-outline", item.icon);
        check(!item.matches("/lovelace"));
        row.put("app", "");
        rejects(() -> NavItem.fromRow(row, true));
        // The picker lists what getLauncherApps returns: labelled, de-duplicated, package names only.
        List<Object> launcher = new ArrayList<>();
        launcher.add(appRow("com.spotify.music", "Spotify"));
        launcher.add(appRow("com.spotify.music", "Spotify again"));
        launcher.add(appRow("com.example.camera", ""));
        launcher.add(appRow("not a package", "Broken"));
        launcher.add("junk");
        List<Map<String, Object>> choices = AppChoices.choices(launcher);
        eq(2, choices.size());
        eq("com.spotify.music", choices.get(0).get("id"));
        eq("Spotify", choices.get(0).get("label"));
        eq("com.spotify.music", choices.get(0).get("detail"));
        eq("com.example.camera", choices.get(1).get("label"));
        check(!choices.get(1).containsKey("detail"));
        check(AppChoices.choices(null).isEmpty());
        check(AppChoices.isAppKey("items.app") && AppChoices.isAppKey("items.entries.app") && !AppChoices.isAppKey("items.page"));
    }

    private static Map<String, Object> appRow(String pkg, String label) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("package", pkg);
        app.put("label", label);
        return app;
    }

    private static void panels() {
        // Built-in pages are not in any dashboard list, so each choice names its path.
        for (Map.Entry<String, String> panel : NavTarget.PANELS.entrySet()) {
            NavTarget target = NavTarget.panel(panel.getKey(), "");
            eq(NavTarget.Kind.PANEL, target.kind);
            eq(panel.getValue(), target.value);
            eq("haNavigate", target.command().name);
            eq(panel.getValue(), target.command().arguments.get("path"));
        }
        eq("energy", NavTarget.panel("Energy", "ignored").value);
        eq("profile", NavTarget.panel("Other", " /profile/ ").value);
        rejects(() -> NavTarget.panel("Other", ""));
        rejects(() -> NavTarget.panel("Other", "https://x.example/a"));
        rejects(() -> NavTarget.panel("Dance", ""));
        rejects(() -> NavTarget.panel("", ""));
        // A built-in page stays lit on its tabs. A dashboard view matches only itself.
        NavTarget energy = NavTarget.panel("Energy", "");
        check(energy.shows("/energy"));
        check(energy.shows("/energy/electricity"));
        check(energy.shows("/energy/gas?x=1"));
        check(!energy.shows("/energy-two"));
        check(!energy.shows("/map"));
        check(!energy.shows(null));
        check(!NavTarget.page("lovelace/kitchen").shows("/lovelace/kitchen/sub"));
        check(NavTarget.page("lovelace/0").shows("/lovelace"));
        Map<String, Object> row = new HashMap<>();
        row.put("label", "Usage");
        row.put("opens", "Built-in page");
        row.put("panel", "Energy");
        NavItem item = NavItem.fromRow(row, true);
        eq(NavItem.Type.PAGE, item.type);
        check(item.matches("/energy/water"));
        eq("mdi:lightning-bolt-outline", item.icon);
        row.put("panel", "Other");
        row.put("panelPath", "profile");
        check(NavItem.fromRow(row, true).matches("/profile"));
        row.put("panelPath", "");
        rejects(() -> NavItem.fromRow(row, true));
        // A menu entry can open one too, and lights its menu.
        Map<String, Object> entry = new HashMap<>();
        entry.put("label", "Gas");
        entry.put("opens", "Built-in page");
        entry.put("panel", "Energy");
        check(NavItem.menu("More", "", "", Arrays.<Map<?, ?>>asList(entry)).matches("/energy/gas"));
    }

    private static void commands() {
        NavTarget.Command nav = NavTarget.page("lovelace/kitchen").command();
        eq("haNavigate", nav.name);
        eq("lovelace/kitchen", nav.arguments.get("path"));
        // haNavigate takes query strings now, so a query path stays in the page.
        NavTarget.Command query = NavTarget.page("cameras/0?camera=garage").command();
        eq("haNavigate", query.name);
        eq("cameras/0?camera=garage", query.arguments.get("path"));
        eq("showCameraView", NavTarget.camera(CAM).command().name);
        eq(CAM, NavTarget.camera(CAM).command().arguments.get("viewId"));
        // The calendar opens on the kiosk's default view, or on the one picked.
        eq("openCalendar", NavTarget.calendar("Default").command().name);
        check(NavTarget.calendar("Default").command().arguments.isEmpty());
        check(NavTarget.calendar("").command().arguments.isEmpty());
        eq("week", NavTarget.calendar("Week").command().arguments.get("view"));
        eq("schedule", NavTarget.calendar(" Schedule ").command().arguments.get("view"));
        rejects(() -> NavTarget.calendar("Year"));
        check(NavTarget.calendar("Day").matchPath() == null);
        for (String view : NavTarget.CALENDAR_VIEWS) NavTarget.calendar(view);
        Map<String, Object> calendarRow = new HashMap<>();
        calendarRow.put("label", "Calendar");
        calendarRow.put("opens", "Calendar");
        calendarRow.put("calendarView", "Month");
        NavItem calendarItem = NavItem.fromRow(calendarRow, true);
        eq(NavTarget.Kind.CALENDAR, calendarItem.target.kind);
        eq("month", calendarItem.target.value);
        eq("mdi:calendar-month", NavItem.kindIcon(NavTarget.Kind.CALENDAR));
        eq("showMusicAssistant", NavTarget.action("Music").command().name);
        eq("showNowPlaying", NavTarget.action("Now playing").command().name);
        eq("startScreensaver", NavTarget.action("Screensaver").command().name);
        eq("showAppLauncher", NavTarget.action("App launcher").command().name);
        eq("reload", NavTarget.action("Reload").command().name);
        eq("loadStartUrl", NavTarget.action("Start page").command().name);
        eq("loadUrl", NavTarget.url("https://x.example/").command().name);
        check(NavTarget.action("Music").command().arguments.isEmpty());
    }

    private static void menus() {
        Map<String, Object> climate = menuRow("Climate", "mdi:weather-partly-cloudy",
            page("Weather", "panel/weather", "mdi:weather-partly-cloudy"),
            page("Health and air", "panel/health-and-air", "mdi:home-heart"),
            action("Music", "Music"));
        NavItem menu = NavItem.fromRow(climate, true);
        eq(NavItem.Type.MENU, menu.type);
        eq(3, menu.children.size());
        eq(NavItem.Type.ACTION, menu.children.get(2).type);
        eq("mdi:music", menu.children.get(2).icon);
        eq("mdi:weather-partly-cloudy", menu.children.get(0).icon);
        check(menu.matches("/panel/weather"));
        check(menu.matches("/panel/health-and-air/"));
        check(!menu.matches("/panel/toggles"));
        check(!menu.matches(null));
        // A menu needs entries, and an entry cannot open another menu.
        rejects(() -> NavItem.fromRow(menuRow("Empty", ""), true));
        rejects(() -> NavItem.fromRow(menuRow("Deep", "", menuRow("Inner", "", page("A", "a/a", ""))), true));
        rejects(() -> NavItem.fromRow(menuRow("Bad", "", page("A", "", "")), true));
        rejects(() -> NavItem.fromRow(page("", "a/b", ""), true));
        Map<String, Object>[] many = rows(NavItem.MAX_CHILDREN + 1);
        rejects(() -> NavItem.fromRow(menuRow("Many", "", many), true));
        eq(NavItem.MAX_CHILDREN, NavItem.fromRow(menuRow("Full", "", rows(NavItem.MAX_CHILDREN)), true).children.size());
        // Camera entries without an icon get a camera icon, unless the label suggests a better one.
        NavItem cams = NavItem.fromRow(menuRow("Cameras", "mdi:cctv",
            camera("Cam 9", ""), camera("Garage", "mdi:door"), page("Zork", "a/z", ""), camera("Driveway", "")), true);
        eq(NavItem.CAMERA_ICON, cams.children.get(0).icon);
        eq("mdi:door", cams.children.get(1).icon);
        eq(NavItem.DEFAULT_ICON, cams.children.get(2).icon);
        eq("mdi:road-variant", cams.children.get(3).icon);
        eq("mdi:weather-partly-cloudy", NavItem.fromRow(menuRow("Weather", "", page("A", "a/a", "")), true).icon);
        // Icons: a blank icon is picked from the label, a blank selected icon follows the icon.
        Map<String, Object> home = page("Home", "a/b", "");
        home.put("iconSelected", "mdi:home");
        eq("mdi:home", NavItem.fromRow(home, true).iconSelected);
        eq("mdi:home-outline", NavItem.fromRow(home, true).icon);
        eq("mdi:cctv", NavItem.icons("mdi:cctv", "  ", "x")[1]);
        eq("x", NavItem.icons(" ", "", "x")[0]);
        eq("mdi:monitor-shimmer", NavItem.fromRow(action("Saver", "Screensaver"), true).icon);
        eq("mdi:apps", NavItem.fromRow(action("Launcher", "App launcher"), true).icon);
        Map<String, Object> web = row("Zork", "Web address");
        web.put("url", "https://x.example");
        eq("mdi:web", NavItem.fromRow(web, true).icon);
        // By default a tap opens the menu. With Tap opens set, a tap opens that page instead and a
        // long press opens the menu, and the item lights up on that page too.
        check(!menu.longPressMenu());
        check(menu.target == null);
        Map<String, Object> split = menuRow("Climate", "", page("Weather", "panel/weather", ""));
        split.put("tapPage", "panel/climate");
        NavItem both = NavItem.fromRow(split, true);
        eq(NavItem.Type.MENU, both.type);
        check(both.longPressMenu());
        eq("haNavigate", both.target.command().name);
        eq("panel/climate", both.target.value);
        check(both.matches("/panel/climate"));
        check(both.matches("/panel/weather"));
        check(!both.matches("/panel/other"));
        split.put("tapPage", "");
        check(!NavItem.fromRow(split, true).longPressMenu());
        // Tap opens is ignored on anything but a menu.
        Map<String, Object> plain = page("Home", "a/home", "");
        plain.put("tapPage", "a/other");
        check(!NavItem.fromRow(plain, true).longPressMenu());
        eq("a/home", NavItem.fromRow(plain, true).target.value);
        // Empty icons are filled in with the icon the bar draws, so the settings page shows them.
        List<Object> withEmpty = list(page("Garage", "a/g", ""), page("Kept", "a/k", "mdi:star"),
            menuRow("Cams", "", camera("Zork", ""), camera("Driveway", "")), page("", "a/x", ""), page("Bad", "", ""));
        List<Map<String, Object>> filledRows = NavItem.withIcons(withEmpty);
        eq("mdi:garage", filledRows.get(0).get("icon"));
        eq("mdi:star", filledRows.get(1).get("icon"));
        eq(NavItem.DEFAULT_ICON, filledRows.get(2).get("icon"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filledEntries = (List<Map<String, Object>>) filledRows.get(2).get("entries");
        eq(NavItem.CAMERA_ICON, filledEntries.get(0).get("icon"));
        eq("mdi:road-variant", filledEntries.get(1).get("icon"));
        eq("", filledRows.get(3).get("icon"));
        eq("", filledRows.get(4).get("icon"));
        check(NavItem.withIcons(filledRows) == null);
        check(NavItem.withIcons(null) == null);
        // KS fills in every field, including hidden ones. Only the fields Opens selects count.
        Map<String, Object> filled = page("Lights", "panel/lights", "");
        filled.put("camera", "");
        filled.put("url", "");
        filled.put("action", "Music");
        filled.put("entries", new ArrayList<Object>());
        eq(NavItem.Type.PAGE, NavItem.fromRow(filled, true).type);
        // A row with no Opens is a page, the list's first choice.
        Map<String, Object> bare = new LinkedHashMap<>();
        bare.put("label", "Bare");
        bare.put("page", "a/b");
        eq(NavItem.Type.PAGE, NavItem.fromRow(bare, true).type);
    }

    private static void guesses() {
        eq("mdi:garage-variant", IconGuess.forLabel("Garage interior"));
        eq("mdi:garage", IconGuess.forLabel("Garage"));
        eq("mdi:road-variant", IconGuess.forLabel("Driveway"));
        eq("mdi:door", IconGuess.forLabel("Front porch"));
        eq("mdi:sofa", IconGuess.forLabel("Living room"));
        eq("mdi:bed", IconGuess.forLabel("Blue bedroom"));
        eq("mdi:home-export-outline", IconGuess.forLabel("Exterior"));
        eq("mdi:home-import-outline", IconGuess.forLabel("Interior"));
        eq("mdi:arrow-down-circle-outline", IconGuess.forLabel("South"));
        eq("mdi:patio-heater", IconGuess.forLabel("Patio"));
        eq("mdi:heart-pulse", IconGuess.forLabel("System health"));
        eq("mdi:air-filter", IconGuess.forLabel("Health and air"));
        eq("mdi:toggle-switch-outline", IconGuess.forLabel("Toggles"));
        // Whole words only: these must not match bed, door or air.
        check(IconGuess.forLabel("Bedside") == null);
        check(IconGuess.forLabel("Outdoorsy") == null);
        check(IconGuess.forLabel("Fairy") == null);
        check(IconGuess.forLabel("Nook") == null);
        check(IconGuess.forLabel(null) == null);
    }

    private static void config() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("position", "Top");
        settings.put("labels", "Active only");
        settings.put("hideOn", list(hidePage("panel/settings"), hidePage("/lovelace/kiosk/")));
        settings.put("scale", "120%");
        Map<String, Object> home = page("Home", "lovelace/0", "mdi:home-outline");
        home.put("iconSelected", "mdi:home");
        settings.put("items", list(
            home,
            menuRow("Climate", "mdi:weather-partly-cloudy", page("Weather", "panel/weather", "")),
            page("Toggles", "panel/toggles", ""),
            camera("Cameras", "mdi:cctv"),
            page("Broken", "", ""),
            action("Bad", "Dance")));
        NavConfig cfg = NavConfig.parse(settings);
        check(cfg.top);
        check(!cfg.vertical);
        eq(NavConfig.Edge.TOP, cfg.edge);
        check(cfg.makeRoom);
        Map<String, Object> side = new HashMap<>(settings);
        side.put("position", "Left");
        NavConfig left = NavConfig.parse(side);
        eq(NavConfig.Edge.LEFT, left.edge);
        check(left.vertical);
        check(!left.top);
        check(!left.sameLook(cfg));
        side.put("position", "Right");
        eq(NavConfig.Edge.RIGHT, NavConfig.parse(side).edge);
        check(NavConfig.parse(side).vertical);
        eq(NavConfig.Labels.ACTIVE, cfg.labels);
        eq(1.2f, cfg.scale);
        eq(NavConfig.DEFAULT_INSET, cfg.inset);
        eq(Arrays.asList("/panel/settings", "/lovelace/kiosk"), cfg.hideOn);
        eq("mdi:home", cfg.items.get(0).iconSelected);
        eq("mdi:home-outline", cfg.items.get(0).icon);
        eq(4, cfg.items.size());
        eq(NavItem.Type.PAGE, cfg.items.get(0).type);
        eq(NavItem.Type.MENU, cfg.items.get(1).type);
        eq(NavItem.Type.ACTION, cfg.items.get(3).type);
        eq("mdi:toggle-switch-outline", cfg.items.get(2).icon);
        eq(2, cfg.problems.size());
        check(cfg.problems.get(0).startsWith("Broken: "));
        check(cfg.problems.get(1).startsWith("Bad: "));
        eq(0, cfg.activeIndex("/lovelace"));
        eq(0, cfg.activeIndex("/lovelace/0"));
        eq(1, cfg.activeIndex("/panel/weather"));
        eq(2, cfg.activeIndex("/panel/toggles"));
        eq(-1, cfg.activeIndex("/panel/cars"));
        eq(-1, cfg.activeIndex(null));
        // Shown everywhere except Hide on.
        check(cfg.visibleAt("/panel/cars"));
        check(cfg.visibleAt(null));
        // A picked page hides the bar on that view only, not on the views beside or below it.
        check(!cfg.visibleAt("/panel/settings"));
        check(!cfg.visibleAt("/panel/settings?tab=2"));
        check(cfg.visibleAt("/panel/settings-2"));
        check(cfg.visibleAt("/panel/settings/sub"));
        check(!cfg.visibleAt("/lovelace/kiosk"));

        // While a camera view is open, the item that opens it lights up, not the page under it.
        check(cfg.overCameras);
        eq(3, cfg.activeIndex("/panel/toggles", CAM));
        eq(2, cfg.activeIndex("/panel/toggles", null));
        eq(-1, cfg.activeIndex("/panel/toggles", "ffffffffffffffffffffffffffffffff"));
        check(cfg.items.get(3).showsCamera(CAM));
        check(!cfg.items.get(0).showsCamera(CAM));
        check(!cfg.items.get(3).showsCamera(null));
        NavItem camMenu = NavItem.fromRow(menuRow("Cams", "", page("A", "a/a", ""), camera("Front", "")), true);
        check(camMenu.showsCamera(CAM));
        Map<String, Object> noCameras = new HashMap<>(settings);
        noCameras.put("overCameras", false);
        check(!NavConfig.parse(noCameras).overCameras);
        check(!NavConfig.parse(noCameras).sameLook(cfg));
        Map<String, Object> wider = new HashMap<>(settings);
        wider.put("hideOn", new ArrayList<Object>());
        NavConfig everywhere = NavConfig.parse(wider);
        check(everywhere.visibleAt("/panel/settings"));
        check(everywhere.sameLook(cfg));
        wider.put("makeRoom", false);
        NavConfig floating = NavConfig.parse(wider);
        check(!floating.makeRoom);
        check(!floating.sameLook(cfg));

        // At most 16 items. Rows past that are left out.
        settings.put("items", list(rows(20)));
        eq(NavConfig.MAX_ITEMS, NavConfig.parse(settings).items.size());

        NavItem longMenu = NavItem.fromRow(menuRow("M", "", rows(7)), true);
        NavItem shortMenu = NavItem.fromRow(menuRow("M", "", rows(2)), true);
        NavConfig auto = NavConfig.parse(new HashMap<String, Object>());
        check(auto.grid(longMenu));
        check(!auto.grid(shortMenu));

        NavConfig empty = NavConfig.parse(new HashMap<String, Object>());
        check(!empty.top);
        eq(NavConfig.Labels.ALWAYS, empty.labels);
        eq(1f, empty.scale);
        eq(NavConfig.DEFAULT_INSET, empty.inset);
        eq(1f, empty.opacity);
        // Swipe to hide is on by default, starting open, and the dashboard keeps clear.
        eq(NavConfig.Collapse.START_OPEN, empty.collapse);
        eq(NavConfig.Edge.BOTTOM, empty.edge);
        check(!empty.vertical);
        check(empty.makeRoom);
        eq(0, empty.autoCollapse);
        check(empty.items.isEmpty());
        check(!empty.visibleAt("/anything"));

        Map<String, Object> folding = new HashMap<>(settings);
        folding.put("opacity", 70.0);
        folding.put("collapse", "Start hidden");
        folding.put("autoCollapse", 30.0);
        NavConfig fold = NavConfig.parse(folding);
        eq(0.7f, fold.opacity);
        eq(NavConfig.Collapse.START_COLLAPSED, fold.collapse);
        eq(30, fold.autoCollapse);
        folding.put("collapse", "Start visible");
        eq(NavConfig.Collapse.START_OPEN, NavConfig.parse(folding).collapse);
        folding.put("collapse", "Off");
        // A countdown without collapsing would have nothing to do.
        eq(0, NavConfig.parse(folding).autoCollapse);
        folding.put("opacity", 5.0);
        eq(0.2f, NavConfig.parse(folding).opacity);

        Map<String, Object> odd = new HashMap<>();
        odd.put("scale", "huge");
        odd.put("items", "not a list");
        NavConfig tolerant = NavConfig.parse(odd);
        eq(1f, tolerant.scale);
        check(tolerant.items.isEmpty());
        List<Object> mixed = new ArrayList<>();
        mixed.add("junk");
        mixed.add(page("Ok", "a/b", ""));
        odd.put("items", mixed);
        eq(1, NavConfig.parse(odd).items.size());
    }

    private static Map<String, Object> hidePage(String page) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("page", page);
        return row;
    }

    private static Map<String, Object> row(String label, String opens) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("opens", opens);
        row.put("icon", "");
        row.put("iconSelected", "");
        return row;
    }

    private static Map<String, Object> page(String label, String path, String icon) {
        Map<String, Object> row = row(label, NavItem.PAGE);
        row.put("page", path);
        row.put("icon", icon);
        return row;
    }

    private static Map<String, Object> camera(String label, String icon) {
        Map<String, Object> row = row(label, NavItem.CAMERA);
        row.put("camera", CAM);
        row.put("icon", icon);
        return row;
    }

    private static Map<String, Object> action(String label, String action) {
        Map<String, Object> row = row(label, NavItem.ACTION);
        row.put("action", action);
        return row;
    }

    @SafeVarargs
    private static Map<String, Object> menuRow(String label, String icon, Map<String, Object>... entries) {
        Map<String, Object> row = row(label, NavItem.MENU);
        row.put("icon", icon);
        row.put("entries", list(entries));
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object>[] rows(int count) {
        Map<String, Object>[] rows = new Map[count];
        for (int i = 0; i < count; i++) rows[i] = page("E" + i, "a/e" + i, "");
        return rows;
    }

    @SafeVarargs
    private static List<Object> list(Map<String, Object>... rows) {
        return new ArrayList<Object>(Arrays.asList(rows));
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { passed++; }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Check failed");
        passed++;
    }

    private static void eq(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
        passed++;
    }
}