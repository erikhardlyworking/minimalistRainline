package app.rainline;

import org.junit.Test;
import static org.junit.Assert.*;

public final class WidgetThemeTest {
    @Test public void absentPreferencesStartLightButSavedLegacyPreferencesKeepTheirLook() {
        assertTrue(WidgetTheme.LIGHT.matches(WidgetSettings.fromJson(null)));
        assertTrue(WidgetTheme.LIGHT.matches(new WidgetSettings()));
        assertTrue(WidgetTheme.MINIMAL.matches(WidgetSettings.fromJson("{}")));
        WidgetSettings old = WidgetSettings.fromJson("{\"backgroundColor\":\"#80445566\",\"paddingTop\":28,"
                + "\"guides\":1,\"normalLine\":true,\"labels\":false,\"highlightColor\":\"#12AB34\"}");
        assertEquals(0x80445566, old.backgroundColor);
        assertEquals(28, old.paddingTop);
        assertEquals(1, old.guides);
        assertTrue(old.normalLine);
        assertFalse(old.labels);
        assertEquals(0xff12ab34, old.highlightColor);
        assertEquals(0xffffffff, old.rainColor);
        assertEquals(0xffffffff, old.foregroundColor);
        assertEquals(0, old.cornerRadius);
        assertEquals(old.toJson(), old.copy().toJson());
    }

    @Test public void themesRoundTripAndPreserveWeatherAndUpdateChoices() {
        WidgetSettings s = new WidgetSettings();
        s.follow = false; s.latitude = 59.9139; s.longitude = 10.7522;
        s.place = "Oslo"; s.locationAt = 12345; s.accuracy = 80;
        s.scaleMax = 6; s.highlightThreshold = 4; s.highlightRain = false;
        s.automatic = false; s.rainRefreshMinutes = 10; s.dryRefreshMinutes = 30;
        for (WidgetTheme theme : WidgetTheme.values()) {
            theme.applyTo(s);
            WidgetSettings copy = s.copy();
            assertTrue(theme.matches(copy));
            assertEquals(s.toJson(), copy.toJson());
            assertFalse(copy.follow);
            assertEquals(59.9139, copy.latitude, 0);
            assertEquals(10.7522, copy.longitude, 0);
            assertEquals("Oslo", copy.place);
            assertEquals(12345, copy.locationAt);
            assertEquals(80, copy.accuracy, 0);
            assertEquals(6, copy.scaleMax, 0);
            assertEquals(4, copy.highlightThreshold, 0);
            assertFalse(copy.highlightRain);
            assertFalse(copy.automatic);
            assertEquals(10, copy.rainRefreshMinutes);
            assertEquals(30, copy.dryRefreshMinutes);
            copy.rainColor = 0xff123456;
            assertFalse(theme.matches(copy));
            assertTrue(theme.matches(s));
        }
    }

    @Test public void customColoursAndCornersSurviveReload() {
        WidgetSettings s = new WidgetSettings();
        s.rainColor = 0xff113377; s.foregroundColor = 0xff224466;
        s.backgroundColor = 0x80442211; s.cornerRadius = 8;
        s.paddingLeft = 3; s.labels = false;
        assertEquals(s.toJson(), s.copy().toJson());
        WidgetSettings invalid = WidgetSettings.fromJson("{\"rainColor\":\"bad\","
                + "\"foregroundColor\":\"#00445566\",\"cornerRadius\":100}");
        assertEquals(0xffffffff, invalid.rainColor);
        assertEquals(0xff445566, invalid.foregroundColor);
        assertEquals(24, invalid.cornerRadius);
    }
}
