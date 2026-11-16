# RelayBook — Product Requirements Document (PRD)

| | |
|---|---|
| **Product** | RelayBook — a reachability-first digital address book |
| **Version** | 2.0 (requirements as shipped) · doc added 2026-11-08 |
| **Status** | Implemented — see `TASKS.md` and `CHANGELOG.md` for evidence |
| **Related** | `README.md` (run guide) · `PROJECT_REPORT.md` (academic report) · `database/schema.sql` |

> Companion docs, not duplicates: this PRD states *what the product must do and why*.
> The academic *how* (class design, syllabus mapping, algorithm tables) lives in
> `PROJECT_REPORT.md`; the execution log lives in `TASKS.md` and `CHANGELOG.md`.

## 1. Product summary

RelayBook is a local-first contact manager that treats **every route to a person
(phone, email, backup phone, WhatsApp, Telegram) as its own small record with its
own verification date**. A rule-based, fully explainable advisor recommends which
route to try, answers "is now a good time?" in the contact's own time zone, and
nags — gently and honestly — when records go stale. It ships as a Java Swing
desktop client and a browser page served by the same process, sharing one service
layer, backed by an in-memory demo store or MySQL.

## 2. Problem

Ordinary address books fail in two specific, everyday ways:

1. **One shared "last verified" date.** A number and an email go stale at
   different speeds, but conventional records carry a single freshness stamp —
   so users stop trusting the whole record.
2. **Emergency records get no extra care.** The contact you need *when things go
   wrong* is held to the same standard as everyone else, and often has no
   fallback route at all.

Both flaws are observable in common address-book apps; the survey evidence
itself is intentionally left as placeholders in `PROJECT_REPORT.md` for the
student to collect and record before submission.

## 3. Goals and non-goals

**Goals**

- G1. Make per-route freshness visible and actionable (check-ins, stale lists).
- G2. Hold emergency contacts to a higher, configurable standard (2 routes, 90-day window, stricter scoring).
- G3. Recommend the next route with a reason a user can audit ("email was verified 20 days ago, phone 300 days ago").
- G4. Keep every rule explainable in a viva — simple constants, no ML.
- G5. One domain core serving two UIs and two storage backends.

**Non-goals (explicit, honest)**

- NG1. RelayBook does not send calls/messages or verify ownership of numbers.
- NG2. The 0–100 score measures **route coverage + record freshness**; it is not
  a probability that someone will answer.
- NG3. Not a multi-user, authenticated, or internet-exposed service.
- NG4. No encryption-at-rest, audit log, or consent-revocation workflow (documented limitations).

## 4. Personas

| Persona | Need from RelayBook |
|---|---|
| **Riya**, 34, organizes family + building society contacts | "Tell me which number to try and when I last confirmed it — without making me maintain a spreadsheet." |
| **Dr. Mehta's family**, emergency use case | "The doctor's card must always carry two working routes and be re-checked every 90 days." |
| **The examiner** (OOP course) | "Show me inheritance that earns its keep, real patterns, a clean exception tree, tests — and an honest demo." |
| **Future contributor / agent** | "Constrain me with clear build commands, frozen boundaries, and verification duties" (`AGENTS.md`). |

## 5. User stories and acceptance criteria

### Manage the directory
- **US-1** As Riya, I can create person / organization / emergency contacts with
  one or more routes, a preferred route, free-text best time, time zone, and
  private notes. *AC: invalid records cannot exist — the builder validates on
  `build()`; an emergency with fewer than 2 routes is rejected with a clear message.*
- **US-2** As Riya, I can search and sort the directory (text, type, stale-only;
  by name/score/last-verified/type). *AC: the browser filter and the API `?q=&type=&stale=&sort=` hit the same pipeline; results identical.*
- **US-3** As Riya, I can edit and delete records; routes can be added/removed
  per channel with labels. *AC: contact + routes commit together in MySQL.*

### Keep data fresh
- **US-4** As Riya, I can mark a record (or a single route) "checked today" with
  one click in either UI. *AC: `POST /api/contacts/{id}/verify` exists; Swing and web both use it; stats update.*
- **US-5** As Riya, I can see who needs checking (Check-ins page, reminder
  sweep). *AC: stale = preferred route undated or past the subtype window (person 180 / organization 365 / emergency 90 days).*

### Get advice
- **US-6** As Riya, I get a suggested route with a per-route-age rationale.
  *AC: when the preferred route is stale, the freshest alternative wins and the
  reason quotes both ages; nothing is invented when no date exists.*
- **US-7** As Riya, I see "is now a good time?" — the contact's local time and
  whether a parsed window ("Weekdays 18:00-21:00") is open now. *AC: invalid time zones are rejected; unparsable free text is kept as text only.*
- **US-8** As Riya, I see a 0–100 score and a status chip. *AC: point values are named constants in one place (`RoutingPolicy`); emergency records use the strict policy; boundary tests pin the bands.*

### Share and back up
- **US-9** As Riya, I can export a consent-gated vCard. *AC: consent off → identity only; consent on → routes; notes/relationship/best time/time zone never exported; `N:` always present; 75-octet folding; CRLF; proper escaping.*
- **US-10** As Riya, I can import vCards with duplicate detection. *AC: one
  broken card rejects one line, not the file; duplicates are skipped and reported.*
