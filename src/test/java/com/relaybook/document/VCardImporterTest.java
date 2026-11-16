package com.relaybook.document;

import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import com.relaybook.repository.InMemoryContactRepository;
import com.relaybook.service.ContactService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** vCard import: fresh cards, duplicates, malformed cards, folding awareness. */
class VCardImporterTest {

    private final ContactService service = new ContactService(new InMemoryContactRepository(false));

    private static final String TWO_CARDS = ""
            + "BEGIN:VCARD\r\n"
            + "VERSION:3.0\r\n"
            + "FN:Rhea Menon\r\n"
            + "N:Menon;Rhea;;;\r\n"
            + "TEL;TYPE=CELL:+91 10000 00027\r\n"
            + "EMAIL;TYPE=INTERNET:rhea@example.test\r\n"
            + "END:VCARD\r\n"
            + "BEGIN:VCARD\r\n"
            + "VERSION:3.0\r\n"
            + "N:Patel\r\n"
            + "TEL;TYPE=VOICE:+91 10000 00028\r\n"
            + "END:VCARD\r\n";

    @Test
    void importsFreshCardsAndReportsTheSummary() {
        ImportSummary summary = new VCardImporter(service).importFromText(TWO_CARDS);
        assertEquals(2, summary.getImportedCount());
        assertEquals(0, summary.getSkippedDuplicatesCount());
        assertEquals(0, summary.getRejectedCount());
        assertTrue(summary.getImportedNames().contains("Rhea Menon"));
        // The second card had no FN, so its name came from the N: family slot.
        assertEquals(2, service.listContacts().size());
    }

    @Test
    void importedRoutesExistWithoutVerificationDates() {
        new VCardImporter(service).importFromText(TWO_CARDS);
        Contact rhea = service.listContacts().stream()
                .filter(contact -> contact.getFullName().equals("Rhea Menon"))
                .findFirst().orElseThrow();
        assertEquals("+91 10000 00027", rhea.getPhone());
        assertEquals("rhea@example.test", rhea.getEmail());
        assertFalse(rhea.getRoute(Channel.PHONE).orElseThrow().getLastVerified() != null);
        assertFalse(rhea.hasShareConsent());
        assertEquals("Imported from vCard", rhea.getRelationship());
    }

    @Test
    void aCardMatchingAnExistingRecordIsSkippedAsDuplicate() {
        service.create(Contact.builder(ContactType.PERSON).id("dup-target").fullName("Rhea Menon")
                .route(Channel.PHONE, "+91 10000 00027").build());
        ImportSummary summary = new VCardImporter(service).importFromText(TWO_CARDS);
        assertEquals(1, summary.getSkippedDuplicatesCount());
        assertEquals(1, summary.getImportedCount());
        assertTrue(summary.getSkippedDuplicates().contains("Rhea Menon"));
    }

    @Test
    void malformedAndNamelessCardsAreRejectedWithoutAbortingTheRun() {
        String mixed = ""
                + "BEGIN:VCARD\r\nEND:VCARD\r\n"                                          // no properties at all
                + "BEGIN:VCARD\r\nVERSION:3.0\r\nTEL:+91 10000 00029\r\nEND:VCARD\r\n" // no FN/N
                + TWO_CARDS;
        ImportSummary summary = new VCardImporter(service).importFromText(mixed);
        assertEquals(2, summary.getImportedCount());
        assertEquals(2, summary.getRejectedCount());
        assertTrue(summary.getRejected().get(0).contains("without properties"), () -> summary.getRejected().toString());
        assertTrue(summary.getRejected().get(1).contains("without FN or N"), () -> summary.getRejected().toString());
    }

    @Test
    void unfoldedContinuationLinesAreUnderstood() {
        // A folded FN line (CRLF + space) must be joined before parsing.
        // The NOTE value is folded with a CRLF + space continuation (RFC 2425),
        // which the importer must join before parsing.
        String folded = ""
                + "BEGIN:VCARD\r\n"
                + "VERSION:3.0\r\n"
                + "FN:Longus Namus Withfold\r\n"
                + "NOTE:this note\r\n"
                + "  continues on two lines\r\n"
                + "TEL;TYPE=CELL:+91 10000 00030\r\n"
                + "END:VCARD\r\n";
        ImportSummary summary = new VCardImporter(service).importFromText(folded);
        assertEquals(1, summary.getImportedCount());
        Contact imported = service.listContacts().stream()
                .filter(contact -> contact.getFullName().equals("Longus Namus Withfold"))
                .findFirst().orElseThrow();
        assertEquals("this note continues on two lines", imported.getNotes());
    }

    @Test
    void exportedCardsRoundTrip() {
        Contact original = Contact.builder(ContactType.PERSON).id("rt-1").fullName("Round Trip Son")
                .route(Channel.PHONE, "+91 10000 00031", LocalDate.now())
                .route(Channel.EMAIL, "rt@example.test")
                .shareConsent(true)
                .build();
        String card = new ContactCardDocument(original).export();
        ImportSummary summary = new VCardImporter(service).importFromText(card);
        assertEquals(1, summary.getImportedCount());
        Contact imported = service.requireContact(summary.getImportedNames().contains("Round Trip Son")
                ? service.listContacts().stream().filter(c -> c.getFullName().equals("Round Trip Son"))
                        .findFirst().orElseThrow().getId()
                : "");
        assertEquals("Round Trip Son", imported.getFullName());
        assertEquals("+91 10000 00031", imported.getPhone());
        assertEquals("rt@example.test", imported.getEmail());
        assertEquals(ContactType.PERSON, imported.getContactType());
    }

    @Test
    void emptyInputIsRejectedCleanly() {
        ImportSummary summary = new VCardImporter(service).importFromText("   ");
        assertEquals(0, summary.getImportedCount());
        assertEquals(1, summary.getRejectedCount());
        assertTrue(summary.getRejected().contains("The file is empty."));
    }
}
