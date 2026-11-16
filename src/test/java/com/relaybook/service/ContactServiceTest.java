package com.relaybook.service;

import com.relaybook.exception.ContactNotFoundException;
import com.relaybook.exception.DuplicateContactException;
import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import com.relaybook.repository.InMemoryContactRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Service use-cases: listener notifications, mark-verified, search, sorting, duplicates. */
class ContactServiceTest {

    private static Contact person(String id, String name, String phone) {
        return Contact.builder(ContactType.PERSON).id(id).fullName(name)
                .route(Channel.PHONE, phone, LocalDate.now().minusDays(1)).build();
    }

    /** Observer test double that records every callback. */
    private static final class RecordingListener implements ContactChangeListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onCreated(Contact contact) {
            events.add("created:" + contact.getId());
        }

        @Override
        public void onUpdated(Contact contact) {
            events.add("updated:" + contact.getId());
        }

        @Override
        public void onDeleted(Contact contact) {
            events.add("deleted:" + contact.getId());
        }
    }

    @Test
    void successfulChangesNotifyRegisteredListeners() {
        ContactService service = new ContactService(new InMemoryContactRepository(false));
        RecordingListener listener = new RecordingListener();
        service.addListener(listener);

        Contact contact = person("svc-1", "Notify Me", "+91 10000 00001");
        service.create(contact);
        service.update(contact.toBuilder().notes("changed").build());
        service.markVerified("svc-1");
        service.delete("svc-1");
        service.removeListener(listener);
        // No further notifications after removal:
        service.create(person("svc-2", "Second", "+91 10000 00002"));

        assertEquals(List.of("created:svc-1", "updated:svc-1", "updated:svc-1", "deleted:svc-1"), listener.events);
        assertEquals(1, service.listContacts().size());
    }

    @Test
    void markVerifiedTodaySetsEveryRoute() {
        ContactService service = new ContactService(new InMemoryContactRepository(false));
        Contact contact = Contact.builder(ContactType.PERSON).id("svc-3").fullName("Verifier")
                .route(Channel.PHONE, "+91 10000 00003", LocalDate.now().minusDays(90))
                .route(Channel.EMAIL, "verify@example.test", LocalDate.now().minusDays(200))
                .build();
        service.create(contact);
        service.markVerified("svc-3");
        Contact updated = service.requireContact("svc-3");
        assertEquals(LocalDate.now(), updated.getRoute(Channel.PHONE).orElseThrow().getLastVerified());
        assertEquals(LocalDate.now(), updated.getRoute(Channel.EMAIL).orElseThrow().getLastVerified());
    }

    @Test
    void markVerifiedPerRouteTouchesOnlyThatRoute() {
        ContactService service = new ContactService(new InMemoryContactRepository(false));
        Contact contact = Contact.builder(ContactType.PERSON).id("svc-4").fullName("Partial")
                .route(Channel.PHONE, "+91 10000 00004", LocalDate.now().minusDays(90))
                .route(Channel.EMAIL, "partial@example.test", LocalDate.now().minusDays(200))
                .build();
        service.create(contact);
        service.markVerified("svc-4", Channel.EMAIL);
        Contact updated = service.requireContact("svc-4");
        assertEquals(LocalDate.now().minusDays(90), updated.getRoute(Channel.PHONE).orElseThrow().getLastVerified());
        assertEquals(LocalDate.now(), updated.getRoute(Channel.EMAIL).orElseThrow().getLastVerified());
    }

    @Test
    void markVerifiedPerRouteWithoutThatChannelFailsClearly() {
        ContactService service = new ContactService(new InMemoryContactRepository(false));
        service.create(person("svc-5", "No Telegram", "+91 10000 00005"));
        Exception ex = assertThrows(Exception.class, () -> service.markVerified("svc-5", Channel.TELEGRAM));
        assertTrue(ex.getMessage().contains("No Telegram route"));
    }

    @Test
    void missingContactGivesContactNotFoundException() {
        ContactService service = new ContactService(new InMemoryContactRepository(false));
        assertThrows(ContactNotFoundException.class, () -> service.requireContact("ghost"));
        assertThrows(ContactNotFoundException.class, () -> service.delete("ghost"));
    }

    @Test
    void createRejectsDuplicatesOfNamePlusSharedRoute() {
        ContactService service = new ContactService(new InMemoryContactRepository(false));
        service.create(person("dup-1", "Dup Person", "+91 10000 00006"));
        // same name (different case) plus same phone value -> duplicate
        Contact duplicate = person("dup-2", "dup person", "+91 10000 00006");
        assertThrows(DuplicateContactException.class, () -> service.create(duplicate));
        // same name but a different route is allowed
        service.create(person("dup-3", "Dup Person", "+91 10000 00007"));
        // same route but a different name is allowed
        service.create(person("dup-4", "Other Person", "+91 10000 00006"));
        assertEquals(3, service.listContacts().size());
    }

    @Test
    void searchFiltersByTextTypeAndStaleness() {
        InMemoryContactRepository repository = new InMemoryContactRepository(false);
        ContactService service = new ContactService(repository);
        service.create(Contact.builder(ContactType.PERSON).id("s-1").fullName("Ravi Kumar")
                .route(Channel.PHONE, "+91 10000 00008", LocalDate.now().minusDays(10)).build());
        service.create(Contact.builder(ContactType.ORGANIZATION).id("s-2").fullName("Kumar Electronics")
                .route(Channel.PHONE, "+91 10000 00009", LocalDate.now().minusDays(300)).build());
        service.create(Contact.builder(ContactType.EMERGENCY).id("s-3").fullName("Ravi Verma")
                .route(Channel.PHONE, "+91 10000 00010", LocalDate.now().minusDays(120))
                .route(Channel.EMAIL, "ravi.v@example.test", LocalDate.now().minusDays(2)).build());

        assertEquals(2, service.search("ravi", null, false).size());
        assertEquals(1, service.search("kumar", ContactType.ORGANIZATION, false).size());
        // Ravi Verma (emergency, preferred phone 120 days old) is stale; Kumar Electronics (365-day window) is not.
        List<Contact> stale = service.search(null, null, true);
        assertEquals(1, stale.size());
        assertEquals("Ravi Verma", stale.get(0).getFullName());
        assertTrue(service.search(null, ContactType.PERSON, false).stream()
                .allMatch(contact -> contact.getContactType() == ContactType.PERSON));
    }

    @Test
    void sortOptionsOrderTheDirectory() {
        ContactService service = new ContactService(new InMemoryContactRepository(false));
        service.create(Contact.builder(ContactType.PERSON).id("o-1").fullName("Charlie")
                .route(Channel.PHONE, "+91 10000 00011", LocalDate.now().minusDays(100)).build());
        service.create(Contact.builder(ContactType.ORGANIZATION).id("o-2").fullName("alpha org")
                .route(Channel.PHONE, "+91 10000 00012", LocalDate.now().minusDays(1)).build());
        List<Contact> contacts = service.listContacts();

        assertEquals("alpha org", SortOption.NAME.apply(contacts, service.getAdvisor()).get(0).getFullName());
        assertEquals("Charlie", SortOption.NAME.apply(contacts, service.getAdvisor()).get(1).getFullName());
        assertEquals("alpha org", SortOption.SCORE.apply(contacts, service.getAdvisor()).get(0).getFullName());
        // Last-verified sorts ascending (oldest first, undated last) so the weakest records float up.
        assertEquals("Charlie", SortOption.LAST_VERIFIED.apply(contacts, service.getAdvisor()).get(0).getFullName());
        assertEquals("alpha org", SortOption.TYPE.apply(contacts, service.getAdvisor()).get(0).getFullName());
    }

    @Test
    void statsCountsMatchTheAdvisorStatuses() {
        ContactService service = new ContactService(new InMemoryContactRepository(false));
        service.create(Contact.builder(ContactType.ORGANIZATION).id("st-1").fullName("Fresh Org")
                .route(Channel.PHONE, "+91 10000 00013", LocalDate.now().minusDays(1))
                .route(Channel.EMAIL, "fresh@example.test", LocalDate.now().minusDays(1)).build());
        service.create(Contact.builder(ContactType.ORGANIZATION).id("st-2").fullName("Stale Org")
                .route(Channel.PHONE, "+91 10000 00014", LocalDate.now().minusDays(400)).build());
        DirectoryStats stats = service.stats();
        assertEquals(2, stats.getTotal());
        assertEquals(1, stats.getStale());
        assertEquals(1, stats.getReady());
        assertEquals(0, stats.getEmergencyWithoutTwoRoutes());
        assertFalse(stats.getSingleRoute() > 2);
    }
}
