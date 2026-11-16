package com.relaybook.document;

import com.relaybook.model.Contact;

import java.util.List;

/**
 * Plain-text (or HTML) summary of the records that need attention: contacts
 * that are stale (past their own recheck window) and contacts with a single
 * saved route. The caller supplies the two already-classified lists, so the
 * document stays a pure formatter.
 */
public final class ReachabilityReportDocument extends Document implements Exportable, Validatable {
    private final List<Contact> staleContacts;
    private final List<Contact> singleRouteContacts;
    private final boolean html;

    public ReachabilityReportDocument(List<Contact> staleContacts, List<Contact> singleRouteContacts, boolean html) {
        super("Reachability summary — records needing attention");
        this.staleContacts = staleContacts;
        this.singleRouteContacts = singleRouteContacts;
        this.html = html;
    }

    @Override
    public String renderContent() {
        return html ? renderHtml() : renderText();
    }

    private String renderText() {
        StringBuilder text = new StringBuilder();
        text.append("RelayBook reachability summary\r\n");
        text.append("Generated: ").append(java.time.LocalDate.now()).append("\r\n");
        text.append("The score measures saved route coverage and record freshness; it is not a chance of a reply.\r\n\r\n");
        text.append("Stale records (").append(staleContacts.size()).append(")\r\n");
        text.append("-----------------------------\r\n");
        if (staleContacts.isEmpty()) {
            text.append("No record is past its recheck window.\r\n");
        }
        for (Contact contact : staleContacts) {
            text.append("- ").append(contact.getFullName()).append(" (").append(contact.getTypeLabel())
                    .append(", recheck every ").append(contact.getStaleAfterDays()).append(" days)\r\n");
        }
        text.append("\r\nSingle-route records (").append(singleRouteContacts.size()).append(")\r\n");
        text.append("---------------------------------\r\n");
        if (singleRouteContacts.isEmpty()) {
            text.append("No single-route records.\r\n");
        }
        for (Contact contact : singleRouteContacts) {
            text.append("- ").append(contact.getFullName()).append(" (").append(contact.getTypeLabel()).append(")\r\n");
        }
        return text.toString();
    }

    private String renderHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>RelayBook reachability summary</title></head><body>")
                .append("<h1>RelayBook reachability summary</h1>")
                .append("<p>Generated: ").append(java.time.LocalDate.now()).append("</p>")
                .append("<p>The score measures saved route coverage and record freshness; it is not a chance of a reply.</p>")
                .append("<h2>Stale records (").append(staleContacts.size()).append(")</h2><ul>");
        for (Contact contact : staleContacts) {
            html.append("<li>").append(escapeHtml(contact.getFullName())).append(" — ")
                    .append(escapeHtml(contact.getTypeLabel())).append(", recheck every ")
                    .append(contact.getStaleAfterDays()).append(" days</li>");
        }
        html.append("</ul><h2>Single-route records (").append(singleRouteContacts.size()).append(")</h2><ul>");
        for (Contact contact : singleRouteContacts) {
            html.append("<li>").append(escapeHtml(contact.getFullName())).append(" — ")
                    .append(escapeHtml(contact.getTypeLabel())).append("</li>");
        }
        return html.append("</ul></body></html>").toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public boolean isValid() {
        return staleContacts != null && singleRouteContacts != null;
    }
}
