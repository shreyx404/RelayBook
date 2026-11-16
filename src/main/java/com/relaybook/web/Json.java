package com.relaybook.web;

import com.relaybook.model.Contact;
import com.relaybook.model.ContactMethod;
import com.relaybook.service.ReachabilityReport;

import java.util.List;

/** Minimal JSON encoder for this small, fixed API shape; user text is always escaped. */
public final class Json {
    private Json() { }

    public static String contacts(List<Contact> contacts, com.relaybook.service.ReachabilityAdvisor advisor) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < contacts.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(contact(contacts.get(i), advisor.analyze(contacts.get(i))));
        }
        return json.append(']').toString();
    }

    public static String contact(Contact contact, ReachabilityReport report) {
        StringBuilder json = new StringBuilder("{");
        field(json, "id", contact.getId()).append(',');
        field(json, "type", contact.getContactType().name()).append(',');
        field(json, "typeLabel", contact.getTypeLabel()).append(',');
        field(json, "fullName", contact.getFullName()).append(',');
        field(json, "organization", contact.getOrganization()).append(',');
        field(json, "relationship", contact.getRelationship()).append(',');
        // Legacy flat fields (derived from the matching routes) keep old clients working.
        field(json, "phone", contact.getPhone()).append(',');
        field(json, "email", contact.getEmail()).append(',');
        field(json, "alternatePhone", contact.getAlternatePhone()).append(',');
        field(json, "preferredChannel", contact.getPreferredChannel() == null ? null : contact.getPreferredChannel().name()).append(',');
        json.append("\"routes\":[");
        List<ContactMethod> routes = contact.getRoutes();
        for (int i = 0; i < routes.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(route(routes.get(i)));
        }
        json.append("],");
        field(json, "bestTime", contact.getBestTime()).append(',');
        field(json, "reachWindow", contact.getTimeWindow() == null ? null : contact.getTimeWindow().toString()).append(',');
        field(json, "timeZone", contact.getTimeZone()).append(',');
        field(json, "notes", contact.getNotes()).append(',');
        field(json, "lastVerified", contact.getLastVerified() == null ? null : contact.getLastVerified().toString()).append(',');
        json.append("\"shareConsent\":").append(contact.hasShareConsent()).append(',');
        json.append("\"staleAfterDays\":").append(contact.getStaleAfterDays()).append(',');
        field(json, "describe", contact.describe()).append(',');
        json.append("\"score\":").append(report.getScore()).append(',');
        field(json, "status", report.getStatus()).append(',');
        field(json, "suggestedChannel", report.getSuggestedChannel() == null ? null : report.getSuggestedChannel().getLabel()).append(',');
        field(json, "suggestedValue", report.getSuggestedValue()).append(',');
        field(json, "rationale", report.getRationale()).append(',');
        json.append("\"availableRoutes\":").append(report.getAvailableRoutes()).append(',');
        json.append("\"daysSinceVerified\":").append(report.getDaysSinceVerified()).append(',');
        field(json, "localTime", report.getLocalTimeText()).append(',');
        json.append("\"windowKnown\":").append(report.isWindowKnown()).append(',');
        json.append("\"goodTimeNow\":").append(report.isGoodTimeToReachNow());
        return json.append('}').toString();
    }

    private static String route(ContactMethod route) {
        StringBuilder json = new StringBuilder("{");
        field(json, "channel", route.getChannel().name()).append(',');
        field(json, "channelLabel", route.getChannel().getLabel()).append(',');
        field(json, "value", route.getValue()).append(',');
        field(json, "lastVerified", route.getLastVerified() == null ? null : route.getLastVerified().toString()).append(',');
        field(json, "label", route.getLabel());
        return json.append('}').toString();
    }

    /** Serialises a list of strings as a JSON array (used by the import summary). */
    public static String stringArray(java.util.List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(quote(values.get(i)));
        }
        return json.append(']').toString();
    }

    public static String stats(com.relaybook.service.DirectoryStats stats) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"total\":").append(stats.getTotal()).append(',');
        json.append("\"stale\":").append(stats.getStale()).append(',');
        json.append("\"singleRoute\":").append(stats.getSingleRoute()).append(',');
        json.append("\"emergencyWithoutTwoRoutes\":").append(stats.getEmergencyWithoutTwoRoutes()).append(',');
        json.append("\"ready\":").append(stats.getReady()).append(',');
        json.append("\"withBackup\":").append(stats.getWithBackup());
        return json.append('}').toString();
    }

    public static String error(String message) {
        return "{\"error\":" + quote(message) + "}";
    }

    private static StringBuilder field(StringBuilder target, String key, String value) {
        return target.append(quote(key)).append(':').append(quote(value));
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
            }
        }
        return escaped.append('"').toString();
    }
}
