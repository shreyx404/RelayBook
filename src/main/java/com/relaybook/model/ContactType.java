package com.relaybook.model;

import java.util.Locale;

/** The kinds of records RelayBook can store. */
public enum ContactType {
    PERSON("Person"),
    ORGANIZATION("Organization"),
    EMERGENCY("Emergency contact");

    private final String label;

    ContactType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ContactType from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PERSON;
        }
        try {
            return ContactType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown contact type: " + value);
        }
    }
}
