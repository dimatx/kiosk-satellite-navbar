# Changelog

## 0.20.0

- **Added** an **Any action** choice for **Opens**. Pick any action with the same chooser as Gestures, including another plugin's commands such as a doorbell view. It works on items and menu entries and needs no extra permission.
- **Changed** empty icons are no longer saved back into the settings. The bar still draws the icon it guesses from the label.
- **Changed** built-in page rows no longer show a subtitle, because rows allow four subtitle fields.
- Needs Kiosk Satellite 2026.10.42 or newer.

## 0.19.0

- An item or menu entry can open an **App**, picked from the apps in the kiosk's App Launcher (issue 4). Needs Kiosk Satellite 2026.10.39 or newer, which this release now declares with minAppVersion.

## 0.18.0

- An item or menu entry can open a **Built-in page** that no dashboard list shows: Energy, Map, Logbook, History, To-do or Media, or any other path under **Other** (issue 3). The item stays lit on the page's tabs.
- New screenshots on a calmer dashboard.

## 0.17.0

- An item or menu entry can open the kiosk's **Calendar**, on its default view or on Day, Week, Month or Schedule. Needs Kiosk Satellite 2026.10.33 or newer (issue 1).
- A bar holds up to 16 items, as many as a menu (issue 2).

## 0.16.4

- An amber icon tile, so the icon stands out against the teal Community Plugins page.

## 0.16.3

- A simpler icon that reads at the small sizes Community Plugins and Browse plugins use.

## 0.16.2

- README: up to date on where menus open, Fleet Management and Scale UI.

## 0.16.1

- A menu longer than the bar is centered on the bar instead of on its item, so a tall menu beside a short rail hangs evenly around it. Shorter menus still open beside their item.

## 0.16.0

- An icon for Community Plugins and **Browse plugins**.
- On Kiosk Satellite 2026.10.30 and newer the bar and its menus follow each kiosk's **Scale UI**, and **Size** acts on top of it. With Fleet Management, keep one Size for the whole fleet and set Scale UI per kiosk.
- The README has a one-click install button and says what the plugin does with your data.
- Plugin SDK updated to Kiosk Satellite 2026.10.31.

## 0.15.2

- The bar and its menus are no longer cut off on a kiosk whose **UI scale** is above 100%. Kiosk Satellite sizes a plugin overlay for the scaled UI and made the window smaller than the bar, so its far end and bottom were cut off. The bar now detects this and sizes itself so the window fits (SDK gap 26).

## 0.15.1

