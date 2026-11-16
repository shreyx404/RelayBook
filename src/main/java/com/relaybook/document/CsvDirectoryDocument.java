package com.relaybook.document;

import com.relaybook.model.Contact;
import com.relaybook.model.ContactMethod;

import java.util.List;

/**
 * Comma-separated directory export (Exportable + Validatable).
 *
 * <p>Privacy-aware by default: private notes are never included and routes are
 * included only for contacts whose share-consent flag is set. The constructor
 * flag {@code fullBackup} is meant for an explicit local backup chosen by the
 * user; it then includes notes and every route. The document records which
 * mode produced it.</p>
 */
public final class CsvDirectoryDocument extends Document implements Exportable, Validatable, PrivacyLabelled {
    private final List<Contact> contacts;
    private final boolean fullBackup;

    public CsvDirectoryDocument(List<Contact> contacts, boolean fullBackup) {
        super(fullBackup ? "RelayBook directory — full local backup" : "RelayBook directory — privacy-aware export");
        this.contacts = contacts;
        this.fullBackup = fullBackup;
    }

    @Override
    public String renderContent() {
        StringBuilder csv = new StringBuilder();
        csv.append("id,type,full_name,organization,relationship,preferred_channel,best_time,time_zone,last_verified,share_consent,routes");
        if (fullBackup) {
            csv.append(",notes");
        }
        csv.append("\r\n");
        for (Contact contact : contacts) {
            csv.append(escape(contact.getId())).append(',');
            csv.append(escape(contact.getTypeLabel())).append(',');
            csv.append(escape(contact.getFullName())).append(',');
            csv.append(escape(contact.getOrganization())).append(',');
            csv.append(escape(contact.getRelationship())).append(',');
            csv.append(escape(contact.getPreferredChannel() == null ? "" : contact.getPreferredChannel().name())).append(',');
            csv.append(escape(contact.getBestTime())).append(',');
            csv.append(escape(contact.getTimeZone())).append(',');
            csv.append(escape(contact.getLastVerified() == null ? "" : contact.getLastVerified().toString())).append(',');
            csv.append(contact.hasShareConsent()).append(',');
            csv.append(escape(routesText(contact)));
            if (fullBackup) {
                csv.append(',').append(escape(contact.getNotes()));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    /** Routes joined as CHANNEL=value pairs; only consented contacts contribute unless full backup. */
    private String routesText(Contact contact) {
        boolean includeRoutes = fullBackup || contact.hasShareConsent();
        if (!includeRoutes) {
            return "";
        }
        StringBuilder routes = new StringBuilder();
        for (ContactMethod route : contact.getRoutes()) {
            if (routes.length() > 0) {
                routes.append("; ");
            }
            routes.append(route.getChannel().name()).append('=').append(route.getValue());
        }
        return routes.toString();
    }

    @Override
    public boolean isValid() {
        return contacts != null;
    }

    @Override
    public String getPrivacyLabel() {
        return fullBackup
                ? "Full local backup: includes private notes and all routes. Keep the file safe."
                : "Privacy-aware export: no notes; routes only for consented contacts.";
    }

    /** RFC 4180 field escaping: wrap in quotes when needed and double embedded quotes. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
