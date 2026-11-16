package com.relaybook.service;

import com.relaybook.model.Channel;

import java.util.Collections;
import java.util.List;

/** Result of the rule-based route/freshness assessment; the score is not a success probability. */
public final class ReachabilityReport {
    private final int score;
    private final String status;
    private final Channel suggestedChannel;
    private final String suggestedValue;
    private final String rationale;
    private final int availableRoutes;
    private final long daysSinceVerified;
    private final List<RouteFreshness> routeFreshness;
    private final String localTimeText;
    private final boolean windowKnown;
    private final boolean goodTimeToReachNow;

    public ReachabilityReport(int score, String status, Channel suggestedChannel,
                              String suggestedValue, String rationale,
                              int availableRoutes, long daysSinceVerified) {
        this(score, status, suggestedChannel, suggestedValue, rationale,
                availableRoutes, daysSinceVerified, Collections.emptyList(), null, false, false);
    }

    public ReachabilityReport(int score, String status, Channel suggestedChannel,
                              String suggestedValue, String rationale,
                              int availableRoutes, long daysSinceVerified,
                              List<RouteFreshness> routeFreshness) {
        this(score, status, suggestedChannel, suggestedValue, rationale,
                availableRoutes, daysSinceVerified, routeFreshness, null, false, false);
    }

    public ReachabilityReport(int score, String status, Channel suggestedChannel,
                              String suggestedValue, String rationale,
                              int availableRoutes, long daysSinceVerified,
                              List<RouteFreshness> routeFreshness,
                              String localTimeText, boolean windowKnown, boolean goodTimeToReachNow) {
        this.score = score;
        this.status = status;
        this.suggestedChannel = suggestedChannel;
        this.suggestedValue = suggestedValue;
        this.rationale = rationale;
        this.availableRoutes = availableRoutes;
        this.daysSinceVerified = daysSinceVerified;
        this.routeFreshness = List.copyOf(routeFreshness);
        this.localTimeText = localTimeText;
        this.windowKnown = windowKnown;
        this.goodTimeToReachNow = goodTimeToReachNow;
    }

    public int getScore() { return score; }
    public String getStatus() { return status; }
    public Channel getSuggestedChannel() { return suggestedChannel; }
    public String getSuggestedValue() { return suggestedValue; }
    public String getRationale() { return rationale; }
    public int getAvailableRoutes() { return availableRoutes; }
    /** -1 means no verification date was recorded. */
    public long getDaysSinceVerified() { return daysSinceVerified; }
    /** Freshness of each individual route, in channel order. */
    public List<RouteFreshness> getRouteFreshness() { return routeFreshness; }
    /** Contact's current local time such as "14:35 (Asia/Kolkata)"; null when no valid zone. */
    public String getLocalTimeText() { return localTimeText; }
    /** True when a structured time window could be evaluated. */
    public boolean isWindowKnown() { return windowKnown; }
    /** True when the contact's local time is inside their saved window. */
    public boolean isGoodTimeToReachNow() { return goodTimeToReachNow; }

    /** Per-route verification age; {@code daysSinceVerified == null} means never verified. */
    public static final class RouteFreshness {
        private final Channel channel;
        private final String value;
        private final Long daysSinceVerified;
        private final String label;

        public RouteFreshness(Channel channel, String value, Long daysSinceVerified, String label) {
            this.channel = channel;
            this.value = value;
            this.daysSinceVerified = daysSinceVerified;
            this.label = label;
        }

        public Channel getChannel() { return channel; }
        public String getValue() { return value; }
        public Long getDaysSinceVerified() { return daysSinceVerified; }
        public String getLabel() { return label; }
    }
}