- Plugin SDK updated to Kiosk Satellite 2026.10.25. No change in behavior; the navbar still needs 2026.10.23 or newer.
- The README links the new [Action Bar](https://github.com/dimatx/kiosk-satellite-action-bar) companion.
- SDK gap 17 (template tooling on Windows) is resolved upstream.

## 0.15.0

- Auto-hide pauses while a menu is open. The menu still closes after 30 seconds, then the bar's countdown restarts. Changing Hide after applies immediately.
- Startup camera and screensaver reads cannot overwrite newer events. Until the screensaver state is known, the bar stays below it.
- Successful retries clear the corresponding error without hiding unrelated failures.
- Bars and rails scroll when they do not fit. Menu grids adapt their column count to available width, and tall menus scroll.
- Touch targets stay at least 48 dp high at every size. The tab keeps its dimensions on unmatched pages.
- Highlight updates touch only the affected bar items and reuse icon drawables where possible. Tab selection no longer repeats layout and styling.
- Added virtual-clock controller tests for menu timeouts, settings changes, callback ordering, session shutdown and error recovery.

## 0.14.0

- **Show over camera views**, on by default. While a camera view is open, the bar stays on screen above it with a **Close** button at its end. The item that opens the camera lights up, so you can switch cameras from the grid or leave. Turn it off to keep the bar under camera views as before.
- **Hide the bar on** replaces *Pages without the bar*, as its own section at the end of the settings page.
- The README is reorganized for first-time visitors.

## 0.13.0

- **Tap opens**, optional on a menu item. Pick a page and a tap opens it, while a long press opens the menu. The item lights up on that page as well as on its entries' pages. Left empty, a tap opens the menu as before.
- **Pages without the bar** has its own section at the end of the page, so the pages you add are clearly grouped.

## 0.12.0

- **Plainer wording.** Settings now match Kiosk Satellite's own pages: the **Look** group is **Appearance**, and every setting has a short description. **Swipe to tuck away** is now **Swipe to hide**, with Start visible, Start hidden or Off, and **Tuck away after** is now **Hide after**.
- **Pages without the bar** replaces **Hide on**. Pick pages with the dashboard picker instead of typing paths. Each picked page hides the bar on that view only.
- **Icons fill themselves in.** An item or menu entry left without an icon gets the one the bar picked for it, so the settings page shows the icon in use.
- **Quieter status.** The plugin page shows a status line only when something needs attention, such as an item that cannot be used.
- Menu entries come right after **Opens**, ahead of the icons.

**Upgrading:** choose **Swipe to hide** again if it was Start tucked away, and add any **Hide on** pages under **Pages without the bar**.

## 0.11.0

Rebuilt on the plugin SDK in Kiosk Satellite 2026.10.23. Needs that version or newer.

- **Items are a list.** Add, edit, reorder and delete items on the plugin's page instead of filling in seven fixed slots. A menu's entries are a list inside its item.
- **Pickers instead of typing.** Pages come from the dashboard picker, camera views from the camera view picker by name, and icons from the icon picker. No more paths, 32-character IDs or `Label = target` strings.
- **Settings that apply.** Each item shows only the fields its **Opens** choice needs, and **Tuck away after** shows only while tucking is on.
- **Keep the dashboard clear.** New setting, on by default. The dashboard ends above the bar, so no card-mod padding is needed.
- **Menus open over their item** and close on a tap anywhere else, without pressing what is under the tap. Menus scale in and fade out, and the bar fades in.
- **Icons come from Kiosk Satellite.** The bundled icon font is gone, and the package shrank from 668 KB to 37 KB.
- **Pages with a query string**, such as `cameras/0?camera=garage`, stay in the page instead of reloading it.
- A camera menu can now hold every camera: up to 16 entries, with no length limit.

**Upgrading:** the settings changed shape, so the old item slots do not carry over. Add your items again under **Items**.

## 0.10.2

- With Labels set to Active only, the bar keeps the same size as you move between pages, so it no longer redraws from scratch on each change.
- Long labels end with an ellipsis instead of widening their item.
- An open menu closes by itself after 30 seconds.
- TalkBack reads which item is selected.

## 0.10.1

- While a menu is open, its item shows a lighter pill, so it is clear where the menu came from.
- The Kiosk Satellite log no longer gets a line for every tap, only for taps that arrive more than 150 ms late.

## 0.10.0

- **Left and right.** Position can be Left or Right for a vertical rail. Menus open beside it, and a sideways swipe tucks it into a tab on the edge.
- **Tidier settings.** Look and Behavior groups, and each item lists Label and Target before its icons. Collapsing is now **Swipe to tuck away**, with Start open, Start tucked away or Off, and **Tuck away after**. Shorter descriptions throughout.
- Haptic feedback on taps, where the device supports it.

**Upgrading:** set Swipe to tuck away again if it was Start collapsed.

## 0.9.1

- Actions with no icon get one that fits: screensaver, apps, now playing, web addresses.
- A new README with screenshots.

## 0.9.0

- **Readable menus.** Menu entries are written `Label = target`, with an optional `= mdi:icon` at the end, instead of `Label|icon|target`.
- **Camera view IDs on their own.** A target that is a 32-character KS camera view ID opens that camera, with no `camera:` prefix. Twelve cameras fit in one menu.
- **Short setting descriptions.** The settings page gives a one-line hint, and the README has the details.
- The older pipe syntax and the `camera:` and `url:` prefixes are gone.

## 0.8.0

- **Simpler settings.** The bar shows on every page. An optional **Hide on** replaces Show on. Items have no **Type**: an item is on when it has a label, and what it does follows its target. **Edge distance** and **Menu layout** are gone. The edge distance is fixed, and menus switch to a grid on their own above 6 entries. 43 settings down to 35.

**Upgrading:** set Hide on if the bar should stay off some pages, and clear the Label of any item that was Off.

## 0.7.1

- Fixed: in 0.7.0 the bar could stay invisible, and expanding could leave a stretched frame on screen. The bar now skips drawing while its size changes instead of using transparency, and reveals itself 150 ms after KS reports the new size.

## 0.7.0

- **No more stretched frames.** The bar, its tab and menus stay hidden while their overlay is being resized, and appear once KS has applied the new size. Before, the previous frame showed stretched to the new size for a moment.
- Menus open in their own overlay, so opening one no longer resizes or redraws the bar. A menu card still sits over its item, and tapping the space beside it closes it.

## 0.6.0

- **Icons from labels.** An empty icon is picked from the label, such as Driveway → road, Garage → garage, Living → sofa, Exterior → house with an arrow out. Camera menus no longer show the same icon on every entry.
- Plainer plugin page. A short description, a status line in plain words, and units on the sliders (dp, %, s).

## 0.5.1

- Menus stay at least 95% opaque so their entries stay readable over a busy dashboard. The bar keeps its own Opacity.
- Every tap logs the command it runs, and logs it if the command fails.

## 0.5.0

- **Grid menus.** **Menu layout** can be Auto, List or Grid. Grid shows tiles with an icon over a label, and Auto uses a grid above 6 entries.
- Menus hold up to 16 entries.
- Camera entries without an icon get a camera icon, which also keeps long camera menus short enough to fit in the setting.

## 0.4.0

- **Swipe to collapse.** Swipe the bar toward its edge to fold it into its tab, and swipe the tab back or tap it to open the bar. The collapse chevron is gone.
- **Selected icon** is its own optional setting. A pipe in Icon still works for existing settings.
- Faster feedback. A press shows at once with no ripple animation, a tapped page is highlighted before Home Assistant confirms it, and moving the highlight no longer re-lays out the bar.
- Each tap logs how late KS delivered the touch and when the bar redrew.

## 0.3.2

- **Collapsing** now defaults to **Start open**. The bar starts expanded with a collapse chevron, and stays open until someone taps it. **Collapse after** still defaults to 0, which never collapses the bar on its own.

## 0.3.1

- The bar repaints when KS resizes its overlay. On a ThinkSmart View it could show as a blank box, or as the tab stretched to the size of the bar.

## 0.3.0

- The bar, its tab and its menus now live in one overlay. Collapsing, expanding and opening a menu are now instant. Before, each change created a new KS overlay, which could take several seconds to appear on a ThinkSmart View.
- Menus open next to their item instead of in the middle of the bar.
- Tapping the space beside an open menu closes it.

## 0.2.0

- **Opacity**: a translucent bar background, with solid icons and labels.
- **Collapsing**: the bar can fold into a tab at its edge, starting open or collapsed. **Collapse after** folds it away again after a set time without taps.
- Tapping a page on another dashboard no longer reloads it. `haNavigate` can report failure after it has already moved the page.
- The bar and menus are now managed on one thread, so a tap and a page change can no longer race.
- Failed taps are written to the KS log.

## 0.1.1

- The active item is highlighted when the bar first appears. A page change that arrived while the bar was being built was lost.

## 0.1.0

- First release: a native bar of up to 7 items that highlights the item for the page on screen.
- Page, Action and Menu items. Actions open camera views, Music Assistant, Now Playing, the screensaver, the app launcher, the Start URL or a URL, or reload the dashboard.
- Bundled Material Design Icons 7.4.47.
- Show, Hide and Toggle navbar actions.
