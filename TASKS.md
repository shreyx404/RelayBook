# RelayBook — Task board (Tiers A–N)

Execution record for the v1.0 → v2.0 redesign. One row per task: what changed,
the OOP concept it demonstrates, and where the code lives. Detailed change
tables and viva talking points live in `CHANGELOG.md`.

**Legend:** `[x]` done · `[ ]` open · *(re-verified 2026-11-08: JDK 17.0.21,
Maven 3.9.16, compile target 11 — see the verification log at the bottom.)*

## Definition of Done (applied to every tier)

- `mvn compile` clean **and** plain `javac --release 11` clean (smoke path).
- `mvn test` green; `scripts/smoke-test.sh` green.
- No new runtime dependencies; `syllabus/BankAccount*` untouched.
- Behaviour changes documented in `CHANGELOG.md` (concept + viva point).

## Tier 1 — core OOP redesign

- [x] **A. Subtype polymorphism** — `getStaleAfterDays()` / `getMinimumRoutes()` / `describe()` on `Contact`; Person 180/1, Organization 365/1, Emergency 90/2; validator + advisor read the subtype, no hard-coded 90/180/365. *Concept: dynamic dispatch.* `model/Contact.java`, subtypes, `ContactValidator`, `ReachabilityAdvisor`
- [x] **B. Per-route verification** — immutable `ContactMethod`; routes as an immutable collection with legacy `getPhone()/getEmail()` as derived views; `Channel` + WHATSAPP/TELEGRAM; MySQL `contact_methods` table, transactional writes, idempotent v1 migration; route editors in Swing + web; `routes[]` in JSON. *Concept: encapsulation/immutability + safe schema evolution.* `model/`, `repository/`, `web/Json.java`
- [x] **C. Strategy scoring** — `RoutingPolicy` constants; `AbstractRoutingPolicy` skeleton with two hook methods; `Default` vs `Strict` (emergency) policies; advisor as context. *Concept: Strategy + hook methods.* `service/RoutingPolicy*.java`
- [x] **D. Builder** — validating `Contact.Builder`, `toBuilder()` copies, 13-arg constructor chain removed; `markVerified(id)` / `markVerified(id, channel)`. *Concept: atomic construction; immutable updates.* `model/Contact.java`, `ContactService`
- [x] **E. Observer** — `ContactChangeListener` (default methods), `CopyOnWriteArrayList` in the service, Swing refresh via `invokeLater` — including changes made through the web API. *Concept: Observer, thread-safe subjects, EDT discipline.* `service/ContactChangeListener.java`, `ui/AddressBookFrame`
- [x] **F. Exception hierarchy** — `RelayBookException` → Validation/NotFound/Duplicate; `DataAccessException` joins; web maps 400/404/409/500 with one catch chain. *Concept: exceptions mirroring the domain.* `exception/`, `web/WebServer`

## Tier 2 — features

- [x] **G. "Is now a good time?"** — `TimeWindow` parser (day lists, ranges, overnight, "any time"), `isOpenAt(ZonedDateTime)`, strict/`Optional` doors; ZoneId validation → `ValidationException`; local time + open-now shown in all UIs. *Concept: value object with two failure modes.* `model/TimeWindow.java`
- [x] **H. Documents & import/export** — `Document.export()` final (Template Method); `CsvDirectoryDocument` (privacy-aware), `JsonBackupDocument` (full), `ReachabilityReportDocument`; `VCardImporter` with `Result<T>` + `ImportSummary`; API + Swing File menu. *Concept: Template Method; fail-soft bulk ops.* `document/`
- [x] **I. Quick actions & stats** — "Mark checked today" in Swing; `POST /api/contacts/{id}/verify(?channel=)`; check-ins/person pages stopped re-PUTting whole records; `DirectoryStats` + `/api/stats` + dashboard strips. *Concept: command over CRUD; shared read model.* `ui/`, `web/`
- [x] **J. Collections/streams/concurrency** — `Comparable<Contact>` + comparator constants; `SortOption` enum + `?sort=`; stream `search(query, typeFilter, onlyStale)` shared by both clients; `Result<T>` generics; SwingWorker everywhere; daemon `ReminderScheduler` with clean stop. *Concept: streams, generics, executor lifecycle.* `service/`, `ui/`, `App.java`

