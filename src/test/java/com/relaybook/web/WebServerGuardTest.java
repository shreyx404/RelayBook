package com.relaybook.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Same-origin / DNS-rebinding guard for state-changing API requests.
 * Verified additionally by curl checks documented in the README.
 */
class WebServerGuardTest {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    @Test
    void plainRequestsWithMatchingHostHeaderAreAllowed() {
        assertTrue(WebServer.isStateChangeAllowed(null, "127.0.0.1:8080", SERVER_HOST, SERVER_PORT));
        assertTrue(WebServer.isStateChangeAllowed("", "127.0.0.1:8080", SERVER_HOST, SERVER_PORT));
        assertTrue(WebServer.isStateChangeAllowed(
                "http://127.0.0.1:8080", "127.0.0.1:8080", SERVER_HOST, SERVER_PORT));
    }

    @Test
    void crossOriginPostsAreRejected() {
        assertFalse(WebServer.isStateChangeAllowed(
                "http://evil.example:8080", "127.0.0.1:8080", SERVER_HOST, SERVER_PORT));
        assertFalse(WebServer.isStateChangeAllowed(
                "http://127.0.0.1:9999", "127.0.0.1:8080", SERVER_HOST, SERVER_PORT));
    }

    @Test
    void reboundHostnamesAreRejected() {
        // DNS rebinding: evil.example resolves to 127.0.0.1 but sends its own hostname.
        assertFalse(WebServer.isStateChangeAllowed(null, "evil.example:8080", SERVER_HOST, SERVER_PORT));
        assertFalse(WebServer.isStateChangeAllowed("http://evil.example:8080", "evil.example:8080",
                SERVER_HOST, SERVER_PORT));
    }

    @Test
    void wrongPortOrHostHeaderIsRejected() {
        assertFalse(WebServer.isStateChangeAllowed(null, "127.0.0.1:9090", SERVER_HOST, SERVER_PORT));
        assertFalse(WebServer.isStateChangeAllowed(null, "192.168.1.5:8080", SERVER_HOST, SERVER_PORT));
        assertFalse(WebServer.isStateChangeAllowed(null, null, SERVER_HOST, SERVER_PORT));
    }

    @Test
    void httpsOriginCanNeverMatchThePlainHttpServer() {
        assertFalse(WebServer.isStateChangeAllowed(
                "https://127.0.0.1:8080", "127.0.0.1:8080", SERVER_HOST, SERVER_PORT));
    }

    @Test
    void wildcardBindingAcceptsLoopbackHostHeaders() {
        assertTrue(WebServer.isStateChangeAllowed(null, "127.0.0.1:8080", "0.0.0.0", SERVER_PORT));
        assertTrue(WebServer.isStateChangeAllowed(null, "localhost:8080", "0.0.0.0", SERVER_PORT));
        assertFalse(WebServer.isStateChangeAllowed(null, "evil.example:8080", "0.0.0.0", SERVER_PORT));
    }
}
