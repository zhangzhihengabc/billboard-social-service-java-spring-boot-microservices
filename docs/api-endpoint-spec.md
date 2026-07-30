# Billboard Social Service — Full API Endpoint Specification

> Complete, testable reference for the frontend team. Covers every REST endpoint exposed by
> `billboard-social-service`, including auth, path/query params, request bodies (field-by-field
> with validation rules), response bodies, success codes, and documented error codes.
>
> **Total: 164 endpoints** across 6 functional modules
> (Graph 44, Groups 55, Events 38, Game Groups 22, User Suggestions 1, Friends Finder 4).

---

## 0. Global / Cross-Cutting Concerns

### 0.1 Base URLs

| Environment | Base URL |
|---|---|
| Local dev | `http://localhost:8082` (default port **8082**) |
| Production | `https://apigateway.pineapps.online/social` (API gateway, `/social` context path) |

- In deployed environments a **`/social` context-path** is prepended (e.g. `https://apigateway.pineapps.online/social/api/v1/friendships`).
- All endpoint paths below are written **without** the context path — prepend `/social` in prod, nothing locally.
- Swagger UI: `/swagger-ui.html` — OpenAPI JSON: `/v3/api-docs`.

### 0.2 Authentication

Standard endpoints use a **JWT Bearer token**:

```
Authorization: Bearer <JWT>
```

- The token is issued by the upstream SSO/identity service (HMAC-SHA signed; the `Bearer ` prefix is required).
- JWT claims consumed: `userId` (Long → current user id), `name` (username), `email`, `roles` (→ `ROLE_<role>` authorities).
- The current user id is **always taken from the token** (`principal.getId()`, a `Long`) — it is never passed in the request body or query string.

**Public endpoints (no auth):**
- `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-resources/**`, `/webjars/**`
- `/actuator/**`
- All `OPTIONS /**` (CORS preflight)
- `/api/v1/internal/**` — see internal auth below

**Internal / service-to-service auth** (only `/api/v1/internal/**`, i.e. `InternalEventController`):
```
X-Internal-Api-Key: <shared-secret>
```
- Missing/invalid key → **401** `"Invalid or missing internal API key"`.
- These endpoints do **not** use JWT; they take `organisationId` / user ids as explicit parameters.

