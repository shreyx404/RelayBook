# RelayBook changelog (v1.0.0 → v2.0.0)

Every change is grouped by the tier it belongs to, names the OOP concept it
demonstrates, and gives a one-line talking point you can use in a viva.
All items were implemented and compiled; what was actually executed is listed
at the bottom ("Verified by running").

## Tier 1 — core OOP redesign

### A. Behaviour that differs by subtype (polymorphism)

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| Overridable `getStaleAfterDays()`, `getMinimumRoutes()`, `describe()` added to `Contact`; Person 180 days / 1 route, Organization 365 / 1, Emergency 90 / 2 | `model/Contact.java`, `model/PersonContact.java`, `model/OrganizationContact.java`, `model/EmergencyContact.java` | Dynamic dispatch: one call site, three behaviours | "The advisor never asks *what type is this?* — it calls `contact.getStaleAfterDays()` and the subtype answers, so adding a fourth type needs no new if/else." |
| Validator produces type-specific errors ("Emergency contacts need at least two routes…") from the subtype's own minimum | `model/ContactValidator.java` | Polymorphism applied to validation rules | "The rule and its wording travel with the subtype, not with the validator." |
| Advisor reads the staleness threshold from the contact instead of hard-coded 90/180/365 | `service/ReachabilityAdvisor.java` | Information expert / polymorphism | "A record knows its own recheck window; the advisor only does arithmetic." |

### B. Per-route verification

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| New immutable value object `ContactMethod` (channel, value, last-verified date, optional label) with copy-on-write helpers | `model/ContactMethod.java` | Encapsulation + immutability | "Routes are immutable, so a stale cache can never show a half-updated number; changes produce new instances." |
| `Contact` stores routes as an immutable `EnumMap`-backed collection; legacy `getPhone()/getEmail()/getAlternatePhone()` kept as derived views; record-level `getLastVerified()` = latest route date | `model/Contact.java` | Encapsulation with backward compatibility | "The flat fields became views over routes, so 20-year-old callers keep compiling while storage is normalized." |
| `Channel` extended with `WHATSAPP` and `TELEGRAM` (plus `isDialable()`) | `model/Channel.java` | Enum extensibility | "One new enum constant makes the channel available in forms, advisor, storage and vCard without touching any consumer." |
| MySQL: normalized `contact_methods` table (FK `ON DELETE CASCADE`, unique per channel), contact+routes written in one transaction, prepared statements everywhere, transactional migration from the old `phone`/`email`/`alternate_phone` columns (copy then drop, guarded by a column whitelist) | `repository/MySqlContactRepository.java`, `database/schema.sql` | JDBC transactions + safe schema evolution | "A contact and its routes commit together or not at all; the migration is idempotent because it is driven by the column list it finds." |
| In-memory repository seeds via the same builder the UIs use | `repository/InMemoryContactRepository.java` | Single construction path | "Demo data obeys the same rules as user data because there is only one way to build a contact." |
| JSON exposes `routes[]` per route; web form got a route editor (channel, value, verified date, label) and the Swing form a route table editor; person page shows per-route freshness | `web/Json.java`, `src/main/resources/web/*`, `ui/AddressBookFrame.java` | Separation of concerns | "All three front-ends edit the same value objects; none of them know how storage works." |

### C. Strategy pattern for scoring

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| `RoutingPolicy` interface with all point values as named constants in one place | `service/RoutingPolicy.java` | Strategy interface as contract | "Both policies and every future one must draw from the same constant pool, so scores stay comparable." |
| `AbstractRoutingPolicy` holds the shared algorithm with two hook methods (`coveragePoints`, `freshnessPoints`); `DefaultRoutingPolicy` and `StrictRoutingPolicy` differ only in the hooks | `service/AbstractRoutingPolicy.java`, `service/DefaultRoutingPolicy.java`, `service/StrictRoutingPolicy.java` | Strategy + hook methods | "The skeleton (route choice, rationale, status) is written once; a policy is literally just two point functions." |
| Advisor keeps a policy per contact type and delegates; custom registries can be injected | `service/ReachabilityAdvisor.java`, `service/ContactService.java` | Context class of the Strategy pattern | "Emergency records get the strict policy without the advisor knowing a single point value." |

