// SPDX-License-Identifier: Apache-2.0
package com.shymoose.ksnavbar;

/** Dashboard path helpers shared by matching and visibility. */
final class Paths {
    private Paths() {}

    static String stripTrailingSlashes(String path) {
        String result = path;
        while (result.length() > 1 && result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    /**
     * Drops the query and fragment and trailing slashes, and treats a dashboard's first view
     * ({@code /dash/0}) as its root ({@code /dash}), the way Home Assistant serves both.
     */
    static String normalize(String path) {
        if (path == null) return null;
        String result = path.trim();
        int cut = indexOfAny(result, '?', '#');
        if (cut >= 0) result = result.substring(0, cut);
        if (result.isEmpty()) return "/";
        if (!result.startsWith("/")) result = "/" + result;
        result = stripTrailingSlashes(result);
        if (result.matches("^/[^/]+/0$")) result = result.substring(0, result.length() - 2);
        return result;
    }

    /**
     * A hide-on entry. A bare page such as energy, which no dashboard view path looks like, also
     * covers its sub-pages, so it is kept as a prefix match.
     */
    static String hideEntry(String page) {
        String path = normalize(page);
        String raw = stripTrailingSlashes(page.trim().replaceAll("[?#].*$", ""));
        boolean bare = !raw.isEmpty() && raw.indexOf('/', 1) < 0 && !raw.equals("/");
        return bare ? path + "/*" : path;
    }

    private static int indexOfAny(String text, char a, char b) {
        int first = text.indexOf(a);
        int second = text.indexOf(b);
        if (first < 0) return second;
        if (second < 0) return first;
        return Math.min(first, second);
    }
}
