# Project Report: RelayBook — Reachability-First Digital Address Book

> Replace the placeholders below with your own names, class/division, roll numbers, faculty guide, and the screenshots/output from your own run. Do not submit a report claiming tests or survey results you have not actually completed. The "verified" claims in this template describe what the application was built to do and what the included automated checks cover; record **your** run dates and results in the testing section before submission.

**Student name(s):** 【your name(s)】
**Class/Division:** 【class · division】  **Roll number(s):** 【roll numbers】
**Faculty guide / Head of Department:** 【name】  **Academic year:** 【year】

## 1. Abstract

RelayBook is an object-oriented Java address-book application that stores people, organizations, and emergency contacts together with several ways to reach them. Unlike a basic contact list, it treats every route (phone, email, backup phone, WhatsApp, Telegram) as its own record with an individual verification date, a preferred route, a structured availability window, and a per-type freshness policy. A transparent rule-based advisor — built on the Strategy pattern — recommends a route and explains its choice using each route's own age ("Email was verified 20 days ago, phone 300 days ago: try email first"). The application offers a Java Swing desktop client and a browser interface served by the same Java process, kept in sync by an Observer mechanism. A repository abstraction supports both an in-memory demonstration store and MySQL persistence through JDBC, with a normalized `contact_methods` table and transactional writes. The project demonstrates classes, encapsulation, inheritance with real subtype behaviour, polymorphism, abstract classes, interfaces, the Builder, Strategy, Observer and Template Method patterns, a custom exception hierarchy, generics, streams, comparators, concurrency primitives, JDBC CRUD, and Swing event handling. It is a coursework prototype and does not send or verify communications.

## 2. Problem statement

Everyday contact lists store names and communication fields but do not make data freshness or fallback routes visible. Two specific flaws recur: (1) one "last verified" date is shared by every stored number, although numbers and mailboxes go stale at different times, and (2) emergency records — which are needed exactly when things go wrong — are held to no higher standard than casual acquaintances. The project explores whether an address book can fix both flaws while staying explainable, without becoming a messaging tool.

## 3. Proposed solution

RelayBook maintains one contact record with an immutable collection of routes; each route owns its verification date and optional label. A Strategy-based advisor scores coverage and freshness (stricter rules for emergency contacts) and suggests the freshest usable route, quoting per-route ages in its rationale. A structured `TimeWindow` answers "is now a good time?" using the contact's own time zone. The same service logic is exposed to a Swing UI and a small browser UI; the service notifies registered listeners so both UIs stay in sync live. The `ContactRepository` interface isolates persistence from application logic; MySQL and in-memory implementations can be switched without changing either UI. Documents (vCard, CSV, JSON backup, reachability summary) are built through a Template Method base class with privacy rules baked in.

## 4. Objectives

1. Build a usable CRUD address book for several contact types.
2. Apply core OOP principles — inheritance with behaviour, polymorphism, abstract classes, interfaces — to a meaningful domain.
3. Implement per-route verification so the advisor can reason about each route's freshness separately.
4. Make the route-selection and staleness rules pluggable (Strategy) and observable (Observer).
5. Persist records through MySQL JDBC with prepared statements, transactions, and a normalized schema.
6. Build a Java Swing interface with event handling that never blocks the event dispatch thread.
7. Integrate a simple web page with the Java service, including basic same-origin protection.
8. Export privacy-aware documents and import vCards with duplicate detection.
9. Include the separate bank-account/Scanner exercise required by the syllabus.

## 5. Scope and assumptions

- The application stores routes; it does not send calls, emails, or messages.
- The score is a reminder/coverage indicator, not a probability of successful contact.
- Routes carry individual verification dates; the record-level date is derived as the latest route date.
- MySQL mode assumes a reachable MySQL server and the Connector/J runtime dependency.
- The local web app is intended for a classroom demonstration on a trusted machine; the Origin/Host guard is hygiene, not authentication.
- Imported routes start without verification dates — imported is not verified.
- The sample in-memory contacts are synthetic. Do not put sensitive real-world data in a publicly exposed demo.

## 6. Requirements

### Functional requirements

- Create, view, update, search, sort, and delete contacts.
- Store person, organization, and emergency contact types with per-type staleness windows (180/365/90 days) and route minimums (1/1/2).
- Store routes as first-class records with per-route verification dates, labels, and preferred channel.
- Parse structured availability windows ("Weekdays 18:00-21:00", "any time") and validate IANA time zones.
- Recommend a route using each route's own verification age and display a short reason.
- Show contacts needing a check-in; support one-click "mark checked today" (record-wide and per-route).
- Show directory statistics (total, stale, single-route, ready, emergency contacts missing a second route).
- Persist contacts in MySQL with JDBC CRUD spanning contacts + routes in one transaction.
- Provide a Swing GUI and a browser contact manager with directory, check-ins, and person-detail pages.
- Export consent-gated vCards, privacy-aware CSV, explicit full JSON/CSV backups, and a reachability summary; import vCards with duplicate detection.
- Support the bank-account console exercise using `Scanner`.

