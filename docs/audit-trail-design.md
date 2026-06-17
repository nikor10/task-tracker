# Feature Design — Task Activity Logging (Audit Trail)

A **design-first** learning session: we work out *how we would build* a task audit trail for
this app, Socratically, before any code is written. Same teach → quiz → confirm rhythm as the
Specifications session. Each box is checked only after demonstrated mastery (own words +
edge-case reasoning).

Legend: `[ ]` not yet · `[~]` in progress · `[x]` mastered

---

## 1. The Problem — why an audit trail exists  ✅ MASTERED
- [x] What an audit trail is: append-only record of who-did-what-when (+ action type, before→after)
- [x] Why row state is NOT enough: an UPDATE is **destructive** — it overwrites the old value in place,
      gone forever. The tasks table is a present-only snapshot; the trail is a time series.
- [x] Motivations, each driving a design need: forensics→timestamps/ordering; compliance→immutability+actor; undo→old values
- [x] What's missing today: no history of task changes at all

## 2. The Solution — what to build and why this way  ✅ MASTERED
- [x] WHAT to capture per entry: actor, action, entity ref, timestamp, old→new values
      - schema designed: id, task_id, user_id, action, field_changed, old_value, new_value, timestamp
      - granularity decision: one row PER changed field (queryable; needs timestamp/change_set_id to regroup an atomic edit)
      - value-typing decision: old/new stored as strings (VARCHAR/TEXT) → uniform schema, cost = lost type-safety
- [x] WHERE to capture it — menu understood:
      - manual-in-service: full control, but coverage depends on discipline (forget a method → silent gap)
      - @LastModifiedBy/Date: trivial but only the LATEST touch — overwrites history (useless for a trail)
      - JPA @EntityListeners/@PreUpdate: auto on entity lifecycle
      - Envers: full history nearly free, but opaque _AUD tables, harder to query/shape, still needs a RevisionListener for the actor
      - application events (reuse TaskAssignedEvent pattern) / DB triggers
      - MANUAL diffing gotcha: must capture OLD values BEFORE the setters overwrite them in memory
      - direction for THIS app: manual-in-service or app-events (keep audit schema under our control)
- [ ] The data model: an append-only `task_activity` table (columns, types, FK choices)
- [x] Capturing the actor via `AuthFacade` / SecurityContext (must happen on the request thread)
- [x] **Transaction boundary**: SAME transaction (atomic with the change) — unlike the email's AFTER_COMMIT.
      AFTER_COMMIT + fire-and-forget would risk a committed-but-unlogged change = silent hole in the trail.
- [x] Sync vs async: SecurityContext is a request-thread ThreadLocal; it does NOT propagate to @Async threads
      → an async listener would record a null actor unless the actor is captured up-front in the event payload

## 3. Edge Cases & Design Decisions  ✅ MASTERED
- [x] Append-only/immutability enforced at DB level (no UPDATE/DELETE grant) = defense in depth; app discipline alone is bypassable
- [x] In-transaction trade-off: a failed audit rolls back the business change = intentional "fail-closed / if we can't log it we don't do it" for compliance-critical systems
- [x] AFTER_COMMIT trade-off: crash between commit and async log = silent unlogged change (rejected for audit)
- [x] Task deletion: KEEP the history (CASCADE would erase 'who deleted it'); avoid hard FK / use SET NULL / self-describing rows
- [x] Read authz: reuse `visibleTo` — non-admin sees only trails for tasks visible to them; admins see all
- [x] PII: immutable "keep forever" collides with GDPR erasure → don't audit sensitive fields / store refs / controlled redaction
- [x] Volume: table grows fast (rows per field per edit) → index on task_id+timestamp, paginate reads, retention policy

## 4. Broader Context — why it matters  ✅ MASTERED
- [x] Side-effect design principle: criticality of FAILURE decides the style. Courtesy (email) → decouple/async/fire-and-forget. Integrity (audit) → atomic/synchronous/fail-closed.
- [x] Non-repudiation = can't deny you did it; delivered by THREE things together: reliable actor (who) + completeness (no gaps) + immutability (no tampering)
- [x] Forensics: reconstruct how a task reached a (broken) state
- [x] Enables downstream: history/time-travel, undo/restore (needs old values), activity feed UI, anomaly/security detection, accountability reports
- [x] Reuses TaskAssignedEvent's payload-of-plain-values idea; contrasts on tx boundary
- [x] KEY REFINEMENT: actor-capture is solvable in either sync/async (put actor in payload). The PRIMARY reason audit is synchronous is completeness/atomicity, NOT the actor.

---

### Implementation status (design → code)
- [x] V3__task_activity.sql — append-only table, soft refs + snapshots, change_set_id, index, immutability note
- [x] TaskActivity entity + TaskActivityAction enum — plain-id soft refs, getters-only (append-only object)
- [x] TaskActivityRepository — paginated findByTaskId
- [x] TaskActivityRecorder — per-field diff, stringify, actor capture, change_set_id; called in-transaction
- [x] TaskService wiring — recordCreate (add), recordUpdate before setters (update), recordDelete (delete);
      @Transactional added to updateById/deleteById for atomic audit
- [x] Read endpoint GET /tasks/{id}/activity — paginated, newest-first, gated by getTaskById (404/403 reuse)
- [x] Tests — TaskActivityRepositoryTest (data), TaskActivityRecorderTest (diff logic),
      TaskServiceTest (delegation + gate), TaskControllerIntegrationTest (endpoint + authz). All 135 pass.

### Progress log
- (session start) — design-first mode chosen; establishing baseline understanding
- ✅ SESSION COMPLETE — all four sections mastered via restatement + quizzes + connected teach-back
- Notable: learner questioned the framing of the actor pitfall, leading to the refinement that
  completeness (not actor capture) is the load-bearing reason for same-transaction audit.
