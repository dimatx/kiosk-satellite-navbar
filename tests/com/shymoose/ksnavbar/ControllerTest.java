// SPDX-License-Identifier: Apache-2.0
package com.shymoose.ksnavbar;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Exercises the real controller with a virtual clock and deferred host callbacks, without drawing. */
public final class ControllerTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        menuTimeout();
        menuReplacement();
        changedDelay();
        cameraOrdering();
        statusRecovery();
        stoppedSession();
        layoutBounds();
        appChoices();
        System.out.println("ControllerTest: " + checks + " checks passed");
    }

    private static void menuTimeout() throws Exception {
        Fixture f = new Fixture(10);
        f.plugin.taps.onMenu(0, 0, 80);
        f.clock.advance(29_999);
        check(!collapsed(f.plugin), "open menu must suspend auto-hide");
        check(f.host.menu, "menu must stay open before 30 seconds");
        f.clock.advance(1);
        check(!f.host.menu, "menu must close at 30 seconds");
        f.clock.advance(9_999);
        check(!collapsed(f.plugin), "bar gets a fresh delay after menu closes");
        f.clock.advance(1);
        check(collapsed(f.plugin), "bar hides at 40 seconds");

        f = new Fixture(10);
        f.plugin.taps.onMenu(0, 0, 80);
        f.clock.advance(2_000);
        f.plugin.onEvent("overlay.closed", map("key", "menu"));
        f.clock.flush();
        f.clock.advance(9_999);
        check(!collapsed(f.plugin), "outside dismissal restarts the delay");
        f.clock.advance(1);
        check(collapsed(f.plugin), "outside dismissal eventually hides the bar");

        f = new Fixture(0);
        f.plugin.taps.onMenu(0, 0, 80);
        f.clock.advance(60_000);
        check(!f.host.menu && !collapsed(f.plugin), "zero delay keeps only the bar visible");
    }

    private static void changedDelay() throws Exception {
        Fixture f = new Fixture(60);
        f.clock.advance(5_000);
        int shows = f.host.barShows;
        f.plugin.configure(settings(2));
        f.clock.flush();
        check(shows == f.host.barShows, "delay-only change must not rebuild the overlay");
        f.clock.advance(2_000);
        check(collapsed(f.plugin), "new delay applies without a tap");

        f = new Fixture(2);
        f.plugin.configure(settings(0));
        f.clock.advance(10_000);
        check(!collapsed(f.plugin), "zero cancels an armed timer");
        f.plugin.configure(settings(1));
        f.plugin.execute("hide", Collections.emptyMap());
        f.clock.advance(5_000);
        check(!collapsed(f.plugin), "hidden bar has no auto-hide timer");
        f.plugin.execute("show", Collections.emptyMap());
        f.clock.flush();
        f.clock.advance(1_000);
        check(collapsed(f.plugin), "show starts a new timer");
    }

    private static void menuReplacement() throws Exception {
        Fixture f = new Fixture(5);
        f.plugin.taps.onMenu(0, 0, 80);
        OverlayFactory old = f.host.menus.get(0);
        f.plugin.taps.onMenu(0, 0, 80);
        f.plugin.taps.onMenu(0, 0, 80);
        old.onDestroy(null);
        f.clock.advance(10_000);
        check(!collapsed(f.plugin), "old menu destruction must not re-arm auto-hide");
        check((Integer) field(f.plugin, "openMenu") == 0, "old menu destruction preserves replacement");
        f.plugin.taps.onTarget(NavTarget.page("demo/page"));
        f.clock.advance(4_999);
        check(!collapsed(f.plugin), "choosing a menu entry restarts auto-hide");
        f.clock.advance(1);
        check(collapsed(f.plugin), "chosen menu entry eventually hides bar");
    }

    private static void cameraOrdering() throws Exception {
        Fixture f = new Fixture(0, false);
        f.plugin.onEvent("ks.camera.view", camera(true, "new"));
        f.host.reply("getCameraViewState", true, camera(false, null), null);
        f.clock.flush();
        check("new".equals(field(f.plugin, "camera")), "late startup read must not overwrite a camera event");
        check(!(Boolean) field(f.plugin, "builtOver"), "unknown screensaver state stays below KS");
        f.plugin.onEvent("ks.screensaver.state", map("active", false));
        f.host.reply("isScreensaverActive", true, true, null);
        f.clock.flush();
        check((Boolean) field(f.plugin, "builtOver"), "late screensaver read must not overwrite an event");
        f.plugin.onEvent("ks.screensaver.state", map("active", true));
        f.clock.flush();
        check(!(Boolean) field(f.plugin, "builtOver"), "active screensaver moves bar below KS");

        f = new Fixture(0, false);
        f.host.reply("getCameraViewState", true, camera(true, "existing"), null);
        f.host.reply("isScreensaverActive", true, true, null);
        f.clock.flush();
        check(!(Boolean) field(f.plugin, "builtOver"), "startup with an active screensaver never moves above it");
    }

    private static void statusRecovery() {
        Fixture f = new Fixture(0);
        NavTarget target = NavTarget.page("lovelace/lights");
        f.plugin.taps.onTarget(target);
        f.host.reply("haNavigate", false, null, "offline");
        f.clock.flush();
        check(f.host.status.contains("offline") && f.host.error, "command failure is visible");
        f.plugin.taps.onTarget(target);
        f.host.reply("haNavigate", true, true, null);
        f.clock.flush();
        check(f.host.status.isEmpty() && !f.host.error, "successful retry clears cached error");

        f.plugin.taps.onTarget(target);
        f.plugin.taps.onTarget(target);
        f.host.reply("haNavigate", false, null, "stale");
        f.host.reply("haNavigate", true, true, null);
        f.clock.flush();
        check(f.host.status.isEmpty(), "stale navigation error is ignored");

        f.plugin.onEvent("ks.browser.state", Collections.emptyMap());
        f.host.reply("getDashboardState", false, null, "read failed");
        f.clock.flush();
        check(f.host.status.contains("read failed"), "read error is visible");
        f.plugin.taps.onTarget(target);
        f.host.reply("haNavigate", true, true, null);
        f.clock.flush();
        check(f.host.status.contains("read failed"), "navigation success must not clear an unrelated error");
        f.plugin.onEvent("ks.browser.state", Collections.emptyMap());
        f.host.reply("getDashboardState", true, map("currentPath", "/lovelace/0"), null);
        f.clock.flush();
        check(f.host.status.isEmpty(), "same-page healthy read clears read error");
    }

    private static void stoppedSession() throws Exception {
        Fixture f = new Fixture(1, false);
        f.plugin.taps.onMenu(0, 0, 80);
        f.plugin.stop();
        f.clock.flush();
        f.host.reply("getCameraViewState", true, camera(true, "old"), null);
        f.host.reply("isScreensaverActive", true, false, null);
        f.clock.advance(60_000);
        check(!collapsed(f.plugin), "stop cancels pending timers");
        check(field(f.plugin, "camera") == null, "stopped session ignores reads");
        check(f.clock.tasks.isEmpty(), "stop leaves no scheduled work");
        Host replacement = new Host();
        f.plugin.start(replacement, settings(0));
        replacement.reply("getDashboardState", true, map("currentPath", "/demo/new"), null);
        replacement.reply("getCameraViewState", true, camera(false, null), null);
        replacement.reply("isScreensaverActive", true, false, null);
        f.clock.flush();
        check(replacement.barShows == 1, "reused plugin starts one new bar");
        check(replacement.status.isEmpty(), "new session starts clean");
    }

    private static void appChoices() {
        Fixture f = new Fixture(0);
        List<Object> apps = new ArrayList<>();
        Map<String, Object> app = new HashMap<>();
        app.put("package", "com.spotify.music");
        app.put("label", "Spotify");
        apps.add(app);
        // The list is made at start, with no run to report to.
        f.host.reply("getLauncherApps", true, apps, null);
        check(f.host.listed.get("items.app") == 1 && f.host.listed.get("items.entries.app") == 1, "apps listed for both pickers at start");
        check(f.host.results.isEmpty(), "a start listing has no run to end");
        // A Refresh press names its run and gets the outcome.
        f.plugin.onEvent("settings.choices", refresh("items.app", "r1"));
        f.host.reply("getLauncherApps", true, apps, null);
        check("r1:success:Found 1 app.".equals(f.host.results.get(0)), "refresh reports what it found");
        f.plugin.onEvent("settings.choices", refresh("items.entries.app", "r2"));
        f.host.reply("getLauncherApps", true, new ArrayList<Object>(), null);
        check(f.host.results.get(1).startsWith("r2:success:No apps are listed"), "an empty launcher says where to add apps");
        f.plugin.onEvent("settings.choices", refresh("items.app", "r3"));
        f.host.reply("getLauncherApps", false, null, "launcher off");
        check("r3:error:Could not list apps: launcher off".equals(f.host.results.get(2)), "a failed listing ends the run with the reason");
        // Another setting's refresh is not ours.
        f.plugin.onEvent("settings.choices", refresh("other", "r4"));
        check(f.host.replies.get("getLauncherApps").isEmpty(), "other settings are left alone");
        // A tap launches the app, and a refusal is shown.
        f.plugin.taps.onTarget(NavTarget.app("com.spotify.music"));
        f.host.reply("launchApp", true, null, null);
        f.clock.flush();
        check(f.host.status.isEmpty(), "launching an app leaves no error");
        f.plugin.taps.onTarget(NavTarget.app("com.spotify.music"));
        f.host.reply("launchApp", false, null, "not in the launcher");
        f.clock.flush();
        check(f.host.status.contains("not in the launcher") && f.host.error, "a refused launch is shown");
    }

    private static Map<String, Object> refresh(String key, String run) {
        Map<String, Object> payload = map("key", key);
        payload.put("run", run);
        return payload;
    }

    private static void layoutBounds() {
        for (int available : new int[] {240, 320, 480, 800}) {
            for (float scale : new float[] {.8f, 1f, 1.4f}) {
                int margin = Math.round(4 * scale);
                int columns = NavLayout.columns(16, available, Math.round(108 * scale));
                int width = NavLayout.tileWidth(available, columns, Math.round(104 * scale), margin);
                check(columns >= 1 && columns <= 4, "bounded grid columns");
                check(columns * (width + margin) <= available, "grid fits the available width");
                check(width >= NavLayout.MIN_TOUCH_DP, "grid tiles retain usable width");
            }
        }
    }

    private static boolean collapsed(NavbarPlugin plugin) throws Exception { return (Boolean) field(plugin, "collapsed"); }

    private static Object field(NavbarPlugin plugin, String name) throws Exception {
        Field field = NavbarPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(plugin);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static Map<String, Object> map(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private static Map<String, Object> camera(boolean active, String id) {
        Map<String, Object> map = map("active", active);
        map.put("viewId", id);
        return map;
    }

    private static Map<String, Object> settings(int delay) {
        Map<String, Object> page = map("label", "Home");
        page.put("opens", "Page");
        page.put("page", "lovelace/0");
        page.put("icon", "mdi:home");
        Map<String, Object> menu = map("label", "Menu");
        menu.put("opens", "Menu");
        menu.put("entries", Collections.singletonList(page));
        menu.put("icon", "mdi:menu");
        Map<String, Object> settings = map("items", Collections.singletonList(menu));
        settings.put("autoCollapse", delay);
        return settings;
    }

    private static final class Fixture {
        final Clock clock = new Clock();
        final Host host = new Host();
        final NavbarPlugin plugin = new NavbarPlugin(clock);
        Fixture(int delay) { this(delay, true); }
        Fixture(int delay, boolean ready) {
            plugin.start(host, settings(delay));
            host.reply("getDashboardState", true, map("currentPath", "/lovelace/0"), null);
            if (ready) {
                host.reply("getCameraViewState", true, camera(false, null), null);
                host.reply("isScreensaverActive", true, false, null);
            }
            clock.flush();
        }
    }

    private static final class Clock implements NavbarPlugin.Scheduler {
        private static final class Task {
            final Runnable run;
            final long time;
            Task(Runnable run, long time) { this.run = run; this.time = time; }
        }
        final List<Task> tasks = new ArrayList<>();
        long now;
        public void post(Runnable run) { postDelayed(run, 0); }
        public void postDelayed(Runnable run, long delay) { tasks.add(new Task(run, now + delay)); }
        public void removeCallbacks(Runnable run) { tasks.removeIf(task -> task.run == run); }
        void flush() { advance(0); }
        void advance(long duration) {
            long end = now + duration;
            int count = 0;
            while (!tasks.isEmpty()) {
                tasks.sort(Comparator.comparingLong(task -> task.time));
                Task next = tasks.get(0);
                if (next.time > end) break;
                tasks.remove(0);
                now = next.time;
                next.run.run();
                if (++count > 1_000) throw new AssertionError("runaway scheduler");
            }
            now = end;
        }
    }

    private static final class Host implements PluginHost {
        final Map<String, List<CommandCallback>> replies = new HashMap<>();
        int barShows;
        boolean menu;
        String status = "";
        boolean error;
        final List<OverlayFactory> menus = new ArrayList<>();
        public void subscribe(String event) {}
        public void executeCommand(String name, Map<String, Object> args, CommandCallback callback) {
            replies.computeIfAbsent(name, key -> new ArrayList<>()).add(callback);
        }
        void reply(String name, boolean ok, Object value, String error) {
            replies.get(name).remove(0).onResult(ok, value, error);
        }
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) {
            if ("bar".equals(key)) barShows++;
            else {
                menu = true;
                menus.add(factory);
            }
        }
        public void hideOverlay(String key) { if ("menu".equals(key)) menu = false; }
        public void setContentInsets(int l, int t, int r, int b) {}
        final Map<String, Integer> listed = new HashMap<>();
        final List<String> results = new ArrayList<>();
        public void setSettingChoices(String key, List<Map<String, Object>> choices) { listed.put(key, choices.size()); }
        public boolean setupResult(String run, String state, String message) { results.add(run + ":" + state + ":" + message); return true; }
        public void status(String message, boolean error) { this.status = message; this.error = error; }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) { throw new AssertionError(message); }
    }
}