### D. Builder pattern

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| Fluent `Contact.Builder` with `build()` that validates; subclasses take the builder (13-argument constructor chain removed); `toBuilder()` for copies; `verifyAllRoutes`/`verifyRoute` helpers | `model/Contact.java`, subclasses, `model/ContactFactory.java` | Builder + creational consistency | "Invalid contacts cannot exist: construction and validation are one atomic step." |
| `ContactService.markVerified(id)` and per-route `markVerified(id, channel)` implemented with `toBuilder()` | `service/ContactService.java` | Immutable updates through a builder | "Marking a check-in is a copy with a changed date — never a mutation of shared state." |

### E. Observer pattern

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| `ContactChangeListener` (default methods `onCreated/onUpdated/onDeleted/onReminderSweep`); `ContactService` keeps a `CopyOnWriteArrayList` and notifies after every successful change; Swing frame registers and refreshes via `SwingUtilities.invokeLater` | `service/ContactChangeListener.java`, `service/ContactService.java`, `ui/AddressBookFrame.java` | Observer; thread-safe listener list; EDT discipline | "Create a contact in the browser and the Swing table moves by itself — the service is the subject, both UIs are just observers." |

### F. Custom exception hierarchy

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| New `exception` package: abstract unchecked `RelayBookException` → `ValidationException`, `ContactNotFoundException`, `DuplicateContactException`; `DataAccessException` joins the hierarchy; all domain `IllegalArgumentException`/`NoSuchElementException` replaced | `exception/*`, `repository/DataAccessException.java`, model/service call sites | Exception hierarchies mirror domain hierarchies | "The web layer maps a whole family of failures to 400/404/409/500 with one ordered catch chain." |
| Web mapping: Validation→400, NotFound→404, Duplicate→409, other RelayBook→500 with safe message | `web/WebServer.java` | Separation of transport from domain | "Domain code never mentions HTTP; the transport never invents domain rules." |
| Duplicate detection on create (same name + one shared route value) | `service/ContactService.java` | Reusable domain rule | "The importer reuses exactly this rule, so the web API and file import can never disagree about duplicates." |

## Tier 2 — features

### G. "Is now a good time?"

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| `TimeWindow` value object: parser for "Weekdays 18:00-21:00", day lists, bare ranges, "any time", overnight windows; `isOpenAt(ZonedDateTime)`; strict parse throws, `tryParse` returns `Optional` | `model/TimeWindow.java` | Value object with total and partial failure modes | "One parser, two doors: strict for tests and tools, Optional for free-text fields." |
| Time zone validated with `ZoneId.of(...)` → `ValidationException`; structured window stored alongside the free-text best time | `model/ContactValidator.java`, `model/ContactFactory.java` | Defensive parsing at the boundary | "The free text stays free; the structure is only kept when it really parses." |
| `ReachabilityReport` gains the contact's local time and a good-time-now flag; shown in Swing advice, contact cards, person page | `service/ReachabilityReport.java`, `service/AbstractRoutingPolicy.java`, UIs | Report object as a read model | "The UI never computes reachability; it renders the report the policy produced." |

### H. Import/export and more Document subclasses

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| Template Method: `Document.export()` is final (header → `renderContent()` → footer); vCard moved onto the hooks | `document/Document.java`, `document/ContactCardDocument.java` | Template Method | "Every document is assembled the same way; a subclass can only fill the blanks." |
| `CsvDirectoryDocument` (privacy-aware; full-backup mode explicit), `JsonBackupDocument` (full), `ReachabilityReportDocument` (txt/html) — all `Exportable`/`Validatable` | `document/*` | Polymorphism across document types | "The export menu offers three documents without a single instanceof — they all answer `export()`." |
| `VCardImporter` with per-card `Result<Contact>` outcomes, duplicate skip via the service rule, `ImportSummary` result object | `document/VCardImporter.java`, `document/ImportSummary.java`, `service/Result.java` | Generic result type; fail-soft bulk operations | "One broken card costs one rejected line, not the whole file." |
| API: `POST /api/import/vcard`, `GET /api/export/csv[?full=]`, `/api/export/json`, `/api/export/reachability[?format=]`; Swing File menu items with `JFileChooser` | `web/WebServer.java`, `ui/AddressBookFrame.java` | Same service, two transports | "Menu items and curl hit the same domain methods; the UI adds nothing but dialogs." |

