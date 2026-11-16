package com.relaybook.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Subtype-specific behaviour: thresholds, minimum routes, describe(), comparators. */
class ContactSubtypeBehaviourTest {

    private Contact person() {
        return Contact.builder(ContactType.PERSON).id("p1").fullName("Mira Sen")
                .route(Channel.PHONE, "+91 11111 11111", LocalDate.now().minusDays(10)).build();
    }

    @Test
    void personIsStaleAfter180DaysAndNeedsOneRoute() {
        Contact contact = person();
        assertEquals(180, contact.getStaleAfterDays());
        assertEquals(1, contact.getMinimumRoutes());
        assertEquals(ContactType.PERSON, contact.getContactType());
    }

    @Test
    void organizationIsStaleAfter365DaysAndDescribeMentionsOrganization() {
        Contact contact = Contact.builder(ContactType.ORGANIZATION).id("o1").fullName("Pune Repair Collective")
                .organization("Pune Repair Collective")
                .route(Channel.EMAIL, "hello@example.test").build();
        assertEquals(365, contact.getStaleAfterDays());
        assertEquals(1, contact.getMinimumRoutes());
        assertTrue(contact.describe().contains("Organization record"));
        assertTrue(contact.describe().contains("Pune Repair Collective"));
    }

    @Test
    void emergencyIsStaleAfter90DaysNeedsTwoRoutesAndDescribeMentionsEmergency() {
        Contact contact = Contact.builder(ContactType.EMERGENCY).id("e1").fullName("Dev Rao")
                .route(Channel.PHONE, "+91 22222 22222")
                .route(Channel.EMAIL, "dev@example.test").build();
        assertEquals(90, contact.getStaleAfterDays());
        assertEquals(2, contact.getMinimumRoutes());
        assertTrue(contact.describe().toLowerCase().contains("emergency"));
        assertTrue(contact.describe().contains("at least 2 routes"));
    }

    @Test
    void recordLevelDateIsTheLatestRouteDate() {
        Contact contact = Contact.builder(ContactType.PERSON).id("p2").fullName("Two Dates")
                .route(Channel.PHONE, "+91 1", LocalDate.now().minusDays(100))
                .route(Channel.EMAIL, "a@example.test", LocalDate.now().minusDays(5)).build();
        assertEquals(LocalDate.now().minusDays(5), contact.getLastVerified());
    }

    @Test
    void legacyGettersReadTheMatchingRoutes() {
        Contact contact = Contact.builder(ContactType.PERSON).id("p3").fullName("Legacy")
                .route(Channel.PHONE, "+91 1")
                .route(Channel.EMAIL, "a@example.test")
                .route(Channel.ALTERNATE_PHONE, "+91 2").build();
        assertEquals("+91 1", contact.getPhone());
        assertEquals("a@example.test", contact.getEmail());
        assertEquals("+91 2", contact.getAlternatePhone());
        assertFalse(contact.getRoute(Channel.WHATSAPP).isPresent());
        assertEquals(3, contact.getRoutes().size());
    }

    @Test
    void routesListIsImmutable() {
        Contact contact = person();
        List<ContactMethod> routes = contact.getRoutes();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> routes.add(new ContactMethod(Channel.PHONE, "+91 999", null, null)));
    }

    @Test
    void compareToIsCaseInsensitiveByName() {
        Contact alpha = Contact.builder(ContactType.PERSON).id("a").fullName("alpha").route(Channel.PHONE, "+91 1").build();
        Contact beta = Contact.builder(ContactType.PERSON).id("b").fullName("Beta").route(Channel.PHONE, "+91 2").build();
        assertTrue(alpha.compareTo(beta) < 0);
        assertTrue(beta.compareTo(alpha) > 0);
        assertEquals(0, alpha.compareTo(
                Contact.builder(ContactType.PERSON).id("c").fullName("ALPHA").route(Channel.PHONE, "+91 3").build()));
    }

    @Test
    void lastVerifiedComparatorSortsNullsLast() {
        Contact undated = Contact.builder(ContactType.PERSON).id("u").fullName("Undated")
                .route(Channel.PHONE, "+91 1").build();
        Contact recent = Contact.builder(ContactType.PERSON).id("r").fullName("Recent")
                .route(Channel.PHONE, "+91 1", LocalDate.now().minusDays(1)).build();
        java.util.List<Contact> sorted = new java.util.ArrayList<>(List.of(undated, recent));
        sorted.sort(Contact.BY_LAST_VERIFIED);
        assertEquals("Recent", sorted.get(0).getFullName());
        assertEquals("Undated", sorted.get(1).getFullName());
    }

    @Test
    void typeComparatorOrdersByTypeLabel() {
        Contact emergency = Contact.builder(ContactType.EMERGENCY).id("e").fullName("Zeta")
                .route(Channel.PHONE, "+91 1").route(Channel.EMAIL, "z@example.test").build();
        Contact organization = Contact.builder(ContactType.ORGANIZATION).id("o").fullName("Alpha Org")
                .route(Channel.PHONE, "+91 2").build();
        java.util.List<Contact> sorted = new java.util.ArrayList<>(List.of(emergency, organization));
        sorted.sort(Contact.BY_TYPE);
        // Alphabetical by type label: "Emergency contact" < "Organization".
        assertEquals("Zeta", sorted.get(0).getFullName());
        assertEquals("Alpha Org", sorted.get(1).getFullName());
    }

    @Test
    void toBuilderCopiesEveryFieldAndLeavesOriginalUntouched() {
        Contact original = person();
        Contact changed = original.toBuilder().fullName("Mira S. Sen").verifyRoute(Channel.PHONE, LocalDate.now()).build();
        assertEquals("Mira Sen", original.getFullName());
        assertEquals("Mira S. Sen", changed.getFullName());
        assertEquals(LocalDate.now().minusDays(10), original.getRoute(Channel.PHONE).orElseThrow().getLastVerified());
        assertEquals(LocalDate.now(), changed.getRoute(Channel.PHONE).orElseThrow().getLastVerified());
    }
}
