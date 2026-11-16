package com.relaybook.service;

import com.relaybook.model.Contact;

import java.util.List;

/**
 * Optional background sweep: a daemon {@link java.util.concurrent.ScheduledExecutorService}
 * periodically recomputes the stale list and notifies the listeners
 * ({@code onReminderSweep}). Started once by the application and stopped
 * cleanly on shutdown so no thread outlives the process.
 */
public final class ReminderScheduler {
    private final ContactService service;
    private final long periodMinutes;
    private java.util.concurrent.ScheduledExecutorService executor;

    public ReminderScheduler(ContactService service, long periodMinutes) {
        this.service = service;
        this.periodMinutes = Math.max(1, periodMinutes);
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "relaybook-reminders");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::sweep, periodMinutes, periodMinutes,
                java.util.concurrent.TimeUnit.MINUTES);
    }

    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("Reminder scheduler did not stop cleanly.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    /** One sweep: recompute the stale list and notify the listeners. */
    public void sweep() {
        try {
            List<Contact> stale = service.search(null, null, true);
            for (com.relaybook.service.ContactChangeListener listener : service.getListeners()) {
                listener.onReminderSweep(stale);
            }
        } catch (RuntimeException ex) {
            System.err.println("Reminder sweep failed: " + ex.getMessage());
        }
    }
}