## Tier 3 — quality & polish

- [x] **K. Tests** — JUnit 5 + Surefire (test scope only); **91 tests** across model/service/repository/document/web; boundary pins at 90/91, 180/181, 365/366 days for every subtype × both policies. `src/test/java/**`
- [x] **L. vCard correctness** — mandatory `N:`, `EMAIL;TYPE=INTERNET`, UTF-8-safe 75-**octet** folding, CRLF, `\;\,\\n` escaping; notes/relationship/best-time/time zone never exported; routes only with consent; WHATSAPP/TELEGRAM explicit properties. `document/ContactCardDocument.java`
- [x] **M. Local web security** — POST/PUT/DELETE rejected (403) when Origin/Host ≠ server's own; loopback default; non-loopback `HOST` warning; guard core unit-tested. `web/WebServer.java`, `App.java`
- [x] **N. Documentation** — README + PROJECT_REPORT rewritten; Mermaid class/sequence/ER sources in `docs/`; student-identifying fields kept as placeholders (nothing fabricated). `README.md`, `PROJECT_REPORT.md`, `docs/*.mmd`

## Still open (honest list)

- [x] **MySQL on stock MySQL 8** — native Oracle MySQL 8.4.9 Win64 installed, verified, and running on Windows; `database/schema.sql` (InnoDB, `utf8mb4_0900_ai_ci`) verified with live relational persistence, transactions, and MySQL Workbench 8.0 CE.
- [ ] **Student placeholders** — name/roll/class/survey/screenshots in `PROJECT_REPORT.md` and `README.md` are 【placeholders】; fill with real data before submission.
- [ ] **Own-run rows** — the manual test table in `PROJECT_REPORT.md` §11 asks for *your* dates/results; record them.
- [ ] **Future ideas** (not promised): encrypted storage, per-field revocable consent, check-in history, "imported ≠ verified" state, policy-registry config, user study.

## Verification log (what was actually executed)

| When | What | Result |
|---|---|---|
| 2026-11-06 (v2 build session) | `mvn test` (JDK 21, Maven 3.9.9) | 91/91 pass |
| 2026-11-06 | `scripts/smoke-test.sh` | pass |
| 2026-11-06 | HTTP exercise: create with WHATSAPP/TELEGRAM + per-route dates, emergency 2-route rule, duplicates 409, 404s, timezone 400, verify endpoints, `/api/stats`, filtered search, vCard round-trip, origin guard | all as expected |
| 2026-11-06 | MySQL-mode persistence + v1→v2 migration against **MariaDB 11.8.6** | contact + 3 routes in one transaction; restart-safe; migration copied routes and dropped legacy columns; one real bug found & fixed (derived `last_verified` no longer fed back into builder on read) |
| 2026-11-08 (re-verification, JDK 17.0.21 / Maven 3.9.16) | `mvn compile`, `mvn test` | clean; **91/91 pass** |
| 2026-11-08 | `scripts/smoke-test.sh` | pass |
| 2026-11-08 | Live HTTP: emergency 1-route → **400** ("Emergency contacts need at least two routes…"), duplicate → **409**, `/api/stats` JSON, cross-origin POST → **403**, `?stale=true` filter | all as expected |
| 2026-11-08 | vCard export check | `N:` present, `EMAIL;TYPE=INTERNET`, CRLF, consent-gated TEL/EMAIL, no private fields |
| 2026-11-08 | **Swing Observer check on Xvfb** (screenshots `demo/30`, `demo/31`) | contact created via web API while Swing open → **table auto-refreshed without clicking Refresh** |
| 2026-11-07 | v1 baseline demo (browser + Swing walkthrough, screenshots `demo/01–28`) | documented in `demo/README.md` |
| 2026-11-16 (Windows native verification) | Stock Oracle MySQL 8.4.9 on Windows x64 + Java 27 + Maven wrapper | **91/91 tests pass**; native Windows MySQL initialized and schema.sql applied (InnoDB, `utf8mb4_0900_ai_ci`); full CRUD persistence verified over HTTP and native `mysql.exe`; 1-click Windows batch runners (`run-app.bat`, `open-sql-console.bat`, `view-tables.bat`, `start-mysql.bat`) and Web Inspector added; MySQL Workbench 8.0 CE verified. |
