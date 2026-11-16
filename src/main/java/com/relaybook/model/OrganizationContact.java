package com.relaybook.model;

import java.time.LocalDate;

/**
 * A company, service desk, club, or other organization entry.
 * Organization details (switchboards, published office numbers) change less
 * often, so records stay fresh for 365 days; at least one route is required.
 */
public class OrganizationContact extends Contact {
    /** Organization records are considered stale after 365 days. */
    public static final int STALE_AFTER_DAYS = 365;

    /** Constructed from a builder; the shared constructor lives on Contact. */
    public OrganizationContact(Builder builder) {
        super(builder);
    }

    @Override
    public ContactType getContactType() {
        return ContactType.ORGANIZATION;
    }

    @Override
    public int getStaleAfterDays() {
        return STALE_AFTER_DAYS;
    }

    @Override
    public String describe() {
        String org = getOrganization() == null ? getFullName() : getOrganization();
        return "Organization record: " + org
                + " is the organization behind this entry. Contact details for organizations such as "
                + org + " usually change less often, so they are rechecked every "
                + getStaleAfterDays() + " days.";
    }
}