**CORS:** all origins `*`; methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`.

### 0.3 Auth failure responses

| Situation | Status | Body shape |
|---|---|---|
| Missing/invalid/expired token on a protected endpoint | **401** | `{ timestamp, status, error: "Unauthorized", message, path }` |
| Authenticated but lacking permission | **403** | `{ timestamp, status, error: "Forbidden", message, path }` |

> Note: auth-layer 401/403 bodies include a `path` field and omit `validationErrors`. Business-logic
> errors (below) use the `GlobalExceptionHandler` shape instead.

### 0.4 Standard error response (business logic)

All business/validation errors return this shape (`GlobalExceptionHandler`):

```json
{
  "timestamp": "2026-07-14T12:34:56.789Z",
  "status": 400,
  "error": "Bad Request",
  "message": "human-readable message",
  "validationErrors": { "fieldName": "error message" }
}
```

- `timestamp` — ISO-8601 UTC string.
- `validationErrors` — present **only** for bean-validation failures (field → message map); otherwise absent/null.

**Exception → status mapping (important for testing edge cases):**

| Condition | Status |
|---|---|
| Validation / bad input / `IllegalArgumentException` | **400** |
| **Resource not found** (`ResourceNotFoundException`) | **400** (deliberately, *not* 404) |
| Forbidden / access denied | **403** |
| Method not allowed | **405** |
| Missing required query param / path variable | **400** |
| Malformed JSON body | **400** |
| Downstream user-service 404 | **400** ("Referenced user not found") |
| Downstream user-service 401/403 | **403** |
| Downstream user-service 5xx / unavailable | **503** |
| Unhandled | **500** |

> ⚠️ **Key gotcha for frontend:** "not found" cases generally return **400**, not 404. The only
> documented genuine `404` is `GET /api/v1/game-groups/{groupId}/chat-channel` (channel not yet provisioned).

### 0.5 Standard pagination

Most list endpoints are paginated with these query params:

| Name | Type | Required | Default | Constraints |
|---|---|---|---|---|
| `page` | int | No | `0` | `min 0`, `max 1000` (Game Groups use `max 10000`) |
| `size` | int | No | `20` | `min 1`, `max 100` (scrim-history default is `10`) |

**`PageResponse<T>` envelope:**

```json
{
  "content": [ /* array of T */ ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5,
  "first": true,
  "last": false,
  "empty": false,
  "sort": { "empty": false, "sorted": true, "unsorted": false, "sortBy": "createdAt", "direction": "DESC" }
}
```

| Field | Type | Notes |
|---|---|---|
| `content` | array of `T` | items in current page |
| `page` | int | current page (0-indexed) |
| `size` | int | page size |
| `totalElements` | long | total across all pages |
| `totalPages` | int | total pages |
| `first` / `last` / `empty` | boolean | position flags |
| `sort` | object | `{ empty, sorted, unsorted, sortBy, direction }` |

### 0.6 Shared object: `UserSummary`

Nested in many responses (fetched from SSO service; may be null if lookup fails):

| Field | Type |
|---|---|
| `id` | integer (int64) |
| `username` | string |
| `email` | string |

### 0.7 Date/time format

All timestamp fields serialize as UTC strings: `yyyy-MM-dd'T'HH:mm:ss'Z'` (e.g. `2026-01-19T11:33:16Z`).

### 0.8 ID type reference (⚠ Swagger mislabels some)

- **User ids** (`userId`, `friendId`, `newOwnerId`, `teamId`, etc.) are `Long` (integer), even though some Swagger examples show UUID strings.
- **Resource ids** (`friendshipId`, `pokeId`, `groupId`, `eventId`, `invitationId`, `contentId`, `suggestionId`, `id`) are genuine **UUID** strings.

---

# MODULE 1 — Graph (Social Relationships)

Base tag: friendships, follows, blocks, pokes, reactions, shares, presence.
**All endpoints require Bearer JWT.**

### Enums

| Enum | Values |
|---|---|
| `FriendshipStatus` | `PENDING`, `ACCEPTED`, `DECLINED`, `BLOCKED`, `CANCELLED` |
| `ReactionType` | `LIKE`, `LOVE`, `HAHA`, `WOW`, `SAD`, `ANGRY` |
| `ContentType` | `POST`, `COMMENT`, `PHOTO`, `VIDEO`, `ALBUM`, `EVENT`, `GROUP`, `STORY`, `ASSIGNMENT`, `COURSE_MATERIAL`, `NOTICE`, `CIRCULAR` (case-insensitive) |

---

## 1.1 Friendships — `/api/v1/friendships`

### `FriendshipResponse`
| Field | Type | Notes |
|---|---|---|
| `id` | uuid | |
| `requesterId` | int64 | |
| `addresseeId` | int64 | |
| `status` | `FriendshipStatus` | |
| `message` | string (nullable) | |
| `mutualFriendsCount` | int32 (nullable) | |
| `acceptedAt` | date-time (nullable) | |
| `createdAt` | date-time | |

### `FriendResponse`
| Field | Type |
|---|---|
| `friendId` | int64 |
| `username` | string (nullable) |
| `mutualFriendsCount` | int32 (nullable) |
| `friendsSince` | date-time (nullable) |

| # | Method & Path | Description | Request | Response | Success | Errors |
|---|---|---|---|---|---|---|
| 1 | `POST /request` | Send friend request | Body `FriendRequest`: `userId` int64 **required**; `message` string ≤500 | `FriendshipResponse` | 201 | 400 (self / already friends / pending / blocked / limit / not found), 401 |
| 2 | `POST /{friendshipId}/accept` | Accept request | path `friendshipId` uuid | `FriendshipResponse` | 200 | 400, 401 |
| 3 | `POST /{friendshipId}/decline` | Decline request | path `friendshipId` uuid | — | 204 | 400, 401 |
| 4 | `DELETE /{friendshipId}/cancel` | Cancel sent request | path `friendshipId` uuid | — | 204 | 400, 401 |
| 5 | `DELETE /{friendId}` | Unfriend | path `friendId` int64 | — | 204 | 400, 401 |
| 6 | `GET /` | List friends (paginated) | `page`, `size` | `PageResponse<FriendResponse>` | 200 | 400, 401 |
| 7 | `GET /requests/pending` | Received requests | `page`, `size` | `PageResponse<FriendshipResponse>` | 200 | 400, 401 |
| 8 | `GET /requests/sent` | Sent requests | `page`, `size` | `PageResponse<FriendshipResponse>` | 200 | 400, 401 |
| 9 | `GET /ids` | All friend user ids | — | `Long[]` | 200 | 401 |
| 10 | `GET /mutual/{userId}` | Mutual friend ids | path `userId` int64 | `Long[]` | 200 | 400, 401 |
| 11 | `GET /check/{userId}` | Are we friends? | path `userId` int64 | `boolean` | 200 | 400, 401 |
| 12 | `GET /count` | Friends count | — | `long` | 200 | 401 |

---

## 1.2 Follows — `/api/v1/follows`

### `FollowResponse`
| Field | Type |
|---|---|
| `id` | uuid |
| `followerId` | int64 |
| `followingId` | int64 |
| `notificationsEnabled` | boolean |
| `isCloseFriend` | boolean |
| `isMuted` | boolean |
| `createdAt` | date-time |
| `user` | `UserSummary` (nullable) |

### `FollowStatsResponse`
| Field | Type |
|---|---|
| `userId` | int64 |
| `followersCount` | int64 |
| `followingCount` | int64 |
| `isFollowing` | boolean |
| `isFollowedBy` | boolean |

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /` | Body `FollowRequest`: `userId` int64 **required**; `notificationsEnabled` bool; `isCloseFriend` bool | `FollowResponse` | 201 | 400 (self / already / blocked / limit), 401 |
| 2 | `DELETE /{userId}` | path `userId` int64 | — | 204 | 400, 401 |
| 3 | `PUT /{userId}` | path `userId`; Body `UpdateFollowRequest`: `notificationsEnabled`, `isCloseFriend`, `isMuted` (all bool, optional) | `FollowResponse` | 200 | 400, 401 |
| 4 | `GET /followers` | `page`, `size` | `PageResponse<FollowResponse>` | 200 | 400, 401 |
| 5 | `GET /following` | `page`, `size` | `PageResponse<FollowResponse>` | 200 | 400, 401 |
| 6 | `GET /close-friends` | `page`, `size` | `PageResponse<FollowResponse>` | 200 | 400, 401 |
| 7 | `GET /stats/{userId}` | path `userId` int64 | `FollowStatsResponse` | 200 | 400, 401 |
| 8 | `GET /following/ids` | — | `Long[]` | 200 | 401 |
| 9 | `GET /check/{userId}` | path `userId` int64 | `boolean` | 200 | 400, 401 |

---

## 1.3 Blocks — `/api/v1/blocks`

### `BlockResponse`
| Field | Type |
|---|---|
| `id` | uuid |
| `blockedId` | int64 |
| `reason` | string (nullable) |
| `createdAt` | date-time |
| `blockedUser` | `UserSummary` (nullable) |

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /` | Body `BlockRequest`: `userId` int64 **required**; `reason` ≤500; `blockMessages`/`blockPosts`/`blockComments` bool | `BlockResponse` | 201 | 400 (self / already / limit), 401 |
| 2 | `DELETE /{userId}` | path `userId` int64 | — | 204 | 400, 401 |
| 3 | `GET /` | `page`, `size` | `PageResponse<BlockResponse>` | 200 | 400, 401 |
| 4 | `GET /ids` | — | `Long[]` | 200 | 401 |
| 5 | `GET /check/{userId}` | path `userId` int64 | `boolean` | 200 | 400, 401 |

---

## 1.4 Pokes — `/api/v1/pokes`

### `PokeResponse`
| Field | Type |
|---|---|
| `id` | uuid |
| `pokerId` | int64 |
| `pokedId` | int64 |
| `isActive` | boolean |
| `pokeCount` | int32 |
| `pokedBackAt` | date-time (nullable) |
| `createdAt` | date-time |
| `poker` | `UserSummary` (nullable) |

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /` | Body `PokeRequest`: `userId` int64 **required** | `PokeResponse` | 201 | 400 (self / blocked / exists), 401 |
| 2 | `POST /{pokeId}/poke-back` | path `pokeId` uuid | `PokeResponse` | 200 | 400, 401 |
| 3 | `GET /received` | `page`, `size` | `PageResponse<PokeResponse>` | 200 | 400, 401 |
| 4 | `GET /sent` | `page`, `size` | `PageResponse<PokeResponse>` | 200 | 400, 401 |
| 5 | `GET /count` | — | `long` | 200 | 401 |
| 6 | `DELETE /{pokeId}/dismiss` | path `pokeId` uuid | — | 204 | 400, 401 |

---

## 1.5 Reactions — `/api/v1/reactions`

### `ReactionResponse`
| Field | Type |
|---|---|
| `id` | uuid |
| `userId` | int64 |
| `contentType` | `ContentType` |
| `contentId` | uuid |
| `reactionType` | `ReactionType` |
| `createdAt` | date-time |
| `user` | `UserSummary` (nullable) |

### `ReactionStatsResponse`
| Field | Type |
|---|---|
| `contentType` | `ContentType` |
| `contentId` | uuid |
| `totalCount` | int64 |
| `countByType` | map `ReactionType`→int64 (nullable) |
| `userReacted` | boolean |
| `userReactionType` | `ReactionType` (nullable) |

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /` | Body `ReactionRequest`: `contentType` **required**; `contentId` uuid **required**; `reactionType` **required**; `contentOwnerId` int64 | `ReactionResponse` | 201 | 400, 401 |
| 2 | `DELETE /{contentType}/{contentId}` | path enum + uuid | — | 204 | 400, 401 |
| 3 | `GET /{contentType}/{contentId}` | path enum + uuid; `page`, `size` | `PageResponse<ReactionResponse>` | 200 | 400, 401 |
| 4 | `GET /{contentType}/{contentId}/type/{reactionType}` | path enum + uuid + enum; `page`, `size` | `PageResponse<ReactionResponse>` | 200 | 400, 401 |
| 5 | `GET /{contentType}/{contentId}/stats` | path enum + uuid | `ReactionStatsResponse` | 200 | 400, 401 |
| 6 | `GET /{contentType}/{contentId}/check` | path enum + uuid | `boolean` | 200 | 400, 401 |

---

## 1.6 Shares — `/api/v1/shares`

### `ShareResponse`
| Field | Type |
|---|---|
| `id` | uuid |
| `userId` | int64 |
| `contentType` | `ContentType` |
| `contentId` | uuid |
| `targetUserId` | int64 (nullable) |
| `message` | string (nullable) |
| `shareToFeed` | boolean |
| `shareToStory` | boolean |
| `isPrivateShare` | boolean |
| `createdAt` | date-time |
| `user` | `UserSummary` (nullable) |

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /` | Body `ShareRequest`: `contentType` **required**; `contentId` uuid **required**; `contentOwnerId` int64; `targetUserId` int64; `message` ≤1000; `shareToFeed`/`shareToStory`/`isPrivateShare` bool | `ShareResponse` | 201 | 400, 401 |
| 2 | `GET /content/{contentType}/{contentId}` | path enum + uuid; `page`, `size` | `PageResponse<ShareResponse>` | 200 | 400, 401 |
| 3 | `GET /user` | `page`, `size` | `PageResponse<ShareResponse>` | 200 | 400, 401 |
| 4 | `GET /count/{contentType}/{contentId}` | path enum + uuid | `long` | 200 | 400, 401 |

---

## 1.7 Presence — `/api/v1/presence`

### `FriendPresenceResponse`
| Field | Type |
|---|---|
| `userId` | int64 |
| `username` | string |
| `online` | boolean |

| # | Method & Path | Description | Request | Response | Success | Errors |
|---|---|---|---|---|---|---|
| 1 | `PUT /heartbeat` | Mark self online. Call at least once/min; presence expires ~60s after last heartbeat. | — | — | 200 | 401 |
| 2 | `GET /friends` | Online friends | — | `FriendPresenceResponse[]` | 200 | 401 |

---

# MODULE 2 — Groups

**All endpoints require Bearer JWT** (except `GET /groups/{groupId}` and `GET /groups/slug/{slug}` which tolerate anonymous access for public groups).

### Enums

| Enum | Values |
|---|---|
| `GroupType` | `PUBLIC` (see & join), `CLOSED` (see, request to join), `PRIVATE` (members-only content), `SECRET` (hidden from search) |
| `MemberRole` | `OWNER`, `ADMIN`, `MODERATOR`, `MEMBER` |
| `MemberStatus` | `PENDING`, `APPROVED`, `REJECTED`, `LEFT`, `BANNED` |
| Invitation `status` | `PENDING`, `ACCEPTED`, `DECLINED` (cancel = hard delete) |

### `GroupResponse`
| Field | Type | Notes |
|---|---|---|
| `id` | uuid | |
| `name` | string | |
| `slug` | string | |
| `description` | string (nullable) | |
| `groupType` | `GroupType` | |
| `ownerId` | int64 | |
| `categoryId` | uuid (nullable) | |
| `categoryName` | string (nullable) | |
| `coverImageUrl` / `iconUrl` | string (nullable) | |
| `location` / `website` / `rules` | string (nullable) | |
| `memberCount` / `postCount` | int32 | |
| `isVerified` / `isFeatured` | boolean | |
| `allowMemberPosts` / `requirePostApproval` / `requireJoinApproval` / `allowMemberInvites` | boolean | |
| `createdAt` | date-time | |
| `isMember` / `isAdmin` / `isPending` | boolean | current-user context |
| `userRole` | `MemberRole` | current-user role |

### `GroupSummaryResponse`
`id` uuid, `name`, `slug`, `groupType`, `iconUrl` (nullable), `memberCount` int32, `isVerified` bool.

### `GroupMemberResponse`
`id` uuid, `groupId` uuid, `userId` int64, `role` `MemberRole`, `status` `MemberStatus`, `joinedAt` date-time, `postCount` int32, `contributionScore` int32, `notificationsEnabled` bool, `mutedUntil` date-time (nullable), `user` `UserSummary`.

### `MembershipResponse`
`groupId` uuid, `groupName`, `groupSlug`, `groupIconUrl` (nullable), `groupType`, `role`, `status`, `joinedAt` date-time, `notificationsEnabled` bool.

### `MembershipStatsResponse`
`totalMembers`, `pendingRequests`, `adminCount`, `moderatorCount`, `bannedCount` — all int64.

### `InvitationResponse`
`id` uuid, `groupId` uuid, `groupName`, `groupIconUrl` (nullable), `inviterId` int64, `inviterName` (nullable), `inviteeId` int64 (nullable), `inviteeEmail` (nullable), `message` (nullable), `status` string, `inviteCode` (nullable), `createdAt` date-time, `expiresAt`/`acceptedAt`/`declinedAt` date-time (nullable), `inviter` `UserSummary`.

### `GroupCategoryResponse`
`id` uuid, `name`, `slug`, `description` (nullable), `icon` (nullable), `parentId` uuid (nullable), `displayOrder` int32, `groupCount` int32, `isActive` bool, `createdAt` date-time.

---

## 2.1 Groups — `/api/v1/groups`

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /` | Body `CreateGroupRequest` (below) | `GroupResponse` | 201 | 400 (invalid / limit), 401 |
| 2 | `GET /{groupId}` | path `groupId` uuid (auth optional) | `GroupResponse` | 200 | 400 (not found), 401, 403 (private) |
| 3 | `GET /slug/{slug}` | path `slug` (1–200 chars, auth optional) | `GroupResponse` | 200 | 400, 401, 403 |
| 4 | `PUT /{groupId}` | path uuid; Body `UpdateGroupRequest` (all optional, same fields as create) — admin only | `GroupResponse` | 200 | 400, 401, 403 |
| 5 | `DELETE /{groupId}` | path uuid — owner only | — | 204 | 400, 401, 403 |
| 6 | `GET /search` | `query` **required** (1–100), `page`, `size` | `PageResponse<GroupSummaryResponse>` | 200 | 400, 401 |
| 7 | `GET /popular` | `page`, `size` | `PageResponse<GroupSummaryResponse>` | 200 | 400, 401 |
| 8 | `GET /featured` | `page`, `size` | `PageResponse<GroupSummaryResponse>` | 200 | 400, 401 |
| 9 | `GET /category/{categoryId}` | path uuid; `page`, `size` | `PageResponse<GroupSummaryResponse>` | 200 | 400, 401 |
| 10 | `GET /my` | `page`, `size` | `PageResponse<MembershipResponse>` | 200 | 400, 401 |

**`CreateGroupRequest`:** `name` string **required** (3–100); `description` ≤5000; `groupType` `GroupType`; `categoryId` uuid; `location`, `website`, `rules` strings; `allowMemberPosts`, `requirePostApproval`, `requireJoinApproval`, `allowMemberInvites` bool.

---

## 2.2 Group Members — `/api/v1/groups/{groupId}/members`

`groupId` (uuid) is a path variable on every endpoint.

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /join` | Body `JoinGroupRequest` (optional): `message` ≤500 | `GroupMemberResponse` | 201 | 400, 401 |
| 2 | `POST /leave` | — | — | 204 | 400 (owner can't leave), 401 |
| 3 | `GET /` | `page`, `size` | `PageResponse<GroupMemberResponse>` | 200 | 400, 401 |
| 4 | `GET /me` | — | `GroupMemberResponse` | 200 | 400 (not a member), 401 |
| 5 | `GET /{userId}` | path `userId` int64 | `GroupMemberResponse` | 200 | 400, 401 |
| 6 | `GET /pending` | `page`, `size` — moderator | `PageResponse<GroupMemberResponse>` | 200 | 400, 401, 403 |
| 7 | `GET /banned` | `page`, `size` — moderator | `PageResponse<GroupMemberResponse>` | 200 | 400, 401, 403 |
| 8 | `GET /admins` | — | `GroupMemberResponse[]` | 200 | 400, 401 |
| 9 | `GET /ids` | — | `Long[]` | 200 | 400, 401 |
| 10 | `GET /stats` | — | `MembershipStatsResponse` | 200 | 400, 401 |
| 11 | `POST /{memberId}/approve` | path `memberId` uuid — moderator | `GroupMemberResponse` | 200 | 400, 401, 403 |
| 12 | `POST /{memberId}/reject` | path `memberId` uuid — moderator | — | 204 | 400, 401, 403 |
| 13 | `DELETE /{userId}` | path `userId` int64 | — | 204 | 400, 401, 403 |
| 14 | `PUT /{userId}/role` | path int64; Body `UpdateMemberRoleRequest`: `role` `MemberRole` — admin | `GroupMemberResponse` | 200 | 400, 401, 403 |
| 15 | `POST /{userId}/promote-to-admin` | path int64 — owner | `GroupMemberResponse` | 200 | 400, 401, 403 |
| 16 | `POST /{userId}/promote-to-moderator` | path int64 — admin+ | `GroupMemberResponse` | 200 | 400, 401, 403 |
| 17 | `POST /{userId}/demote-admin` | path int64 — owner | `GroupMemberResponse` | 200 | 400, 401, 403 |
| 18 | `POST /{userId}/demote-moderator` | path int64 — admin+ | `GroupMemberResponse` | 200 | 400, 401, 403 |
| 19 | `POST /{userId}/mute` | path int64; Body `MuteMemberRequest`: `durationHours` int32 (null=indefinite), `reason` ≤500 | `GroupMemberResponse` | 200 | 400, 401, 403 |
| 20 | `POST /{userId}/unmute` | path int64 — moderator | `GroupMemberResponse` | 200 | 400, 401, 403 |
| 21 | `POST /{userId}/ban` | path int64; Body `BanMemberRequest` (optional): `reason` ≤500 | — | 204 | 400, 401, 403 |
| 22 | `POST /{userId}/unban` | path int64 — moderator | — | 204 | 400, 401, 403 |
| 23 | `PUT /me/settings` | Body `UpdateMemberSettingsRequest`: `notificationsEnabled`, `showInProfile` bool | `GroupMemberResponse` | 200 | 400, 401 |
| 24 | `POST /transfer-ownership/{newOwnerId}` | path `newOwnerId` int64 — owner | — | 200 | 400, 401, 403 |

---

## 2.3 Group Categories — `/api/v1/groups/categories`

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /` | Body `CreateGroupCategoryRequest`: `name` **required** (1–100); `description` ≤500; `icon` ≤50; `parentId` uuid; `displayOrder` int32; `isActive` bool | `GroupCategoryResponse` | 201 | 400, 401 |
| 2 | `GET /{categoryId}` | path uuid | `GroupCategoryResponse` | 200 | 400, 401 |
| 3 | `GET /slug/{slug}` | path slug (1–120) | `GroupCategoryResponse` | 200 | 400, 401 |
| 4 | `PUT /{categoryId}` | path uuid; Body `UpdateGroupCategoryRequest` (all optional) | `GroupCategoryResponse` | 200 | 400, 401 |
| 5 | `DELETE /{categoryId}` | path uuid | — | 204 | 400 (has groups/subcats), 401 |
| 6 | `GET /` | `page`, `size` | `PageResponse<GroupCategoryResponse>` | 200 | 400, 401 |
| 7 | `GET /list` | — | `GroupCategoryResponse[]` | 200 | 401 |
| 8 | `GET /root` | `page`, `size` | `PageResponse<GroupCategoryResponse>` | 200 | 400, 401 |
| 9 | `GET /{parentId}/subcategories` | path uuid; `page`, `size` | `PageResponse<GroupCategoryResponse>` | 200 | 400, 401 |
| 10 | `GET /search` | `query` **required** (1–100), `page`, `size` | `PageResponse<GroupCategoryResponse>` | 200 | 400, 401 |

---

## 2.4 Group Invitations — `/api/v1` (mixed paths)

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /groups/{groupId}/invitations` | Body `InviteMemberRequest`: `userId` int64, `email` ≤255, `message` ≤500, `expirationDays` int32 (null=7) | `InvitationResponse` | 201 | 400, 401, 403 |
| 2 | `POST /groups/{groupId}/invitations/link` | Body `CreateInviteLinkRequest` (optional): `message` ≤500, `expirationDays`, `maxUses` int32 | `InvitationResponse` (with `inviteCode`) | 201 | 400, 401, 403 |
| 3 | `GET /groups/{groupId}/invitations` | `page`, `size` — moderator | `PageResponse<InvitationResponse>` | 200 | 400, 401, 403 |
| 4 | `GET /groups/{groupId}/invitations/{invitationId}` | path uuid + uuid | `InvitationResponse` | 200 | 400, 401 |
| 5 | `DELETE /groups/{groupId}/invitations/{invitationId}` | path uuid + uuid — moderator (hard delete) | — | 204 | 400, 401, 403 |
| 6 | `GET /invitations` | `page`, `size` — my invitations | `PageResponse<InvitationResponse>` | 200 | 400, 401 |
| 7 | `GET /invitations/count` | — | `long` | 200 | 401 |
| 8 | `POST /invitations/{invitationId}/accept` | path uuid | `GroupMemberResponse` | 200 | 400, 401, 403 |
| 9 | `POST /invitations/{invitationId}/decline` | path uuid | — | 204 | 400, 401, 403 |
| 10 | `POST /invitations/join` | `code` **required** (query) | `GroupMemberResponse` | 200 | 400, 401 |
| 11 | `GET /invitations/preview` | `code` **required** (query) | `InvitationResponse` | 200 | 400, 401 |

---

# MODULE 3 — Events

**Standard endpoints require Bearer JWT.** Category admin endpoints additionally require `ROLE_ADMIN`.

### Enums

| Enum | Values |
|---|---|
| `EventStatus` | `DRAFT`, `PUBLISHED`, `CANCELLED`, `COMPLETED` |
| `EventType` | `IN_PERSON`, `ONLINE`, `HYBRID` |
| `EventVisibility` | `PUBLIC`, `FRIENDS`, `PRIVATE`, `GROUP` |
| `RecurrenceType` | `NONE`, `DAILY`, `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `YEARLY` |
| `RsvpStatus` | `GOING`, `MAYBE`, `NOT_GOING`, `INVITED`, `WAITLIST`, `CHECKED_IN` |

### `EventResponse` (key fields)
`id` uuid, `title`, `slug`, `description` (nullable), `hostId` int64, `groupId` uuid (nullable), `categoryId`/`categoryName` (nullable), `eventType`, `visibility`, `status`, `acceptingRsvps` bool, `coverImageUrl` (nullable), `startTime` date-time, `endTime` date-time (nullable), `timezone` (nullable), `isAllDay` bool, `venueName`/`address`/`city`/`country` (nullable), `maxAttendees` int32 (nullable), `goingCount`/`maybeCount`/`invitedCount` int32, `isTicketed` bool, `ticketPrice` number (nullable), `ticketCurrency` (nullable), `recurrenceType` (nullable), `allowGuests`/`showGuestList` bool, `createdAt` date-time, `host` `UserSummary`, `coHosts` `UserSummary[]` (nullable), `isHost`/`isCoHost` bool (nullable).

### `EventSummaryResponse`
`id` uuid, `title`, `slug`, `coverImageUrl` (nullable), `startTime` date-time, `endTime` (nullable), `venueName`/`city` (nullable), `eventType`, `goingCount` int32, `isTicketed` bool, `ticketPrice`/`ticketCurrency` (nullable).

### `RsvpResponse`
`id` uuid, `eventId` uuid, `userId` int64, `status` `RsvpStatus`, `guestCount` int32 (nullable), `note` (nullable), `respondedAt` date-time (nullable), `checkedInAt` date-time (nullable), `notificationsEnabled` bool, `user` `UserSummary`.

### `CoHostResponse`
`id` uuid, `eventId` uuid, `userId` int64, `username`, `displayName` (nullable), `avatarUrl` (nullable), `addedAt` date-time, `user` `UserSummary`.

### `CategoryResponse` (event category)
`id` uuid, `name`, `slug`, `description` (nullable), `icon` (nullable), `color` hex (nullable), `displayOrder` int32, `eventCount` int32, `isActive` bool, `createdAt`/`updatedAt` date-time.

---

## 3.1 Events — `/api/v1/events`

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /` | Body `CreateEventRequest` (below) | `EventResponse` | 201 | 400, 401 |
| 2 | `PUT /{eventId}` | path uuid; Body `UpdateEventRequest` (all optional; adds `status`, `acceptingRsvps`) — edit perm | `EventResponse` | 200 | 400, 401, 403 |
| 3 | `DELETE /{eventId}` | path uuid — host only | — | 204 | 400, 401, 403 |
| 4 | `GET /{eventId}` | path uuid (auth optional) | `EventResponse` | 200 | 400, 401, 403 |
| 5 | `GET /slug/{slug}` | path slug (auth optional) | `EventResponse` | 200 | 400, 401, 403 |
| 6 | `POST /{eventId}/publish` | path uuid — edit perm | `EventResponse` | 200 | 400 (only DRAFT), 401, 403 |
| 7 | `POST /{eventId}/cancel` | path uuid; `reason` (query, optional) — host only | `EventResponse` | 200 | 400, 401, 403 |
| 8 | `GET /upcoming` | `page`, `size` | `PageResponse<EventSummaryResponse>` | 200 | 400, 401 |
| 9 | `GET /search` | `query` **required**, `page`, `size` | `PageResponse<EventSummaryResponse>` | 200 | 400, 401 |
| 10 | `GET /popular` | `page`, `size` | `PageResponse<EventSummaryResponse>` | 200 | 400, 401 |
| 11 | `GET /my/upcoming` | `page`, `size` | `PageResponse<EventSummaryResponse>` | 200 | 400, 401 |
| 12 | `GET /my/hosted` | `page`, `size` | `PageResponse<EventSummaryResponse>` | 200 | 400, 401 |

**`CreateEventRequest`:** `title` string **required** (3–200); `description` ≤10000; `groupId`/`categoryId` uuid; `eventType`, `visibility` enums; `coverImageUrl`; `startTime` date-time **required, must be future**; `endTime`; `timezone`; `isAllDay` bool; `venueName`/`address`/`city`/`country`; `latitude`/`longitude` double; `onlineUrl`/`onlinePlatform`; `maxAttendees` int32 (min 1); `isTicketed` bool; `ticketPrice` (≥0.00); `ticketCurrency`; `recurrenceType`; `recurrenceEndDate`; `allowGuests` bool; `guestsPerRsvp` int32 (0–10); `showGuestList`/`allowComments`/`requireApproval` bool.

---

## 3.2 RSVPs — `/api/v1/events/{eventId}/rsvp`

`eventId` (uuid) is a path variable on every endpoint.

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `POST /` | Body `RsvpRequest`: `status` `RsvpStatus` **required**; `guestCount` int32 (0–10, default 0); `note` ≤500 | `RsvpResponse` | 201 | 400 (capacity), 401 |
| 2 | `DELETE /` | — | — | 204 | 400 (host can't cancel), 401 |
| 3 | `GET /attendees` | `status` `RsvpStatus` (optional), `page`, `size` | `PageResponse<RsvpResponse>` | 200 | 400, 401 |
| 4 | `GET /going` | `page`, `size` | `PageResponse<RsvpResponse>` | 200 | 400, 401 |
| 5 | `GET /checked-in` | `page`, `size` | `PageResponse<RsvpResponse>` | 200 | 400, 401 |
| 6 | `GET /going/ids` | — | `Long[]` | 200 | 400, 401 |
| 7 | `POST /{userId}/check-in` | path `userId` int64 — host/co-host | `RsvpResponse` | 200 | 400, 401, 403 |
| 8 | `DELETE /{userId}/check-in` | path `userId` int64 — host/co-host | `RsvpResponse` | 200 | 400, 401, 403 |
| 9 | `GET /my-status` | — | `RsvpResponse` | 200 | 400 (no RSVP), 401 |
| 10 | `POST /co-hosts` | Body `AddCoHostRequest`: `userId` int64 **required** — host only | `CoHostResponse` | 201 | 400, 401, 403 |
| 11 | `DELETE /co-hosts/{userId}` | path `userId` int64 — host only | — | 204 | 400, 401, 403 |
| 12 | `GET /co-hosts` | — | `CoHostResponse[]` | 200 | 400, 401 |

---

## 3.3 Event Categories — `/api/v1/event-categories`

| # | Method & Path | Auth | Request | Response | Success | Errors |
|---|---|---|---|---|---|---|
| 1 | `GET /` | user | — | `CategoryResponse[]` (active) | 200 | 401 |
| 2 | `GET /all` | **ADMIN** | — | `CategoryResponse[]` (incl. inactive) | 200 | 401, 403 |
| 3 | `GET /{id}` | user | path uuid | `CategoryResponse` | 200 | 400, 401 |
| 4 | `GET /slug/{slug}` | user | path slug (1–120) | `CategoryResponse` | 200 | 400, 401 |
| 5 | `POST /` | **ADMIN** | Body `CreateCategoryRequest`: `name` **required** (2–100); `slug` ≤120 (lowercase/digits/hyphen); `description` ≤500; `icon` ≤50; `color` hex; `displayOrder` int32 (0–1000) | `CategoryResponse` | 201 | 400, 401, 403 |
| 6 | `PUT /{id}` | **ADMIN** | path uuid; Body `UpdateCategoryRequest` (all optional; adds `isActive`) | `CategoryResponse` | 200 | 400, 401, 403 |
| 7 | `DELETE /{id}` | **ADMIN** | path uuid (hard delete; fails if has events) | — | 204 | 400, 401, 403 |
| 8 | `PATCH /{id}/toggle-active` | **ADMIN** | path uuid | `CategoryResponse` | 200 | 400, 401, 403 |
| 9 | `PATCH /{id}/reorder` | **ADMIN** | path uuid; `displayOrder` int32 **required** (0–1000) | `CategoryResponse` | 200 | 400, 401, 403 |

---

## 3.4 Internal Events (S2S) — `/api/v1/internal/events`

> **Auth: `X-Internal-Api-Key` header** (no JWT). Identity passed explicitly via `organisationId`/`hostId`.

| # | Method & Path | Request | Response | Success |
|---|---|---|---|---|
| 1 | `POST /` | Body `InternalCreateEventRequest` (like CreateEvent + `organisationId` int64 **required, positive**, `hostId` int64 **required, positive**, `status` enum; `startTime` not required-future) | `EventResponse` | 201 |
| 2 | `POST /{eventId}/rsvp/bulk` | path uuid; Body `BulkRsvpRequest`: `userIds` int64[] **required** (≤1000), `status` **required**, `organisationId` **required** | `BulkRsvpResult` `{ created, alreadyExists }` | 200 |
| 3 | `GET /{eventId}/rsvp/non-responded` | path uuid; `organisationId` **required** | `Long[]` | 200 |
| 4 | `GET /by-org` | `organisationId` **required**, `start` date-time **required**, `end` date-time **required**, `status`/`eventType` optional | `EventSummaryResponse[]` | 200 |
| 5 | `DELETE /{eventId}` | path uuid; `organisationId` **required** (sets CANCELLED) | `EventResponse` | 200 |

Errors: 400 (validation), 401 (bad/missing internal key).

---

# MODULE 4 — Game Groups (Esports)

Base path `/api/v1/game-groups`. **Bearer JWT** except where noted public.

### `GameGroupResponse` (key fields)
`id` uuid, `name`, `slug`, `description`, `groupType` `GroupType`, `ownerId` int64, `memberCount` int32, `isVerified` bool, `gameTag`, `gameId` uuid, `region`, `platform`, `minRank`/`maxRank`, `scrimCount` int32, `winRate` number, `averageElo` int32, `requireGameAccount` bool, `discordServerId`/`discordChannelId`, `isMember`/`isAdmin` bool, `createdAt` date-time.

| # | Method & Path | Auth | Request | Response | Success | Errors |
|---|---|---|---|---|---|---|
| 1 | `POST /` | yes | Body `CreateGameGroupRequest`: `name` **required** (3–100); `description` ≤5000; `groupType` (default PUBLIC); `gameTag` **required** (≤50); `gameId` uuid; `region` ≤30; `platform` ≤20; `minRank`/`maxRank` ≤30; `requireGameAccount` bool; `discordServerId`/`discordChannelId` ≤30 | `GameGroupResponse` | 201 | 400, 401 |
| 2 | `GET /{groupId}` | optional | path uuid | `GameGroupResponse` | 200 | — |
| 3 | `PUT /{groupId}/profile` | yes | path uuid; Body `UpdateGameGroupProfileRequest`: `region`, `platform`, `minRank`, `maxRank`, `requireGameAccount`, `discordServerId`, `discordChannelId` | `GameGroupResponse` | 200 | — |
| 4 | `GET /search` | yes | `gameTag`, `region` (default ""), `page`, `size` (page max 10000) | `PageResponse<GameGroupResponse>` | 200 | 400 |
| 5 | `GET /{groupId}/embed` | **public** | path uuid | `GameGroupEmbedResponse`: `id`, `name`, `slug`, `groupType`, `gameTag`, `region`, `memberCount`, `isVerified`, `iconUrl` | 200 | — |
| 6 | `PUT /{groupId}/scrim-filter` | yes | path uuid; Body `ScrimFilterRequest`: `gameTag` **required** (≤50); `region` ≤30; `format` ≤10; `mapPool`; `minTeamSize`/`maxTeamSize` int32; `minElo`/`maxElo` int32; `availabilitySlots`; `isActive` bool | `ScrimFilterResponse` | 200 | — |
| 7 | `GET /{groupId}/scrim-filter` | yes | path uuid | `ScrimFilterResponse` | 200 | — |
| 8 | `POST /{groupId}/lfs/broadcast` | yes | path uuid; Body `LfsBroadcastRequest`: `groupId` uuid **required** (dup of path), `message` ≤500 | — | 200 | — |
| 9 | `DELETE /{groupId}/lfs/broadcast` | yes | path uuid | — | 204 | — |
| 10 | `POST /{groupId}/lfs/match-found` | yes | path uuid; Body `LfsMatchFoundRequest`: `groupId` **required**, `matchedGroupId` uuid **required**, `gameTag` ≤50 | `LfsMatchFoundResponse`: `groupId`, `matchedGroupId`, `gameTag`, `region`, `matchedAt` | 200 | — |
| 11 | `POST /{groupId}/teams` | yes | path uuid; Body `LinkTeamRequest`: `teamId` int64 **required** | `GroupTeamLinkResponse`: `id`, `groupId`, `teamId`, `teamName`, `linkedBy`, `linkedAt` | 201 | — |
| 12 | `GET /{groupId}/teams` | yes | path uuid | `GroupTeamLinkResponse[]` | 200 | — |
| 13 | `DELETE /{groupId}/teams/{teamId}` | yes | path uuid + int64 | — | 204 | — |
| 14 | `POST /{groupId}/transfer-ownership` | yes | path uuid; Body `TransferOwnershipRequest`: `newOwnerId` int64 **required** | — | 200 | — |
| 15 | `POST /accounts/link` | yes | Body `LinkGameAccountRequest`: `gameTag` **required** (≤50), `gameAccountId` **required** (≤100), `gameAccountName` ≤100 | `GameAccountLinkResponse`: `id`, `userId`, `gameTag`, `gameAccountId`, `gameAccountName`, `verificationStatus`, `verifiedAt`, `createdAt` | 201 | — |
| 16 | `GET /accounts/me` | yes | — | `GameAccountLinkResponse[]` | 200 | — |
| 17 | `GET /{groupId}/audit-log` | yes | path uuid; `page`, `size` | `PageResponse<AuditLogResponse>`: item = `id`, `groupId`, `actorUserId`, `action`, `targetType`, `targetId`, `details`, `createdAt` | 200 | 400 |
| 18 | `GET /{groupId}/member-ids` | yes | path uuid | `Long[]` (approved members) | 200 | — |
| 19 | `POST /{groupId}/join` | yes | path uuid; Body `JoinGroupRequest`: `message` ≤500 (rank/account gated) | `GroupMemberResponse` | 200 | 400 (rank/account not met), 401 |
| 20 | `GET /lfs/search` | public | `gameTag`, `region`, `format` (default ""), `minElo`/`maxElo` int32 optional, `page`, `size` | `PageResponse<LfsGroupResponse>` | 200 | 400 |
| 21 | `GET /leaderboard` | public | `gameTag`, `sortBy` (`WIN_RATE`/`SCRIM_COUNT`/`AVERAGE_ELO`, default WIN_RATE), `page`, `size` | `PageResponse<LeaderboardEntryResponse>` | 200 | 400 |
| 22 | `GET /{groupId}/chat-channel` | yes | path uuid | `ChatChannelResponse`: `groupId`, `chatChannelId` (null if not provisioned) | 200 | **404** (not yet provisioned), 401 |

**`LfsGroupResponse`:** `groupId`, `groupName`, `slug`, `memberCount`, `gameTag`, `region`, `platform`, `averageElo`, `scrimCount`, `format`, `mapPool`, `minTeamSize`, `maxTeamSize`, `minElo`, `maxElo`, `availabilitySlots`, `lastBroadcastAt`.
**`LeaderboardEntryResponse`:** `groupId`, `groupName`, `slug`, `memberCount`, `gameTag`, `region`, `scrimCount`, `winRate`, `averageElo`.
**`ScrimFilterResponse`:** `id`, `groupId`, `gameTag`, `region`, `format`, `mapPool`, `minTeamSize`, `maxTeamSize`, `minElo`, `maxElo`, `availabilitySlots`, `isActive`, `lastBroadcastAt`.

---

# MODULE 5 — User Suggestions

Base path `/api/v1/suggestions`. **Bearer JWT.**

### `SuggestionResponse`
| Field | Type | Notes |
|---|---|---|
| `suggestedUserId` | int64 | |
| `mutualFriendCount` | int32 | 0 for popularity-based |
| `reason` | string | e.g. "8 mutual friends" |
| `source` | string | `FRIEND_OF_FRIEND` \| `POPULAR` |
| `user` | `UserSummary` (nullable) | from SSO |

| # | Method & Path | Description | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `GET /` | Suggested users (mutual friends; falls back to popular for new accounts) | `SuggestionResponse[]` | 200 | 401 |

---

# MODULE 6 — Friends Finder

Base path `/api/v1/friends-finder`. **Bearer JWT.**

### `FriendFinderResultResponse`
`userId` int64, `gamerTag`, `skillLevel` int32 (1–10), `region`, `avatarUrl` (nullable), `friendshipStatus` (`NONE`/`PENDING`/`ACCEPTED`), `mutualFriendCount` int32, `scrimCount` int32, `lastScrimAt` date-time (nullable).

### `FriendSuggestionResponse`
`id` uuid, `suggestedUserId` int64, `gamerTag`, `avatarUrl` (nullable), `suggestionScore` double (0–100), `source`, `gameMode` (nullable), `interactionCount` int32, `mutualFriendCount` int32, `createdAt` date-time.

### `ScrimHistoryResponse`
`id` uuid, `opponentUserId` int64, `opponentGamerTag`, `gameMode`, `matchQualityScore` double (0–100), `playedAt` date-time, `friendshipStatus` (`NONE`/`PENDING`/`ACCEPTED`).

| # | Method & Path | Request | Response | Success | Errors |
|---|---|---|---|---|---|
| 1 | `GET /search` | `region` (optional), `minSkillLevel`/`maxSkillLevel` int (optional), `page`, `size` | `PageResponse<FriendFinderResultResponse>` | 200 | 400, 401 |
| 2 | `GET /suggestions` | `page`, `size` | `PageResponse<FriendSuggestionResponse>` | 200 | 400, 401 |
| 3 | `POST /suggestions/{suggestionId}/dismiss` | path `suggestionId` uuid | — | 204 | 401 |
| 4 | `GET /scrim-history` | `page`, `size` (**default size = 10**) | `PageResponse<ScrimHistoryResponse>` | 200 | 400, 401 |

---

## Appendix — Testing checklist / known quirks

1. **404 vs 400:** most "not found" errors return **400**. The only genuine `404` is game-group `chat-channel`.
2. **User id path vars are `Long`**, not UUID — some Swagger examples wrongly show UUIDs. Send numeric ids.
3. **Auth on "public" game-group endpoints** (`embed`, `lfs/search`, `leaderboard`) — documented public but the class declares `bearerAuth`; test both with and without a token and confirm behaviour against the deployed gateway.
4. **`LfsBroadcastRequest` / `LfsMatchFoundRequest`** require `groupId` in the body **and** the path — send it in both.
5. **Pagination bounds differ:** Game Groups allow `page` up to 10000; others up to 1000. `scrim-history` defaults `size` to 10, everything else to 20.
6. **Enum values are case-sensitive** on input except `ContentType` (case-insensitive). Invalid enum → 400.
7. **Timestamps** are always UTC `...'Z'` strings.
8. **Empty/optional bodies:** `join`, `ban`, invite-link, RSVP-less flows accept an empty JSON object `{}` where the body is marked optional.
9. **Internal endpoints** need `X-Internal-Api-Key`, not a JWT — frontend normally should NOT call these directly.
