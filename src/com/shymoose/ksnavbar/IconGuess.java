// SPDX-License-Identifier: Apache-2.0
package com.shymoose.ksnavbar;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Picks a Material Design Icon from an item's label when its Icon setting is empty, so "Driveway"
 * gets a road and "Living room" a sofa. Rules are checked in order and the first match wins, so more
 * specific phrases come before general words. Matching is on whole words, case-insensitively.
 */
final class IconGuess {
    private IconGuess() {}

    private static final String[][] RULES = {
        // Cameras and places around a house.
        {"mdi:garage-variant", "garage interior", "garage inside", "inside garage"},
        {"mdi:garage", "garage", "carport"},
        {"mdi:road-variant", "driveway"},
        {"mdi:road", "street", "road", "sidewalk"},
        {"mdi:doorbell-video", "doorbell", "klingel", "türklingel"},
        {"mdi:door", "porch", "front door", "back door", "door", "entry", "entrance", "foyer", "mudroom"},
        {"mdi:gate", "gate"},
        {"mdi:patio-heater", "patio"},
        {"mdi:table-furniture", "deck", "terrace"},
        {"mdi:balcony", "balcony"},
        {"mdi:pool", "pool"},
        {"mdi:flower-outline", "garden", "flower", "flowers"},
        {"mdi:grass", "yard", "backyard", "lawn", "grass"},
        {"mdi:barn", "shed", "barn"},
        {"mdi:fence", "fence"},
        {"mdi:home-export-outline", "exterior", "outside", "outdoor", "outdoors"},
        {"mdi:home-import-outline", "interior", "inside", "indoor", "indoors"},
        {"mdi:arrow-up-circle-outline", "north"},
        {"mdi:arrow-down-circle-outline", "south"},
        {"mdi:arrow-right-circle-outline", "east"},
        {"mdi:arrow-left-circle-outline", "west"},
        // Rooms.
        {"mdi:sofa", "living", "lounge"},
        {"mdi:sofa-outline", "family", "den"},
        {"mdi:stove", "kitchen"},
        {"mdi:table-chair", "dining"},
        {"mdi:bed", "bedroom", "bed", "nursery"},
        {"mdi:desk", "office", "study"},
        {"mdi:dumbbell", "gym", "fitness", "workout"},
        {"mdi:washing-machine", "laundry"},
        {"mdi:shower", "bathroom", "bath", "shower"},
        {"mdi:stairs-down", "basement"},
        {"mdi:stairs", "stairs", "stairway", "staircase"},
        {"mdi:television", "tv", "media", "theater", "theatre"},
        // Dashboards, devices and features.
        {"mdi:home-lightning-bolt", "energy", "power", "electricity"},
        {"mdi:solar-power-variant-outline", "solar"},
        {"mdi:weather-partly-cloudy", "weather", "forecast"},
        {"mdi:thermostat", "climate", "thermostat", "heating", "hvac"},
        {"mdi:heart-pulse", "system health", "system status"},
        {"mdi:air-filter", "air", "health"},
        {"mdi:sprinkler-variant", "irrigation", "sprinkler", "sprinklers"},
        {"mdi:robot-vacuum", "vacuum", "vacuuming"},
        {"mdi:lightbulb-outline", "light", "lights", "lighting"},
        {"mdi:fan", "fan", "fans"},
        {"mdi:blinds", "blinds", "shades", "covers"},
        {"mdi:shield-home-outline", "security", "alarm"},
        {"mdi:lock-outline", "lock", "locks"},
        {"mdi:toggle-switch-outline", "toggles", "switches", "toggle"},
        {"mdi:devices", "devices", "device"},
        {"mdi:car", "car", "cars", "vehicle", "vehicles"},
        {"mdi:dog", "dog", "dogs", "pet", "pets"},
        {"mdi:cat", "cat", "cats"},
        {"mdi:baby-face-outline", "baby", "kids"},
        {"mdi:music", "music", "audio"},
        {"mdi:printer-3d", "printer", "3d"},
        {"mdi:server", "server", "servers"},
        {"mdi:network-outline", "network"},
        {"mdi:heart-pulse", "system", "status"},
        {"mdi:microphone-message", "voice", "assistant", "satellite"},
        {"mdi:account-outline", "profile", "user", "account"},
        {"mdi:cog", "settings", "setup"},
        {"mdi:calendar-outline", "calendar", "agenda"},
        {"mdi:chart-line", "history", "stats", "statistics"},
        {"mdi:refresh", "reload", "refresh"},
        {"mdi:cctv", "camera", "cameras", "cctv"},
        {"mdi:view-dashboard-outline", "overview", "dashboard"},
        {"mdi:dots-horizontal-circle-outline", "more"},
        {"mdi:home-outline", "home", "house"},
    };

    private static final Pattern[] PATTERNS = new Pattern[RULES.length];

    static {
        for (int i = 0; i < RULES.length; i++) {
            StringBuilder words = new StringBuilder();
            for (int j = 1; j < RULES[i].length; j++) {
                if (words.length() > 0) words.append('|');
                words.append(Pattern.quote(RULES[i][j]).replace(" ", "\\E\\s+\\Q"));
            }
            PATTERNS[i] = Pattern.compile("(?<![a-z0-9])(?:" + words + ")(?![a-z0-9])");
        }
    }

    /** The icon for a label, or null when no rule matches. */
    static String forLabel(String label) {
        if (label == null) return null;
        String text = label.toLowerCase(Locale.ROOT);
        for (int i = 0; i < RULES.length; i++) {
            if (PATTERNS[i].matcher(text).find()) return RULES[i][0];
        }
        return null;
    }
}
