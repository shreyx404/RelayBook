package com.relaybook;

import com.relaybook.repository.ContactRepository;
import com.relaybook.repository.InMemoryContactRepository;
import com.relaybook.repository.MySqlContactRepository;
import com.relaybook.service.ContactService;
import com.relaybook.syllabus.BankAccountDemo;
import com.relaybook.ui.AddressBookFrame;
import com.relaybook.web.WebServer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/** Application entry point. Runs the Swing client and its companion local web page. */
public final class App {
    private App() { }

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--bank-demo")) {
            BankAccountDemo.run();
            return;
        }

        ContactRepository repository = createRepository();
        ContactService service = new ContactService(repository);
        com.relaybook.service.ReminderScheduler reminderScheduler =
                new com.relaybook.service.ReminderScheduler(service, integerEnvironment("REMINDER_MINUTES", 60));
        reminderScheduler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(reminderScheduler::stop));

        String host = environment("HOST", "127.0.0.1");
        int port = integerEnvironment("PORT", 8080);
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) {
            System.err.println("WARNING: HOST is '" + host + "'. The RelayBook API has no authentication; "
                    + "anyone who can reach this address can read and change your contacts. "
                    + "Prefer the loopback address unless you understand the risk.");
        }
        WebServer webServer = new WebServer(service, host, port);
        webServer.start();

        boolean webOnly = Arrays.asList(args).contains("--web-only");
        if (webOnly || GraphicsEnvironment.isHeadless()) {
            System.out.println("Web-only mode. Press Ctrl+C to stop the server.");
            new CountDownLatch(1).await();
            return;
        }

        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // The platform default look and feel is fine if Nimbus is unavailable.
        }
        SwingUtilities.invokeLater(() -> new AddressBookFrame(service, webServer, reminderScheduler).setVisible(true));
    }

    private static ContactRepository createRepository() throws IOException {
        String url = System.getenv("DB_URL");
        if (url == null || url.trim().isEmpty()) {
            System.out.println("Storage mode: in-memory demo (records are cleared when the app exits).");
            return new InMemoryContactRepository(true);
        }
        String user = environment("DB_USER", "root");
        String password = environment("DB_PASSWORD", "");
        System.out.println("Storage mode: MySQL JDBC");
        return new MySqlContactRepository(url, user, password);
    }

    private static String environment(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int integerEnvironment(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1 || parsed > 65535) {
                throw new NumberFormatException("port out of range");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be a TCP port from 1 to 65535.");
        }
    }
}
