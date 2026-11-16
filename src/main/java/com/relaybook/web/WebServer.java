package com.relaybook.web;

import com.relaybook.document.ContactCardDocument;
import com.relaybook.document.CsvDirectoryDocument;
import com.relaybook.document.ImportSummary;
import com.relaybook.document.JsonBackupDocument;
import com.relaybook.document.ReachabilityReportDocument;
import com.relaybook.document.VCardImporter;
import com.relaybook.exception.ContactNotFoundException;
import com.relaybook.exception.DuplicateContactException;
import com.relaybook.exception.RelayBookException;
import com.relaybook.exception.ValidationException;
import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactFactory;
import com.relaybook.service.ContactService;
import com.relaybook.service.ReachabilityAdvisor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local HTTP server that serves the browser UI and REST endpoints over the same origin. */
public final class WebServer {
    private final ContactService service;
    private final HttpServer server;
    private final ExecutorService executor;
    private final String host;
    private final int port;

    public WebServer(ContactService service, String host, int port) throws IOException {
        this.service = service;
        this.host = host;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "relaybook-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/api", this::handleApi);
        server.createContext("/", this::handlePage);
    }

    public void start() {
        server.start();
        System.out.println("RelayBook web page: " + getUrl());
    }

    public void stop() {
        server.stop(1);
        executor.shutdownNow();
    }

    public String getUrl() {
        String displayHost = "0.0.0.0".equals(host) ? "127.0.0.1" : host;
        return "http://" + displayHost + ":" + port;
    }

    private void handlePage(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Json.error("Only GET is supported for website pages."));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("/paper-texture.jpg".equals(path)) {
            serveResource(exchange, "web/paper-texture.jpg", "image/jpeg", "public, max-age=3600");
            return;
        }
        if ("/header-atmosphere.gif".equals(path)) {
            serveResource(exchange, "web/header-atmosphere.gif", "image/gif", "public, max-age=3600");
            return;
        }
        if ("/site.css".equals(path)) {
            serveResource(exchange, "web/site.css", "text/css; charset=utf-8", "public, max-age=3600");
            return;
        }
        if ("/site.js".equals(path)) {
            serveResource(exchange, "web/site.js", "application/javascript; charset=utf-8", "public, max-age=3600");
            return;
        }

        if ("/about".equals(path) || "/about.html".equals(path)
                || "/course".equals(path) || "/course.html".equals(path)) {
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        String pageResource;
        if ("/".equals(path) || "/index.html".equals(path)) {
            pageResource = "web/index.html";
        } else if ("/check-ins".equals(path) || "/check-ins.html".equals(path)) {
            pageResource = "web/check-ins.html";
        } else if (path.startsWith("/person/") && path.length() > "/person/".length()
                && path.indexOf('/', "/person/".length()) < 0) {
            pageResource = "web/person.html";
        } else {
            sendJson(exchange, 404, Json.error("Page not found."));
            return;
        }
        serveResource(exchange, pageResource, "text/html; charset=utf-8", "no-store");
    }

    private void serveResource(HttpExchange exchange, String resource, String contentType,
                               String cacheControl) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                sendJson(exchange, 404, Json.error("Page asset is missing: " + resource));
                return;
            }
            byte[] content = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", cacheControl);
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();
            String collection = "/api/contacts";

            // Basic CSRF / DNS-rebinding protection: state-changing requests must
            // come from this server's own origin (loopback by default).
            if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                if (!isStateChangeAllowed(exchange.getRequestHeaders().getFirst("Origin"),
                        exchange.getRequestHeaders().getFirst("Host"), host, port)) {
                    sendJson(exchange, 403, Json.error("Rejected: this request came from a different origin. "
                            + "Use the page served at " + getUrl() + "."));
                    return;
                }
            }

            if ("/api/import/vcard".equals(path) && "POST".equals(method)) {
                handleVCardImport(exchange);
                return;
            }
            if (path.startsWith("/api/export/") && "GET".equals(method)) {
                handleExport(exchange, path);
                return;
            }
            if ("/api/stats".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, Json.stats(service.stats()));
                return;
            }

            if (collection.equals(path)) {
                if ("GET".equals(method)) {
                    Map<String, String> query = readQuery(exchange.getRequestURI().getRawQuery());
                    String q = query.get("q");
                    String typeParam = query.get("type");
                    com.relaybook.model.ContactType typeFilter = typeParam == null || typeParam.isEmpty()
                            ? null : com.relaybook.model.ContactType.from(typeParam);
                    boolean onlyStale = Boolean.parseBoolean(query.getOrDefault("stale", "false"));
                    java.util.List<Contact> result = q == null && typeFilter == null && !onlyStale
                            ? service.listContacts()
                            : service.search(q, typeFilter, onlyStale);
                    com.relaybook.service.SortOption sort = com.relaybook.service.SortOption.from(query.get("sort"));
                    sendJson(exchange, 200, Json.contacts(sort.apply(result, service.getAdvisor()), service.getAdvisor()));
                    return;
                }
                if ("POST".equals(method)) {
                    Map<String, String> form = readForm(exchange);
                    Contact created = ContactFactory.fromForm(UUID.randomUUID().toString(), form);
                    service.create(created);
                    sendJson(exchange, 201, Json.contact(created, service.analyze(created)));
                    return;
                }
                sendJson(exchange, 405, Json.error("Use GET to list contacts or POST to create one."));
                return;
            }

            String prefix = collection + "/";
            if (!path.startsWith(prefix)) {
                sendJson(exchange, 404, Json.error("API endpoint not found."));
                return;
            }
            String remainder = path.substring(prefix.length());
            if (remainder.endsWith("/verify") && "POST".equals(method)) {
                String id = decodePathPart(remainder.substring(0, remainder.length() - "/verify".length()));
                String channel = readQuery(exchange.getRequestURI().getRawQuery()).get("channel");
                Contact verified = channel == null || channel.trim().isEmpty()
                        ? service.markVerified(id)
                        : service.markVerified(id, Channel.from(channel));
                sendJson(exchange, 200, Json.contact(verified, service.analyze(verified)));
                return;
            }
            if (remainder.endsWith("/card") && "GET".equals(method)) {
                String id = decodePathPart(remainder.substring(0, remainder.length() - "/card".length()));
                Contact contact = service.requireContact(id);
                String vCard = new ContactCardDocument(contact).export();
                byte[] body = vCard.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/vcard; charset=utf-8");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"relaybook-contact.vcf\"");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                return;
            }

            if (remainder.contains("/")) {
                sendJson(exchange, 404, Json.error("API endpoint not found."));
                return;
            }
            String id = decodePathPart(remainder);
            if ("GET".equals(method)) {
                Contact contact = service.requireContact(id);
                sendJson(exchange, 200, Json.contact(contact, service.analyze(contact)));
            } else if ("PUT".equals(method)) {
                Map<String, String> form = readForm(exchange);
                Contact updated = ContactFactory.fromForm(id, form);
                service.update(updated);
                sendJson(exchange, 200, Json.contact(updated, service.analyze(updated)));
            } else if ("DELETE".equals(method)) {
                service.delete(id);
                sendEmpty(exchange, 204);
            } else {
                sendJson(exchange, 405, Json.error("Use GET, PUT, or DELETE for a contact."));
            }
        } catch (ValidationException ex) {
            sendJson(exchange, 400, Json.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            // Non-domain argument problems (bad path shape, bad form encoding).
            sendJson(exchange, 400, Json.error(ex.getMessage()));
        } catch (ContactNotFoundException ex) {
            sendJson(exchange, 404, Json.error(ex.getMessage()));
        } catch (DuplicateContactException ex) {
            sendJson(exchange, 409, Json.error(ex.getMessage()));
        } catch (RelayBookException ex) {
            // Any other RelayBook failure (e.g. DataAccessException) keeps its safe, user-facing message.
            sendJson(exchange, 500, Json.error(ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            sendJson(exchange, 500, Json.error("The request could not be completed. Check the application log."));
        }
    }

    /** Imports a vCard request body and answers with the per-card summary. */
    private void handleVCardImport(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.length() > 512_000) {
            throw new ValidationException("The uploaded vCard file is too large.");
        }
        ImportSummary summary = new VCardImporter(service).importFromText(body);
        StringBuilder json = new StringBuilder("{");
        json.append("\"importedCount\":").append(summary.getImportedCount()).append(',');
        json.append("\"skippedDuplicatesCount\":").append(summary.getSkippedDuplicatesCount()).append(',');
        json.append("\"rejectedCount\":").append(summary.getRejectedCount()).append(',');
        json.append("\"summary\":").append(Json.quote(summary.summaryText())).append(',');
        json.append("\"imported\":").append(Json.stringArray(summary.getImportedNames())).append(',');
        json.append("\"skippedDuplicates\":").append(Json.stringArray(summary.getSkippedDuplicates())).append(',');
        json.append("\"rejected\":").append(Json.stringArray(summary.getRejected()));
        sendJson(exchange, 200, json.append('}').toString());
    }

    /** Directory exports: CSV (privacy-aware or full backup), JSON backup, reachability summary. */
    private void handleExport(HttpExchange exchange, String path) throws IOException {
        Map<String, String> query = readQuery(exchange.getRequestURI().getRawQuery());
        if ("/api/export/csv".equals(path)) {
            boolean fullBackup = Boolean.parseBoolean(query.getOrDefault("full", "false"));
            CsvDirectoryDocument document = new CsvDirectoryDocument(service.listContacts(), fullBackup);
            sendAttachment(exchange, "text/csv; charset=utf-8", "relaybook-directory" + (fullBackup ? "-full-backup" : "") + ".csv",
                    document.export());
        } else if ("/api/export/json".equals(path)) {
            JsonBackupDocument document = new JsonBackupDocument(service.listContacts());
            sendAttachment(exchange, "application/json; charset=utf-8", "relaybook-backup.json", document.export());
        } else if ("/api/export/reachability".equals(path)) {
            boolean html = "html".equalsIgnoreCase(query.getOrDefault("format", "txt"));
            List<Contact> contacts = service.listContacts();
            List<Contact> stale = new java.util.ArrayList<>();
            List<Contact> singleRoute = new java.util.ArrayList<>();
            for (Contact contact : contacts) {
                String status = service.analyze(contact).getStatus();
                if ("Needs verification".equals(status)) {
                    stale.add(contact);
                } else if ("Single route".equals(status) || "Add a backup route".equals(status)) {
                    singleRoute.add(contact);
                }
            }
            ReachabilityReportDocument document = new ReachabilityReportDocument(stale, singleRoute, html);
            sendAttachment(exchange, html ? "text/html; charset=utf-8" : "text/plain; charset=utf-8",
                    "relaybook-reachability." + (html ? "html" : "txt"), document.export());
        } else {
            sendJson(exchange, 404, Json.error("Unknown export: " + path));
        }
    }

    private Map<String, String> readQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int split = pair.indexOf('=');
            String key = split < 0 ? pair : pair.substring(0, split);
            String value = split < 0 ? "" : pair.substring(split + 1);
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private void sendAttachment(HttpExchange exchange, String contentType, String filename, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /**
     * Same-origin decision for state-changing requests (static and side-effect
     * free so it can be unit tested).
     *
     * <p>Rules: the Host header must name this server's host and port (this
     * blocks DNS rebinding, where a foreign hostname resolves to 127.0.0.1);
     * if an Origin header is present (browsers send it on cross-origin and
     * most same-origin POSTs), its authority must equal the Host header (this
     * blocks cross-site request forgery). Requests without an Origin header
     * (curl, same-site tools) are allowed when the Host matches.</p>
     */
    static boolean isStateChangeAllowed(String originHeader, String hostHeader, String serverHost, int serverPort) {
        if (hostHeader == null || hostHeader.trim().isEmpty()) {
            return false; // HTTP/1.1 requires Host; absence is suspicious.
        }
        String hostValue = hostHeader.trim().toLowerCase(Locale.ROOT);
        int colon = hostValue.lastIndexOf(':');
        String requestHost;
        int requestPort;
        try {
            if (colon < 0) {
                requestHost = hostValue;
                requestPort = 80;
            } else {
                requestHost = hostValue.substring(0, colon);
                requestPort = Integer.parseInt(hostValue.substring(colon + 1));
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        String configuredHost = serverHost == null ? "" : serverHost.trim().toLowerCase(Locale.ROOT);
        boolean hostMatches = requestHost.equals(configuredHost)
                || (configuredHost.equals("0.0.0.0")
                        && (requestHost.equals("127.0.0.1") || requestHost.equals("localhost")));
        if (!hostMatches || requestPort != serverPort) {
            return false;
        }
        if (originHeader == null || originHeader.trim().isEmpty() || "null".equalsIgnoreCase(originHeader.trim())) {
            return true; // No Origin header (curl, tests); the Host check above already matched.
        }
        String origin = originHeader.trim().toLowerCase(Locale.ROOT);
        if (!origin.startsWith("http://")) {
            return false; // An https origin can never share our plain-HTTP origin.
        }
        return origin.substring("http://".length()).equals(hostValue);
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.length() > 32_000) {
            throw new ValidationException("Form is too large.");
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (body.isEmpty()) {
            return values;
        }
        for (String pair : body.split("&")) {
            int split = pair.indexOf('=');
            String key = split < 0 ? pair : pair.substring(0, split);
            String value = split < 0 ? "" : pair.substring(split + 1);
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private String decodePathPart(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void sendJson(HttpExchange exchange, int status, String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
