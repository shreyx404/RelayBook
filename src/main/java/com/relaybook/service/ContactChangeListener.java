package com.relaybook.service;

import com.relaybook.model.Contact;

import java.util.List;

/**
 * Observer: objects that want to know when the directory changes register
 * with {@link ContactService}. The Swing table is a listener, so a contact
 * created through the browser API appears in the desktop app immediately.
 *
 * <p>All methods are default methods so a listener implements only what it
 * cares about. Implementations that touch Swing must schedule their work on
 * the event dispatch thread with {@code SwingUtilities.invokeLater}.</p>
 */
public interface ContactChangeListener {

    /** Called after a contact was successfully created. */
    default void onCreated(Contact contact) { }

    /** Called after a contact was successfully updated. */
    default void onUpdated(Contact contact) { }

    /** Called after a contact was successfully deleted. */
    default void onDeleted(Contact contact) { }

    /**
     * Called after the {@code ReminderScheduler} recomputed the stale list
     * (periodic sweep); the payload is the contacts currently stale.
     */
    default void onReminderSweep(List<Contact> staleContacts) { }
}
