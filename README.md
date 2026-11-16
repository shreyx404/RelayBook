# RelayBook — a reachability-first digital address book

**Course:** OOP in Java
**Stack:** Java 11, Swing, JDBC/MySQL, built-in Java HTTP server, HTML/CSS/vanilla JavaScript

> A contact book that does not stop at *who is this?* It also asks *which saved route should I try next, when was that exact route last checked, and is now a good time?*

## 1. Project idea and the gap it targets

A conventional address book is very good at storing names, phone numbers, and email addresses. A common day-to-day gap is that the data is static: a number may have changed, there may be no fallback route, and the list does not capture a useful time window or — critically — *which* route was checked *when*.

**RelayBook's differentiator** is a small, explainable "reachability layer" over an address book:

- stores **routes as first-class records** — phone, email, backup phone, WhatsApp, Telegram — each with its own verification date and optional label;
- stores best time/time zone (with a structured, parseable window) and shows *the contact's local time right now*;
- selects the next route by comparing **each route's own freshness** ("Email was verified 20 days ago, phone 300 days ago: try email first");
- shows a transparent 0–100 **coverage/freshness indicator** whose thresholds depend on the contact type (emergency records are held to a stricter standard);
- exports privacy-aware documents: vCard with consent-gated routes, CSV directory (identity-only unless the user chooses a full local backup), full JSON backup, and a plain-text reachability summary;
- imports vCards with duplicate detection;
- has both a Java Swing desktop client and a same-data browser page served by the Java application, and changes made in either are visible in the other **immediately** (Observer pattern).

This is a project-level design contribution, **not a claim that nobody in the world has built similar features**. For a strong submission, validate the gap with a short survey of classmates/family and compare a few address-book apps; describe the evidence you actually collect.

### What is deliberately not claimed

The score is a heuristic for **saved route coverage and record freshness**. It is not a probability that a person will answer. RelayBook does not send messages, verify phone/email ownership, or guarantee delivery.

## 2. Main features

1. Add, list, edit, search, sort, and delete people, organizations, and emergency contacts.
2. Save any number of routes (per channel: phone, email, backup phone, WhatsApp, Telegram) with per-route "last checked" dates and labels.
3. Get a suggested fallback route and a reason that quotes each route's own verification age.
4. "Is now a good time?" — the report shows the contact's local time and whether their saved window is open.
5. Quick actions: "Mark checked today" in Swing and the web pages (one endpoint, no field re-PUT).
6. Directory statistics in both UIs: total, stale, single-route, ready, emergency contacts missing a second route.
7. Import vCards with duplicate detection; export privacy-aware CSV, full JSON backup, reachability summary, and consent-gated vCards.
8. Optional reminder scheduler (daemon thread) that recomputes the stale list periodically.
9. Run in quick in-memory demo mode, or persist contacts in MySQL through JDBC (contact + routes in one transaction).
10. Basic local-web protection: state-changing API calls from a foreign Origin/Host are rejected (403).
11. Run the required syllabus Bank Account / `Scanner` exercise with `--bank-demo`.

## 3. OOP syllabus coverage

