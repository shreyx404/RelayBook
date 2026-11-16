package com.relaybook.model;

import java.time.LocalDate;

/**
 * A person entry. Person records go stale after 180 days without a check-in
 * and must keep at least one reachable route.
 */
public class PersonContact extends Contact {
    /** Person records are considered stale after 180 days. */
    public static final int STALE_AFTER_DAYS = 180;

    /** Constructed from a builder; the shared constructor lives on Contact. */
    public PersonContact(Builder builder) {
        super(builder);
    }

    @Override
    public ContactType getContactType() {
        return ContactType.PERSON;
    }

    @Override
    public int getStaleAfterDays() {
        return STALE_AFTER_DAYS;
    }

    @Override
    public String describe() {
        String context = getRelationship() == null ? "" : " Relationship: " + getRelationship() + ".";
        return "Person record for " + getFullName() + "." + context
                + " Check in every " + getStaleAfterDays() + " days.";
    }
}
