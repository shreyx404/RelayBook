package com.relaybook.model;

import com.relaybook.exception.ValidationException;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable value object describing exactly one communication route of a contact.
 *
 * <p>Each route owns its verification date and an optional label, so advice and
 * exports can reason about the freshness of the exact route instead of a
 * record-wide date. Instances never change after construction; helpers such as
 * {@link #withLastVerified(LocalDate)} return copies.</p>
 */
public final class ContactMethod {
    private final Channel channel;
    private final String value;
    private final LocalDate lastVerified;
    private final String label;

    public ContactMethod(Channel channel, String value, LocalDate lastVerified, String label) {
        if (channel == null) {
            throw new ValidationException("A route needs a channel (phone, email, WhatsApp...).");
        }
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException("A " + channel.getLabel() + " route needs a value.");
        }
        this.channel = channel;
        this.value = value.trim();
        this.lastVerified = lastVerified;
        this.label = label == null || label.trim().isEmpty() ? null : label.trim();
    }

    /** Convenience factory for a route without a verification date or label. */
    public static ContactMethod of(Channel channel, String value) {
        return new ContactMethod(channel, value, null, null);
    }

    public Channel getChannel() { return channel; }
    public String getValue() { return value; }
    /** Null when this route has never been checked. */
    public LocalDate getLastVerified() { return lastVerified; }
    /** Optional user label such as "Mobile" or "Office landline"; null when absent. */
    public String getLabel() { return label; }

    /** Copy of this route with a new verification date; values are unchanged. */
    public ContactMethod withLastVerified(LocalDate verified) {
        return new ContactMethod(channel, value, verified, label);
    }

    /** Copy of this route checked today. */
    public ContactMethod verifiedToday() {
        return withLastVerified(LocalDate.now());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContactMethod)) {
            return false;
        }
        ContactMethod that = (ContactMethod) other;
        return channel == that.channel
                && value.equals(that.value)
                && Objects.equals(lastVerified, that.lastVerified)
                && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, value, lastVerified, label);
    }

    @Override
    public String toString() {
        return channel.getLabel() + " " + value;
    }
}
