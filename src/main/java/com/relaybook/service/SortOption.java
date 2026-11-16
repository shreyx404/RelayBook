package com.relaybook.service;

import com.relaybook.model.Contact;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Directory sort options. NAME/LAST_VERIFIED/TYPE use the comparator constants
 * on {@link Contact}; SCORE needs the advisor, so the comparator is produced
 * here where the advisor is available.
 */
public enum SortOption {
    NAME,
    SCORE,
    LAST_VERIFIED,
    TYPE;

    /** Comparator for this option; score sorts best-first, the rest ascending. */
    public Comparator<Contact> comparator(ReachabilityAdvisor advisor) {
        switch (this) {
            case SCORE:
                return Comparator.comparingInt((Contact contact) -> advisor.analyze(contact).getScore()).reversed();
            case LAST_VERIFIED:
                return Contact.BY_LAST_VERIFIED;
            case TYPE:
                return Contact.BY_TYPE;
            case NAME:
            default:
                return Contact.BY_NAME;
        }
    }

    /** Tolerant parsing for the ?sort= API parameter; unknown values fall back to NAME. */
    public static SortOption from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return NAME;
        }
        try {
            return SortOption.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return NAME;
        }
    }

    /** Sorted copy of the list. */
    public List<Contact> apply(List<Contact> contacts, ReachabilityAdvisor advisor) {
        return contacts.stream().sorted(comparator(advisor)).collect(Collectors.toList());
    }
}
