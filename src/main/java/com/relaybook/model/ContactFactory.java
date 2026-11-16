package com.relaybook.model;

import com.relaybook.exception.ValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Creates the correct subtype from a form or a database row.
 * All creation goes through {@link Contact.Builder}, so there is exactly one
 * place that normalises fields and one place that validates them.
 *
 * <p>Forms may send per-route entries using indexed keys such as
 * {@code route.0.channel=PHONE}, {@code route.0.value=...},
 * {@code route.0.verified=YYYY-MM-DD}, {@code route.0.label=...}.
 * The legacy flat fields (phone, email, alternatePhone) are still accepted so
 * older clients and check-in pages keep working; explicit route entries win
 * when both are present for the same channel.</p>
 */
public final class ContactFactory {
    private ContactFactory() { }

    public static Contact fromForm(String id, Map<String, String> form) {
        ContactType type = ContactType.from(form.get("type"));
        Contact.Builder builder = Contact.builder(type)
                .id(id == null || id.trim().isEmpty() ? UUID.randomUUID().toString() : id)
                .fullName(value(form, "fullName"))
                .organization(value(form, "organization"))
                .relationship(value(form, "relationship"))
                .preferredChannel(Channel.from(form.get("preferredChannel")))
                .bestTime(value(form, "bestTime"))
                .timeWindow(TimeWindow.tryParse(value(form, "bestTime")).orElse(null))
                .timeZone(value(form, "timeZone"))
                .notes(value(form, "notes"))
                .lastVerified(parseDate(form.get("lastVerified"), "Last verified"))
                .shareConsent(Boolean.parseBoolean(value(form, "shareConsent")));

        // Explicit per-route entries first so they win over the legacy fields.
        for (Map.Entry<Integer, Map<String, String>> entry : indexedRoutes(form).entrySet()) {
            Map<String, String> route = entry.getValue();
            Channel channel = Channel.from(route.get("channel"));
            String routeValue = value(route, "value");
            if (channel == null || routeValue.trim().isEmpty()) {
                continue;
            }
            builder.route(new ContactMethod(channel, routeValue,
                    parseDate(route.get("verified"), "Route verified date"), value(route, "label")));
        }

        // Legacy flat fields: still supported for backward compatibility.
        addLegacyRoute(builder, Channel.PHONE, form.get("phone"));
        addLegacyRoute(builder, Channel.EMAIL, form.get("email"));
        addLegacyRoute(builder, Channel.ALTERNATE_PHONE, form.get("alternatePhone"));
        return builder.build();
    }

    private static void addLegacyRoute(Contact.Builder builder, Channel channel, String rawValue) {
        if (rawValue != null && !rawValue.trim().isEmpty()) {
            builder.route(new ContactMethod(channel, rawValue, null, null));
        }
    }

    /** Groups route.<index>.<field> keys so the UI can send routes in order. */
    private static Map<Integer, Map<String, String>> indexedRoutes(Map<String, String> form) {
        Map<Integer, Map<String, String>> indexed = new TreeMap<>();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("route.") || !key.contains(".")) {
                continue;
            }
            String[] parts = key.split("\\.", 3);
            if (parts.length != 3) {
                continue;
            }
            try {
                int index = Integer.parseInt(parts[1].trim());
                indexed.computeIfAbsent(index, ignored -> new TreeMap<>()).put(parts[2], entry.getValue());
            } catch (NumberFormatException ignored) {
                // Not a route index; ignore quietly so unknown fields never break a save.
            }
        }
        return indexed;
    }

    private static String value(Map<String, String> form, String key) {
        String value = form.get(key);
        return value == null ? "" : value;
    }

    private static LocalDate parseDate(String value, String fieldLabel) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new ValidationException(fieldLabel + " must use YYYY-MM-DD format.");
        }
    }
}