- **US-11** As Riya, I can export privacy-aware CSV, an explicit full backup
  (JSON/CSV), and a reachability summary. *AC: full backups label themselves; the privacy CSV never contains notes or unconsented routes.*

### Admin / ops
- **US-12** As Riya, I can run it without a database (in-memory demo) or with
  MySQL. *AC: `DB_URL` selects MySQL; no silent fallback to memory.*
- **US-13** As a cautious user, state-changing API calls from a foreign origin
  are rejected. *AC: mismatched Origin/Host → 403; loopback binding by default; non-loopback `HOST` prints a warning.*
- **US-14** As the examiner, the bank-account `Scanner` exercise runs
  separately. *AC: `--bank-demo`; untouched by v2 work.*

## 6. Functional requirements (shipped in v2.0)

| ID | Requirement | Priority | Where |
|---|---|---|---|
| FR-1 | CRUD for the three contact subtypes | Must | `service/ContactService`, repositories |
| FR-2 | Routes as first-class records: channel, value, per-route date, label | Must | `model/ContactMethod`, `contact_methods` table |
| FR-3 | Subtype policies: staleness 180/365/90 days, route minimums 1/1/2 | Must | `PersonContact`/`OrganizationContact`/`EmergencyContact` |
| FR-4 | Strategy-based scoring: default + strict (emergency) policies | Must | `RoutingPolicy`, `AbstractRoutingPolicy`, `Default/StrictRoutingPolicy` |
| FR-5 | Advisor fallback by freshest route + explained rationale | Must | `ReachabilityAdvisor`, `ReachabilityReport` |
| FR-6 | Check-ins: record-wide and per-route "mark verified today" | Must | `ContactService.markVerified`, `/api/contacts/{id}/verify` |
| FR-7 | Structured `TimeWindow` parsing + `isOpenAt` + ZoneId validation | Should | `model/TimeWindow` |
| FR-8 | Live sync of both UIs via Observer | Must | `ContactChangeListener`, Swing `invokeLater` |
| FR-9 | Directory stats surfaced in both UIs + `/api/stats` | Should | `DirectoryStats` |
| FR-10 | Stream search + sort options shared by API and Swing | Should | `ContactService.search`, `SortOption` |
| FR-11 | MySQL CRUD: prepared statements, transactional contact+routes, idempotent v1→v2 migration | Must | `MySqlContactRepository`, `schema.sql` |
| FR-12 | vCard export per §US-9 rules; WHATSAPP/TELEGRAM as explicit properties | Must | `ContactCardDocument` |
| FR-13 | vCard import with `Result<T>` outcomes + `ImportSummary` | Should | `VCardImporter` |
| FR-14 | CSV / JSON backup / reachability documents via Template Method | Should | `document/*` |
| FR-15 | Same-origin guard on POST/PUT/DELETE | Must | `WebServer` |
| FR-16 | Reminder sweep (daemon scheduler, clean stop) | Could | `ReminderScheduler` |
| FR-17 | Bank account + Scanner exercise | Must | `syllabus/*` |

## 7. Non-functional requirements

| ID | Requirement | Verified by |
|---|---|---|
| NFR-1 | Java 11 bytecode target (`--release 11`); runs on newer JDKs | smoke test + `mvn test` on JDK 17 |
| NFR-2 | No new runtime dependencies (JUnit is test-scope only) | `pom.xml` inspection |
| NFR-3 | UI never blocks the EDT: repository/file I/O in `SwingWorker`s | design + code review |
| NFR-4 | All SQL values via prepared statements; multi-table writes transactional | code review, MariaDB run |
| NFR-5 | Offline-friendly web assets (no CDN) | resources are local |
| NFR-6 | Privacy defaults: notes/best-time/time zone never leave the app; routes need consent | unit tests + manual export diff |
| NFR-7 | Every domain rule explainable; magic numbers banned from logic (named constants) | code review |
| NFR-8 | 91 automated tests, no DB/server required | `mvn test` → 91/91 |

## 8. Acceptance gates (all verified 2026-11-08 — see `TASKS.md` § "Verification log")

1. `mvn compile` and plain `javac --release 11` both succeed; 91/91 tests green.
2. In-memory mode serves Swing + browser from one process.
3. Emergency contact with one route → HTTP 400 with a type-specific message.
4. Duplicate create → 409; unknown id → 404; bad input → 400.
5. Cross-origin state-changing request → 403.
6. vCard: `N:` present, ≤75-octet lines, CRLF, escaping, consent gate, no private fields.
7. Web-created contact appears in Swing without manual refresh (Observer).
8. `/api/stats`, filtered search, verify endpoints respond correctly.
9. MySQL: contact + routes in one transaction; migration from v1 schema (verified against MariaDB; stock MySQL 8 noted as untested-by-us).

## 9. Release plan

| Version | Content | State |
|---|---|---|
| 1.0.0 | Baseline: CRUD, single last-verified date, v1 schema, basic vCard | shipped (superseded) |
| 2.0.0 | OOP redesign Tiers A–N (see `CHANGELOG.md`) | shipped, verified |
| 2.0.x | Documentation add-on: `PRD.md`, `TASKS.md`, `AGENTS.md` | this update |

## 10. Open questions / future ideas

- Encrypted local storage; per-field, revocable consent; check-in history timeline.
- Configurable thresholds via the policy registry (currently constants by design).
- A visible "imported, not yet verified" state for imported cards.
- Small user study to tune windows/wording (evidence currently survey-shaped, pending the student's own data).
