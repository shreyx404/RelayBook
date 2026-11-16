package com.relaybook.model;

import com.relaybook.exception.ValidationException;

import java.util.Locale;

/**
 * Contact route types supported by the reachability advisor.
 * The enum is open for growth: WHATSAPP and TELEGRAM show that a new channel
 * becomes available everywhere (forms, advisor, storage, vCard) without
 * changing the code that iterates over {@code Channel.values()}.
 */
public enum Channel {
    PHONE("Phone"),
    EMAIL("Email"),
    ALTERNATE_PHONE("Backup phone"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram");

    private final String label;

    Channel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** True for channels that are dialled like a phone number. */
    public boolean isDialable() {
        return this == PHONE || this == ALTERNATE_PHONE || this == WHATSAPP;
    }

    public static Channel from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Channel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unknown contact channel: " + value);
        }
    }
}
