package com.relaybook.repository;

import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CRUD behaviour of the in-memory store (no MySQL required). */
class InMemoryContactRepositoryTest {

    private Contact sample(String id) {
        return Contact.builder(ContactType.PERSON).id(id).fullName("Repo Person")
                .route(Channel.PHONE, "+91 10000 00015", LocalDate.now().minusDays(2))
                .route(Channel.EMAIL, "repo@example.test")
                .bestTime("any time")
                .timeZone("Asia/Kolkata")
                .notes("private")
                .build();
    }

    @Test
    void insertFindUpdateDeleteRoundTrip() {
        InMemoryContactRepository repository = new InMemoryContactRepository(false);
        repository.insert(sample("r-1"));
        assertEquals(1, repository.findAll().size());
        assertTrue(repository.findById("r-1").isPresent());

        Contact updated = repository.findById("r-1").orElseThrow().toBuilder()
                .fullName("Repo Person Jr.")
                .build();
        repository.update(updated);
        assertEquals("Repo Person Jr.", repository.findById("r-1").orElseThrow().getFullName());

        assertTrue(repository.delete("r-1"));
        assertFalse(repository.delete("r-1"));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void duplicateIdInsertFails() {
        InMemoryContactRepository repository = new InMemoryContactRepository(false);
        repository.insert(sample("r-2"));
        assertThrows(DataAccessException.class, () -> repository.insert(sample("r-2")));
    }

    @Test
    void updateMissingContactFails() {
        InMemoryContactRepository repository = new InMemoryContactRepository(false);
        assertThrows(DataAccessException.class, () -> repository.update(sample("ghost")));
    }

    @Test
    void findAllIsSortedByNameCaseInsensitive() {
        InMemoryContactRepository repository = new InMemoryContactRepository(false);
        repository.insert(Contact.builder(ContactType.PERSON).id("r-3").fullName("bravo")
                .route(Channel.PHONE, "+91 10000 00016").build());
        repository.insert(Contact.builder(ContactType.PERSON).id("r-4").fullName("Alpha")
                .route(Channel.PHONE, "+91 10000 00017").build());
        List<Contact> all = repository.findAll();
        assertEquals("Alpha", all.get(0).getFullName());
        assertEquals("bravo", all.get(1).getFullName());
    }

    @Test
    void seededDemoDataRespectsValidationRules() {
        InMemoryContactRepository repository = new InMemoryContactRepository(true);
        assertEquals(3, repository.findAll().size());
        for (Contact contact : repository.findAll()) {
            assertTrue(contact.getRoutes().size() >= contact.getMinimumRoutes(),
                    "seeded record " + contact.getFullName() + " must satisfy its own minimum");
        }
    }
}
