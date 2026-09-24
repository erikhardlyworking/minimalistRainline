package app.rainline;

/** Generous local prefilter for Norway, Sweden, Denmark and Finland, not a radar map.
 * Includes their mainland/coastal islands; MET remains authoritative near the edges.
 * Iceland, the Faroes, Greenland, Jan Mayen and Svalbard are outside this product.
 */
final class NowcastRegion {
    private NowcastRegion() { }
    static boolean mayHaveCoverage(double latitude, double longitude) {
        return ForecastWindow.isCoordinate(latitude, longitude)
                && latitude >= 54 && latitude <= 72 && longitude >= 4 && longitude <= 32;
    }
    static boolean outside(WidgetSettings settings) {
        return settings.hasLocation() && !mayHaveCoverage(settings.latitude, settings.longitude);
    }
    static void requirePossibleCoverage(double latitude, double longitude) throws UpdateIssue.Failure {
        if (!mayHaveCoverage(latitude, longitude))
            throw UpdateIssue.OUTSIDE_COVERAGE.failure("This location is outside the forecast area.");
    }
}
