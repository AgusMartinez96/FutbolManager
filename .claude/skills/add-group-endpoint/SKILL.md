---
name: add-group-endpoint
description: Add a REST endpoint that reads or writes data owned by a group (players, matches, events, stats). Use for "new endpoint", "add an API route", "expose X for a group".
---

# Add a group-scoped endpoint

## When to use
Any route under `/api/groups/{groupId}/**`.

## Steps
1. Put the handler in the feature's `*Controller` with request/response DTOs as nested `record`s and
   Bean Validation annotations on the request record (`@Valid @RequestBody`).
2. First line of the handler: `groupAccess.requireMember(groupId, user)` (or `requireAdmin`), where
   `user` is `@AuthenticationPrincipal AuthUser user`. A non-member gets 404, never 403.
3. Load nested resources by `(id, groupId)` — e.g. `matchRepo.findByIdAndGroupId(matchId, groupId)` —
   and throw `ApiException.notFound("Partido no encontrado.")` when empty.
4. Errors via `ApiException` with a Spanish `detail`; validation is handled by `ApiExceptionHandler`.
5. Add the route to blueprint §5 conventions if it introduces a new status code.
6. Integration test with `ApiTestClient`: happy path, a non-member (404), a member without the role
   (403 when the route is admin-only), and one validation failure (400 with `errors[].field`).

## Verify
```bash
./mvnw -q test -Dtest=<YourFeature>IntegrationTest   # expect: exit 0
./mvnw -q verify                                       # expect: exit 0
```

## Do not
- Return 403 for another group's resource.
- Query group data without `groupId` in the `WHERE` clause.
- Return JPA entities directly from a controller.
