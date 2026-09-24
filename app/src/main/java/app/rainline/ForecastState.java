package app.rainline;

/** Cached samples take priority over loading and errors; missing data is never dry weather. */
public enum ForecastState {
    DATA, LOADING, UNAVAILABLE;

    public static ForecastState of(WidgetSettings settings, Forecast forecast, long now,
                                   UpdateIssue issue, boolean pending) {
        if (NowcastRegion.outside(settings)) return UNAVAILABLE;
        if (settings.hasLocation() && !settings.locationExpired(now)
                && ForecastWindow.hasUpcomingData(forecast, now)) return DATA;
        if (pending) return LOADING;
        if (!settings.hasLocation() || settings.locationExpired(now)
                || (issue != UpdateIssue.NONE && issue != UpdateIssue.API_DEPRECATED)
                || (forecast != null && !forecast.hasRadar())) return UNAVAILABLE;
        return LOADING;
    }
}