| Syllabus item | RelayBook implementation |
|---|---|
| Bank account class, constructor, deposit, withdraw, change name, fee, `toString()`, `Scanner` | `syllabus/BankAccount.java`, `syllabus/BankAccountDemo.java`; launch using `--bank-demo`. Kept as a separate syllabus exercise rather than forcing financial behavior into an address-book domain. |
| Inheritance, `super`, `this`, `static`, `final`, polymorphism | `Contact` (abstract) → `PersonContact` / `OrganizationContact`; `EmergencyContact extends PersonContact` (multilevel). Subtypes override `getContactType()`, `getStaleAfterDays()`, `getMinimumRoutes()`, `describe()`. `ContactFactory` and the builder create subtypes; repositories/UIs process a `List<Contact>` polymorphically. Static instance counter, final ID/getter. |
| Abstract class, interface, multiple interfaces | `document/Document` (abstract, Template Method `export()`); `Exportable`, `Validatable`, `PrivacyLabelled`, `ShareableDocument`; `ContactCardDocument extends Document implements ShareableDocument, PrivacyLabelled`; `service/RoutingPolicy` (Strategy); `service/ContactChangeListener` (Observer); `repository/ContactRepository`. |
| Builder pattern | `Contact.Builder` — fluent, validates in `build()`, `toBuilder()` for copies; subclass constructors take the builder (no 13-argument chains). |
| Strategy pattern | `DefaultRoutingPolicy` / `StrictRoutingPolicy` selected per contact type; all point values are named constants in `RoutingPolicy`. |
| Observer pattern | `ContactService` notifies `ContactChangeListener`s after every successful change; the Swing table refreshes automatically, including for web-API changes. |
| Template Method | `Document.export()` is `final`: header + `renderContent()` + footer hooks. |
| Custom exceptions | `exception/RelayBookException` (abstract, unchecked) → `ValidationException` (400), `ContactNotFoundException` (404), `DuplicateContactException` (409); `repository/DataAccessException` joins the hierarchy (500). |
| Generics | `service/Result<T>` (success value or error) used by the vCard importer; generic collections throughout. |
| Collections, streams, Comparable/Comparator | `Contact implements Comparable<Contact>`; comparator constants and `SortOption` (name/score/last-verified/type); `ContactService.search(...)` is a stream pipeline shared by Swing and the API. |
| Concurrency | `CopyOnWriteArrayList` listener list; daemon `ScheduledExecutorService` reminder sweep with clean start/stop; `SwingWorker` for all repository/file I/O; thread-safe in-memory store. |
| MySQL/Oracle JDBC CRUD | `repository/MySqlContactRepository.java`: prepared statements, transactions spanning `contacts` + `contact_methods`, safe migration from legacy flat columns. |
| Swing GUI and event handling | `ui/AddressBookFrame.java`: File/Help menus, action listeners, table selection listener, route editor table, stats strip, `SwingWorker` usage. |
| Web integration (extra) | `web/WebServer.java`: REST API, contact list, check-ins, person detail pages, import/export endpoints, `/api/stats`, same-origin guard. No CDN, works offline. |

### Inheritance sketch

```text
                    Contact (abstract, Comparable)
                   /                  \
       PersonContact              OrganizationContact
             |
      EmergencyContact        (each subtype defines its own freshness rules)

Document (abstract, Template Method final export())
       |-- ContactCardDocument   (implements ShareableDocument + PrivacyLabelled)
       |-- CsvDirectoryDocument  (Exportable + Validatable + PrivacyLabelled)
       |-- JsonBackupDocument    (Exportable + Validatable)
       `-- ReachabilityReportDocument (Exportable + Validatable)

RoutingPolicy (Strategy)          ContactChangeListener (Observer)
       |-- DefaultRoutingPolicy          ^ (Swing frame; reminder scheduler)
       `-- StrictRoutingPolicy           |
ContactService ---------------------------+
```

## 4. Architecture

```text
Swing UI ─────────┐
                  ├── ContactService ── ContactRepository ── In-memory demo store
Browser UI ─ API ─┘         │                 └── MySQL JDBC store (contacts + contact_methods)
                            ├── ReachabilityAdvisor → RoutingPolicy (default / strict)
                            ├── ContactChangeListener (Observer)
                            └── Document subclasses (vCard, CSV, JSON backup, summary)
```

The Swing and browser clients use the same `ContactService`, so creating a contact in one is visible in the other without a manual refresh. MySQL is selected when `DB_URL` is set; otherwise the app uses a thread-safe in-memory store with synthetic examples. The in-memory examples disappear when the app exits.

## 5. Run the project

### Requirements

- JDK 11 or newer
- Maven 3.8+ recommended
- MySQL 8+ only if you want persistent DB mode

