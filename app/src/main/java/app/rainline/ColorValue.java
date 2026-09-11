package app.rainline;

import java.util.Locale;

/** Hex input shared by settings persistence and the colour picker. */
public final class ColorValue {
    private ColorValue() {}
    public static int parse(String input) {
        String hex = input.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("(?i)([0-9a-f]{6}|[0-9a-f]{8})"))
            throw new IllegalArgumentException("Use #RRGGBB or #AARRGGBB");
        long value = Long.parseLong(hex, 16);
        return (int) (hex.length() == 6 ? value | 0xff000000L : value);
    }
    public static String format(int color) {
        return (color >>> 24) == 255 ? String.format(Locale.ROOT, "#%06X", color & 0xffffff)
                : String.format(Locale.ROOT, "#%08X", color);
    }
}
