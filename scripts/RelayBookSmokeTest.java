package com.relaybook;

import com.relaybook.document.ContactCardDocument;
import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import com.relaybook.repository.InMemoryContactRepository;
import com.relaybook.service.ContactService;
import com.relaybook.syllabus.BankAccount;

import java.time.LocalDate;

/** Dependency-free smoke tests; run with scripts/smoke-test.sh. */
public final class RelayBookSmokeTest {
    private RelayBookSmokeTest() { }

    public static void main(String[] args) {
        InMemoryContactRepository repository = new InMemoryContactRepository(false);
        ContactService service = new ContactService(repository);

        Contact stale = Contact.builder(ContactType.EMERGENCY)
                .id("test-1")
                .fullName("Test Person")
                .relationship("Emergency")
                .preferredChannel(Channel.PHONE)
                .route(Channel.PHONE, "+91 11111 11111", LocalDate.now().minusDays(250), "Mobile")
                .route(Channel.EMAIL, "person@example.test", LocalDate.now().minusDays(5), null)
                .bestTime("Evenings")
                .timeZone("Asia/Kolkata")
                .notes("private note")
                .build();
        service.create(stale);
        check(service.listContacts().size() == 1, "create/read");
        check(service.analyze(stale).getStatus().equals("Needs verification"), "stale status");
        check(service.analyze(stale).getSuggestedChannel() == Channel.EMAIL, "fallback recommendation");
        check(stale.getPhone().equals("+91 11111 11111"), "legacy phone getter reads the route");
        check(stale.getLastVerified().equals(LocalDate.now().minusDays(5)), "record date is the latest route date");

        String restrictedCard = new ContactCardDocument(stale).export();
        check(!restrictedCard.contains("TEL") && !restrictedCard.contains("EMAIL"), "consent-aware route omission");
        check(!restrictedCard.contains("private note"), "private note omission");

        Contact consented = Contact.builder(ContactType.PERSON)
                .id("test-2")
                .fullName("Consented Person")
                .organization("Example Org")
                .relationship("Colleague")
                .preferredChannel(Channel.PHONE)
                .route(Channel.PHONE, "+91 22222 22222", LocalDate.now(), "Mobile")
                .route(Channel.EMAIL, "person2@example.test", LocalDate.now(), null)
                .bestTime("Mornings")
                .timeZone("Asia/Kolkata")
                .notes("never export this note")
                .shareConsent(true)
                .build();
        String sharedCard = new ContactCardDocument(consented).export();
        check(sharedCard.contains("TEL;TYPE=CELL") && sharedCard.contains("EMAIL;TYPE=INTERNET"), "consented route export");
        check(!sharedCard.contains("never export this note"), "notes omitted from all exports");

        Contact updated = stale.toBuilder()
                .fullName("Updated Person")
                .verifyAllRoutes(LocalDate.now())
                .build();
        service.update(updated);
        check(service.requireContact("test-1").getFullName().equals("Updated Person"), "update");
        check(service.requireContact("test-1").getLastVerified().equals(LocalDate.now()), "verify all routes");
        service.delete("test-1");
        check(service.listContacts().isEmpty(), "delete");

        boolean rejected = false;
        try {
            Contact.builder(ContactType.EMERGENCY)
                    .fullName("Single Route Emergency")
                    .route(Channel.PHONE, "+91 33333 33333")
                    .build();
        } catch (RuntimeException expected) {
            rejected = expected.getMessage().contains("at least two routes");
        }
        check(rejected, "emergency needs two routes (polymorphic validator)");

        // --- Tier 2H: documents, vCard correctness, importer ---
        String exportedCard = new ContactCardDocument(consented).export();
        check(exportedCard.contains("N:Person;Consented"), "vCard has the required N: property");
        check(exportedCard.contains("EMAIL;TYPE=INTERNET"), "vCard email uses TYPE=INTERNET");
        check(exportedCard.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"), "vCard uses CRLF endings");
        check(exportedCard.endsWith("END:VCARD\r\n"), "vCard ends with END:VCARD");

        com.relaybook.document.CsvDirectoryDocument privacyCsv =
                new com.relaybook.document.CsvDirectoryDocument(java.util.List.of(consented, updated), false);
        check(!privacyCsv.export().contains("never export this note"), "privacy CSV omits notes");
        check(privacyCsv.export().contains("+91 22222 22222"), "privacy CSV keeps consented routes");
        com.relaybook.document.CsvDirectoryDocument fullCsv =
                new com.relaybook.document.CsvDirectoryDocument(java.util.List.of(consented), true);
        check(fullCsv.export().contains("never export this note"), "full-backup CSV is explicit about notes");

        com.relaybook.document.JsonBackupDocument backup =
                new com.relaybook.document.JsonBackupDocument(java.util.List.of(consented));
        check(backup.export().contains("\"kind\":\"relaybook-full-backup\"") && backup.export().contains("Mobile"),
                "JSON backup contains routes and labels");

        com.relaybook.service.Result<String> ok = com.relaybook.service.Result.success("value");
        com.relaybook.service.Result<String> bad = com.relaybook.service.Result.failure("broken");
        check(ok.isSuccess() && ok.getValue().equals("value"), "Result success branch");
        check(!bad.isSuccess() && bad.getError().equals("broken"), "Result failure branch");

        Contact imported = com.relaybook.model.Contact.builder(ContactType.PERSON)
                .fullName("Imported Person")
                .route(Channel.PHONE, "+91 44444 44444")
                .route(Channel.EMAIL, "imported@example.test")
                .build();
        service.create(imported);
        String exported = new ContactCardDocument(service.requireContact(imported.getId()).toBuilder()
                .shareConsent(true).build()).export();
        com.relaybook.document.ImportSummary summary = new com.relaybook.document.VCardImporter(service).importFromText(exported);
        check(summary.getSkippedDuplicatesCount() == 1, "importer flags duplicate of existing record");
        String freshCard = exported.replace("+91 44444 44444", "+91 44444 99999").replace("Imported Person", "Fresh Import");
        com.relaybook.document.ImportSummary freshSummary = new com.relaybook.document.VCardImporter(service).importFromText(freshCard);
        check(freshSummary.getImportedCount() == 1, "importer creates a fresh card");
        check(freshSummary.getRejectedCount() == 0, "importer reports no rejections");

        BankAccount account = new BankAccount("A-1", "Student", 100.00);
        check(account.deposit(25.50), "deposit");
        check(account.withdraw(10.00), "withdraw");
        check(!account.withdraw(500.00), "insufficient-funds guard");
        check(account.chargeFee(5.00), "fee");
        check(account.changeName("Updated Student"), "change name");
        check(account.toString().contains("Updated Student") && account.toString().contains("110.50"), "summary/toString");

        System.out.println("All RelayBook smoke checks passed.");
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Failed smoke check: " + label);
        }
    }
}
