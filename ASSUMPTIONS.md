# Assumptions

Every decision made is listed here with the reasoning behind it and,
where relevant, what would change if the assumption is wrong.

---

## 1. Domain and data model

**1.1 "Book id" means a physical copy, not an edition.**
The spec says multiple copies of one ISBN are allowed, that each gets a different id, and that
borrowing targets a book id. Together those only make sense if the id identifies a *copy*.
Internally this is modelled as `book_copy` rows sharing a `book_edition` row, which is what makes
"same ISBN ⇒ same title and author" a property of the schema rather than a check in code. The API
still returns a flat `{id, isbn, title, author}` object.

**1.2 ISBNs are normalised, then fully validated including the check digit.**
Hyphens and spaces are stripped and the ISBN-10 check character is upper-cased, so
`978-0-13-235088-4`, `978 0 13 235088 4` and `9780132350884` are the same book. Normalisation is not
cosmetic: the ISBN is the primary key of `book_edition`, so without it one book could be registered
twice under two spellings of its own identifier.

The value is then checked as a real ISBN-10 or ISBN-13, check digit included, by the `@Isbn`
constraint. A length-and-digits regex was rejected as insufficient: it accepts an 11-digit string,
and it accepts any single mistyped or transposed digit. Since the ISBN is a primary key here, a typo
does not fail loudly — it silently creates a second edition that will never merge with the real one,
so it is worth catching at the edge of the system.

The known cost: a small number of legitimately published books, mostly older or self-published, do
carry invalid check digits, and this API will reject them. That is the deliberate trade — a stricter
rule on a primary key, at the price of a rare manual override. If the library needs those entries,
relaxing `IsbnNormalizer.isValid` to a length-only check is a one-method change, and the constraint
is isolated in `com.library.api.validation` precisely so that decision stays in one place.

**1.3 Title and author comparison is case- and whitespace-insensitive.**
`"clean code"`, `" Clean Code "` and `"CLEAN CODE"` are treated as the same title when checking an
ISBN against what is on file. The originally registered form is the one stored and returned.
Rejecting a copy over a stray trailing space would be user-hostile.

**1.4 Email is the natural business key for a borrower.**
Names are not unique; emails are. A duplicate registration returns `409` rather than silently
returning the existing member, so the caller always knows which happened. Emails are stored
lower-cased and the uniqueness index is case-insensitive.

**1.5 Borrowers and books are never deleted.**
No `DELETE` endpoints. Removing a borrower who has an open loan, or a copy that is currently out,
raises questions the spec does not answer. Soft-deletion (a `withdrawn_at` column) would be the
natural extension.

**1.6 Book metadata is immutable after registration.**
There is no `PUT /books/{id}`. Correcting a typo in a title would change it for every copy sharing
that ISBN, which needs a deliberate policy decision.

---

## 2. Borrowing rules

**2.1 A borrower may hold any number of books simultaneously.**
No borrowing limit is specified, so none is imposed. A limit would go in `LendingService.borrow()`
as a count of the borrower's active loans.

**2.2 There are no due dates, loan periods, renewals, reservations or fines.**
None are mentioned. `borrow_record` carries `borrowed_at` and `returned_at`; a `due_at` column and
an overdue query would slot in without restructuring anything.

**2.3 Only the borrower holding the loan may return the book — `403` otherwise.**
The spec frames both actions as "on behalf of a borrower", so the borrower id is required on
return too, and is verified. The alternative (anyone may return any book, as at a real returns
desk) is defensible, but silently accepting a mismatched borrower id would make the parameter
meaningless. If the library prefers desk behaviour, drop the `BorrowerMismatchException` check.

**2.4 Returning a book that is not on loan is a `409`, not a silent success.**
Making the operation idempotent would hide client bugs — the caller almost certainly has stale
state. The `409` says so explicitly.

**2.5 Borrowing is not idempotent.**
The same borrower calling borrow twice on the same copy gets `409 BOOK_ALREADY_BORROWED` on the
second call. The requirement is "no more than one member borrowing the same book id at a time",
which the existing loan already satisfies. An `Idempotency-Key` header would be the clean fix for
retry-safe clients.

**2.6 A returned copy becomes immediately borrowable again.**
No shelving delay or inspection step.