### Quick demo (no database needed)

From this directory:

```bash
mvn exec:java
```

This opens the Swing window and starts the browser page at `http://127.0.0.1:8080`. For a browser-only run on a headless machine:

```bash
mvn exec:java -Dexec.args="--web-only"
```

For the syllabus bank-account exercise:

```bash
mvn exec:java -Dexec.args="--bank-demo"
```

If port 8080 is taken, set `PORT=8090`. `REMINDER_MINUTES` (default 60) controls the background stale sweep; set it to a small value during a demo to see `onReminderSweep` fire.

### MySQL mode

1. Create the schema (or let the app create the tables):

   ```bash
   mysql -u root -p < database/schema.sql
   ```

2. Set your connection values. Do not commit real credentials:

   ```bash
   export DB_URL='jdbc:mysql://127.0.0.1:3306/relaybook?serverTimezone=UTC&useSSL=false'
   export DB_USER='root'
   export DB_PASSWORD='your-local-password'
   ```

3. Run with Maven so MySQL Connector/J is on the runtime classpath:

   ```bash
   mvn exec:java
   ```

When `DB_URL` is present, the application attempts MySQL and fails with an actionable error if the connection or driver is not available. It does **not** silently switch to memory mode.

**Upgrading from the v1 flat schema:** on first start against an old database the repository detects the legacy `phone`/`email`/`alternate_phone` columns, copies them into `contact_methods` (each route inherits the old record-level date) inside a transaction, and then removes the legacy columns. The migration runs once and is guarded by a column whitelist. It was verified end-to-end against MariaDB 11.8 (simulated v1 table → migrated → row readable through the API). Note: `database/schema.sql` uses the MySQL 8 collation `utf8mb4_0900_ai_ci`; MariaDB does not ship it, so on MariaDB either let the application create the tables itself (it only sets `CHARSET=utf8mb4`) or change the collation line before running the script.

A contact and its routes are always written in **one transaction**; deleting a contact removes its routes through `ON DELETE CASCADE`.

### Compile without Maven (memory mode)

The project has no compile-time third-party dependencies (JUnit is test-scope only). From the project directory:

```bash
mkdir -p out
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 11 --add-modules jdk.httpserver -encoding UTF-8 -d out
cp -R src/main/resources/* out/
java --add-modules jdk.httpserver -cp out com.relaybook.App
```

For MySQL with direct `javac`, also put MySQL Connector/J on the runtime classpath. On Windows, use `;` rather than `:` between classpath entries.

### Tests and smoke checks

```bash
mvn test                 # 91 JUnit 5 tests; no MySQL required
./scripts/smoke-test.sh  # plain javac + dependency-free runtime checks
```

## 6. Database CRUD and browser API

The API is served locally by the Java process:

- `GET /api/contacts` — list (`?q=&type=&stale=&sort=` filters)
- `GET /api/contacts/{id}` — read one
- `POST /api/contacts` — create (form-encoded; per-route entries `route.N.channel/value/verified/label`, legacy flat fields still accepted)
- `PUT /api/contacts/{id}` — update
- `DELETE /api/contacts/{id}` — delete
- `POST /api/contacts/{id}/verify` — mark checked today (`?channel=EMAIL` for one route)
- `GET /api/contacts/{id}/card` — consent-aware vCard download
- `GET /api/stats` — directory counters
- `POST /api/import/vcard` — import (body = vCard text) → per-card summary JSON
- `GET /api/export/csv` (`?full=true` for local backup), `GET /api/export/json`, `GET /api/export/reachability` (`?format=html`)

The HTML pages and API share an origin. **State-changing requests (POST/PUT/DELETE) whose `Origin` or `Host` header does not match the server's own host:port are rejected with 403** — basic CSRF and DNS-rebinding protection. Quick curl checks:

