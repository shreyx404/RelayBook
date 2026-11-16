package com.relaybook.document;

import com.relaybook.model.Contact;
import com.relaybook.model.ContactMethod;
import com.relaybook.web.Json;

import java.util.List;

/**
 * Full JSON backup of the directory: every field, including private notes and
 * every route with its own verification date. This document is intentionally
 * NOT privacy-filtered; the UI only offers it as an explicit local backup.
 */
public final class JsonBackupDocument extends Document implements Exportable, Validatable {
    private final List<Contact> contacts;

    public JsonBackupDocument(List<Contact> contacts) {
        super("RelayBook full backup (JSON)");
        this.contacts = contacts;
    }

    @Override
    public String renderContent() {
        StringBuilder json = new StringBuilder("{");
        json.append("\"exportedAt\":\"").append(java.time.LocalDate.now()).append("\",");
        json.append("\"kind\":\"relaybook-full-backup\",");
        json.append("\"contacts\":[");
        for (int i = 0; i < contacts.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(contactJson(contacts.get(i)));
        }
        return json.append("]}").toString();
    }

    private String contactJson(Contact contact) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"id\":").append(Json.quote(contact.getId())).append(',');
        json.append("\"type\":").append(Json.quote(contact.getContactType().name())).append(',');
        json.append("\"fullName\":").append(Json.quote(contact.getFullName())).append(',');
        json.append("\"organization\":").append(Json.quote(contact.getOrganization())).append(',');
        json.append("\"relationship\":").append(Json.quote(contact.getRelationship())).append(',');
        json.append("\"preferredChannel\":").append(Json.quote(
                contact.getPreferredChannel() == null ? null : contact.getPreferredChannel().name())).append(',');
        json.append("\"bestTime\":").append(Json.quote(contact.getBestTime())).append(',');
        json.append("\"reachWindow\":").append(Json.quote(
                contact.getTimeWindow() == null ? null : contact.getTimeWindow().toString())).append(',');
        json.append("\"timeZone\":").append(Json.quote(contact.getTimeZone())).append(',');
        json.append("\"notes\":").append(Json.quote(contact.getNotes())).append(',');
        json.append("\"shareConsent\":").append(contact.hasShareConsent()).append(',');
        json.append("\"routes\":[");
        List<ContactMethod> routes = contact.getRoutes();
        for (int i = 0; i < routes.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            ContactMethod route = routes.get(i);
            json.append("{\"channel\":").append(Json.quote(route.getChannel().name())).append(',');
            json.append("\"value\":").append(Json.quote(route.getValue())).append(',');
            json.append("\"lastVerified\":").append(Json.quote(
                    route.getLastVerified() == null ? null : route.getLastVerified().toString())).append(',');
            json.append("\"label\":").append(Json.quote(route.getLabel())).append('}');
        }
        return json.append("]}").toString();
    }

    @Override
    public boolean isValid() {
        return contacts != null;
    }
}