### Non-functional requirements

- Use Java 11 or newer (the pom compiles with `--release 11`).
- Keep UI logic separate from persistence and domain validation; both UIs render the same service objects.
- Use prepared statements for all SQL data values; wrap multi-table writes in transactions.
- Avoid external frontend CDNs so the page works offline.
- Keep the event dispatch thread free of repository and file I/O (`SwingWorker`).
- Reject state-changing API requests from mismatched Origin/Host headers.

## 7. OOP design and syllabus mapping

| Concept | Project design / example |
|---|---|
| Class and object | `Contact`, `ContactMethod`, `TimeWindow`, `BankAccount`, generated `Document` objects. |
| Encapsulation | All model fields are private/final; routes are exposed as immutable collections; `ContactMethod` is an immutable value object with copy helpers. |
| Constructor and `this` | Field assignment in `Contact(Builder)` uses `this.field = ...`; the builder is the single construction path. |
| Inheritance | `PersonContact`/`OrganizationContact` extend `Contact`; `EmergencyContact extends PersonContact` (multilevel). |
| `super` | `EmergencyContact(Builder)` → `super(builder)` → `PersonContact` → `Contact`. |
| Static | `Contact.getCreatedObjectCount()`; `Channel.from(...)`; comparator constants; policy point constants. |
| Final | Contact IDs are final, `getId()` is final, `Document.export()` is final (Template Method), policy constants are `static final`. |
| Polymorphism | Subtype overrides (`getStaleAfterDays`, `getMinimumRoutes`, `describe`, `getContactType`) are dispatched through `Contact` references everywhere; repositories, advisor, and UIs never downcast. |
| Abstract class | `Contact` and `Document`; `AbstractRoutingPolicy` shares the scoring skeleton with two abstract hook methods. |
| Interface | `Exportable`, `Validatable`, `PrivacyLabelled`, `ShareableDocument`, `ContactRepository`, `RoutingPolicy` (Strategy), `ContactChangeListener` (Observer, default methods). |
| Builder pattern | `Contact.Builder` with fluent setters, validating `build()`, `toBuilder()`; the factory and both UIs use it; the 13-argument constructor chain is gone. |
| Strategy pattern | `DefaultRoutingPolicy` / `StrictRoutingPolicy` per contact type; the advisor is the context; all point values live as named constants in `RoutingPolicy`. |
| Observer pattern | `ContactService` keeps a `CopyOnWriteArrayList` of listeners and notifies after every successful change; the Swing table refreshes via `SwingUtilities.invokeLater`, including for web-API changes; the reminder scheduler broadcasts stale sweeps. |
| Template Method | `Document.export()` is final: `appendHeader` → `renderContent` → `appendFooter`; the vCard uses the hooks. |
| Custom exceptions | Abstract unchecked `RelayBookException` → `ValidationException`, `ContactNotFoundException`, `DuplicateContactException`; `DataAccessException` joins the tree; the web layer maps them to 400/404/409/500. |
| Generics | `Result<T>` (success value or error message) used by the vCard importer; typed collections everywhere. |
| Comparable / Comparator | `Contact implements Comparable<Contact>` (name, case-insensitive); comparator constants `BY_NAME`, `BY_LAST_VERIFIED`, `BY_TYPE`; `SortOption` adds score-based ordering. |
| Streams | `ContactService.search(query, typeFilter, onlyStale)` filters/sorts with streams and is shared by Swing and the API. |
| Concurrency | `CopyOnWriteArrayList` listeners, daemon `ScheduledExecutorService` reminder sweep with clean stop, `SwingWorker` for all repository/file I/O, thread-safe in-memory store. |
| JDBC | `MySqlContactRepository` implements CRUD with prepared statements; contact + route writes run in one transaction; safe transactional migration from the legacy flat columns. |
| Swing/events | `AddressBookFrame` uses `ActionListener` lambdas, a table selection listener, a route editor table, `JFileChooser` import/export, and `SwingWorker`s. |
| Scanner | `BankAccountDemo` accepts menu input via `Scanner`. |

### BankAccount exercise

`BankAccount` is intentionally separate from RelayBook's contact domain: deposits, withdrawals, fees, account-holder-name changes, and account summaries have no natural role in an address book. It still satisfies the syllabus requirement and can be demonstrated with `--bank-demo`.

