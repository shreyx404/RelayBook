package com.relaybook.repository;

import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe demo repository. Records disappear when the application exits. */
public final class InMemoryContactRepository implements ContactRepository {
    private final ConcurrentMap<String, Contact> contacts = new ConcurrentHashMap<>();

    public InMemoryContactRepository(boolean seedDemoData) {
        if (seedDemoData) {
            seedExamples();
        }
    }

    private void seedExamples() {
        // Seeding uses the same builder the UIs use, so demo data obeys the same rules.
        Contact mira = Contact.builder(ContactType.PERSON)
                .id("demo-mira-sen")
                .fullName("Mira Sen")
                .relationship("Friend")
                .preferredChannel(Channel.PHONE)
                .route(Channel.PHONE, "+91 00000 10001", LocalDate.now().minusDays(42), "Mobile")
                .route(Channel.EMAIL, "mira@example.test", LocalDate.now().minusDays(120), null)
                .route(Channel.ALTERNATE_PHONE, "+91 00000 10002", null, "Home")
                .bestTime("Weekdays after 6 pm")
                .timeZone("Asia/Kolkata")
                .notes("Synthetic sample record. Do not use as real contact data.")
                .shareConsent(false)
                .build();
        Contact dev = Contact.builder(ContactType.EMERGENCY)
                .id("demo-dev-rao")
                .fullName("Dev Rao")
                .relationship("Emergency contact")
                .preferredChannel(Channel.PHONE)
                .route(Channel.PHONE, "+91 00000 20001", LocalDate.now().minusDays(240), "Mobile")
                .route(Channel.EMAIL, "dev@example.test", LocalDate.now().minusDays(30), null)
                .bestTime("Any time")
                .timeZone("Asia/Kolkata")
                .notes("Synthetic sample record. Confirm details before relying on it.")
                .shareConsent(false)
                .build();
        Contact studio = Contact.builder(ContactType.ORGANIZATION)
                .id("demo-studio")
                .fullName("Pune Repair Collective")
                .organization("Pune Repair Collective")
                .relationship("Local service")
                .preferredChannel(Channel.EMAIL)
                .route(Channel.EMAIL, "hello@example.test", null, "Front desk")
                .route(Channel.ALTERNATE_PHONE, "+91 00000 30001", null, "Workshop")
                .bestTime("Weekdays, 10 am to 5 pm")
                .timeZone("Asia/Kolkata")
                .notes("Synthetic sample record. Do not use as real contact data.")
                .shareConsent(false)
                .build();
        contacts.put(mira.getId(), mira);
        contacts.put(dev.getId(), dev);
        contacts.put(studio.getId(), studio);
    }

    @Override
    public List<Contact> findAll() {
        List<Contact> result = new ArrayList<>(contacts.values());
        result.sort(Comparator.comparing(Contact::getFullName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Override
    public Optional<Contact> findById(String id) {
        return Optional.ofNullable(contacts.get(id));
    }

    @Override
    public void insert(Contact contact) {
        if (contacts.putIfAbsent(contact.getId(), contact) != null) {
            throw new DataAccessException("A contact with that ID already exists.");
        }
    }

    @Override
    public void update(Contact contact) {
        if (contacts.replace(contact.getId(), contact) == null) {
            throw new DataAccessException("Contact no longer exists.");
        }
    }

    @Override
    public boolean delete(String id) {
        return contacts.remove(id) != null;
    }
}
