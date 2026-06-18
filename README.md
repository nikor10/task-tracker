# Task Tracker — Spring Boot REST API

A multi-user task-management backend built with **Spring Boot 4.1.0** and **Java 21**. Users own
projects, projects contain tasks, and tasks can be assigned to other users. Every data access is
scoped to the signed-in user, every task change is recorded in an append-only audit trail, and
assigning a task triggers an asynchronous email notification.

The project is deliberately built as a **clean layered architecture** and showcases three
non-trivial "advanced" features — dynamic search, asynchronous event-driven email, and an audit
log — each chosen to demonstrate a *different* engineering concern.

---

## Table of contents

1. [Technology stack](#1-technology-stack)
2. [High-level architecture](#2-high-level-architecture)
3. [Project structure](#3-project-structure)
4. [The domain model (entities)](#4-the-domain-model-entities)
5. [Persistence: H2 + Flyway](#5-persistence-h2--flyway)
6. [Repositories](#6-repositories)
7. [Services (business logic)](#7-services-business-logic)
8. [Controllers (REST API)](#8-controllers-rest-api)
9. [Security & authorization](#9-security--authorization)
10. [Exception handling](#10-exception-handling)
11. [Advanced feature 1 — dynamic task search (Specifications)](#11-advanced-feature-1--dynamic-task-search-specifications)
12. [Advanced feature 2 — asynchronous email notifications](#12-advanced-feature-2--asynchronous-email-notifications) ← **requires per-machine email setup**
13. [Advanced feature 3 — audit trail (activity log)](#13-advanced-feature-3--audit-trail-activity-log)
14. [API documentation (Swagger / OpenAPI)](#14-api-documentation-swagger--openapi)
15. [Running the application](#15-running-the-application)
16. [Seed data & test accounts](#16-seed-data--test-accounts)
17. [End-to-end request walkthrough](#17-end-to-end-request-walkthrough)
18. [Design decisions & known caveats](#18-design-decisions--known-caveats)

---

## 1. Technology stack

| Concern | Technology | Notes |
|---|---|---|
| Language / runtime | Java 21 | `pom.xml` `<java.version>21</java.version>` |
| Framework | Spring Boot **4.1.0** | Bleeding-edge; auto-configuration is split into per-feature modules |
| Web layer | `spring-boot-starter-webmvc` | Classic servlet MVC, `@RestController` |
| Persistence | Spring Data JPA + Hibernate | Repositories, Criteria API |
| Database | **H2 in-memory** (`jdbc:h2:mem:testdb`) | Recreated on every startup |
| Schema migrations | **Flyway** (`flyway-core` + `spring-boot-flyway`) | Versioned SQL, `ddl-auto=none` |
| Security | Spring Security | HTTP Basic, BCrypt, role-based |
| Validation | `spring-boot-starter-validation` | Jakarta Bean Validation (`@NotBlank`, `@Size`, …) |
| Email | `spring-boot-starter-mail` | `JavaMailSender` over Gmail SMTP |
| Async | Spring `@Async` + `@EnableAsync` | Dedicated thread pool |
| API docs | springdoc-openapi **2.8.5** | Swagger UI at `/swagger-ui.html` |
| Frontend | Static SPA (`static/index.html`, `app.js`) | Plain HTML/JS that consumes the API |

> **Spring Boot 4 note:** In Boot 4 auto-configuration was broken into smaller modules. Flyway no
> longer auto-runs from `flyway-core` alone — the project explicitly adds the
> `spring-boot-flyway` module so `FlywayAutoConfiguration` is present and migrations run on startup.

---

## 2. High-level architecture

The app is a **strict layered architecture**. Each layer has exactly one responsibility, and
dependencies only ever point *downward*:

```
            HTTP request (JSON + HTTP Basic credentials)
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │  Security filter chain  (WHO are you?)   │  ← authentication, before any controller
        └─────────────────────────────────────────┘
                              │
                              ▼
   Controller   (@RestController)  — HTTP mapping, @Valid, (de)serialization, pagination
                              │
                              ▼
   Service      (@Service)         — business logic, authorization (WHAT may you do?), @Transactional
                              │
                              ▼
   Repository   (Spring Data JPA)  — data access, derived queries, JPQL, Specifications
                              │
                              ▼
   Hibernate / JPA  → JDBC  →  H2 in-memory database
```

Two cross-cutting concerns sit beside this stack:

- **`@RestControllerAdvice`** — turns exceptions thrown anywhere below into clean JSON error
  responses with the right HTTP status.
- **Events** — the service layer publishes domain events; an async listener reacts (email) without
  the service knowing or caring.

**Key principle:** *authentication* (proving identity) happens in the security filter chain
**before** the controller; *authorization* (deciding what data you may touch) happens in the
**service layer**, close to the data.

---

## 3. Project structure

```
src/main/java/com/nikola/task_tracker_project_spring/
├── TaskTrackerProjectSpringApplication.java   # @SpringBootApplication entry point
├── config/
│   ├── SecurityConfig.java        # filter chain, UserDetailsService, BCrypt bean
│   ├── AuthFacade.java            # helper to read the signed-in user from the security context
│   ├── AsyncConfig.java           # @EnableAsync + the notification thread pool
│   └── SwaggerConfig.java         # OpenAPI info block + HTTP Basic "Authorize" button
├── controller/
│   ├── TaskController.java        # /api/.../tasks   (9 endpoints)
│   ├── ProjectController.java     # /api/projects    (7 endpoints)
│   ├── UserController.java        # /api/users       (4 endpoints)
│   └── AuthController.java        # /api/me          (current-user probe)
├── service/
│   ├── TaskService.java           # task orchestration, the "both" visibility rule
│   ├── ProjectService.java        # project rules, owner-gate
│   ├── UserService.java           # user CRUD, BCrypt password encoding
│   └── TaskActivityRecorder.java  # builds & persists audit rows (field diffing)
├── repository/
│   ├── TaskRepository.java        # JpaRepository + JpaSpecificationExecutor + JPQL
│   ├── ProjectRepository.java
│   ├── UserRepository.java
│   ├── TaskActivityRepository.java
│   └── TaskSpecifications.java    # composable Criteria-API WHERE fragments
├── entity/
│   ├── User.java  Project.java  Task.java  TaskActivity.java
│   └── TaskStatus.java  TaskPriority.java  TaskActivityAction.java   (enums)
├── event/
│   ├── TaskAssignedEvent.java     # immutable record carrying plain values
│   └── TaskAssignedListener.java  # @Async @TransactionalEventListener(AFTER_COMMIT)
└── exception/
    ├── GlobalExceptionHandler.java               # @RestControllerAdvice
    └── UserNotFoundException / ProjectNotFoundException / TaskNotFoundException

src/main/resources/
├── application.properties         # datasource, mail, H2 console config
├── db/migration/
│   ├── V1__create_schema.sql      # tables
│   ├── V2__seed_data.sql          # demo users/projects/tasks
│   └── V3__task_activity.sql      # audit table (no FKs)
└── static/
    ├── index.html  app.js         # the SPA frontend
```

---

## 4. The domain model (entities)

Four JPA entities and three enums.

### `User`
| Field | Rules |
|---|---|
| `id` | identity-generated, read-only |
| `username` | `@NotBlank`, `@Size(min = 3)`, **unique** |
| `email` | `@NotBlank`, `@Email`, **unique** |
| `password` | `@NotBlank`, `@Size(min = 8)`, **`@JsonProperty(WRITE_ONLY)`** — accepted in requests, never serialized back |
| `createdAt` | set by `@PrePersist`, not updatable |

### `Project`
- `name` (`@Size(min = 3, max = 50)`), optional `description`.
- `@ManyToOne owner` (the user who owns it).
- `@OneToMany(mappedBy = "project", cascade = ALL, orphanRemoval = true) tasks` — deleting a
  project cascade-deletes its tasks; `mappedBy` makes `Task` the *owning* side of the FK.

### `Task`
- `title` (`@Size(min = 3, max = 100)`), optional `description`.
- `status` (`TaskStatus`) and `priority` (`TaskPriority`), both `@NotNull` and stored as
  `@Enumerated(EnumType.STRING)` (the enum *name* is written, not its ordinal — robust to reordering).
- optional `dueDate`.
- `@ManyToOne(LAZY) project` — annotated `@JsonIgnore` to break the Project↔Task serialization cycle.
- `@ManyToOne(LAZY) assignee` — the user the task is assigned to (nullable).

### `TaskActivity` (audit row — see [§13](#13-advanced-feature-3--audit-trail-activity-log))
Append-only. **No public setters.** Stores `taskId`/`userId` as plain numbers (not associations)
plus `taskTitle`/`username` snapshots, so a row stays readable after the task/user is deleted.

### Enums
- `TaskStatus` = `TODO | IN_PROGRESS | COMPLETED`
- `TaskPriority` = `LOW | MEDIUM | HIGH`
- `TaskActivityAction` = `CREATE | UPDATE | DELETE`

All entities use `@PrePersist` to stamp `createdAt` and `@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})`
so Hibernate's lazy-proxy internals don't leak into JSON.

---

## 5. Persistence: H2 + Flyway

- **Database:** H2 runs **in-memory** (`spring.datasource.url=jdbc:h2:mem:testdb`). It is created
  fresh on every startup and discarded on shutdown — perfect for a demo, zero external setup.
  The H2 web console is enabled at **`/h2-console`** (JDBC URL `jdbc:h2:mem:testdb`, user `sa`, no password).

- **Flyway owns the schema, not Hibernate.** `spring.jpa.hibernate.ddl-auto=none` deliberately turns
  off Hibernate's automatic schema generation so the two never fight. The schema is defined entirely
  by versioned SQL migrations that Flyway applies in order on startup:

  | Migration | Purpose |
  |---|---|
  | `V1__create_schema.sql` | Creates `users`, `projects`, `tasks` with FK constraints |
  | `V2__seed_data.sql` | Inserts demo users, projects and tasks |
  | `V3__task_activity.sql` | Creates the `task_activity` audit table (intentionally **no** FK constraints) |

  Flyway records applied versions in its `flyway_schema_history` table, so each migration runs once
  and the schema is reproducible and version-controlled.

---

## 6. Repositories

All repositories are **interfaces** — Spring Data JPA generates the implementing proxy at runtime.
Three styles of query are used:

1. **Derived queries** — the method *name* is the query. e.g.
   `findByOwnerId(Long, Pageable)`, `findByAssigneeId(Long)`, `findByDueDate(LocalDate)`,
   `findByProjectIdAndStatus(...)`.

2. **`@Query` (JPQL)** — for anything the method-name DSL can't express cleanly. JPQL navigates the
   **object graph**, not SQL tables. Examples:
   - `TaskRepository.findOverdueTasks` — `dueDate < :date AND status <> COMPLETED ORDER BY dueDate`.
   - `findOverdueTasksForUser` / `findByDueDateForUser` — same, but with the visibility predicate
     `(t.project.owner.id = :userId OR t.assignee.id = :userId)`.
   - `ProjectRepository.findAccessibleProjects` — owner **OR** "has a task assigned to me" (subquery).
   - `UserRepository.searchByUsernameOrEmail` — case-insensitive `LIKE` over two columns.

3. **`JpaSpecificationExecutor`** — `TaskRepository` also extends this, unlocking
   `findAll(Specification, Pageable)` for the dynamic search in [§11](#11-advanced-feature-1--dynamic-task-search-specifications).

`Page` / `Pageable` give pagination + sorting for free across the list endpoints.

---

## 7. Services (business logic)

Services hold the business rules **and the authorization**. They are where `@Transactional` lives.

### `UserService`
- `createUser` — **BCrypt-encodes the raw password** before saving (`passwordEncoder.encode(...)`).
- `getAllUsers`, `getUserById` (404 via `UserNotFoundException`), `searchUsers` (trims keyword).

### `ProjectService` — the **owner-gate** shape
- `getProjectById` → loads, then `assertVisible`: admin OK, otherwise the caller **must be the owner**,
  else `AccessDeniedException`. Used by read/update/delete so non-owners can't touch a project.
- `assertExists` — existence-only (404 if missing) **without** the owner gate. Used where row-level
  authorization is applied to the *results* instead (task search).
- `createProject` — forces ownership: a regular user always becomes the owner of what they create;
  an admin must name an existing owner.
- `findAll` / `getAccessibleProjects` — admins see everything, regular users see only their own
  (or, for "accessible", projects where they're an assignee).

### `TaskService` — the **"both" visibility** shape, and the orchestrator
- `getTaskById` → load (404) then `assertVisible`: visible if admin **OR** owns the task's project
  **OR** is the task's assignee; else 403.
- `addTask` (`@Transactional`) — see the orchestration in [§17](#17-end-to-end-request-walkthrough).
- `updateById` / `deleteById` (`@Transactional`) — gate visibility, **record the audit rows**, then mutate.
- `getAllTasks` — the dynamic search (builds a `Specification`).
- `getTasksDueToday` / `getOverdueTasks` / `getTasksByUser` — admin sees all; otherwise scoped to the caller.

**Why `@Transactional` matters:** a method annotated `@Transactional` runs as one atomic unit —
everything commits together on a normal return, or rolls back together if a `RuntimeException`
escapes. This is what guarantees that a task change and its audit rows are never out of sync.

---

## 8. Controllers (REST API)

Thin web adapters. They map HTTP → service calls, apply `@Valid`, and return objects that Spring
serializes to JSON. They contain **no business logic**.

### Tasks — `TaskController` (`/api`)
| Method | Path | Description |
|---|---|---|
| `POST` | `/projects/{projectId}/tasks` | Create a task in a project (owner only) |
| `GET` | `/tasks/{id}` | Get one task (owner or assignee) |
| `GET` | `/tasks/{id}/activity` | Paged audit trail, newest first (`size=20, sort=createdAt DESC`) |
| `GET` | `/projects/{projectId}/tasks` | **Search** — 8 optional filters + paging (`size=10, sort=dueDate`) |
| `PUT` | `/tasks/{id}` | Update a task (owner only) |
| `DELETE` | `/tasks/{id}` | Delete a task (owner only) |
| `GET` | `/tasks/due-today` | Caller's tasks due today |
| `GET` | `/users/{userId}/tasks` | Tasks assigned to a user |
| `GET` | `/tasks/overdue` | Caller's incomplete past-due tasks |

### Projects — `ProjectController` (`/api/projects`)
`POST /` · `GET /` (paged) · `GET /{id}` · `GET /by-owner/{ownerId}` · `GET /accessible` ·
`PUT /{id}` · `DELETE /{id}`

### Users — `UserController` (`/api/users`)
`POST /` (**admin-only**) · `GET /` · `GET /{id}` · `GET /search?keyword=`

### Auth — `AuthController`
`GET /api/me` → `{ "username": "...", "admin": true|false }`. The SPA calls this to confirm login.

**Binding annotations used:** `@PathVariable` (id in the URL), `@RequestParam` (query filters,
optional), `@RequestBody @Valid` (JSON body, validated → `400` on violation *before the service
runs*), and `Pageable` (`?page=&size=&sort=`).

---

## 9. Security & authorization

Configured in `SecurityConfig` via a `SecurityFilterChain` bean.

**Authentication = HTTP Basic.** Each request carries `Authorization: Basic <base64 user:pass>`.
There are **two kinds of account**:

- **`admin`** — a single **in-memory** account (`UserDetailsService`), role `ROLE_ADMIN`, with **no
  row in the `users` table**. Admin sees and does everything.
- **everyone else** — looked up in the DB by username; role `ROLE_USER`. Their BCrypt-hashed password
  (column `users.password`) is compared by Spring Security against the supplied credentials.

**URL rules:**
```
/h2-console/**                         → permitAll
/, /index.html, /app.js, /favicon.ico  → permitAll   (SPA shell must load before login)
/v3/api-docs/**, /swagger-ui/**, ...   → permitAll   (API docs are public)
POST /api/users                        → hasRole("ADMIN")   (only admins register users)
/api/**                                → authenticated      (everything else needs a login)
```

**Other hardening / behavior:**
- **Custom JSON 401** — instead of the standard `WWW-Authenticate: Basic` header (which makes the
  browser pop up its native credential dialog), the app returns a plain
  `{"status":401,"message":"Authentication required"}`. This lets the SPA own the login experience.
- `csrf` disabled (stateless API), `frameOptions = sameOrigin` (so the H2 console can render in a frame).
- **`BCryptPasswordEncoder`** bean — one-way salted password hashing.
- **`AuthFacade`** — a small helper that reads the current user from the `SecurityContext`:
  `username()`, `isAdmin()`, `currentUser()`, `currentUserId()`. For the in-memory admin
  `currentUserId()` returns `null` (no DB row) — which is exactly why the per-row owner/assignee
  checks always test `isAdmin()` first and short-circuit.

**Authorization is two-shaped, on purpose:**
- **Owner-gate** (projects, and single-task reads): non-owner → `AccessDeniedException` (or 404 to
  hide existence). One resource, one decision.
- **Per-row visibility** (task lists): unauthorized rows simply *never appear* in the result set —
  the visibility rule is pushed into the SQL `WHERE` (see [§11](#11-advanced-feature-1--dynamic-task-search-specifications)).

---

## 10. Exception handling

`GlobalExceptionHandler` is a single `@RestControllerAdvice` that catches exceptions thrown
*anywhere* below the controller and converts them into a uniform JSON body
`{ status, message, timestamp }` with the correct HTTP status:

| Exception | HTTP status |
|---|---|
| `AccessDeniedException` (Spring Security) | **403 Forbidden** |
| `UserNotFoundException` | **404 Not Found** |
| `ProjectNotFoundException` | **404 Not Found** |
| `TaskNotFoundException` | **404 Not Found** |

Custom exceptions extend `RuntimeException` (unchecked → no `throws` clutter, and they trigger
`@Transactional` rollback automatically). Exceptions **bubble up the call stack** from the service,
through the controller (no `try/catch` there), into Spring's dispatcher, which routes them to the
matching `@ExceptionHandler`.

> **Design subtlety:** the project owner-gate throws **404** (not 403) for a project that exists but
> isn't yours. Returning 403 would *confirm the resource exists*; 404 leaks nothing.

---

## 11. Advanced feature 1 — dynamic task search (Specifications)

**The problem:** the task-search endpoint accepts **8 optional filters** (title, description, status,
priority, assigneeId, dueAfter, dueBefore, plus the mandatory projectId). Supporting every
*combination* with fixed queries would need **2⁸ = 256 methods**.

**The solution:** build the query at runtime from only the filters actually supplied, using Spring
Data **`Specification`** objects (lambdas over the JPA Criteria API). Each factory in
`TaskSpecifications` returns one composable `WHERE` fragment:

```java
public static Specification<Task> hasStatus(TaskStatus status) {
    return (root, query, builder) -> builder.equal(root.get("status"), status);
}
```

`TaskService.getAllTasks` composes them:

```java
Specification<Task> spec = Specification.where(belongsToProject(projectId));
if (title != null && !title.isBlank()) spec = spec.and(titleContains(title));
if (status != null)                    spec = spec.and(hasStatus(status));
// ... one if-block per supplied filter ...
if (!authFacade.isAdmin())             spec = spec.and(visibleTo(authFacade.currentUserId()));
return taskRepository.findAll(spec, pageable);   // ONE SQL statement
```

**The standout detail — authorization pushed into the query.** For non-admins, an extra `visibleTo`
predicate (`project.owner.id = me OR assignee.id = me`) is `AND`-ed onto the search. Doing this *in
the SQL* (rather than filtering in Java after fetching) is what keeps **pagination correct**: "page 1
of 20" means 20 tasks you're *actually allowed to see*, and the total count is right. The database
never even ships rows you can't see.

---

## 12. Advanced feature 2 — asynchronous email notifications

When a task is created **with an assignee**, the assignee is emailed — without slowing the API
response, without breaking task creation if mail fails, and without ever emailing about a task that
later rolled back.

### How it works (four pieces)

```
TaskService.addTask()  ──publishes──▶  TaskAssignedEvent  ──▶  TaskAssignedListener  ──▶  EmailService → SMTP
```

1. **Producer** — inside `addTask` (which is `@Transactional`), after saving the task and resolving
   the assignee, the service publishes a `TaskAssignedEvent`. The service does **not** know an email
   gets sent — that's the decoupling.
2. **The event** — `TaskAssignedEvent` is an immutable `record` carrying **plain values**
   (`taskId, taskTitle, projectName, assigneeUsername, assigneeEmail`), *not* JPA entities. This is
   deliberate: the listener runs after commit on another thread, where the Hibernate session is
   closed — touching a lazy entity field there would throw `LazyInitializationException`. Copying the
   needed values into the event while the transaction is open avoids that entirely.
3. **Listener** — `TaskAssignedListener.onTaskAssigned` is annotated:
   ```java
   @Async("notificationExecutor")
   @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
   ```
   - **`AFTER_COMMIT`** — fire only once the task-creation transaction has *durably committed*, so we
     never notify about a task that rolled back.
   - **`@Async("notificationExecutor")`** — run on a background thread pool (`AsyncConfig`: core 2,
     max 5, queue 100, thread prefix `notify-`), so a slow SMTP handshake never delays the HTTP
     response.
   - A `try/catch (MailException)` makes it **fire-and-forget**: a failed email is logged, never
     propagated — the created task stands regardless.
4. **`EmailService`** — a thin wrapper around `JavaMailSender` that builds and sends a
   `SimpleMailMessage` (from / to / subject / text).

### ⚠️ Required per-machine email setup (READ THIS)

**Email will not send out of the box. Each person running the project must supply their *own* Gmail
address and a Google *App Password* on their *own physical machine*.** Credentials are intentionally
**not** committed to source control — `application.properties` only reads them from environment
variables:

```properties
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME:}     # ← your full Gmail address
spring.mail.password=${MAIL_PASSWORD:}     # ← a Google App Password (NOT your normal password)
app.mail.from=${MAIL_FROM:}                # ← the "From" address (for Gmail, same as username)
```

**Steps to enable it on your computer:**

1. Use a Gmail account with **2-Factor Authentication enabled**.
2. Generate a 16-character **App Password** at <https://myaccount.google.com/apppasswords>
   (a normal account password will **not** work with SMTP).
3. Set the following environment variables on your own system *before* starting the app
   (PowerShell example):
   ```powershell
   $env:MAIL_USERNAME = "yourname@gmail.com"
   $env:MAIL_PASSWORD = "your16charapppassword"
   $env:MAIL_FROM     = "yourname@gmail.com"
   ```
   (or set them as persistent user/system environment variables).
4. Start the app and assign a task — the assignee receives the email.

If these variables are left blank the app still runs perfectly; the email send simply fails and is
logged (fire-and-forget), so **every other feature continues to work without any email configuration.**

> **Network note:** some corporate / university Wi-Fi blocks outbound SMTP (port 587). If sending
> hangs or fails on such a network, test from a different connection (e.g. a mobile hotspot).

---

## 13. Advanced feature 3 — audit trail (activity log)

Every CREATE / UPDATE / DELETE of a task writes immutable rows to `task_activity` recording **who**
changed **what**, **when**, with **before/after** values. Read back via `GET /tasks/{id}/activity`
(newest-first, paginated, same visibility gate as the task itself).

It is the **deliberate opposite of the email feature**:

| | Audit trail | Email |
|---|---|---|
| Timing | **synchronous, same transaction** | async, after commit |
| Guarantee | **must not be lost** | must not block/break the request |
| Mechanism | inline call inside `@Transactional` | event + `AFTER_COMMIT` + `@Async` |

### Four design pillars

1. **Append-only / immutable.** `TaskActivity` has **no public setters**; the repository exposes only
   reads + save. The V3 migration also documents that, in production, the DB user is granted
   INSERT/SELECT but **not** UPDATE/DELETE. An audit log you can edit is worthless.
2. **Soft references + snapshots.** It stores `taskId`/`userId` as plain numbers (not `@ManyToOne`)
   plus `taskTitle`/`username` *snapshots*. The `task_activity` table has **no foreign keys** on
   purpose, so deleting a task or user never cascade-deletes its history — and each row stays
   human-readable even after the referenced row is gone.
3. **Field-level diffing with change-sets.** `TaskActivityRecorder.recordUpdate(before, after)` writes
   **one row per field that actually changed** (`Objects.equals` comparison; unchanged fields produce
   nothing, and `saveAll([])` is a no-op). All rows from one edit share a single `changeSetId` (a
   UUID), so a 3-field edit = 3 rows you can regroup. *Diffing happens **before** the setters
   overwrite the old values* — that ordering in `updateById` is essential.
4. **Same-transaction atomicity.** `TaskActivityRecorder` has **no `@Transactional` of its own** — it's
   always called from an already-transactional `TaskService` method, so the audit rows commit (or roll
   back) atomically with the task change. You can never have a committed change with a missing audit
   row. It also runs on the request thread, where the `SecurityContext` (the actor) is available.

---

## 14. API documentation (Swagger / OpenAPI)

The whole API is self-documenting via **springdoc-openapi**:

- **`/v3/api-docs`** — the generated OpenAPI JSON (springdoc scans the controllers/entities at runtime).
- **`/swagger-ui.html`** — an interactive UI rendering that JSON; you can call endpoints from the browser.

`SwaggerConfig` adds a global `OpenAPI` bean with an **info block** (title, version, description of the
auth model and activity log) and registers an **HTTP Basic security scheme** — so Swagger UI shows an
**Authorize** button; log in once and every "Try it out" call carries your credentials.

Descriptions come from annotations: `@Tag` (groups endpoints), `@Operation` (per-endpoint summary),
`@Parameter` (each query filter), and `@Schema` (models/fields, with `example` values, `READ_ONLY` for
server-set fields, and `WRITE_ONLY` for `password`).

> **Note on enums:** springdoc inlines an enum as a plain string + list of allowed values and ignores a
> class-level `@Schema` on the enum type. So the meanings of each value (e.g. "TODO / IN_PROGRESS /
> COMPLETED") are documented on the **referencing field** (`Task.status`, `Task.priority`,
> `TaskActivity.action`) instead.

---

## 15. Running the application

**Prerequisites:** JDK 21. (Maven is bundled via the `mvnw` wrapper.)

```bash
# from the project root
./mvnw spring-boot:run        # macOS/Linux
.\mvnw.cmd spring-boot:run    # Windows PowerShell
```

Then open:
- **App / SPA:** <http://localhost:8080/>
- **Swagger UI:** <http://localhost:8080/swagger-ui.html>
- **H2 console:** <http://localhost:8080/h2-console>  (JDBC `jdbc:h2:mem:testdb`, user `sa`, blank password)

On startup Flyway builds the schema and seeds the demo data; the database is in-memory, so every
restart is a clean slate. (Email requires the [§12](#12-advanced-feature-2--asynchronous-email-notifications)
environment variables — everything else works without them.)

---

## 16. Seed data & test accounts

`V2__seed_data.sql` inserts three users. **All three share the password `password123`** (stored
BCrypt-hashed). The admin account is in-memory (username `admin`, password `admin`).

| Username | Email | Password | Role | Owns projects |
|---|---|---|---|---|
| `admin` | — | `admin` | ADMIN | (sees everything) |
| `alice` | alice@example.com | `password123` | USER | Website Redesign, Mobile App |
| `bob` | bob@example.com | `password123` | USER | Data Pipeline |
| `charlie` | charlie@example.com | `password123` | USER | — (assignee only) |

Seeded projects: **Website Redesign** & **Mobile App** (owner alice), **Data Pipeline** (owner bob),
plus 7 tasks distributed across them with varied status / priority / due dates / assignees — enough
to exercise search, overdue, due-today and the visibility rules immediately.

Example call:
```bash
curl -u alice:password123 http://localhost:8080/api/projects
curl -u bob:password123  "http://localhost:8080/api/projects/3/tasks?status=IN_PROGRESS"
```

---

## 17. End-to-end request walkthrough

**`POST /api/projects/1/tasks`** with body `{ "title": "...", "status": "TODO", "priority": "HIGH",
"assignee": { "id": 3 } }`, authenticated as `alice`:

1. **Security filter chain** validates alice's HTTP Basic credentials (BCrypt) → `ROLE_USER`.
2. **`TaskController.addTask`** binds `@PathVariable projectId=1` and `@RequestBody @Valid Task` —
   validation runs first; an invalid title would short-circuit to **400** before any service code.
3. **`TaskService.addTask`** (opens a `@Transactional` unit):
   1. `projectService.getProjectById(1)` → loads project **and enforces alice owns it** (else 403/404).
   2. `task.setProject(project)`, `taskRepository.save(task)`.
   3. `activityRecorder.recordCreate(saved)` → **audit row written in the same transaction**.
   4. assignee id present → `userService.getUserById(3)` (resolve full user for the email), then
      `eventPublisher.publishEvent(new TaskAssignedEvent(...))` with plain values.
4. Method returns → **transaction commits** (task + audit row together).
5. **After commit**, the `@Async @TransactionalEventListener` fires on a background thread and emails
   charlie — without delaying the response, and only because the commit succeeded.
6. The saved `Task` is serialized to JSON (its `project` is `@JsonIgnore`d; the assignee's password is
   never included) and returned as **200**.

If anything in step 3 had thrown, the whole unit — task **and** audit row — would roll back, and (since
the commit never happened) **no email would be sent**.

---

## 18. Design decisions & known caveats

**Deliberate decisions**
- **Authorization in the service layer**, in two shapes (owner-gate vs per-row visibility) chosen per
  use case.
- **Three advanced features with contrasting guarantees** — search (correctness under pagination),
  email (decoupled, async, fire-and-forget), audit (synchronous, atomic, immutable).
- **Flyway, not `ddl-auto`** — reproducible, reviewable schema.
- **Secrets via environment variables** — no credentials in source control (see [§12](#12-advanced-feature-2--asynchronous-email-notifications)).
- **Enum value docs on the referencing field** — a real limitation of springdoc, handled honestly.

**Known caveats**
- **springdoc 2.8.5 officially targets Spring Boot 3.x**, while this project runs on Boot **4.1.0**.
  It works (verified against the live `/v3/api-docs`), but it is an unsupported version pairing.
- The global exception handler does **not** yet shape `MethodArgumentNotValidException` (`@Valid` →
  400) or a bad `sort=` parameter (`PropertyReferenceException` → 500) into the custom JSON format;
  those fall through to Spring's defaults. A natural next improvement.
- H2 is **in-memory** — all data resets on restart. Swapping in a persistent database is a config change
  (datasource URL + driver), since Flyway already owns the schema.
```
