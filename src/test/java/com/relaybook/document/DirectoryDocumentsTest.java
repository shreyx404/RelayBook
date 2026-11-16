package com.relaybook.document;

import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import com.relaybook.service.ReachabilityAdvisor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Privacy behaviour of the CSV / JSON backup / reachability summary documents. */
class DirectoryDocumentsTest {

    private static final ReachabilityAdvisor ADVISOR = new ReachabilityAdvisor();

    private static Contact consented() {
        return Contact.builder(ContactType.PERSON).id("d-1").fullName("Consented Person")
                .organization("Org")
                .relationship("Friend")
                .notes("secret note")
                .route(Channel.PHONE, "+91 10000 00022", LocalDate.now(), "Mobile")
                .route(Channel.EMAIL, "consent@example.test")
                .shareConsent(true)
                .build();
    }

    private static Contact unconsented() {
        return Contact.builder(ContactType.ORGANIZATION).id("d-2").fullName("Private Org")
                .notes("hidden note")
                .route(Channel.PHONE, "+91 10000 00023")
                .build();
    }

    @Test
    void privacyCsvOmitsNotesAndUnconsentedRoutes() {
        String csv = new CsvDirectoryDocument(List.of(consented(), unconsented()), false).export();
        assertFalse(csv.contains("secret note"));
        assertFalse(csv.contains("hidden note"));
        assertTrue(csv.contains("+91 10000 00022"), "consented route should be present");
        assertFalse(csv.contains("+91 10000 00023"), "unconsented route must be omitted");
        assertTrue(csv.contains("Consented Person"));
        assertTrue(csv.contains("Private Org"));
    }

    @Test
    void fullBackupCsvIncludesEverythingAndSaysSo() {
        CsvDirectoryDocument document = new CsvDirectoryDocument(List.of(consented()), true);
        String csv = document.export();
        assertTrue(csv.contains("secret note"));
        assertTrue(csv.contains("+91 10000 00023") || !csv.contains("Private Org")); // only consented listed here
        assertTrue(document.getPrivacyLabel().contains("Full local backup"));
    }

    @Test
    void csvEscapesCommasAndQuotes() {
        Contact contact = Contact.builder(ContactType.PERSON).id("d-3").fullName("Quote, \"The\" Comma")
                .route(Channel.PHONE, "+91 10000 00024").build();
        String csv = new CsvDirectoryDocument(List.of(contact), false).export();
        assertTrue(csv.contains("\"Quote, \"\"The\"\" Comma\""), () -> csv);
    }

    @Test
    void jsonBackupIsExplicitlyComplete() {
        String json = new JsonBackupDocument(List.of(consented(), unconsented())).export();
        assertTrue(json.contains("\"kind\":\"relaybook-full-backup\""));
        assertTrue(json.contains("secret note"), "backup includes notes");
        assertTrue(json.contains("hidden note"), "backup includes notes");
        assertTrue(json.contains("+91 10000 00023"), "backup includes all routes");
        assertTrue(json.contains("\"lastVerified\""), "backup includes per-route dates");
    }

    @Test
    void reachabilitySummaryListsStaleAndSingleRouteContacts() {
        Contact stale = Contact.builder(ContactType.PERSON).id("d-4").fullName("Stale Sam")
                .route(Channel.PHONE, "+91 10000 00025", LocalDate.now().minusDays(400)).build();
        Contact single = Contact.builder(ContactType.PERSON).id("d-5").fullName("Single Rita")
                .route(Channel.PHONE, "+91 10000 00026", LocalDate.now()).build();

        List<Contact> staleList = staleStatus(stale);
        List<Contact> singleList = singleStatus(single);

        String text = new ReachabilityReportDocument(staleList, singleList, false).export();
        assertTrue(text.contains("Stale records (1)"));
        assertTrue(text.contains("Stale Sam"));
        assertTrue(text.contains("Single-route records (1)"));
        assertTrue(text.contains("Single Rita"));
        assertTrue(text.contains("not a chance of a reply"));

        String html = new ReachabilityReportDocument(staleList, singleList, true).export();
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("Stale Sam"));
    }

    private List<Contact> staleStatus(Contact contact) {
        return ADVISOR.analyze(contact).getStatus().equals("Needs verification")
                ? List.of(contact) : List.of();
    }

    private List<Contact> singleStatus(Contact contact) {
        String status = ADVISOR.analyze(contact).getStatus();
        return ("Single route".equals(status) || "Add a backup route".equals(status))
                ? List.of(contact) : List.of();
    }
}
