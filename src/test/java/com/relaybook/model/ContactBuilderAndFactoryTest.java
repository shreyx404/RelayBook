package com.relaybook.model;

import com.relaybook.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builder behaviour and the form-driven factory, including per-route form fields. */
class ContactBuilderAndFactoryTest {

    @Test
    void buildValidatesSoAnInvalidContactNeverEscapes() {
        assertThrows(ValidationException.class, () ->
                Contact.builder(ContactType.PERSON).fullName("No Routes").build());
        assertThrows(ValidationException.class, () ->
                Contact.builder(ContactType.PERSON).build());
    }

    @Test
    void builderGeneratesAnIdWhenNoneGiven() {
        Contact first = Contact.builder(ContactType.PERSON).fullName("A")
                .route(Channel.PHONE, "+91 11111 11111").build();
        Contact second = Contact.builder(ContactType.PERSON).fullName("B")
                .route(Channel.PHONE, "+91 22222 22222").build();
        assertNotNull(first.getId());
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void factoryParsesIndexedRouteFields() {
        Map<String, String> form = new HashMap<>();
        form.put("type", "PERSON");
        form.put("fullName", "Form Person");
        form.put("route.0.channel", "WHATSAPP");
        form.put("route.0.value", "+91 90000 00000");
        form.put("route.0.verified", "2026-01-05");
        form.put("route.0.label", "Mobile");
        form.put("route.1.channel", "TELEGRAM");
        form.put("route.1.value", "@formperson");
        Contact contact = ContactFactory.fromForm("form-1", form);
        assertEquals(2, contact.getRoutes().size());
        assertEquals(Channel.WHATSAPP, contact.getRoute(Channel.WHATSAPP).orElseThrow().getChannel());
        assertEquals("Mobile", contact.getRoute(Channel.WHATSAPP).orElseThrow().getLabel());
        assertEquals(LocalDate.of(2026, 1, 5), contact.getRoute(Channel.WHATSAPP).orElseThrow().getLastVerified());
        assertEquals("@formperson", contact.getRoute(Channel.TELEGRAM).orElseThrow().getValue());
    }

    @Test
    void legacyFlatFieldsStillWorkForOlderClients() {
        Map<String, String> form = new HashMap<>();
        form.put("type", "PERSON");
        form.put("fullName", "Legacy Caller");
        form.put("phone", "+91 11111 11111");
        form.put("email", "legacy@example.test");
        form.put("preferredChannel", "EMAIL");
        form.put("lastVerified", "2026-02-01");
        Contact contact = ContactFactory.fromForm("legacy-1", form);
        assertEquals("+91 11111 11111", contact.getPhone());
        assertEquals("legacy@example.test", contact.getEmail());
        assertEquals(LocalDate.of(2026, 2, 1), contact.getLastVerified());
        assertEquals(Channel.EMAIL, contact.getPreferredChannel());
    }

    @Test
    void explicitRouteEntriesWinOverLegacyFieldsOnTheSameChannel() {
        Map<String, String> form = new HashMap<>();
        form.put("type", "PERSON");
        form.put("fullName", "Both Kinds");
        form.put("phone", "+91 00000 00000");
        form.put("route.0.channel", "PHONE");
        form.put("route.0.value", "+91 11111 11111");
        form.put("route.0.verified", "2026-03-01");
        Contact contact = ContactFactory.fromForm("both-1", form);
        assertEquals("+91 11111 11111", contact.getPhone());
    }

    @Test
    void badFormDateIsAValidationException() {
        Map<String, String> form = new HashMap<>();
        form.put("type", "PERSON");
        form.put("fullName", "Bad Date");
        form.put("phone", "+91 11111 11111");
        form.put("lastVerified", "01/02/2026");
        ValidationException ex = assertThrows(ValidationException.class, () -> ContactFactory.fromForm("bad-1", form));
        assertTrue(ex.getMessage().contains("YYYY-MM-DD"));
    }

    @Test
    void unknownChannelIsAValidationException() {
        Map<String, String> form = new HashMap<>();
        form.put("type", "PERSON");
        form.put("fullName", "Bad Channel");
        form.put("route.0.channel", "SMOKE_SIGNAL");
        form.put("route.0.value", "hill top");
        assertThrows(ValidationException.class, () -> ContactFactory.fromForm("bad-2", form));
    }

    @Test
    void preferredChannelFallsBackToTheFirstSavedRoute() {
        Contact contact = Contact.builder(ContactType.PERSON).fullName("Fallback Pref")
                .preferredChannel(Channel.EMAIL) // not saved
                .route(Channel.PHONE, "+91 11111 11111").build();
        assertEquals(Channel.PHONE, contact.getPreferredChannel());
    }
}
