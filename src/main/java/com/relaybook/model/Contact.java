package com.relaybook.model;

import com.relaybook.exception.ValidationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Encapsulated base class for every address-book entry.
 * The immutable ID and final getter are intentional examples of Java's final keyword.
 *
 * <p>Subtype-specific behaviour lives here as overridable methods
 * ({@link #getStaleAfterDays()}, {@link #getMinimumRoutes()}, {@link #describe()}),
 * so the validator, advisor, and UI can treat every record polymorphically
 * as {@code Contact} while each subtype supplies its own rules.</p>
 *
 * <p>A contact owns an immutable collection of {@link ContactMethod} routes;
 * each route carries its own verification date. The legacy convenience getters
 * ({@link #getPhone()}, {@link #getEmail()}, {@link #getAlternatePhone()})
 * read the matching route, and {@link #getLastVerified()} reports the latest
 * route verification date, so older code keeps working unchanged.</p>
 */
public abstract class Contact implements Comparable<Contact> {
    /** Default staleness threshold applied by ordinary person records. */
    public static final int DEFAULT_STALE_AFTER_DAYS = 180;
    public static final int MAX_NOTE_LENGTH = 1000;
    private static final AtomicLong CREATED_OBJECTS = new AtomicLong();

    private final String id;
    private final String fullName;
    private final String organization;
    private final String relationship;
    private final Map<Channel, ContactMethod> routes;
    private final Channel preferredChannel;
    private final String bestTime;
    private final TimeWindow timeWindow;
    private final String timeZone;
    private final String notes;
    private final boolean shareConsent;

    /**
     * Subtypes are constructed from a {@link Builder}; this keeps one place
     * where fields are normalised instead of repeating long parameter lists
     * in every subclass constructor.
     */
    protected Contact(Builder builder) {
        // `this` disambiguates each field from its constructor parameter.
        this.id = requireText(builder.id, "Contact ID");
        this.fullName = requireText(builder.fullName, "Name");
        this.organization = clean(builder.organization);
        this.relationship = clean(builder.relationship);

        Map<Channel, ContactMethod> collected = new EnumMap<>(Channel.class);
        for (ContactMethod method : builder.routes) {
            if (method == null || method.getValue().trim().isEmpty()) {
                continue;
            }
            // A route without its own date inherits the record-level default date.
            LocalDate verified = method.getLastVerified() != null
                    ? method.getLastVerified() : builder.lastVerified;
            collected.putIfAbsent(method.getChannel(),
                    new ContactMethod(method.getChannel(), method.getValue(), verified, method.getLabel()));
        }
        this.routes = Collections.unmodifiableMap(new EnumMap<>(collected));
        this.preferredChannel = resolvePreferred(builder.preferredChannel, collected);
        this.bestTime = clean(builder.bestTime);
        this.timeWindow = builder.timeWindow;
        this.timeZone = clean(builder.timeZone);
        this.notes = clean(builder.notes);
        this.shareConsent = builder.shareConsent;
        CREATED_OBJECTS.incrementAndGet();
    }

    private static Channel resolvePreferred(Channel preferred, Map<Channel, ContactMethod> routes) {
        if (preferred != null && routes.containsKey(preferred)) {
            return preferred;
        }
        return routes.isEmpty() ? null : routes.values().iterator().next().getChannel();
    }

    private static String requireText(String value, String field) {
        String result = clean(value);
        if (result == null) {
            throw new ValidationException(field + " is required.");
        }
        return result;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public final String getId() { return id; }
    public final String getFullName() { return fullName; }
    public final String getOrganization() { return organization; }
    public final String getRelationship() { return relationship; }

    /** Immutable, channel-ordered view of the saved routes. */
    public final List<ContactMethod> getRoutes() {
        return Collections.unmodifiableList(new ArrayList<>(routes.values()));
    }

    /** The saved route for a channel, if any. */
    public final java.util.Optional<ContactMethod> getRoute(Channel channel) {
        return java.util.Optional.ofNullable(routes.get(channel));
    }

    public final Channel getPreferredChannel() { return preferredChannel; }
    public final String getBestTime() { return bestTime; }
    /** Structured reachability window parsed from the best-time text; null when the text is not parseable. */
    public final TimeWindow getTimeWindow() { return timeWindow; }
    public final String getTimeZone() { return timeZone; }
    public final String getNotes() { return notes; }
    public final boolean hasShareConsent() { return shareConsent; }

    /** Convenience getter backed by the PHONE route, kept for existing callers. */
    public final String getPhone() {
        return routeValue(Channel.PHONE);
    }

    /** Convenience getter backed by the EMAIL route, kept for existing callers. */
    public final String getEmail() {
        return routeValue(Channel.EMAIL);
    }

    /** Convenience getter backed by the ALTERNATE_PHONE route, kept for existing callers. */
    public final String getAlternatePhone() {
        return routeValue(Channel.ALTERNATE_PHONE);
    }

    private String routeValue(Channel channel) {
        ContactMethod method = routes.get(channel);
        return method == null ? null : method.getValue();
    }

    /**
     * Record-level verification date: the latest verification among the saved
     * routes, or null when no route has ever been checked. This keeps the old
     * single-date view meaningful while routes stay individually dated.
     */
    public final LocalDate getLastVerified() {
        return routes.values().stream()
                .map(ContactMethod::getLastVerified)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    /** Overridden by subclasses; called polymorphically by the UI and API. */
    public abstract ContactType getContactType();

    public final String getTypeLabel() {
        return getContactType().getLabel();
    }

    /**
     * Days after the last check-in when this record counts as stale.
     * Overridden per subtype (person 180, organization 365, emergency 90);
     * the advisor and UI read this value instead of hard-coding numbers.
     */
    public int getStaleAfterDays() {
        return DEFAULT_STALE_AFTER_DAYS;
    }

    /**
     * Minimum number of distinct routes this kind of contact must keep.
     * The validator produces a type-specific error when the record has fewer.
     */
    public int getMinimumRoutes() {
        return 1;
    }

    /** One-line human description of the record; each subtype adds its own context. */
    public String describe() {
        return getTypeLabel() + " record for " + getFullName() + ".";
    }

    /** Fluent copy constructor: change fields on a copy and build again. */
    public Builder toBuilder() {
        return Builder.fromContact(this);
    }

    /** Natural order: by full name, case-insensitive. */
    @Override
    public int compareTo(Contact other) {
        return getFullName().compareToIgnoreCase(other.getFullName());
    }

    /** Comparator constants reused by the service sort options and the UIs. */
    public static final java.util.Comparator<Contact> BY_NAME =
            java.util.Comparator.comparing(Contact::getFullName, String.CASE_INSENSITIVE_ORDER);

    /** Least recently verified last: records without any date sort last. */
    public static final java.util.Comparator<Contact> BY_LAST_VERIFIED =
            java.util.Comparator.comparing(Contact::getLastVerified,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));

    public static final java.util.Comparator<Contact> BY_TYPE =
            java.util.Comparator.comparing(Contact::getTypeLabel, String.CASE_INSENSITIVE_ORDER);

    /** Static state demonstrates a class-level member (not a production metric). */
    public static long getCreatedObjectCount() {
        return CREATED_OBJECTS.get();
    }

    /** Creates a builder for the requested subtype; {@link Builder#build()} validates. */
    public static Builder builder(ContactType type) {
        return new Builder().type(type);
    }

    /** Creates the concrete subtype described by the builder. */
    static Contact newOfType(ContactType type, Builder builder) {
        switch (type) {
            case ORGANIZATION:
                return new OrganizationContact(builder);
            case EMERGENCY:
                return new EmergencyContact(builder);
            case PERSON:
            default:
                return new PersonContact(builder);
        }
    }

    @Override
    public String toString() {
        return getTypeLabel() + ": " + fullName;
    }

    /**
     * Fluent builder for contacts of any subtype. {@link #build()} normalises
     * the fields and runs {@link ContactValidator}, so an invalid contact can
     * never leave the builder.
     */
    public static final class Builder {
        private ContactType type = ContactType.PERSON;
        private String id = UUID.randomUUID().toString();
        private String fullName;
        private String organization;
        private String relationship;
        private final List<ContactMethod> routes = new ArrayList<>();
        private Channel preferredChannel;
        private String bestTime;
        private TimeWindow timeWindow;
        private String timeZone;
        private String notes;
        private LocalDate lastVerified;
        private boolean shareConsent;

        public Builder type(ContactType type) {
            this.type = type == null ? ContactType.PERSON : type;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder organization(String organization) {
            this.organization = organization;
            return this;
        }

        public Builder relationship(String relationship) {
            this.relationship = relationship;
            return this;
        }

        /** Adds one route; a route without its own date inherits the record-level date. */
        public Builder route(ContactMethod method) {
            if (method != null) {
                routes.add(method);
            }
            return this;
        }

        public Builder route(Channel channel, String value) {
            return route(new ContactMethod(channel, value, null, null));
        }

        public Builder route(Channel channel, String value, LocalDate verified) {
            return route(new ContactMethod(channel, value, verified, null));
        }

        public Builder route(Channel channel, String value, LocalDate verified, String label) {
            return route(new ContactMethod(channel, value, verified, label));
        }

        /** Replaces the whole route list (used when copying or loading from storage). */
        public Builder routes(List<ContactMethod> methods) {
            routes.clear();
            if (methods != null) {
                routes.addAll(methods);
            }
            return this;
        }

        public Builder preferredChannel(Channel preferredChannel) {
            this.preferredChannel = preferredChannel;
            return this;
        }

        public Builder bestTime(String bestTime) {
            this.bestTime = bestTime;
            return this;
        }

        /** Structured window used by the "is now a good time?" feature. */
        public Builder timeWindow(TimeWindow timeWindow) {
            this.timeWindow = timeWindow;
            return this;
        }

        public Builder timeZone(String timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        /** Default verification date applied to routes that have none of their own. */
        public Builder lastVerified(LocalDate lastVerified) {
            this.lastVerified = lastVerified;
            return this;
        }

        public Builder shareConsent(boolean shareConsent) {
            this.shareConsent = shareConsent;
            return this;
        }

        /** Sets today's date on every route (the "mark checked today" action). */
        public Builder verifyAllRoutes(LocalDate date) {
            List<ContactMethod> verified = new ArrayList<>();
            for (ContactMethod method : routes) {
                verified.add(method.withLastVerified(date));
            }
            routes.clear();
            routes.addAll(verified);
            return this;
        }

        /** Sets a verification date on one channel's route only. */
        public Builder verifyRoute(Channel channel, LocalDate date) {
            List<ContactMethod> updated = new ArrayList<>();
            for (ContactMethod method : routes) {
                updated.add(method.getChannel() == channel ? method.withLastVerified(date) : method);
            }
            routes.clear();
            routes.addAll(updated);
            return this;
        }

        /** Validates and creates an immutable contact of the configured subtype. */
        public Contact build() {
            Contact contact = Contact.newOfType(type, this);
            ContactValidator.validate(contact);
            return contact;
        }

        private static Builder fromContact(Contact contact) {
            Builder builder = new Builder()
                    .type(contact.getContactType())
                    .id(contact.getId())
                    .fullName(contact.getFullName())
                    .organization(contact.getOrganization())
                    .relationship(contact.getRelationship())
                    .routes(contact.getRoutes())
                    .preferredChannel(contact.getPreferredChannel())
                    .bestTime(contact.getBestTime())
                    .timeWindow(contact.getTimeWindow())
                    .timeZone(contact.getTimeZone())
                    .notes(contact.getNotes())
                    .lastVerified(contact.getLastVerified())
                    .shareConsent(contact.hasShareConsent());
            return builder;
        }
    }
}
