package com.relaybook.service;

/**
 * Immutable directory counters shared by the Swing stats panel and the
 * GET /api/stats endpoint. Computed from the same statuses the advisor
 * produces, so both views always agree.
 */
public final class DirectoryStats {
    private final int total;
    private final int stale;
    private final int singleRoute;
    private final int emergencyWithoutTwoRoutes;
    private final int ready;
    private final int withBackup;

    public DirectoryStats(int total, int stale, int singleRoute,
                          int emergencyWithoutTwoRoutes, int ready, int withBackup) {
        this.total = total;
        this.stale = stale;
        this.singleRoute = singleRoute;
        this.emergencyWithoutTwoRoutes = emergencyWithoutTwoRoutes;
        this.ready = ready;
        this.withBackup = withBackup;
    }

    public int getTotal() { return total; }
    public int getStale() { return stale; }
    public int getSingleRoute() { return singleRoute; }
    public int getEmergencyWithoutTwoRoutes() { return emergencyWithoutTwoRoutes; }
    public int getReady() { return ready; }
    public int getWithBackup() { return withBackup; }
}
