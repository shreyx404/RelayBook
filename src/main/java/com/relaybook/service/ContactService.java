package com.relaybook.service;

import com.relaybook.exception.ContactNotFoundException;
import com.relaybook.exception.ValidationException;
import com.relaybook.exception.DuplicateContactException;
import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import com.relaybook.model.ContactValidator;
import com.relaybook.repository.ContactRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Application use-cases, shared by Swing and the browser UI.
 *
 * <p>Every successful change notifies the registered
 * {@link ContactChangeListener} objects (Observer pattern). The listener list
 * is a {@link CopyOnWriteArrayList}, so web-server threads can notify while
 * the Swing thread adds or removes listeners without extra locking.</p>
 */
public final class ContactService {
    private final ContactRepository repository;
    private final ReachabilityAdvisor advisor;
    private final List<ContactChangeListener> listeners = new CopyOnWriteArrayList<>();

    public ContactService(ContactRepository repository) {
        this(repository, new ReachabilityAdvisor());
    }

    public ContactService(ContactRepository repository, ReachabilityAdvisor advisor) {
        this.repository = repository;
        this.advisor = advisor;
    }

    public void addListener(ContactChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(ContactChangeListener listener) {
        listeners.remove(listener);
    }

    /** Read-only view for components (e.g. the reminder scheduler) that broadcast to all listeners. */
    public List<ContactChangeListener> getListeners() {
        return java.util.Collections.unmodifiableList(listeners);
    }

    public List<Contact> listContacts() {
        return repository.findAll();
    }

    public Optional<Contact> findContact(String id) {
        return repository.findById(id);
    }

    public Contact requireContact(String id) {
        return findContact(id).orElseThrow(() -> new ContactNotFoundException(id));
    }

    public Contact create(Contact contact) {
        ContactValidator.validate(contact);
        ensureNew(contact);
        findDuplicate(contact).ifPresent(existing -> {
            throw new DuplicateContactException("A contact named " + existing.getFullName()
                    + " already shares this name and a route value. Edit that record instead of creating a copy.");
        });
        repository.insert(contact);
        notifyCreated(contact);
        return contact;
    }

    public Contact update(Contact contact) {
        ContactValidator.validate(contact);
        requireContact(contact.getId());
        repository.update(contact);
        notifyUpdated(contact);
        return contact;
    }

    public void delete(String id) {
        Contact removed = requireContact(id);
        if (!repository.delete(id)) {
            throw new ContactNotFoundException(id);
        }
        notifyDeleted(removed);
    }

    /**
     * "Mark checked today": copies the contact with {@link Contact#toBuilder()},
     * sets every route's verification date to today, and saves it.
     */
    public Contact markVerified(String id) {
        return markVerified(id, LocalDate.now());
    }

    public Contact markVerified(String id, LocalDate date) {
        Contact contact = requireContact(id);
        Contact verified = contact.toBuilder().verifyAllRoutes(date).build();
        repository.update(verified);
        notifyUpdated(verified);
        return verified;
    }

    /** Per-route variant: verifies only the given channel's route. */
    public Contact markVerified(String id, Channel channel) {
        return markVerified(id, channel, LocalDate.now());
    }

    public Contact markVerified(String id, Channel channel, LocalDate date) {
        if (channel == null) {
            throw new ValidationException("A channel is required to verify one route.");
        }
        Contact contact = requireContact(id);
        if (contact.getRoute(channel).isEmpty()) {
            throw new ValidationException("No " + channel.getLabel() + " route is saved for this contact.");
        }
        Contact verified = contact.toBuilder().verifyRoute(channel, date).build();
        repository.update(verified);
        notifyUpdated(verified);
        return verified;
    }

    /**
     * Stream-based search shared by the Swing UI and the API: free-text across
     * name/organization/context/route values, optional type filter, and an
     * only-stale switch that uses each contact's own threshold.
     */
    public List<Contact> search(String query, ContactType typeFilter, boolean onlyStale) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        return repository.findAll().stream()
                .filter(contact -> typeFilter == null || contact.getContactType() == typeFilter)
                .filter(contact -> matches(contact, needle))
                .filter(contact -> !onlyStale || isStale(contact, today))
                .sorted(NAME_ORDER)
                .collect(Collectors.toList());
    }

