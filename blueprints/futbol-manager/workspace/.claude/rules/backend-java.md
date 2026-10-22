---
description: Spring Boot 4 backend conventions (packages, controllers, errors, tenancy)
paths:
  - "src/main/java/**"
  - "src/test/java/**"
---

# Backend Java

- Base package `com.futbolmanager`; feature packages `auth`, `group`, `player`, `elo`, `match`,
  `match.balance`, `live`, `stats`, `common`, `config`. A feature never imports another feature's
  controller; shared code goes in `common`.
- Spring Data repositories are **nested interfaces inside their entity** (`User.Repo`,
  `FriendGroup.Repo`, `Player.Repo`, and every other entity). This only works because `FutbolManagerApplication` declares
  `@EnableJpaRepositories(considerNestedRepositories = true)` — never remove that attribute.
- A feature's `*Controller` holds its `@Transactional` application logic and its request/response
  DTOs as nested `record`s. Pure algorithms (`EloCalculator`, `TeamBalancer`) import nothing from
  `org.springframework` or `jakarta.persistence`.
- `elo_changes` is never a JPA entity: read and write it with `JdbcClient` only.
- Group-owned data: every repository method that reads group data takes `groupId`. Every endpoint
  under `/api/groups/{groupId}/**` starts with `groupAccess.requireMember(groupId, user)` (or
  `requireAdmin`). Non-member → `ApiException.notFound(...)` (404, never 403). Member lacking the role → 403.
- Errors: throw `ApiException` (status + Spanish `detail`); `ApiExceptionHandler` renders RFC 9457
  `ProblemDetail`. Validation errors → 400 with `errors: [{field, message}]`.
- Jackson 3 (`tools.jackson.*`), not `com.fasterxml.jackson.databind`. `@MockitoBean`, not `@MockBean`.
- Timestamps are `Instant` (UTC). Enums are `@Enumerated(EnumType.STRING)` against text + CHECK columns.
- Do not map `created_at`/`updated_at` as writable: the database (default + trigger) owns them.
- Never log passwords, password hashes, session ids or CSRF tokens.
- Integration tests: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration.class)`,
  HTTP through `com.futbolmanager.support.ApiTestClient`, unique usernames via `ApiTestClient.uniqueUsername`.
