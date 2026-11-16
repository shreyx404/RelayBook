package com.relaybook.model;

import com.relaybook.exception.ValidationException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

/** Small domain validation layer shared by the Swing form and web API. */
public final class ContactValidator {
    private ContactValidator() { }

    public static void validate(Contact contact) {
        if (contact == null) {
            throw new ValidationException("Contact is required.");
        }
        if (isBlank(contact.getFullName())) {
            throw new ValidationException("Name is required.");
        }
        int routeCount = contact.getRoutes().size();
        if (routeCount < contact.getMinimumRoutes()) {
            // Polymorphic message: the requirement comes from the subtype's own override.
            throw new ValidationException(minimumRoutesMessage(contact));
        }
        for (ContactMethod method : contact.getRoutes()) {
            if (method.getChannel() == Channel.EMAIL && !isEmailShape(method.getValue())) {
                throw new ValidationException("Enter an email address in a valid format: " + method.getValue());
            }
        }
        if (contact.getNotes() != null && contact.getNotes().length() > Contact.MAX_NOTE_LENGTH) {
            throw new ValidationException("Notes must be no longer than " + Contact.MAX_NOTE_LENGTH + " characters.");
        }
        LocalDate verified = contact.getLastVerified();
        if (verified != null && verified.isAfter(LocalDate.now())) {
            throw new ValidationException("Last verified date cannot be in the future.");
        }
        if (!isBlank(contact.getTimeZone())) {
            try {
                ZoneId.of(contact.getTimeZone().trim());
            } catch (DateTimeException ex) {
                throw new ValidationException("Time zone is not a valid IANA zone id (example: Asia/Kolkata).");
            }
        }
    }

    /**
     * Type-specific wording produced from the subtype's own minimum;
     * the base message covers the common one-route case.
     */
    private static String minimumRoutesMessage(Contact contact) {
        int minimum = contact.getMinimumRoutes();
        if (contact.getContactType() == ContactType.EMERGENCY) {
            return "Emergency contacts need at least two routes (for example a phone and an email) "
                    + "so a fallback exists when the first one fails.";
        }
        return minimum > 1
                ? contact.getTypeLabel() + " contacts need at least " + minimum + " routes."
                : "Add at least one phone number or email route.";
    }

    private static boolean isEmailShape(String value) {
        return value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