    private boolean matches(Contact contact, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        return contact.getFullName().toLowerCase(Locale.ROOT).contains(needle)
                || contains(contact.getOrganization(), needle)
                || contains(contact.getRelationship(), needle)
                || contact.getRoutes().stream()
                        .anyMatch(route -> route.getValue().toLowerCase(Locale.ROOT).contains(needle));
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean isStale(Contact contact, LocalDate today) {
        return advisor.analyze(contact, today).getStatus().equals("Needs verification");
    }

    /** Directory counters for the Swing stats panel and GET /api/stats. */
    public DirectoryStats stats() {
        int total = 0;
        int stale = 0;
        int singleRoute = 0;
        int emergencyWithoutTwoRoutes = 0;
        int ready = 0;
        int withBackup = 0;
        for (Contact contact : repository.findAll()) {
            total++;
            if (contact.getRoutes().size() >= 2) {
                withBackup++;
            }
            if (contact.getContactType() == com.relaybook.model.ContactType.EMERGENCY
                    && contact.getRoutes().size() < contact.getMinimumRoutes()) {
                emergencyWithoutTwoRoutes++;
            }
            String status = advisor.analyze(contact).getStatus();
            if ("Needs verification".equals(status)) {
                stale++;
            } else if ("Single route".equals(status) || "Add a backup route".equals(status)) {
                singleRoute++;
            } else if ("Ready".equals(status)) {
                ready++;
            }
        }
        return new DirectoryStats(total, stale, singleRoute, emergencyWithoutTwoRoutes, ready, withBackup);
    }

    private void ensureNew(Contact contact) {
        if (repository.findById(contact.getId()).isPresent()) {
            throw new DuplicateContactException("A contact with this ID already exists.");
        }
    }

    /**
     * Duplicate rule: same name (case-insensitive) plus at least one identical,
     * non-blank route value. Used on create and by the vCard importer.
     */
    public Optional<Contact> findDuplicate(Contact candidate) {
        Set<String> candidateRoutes = routeValues(candidate);
        for (Contact existing : repository.findAll()) {
            if (existing.getId().equals(candidate.getId())) {
                continue;
            }
            if (!existing.getFullName().equalsIgnoreCase(candidate.getFullName().trim())) {
                continue;
            }
            for (String value : routeValues(existing)) {
                if (candidateRoutes.contains(value)) {
                    return Optional.of(existing);
                }
            }
        }
        return Optional.empty();
    }

    private Set<String> routeValues(Contact contact) {
        Set<String> values = new LinkedHashSet<>();
        for (com.relaybook.model.ContactMethod route : contact.getRoutes()) {
            values.add(route.getValue().toLowerCase(Locale.ROOT));
        }
        return values;
    }

    private void notifyCreated(Contact contact) {
        for (ContactChangeListener listener : listeners) {
            listener.onCreated(contact);
        }
    }

    private void notifyUpdated(Contact contact) {
        for (ContactChangeListener listener : listeners) {
            listener.onUpdated(contact);
        }
    }

    private void notifyDeleted(Contact contact) {
        for (ContactChangeListener listener : listeners) {
            listener.onDeleted(contact);
        }
    }

    public ReachabilityReport analyze(Contact contact) {
        return advisor.analyze(contact);
    }

    public ReachabilityAdvisor getAdvisor() {
        return advisor;
    }

    /** Case-insensitive name order, also the base order of the search results. */
    static final Comparator<Contact> NAME_ORDER = Comparator.comparing(
            contact -> contact.getFullName().toLowerCase(Locale.ROOT));
}
