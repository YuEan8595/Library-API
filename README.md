# Library API

A RESTful API for a simple library system: register borrowers, register book copies, list the
catalogue, and lend copies out — one borrower per copy at a time.

Java 17 · Spring Boot 3.4 · PostgreSQL 16 · Maven · Flyway · Docker · GitHub Actions

---

## Table of contents

1. [Quick start](#quick-start)
2. [API reference](#api-reference)
3. [Data model](#data-model)
4. [Choice of database](#choice-of-database)
5. [How "one borrower per copy" is guaranteed](#how-one-borrower-per-copy-is-guaranteed)
6. [Error format](#error-format)
7. [Request tracing](#request-tracing)
8. [Configuration](#configuration)
9. [Testing](#testing)
10. [Project layout](#project-layout)
11. [12-Factor conformance](#12-factor-conformance)
12. [Assumptions](#assumptions)

---

## Quick start

### Option A — Docker Compose (nothing needed but Docker)

```bash
docker compose up --build
```

That builds the image, starts PostgreSQL, runs the Flyway migrations, and exposes the API.

| What | Where |
|---|---|
| API base URL | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI spec | `http://localhost:8080/v3/api-docs` |
| Liveness / readiness | `http://localhost:8080/actuator/health` |

Tear down with `docker compose down -v`.


### Option B — run locally against PostgreSQL

You need JDK 17+ and a PostgreSQL 14+ instance. Maven is provided by the wrapper (`./mvnw`), so no separate Maven install is required.

```bash
# start just the database
docker compose up -d db

# run the app
export DATABASE_URL=jdbc:postgresql://localhost:5432/library
export DATABASE_USERNAME=library
export DATABASE_PASSWORD=library
./mvnw spring-boot:run          # Windows: .\mvnw spring-boot:run
```

### Sixty-second walkthrough

```bash
BASE=http://localhost:8080/api/v1

# 1. register a borrower
curl -s -X POST $BASE/borrowers -H 'Content-Type: application/json' \
  -d '{"name":"Ada Lovelace","email":"ada@example.com"}'
# -> {"id":1,"name":"Ada Lovelace","email":"ada@example.com"}

# 2. register two copies of the same book
curl -s -X POST $BASE/books -H 'Content-Type: application/json' \
  -d '{"isbn":"978-0-13-235088-4","title":"Clean Code","author":"Robert C. Martin"}'
# -> {"id":1,"isbn":"9780132350884",...,"available":true}
curl -s -X POST $BASE/books -H 'Content-Type: application/json' \
  -d '{"isbn":"978-0-13-235088-4","title":"Clean Code","author":"Robert C. Martin"}'
# -> {"id":2,...}   same ISBN, different id

# 3. list the catalogue
curl -s "$BASE/books?page=0&size=20"

# 4. borrow copy 1
curl -s -X POST $BASE/books/1/borrow -H 'Content-Type: application/json' \
  -d '{"borrowerId":1}'

# 5. a second member cannot take the same copy
curl -s -X POST $BASE/books/1/borrow -H 'Content-Type: application/json' \
  -d '{"borrowerId":2}'
# -> 409 BOOK_ALREADY_BORROWED

# 6. return it
curl -s -X POST $BASE/books/1/return -H 'Content-Type: application/json' \
  -d '{"borrowerId":1}'
```

---

## API reference

All endpoints live under `/api/v1`, consume and produce `application/json`, and return errors as
`application/problem+json`. There is no authentication — see [Assumptions](#assumptions).

### `POST /api/v1/borrowers` — register a borrower

Request:

```json
{ "name": "Ada Lovelace", "email": "ada@example.com" }
```

| Field | Rules |
|---|---|
| `name` | required, non-blank, ≤ 255 chars |
| `email` | required, well-formed, ≤ 320 chars, unique (case-insensitive) |

`201 Created` with a `Location` header:

```json
{ "id": 1, "name": "Ada Lovelace", "email": "ada@example.com" }
```

Errors: `400 VALIDATION_FAILED`, `409 EMAIL_ALREADY_REGISTERED`.

### `POST /api/v1/books` — register a book copy

Request:

```json
{ "isbn": "978-0-13-235088-4", "title": "Clean Code", "author": "Robert C. Martin" }
```

| Field | Rules |
|---|---|
| `isbn` | required, valid ISBN-10 or ISBN-13 incl. check digit; hyphens/spaces allowed and stripped |
| `title` | required, non-blank, ≤ 255 chars |
| `author` | required, non-blank, ≤ 255 chars |

`201 Created`:

```json
{ "id": 1, "isbn": "9780132350884", "title": "Clean Code",
  "author": "Robert C. Martin", "available": true }
```

Every call creates a **new copy with a new id**, so posting the same ISBN twice gives you two
independently borrowable books. If the ISBN is already on file with a different title or author,
the request is rejected with `409 ISBN_MISMATCH`.

Errors: `400 VALIDATION_FAILED`, `409 ISBN_MISMATCH`.

### `GET /api/v1/books` — list all books

Query parameters:

| Param | Default | Notes |
|---|---|---|
| `page` | `0` | zero-based |
| `size` | `20` | page size |
| `sort` | `id,asc` | e.g. `sort=id,desc` |
| `search` | – | free-text over title and author, or an exact ISBN |

`200 OK`:

```json
{
  "content": [
    { "id": 1, "isbn": "9780132350884", "title": "Clean Code",
      "author": "Robert C. Martin", "available": false },
    { "id": 2, "isbn": "9780132350884", "title": "Clean Code",
      "author": "Robert C. Martin", "available": true }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1, "last": true
}
```

### `GET /api/v1/books/{bookId}` — fetch one copy

`200 OK` with the same shape as a list entry. `404 RESOURCE_NOT_FOUND` if the id is unknown.

### `POST /api/v1/books/{bookId}/borrow` — borrow on behalf of a borrower

Request:

```json
{ "borrowerId": 1 }
```

`200 OK`:

```json
{
  "loanId": 1, "bookId": 1, "isbn": "9780132350884", "title": "Clean Code",
  "borrowerId": 1, "borrowerName": "Ada Lovelace",
  "borrowedAt": "2026-07-24T10:15:30Z", "returnedAt": null
}
```

Errors: `400 VALIDATION_FAILED`, `404 RESOURCE_NOT_FOUND` (book or borrower),
`409 BOOK_ALREADY_BORROWED`.

### `POST /api/v1/books/{bookId}/return` — return on behalf of a borrower

Request body is identical. `200 OK` returns the closed loan with `returnedAt` populated.

Errors: `403 BORROWER_MISMATCH` (someone else holds the loan), `404 RESOURCE_NOT_FOUND`,
`409 BOOK_NOT_BORROWED`.

### Supporting endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/borrowers` | paginated list of members |
| `GET /api/v1/borrowers/{id}` | one member |
| `GET /api/v1/borrowers/{id}/loans` | what that member currently has out |
| `GET /actuator/health` | liveness/readiness probes |

---

## Data model

The task describes a `Book` with an id, an ISBN, a title and an author. Storing all four on one
row would mean the rule *"two books with the same ISBN must have the same title and author"* is
only ever a **check in application code** — one bad migration or one direct `INSERT` and the data
is inconsistent. So internally the concept is split in two:

```
book_edition                    book_copy                borrow_record
------------                    ---------                -------------
isbn   (PK)  <---------------+  id   (PK)  <----------+  id            (PK)
title                        +--isbn (FK)             +--book_copy_id  (FK)
author                                                   borrower_id   (FK)
                                                         borrowed_at
borrower                                                 returned_at   (nullable)
--------
id (PK), name, email (unique)
```

* **`book_edition`** — the bibliographic identity, keyed by ISBN. Title and author are stored
  *exactly once per ISBN*, so it is structurally impossible for two copies of one ISBN to disagree.
* **`book_copy`** — a physical copy on the shelf. This is the `Book` the API exposes: its `id` is
  what borrow and return operate on, and registering the same ISBN five times creates five rows
  with five ids.
* **`borrow_record`** — one row per borrow event. `returned_at IS NULL` means the loan is open.
  Returned loans are kept rather than deleted, so the library gets a full lending history for free.

This satisfies all three ISBN rules from the spec:

| Rule | How |
|---|---|
| Same title + author, different ISBN → different books | Two `book_edition` rows, unrelated |
| Same ISBN → must share title and author | One `book_edition` row; nothing to disagree with |
| Multiple copies of one ISBN allowed | Many `book_copy` rows pointing at one edition |

The API surface still returns a flat `{ id, isbn, title, author }` object, so consumers never see
the split.

---

## Choice of database

**PostgreSQL.**

The deciding factor is requirement 8 — *no more than one member borrowing the same book id at a
time*. That is a correctness invariant under concurrency, and the cheapest reliable way to enforce
it is to let the database do it:

1. **It is a genuinely relational domain.** Borrowers, editions, copies and loans are a small,
   fixed set of entities joined by foreign keys. There is no schema-flexibility problem for a
   document store to solve, and every query is a join or a lookup by key.

2. **ACID transactions with row-level locking.** Borrowing is read-check-write. PostgreSQL gives
   `SELECT … FOR UPDATE` plus real transactions, so two concurrent borrows of the same copy are
   serialised instead of both passing the availability check.

3. **Partial unique indexes.** PostgreSQL can express exactly the constraint the spec asks for:

   ```sql
   CREATE UNIQUE INDEX ux_borrow_record_active_copy
       ON borrow_record (book_copy_id)
       WHERE returned_at IS NULL;
   ```

   At most one *open* loan per copy, enforced by the storage engine — while still allowing an
   unlimited history of closed loans on the same copy. MySQL cannot do this without a generated
   column workaround; MongoDB cannot do it at all across documents.

4. **Referential integrity.** Foreign keys make an orphaned loan or a copy of a non-existent
   edition impossible, which matters more than raw write throughput for a system whose write
   volume is "a librarian scanning barcodes".

5. **Operationally boring.** Free, mature, available as a managed service on every cloud, and a
   first-class citizen in Spring Data JPA, Flyway and Testcontainers.

**What was considered and rejected:**

| Option | Why not |
|---|---|
| H2 / in-memory | Data vanishes on restart, and it cannot express the partial unique index the core invariant depends on — the schema would not match what ships. Not used anywhere in this project, including tests. |
| MySQL | Fine choice, but no partial unique indexes; the invariant would have to be faked with a generated column or left to application code alone. |
| MongoDB | The invariant spans documents. Enforcing it would mean either a single-copy-embedded design that fights the "many copies" requirement, or application-level locking — strictly worse than a unique index. |

**Schema management** is handled by Flyway (`src/main/resources/db/migration`), with Hibernate set
to `ddl-auto: validate`. Migrations are versioned and reviewable; the app never mutates its own
schema at boot.

---

## How "one borrower per copy" is guaranteed

Three independent layers, so a bug in any one of them does not corrupt data:

1. **Pessimistic row lock.** `LendingService.borrow()` starts with
   `BookCopyRepository.findByIdForUpdate(bookId)`, which issues `SELECT … FOR UPDATE` on the copy
   row. Concurrent borrows of the same copy queue behind it. Lock first, then check — the other
   order leaves a race window.
2. **Explicit check inside the transaction.** With the lock held, an active `borrow_record` for
   that copy means a `409 BOOK_ALREADY_BORROWED`.
3. **Partial unique index.** If anything ever bypasses the service layer, `INSERT` fails and the
   `DataIntegrityViolationException` is translated back into the same clean `409`.

Copies of the *same ISBN* are unaffected: the lock and the index are keyed on `book_copy_id`, so
copy 2 stays borrowable while copy 1 is out.

`ConcurrentBorrowIntegrationTest` proves it: twenty threads race for one copy against a real
PostgreSQL, and exactly one wins.

### The second race: two members registering the same new ISBN

Registering a copy has to create the edition if that ISBN is new. "Look it up, insert if absent" is
racy — both requests can see nothing and both insert. Catching the resulting constraint violation
does not rescue it either: a failed flush leaves the Hibernate session unusable and the transaction
marked rollback-only, so the recovery read fails too.

`BookEditionRepository.insertIfAbsent` collapses the check and the insert into one atomic statement:

```sql
INSERT INTO book_edition (isbn, title, author) VALUES (?, ?, ?)
ON CONFLICT DO NOTHING
```

(The bare `ON CONFLICT DO NOTHING`, without a target column, is used on purpose: `isbn` is the only
unique constraint on the table, so it means the same thing as naming the target explicitly.)

The service then reads the row back and compares title/author against it. Whoever wins, both
requests validate against the same winning row, so a concurrent *conflicting* registration still
gets the same `409 ISBN_MISMATCH` a sequential one would — with no exception handling in the path.

---

## Error format

Every error is [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) `application/problem+json`:

```json
{
  "type": "https://library.example.com/problems/book_already_borrowed",
  "title": "Conflict",
  "status": 409,
  "detail": "Book 1 is currently borrowed and cannot be borrowed again until it is returned",
  "instance": "/api/v1/books/1/borrow",
  "errorCode": "BOOK_ALREADY_BORROWED",
  "timestamp": "2026-07-24T10:15:30.123Z"
}
```

Validation failures add a per-field breakdown:

```json
{
  "status": 400,
  "detail": "One or more fields failed validation",
  "errorCode": "VALIDATION_FAILED",
  "violations": [
    { "field": "email", "message": "email must be a well-formed email address" },
    { "field": "name",  "message": "name must not be blank" }
  ]
}
```

Branch on `errorCode`, not on prose.

| `errorCode` | Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | A field failed Bean Validation |
| `MALFORMED_REQUEST` | 400 | Body is absent or not valid JSON |
| `INVALID_PARAMETER` | 400 | A path/query parameter has the wrong type |
| `BORROWER_MISMATCH` | 403 | A different member holds this loan |
| `INVALID_SORT` | 400 | `sort=` names a field the entity doesn't have |
| `RESOURCE_NOT_FOUND` | 404 | Unknown borrower or book id |
| `ENDPOINT_NOT_FOUND` | 404 | No such route |
| `METHOD_NOT_ALLOWED` | 405 | Right route, wrong HTTP verb |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` is not `application/json` |
| `EMAIL_ALREADY_REGISTERED` | 409 | That email is already a member |
| `ISBN_MISMATCH` | 409 | ISBN on file with a different title/author |
| `BOOK_ALREADY_BORROWED` | 409 | Copy is on loan |
| `BOOK_NOT_BORROWED` | 409 | Copy is on the shelf; nothing to return |
| `CONSTRAINT_VIOLATION` | 409 | A database constraint rejected the write |
| `INTERNAL_ERROR` | 500 | Unexpected; details are logged, not returned |

Stack traces and internal messages are never sent to clients.

---

## Request tracing

Every request is tagged with a short correlation id. It is returned in the `X-Correlation-Id`
response header and printed on every log line that request produces, so one request can be pulled
out of interleaved concurrent traffic with a single `grep`. Send your own `X-Correlation-Id` header
to have a trace span multiple services; otherwise one is generated.

Method-level tracing is handled by an AOP aspect (`logging/LoggingAspect`) that wraps every
controller and service call and logs entry (with arguments), exit (with outcome and elapsed millis),
and any exception — no method has to remember to log, and the format can't drift. It's at `DEBUG`, so
production is quiet by default:

```bash
LOG_LEVEL_APP=DEBUG docker compose up
```

A failing call then reads as a contiguous, indented call tree under one id:

```
DEBUG [library-api,a1b2c3d4] --> BookController.borrow(bookId=1, request=BorrowRequest[borrowerId=2])
DEBUG [library-api,a1b2c3d4] --> LendingService.borrow(bookId=1, borrowerId=2)
ERROR [library-api,a1b2c3d4] <-- LendingService.borrow threw BookAlreadyBorrowedException: Book 1 is currently borrowed ... (4 ms)
```

Arguments whose parameter name contains `password`, `secret`, or `token` are redacted to `****`.

---

## Configuration

Every setting is an environment variable with a sane default — no profiles, no per-environment
config files.

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/library` | JDBC URL |
| `DATABASE_USERNAME` | `library` | DB user |
| `DATABASE_PASSWORD` | `library` | DB password |
| `DATABASE_POOL_MAX` | `10` | Max HikariCP connections |
| `DATABASE_POOL_MIN` | `2` | Min idle connections |
| `PORT` | `8080` | HTTP listen port |
| `VIRTUAL_THREADS_ENABLED` | `true` | Serve requests on virtual threads |
| `LOG_LEVEL_ROOT` | `INFO` | Root log level |
| `LOG_LEVEL_APP` | `INFO` | Application log level; set `DEBUG` for the aspect method trace |

Copy `.env.example` to `.env` for Compose. `.env` is gitignored; no credentials are committed.

---

## Testing

```bash
./mvnw test        # unit tests only — no Docker required
./mvnw verify      # unit + integration (needs a running Docker daemon)
```

On Windows use `.\mvnw` in place of `./mvnw`. The wrapper downloads a pinned Maven version on
first run, so no local Maven install is needed.

Surefire excludes `**/integration/**` and Failsafe picks it up, so the fast suite stays runnable on
a machine with no container runtime.

| Layer | What it covers |
|---|---|
| `BookServiceTest` | ISBN normalisation, edition reuse, mismatch rejection, distinct copy ids |
| `LendingServiceTest` | Borrow/return state machine, lock-before-check ordering, every failure branch |
| `IsbnNormalizerTest` | Hyphen/space stripping, ISBN-10/13 check-digit maths, transposition and length rejections |
| `LoggingAspectTest` | Aspect passes return values through and re-throws exceptions unwrapped |
| `LibraryApiIntegrationTest` | Full HTTP round-trips: status codes, headers, error bodies, pagination, search, the three ISBN rules |
| `ConcurrentBorrowIntegrationTest` | 20 threads, 1 copy, exactly 1 winner |

Integration tests run against a real PostgreSQL 16 container via Testcontainers. This is
deliberate: the central invariant depends on a PostgreSQL partial unique index, so an in-memory
substitute would validate a schema that is never deployed. A fixed `Clock` bean is injected so time-dependent
assertions are exact rather than approximate.

---

## Project layout

```
src/main/java/com/library/api/
├── config/       ClockConfig, OpenApiConfig
├── controller/   HTTP only — routing, status codes, no business logic
├── domain/       JPA entities; invariants live on the entities
├── logging/      Correlation-id filter + AOP method-trace aspect
├── validation/   ISBN normalisation and the @Isbn constraint
├── dto/          Request/response records, isolated from the entities
├── exception/    Typed exceptions + one RestControllerAdvice
├── repository/   Spring Data JPA interfaces
└── service/      Transaction boundaries and business rules
src/main/resources/
├── application.properties           (config; all keys env-overridable)
└── db/migration/
    └── postgresql/V1__init.sql      (schema)
```

Notes on the code:

* **Constructor injection everywhere**, no field injection — dependencies are explicit and the
  classes are unit-testable without a Spring context.
* **DTOs are Java records**, immutable and separate from the entities, so the wire format and the
  schema can evolve independently.
* **Entities never leave the service layer**; `open-in-view` is disabled so no lazy load can fire
  during serialisation.
* **No N+1 queries**: the catalogue endpoint uses `join fetch` for editions and one batched query
  for availability, regardless of page size.
* **`Clock` is injected** rather than calling `Instant.now()` inline.
* **Exceptions carry their own status and error code**, which keeps HTTP concerns out of the
  services and the handler down to a table of one-liners.

---

## 12-Factor conformance

| # | Factor | How |
|---|---|---|
| I | Codebase | One repo, one deployable artifact |
| II | Dependencies | Declared in `pom.xml`; the image builds from a clean Maven cache, nothing implicit from the host |
| III | Config | Everything from environment variables; `.env` is gitignored, no secrets in the repo |
| IV | Backing services | PostgreSQL reached only via `DATABASE_URL` — swap in a managed instance with no code change |
| V | Build, release, run | Multi-stage Dockerfile separates build from run; the runtime image contains no build tooling |
| VI | Processes | Stateless; no session state, no local disk writes — all state is in PostgreSQL |
| VII | Port binding | Self-contained Tomcat on `${PORT}`; no external app server |
| VIII | Concurrency | Scales out horizontally; correctness under multiple instances is guaranteed by DB constraints, not in-process locks |
| IX | Disposability | Fast boot, `server.shutdown: graceful` drains in-flight requests on SIGTERM |
| X | Dev/prod parity | Same PostgreSQL 16 image in Compose, in tests (Testcontainers) and in production |
| XI | Logs | Unbuffered to stdout as an event stream; no log files, no rotation |
| XII | Admin processes | Flyway migrations run as part of the same codebase and image |

Partial: **XII** — migrations run at application startup rather than as a separate release-phase
job. That is the pragmatic choice at this size; at scale they would be split into a dedicated
release step so that N app instances do not contend on the migration lock.

---

## Assumptions

Full list with reasoning: **[ASSUMPTIONS.md](ASSUMPTIONS.md)**.
