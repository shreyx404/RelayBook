package com.relaybook.service;

import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactMethod;
import com.relaybook.model.TimeWindow;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared scoring algorithm for all routing policies.
 *
 * <p>The fixed skeleton (pick routes, choose the freshest fallback, explain it)
 * lives here; subclasses supply only the two point functions
 * {@link #coveragePoints} and {@link #freshnessPoints}. That keeps the point
 * differences between policies small, explicit, and easy to defend in a viva.</p>
 */
public abstract class AbstractRoutingPolicy implements RoutingPolicy {

    @Override
    public ReachabilityReport evaluate(Contact contact, LocalDate today) {
        List<ContactMethod> routes = contact.getRoutes();
        int routeCount = routes.size();

        long days = contact.getLastVerified() == null
                ? -1L : ChronoUnit.DAYS.between(contact.getLastVerified(), today);
        int staleAfterDays = contact.getStaleAfterDays();
        ContactMethod preferredRoute = preferredRoute(contact);
        boolean preferredFresh = isFresh(preferredRoute, contact, today);
        boolean stale = !routes.isEmpty() && !preferredFresh;

        int score = Math.min(MAX_SCORE, coveragePoints(contact, routeCount) + freshnessPoints(days));
        Channel suggested = chooseSuggestedRoute(contact, today);
        String routeValue = valueOf(routes, suggested);
        String rationale = explain(contact, routes, suggested, staleAfterDays, today);
        List<ReachabilityReport.RouteFreshness> freshness = routeFreshness(routes, today);
        String localTimeText = null;
        boolean windowKnown = false;
        boolean goodNow = false;
        if (contact.getTimeZone() != null) {
            try {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of(contact.getTimeZone()));
                localTimeText = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                        .format(DateTimeFormatter.ofPattern("HH:mm")) + " (" + contact.getTimeZone() + ")";
                TimeWindow window = contact.getTimeWindow();
                if (window != null) {
                    windowKnown = true;
                    goodNow = window.isOpenAt(now);
                }
            } catch (RuntimeException ignored) {
                // An invalid zone id is reported by the validator; the report simply omits local time.
            }
        }
        return new ReachabilityReport(score, statusFor(contact, routes, stale), suggested, routeValue,
                rationale, routeCount, days, freshness, localTimeText, windowKnown, goodNow);
    }

    /** Points for having routes; subclasses implement the differences. */
    protected abstract int coveragePoints(Contact contact, int routeCount);

    /** Points for how recently the record was checked; subclasses implement the differences. */
    protected abstract int freshnessPoints(long daysSinceVerified);

    private String statusFor(Contact contact, List<ContactMethod> routes, boolean stale) {
        if (routes.isEmpty()) {
            return "Add a route";
        }
        if (stale) {
            return "Needs verification";
        }
        if (routes.size() < contact.getMinimumRoutes()) {
            return "Add a backup route";
        }
        if (routes.size() == 1) {
            return "Single route";
        }
        return "Ready";
    }

    /** True when the route has a verification date inside the contact's recheck window. */
    private boolean isFresh(ContactMethod route, Contact contact, LocalDate today) {
        if (route == null || route.getLastVerified() == null) {
            return false;
        }
        long age = ChronoUnit.DAYS.between(route.getLastVerified(), today);
        return age >= 0 && age <= contact.getStaleAfterDays();
    }

    private ContactMethod preferredRoute(Contact contact) {
        Channel preferred = contact.getPreferredChannel();
        return preferred == null ? null : contact.getRoute(preferred).orElse(null);
    }

    /**
     * Preferred route when it is fresh; otherwise the alternative with the most
     * recent verification date of its own. Routes without a date lose to dated ones.
     */
    private Channel chooseSuggestedRoute(Contact contact, LocalDate today) {
        List<ContactMethod> routes = contact.getRoutes();
        if (routes.isEmpty()) {
            return null;
        }
        ContactMethod preferredRoute = preferredRoute(contact);
        if (isFresh(preferredRoute, contact, today)) {
            return preferredRoute.getChannel();
        }
        ContactMethod freshest = null;
        for (ContactMethod route : routes) {
            if (route == preferredRoute) {
                continue;
            }
            if (freshest == null || ageOf(route, today) < ageOf(freshest, today)) {
                freshest = route;
            }
        }
        if (freshest != null) {
            return freshest.getChannel();
        }
        return preferredRoute != null ? preferredRoute.getChannel() : routes.get(0).getChannel();
    }

    /** Days since this route was verified; undated routes sort last. */
    private long ageOf(ContactMethod route, LocalDate today) {
        return route.getLastVerified() == null
                ? Long.MAX_VALUE
                : Math.max(0L, ChronoUnit.DAYS.between(route.getLastVerified(), today));
    }

    private String valueOf(List<ContactMethod> routes, Channel channel) {
        for (ContactMethod route : routes) {
            if (route.getChannel() == channel) {
                return route.getValue();
            }
        }
        return "";
    }

    private List<ReachabilityReport.RouteFreshness> routeFreshness(List<ContactMethod> routes, LocalDate today) {
        List<ReachabilityReport.RouteFreshness> freshness = new ArrayList<>();
        for (ContactMethod route : routes) {
            Long days = route.getLastVerified() == null ? null : ageOf(route, today);
            freshness.add(new ReachabilityReport.RouteFreshness(
                    route.getChannel(), route.getValue(), days, route.getLabel()));
        }
        return freshness;
    }

    /** Builds the human explanation from each route's own verification date. */
    private String explain(Contact contact, List<ContactMethod> routes, Channel suggested,
                           int staleAfterDays, LocalDate today) {
        if (routes.isEmpty()) {
            return "No phone, email, or messaging route is saved yet. Add one before relying on this contact.";
        }
        String routeSummary = routeSummary(routes, today);
        ContactMethod preferredRoute = preferredRoute(contact);
        String suggestedName = suggested == null ? "route" : suggested.getLabel();

        if (preferredRoute == null) {
            return "The preferred route is missing. " + routeSummary
                    + ": try " + suggestedName + " first.";
        }
        long preferredAge = ageOf(preferredRoute, today);
        String preferredSentence = preferredRoute.getLastVerified() == null
                ? capitalise(preferredRoute.getChannel().getLabel()) + " has never been verified."
                : capitalise(preferredRoute.getChannel().getLabel()) + " was verified " + preferredAge + " days ago.";

        if (preferredAge <= staleAfterDays && preferredRoute.getLastVerified() != null) {
            if (routes.size() == 1) {
                return "Preferred route is saved and it is the only route. Add a backup route for better resilience.";
            }
            return "Preferred route is saved. " + capitalise(routeSummary) + ".";
        }
        if (suggested != null && suggested != preferredRoute.getChannel()) {
            return capitalise(routeSummary) + ": try " + suggestedName + " first. "
                    + preferredSentence + " Recheck it within " + staleAfterDays
                    + " days for " + contact.getTypeLabel().toLowerCase(Locale.ROOT) + " contacts.";
        }
        if (preferredRoute.getLastVerified() == null) {
            return "No verification date is recorded. Confirm this route before relying on it.";
        }
        return preferredSentence + " Verify the saved route before relying on it.";
    }

    /** "Phone was verified 5 days ago, email has never been verified". */
    private String routeSummary(List<ContactMethod> routes, LocalDate today) {
        StringBuilder summary = new StringBuilder();
        for (ContactMethod route : routes) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(route.getChannel().getLabel());
            if (route.getLastVerified() == null) {
                summary.append(" has never been verified");
            } else {
                summary.append(" was verified ").append(ageOf(route, today)).append(" days ago");
            }
        }
        return summary.toString();
    }

    private String capitalise(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