## 8. Reachability algorithm

### Route coverage points (constants live in `RoutingPolicy`)

| Available routes | Default policy | Strict policy (emergency) |
|---|---:|---:|
| 0 | 0 | 0 |
| below the subtype minimum | 25 | **10** |
| 2 | 45 | 45 |
| 3+ | 60 | 60 |

### Freshness points (age of the latest check-in; same band edges in both policies)

| Age | Default | Strict |
|---|---:|---:|
| no date | 0 | 0 |
| 0–90 days | 40 | 40 |
| 91–180 days | 30 | **15** |
| 181–365 days | 15 | **5** |
| > 365 days | 0 | 0 |

The total is capped at 100. **Staleness** is a separate question from the score: each subtype carries its own window (person 180, organization 365, emergency 90) and a record is stale when its preferred route has no date or is past that window. When the preferred route is stale, the advisor picks the alternative with the most recent verification date and explains the choice with per-route ages; when no alternative exists it still shows the only route but asks the user to verify it. No verification date is ever invented. Boundary tests pin the band edges at 90/91, 180/181, and 365/366 days for every subtype and both policies.

### "Is now a good time?"

When the best-time text parses as a structured window and the time zone is a valid IANA zone, the report adds the contact's current local time and whether the window is open right now; both UIs render it. Free text that does not parse is kept as display text only — nothing is invented.

## 9. Data model

**Contact fields:** `id` (UUID string, primary key), `contact_type`, `full_name`, optional `organization`, `relationship_label`, `preferred_channel`, `best_time` (free text), `time_window` (derived structured window, in-memory), `time_zone`, `notes`, derived `last_verified` (latest route date), `share_consent`, database-managed `created_at`/`updated_at`.

**Route fields (per route, in `contact_methods`):** `channel` (PHONE, EMAIL, ALTERNATE_PHONE, WHATSAPP, TELEGRAM), `value`, `last_verified`, optional `label`.

The schema is in `database/schema.sql`: `contacts` (no flat route columns) and normalized `contact_methods` with `FOREIGN KEY (contact_id) REFERENCES contacts(id) ON DELETE CASCADE` and a unique key per (contact, channel). On first start against a v1 database, the repository copies the legacy `phone`/`email`/`alternate_phone` columns into `contact_methods` inside a transaction and drops them (guarded by a column whitelist).

## 10. System modules

1. **Model:** `Contact` hierarchy, `ContactMethod`, `TimeWindow`, `Channel`, `ContactType`, `ContactFactory`, `ContactValidator`, `Contact.Builder`.
2. **Service:** `ContactService`, `ReachabilityAdvisor` (Strategy context), `AbstractRoutingPolicy`/`DefaultRoutingPolicy`/`StrictRoutingPolicy`, `ReachabilityReport`, `ContactChangeListener`, `ReminderScheduler`, `DirectoryStats`, `SortOption`, `Result<T>`.
3. **Persistence:** `ContactRepository`, `InMemoryContactRepository`, `MySqlContactRepository`.
4. **Document:** abstract `Document` (Template Method), share/export/validation interfaces, `ContactCardDocument`, `CsvDirectoryDocument`, `JsonBackupDocument`, `ReachabilityReportDocument`, `VCardImporter`, `ImportSummary`.
5. **Exceptions:** `RelayBookException`, `ValidationException`, `ContactNotFoundException`, `DuplicateContactException`.
6. **Desktop UI:** `AddressBookFrame` with route editor, stats strip, import/export menu, `SwingWorker` data paths.
7. **Web layer:** `WebServer`, JSON serialization, REST API (list/search/verify/stats/import/export), contact list, check-ins, person-detail pages, shared CSS/JS, same-origin guard.
8. **Syllabus exercise:** `BankAccount` and `BankAccountDemo`.

## 11. Testing plan and results

**Automated.** `mvn test` runs 91 JUnit 5 tests (Surefire) covering: subtype behaviour and comparators, validation rules (through the builder), builder/factory and per-route form parsing, `TimeWindow` parsing and `isOpenAt` (including overnight windows), policy score boundaries at 90/91/180/181/365/366 for every subtype and both policies, the advisor's per-route fallback and rationale text, listener notifications and `markVerified` variants, duplicate detection, stream search and sort options, in-memory repository CRUD, vCard RFC rules (N:, INTERNET, folding, escaping, CRLF, privacy), the three directory documents, the vCard importer (fresh/duplicate/malformed/unfolding/round-trip), and the same-origin guard.

