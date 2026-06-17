# 2.5 Testing

## Goal
Add automated test coverage for the backend's controller, service, and repository layers, following the conventions below.

## Controller Layer — Integration Tests
- At least one integration test per controller, covering every endpoint it exposes.
- Use `@SpringBootTest` with `@AutoConfigureMockMvc` (or `@WebMvcTest` if dependencies can be mocked) to exercise the full request → response cycle.
- For each controller, include:
    - A "happy path" test per endpoint (valid request → expected status code and response body).
    - At least one failure/edge case per controller (e.g. invalid input → 400, resource not found → 404, unauthorized → 401/403 where auth applies).
- Name test classes `<ControllerName>IntegrationTest`.

## Service Layer — Unit Tests
- Unit test every service class using JUnit 5 and Mockito.
- Mock all repository/collaborator dependencies with `@Mock` and `@InjectMocks` — no Spring context should be loaded.
- Cover:
    - Core business logic for each public method.
    - Edge cases: empty/missing data, invalid arguments, not-found scenarios.
    - Use `verify(...)` to confirm interactions with mocks where the behavior matters (e.g. "save is called exactly once").
- Name test classes `<ServiceName>Test`.

## Repository Layer — Data Tests
- One `@DataJpaTest` class per repository.
- Cover:
    - Standard CRUD operations (save, find, delete).
    - Any custom/derived query methods, with test data set up to verify filtering and results.
- Name test classes `<RepositoryName>Test`.
- Note: `@DataJpaTest` uses an embedded database by default. If any repository relies on Postgres-specific features (e.g. JSONB, specific functions/types), flag this so we can configure Testcontainers instead — otherwise the default embedded DB is fine.

## General Conventions
- Place tests under `src/test/java`, mirroring the package structure of `src/main/java`.
- JUnit 5, Mockito, and AssertJ are already available via `spring-boot-starter-test` — no new test dependencies should be needed.
- Prefer several small, single-behavior test methods over one large test method per class.
- Use descriptive method names, e.g. `shouldReturnNotFound_whenTaskDoesNotExist`.

## Done when
- Every controller has at least one integration test.
- Every service class has unit tests covering its main logic and edge cases.
- Every repository has a `@DataJpaTest` covering CRUD operations and custom queries.
- All tests pass via the project's test command (`mvn test` or `./gradlew test`).