package app.rainline;

/** Local appearance presets. Applying one never changes location, timing or the rain scale. */
public enum WidgetTheme {
    LIGHT(0xfff5f7fa, 0xff263547, 0xff1565c0, 0xffb42318, 16, true),
    DARK(0xff202833, 0xffe3eaf2, 0xff82c8ff, 0xffff8585, 16, true),
    MINIMAL(0x00000000, 0xffffffff, 0xffffffff, 0xffff0000, 0, false);

    final int background, foreground, rain, highlight, radius;
    final boolean normalLine;

    WidgetTheme(int background, int foreground, int rain, int highlight, int radius, boolean normalLine) {
        this.background = background;
        this.foreground = foreground;
        this.rain = rain;
        this.highlight = highlight;
        this.radius = radius;
        this.normalLine = normalLine;
    }

    public void applyTo(WidgetSettings s) {
        s.backgroundColor = background;
        s.foregroundColor = foreground;
        s.rainColor = rain;
        s.highlightColor = highlight;
        s.cornerRadius = radius;
        s.normalLine = normalLine;
        s.paddingLeft = s.paddingRight = this == MINIMAL ? 8 : 16;
        s.paddingTop = this == MINIMAL ? 9 : 16;
        s.paddingBottom = this == MINIMAL ? 1 : 12;
        s.guides = 2;
        s.labelSize = this == MINIMAL ? 11 : 12;
        s.showAxis = s.showTicks = s.labels = true;
    }

    public boolean matches(WidgetSettings s) {
        WidgetSettings preset = new WidgetSettings();
        applyTo(preset);
        return s.backgroundColor == background && s.foregroundColor == foreground && s.rainColor == rain
                && s.highlightColor == highlight && s.cornerRadius == radius && s.normalLine == normalLine
                && s.paddingLeft == preset.paddingLeft && s.paddingRight == preset.paddingRight
                && s.paddingTop == preset.paddingTop && s.paddingBottom == preset.paddingBottom
                && s.guides == preset.guides && s.labelSize == preset.labelSize
                && s.showAxis && s.showTicks && s.labels;
    }
}
