# Session Understanding — Dynamic Task Search with JPA Specifications

A running checklist for what you should deeply understand about this session. We work
through it **incrementally** — each box gets checked only after you've demonstrated mastery
(in your own words, and at the code/edge-case level).

Legend: `[ ]` not yet · `[~]` in progress · `[x]` mastered

---

## 1. The Problem — why this work existed  ✅ MASTERED
- [x] What user-facing capability was missing before this session
- [x] Why the *old* approach (derived query methods like `findByProjectIdAndStatus`) doesn't scale
- [x] The "combinatorial explosion" problem of optional filters (worse than 2^n due to ranges/substrings)
- [x] The branches/alternatives considered: derived queries vs `@Query` JPQL vs Criteria/Specifications — and the trade-offs of each (right tool for the *shape* of the query: static→@Query, varying→Specifications)

## 2. The Solution — what was built and why this way  ✅ MASTERED
- [x] What a `Specification<Task>` actually *is* (a lazy WHERE-fragment recipe / Criteria object tree, not a query, not a string)
- [x] `TaskSpecifications`: the static-factory pattern, one predicate per method
- [x] Path traversal: `root.get("project").get("id")` (FK, may skip join) vs `project.owner.id` (needs a real join)
- [x] Case-insensitive `LIKE` via `builder.lower(...)` on BOTH sides
- [x] Dynamic composition in `TaskService.getAllTasks` — `Specification.where(...).and(...)` driven by which params are present
- [x] The blank/null guards (`title != null && !title.isBlank()`) — drop the no-op `LIKE '%%'`
- [x] How `JpaSpecificationExecutor` + `findAll(spec, pageable)` ties it together (no executor → compile error)
- [x] The controller surface: optional `@RequestParam`, enum/date auto-conversion, `@DateTimeFormat`, `@PageableDefault`

## 3. Edge Cases & Design Decisions  ✅ MASTERED
- [x] `assertExists` (404 only) vs `getProjectById` (owner gate / 403) — using the gate here would wrongly 403 a legitimate assignee
- [x] Existence check and row visibility are SEPARATE concerns: 404 = no such project vs 200-empty = exists but nothing visible to you
- [x] Per-row security: the `visibleTo` spec (owner OR assignee), only applied to non-admins
- [x] Admin skips `visibleTo` → sees all matching tasks unfiltered by ownership
- [x] `dueAfter` / `dueBefore` are **strict** (boundary-exclusive); proven by the pivot-1/pivot/pivot+1 test
- [x] Test layering: mock when testing orchestration (service); use a real DB when the thing under test IS the SQL translation (spec). A mocked spec test is worthless — the mock never runs the spec.

## 4. Broader Context — why it matters  ✅ MASTERED
- [x] Generalizes to any "search with N optional filters" feature (project search, user search, ...)
- [x] Authorization-in-the-query beats fetch-then-filter on BOTH axes:
      - correctness: ragged pages + dishonest totalElements if you filter after paging
      - security: unauthorized rows never leave the DB → no leak window
- [x] Frontend contract: every filter optional + ANDed only when present → client sends only what the user filled in; backend handles all combinations

---

### Progress log
- (session start) — establishing baseline understanding
- ✅ SESSION COMPLETE — all four sections mastered, verified by open-ended restatement,
  multiple-choice checks, AND a final connected teach-it-back synthesis
- All four sections mastered, verified by open-ended restatement + multiple-choice checks
- Misconceptions corrected along the way:
  - Specification is part of Spring Data JPA, not a separate dependency
  - single-level FK traversal may skip a join; deeper traversal forces one
  - Mockito *can* mock JpaSpecificationExecutor — the reason for a real DB is that a mock never executes the spec's SQL
