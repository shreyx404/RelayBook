package com.relaybook.document;

import com.relaybook.model.Contact;
import com.relaybook.model.ContactMethod;

import java.nio.charset.StandardCharsets;

/**
 * Generates a privacy-aware vCard 3.0 document for a contact.
 *
 * <p>Correctness rules (RFC 2426): the required {@code N:} property is derived
 * from the full name, email routes use {@code EMAIL;TYPE=INTERNET}, lines
 * longer than 75 octets are folded with a CRLF + space continuation, and all
 * line endings are CRLF. Private notes, relationship context, best time, and
 * time zone are never exported; routes are exported only when the contact's
 * share-consent flag is set.</p>
 */
public final class ContactCardDocument extends Document implements ShareableDocument, PrivacyLabelled {
    private static final int MAX_LINE_OCTETS = 75;
    private static final String CRLF = "\r\n";

    private final Contact contact;

    public ContactCardDocument(Contact contact) {
        super(contact == null ? "Contact card" : contact.getFullName() + " — vCard");
        this.contact = contact;
    }

    @Override
    protected void appendHeader(StringBuilder document) {
        document.append("BEGIN:VCARD").append(CRLF).append("VERSION:3.0").append(CRLF);
    }

    @Override
    protected void appendFooter(StringBuilder document) {
        document.append("END:VCARD").append(CRLF);
    }

    @Override
    public String renderContent() {
        if (!isValid()) {
            throw new IllegalStateException("Cannot export an invalid contact card.");
        }
        StringBuilder card = new StringBuilder();
        appendLine(card, "FN", escape(contact.getFullName()));
        appendLine(card, "N", escape(familyName()) + ";" + escape(givenName()));
        if (contact.getOrganization() != null) {
            appendLine(card, "ORG", escape(contact.getOrganization()));
        }
        // Route details are exported only when the contact's share-consent flag is enabled.
        // Private notes, relationship labels, availability, and time zone are never exported.
        if (isShareAllowed()) {
            for (ContactMethod route : contact.getRoutes()) {
                appendRoute(card, route);
            }
        }
        return card.toString();
    }

    private void appendRoute(StringBuilder card, ContactMethod route) {
        switch (route.getChannel()) {
            case PHONE:
                appendLine(card, "TEL;TYPE=CELL", escape(route.getValue()));
                break;
            case ALTERNATE_PHONE:
                appendLine(card, "TEL;TYPE=VOICE", escape(route.getValue()));
                break;
            case WHATSAPP:
                appendLine(card, "TEL;TYPE=CELL,X-WHATSAPP", escape(route.getValue()));
                break;
            case EMAIL:
                appendLine(card, "EMAIL;TYPE=INTERNET", escape(route.getValue()));
                break;
            case TELEGRAM:
                // No native vCard 3.0 type; exported as an explicitly non-standard property.
                appendLine(card, "X-RELAYBOOK-TELEGRAM", escape(route.getValue()));
                break;
            default:
                break;
        }
    }

    /** Appends one (possibly folded) property line with a CRLF ending. */
    private void appendLine(StringBuilder target, String property, String value) {
        String line = property + ":" + value;
        target.append(fold(line)).append(CRLF);
    }

    /**
     * Folds a logical line so no physical line exceeds 75 octets (RFC 2426 §2.7,
     * line folding from RFC 2425). Continuation lines start with a single space
     * that is removed again when unfolding. Splitting is UTF-8 aware: a fold
     * never lands inside a multi-byte character.
     */
    static String fold(String line) {
        if (line.getBytes(StandardCharsets.UTF_8).length <= MAX_LINE_OCTETS) {
            return line;
        }
        StringBuilder folded = new StringBuilder();
        int octetsOnLine = 0;
        int limit = MAX_LINE_OCTETS; // first physical line carries up to 75 content octets
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            int octets = utf8Octets(codePoint);
            if (octetsOnLine + octets > limit) {
                folded.append(CRLF).append(' ');
                octetsOnLine = 1; // the leading space counts towards the continuation line
                limit = MAX_LINE_OCTETS - 1; // 1 space + 74 content = 75 octets
            }
            folded.appendCodePoint(codePoint);
            octetsOnLine += octets;
            i += Character.charCount(codePoint);
        }
        return folded.toString();
    }

    private static int utf8Octets(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }

    /**
     * Derives the vCard family name: the last whitespace-separated token of the
     * full name; a single-token name goes entirely into the family slot, which
     * keeps the required N: property present for every card.
     */
    private String familyName() {
        String name = contact.getFullName().trim();
        int lastSpace = name.lastIndexOf(' ');
        return lastSpace < 0 ? name : name.substring(lastSpace + 1);
    }

    /** Everything before the family name; empty when the name has one token. */
    private String givenName() {
        String name = contact.getFullName().trim();
        int lastSpace = name.lastIndexOf(' ');
        return lastSpace < 0 ? "" : name.substring(0, lastSpace);
    }

    @Override
    public boolean isValid() {
        return contact != null && contact.getFullName() != null && !contact.getFullName().trim().isEmpty();
    }

    @Override
    public boolean isShareAllowed() {
        return isValid() && contact.hasShareConsent();
    }

    @Override
    public String getPrivacyLabel() {
        return isShareAllowed()
                ? "Consent recorded: saved routes included; private notes omitted."
                : "No sharing consent: identity-only card; routes and private notes omitted.";
    }

    /** vCard 3.0 escaping for property values. */
    static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }
}
