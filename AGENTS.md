ee# AGENTS.md — instructions for AI agents and contributors

Working rules for anyone (human or agent) changing this repository. Read this
before writing code; it exists so that automated contributions meet the same
bar as the original work: **verifiable, dependency-free, explainable**.

## What this project is

RelayBook — a reachability-first address book (Java 11, Swing + built-in HTTP
server + vanilla HTML/JS, in-memory or MySQL JDBC storage). v2.0 is shipped;
the OOP redesign is complete and verified (`TASKS.md`, `CHANGELOG.md`). New
work should be **additive**, not a rewrite.

## Hard constraints (do not violate)

1. **Java 11 target.** Compile with `--release 11`. No preview features, no
   APIs newer than Java 11 (checked by the smoke script on every build).
2. **No new runtime dependencies.** Only JDK modules + MySQL Connector/J at
   runtime. JUnit 5 is **test scope only**. If a feature seems to need a
   library, redesign it with the JDK.
3. **Package layout is stable:** `model`, `service`, `repository`, `document`,
   `exception`, `ui`, `web`, `syllabus`. New classes belong to one of these.
4. **`syllabus/BankAccount*` is frozen** — it is the course's separate Scanner
   exercise. Never refactor it into the contact domain.
5. **Both storage modes must keep working:** in-memory (default) and MySQL
   (`DB_URL`). Never silently fall back from MySQL to memory.
6. **Privacy rules are load-bearing:** notes, relationship, best time and time
   zone never leave the app in exports; route data needs `shareConsent`;
   full backups must label themselves. Do not weaken these.
7. **Honesty rule:** never claim a result you have not run. If you cannot
   compile/test, say so. Record what you ran in `TASKS.md` (verification log)
   or explicitly mark it as unverified.

## Commands

```bash
mvn compile                 # build
mvn test                    # 91 JUnit tests (no DB/server needed)
sh scripts/smoke-test.sh    # javac --release 11 + dependency-free checks
mvn exec:java               # run: Swing + http://127.0.0.1:8080 (memory mode)
mvn exec:java -Dexec.args="--web-only"     # headless
mvn exec:java -Dexec.args="--bank-demo"    # syllabus exercise (frozen)
# MySQL mode:
DB_URL='jdbc:mysql://127.0.0.1:3306/relaybook?serverTimezone=UTC&useSSL=false' \
DB_USER=root DB_PASSWORD=... mvn exec:java
```

No Maven available? Compile like the smoke script does:

```bash
javac --release 11 --add-modules jdk.httpserver -encoding UTF-8 \
  -d out $(find src/main/java -name '*.java')
cp -R src/main/resources/* out/
java --add-modules jdk.httpserver -cp out com.relaybook.App
```

## Working agreement for changes

- **Small, named changes.** One coherent change per commit; subject like
  `service: quote per-route ages in advisor rationale`.
- **Verify after every change:** compile + tests + smoke. A change that fails
  any of the three is not done — fix or revert before moving on.
- **Update docs in the same commit** when behaviour, API, or schema changes:
  `CHANGELOG.md` (grouped under the matching tier, with the OOP concept and a
  viva point), `README.md` (user-facing behaviour), `PROJECT_REPORT.md`
  (design/algorithm), `TASKS.md` (board + verification log).
- **Placeholders stay placeholders.** Student name/roll/survey/screenshots are
  【…】 fields the *student* fills; never fabricate them, never invent survey
  results, screenshots, or run dates.
- **Breaking changes** (public API, schema, exported formats) require a note
  in `CHANGELOG.md` with a migration path before the code lands.

## Code conventions

- **Immutability first:** value objects (`ContactMethod`, `TimeWindow`) are
  final, defensive-copied; `Contact` updates go through `Builder.toBuilder()`,
  never field mutation.
- **Construction is validation:** objects that would be invalid must not
  exist — build() throws `ValidationException`.
- **Exceptions:** throw the `exception` package types (`ValidationException`,
  `ContactNotFoundException`, `DuplicateContactException`,
  `DataAccessException`). Never leak raw `IllegalArgumentException` from the
  domain, and never mention HTTP in domain code (the web layer maps them).
- **Magic numbers are banned from logic:** scoring point values live only as
  named constants in `RoutingPolicy`; staleness windows only in the subtypes.
- **Polymorphism over type checks:** no `instanceof Contact` cascades — ask
  the object (`getStaleAfterDays()`, `getMinimumRoutes()`, `describe()`).
- **One pipeline per behaviour:** search/sort is implemented once
  (`ContactService.search`) and consumed by both UIs and the API.
- **EDT discipline:** Swing event handlers never touch the repository or files
  directly; use `SwingWorker`, post results back on the EDT.
- **SQL:** prepared statements only; contact+routes writes inside one
  transaction; schema changes ship with an idempotent migration path in
  `MySqlContactRepository` and an updated `database/schema.sql`.

## Testing policy

- JUnit 5, `src/test/java`, mirroring package names; no network, no real
  MySQL, no filesystem assumptions — repositories are in-memory fakes.
- Behaviour boundaries get tests (e.g. day-band edges 90/91, 180/181, 365/366
  for every subtype × both policies).
- vCard/CSV/JSON documents are tested byte-level (CRLF, folding limits,
  escaping, consent gating, privacy omissions).
- The web guard core is unit-tested without starting a socket server.
- New feature ⇒ new tests in the same commit; keep `mvn test` green.

## Quick architecture map

```text
model/      Contact hierarchy (Builder), ContactMethod, TimeWindow, Channel
service/    ContactService (Observer subject), ReachabilityAdvisor (Strategy
            context), RoutingPolicies, ReminderScheduler, DirectoryStats, Result<T>
repository/ ContactRepository → InMemory | MySQL (transactional, migrating)
document/   Template Method base + vCard/CSV/JSON/report documents, importer
exception/  RelayBookException hierarchy → mapped to 400/404/409/500 in web/
ui/         AddressBookFrame (SwingWorkers, route editor, Observer listener)
web/        WebServer: REST API + pages, same-origin guard, Json
syllabus/   BankAccount + demo (frozen course exercise)
```
