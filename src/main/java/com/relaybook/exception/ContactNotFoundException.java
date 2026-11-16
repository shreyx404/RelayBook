package com.relaybook.exception;

/** The requested contact does not exist; mapped to HTTP 404. */
public class ContactNotFoundException extends RelayBookException {
    public ContactNotFoundException(String id) {
        super("Contact not found: " + id);
    }
}
