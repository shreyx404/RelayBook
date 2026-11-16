package com.relaybook.document;

import com.relaybook.exception.RelayBookException;
import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import com.relaybook.service.ContactService;
import com.relaybook.service.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Imports contacts from vCard 3.0 text (for example an exported phone book).
 *
 * <p>Each card is parsed into a person contact; the per-card parse outcome is
 * a {@link Result} so one malformed card never aborts the whole file. A card
 * is treated as a duplicate when an existing record has the same name and at
 * least one identical route value (the service's duplicate rule). Every
 * imported record starts without a verification date — imported details are
 * not verified details.</p>
 */
public final class VCardImporter {
    private final ContactService service;

    public VCardImporter(ContactService service) {
        this.service = service;
    }

    /** Imports every card found in the text and reports what happened per card. */
    public ImportSummary importFromText(String vCardText) {
        ImportSummary.Collector summary = new ImportSummary.Collector();
        if (vCardText == null || vCardText.trim().isEmpty()) {
            summary.rejected("The file is empty.");
            return summary.build();
        }
        for (String cardText : splitCards(vCardText)) {
            Result<Contact> parsed = parseCard(cardText);
            if (!parsed.isSuccess()) {
                summary.rejected(parsed.getError());
                continue;
            }
            Contact candidate = parsed.getValue();
            if (service.findDuplicate(candidate).isPresent()) {
                summary.duplicate(candidate.getFullName());
                continue;
            }
            try {
                Contact created = service.create(candidate);
                summary.imported(created.getFullName());
            } catch (RelayBookException ex) {
                summary.rejected("Rejected \"" + candidate.getFullName() + "\": " + ex.getMessage());
            }
        }
        return summary.build();
    }

    /** Splits the text into BEGIN:VCARD..END:VCARD blocks after unfolding. */
    private List<String> splitCards(String text) {
        String unfolded = unfold(text);
        List<String> cards = new ArrayList<>();
        StringBuilder current = null;
        for (String line : unfolded.split("\r\n|\n")) {
            String upper = line.toUpperCase(Locale.ROOT).trim();
            if (upper.startsWith("BEGIN:VCARD")) {
                current = new StringBuilder();
            } else if (upper.startsWith("END:VCARD")) {
                if (current != null) {
                    cards.add(current.toString());
                    current = null;
                }
            } else if (current != null) {
                current.append(line).append('\n');
            }
        }
        return cards;
    }

    /** RFC 2425/2426 unfolding: continuation lines start with a space or tab. */
    static String unfold(String text) {
        StringBuilder unfolded = new StringBuilder();
        for (String line : text.split("\r\n|\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.length() > 0) {
                unfolded.append(line.substring(1));
            } else {
                if (unfolded.length() > 0) {
                    unfolded.append('\n');
                }
                unfolded.append(line);
            }
        }
        return unfolded.toString();
    }

    /** Parses one unfolded card body into a validated, unsaved contact. */
    private Result<Contact> parseCard(String cardText) {
        String fullName = null;
        String organization = null;
        String note = null;
        List<String> phones = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        int propertyCount = 0;

        for (String line : cardText.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String property = line.substring(0, colon).toUpperCase(Locale.ROOT);
            String value = unescape(line.substring(colon + 1).trim());
            if (value.isEmpty()) {
                continue;
            }
            propertyCount++;
            if (property.startsWith("FN")) {
                fullName = value;
            } else if (property.equals("NOTE") || property.startsWith("NOTE;")) {
                note = value;
            } else if (property.equals("N") || property.startsWith("N;")) {
                // N:Family;Given;... — used only when FN is missing.
                String family = value.split(";")[0].trim();
                if (fullName == null && !family.isEmpty()) {
                    fullName = family;
                }
            } else if (property.startsWith("ORG")) {
                organization = value.split(";")[0].trim();
            } else if (property.startsWith("TEL")) {
                phones.add(value);
            } else if (property.startsWith("EMAIL")) {
                emails.add(value);
            }
        }
        if (propertyCount == 0) {
            return Result.failure("Card without properties was skipped.");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            return Result.failure("Card without FN or N name was skipped.");
        }

        Contact.Builder builder = Contact.builder(ContactType.PERSON)
                .fullName(fullName.trim())
                .relationship("Imported from vCard")
                .organization(organization)
                .notes(note);
        boolean sawPhone = false;
        for (String phone : phones) {
            builder.route(sawPhone ? Channel.ALTERNATE_PHONE : Channel.PHONE, phone);
            sawPhone = true;
        }
        for (String email : emails) {
            builder.route(Channel.EMAIL, email);
        }

        try {
            return Result.success(builder.build());
        } catch (RelayBookException ex) {
            return Result.failure("Rejected \"" + fullName.trim() + "\": " + ex.getMessage());
        }
    }

    /** Reverses ContactCardDocument escaping in a single pass (no chained replace). */
    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case 'n':
                    case 'N':
                        out.append('\n');
                        break;
                    case ',':
                        out.append(',');
                        break;
                    case ';':
                        out.append(';');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    default:
                        out.append(next);
                }
                i++;
            } else {
                out.append(current);
            }
        }
        return out.toString();
    }
}