### I. Quick actions and dashboard

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| "Mark checked today" button in Swing; `POST /api/contacts/{id}/verify` (+ `?channel=`) endpoint; check-ins and person pages now call `/verify` instead of re-PUTting every field | `ui/AddressBookFrame.java`, `web/WebServer.java`, `src/main/resources/web/check-ins.html`, `person.html` | Intent-revealing API (command over CRUD) | "The check-in is one verb, one round trip — the client no longer resends the whole record and cannot clobber routes by accident." |
| `DirectoryStats` + `GET /api/stats`; dashboard strip on index.html; stats strip in Swing | `service/DirectoryStats.java`, `service/ContactService.java`, `web/Json.java`, both UIs | Shared read model | "Swing and the browser show identical numbers because both render the same `DirectoryStats`." |

### J. Collections, generics, streams, concurrency

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| `Contact implements Comparable<Contact>` (name, case-insensitive) + comparator constants `BY_NAME`, `BY_LAST_VERIFIED`, `BY_TYPE` | `model/Contact.java` | Natural ordering + `Comparator` constants | "Natural order is the one everyone agrees on; comparators cover the opinions." |
| `SortOption` enum (name/score/last-verified/type); `?sort=` API parameter; Swing sort combo | `service/SortOption.java` | Enum with behaviour | "A new sort is one enum constant, not a new branch in every UI." |
| Stream-based `ContactService.search(query, typeFilter, onlyStale)` shared by Swing and API (`?q=&type=&stale=`) | `service/ContactService.java`, `web/WebServer.java` | Streams + one search, two clients | "The browser filter and the API filter can never drift apart — it is literally the same pipeline." |
| Generic `Result<T>` used by the vCard importer | `service/Result.java` | Generics for either/or returns | "The type system forces the caller to look before calling `getValue()`." |
| All Swing repository/file operations run in `SwingWorker`s; results posted back on the EDT | `ui/AddressBookFrame.java` | Concurrency on the EDT | "JDBC waits in a worker thread; the event dispatch thread only paints." |
| `ReminderScheduler`: daemon `ScheduledExecutorService`, periodic stale sweep, notifies listeners, clean `stop()` | `service/ReminderScheduler.java`, `App.java` | Executor lifecycle | "The daemon never blocks JVM exit, and stop() awaits termination instead of abandoning threads." |

## Tier 3 — quality and polish

### K. Tests

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| JUnit 5 + surefire added (test scope only); 91 tests across model/service/repository/document/web | `pom.xml`, `src/test/java/**` | Testable design pays off | "Because behaviour lives in policies and value objects, every boundary test needs no server and no database." |
| Boundary tests at 90/91, 180/181, 365/366 days for each subtype and both policies; defensive one-route emergency scoring | `service/RoutingPolicyBoundaryTest.java` | Behavioural boundaries | "The band edges are the contract; the tests pin them for both policies." |

### L. vCard correctness

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| Required `N:` property derived from the full name (single token → family slot); `EMAIL;TYPE=INTERNET`; UTF-8-safe 75-octet line folding; CRLF everywhere; proper `\;\,\\n` escaping; notes/relationship/best time/time zone never exported; routes only with consent; WHATSAPP/TELEGRAM exported as explicit non-standard properties | `document/ContactCardDocument.java` | Encoding rules as single-responsibility methods | "Folding counts octets, not characters, so a name with é never splits mid-character." |

### M. Local web security

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| State-changing requests (POST/PUT/DELETE) rejected with 403 when Origin or Host does not match the server's own host:port (CSRF + DNS-rebinding guard), unit-tested core, curl checks documented | `web/WebServer.java`, `web/WebServerGuardTest.java` | Static, side-effect-free decision core | "Host must be us (rebinding fails), Origin must be us if present (CSRF fails); curl still works because it sends no Origin." |
| Warning printed when `HOST` is not loopback | `App.java` | Honest defaults | "The app still listens where you ask, but it says out loud what that means." |

### N. Documentation and report

| Change | Where | OOP concept | Viva point |
|---|---|---|---|
| README + PROJECT_REPORT rewritten for the new design (class list, OOP mapping table, contact_methods data model, per-route algorithm, test plan, limitations); Mermaid sources for class, sequence and ER diagrams in `docs/`; placeholders kept for names/roll numbers/screenshots — nothing fabricated | `README.md`, `PROJECT_REPORT.md`, `docs/*.mmd` | Documentation as design evidence | "Every diagram node is a real class in the tree — nothing is aspirational." |

