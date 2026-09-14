package app.rainline;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CoordinateInputTest {
    @Test public void acceptsDecimalPointsAndUnambiguousDecimalCommas() {
        double[] expected = {59.9139, 10.7522};
        assertArrayEquals(expected, ForecastWindow.parseCoordinates("59.9139, 10.7522"), 0);
        assertArrayEquals(expected, ForecastWindow.parseCoordinates("59,9139; 10,7522"), 0);
        assertArrayEquals(expected, ForecastWindow.parseCoordinates(" 59.9139 ; 10.7522 "), 0);
        assertArrayEquals(new double[]{-12.5, -45.25}, ForecastWindow.parseCoordinates("-12,5; -45,25"), 0);
    }
    @Test public void placeNamesAndAmbiguousInputAreNotSilentlyReinterpreted() {
        assertNull(ForecastWindow.parseCoordinates("Oslo, Norway"));
        assertNull(ForecastWindow.parseCoordinates("Stockholm"));
        assertNull(ForecastWindow.parseCoordinates("59,9139, 10,7522"));
        assertNull(ForecastWindow.parseCoordinates("59,9139;"));
    }
    @Test public void rejectsNonFiniteAndOutOfRangeCoordinates() {
        for (String query : new String[]{"91; 10", "60; -181", "NaN, 10", "60, Infinity"}) {
            try { ForecastWindow.parseCoordinates(query); fail(query); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
