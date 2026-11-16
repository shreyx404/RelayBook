package com.relaybook.document;

import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RFC 2426 correctness: N:, INTERNET, folding, escaping, CRLF, and privacy rules. */
class ContactCardDocumentTest {

    private Contact contact(boolean consent) {
        return Contact.builder(ContactType.PERSON).id("vc-1").fullName("Arjun Venkataraman Iyengar")
                .organization("Example Org")
                .relationship("Colleague")                    // never exported
                .bestTime("Weekdays 18:00-21:00")             // never exported
                .timeZone("Asia/Kolkata")                     // never exported
                .notes("private note; with, special chars")   // never exported
                .route(Channel.PHONE, "+91 10000 00018", LocalDate.now(), "Mobile")
                .route(Channel.EMAIL, "arjun@example.test", LocalDate.now())
                .route(Channel.WHATSAPP, "+91 10000 00019")
                .route(Channel.TELEGRAM, "@arjun")
                .shareConsent(consent)
                .build();
    }

    @Test
    void cardAlwaysHasTheRequiredNProperty() {
        String card = new ContactCardDocument(contact(true)).export();
        assertTrue(card.contains("N:Iyengar;Arjun Venkataraman"),
                () -> "N line missing or wrong: " + card);
        // The last token becomes the family name, everything before it the given name.
        assertEquals("Arjun Venkataraman", givenNameOf(card));
        assertEquals("Iyengar", familyNameOf(card));
    }

    @Test
    void singleTokenNameGoesIntoTheFamilySlot() {
        Contact single = Contact.builder(ContactType.PERSON).id("vc-2").fullName("Madhuri")
                .route(Channel.PHONE, "+91 10000 00020").build();
        String card = new ContactCardDocument(single).export();
        assertTrue(card.contains("N:Madhuri;"), () -> card);
    }

    @Test
    void emailUsesInternetType() {
        String card = new ContactCardDocument(contact(true)).export();
        assertTrue(card.contains("EMAIL;TYPE=INTERNET:arjun@example.test"));
    }

    @Test
    void allPhysicalLinesStayWithin75OctetsEvenWithUnicode() {
        Contact longName = Contact.builder(ContactType.PERSON).id("vc-3")
                .fullName("Ångström Übergänger Öbergruppenführer " + "Straßenbahnhaltestelle ".repeat(4))
                .route(Channel.EMAIL, "ångström.überläng@example.test").build();
        String card = new ContactCardDocument(longName.toBuilder().shareConsent(true).build()).export();
        for (String line : card.split("\r\n", -1)) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75,
                    () -> "physical line exceeds 75 octets: " + line);
        }
    }

    @Test
    void foldingUnfoldsBackToTheOriginalLine() {
        String original = "FN:Very Long Name That Definitely Exceeds The Seventy Five Octet Limit Of RFC 2426";
        String folded = ContactCardDocument.fold(original);
        String unfolded = folded.replace("\r\n ", "");
        assertEquals(original, unfolded);
    }

    @Test
    void specialCharactersAreEscaped() {
        Contact tricky = Contact.builder(ContactType.PERSON).id("vc-4").fullName("Semi;Colon, Comma")
                .route(Channel.PHONE, "+91 10000 00021").build();
        String card = new ContactCardDocument(tricky).export();
        assertTrue(card.contains("FN:Semi\\;Colon\\, Comma"), () -> card);
        assertTrue(card.contains("N:Comma;Semi\\;Colon"), () -> card);
    }

    @Test
    void usesCrlfLineEndingsOnly() {
        String card = new ContactCardDocument(contact(true)).export();
        assertFalse(card.replace("\r\n", "").contains("\n"), "bare LF found");
        assertTrue(card.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"));
        assertTrue(card.endsWith("END:VCARD\r\n"));
    }

    @Test
    void notesRelationshipBestTimeAndTimeZoneAreNeverExported() {
        for (boolean consent : new boolean[]{false, true}) {
            String card = new ContactCardDocument(contact(consent)).export();
            assertFalse(card.contains("private note"), "notes leaked (consent=" + consent + ")");
            assertFalse(card.contains("Colleague"), "relationship leaked");
            assertFalse(card.contains("Weekdays 18:00-21:00"), "best time leaked");
            assertFalse(card.contains("Asia/Kolkata"), "time zone leaked");
        }
    }

    @Test
    void routesAreExportedOnlyWithConsent() {
        assertFalse(new ContactCardDocument(contact(false)).export().contains("TEL"));
        String shared = new ContactCardDocument(contact(true)).export();
        assertTrue(shared.contains("TEL;TYPE=CELL:+91 10000 00018"));
        assertTrue(shared.contains("TEL;TYPE=CELL,X-WHATSAPP:+91 10000 00019"));
        assertTrue(shared.contains("X-RELAYBOOK-TELEGRAM:@arjun"));
    }

    @Test
    void privacyLabelExplainsTheMode() {
        assertTrue(new ContactCardDocument(contact(false)).getPrivacyLabel().contains("No sharing consent"));
        assertTrue(new ContactCardDocument(contact(true)).getPrivacyLabel().contains("Consent recorded"));
    }

    private String familyNameOf(String card) {
        for (String line : card.split("\r\n")) {
            if (line.startsWith("N:")) {
                return line.substring(2).split(";")[0];
            }
        }
        return "";
    }

    private String givenNameOf(String card) {
        for (String line : card.split("\r\n")) {
            if (line.startsWith("N:")) {
                String[] parts = line.substring(2).split(";", -1);
                return parts.length > 1 ? parts[1] : "";
            }
        }
        return "";
    }
}
