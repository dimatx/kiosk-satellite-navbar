# Plugin SDK gaps

Building this navbar ran into places where the Kiosk Satellite plugin SDK made a feature hard, slow or impossible. This page collects them for the KS maintainer. Each entry explains what the navbar needed, what happens today, how the plugin works around it and what would help.

Updated for **Kiosk Satellite 2026.10.40**. 2026.10.39 closed gap 33 and 2026.10.40 closed gap 34. Most of the original list was closed by 2026.10.23, and the open gaps were last measured on that version. 2026.10.27 closed gap 11; the plugin code changed in 2026.10.24 to 2026.10.29 touches none of the others. Gap numbers never change, so a closed gap keeps its number and the numbering has holes.

## How to read this page

Each open gap is one of three kinds:

| Kind | Who would change what |
| --- | --- |
| **Product** | A change to the KS app or the SDK itself |
| **Docs** | Behavior that is fine but undocumented, so plugin authors find it the hard way |
| **Tooling** | The plugin template's build and test scripts |

Some gaps have both a product fix and a docs stopgap. The docs part is also listed under [Documentation](#documentation), so it can land before the product fix does.

Priority reflects how much the gap costs this plugin's users today, not how hard it is to fix.

## At a glance

| # | Gap | Kind | Priority | What users notice |
| --- | --- | --- | --- | --- |
| 15 | Creating an overlay is very slow on low-power panels | Product, Docs | High | Menus take about a second to open on a ThinkSmart View |
| 16 | A resized `WRAP` overlay shows a stale, stretched frame | Product, Docs | High | Without a workaround, the bar flashes stretched when it hides or shows |
| 27 | Plugin overlays are garbled with Impeller on Mesa and minigbm | Product | Medium | On an x86 kiosk with an Intel GPU the bar is unreadable stripes until **Legacy renderer** is turned on |
| 25 | An overlay cannot change layer without being rebuilt | Product | Medium | A pause when a camera view opens or closes |
| 22 | A list row's subtitle shows raw picker values | Product | Medium | Items and hidden pages read `navbar-demo/lights` instead of *Navbar demo / Lights* |
| 23 | An icon field cannot show a fallback | Product | Medium | An empty icon looks unset, and the plugin can no longer fill it in |
| 21 | A list row cannot summarize a nested list | Product | Low | A menu item's row does not say what the menu holds |
| 24 | A list setting's description shows under the add row | Product | Low | **Hide the bar on** explains itself after its button |
| 19 | A local ZIP cannot replace a repository install | Product | Medium (developers) | Testing a build means uninstalling, which loses settings |
| 29 | Fleet sync holds a plugin update without saying so | Product | Medium | A plugin update reaches only some kiosks, with no word on the plugin's page why |
| 30 | A fleet install cannot replace a repository install of the same plugin | Product | Low | Adding a plugin to a profile fails on kiosks that already had it |
| 31 | The Community Plugins check fails on Windows | Tooling | Low (developers) | `community_plugins.py check` crashes before printing its results |
| 32 | The dashboard picker leaves out Home Assistant's built-in pages | Product | Low | **Tap opens** and **Hide the bar on** cannot name Energy, Map or Logbook |
| 20 | A rejected manifest does not say which rule failed | Product, Tooling | Low (developers) | Install fails with `Invalid list setting` and no detail |

## Open gaps

### Overlay responsiveness

The navbar is a set of native overlays, so how fast KS creates, resizes and layers them sets how quick the bar feels. These three gaps cost the most, and every plugin with an interactive overlay will run into them.

#### 15. Creating an overlay is very slow on low-power panels

*Product, Docs.*

**Need:** Collapse the bar into a tab, expand it and open menus, each as a quick response to a tap.

**Today:** Measured on a ThinkSmart View (Snapdragon 624, Android 8.1, KS 2026.10.21) with `adb screencap`. A new `showOverlay`, or a replacement at an existing key, rendered nothing for 8 s or more. One later showed as an undrawn grey box before it finished. The same panel reports 95% janky frames from the dashboard alone. Changes inside an existing overlay, such as moving the highlight, were immediate. Each overlay is a new Flutter platform view, and that seems to be the cost. Still the case on 2026.10.23.

**Workaround:** The bar is one long-lived overlay. Hiding and showing it only changes what is drawn inside it. Menus need their own overlay (see 16), but it is small and holds only the menu card, so it appears in about a second on the same panel.

**Suggestion:** Reuse the platform view when an overlay is replaced at the same key. Until then, say in `overlays.md` that creating or replacing an overlay is expensive on slow devices, and that changing an existing overlay's content is cheap.

#### 16. A resized `WRAP` overlay shows a stale, stretched frame

*Product, Docs.*

**Need:** A wrapped overlay that changes size, such as a bar collapsing to a tab, should repaint at its new size.

**Today:** Measured with `adb screencap` on a ThinkSmart View (Android 8.1, KS 2026.10.21). When the plugin view's measured size changes, KS resizes the platform view but does not redraw the content into the resized surface. The previous frame is scaled to the new size instead. This also explains why a new wrapped overlay can first appear as a blank box in its background color: its first frame was drawn at the 1 px placeholder size. The overlay stays like that until something else invalidates the view.

Logging and on-device `screencap` showed two more details:
- The host `OverlayFrame` reaches the new size before the surface behind it does. A redraw at the moment the host's size matches still lands in the old surface and shows stretched, and nothing redraws after that.
- A change to `View.setAlpha` alone never reached the screen. Content hidden with alpha 0 stayed invisible after alpha went back to 1, while ordinary redraws did arrive.

The 2026.10.23 release notes do not mention it. The new `enter` transitions animate a view that may still be drawing at the wrong size, so it matters as much as before.

**Workaround:** Redrawing after the resize only shortens the glitch, because the stretched frame is still composited first. The plugin skips drawing (an empty `dispatchDraw`, not alpha) from the moment the size will change until the host frame has the measured size. It stays hidden another 150 ms so the surface catches up, then draws, and redraws again at 300 ms and 800 ms. A 900 ms timeout reveals the view anyway. Menus are their own overlay, so opening one never resizes the bar.

**Suggestion:** Don't composite a resized texture until the view has drawn into it at the new size, for example by keeping the old size until the first frame at the new size arrives. Every plugin with a wrapped overlay that changes size hits this, so it belongs in KS rather than in each plugin. Until then, document it with the workaround above.

#### 25. An overlay cannot change layer without being rebuilt

*Product.*

**Need:** Keep the bar above a camera view while one is open, and back under KS's menu and the screensaver once it closes.

**Today:** `onTop` is fixed when the overlay is shown, and it places the overlay over the screensaver and the menu as well as camera views. To follow a camera view, the plugin listens for `camera.view`, replaces its own overlay with `onTop(true)`, and replaces it again when the view closes. Each replacement is a new overlay, so it pays the cost in 15. Because an `onTop` overlay also covers the screensaver, the plugin watches `screensaver.state` and drops back down for it.

**Suggestion:** A layer above camera views but below the screensaver and KS's menu, such as `OverlaySpec.layer(ABOVE_CAMERA)`, or a way to change an existing overlay's layer without replacing it.

#### 27. Plugin overlays are garbled with Impeller on Mesa and minigbm

*Product.*

**Need:** Show the bar on an x86 kiosk with an Intel GPU.

**Today:** On a Surface Pro 6 running Bliss OS 14 (Android 11, Mesa 24.0.8 on Intel UHD 620, `minigbm` gralloc), every plugin overlay draws as horizontal stripes: each row shows two squashed copies of the content, as if 32-bit pixels were read as 16-bit. Every overlay width from 198 to 329 px looked the same, so it is not an alignment problem. KS's own widgets and the dashboard draw correctly. Turning on **Legacy renderer** (`render.disable_impeller`) and restarting the app fixes it, so the fault is in how Impeller composites platform views on this driver.

**Workaround:** None in the plugin. The kiosk runs with **Legacy renderer** on.

**Suggestion:** Detect the bad combination and use Skia for platform views, or turn on **Legacy renderer** automatically when a plugin overlay is shown on Mesa with `minigbm`, the way it already does after startup crashes.
### The settings page

The 2026.10.23 pickers and lists made the navbar configurable without typing paths or JSON. What remains is how those lists present what users chose. These are all small, and all visible on the navbar's main settings page.

#### 22. A list row's subtitle shows raw picker values

*Product.*

**Need:** Show what a row opens in words, the way its picker does.

**Today:** The pickers show names: a page reads *Navbar demo / Lights* and a camera view shows its name. A row's `subtitle` shows the stored value instead, so a page row reads `navbar-demo/lights` and a camera row a 32-character view ID. The same applies when a row's title is a dashboard field: **Hide the bar on** lists its pages as `navbar-demo/settings`.

**Workaround:** Camera views are left out of row subtitles, so a camera item's row shows no subtitle.

**Suggestion:** Render `dashboard` and `cameraView` values in row titles and subtitles the way their pickers do.

#### 23. An icon field cannot show a fallback

*Product.*

**Need:** Leave an item's icon empty and have the settings page show the icon the bar picks for it from the label.

**Today:** An empty `icon` field reads *Choose an icon*, even though the bar draws an icon guessed from the label. Users read that as "no icon".

**Workaround:** None since 0.20.0. The plugin used to save the guessed icon back with `saveSettings`, but KS keeps a list that holds an `action` field exactly as the user saved it, so that no longer persists. An empty icon stays empty on the settings page while the bar draws the guess.

**Suggestion:** Let a plugin supply a placeholder for an empty `icon` field, such as a `placeholderIcon` it can update at runtime, shown greyed in the picker until the user chooses one.

#### 21. A list row cannot summarize a nested list

*Product.*

**Need:** Show something useful under a menu item's name in the Items list, such as "13 entries".

**Today:** A row's `subtitle` can only name fields that are not lists. A menu item has no page, camera or address, so its row shows the label and icon with no subtitle. The only way to see what the menu holds is to open it.

**Suggestion:** Let `subtitle` name a nested list and show its row count, or a short join of its rows' titles.

#### 24. A list setting's description shows under the add row

*Product.*

**Need:** Explain a list setting above its rows, the way other settings put their description under the title.

**Today:** A top-level `list` setting in its own group renders its rows first and its description in small text below the **Add** row. With **Hide the bar on** empty, the page reads *Add page* followed by *The bar stays hidden on these pages.*, so the explanation comes after the button it explains.

**Suggestion:** Render a list's title and description above its rows, or let a group carry a description.

### Installing and managing displays

These matter once there is more than one display, or more than one build.

#### 29. Fleet sync holds a plugin update without saying so

*Product.*

**Need:** Update a plugin on the leader and see every follower get it, or learn why one did not.

**Today:** A follower whose Kiosk Satellite version differs from the leader's takes no plugin updates at all until it is on the same version. The fleet page says only *Needs 2026.10.31* on the follower's row, and the follower's Plugin Manager says nothing. Measured on 2026.10.31 with 11 of 12 followers on 2026.10.29: after a navbar release only the follower on the leader's version moved up, and the rest stayed one or two releases behind with the plugin still marked as synced.

**Workaround:** Update every follower's Kiosk Satellite first, then the plugins.

**Suggestion:** Let plugin packages sync across app versions when the plugin's own manifest allows the follower's version, or at least say on the follower's plugin page and on the leader's Plugins row that the plugin is waiting for an app update.

#### 30. A fleet install cannot replace a repository install of the same plugin

*Product.*

**Need:** Move kiosks that already run a plugin from GitHub under fleet management.

**Today:** A follower that installed the plugin from its GitHub repository refuses the leader's package: the leader's row shows *Could not install Navbar: This plugin ID belongs to another repository. Uninstall it before changing sources.* The packages are the same release from the same repository, checked by SHA-256. Seen on a Fire Max 11 on 2026.10.31.

**Workaround:** Remove the plugin on that follower, which deletes its settings, and sync again.

**Suggestion:** When the leader's package comes from the same repository as the follower's install, take the install over and keep its settings, the way an update does.

#### 19. A local ZIP cannot replace a repository install

*Product.*

**Need:** Test a development build on a kiosk that runs the released version, then go back.

**Today:** **Install from ZIP** (and `installPlugin`) refuses a plugin that was installed from a GitHub repository: *This plugin ID belongs to another repository. Uninstall it before changing sources.* Retested on 2026.10.23. A ZIP can replace another ZIP and keeps its settings, but not a repository install.

**Workaround:** Save the plugin's settings over the remote API, remove it, install the ZIP and restore the settings, then the same in reverse.

**Suggestion:** Let a trusted local ZIP replace a repository install of the same plugin ID and keep its settings, perhaps marked as a development build until the next repository update.

### Building and debugging plugins

These only affect plugin authors, but each cost hours the first time.

#### 31. The Community Plugins check fails on Windows

*Tooling.*

**Need:** Run the submission check locally before submitting, as the template's README asks.

**Today:** On Windows, `python tools/community_plugins.py check <url>` runs every check and then crashes printing the report: `UnicodeEncodeError: 'charmap' codec can't encode character '\u2705'`. The console uses cp1252 and the report starts each line with an emoji.

**Workaround:** Set `PYTHONIOENCODING=utf-8` first.

**Suggestion:** Reconfigure stdout to UTF-8 in the script (`sys.stdout.reconfigure(encoding='utf-8')`), as the other template tools already do for Windows.

#### 32. The dashboard picker leaves out Home Assistant's built-in pages

*Product.*

**Need:** Put Energy, Map or Logbook on the bar. A user asked for Energy (issue 3).

**Today:** The `dashboard` picker, and `getDashboards` behind it, list only dashboards and their views. Home Assistant's own pages, such as Energy, Map, Logbook, History and To-do, are not dashboards, so no picker offers them. `haNavigate` opens them fine: `energy`, `energy/electricity`, `map`, `logbook`, `history`, `todo` and `media-browser` all navigated on 2026.10.33 and `getDashboardState` reported the path.

**Workaround:** 0.18.0 adds an **Opens** choice, **Built-in page**, with a fixed list and an **Other** path. The list is ours, so a page Home Assistant adds later, or one from an integration, needs **Other**. **Tap opens** and **Hide the bar on** still take only dashboard views.

**Suggestion:** Have `getDashboards` return the sidebar's built-in panels too, marked so a plugin can tell them apart, and list them in the `dashboard` picker. Both are available to the frontend as `hass.panels`.
#### 20. A rejected manifest does not say which rule failed

*Product, Tooling.*

**Need:** Find out why Kiosk Satellite rejects a manifest or a saved value.

**Today:** A ZIP whose list `row.subtitle` named five keys failed to install with only `Invalid list setting`. `PluginManifest.kt` raises that message for about ten different rules, such as a subtitle over four keys, a `required` field of the wrong type, a missing `fields` array or a `maxItems` out of range. Finding the cause meant reading the KS source. Saved values have the same problem: `configurePlugin` with a menu entry carrying a field only items have answered `Unknown plugin setting`, without the key or the row.

The template's `tools/plugin_manifest.py` does not check the new setting types at all, so the build passes and the problem shows up only at install time.

**Workaround:** This repo's `tools/test_manifest_schema.py` checks the list rules at build time.

**Suggestion:** Name the setting and the rule in the error, such as `items.row.subtitle: at most 4 keys` or `items[1].entries[0].iconSelected: unknown field`. Teach the template's manifest checker the same rules, so they fail at build time.

## Documentation

The KS plugin docs (`creating-plugins.md`, `overlays.md`, `ks-api.md`) already cover what this plugin relies on: overlay layering and `onTop`, list and picker rules (including the four-key `subtitle` limit), `visibleWhen`, `saveSettings` not calling `configure` again, and the threading contract. Two gaps remain, both stopgaps for a product fix above:

| # | Add to `overlays.md` |
| --- | --- |
| 15 | Creating or replacing an overlay is expensive on slow devices. Keep long-lived overlays and change their content instead |
| 16 | A `WRAP` overlay that changes size can show its previous frame stretched until it redraws. Describe the workaround until KS handles it |

## Resolved

| # | Gap | Resolved by | How the navbar uses it |
| --- | --- | --- | --- |
| 1 | Overlays could not reserve space in the dashboard | `setContentInsets` | **Keep the dashboard clear** reserves the bar's depth, or the tab's while the bar is hidden. No card-mod padding |
| 2 | No structured or long settings | `list` settings with nested lists | Items are a list, and a menu's entries a nested list, edited in a form instead of a 512-character string |
| 3 | Camera views could not be listed | `cameraView` picker and `getCameraViews` | Camera items and entries pick a view by name |
| 4 | Overlay positioning was limited to one inset | Per-edge insets and `offset` | A menu opens over its item, clear of the bar, and KS keeps it on screen |
| 5 | A small overlay could not detect a tap outside it | `closeOnOutsideTouch` | A tap anywhere else closes a menu without pressing what is under it |
| 6 | Plugin code could not read bundled assets | `assetPath` and `openAsset` | Not needed any more, see 7 |
| 7 | No Material Design Icons for native views | `KsTheme.icon` and the `icon` picker | Icons come from KS. The 1.3 MB bundled font is gone and the package shrank from 668 KB to 37 KB |
| 8 | `haNavigate` rejected query strings | Query strings and fragments accepted | Pages with a query, such as `cameras/0?camera=garage`, stay in the page instead of reloading it |
| 9 | No list of Home Assistant dashboards and views | `dashboard` picker and `getDashboards` | Page items, **Tap opens** and **Hide the bar on** pick views from a list |
| 10 | Threading contract for `showOverlay` was unspecified | Documented: never blocks, safe from any thread, `create` runs after the call | No change needed |
| 11 | Fleet sync did not carry plugins or their settings | 2026.10.27: a fleet profile's **Plugins** row installs the leader's plugins on its followers, with their settings and enabled state | Configure the bar on the leader. Settings sync per profile, so displays that need different items need their own profile
| 12 | `haNavigate` reported failure after a successful move | Returns true once the page is on the view | The `loadUrl` fallback and its page check are gone |
| 13 | Overlays had no show, hide or replace transition | `enter`, `exit` and `transitionDuration` | The bar fades in and out, and menus scale in and fade out |
| 14 | `/api/screenshot` did not show native overlays reliably | Fixed by 2026.10.23. Retested: an open menu shows in the API screenshot as it does in `adb screencap` | No more need for adb to check the bar |
| 17 | Template tooling failed on Windows | The template now finds `d8.bat` and skips the two tests Windows cannot run | This repo's tools match the template again |
| 18 | Settings could not depend on each other | `visibleWhen` | **Hide after** shows only while **Swipe to hide** is on, and each item shows only the fields its **Opens** choice needs |
| 26 | A wrapped overlay was cut off when the UI scale was above 100% | 2026.10.30 sizes the window with the unscaled pixel ratio | The workaround in the resize gate stays for older versions and never triggers on 2026.10.30 and newer |
| 28 | A fleet-synced plugin could not be sized per kiosk | 2026.10.30: plugins follow each kiosk's **Scale UI** (upstream issue 1037) | **Size** is a fleet-wide multiplier on top of each kiosk's Scale UI |
| 33 | Plugins could not launch an Android app | `launchApp` and `getLauncherApps` (2026.10.39, upstream issue 1122) | **App** items and entries pick from the App Launcher's apps with plugin choices and open them |
| 34 | A manifest could not declare a minimum Kiosk Satellite version | `minAppVersion` (2026.10.40, upstream issue 1140) | The manifest says 2026.10.39, so an older kiosk refuses the update and keeps the version it has |