## Verified by running (2026-11-06, JDK 21.0.13, Maven 3.9.9, compile target 11)

- `mvn compile`, `mvn test` — 91/91 tests pass, zero failures, zero errors.
- `scripts/smoke-test.sh` (plain `javac --release 11` + dependency-free checks) — passes.
- App started in `--web-only` mode many times during development; exercised over HTTP with curl: create with WHATSAPP/TELEGRAM routes and per-route dates, emergency 2-route rejection (400), duplicate detection (409), unknown id (404), timezone validation (400), per-route verify endpoints, `/api/stats`, filtered search (`?type=&stale=`), vCard export/import round-trip (duplicate correctly skipped, fresh card imported), CSV/JSON/reachability exports, and the origin guard (same-origin allowed; cross-origin and rebound-Host POST/DELETE rejected with 403).
- **MySQL mode was executed against a real server** (MariaDB 11.8.6, wire-compatible with the MySQL Connector/J driver, run locally in the sandbox): the application created `contacts` and `contact_methods`, wrote a 3-route contact in one transaction, read per-route dates and labels back through the API, and the data survived an application restart. The one-time migration from a simulated v1 flat schema (phone/email/alternate_phone columns) copied the routes into `contact_methods`, dropped the legacy columns, and the migrated row read back correctly. This run also surfaced and fixed one real bug: the derived record-level `last_verified` date is no longer fed back into the builder on read, so a route the user left undated stays undated after a database round-trip. (Note: `database/schema.sql` targets MySQL 8 and uses the `utf8mb4_0900_ai_ci` collation, which MariaDB does not ship; on MariaDB, let the application create the tables itself — it specifies only `CHARSET=utf8mb4`.)
- Swing could not be screenshot-verified in this headless sandbox; the frame code compiles and its data paths are covered by the service-level tests. Screenshots are left as placeholders in the report.

## Documentation add-on (2026-11-08)

| Change | Where | Note |
|---|---|---|
| Added the system/governance docs: product requirements, task board with verification log, and contributor/agent rules | `PRD.md`, `TASKS.md`, `AGENTS.md` | PRD states the *what/why* (personas, stories, FR/NFR, acceptance gates); TASKS is the executed A–N board with an honest "still open" list; AGENTS pins constraints (Java 11, no runtime deps, frozen syllabus exercise, privacy rules, verification duties) |
| Full re-verification of v2.0 on a clean machine (JDK 17.0.21, Maven 3.9.16, target 11) | — | `mvn compile` clean, **91/91 tests pass**, smoke test pass; live HTTP checks reproduced emergency-2-route 400, duplicate 409, cross-origin 403, `/api/stats`, stale filter, vCard `N:`/INTERNET/CRLF/consent rules |
| Swing Observer verified **visually** this time (virtual X display): a contact created through the web API appeared in the Swing table without clicking Refresh | `demo/30-v2-swing-before.png`, `demo/31-v2-swing-auto-refresh.png` | closes the caveat from the 2026-11-06 run above; v1-baseline UI walkthrough screenshots (demo/01–28) are also archived in the demo folder |

## Windows native verification and tooling (2026-11-16)

| Change | Where | Note |
|---|---|---|
| Installed and verified native Oracle MySQL 8.4 Server Win64 and MySQL Workbench 8.0 CE | Windows environment | Verified native MySQL 8.4 InnoDB persistence and foreign key CASCADE constraints against `database/schema.sql` (`utf8mb4_0900_ai_ci`). |
| Added 1-click Windows execution and SQL administration batch utilities | `run-app.bat`, `open-sql-console.bat`, `view-tables.bat`, `database/start-mysql.bat` | Simplifies launching RelayBook with MySQL mode and accessing native MySQL CLI without manual setup. |
| Added real-time Web Database Inspector | `sql_viewer.py` | Browser-based live MySQL inspector polling native MySQL engine every 2 seconds with query runner. |
| Re-verified complete test suite | — | **91/91 JUnit tests pass**; end-to-end API writes verified reflected live in MySQL InnoDB tables. |