**Record your own run:** date 【date】, machine 【machine】, JDK version 【version】, result `Tests run: 91, Failures: 0, Errors: 0` 【confirm from your console output】.

**Manual plan (record actual results in the last column):**

| Test | Steps | Expected result | Actual (fill in) |
|---|---|---|---|
| In-memory launch | `mvn exec:java` without `DB_URL` | Swing opens; browser UI loads synthetic records at port 8080. | 【 】 |
| Create contact | Add name + phone + email with per-route dates | Record appears in both clients without a manual refresh; score/status visible. | 【 】 |
| Emergency validation | Try to save an emergency contact with one route | Rejected with "Emergency contacts need at least two routes…". | 【 】 |
| Stale subtype windows | Emergency record checked 120 days ago vs person record with the same age | Emergency shows "Needs verification"; person stays fresh. | 【 】 |
| Per-route fallback | Phone verified 300 days ago, email 20 days ago | Suggested route = email; rationale quotes both ages. | 【 】 |
| Mark checked today | Click the button in Swing and on the web page | All routes (or the chosen channel) get today's date; stats strip updates. | 【 】 |
| Live sync | Create a contact in the browser | Swing table updates without clicking Refresh. | 【 】 |
| Persistence | Configure MySQL, create a contact, restart | Contact and its routes remain after restart (both tables). | 【 】 |
| Legacy migration | Start against a v1 flat-schema database | Routes copied into `contact_methods`, legacy columns dropped, data intact. | 【 】 |
| vCard privacy | Export with sharing off, then on | Off: identity-only card. On: routes included. Notes/relationship/best time/time zone never appear in either. | 【 】 |
| vCard correctness | Export a contact with a very long name | `N:` present; every physical line ≤ 75 octets; CRLF endings; special characters escaped. | 【 】 |
| Import | Import the exported file, then import it again | First run imports; second run reports the record as a duplicate skip. | 【 】 |
| Backups | Export privacy CSV vs full CSV vs JSON backup | Privacy file has no notes and only consented routes; full files contain everything and say so. | 【 】 |
| BankAccount | Run `--bank-demo`; deposit, withdraw, change name, charge fee, summary | Scanner menu accepts actions; failed overdraw leaves balance unchanged. | 【 】 |
| API | `curl http://127.0.0.1:8080/api/contacts` | A JSON array with per-contact `routes[]` is returned. | 【 】 |
| API safety | Cross-origin and rebound-Host POST/DELETE | 403 with a clear message; same-origin requests unaffected. | 【 】 |
| Stats | `curl http://127.0.0.1:8080/api/stats` | JSON counters matching the Swing stats strip. | 【 】 |
| Motion accessibility | Enable reduced-motion preference or toggle dark colors | Header GIF is hidden for reduced-motion users; theme can be changed and is remembered locally. | 【 】 |

## 12. Security, privacy, and ethical considerations

The prototype binds to loopback by default and warns when `HOST` is not loopback. It does not include authentication, HTTPS, encryption-at-rest, authorization roles, or an audit log; the Origin/Host guard on state-changing requests is a basic CSRF/DNS-rebinding measure, not a substitute for authentication. It should not be exposed to the public internet or used as a production contact database. The vCard and CSV features demonstrate data minimization: consent controls route sharing, and notes/availability never leave the app by default; the full-backup documents deliberately include everything and label themselves. In a future version, consent should be explicit per field, revocable, and auditable.

## 13. Limitations and future scope

- The strict policy's penalty for a one-route emergency can only occur on rows migrated from the old flat schema; the builder cannot create such records any more.
- Imported contacts start unverified (by design); a visible "imported, not yet verified" state would help.
- No per-user accounts; a single shared directory.
- The time-window parser accepts 24-hour `HH:MM` ranges, not prose like "after lunch".
- No automated verification or communication integration.
- Basic rule-based score, not personalized by relationship or the user's actual success history.
- Future work: encrypted local storage, per-field consent, check-in history, configurable thresholds via the policy registry, accessibility review, and a small user study.

## 14. Conclusion

RelayBook turns a basic contact list into a small continuity tool by making route alternatives, per-route freshness, and time windows visible — while keeping every rule explainable and testable. Its architecture (Strategy for scoring, Observer for live UIs, Template Method for documents, a custom exception hierarchy mapped to HTTP, and a normalized transactional storage layer) gives the same domain rules to Swing, a browser interface, and either memory or MySQL storage. The originality claim stays measured and the privacy limitations explicit.

*(Screenshots: 【insert your own screenshots here — Swing form with route editor, browser card with per-route dates, check-ins page, stats strip, vCard file contents, test run output.】)*