```bash
# allowed (curl sends no Origin; Host matches):
curl -s -X POST http://127.0.0.1:8080/api/contacts -d 'type=PERSON&fullName=Curl+Test&phone=%2B91+10000+00000'
# rejected (cross-origin):
curl -i -X POST http://127.0.0.1:8080/api/contacts -H 'Origin: http://evil.example' -d 'type=PERSON&fullName=X&phone=1'
# rejected (rebound hostname):
curl -i -X DELETE http://127.0.0.1:8080/api/contacts/any-id -H 'Host: evil.example:8080'
```

## 7. Reachability rule (explainable algorithm)

- **Coverage points** (one place: `RoutingPolicy` constants): default 1 route = 25, 2 = 45, 3+ = 60. The strict policy (emergency contacts) scores a below-minimum record at 10.
- **Freshness points** from the latest check-in: ≤90 days = 40; ≤180 = 30; ≤365 = 15; older/never = 0. The strict policy uses the same band edges but harsher values (≤180 = 15, ≤365 = 5). Band edges are exercised by boundary tests at 90/91, 180/181, 365/366 days for every subtype.
- **Staleness** comes from the contact subtype itself: person 180 days, organization 365, emergency 90. A record is stale when its *preferred* route has no date or is past the window.
- **Route choice:** preferred route when it is fresh; otherwise the *freshest alternative by its own verification date*; the rationale names each route's age ("Email was verified 20 days ago, phone 300 days ago: try email first").
- A missing verification date produces a reminder. It does not invent one.

These thresholds are intentionally simple enough to explain in a viva and can be tuned by injecting a custom policy registry into the advisor.

## 8. Privacy and project limitations

- The browser server binds to `127.0.0.1` by default and warns if `HOST` is not loopback. Do not expose it on a public network without adding authentication, HTTPS, authorization, and appropriate data protection. The Origin/Host guard is a local-web hygiene measure, not authentication.
- This prototype does not encrypt the database, authenticate users, audit access, or implement consent revocation workflows. Use fictional/sample data for demos.
- vCard export omits notes, relationship context, preferred time, time zone, and time window. It includes routes only when share consent is set; WhatsApp/Telegram routes use explicit non-standard properties.
- The CSV export is privacy-aware by default (no notes, routes only for consented contacts); the JSON backup and the "full local backup" CSV deliberately include everything and say so.
- The memory repository is volatile; MySQL mode is the persistence option.
- The import keeps imported routes **without** verification dates — imported is not verified.
- For a real app, add migrations tooling, password/session management, database pooling, encryption, and accessibility review.

## 9. Suggested project demonstration

1. Explain the gap: a stale number with no fallback is a real, everyday failure mode.
2. Add an emergency contact with one route and show the type-specific validation message; add the second route and save.
3. Show a stale emergency contact (90-day window) whose phone is old and email is fresh — the advisor quotes both ages and picks the email.
4. Click **Open browser app**, create a contact there, and point at the Swing table updating itself (Observer).
5. Click **Mark checked today** on the web page and show the Swing stats strip changing.
6. Export a vCard with consent off (identity-only) and on (routes included); show `N:`, folded lines, and that notes never appear. Import the file back and show the duplicate skip.
7. Export the CSV in privacy mode, then as a full local backup, and compare.
8. Show `Contact` subtypes for polymorphism, `RoutingPolicy` for Strategy, `ContactService` listeners for Observer, `Document.export()` for Template Method, and the exception→HTTP mapping table.
9. Run `mvn test` and show the boundary tests; run `--bank-demo` for the syllabus exercise.

## 10. Known limitations and next steps

- Imported contacts start unverified (by design); an "imported but not verified" highlight in the check-ins list would help.
- The strict policy's low score for a one-route emergency only appears for rows migrated from the old schema; the builder cannot create such records any more.
- No per-user accounts; single shared directory.
- Future work: encrypted backups, per-field consent, a check-in history table, and a small user study to tune the point values.
