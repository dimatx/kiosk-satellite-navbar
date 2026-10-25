// SPDX-License-Identifier: Apache-2.0
package com.shymoose.ksnavbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The apps the picker offers: the ones the kiosk's App Launcher lists, which are the only ones a plugin may open. */
final class AppChoices {
    private AppChoices() {}

    /** The picker's key on an item row, and on a menu entry row. They share no list in Kiosk Satellite. */
    static final String[] KEYS = {"items.app", "items.entries.app"};

    static boolean isAppKey(String key) {
        for (String known : KEYS) if (known.equals(key)) return true;
        return false;
    }

    /**
     * Grouped choices from the {@code getLauncherApps} result, a list of {@code {package, label}}. An app
     * without a label shows its package. A package listed twice, or not a package name, is skipped.
     */
    static List<Map<String, Object>> choices(Object data) {
        List<Map<String, Object>> choices = new ArrayList<>();
        if (!(data instanceof List)) return choices;
        Set<String> seen = new LinkedHashSet<>();
        for (Object entry : (List<?>) data) {
            if (!(entry instanceof Map)) continue;
            Map<?, ?> app = (Map<?, ?>) entry;
            String pkg = text(app.get("package"));
            if (pkg.isEmpty() || pkg.length() > 512 || !NavTarget.isPackage(pkg) || !seen.add(pkg)) continue;
            String label = text(app.get("label"));
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("id", pkg);
            choice.put("label", clip(label.isEmpty() ? pkg : label, 120));
            if (!label.isEmpty()) choice.put("detail", clip(pkg, 120));
            choice.put("icon", "mdi:application-outline");
            choices.add(choice);
            if (choices.size() == MAX) break;
        }
        return Collections.unmodifiableList(choices);
    }

    /** The most choices Kiosk Satellite takes for one setting. */
    static final int MAX = 2000;

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String text(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }
}