**2.7 Loan history is retained.**
Returns set `returned_at` rather than deleting the row. Costs nothing and makes any future
reporting requirement trivial.

---

## 3. API design

**3.1 Versioned base path `/api/v1`.**
Lets a v2 coexist later without breaking clients.

**3.2 Borrow and return are `POST` on sub-resources of the book.**
`POST /books/{id}/borrow` and `POST /books/{id}/return`. These are state transitions, not resource
creation or replacement, so `POST` on an action sub-resource is the least surprising mapping. A
loans-as-resources design (`POST /loans`, `PATCH /loans/{id}`) is more purist but adds a resource
the spec never asks for.

**3.3 Borrow and return return `200`, registration returns `201` with `Location`.**
Registration creates an addressable resource; borrowing transitions one that already exists.

**3.4 `GET /books` is paginated, defaulting to 20 per page.**
"Get a list of all books" with an unbounded result set is a production hazard once the catalogue
grows. Pagination is opt-out via `size`. The response envelope is hand-rolled rather than Spring's
serialised `Page`, whose shape is unstable across versions.

**3.5 `GET /books` returns every copy, including ones on loan.**
It is the catalogue, not the shelf. Each entry carries an `available` flag so clients can filter.

**3.6 Availability is exposed even though it was not requested.**
It costs one batched query per page and prevents clients from having to probe with failed borrow
attempts.

**3.7 Timestamps are UTC ISO-8601.**
`Instant` throughout, stored as `TIMESTAMPTZ`, serialised as `2026-07-24T10:15:30Z`. No local time
zones anywhere.

**3.8 Errors follow RFC 7807 with a stable `errorCode`.**
Clients branch on `errorCode`; `detail` is for humans and may be reworded.

---

## 4. Security and operations

**4.1 There is no authentication or authorisation.**
The task describes an API with no notion of identity beyond a borrower id passed as data, and adding
a half-designed auth scheme would obscure the actual requirements. **This API is not
internet-facing as written.** In production it would sit behind an API gateway or add Spring
Security with OAuth2/JWT, at which point the borrower id would come from the token rather than the
request body for actions a member performs on their own behalf.

**4.2 The API user is trusted staff or a trusted service.**
There is no rate limiting, no request quota and no audit trail beyond application logs.

**4.3 Deployment is expected to be multi-instance.**
Hence the reliance on database constraints rather than in-process locking. A `synchronized` block
or a JVM-local lock would silently stop working the moment a second replica is deployed.

**4.4 Default credentials in `docker-compose.yml` are for local development only.**
They exist so `docker compose up` works with no setup. Real deployments supply
`DATABASE_PASSWORD` from a secret store.

**4.5 Migrations run at application startup.**
Convenient at this size. At scale they would move to a dedicated release-phase job so that N
starting instances do not contend on the Flyway lock.

**4.6 Actuator endpoints are exposed without authentication.**
Only `health`, `info` and `metrics`, with health details hidden. In production these would be bound
to an internal port or secured.

---

## 5. Technical choices

**5.1 Maven.**
Satisfies "use a package manager". Maven's declarative POM is faster to review, which
matters for an assessment.

**5.2 PostgreSQL over an in-memory database.**
Reasoning in [README § Choice of database](README.md#choice-of-database). The short version: the
core invariant is a PostgreSQL partial unique index, so an in-memory substitute like H2 would not
support the very feature the correctness of the system rests on. End-to-end behaviour is instead
verified by the Compose smoke test in CI, which runs against the same PostgreSQL image as production.

**5.3 Flyway over `hibernate.ddl-auto`.**
Schema changes are versioned, reviewable and repeatable. Hibernate is set to `validate` so a drift
between entities and migrations fails at boot rather than in production.

**5.4 Pessimistic locking over optimistic locking for borrowing.**
Contention on a single copy is genuinely likely (two people at the desk, a retried request), and
pessimistic locking avoids the retry loop optimistic locking would require. It is scoped to a
single row held for microseconds.

**5.5 Virtual threads are enabled by default.**
The workload is I/O-bound on the database, which is exactly what virtual threads suit. Disable with
`VIRTUAL_THREADS_ENABLED=false`; pinning is not a concern here because no `synchronized` block
wraps a blocking call.

**5.6 No caching layer.**
Premature at this scale, and a cache in front of availability data would need careful invalidation
on every borrow and return.
