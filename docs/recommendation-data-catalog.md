# Friend Recommendation — Data Catalog (ML hand-off)

> **Purpose:** A complete inventory of every piece of user & relationship data
> available for building a friend/user recommendation model. Written for an **ML
> engineer** — each field lists its type, source service, fetch path, and whether
> it's live today. Every field was read directly from the entity/DTO source across
> three repos — this is ground truth, not a guess.
>
> **Scope (3 services):**
> - `social-service` (`com.billboard.social`) — the social graph & interactions.
> - `sso-service` (`sso-for-java-spring-boot-eureka-microservices`, `main`) — user
>   identity & demographics.
> - `esports-backend` (`origin/develop`) — player profile, skill, stats.
>
> **Start with §10 (Feature Matrix)** for the consolidated feature list; §2–§6 are
> the per-source detail behind it.
>
> **Verified:** 2026-07-12 against the branches named above.

---

## 0. The one architectural fact that shapes everything

**There is no local `User` entity in this service.** A user is just a bare
`Long userId` — the SSO user id carried in the JWT (`UserPrincipal.getId()`).
Every table references users by that `Long`. Profile attributes (username, email,
avatar, bio, location, interests) live in *other* services and are fetched on demand.

**Consequence for recommendations:** the algorithm operates over **edges between
`Long` ids** (who is connected to / interacted with whom). Per-user *attributes*
(age, gender, location, skill, country) are **not stored here** — but they **do
exist** in the SSO and esports services (§2, §2b). The distinction that matters for
the ML model is **stored upstream** vs. **currently exposed to social-service**:
much of the useful attribute data is stored but not yet exposed. Each field below is
tagged with its **access status**.

> **Reading guide for the ML engineer:** every feature has three properties you care
> about — **(a) type**, **(b) source service**, and **(c) availability**: is it live
> today, or does it need an endpoint/DTO change to reach? Those are called out per
> field, and summarized in the **Feature Matrix (§10)**.

---

## 1. Where the data lives

| Store | Tech | What it holds |
|---|---|---|
| Primary DB | **PostgreSQL** (Spring Data JPA, Flyway V1–V11) | All edge/entity tables below |
| Cache / ephemeral | **Redis** | Online **presence** (`presence:{userId}` TTL keys) |
| Messaging | **Kafka + RabbitMQ** | Scrim-completed events → feed `scrim_history` |
| External svc | **sso-service** (Feign) | User identity (thin) |
| External svc | **esports-backend** (Feign) | Gamer tag, player stats, search |

All persisted entities extend `BaseEntity`, so every row also carries:
`createdAt`, `updatedAt`, `deletedAt` (soft delete), `version` (optimistic lock).
`createdAt` is your universal **recency** signal.

---

## 2. User identity & demographics (SSO service)

> **Verified against `sso-for-java-spring-boot-eureka-microservices` (`main`).**
> The SSO `User` table stores substantially more than the social-service currently
> receives. **Column names below are the real DB columns** in the `users` /
> `user_details` tables — usable directly if the ML pipeline reads SSO's DB or a
> replica, even where no API exposes them yet.

### 2a. What social-service receives TODAY (thin)
`GET /api/v1/users/{userId}/basic` → `UserBasicResponse` — and the auth social
summary — expose **only**:

| Field | Type | Notes |
|---|---|---|
| `id` | Long | SSO user id |
| `username` | String (`name`) | display |
| `email` | String | display |

Resolved via `UserSummaryResolver.resolveForDisplay(userId)`, which **never throws**
(returns an id-only stub on failure). **Availability today: id/username/email only.**

### 2b. What SSO actually STORES — `users` table (rich, not yet exposed)
| DB column | Type | Recommendation value | Access status |
|---|---|---|---|
| `id` | Long (PK) | user key | ✅ exposed |
| `name` | String(191) | display | ✅ exposed |
| `email` | String(191, unique) | identity | ✅ exposed |
| `password` | String | — **never use** | 🔒 secret |
| **`dob`** | String(20) | **age / age-band homophily** | ⚠️ stored, not exposed |
| **`gender`** | String(20) | demographic homophily | ⚠️ stored, not exposed |
| **`street_address`** | TEXT | **location / geo proximity** | ⚠️ stored, not exposed |
| `phone_number` | String(20) | contact-graph matching (hashed) | ⚠️ stored, not exposed |
| **`user_type`** | enum | `MY_CHANNEL, PREMIUM, NORMAL, ADVERTISER, ECOMMERCE, USB` — segment feature | ⚠️ stored, not exposed |
| `type` | enum | `NORMAL, CHANNEL, ADVERTISER, ECOMMERCE, RESTAURANT_*` — account class | ⚠️ stored, not exposed |
| `status` | enum | `ACTIVE / INACTIVE` — **filter inactive candidates** | ⚠️ stored, not exposed |
| `channel_id` | Long | channel affiliation (co-channel signal) | ⚠️ stored, not exposed |
| `partner_id` | String(64) | partner/tenant segment | ⚠️ stored, not exposed |
| `membership_expires_at` | LocalDateTime | membership tier/recency | ⚠️ stored, not exposed |
| `created_at` | LocalDateTime | **account age** | ⚠️ stored, not exposed |
| `updated_at` | LocalDateTime | last-activity proxy | ⚠️ stored, not exposed |
| `deleted_at` | LocalDateTime | soft-delete → **exclude** | ⚠️ stored, not exposed |
| `roles` | Set&lt;Role&gt; | role-based segment | ⚠️ stored, not exposed |
| `groups` | Set&lt;Group&gt; | SSO group membership | ⚠️ stored, not exposed |
| `organisations` | Set&lt;UserOrganisation&gt; | **org/tenant co-membership** | ⚠️ stored, not exposed |

### 2c. `user_details` table (`UserDetail`) — mostly device/streaming, low rec value
`phone_number`, `chat_id`, `unique_id`, **`allowed_regions`** (geo — useful),
`app_version`, `expiration_date`, plus device fields (`MAC_address`, `ip_address`,
`serial_number`, `allowed_devices`, `serial_number`). Only `allowed_regions` and
`chat_id` are plausibly useful; the rest are DRM/device data — ignore for recs.

### 2d. A rich endpoint already exists (admin-only)
`GET /api/v1/users/{id}` → **`UserDetailResponse`** already returns
`name, email, phoneNumber, dob, gender, streetAddress, status, userType, type,
createdAt, updatedAt, roles`. It's **admin-secured**, so to feed the recommender you
either (a) add a service-to-service/internal-key variant, or (b) widen the public
`/basic` payload. **No new data collection needed — the columns already exist.**

### `PlayerDto` — as **currently mapped** in social-service
| Field | Type | Notes |
|---|---|---|
| `id` | Long | esports player PK |
| `user` | String | SSO user id (as string) |
| `gamerTag` | String | display |

> ⚠️ This is the *social-service's* trimmed projection. It drops most of what the
> upstream service actually returns — see §2b. The stubbed region scorer exists
> because of **this** DTO, not because the data is missing upstream.

### §2b. What esports-backend actually returns (`origin/develop`) — RICHER

> **Verified against `esports-backend` `origin/develop`.** The upstream
> `GET /api/players/user/{userId}` returns a **full `PlayerDTO`** — the social-service
> just isn't mapping the extra fields. Extending the local `PlayerDto` unlocks all of
> these with **no upstream change required**.

**`PlayerDTO` (develop) — actual response body**
| Field | Type | Recommendation value |
|---|---|---|
| `id` | Long | player PK |
| `user` | String | SSO user id |
| `email` | String | identity |
| `gamerTag` | String | display |
| **`avatarUrl`** | String | display enrichment (unblocks suggestion UI) |
| **`bio`** | String (≤500) | possible text-affinity signal |
| **`preferredRole`** | String | **complementary-role matching** (e.g. carry ↔ support) |
| **`skillLevel`** | Integer (default 1) | **skill-bracket homophily** |
| **`country`** | String (name) | **real region proximity** — replaces the stubbed `8.0` |
| **`isAvailable`** | Boolean | filter / rank boost |
| `createdAt` / `updatedAt` | LocalDateTime | account age / activity recency |
| `teamMemberships` | List | **shared-team affinity** signal |

**`PlayerStatistics` (develop) — via `getPlayerStatistics` / `/api/statistics/players/{id}`**
| Field | Type |
|---|---|
| `matchesPlayed`, `wins`, `losses`, `draws` | activity + outcomes |
| `totalKills`, `totalDeaths`, `totalAssists`, `killDeathRatio` | skill |
| `averageScore`, `winRate` | skill |
| `tournamentsParticipated`, `tournamentsWon` | prestige |
| `currentWinStreak`, `bestWinStreak` | form / momentum |

**Useful upstream endpoints already available on develop:**
- `GET /api/players/search/criteria?region=&minSkill=&maxSkill=` — server-side candidate filtering (native `searchByCriteria` on country + skill range).
- `GET /api/players/country/{country}`, `GET /api/players/skill-level/{level}`,
  `GET /api/players/available`, `GET /api/players/rankings` — pre-filtered candidate pools.
- `GET /api/players/{id}/teams` / `findActiveTeamsForPlayer` — shared-team signal source.
- `GET /api/statistics/mvp-players`, `/top-players`, `/rankings/players` — quality ranking.

---

## 3. Social-graph edges (the core recommendation input)

### `Friendship` — table `friendships` *(symmetric, one row per pair)*
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `requesterId` | Long | who sent the request |
| `addresseeId` | Long | who received it |
| `status` | enum | `PENDING, ACCEPTED, DECLINED, BLOCKED, CANCELLED` |
| `acceptedAt` | LocalDateTime | tenure of the friendship |
| `message` | String | request note |
| `mutualFriendsCount` | Integer | denormalized cache |

**This is the primary edge for friend-of-friend recommendation.** Key queries
(`FriendshipRepository`): `findFriendIds(user)`, `findMutualFriendIds(u1,u2)`
(native self-join), `areFriends`, `countFriends`, `findBetweenUsers`.

### `Follow` — table `follows` *(directed / asymmetric)*
| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `followerId` | Long | |
| `followingId` | Long | |
| `notificationsEnabled` | Boolean | |
| `isCloseFriend` | Boolean | strong explicit-affinity signal |
| `isMuted` | Boolean | negative signal — deprioritize |

Key queries (`FollowRepository`): `findFollowingIdsByFollowerId`,
`findFollowerIdsByFollowingId`, `findMutualFollows(user)` (follow-backs),
`findMostFollowedUserIds(excluded, limit)` (popularity / cold-start).

### `Block` — table `blocks` *(exclusion, not a positive signal)*
| Field | Type | Notes |
|---|---|---|
| `blockerId` / `blockedId` | Long | |
| `reason` | String | |
| **`hideFromSuggestions`** | Boolean (default **true**) | **must be honored as a hard exclude** |
| `blockMessages` / `blockPosts` / `blockComments` | Boolean | scope flags |

Key queries (`BlockRepository`): `isBlockedEitherWay(u1,u2)`,
`findBlockedUserIds(user)`, `findBlockedByUserIds(user)`.

---

## 4. Behavioral / interaction signals (who actually engages with whom)

These are the "real behavior" signals — usually stronger predictors than raw graph
topology.

### `ScrimHistory` — table `scrim_history` *(matches played together)*
| Field | Type | Notes |
|---|---|---|
| `userIdA` / `userIdB` | Long | the two players |
| `esportsMatchId` | Long | |
| `gameMode` | String | |
| `matchQualityScore` | Double | how good the match was |
| `playedAt` | LocalDateTime | recency |

Populated by `ScrimCompletedConsumer` (Kafka). Queries: `countBetweenUsers`,
`findRecentOpponents(user, since)`, `findByUserId`.

### `Reaction` — table `reactions` *(the "Like" edge)*
| Field | Type | Notes |
|---|---|---|
| `userId` | Long | who reacted |
| `contentType` | enum | `POST, COMMENT, PHOTO, VIDEO, ALBUM, EVENT, GROUP, STORY, …` |
| `contentId` | UUID | external content |
| **`contentOwnerId`** | Long | **whose content — a user→user affinity edge** |
| `reactionType` | enum | `LIKE, LOVE, HAHA, WOW, SAD, ANGRY` |

> The content itself lives in other services, but `userId → contentOwnerId` is a
> local behavioral affinity edge. **Currently unused by either recommender.**

### `Share` — table `shares`
| Field | Type | Notes |
|---|---|---|
| `userId` | Long | who shared |
| `contentType` / `contentId` | | |
| `contentOwnerId` | Long | affinity to the creator |
| `targetUserId` | Long | direct share-to-a-person — strong DM-like signal |
| `shareToFeed` / `shareToStory` / `isPrivateShare` | Boolean | |

### `Poke` — table `pokes` *(lightweight interaction)*
| Field | Type | Notes |
|---|---|---|
| `pokerId` / `pokedId` | Long | |
| `isActive` | Boolean | |
| `pokedBackAt` | LocalDateTime | reciprocation |
| `pokeCount` | Integer | repeated interaction |

### `Invitation` — table `invitations`
| Field | Type | Notes |
|---|---|---|
| `inviterId` / `inviteeId` / `inviteeEmail` | | |
| `invitationType` | enum | `GROUP, EVENT, PAGE, APP` |
| `targetId` | UUID | |
| `status`, `expiresAt`, `acceptedAt`, `declinedAt`, `inviteCode` | | |

---

## 5. Community / co-membership signals (latent ties)

Two people in the same group/team/event have a latent connection.

### `Group` + `GroupMember`
- `Group`: `name`, `groupType` (`PUBLIC/…`), `ownerId`, `categoryId`, `location`,
  `memberCount`, `postCount`, `isVerified`, `isFeatured`.
- `GroupMember`: `userId`, `role` (`MemberRole`), `status` (`MemberStatus`),
  `joinedAt`, `postCount`, **`contributionScore`** (engagement within the group).

### `GameGroupProfile` (gaming affinity attributes on a group)
`gameTag`, `gameId`, `region`, `platform`, `minRank`/`maxRank`, `scrimCount`,
`winRate`, `averageElo`. — *These attributes exist at the **group** level, not the
user level.*

### `GameAccountLink` (per-user game account)
`userId`, `gameTag`, `gameAccountId`, `gameAccountName`, `verificationStatus`,
`verifiedAt`. — the closest thing to a per-user gaming attribute stored locally.

### `Event` + `EventRsvp` / `EventAttendee` (co-presence)
- `Event`: `hostId`, `groupId`, `categoryId`, `eventType`, `visibility`,
  `startTime`, `city`, `country`, `latitude`, `longitude`.
- `EventRsvp` / `EventAttendee`: `userId`, `status`/`rsvpStatus`, `checkedInAt`,
  `isHost`, `isCoHost`, `guestCount`. — shared attendance / check-in = co-presence.

---

## 6. Pre-computed recommendation data (already exists)

### `FriendSuggestion` — table `friend_suggestions` *(persisted output)*
| Field | Type | Notes |
|---|---|---|
| `userId` | Long | recipient |
| `suggestedUserId` | Long | candidate |
| `suggestionScore` | Double | 0–100 |
| `source` | String | `SCRIM_OPPONENT / ALGORITHMIC / MUTUAL_FRIENDS` |
| `gameMode` | String | |
| `interactionCount` | Integer | |
| `mutualFriendCount` | Integer | |
| `dismissed` / `dismissedAt` | | **negative-feedback signal for tuning** |

Written nightly by `FriendSuggestionScheduler` (cron `0 0 3 * * *`). Current scoring
(0–100): interaction frequency 0–30, mutual friends 0–25, region proximity 0–20
(**stubbed at constant 8** — no region data reachable), match quality 0–25.

### Two live recommenders already exist
| System | Style | Signals used | Endpoint |
|---|---|---|---|
| `suggestion` (`UserSuggestionService`) | real-time graph | friends-of-friends + popular fallback | `GET /api/v1/suggestions` |
| `friendsfinder` (`FriendsFinderService`) | batch, scored, gaming | scrim + mutual + match quality | `GET /api/v1/friends-finder/*` |

---

## 7. Signal → recommendation-value map

| Data you have | Predictive strength | Used today? |
|---|---|---|
| Mutual friends (`findMutualFriendIds`) | ★★★★★ | yes (both) |
| Direct scrims together (`ScrimHistory`) | ★★★★★ | yes (friendsfinder) |
| Reactions/Shares to same owner (`contentOwnerId`) | ★★★★☆ | **no** |
| Direct share-to-person (`Share.targetUserId`) | ★★★★☆ | **no** |
| Shared groups / game-groups (`GroupMember`) | ★★★★☆ | **no** |
| Shared events / check-ins (`EventAttendee`) | ★★★☆☆ | **no** |
| Mutual follows (`findMutualFollows`) | ★★★☆☆ | partial |
| `isCloseFriend` explicit flag | ★★★☆☆ | **no** |
| Poke reciprocation (`pokedBackAt`) | ★★☆☆☆ | **no** |
| Recency (`createdAt`, `playedAt`) | ★★★☆☆ multiplier | partial |
| Popularity (`findMostFollowedUserIds`) | ★★☆☆☆ (cold-start) | yes |
| Region / country proximity | ★★★☆☆ | **available upstream (develop)** — just needs DTO mapping |
| Skill-bracket homophily | ★★★☆☆ | **available upstream (develop)** — `skillLevel` |
| Complementary role (`preferredRole`) | ★★★☆☆ | **available upstream (develop)** |
| Shared teams (`teamMemberships`) | ★★★★☆ | **available upstream (develop)** |
| Interests / bio topics | ★★☆☆☆ | `bio` text available upstream; SSO still has none |

**Hard excludes (never score, always filter):** self, existing/pending friendships,
`isBlockedEitherWay`, `Block.hideFromSuggestions`, optionally `Follow.isMuted`.

---

## 8. Gaps — what we do NOT have

1. **SSO profile is thin *over the wire*, not at rest.** The `/basic` endpoint returns
   id/username/email only — BUT the SSO `users` table **stores** `dob`, `gender`,
   `street_address`, `user_type`, `status`, `created_at`, roles, and org membership
   (§2b), and the admin `UserDetailResponse` already serializes most of them. The gap
   is an **exposure gap** (widen `/basic` or add an internal endpoint), not a
   data-collection gap.
2. **Region/skill homophily is NOT blocked upstream — only unmapped locally.** The
   esports `develop` branch already returns `country`, `skillLevel`, `preferredRole`,
   `teamMemberships`. The scorer's stubbed `8.0` region dimension can be made real by
   **extending the social-service `PlayerDto`** (a local change) — no upstream work.
   *Caveat:* only covers users who have an esports player profile.
3. **No content/post/tag tables here** — content affinity only exists as
   `Reaction`/`Share` *edges* (`contentOwnerId`), not as topics/interests.
4. **Two overlapping recommenders** that don't share signals — should be unified.
5. **Scheduler scaling issues** — `ScrimHistoryRepository.findAll()` loads the whole
   table into memory (has a `TODO`); per-pair mutual-friend lookups are N+1.

---

## 9. Open decisions (needed before building)

1. **Friend vs Follow semantics** — recommend people to *friend* (symmetric) or to
   *follow* (asymmetric)? Changes which edges dominate.
2. **Gaming-first vs general-social** — how much weight to the scrim signal, which
   only exists for esports users.
3. **Map the richer esports `PlayerDto`?** — the esports `develop` branch already
   returns `country`/`skillLevel`/`preferredRole`/`teamMemberships`. Extending the
   **local** `PlayerDto` (no upstream change) unlocks real region + skill scoring for
   users who have an esports profile. Only SSO-side attributes (for non-gamers) would
   need an upstream change.

---

## 10. ML Feature Matrix (the consolidated view)

> **This is the table to build features from.** Every recommendation feature, with
> its type, source, how to fetch it, and whether it's live today.
>
> **Access legend:** ✅ live in social-service · 🟡 upstream, needs a DTO/endpoint
> map (no new data) · 🔒 sensitive/PII (hash or drop).
>
> **Two entity types of feature:**
> **PAIR** = defined for a (user, candidate) pair (the core ranking signals).
> **NODE** = a single-user attribute (used for homophily / filtering / cold-start).

### 10a. Pair features (strongest — behavioral & graph)
| Feature | Kind | Type | Source / how to fetch | Access |
|---|---|---|---|---|
| Mutual friends count | PAIR | int | `FriendshipRepository.findMutualFriendIds(a,b)` | ✅ |
| Are-friends / pending | PAIR | bool/enum | `findBetweenUsers`, `areFriends` | ✅ |
| Mutual follows count | PAIR | int | `FollowRepository.findMutualFollows` | ✅ |
| Follows-back (reciprocal) | PAIR | bool | `existsByFollowerIdAndFollowingId` both ways | ✅ |
| Scrims played together | PAIR | int | `ScrimHistoryRepository.countBetweenUsers` | ✅ |
| Avg match quality together | PAIR | double | `ScrimHistory.matchQualityScore` (avg) | ✅ |
| Days since last scrim | PAIR | int | `ScrimHistory.playedAt` (max) | ✅ |
| Reactions to same creators | PAIR | int | `Reaction.contentOwnerId` overlap | ✅ (unused) |
| Shares to each other | PAIR | int | `Share.targetUserId` | ✅ (unused) |
| Poke count / reciprocated | PAIR | int/bool | `Poke.pokeCount`, `pokedBackAt` | ✅ (unused) |
| Shared groups count | PAIR | int | `GroupMember` overlap by `userId` | ✅ (unused) |
| Shared game-groups count | PAIR | int | `GameGroupProfile` / `GroupMember` | ✅ (unused) |
| Shared events / co-checkin | PAIR | int | `EventAttendee` overlap | ✅ (unused) |
| Shared esports teams | PAIR | int | `PlayerDTO.teamMemberships` / `/players/{id}/teams` | 🟡 |
| Same country | PAIR | bool | `PlayerDTO.country` (both) | 🟡 |
| Skill-level delta | PAIR | int | `PlayerDTO.skillLevel` (abs diff) | 🟡 |
| Complementary role | PAIR | bool | `PlayerDTO.preferredRole` (role fit) | 🟡 |
| Age delta | PAIR | int | SSO `dob` (both) | 🟡 |
| Same city / geo distance | PAIR | float | SSO `street_address` / `allowed_regions` | 🟡 |
| Same org / channel | PAIR | bool | SSO `organisations`, `channel_id` | 🟡 |

### 10b. Node features (per-user — homophily, cold-start, filters)
| Feature | Type | Source | Access |
|---|---|---|---|
| Friend count / degree | int | `FriendshipRepository.countFriends` | ✅ |
| Follower / following count | int | `FollowRepository.countBy*` | ✅ |
| Follower count (popularity) | int | `findMostFollowedUserIds` | ✅ |
| Account age | days | SSO `created_at` / esports `createdAt` | 🟡 |
| Last-active proxy | ts | SSO `updated_at`, presence (Redis) | ✅/🟡 |
| Online now | bool | Redis `presence:{userId}` | ✅ |
| Skill level / win rate | int/float | esports `skillLevel`, `PlayerStatistics.winRate` | 🟡 |
| K/D, tournaments won, streak | numeric | `PlayerStatistics.*` | 🟡 |
| Country / region | string | esports `country`, SSO `street_address` | 🟡 |
| Gender | string | SSO `gender` | 🟡🔒 |
| Age band | int | SSO `dob` | 🟡🔒 |
| User type / segment | enum | SSO `user_type`, `type` | 🟡 |
| Account status (ACTIVE) | enum | SSO `status` | 🟡 |
| Avatar / bio | string | esports `avatarUrl`, `bio` | 🟡 |
| Is-available flag | bool | esports `isAvailable` | 🟡 |

### 10c. Labels / feedback signals (for supervised training)
| Signal | Meaning | Source |
|---|---|---|
| Friend request sent → accepted | positive label | `Friendship.status=ACCEPTED`, `acceptedAt` |
| Friend request declined/cancelled | negative label | `status=DECLINED/CANCELLED` |
| Suggestion dismissed | hard negative | `FriendSuggestion.dismissed`, `dismissedAt` |
| Follow created | weak positive | `Follow.createdAt` |
| Block created | strong negative | `Block` |
| `isCloseFriend` set | strong positive | `Follow.isCloseFriend` |

### 10d. Hard filters (apply BEFORE scoring — never rank these)
self · already-friends / pending request (`Friendship`) ·
blocked either way (`Block.isBlockedEitherWay`) · `Block.hideFromSuggestions=true` ·
optionally `Follow.isMuted` · SSO `status=INACTIVE` / `deleted_at != null`.

### 10e. Notes for the ML engineer
- **Two populations:** *gamers* (have an esports `PlayerDTO` → rich skill/region/team
  features) and *general users* (SSO only). Model should degrade gracefully — expect
  esports features to be **null for non-gamers**. Add an "is_gamer" indicator.
- **Cold-start:** new users have no graph edges → fall back to node features
  (popularity, same country, same skill band, same org) — the current
  `findMostFollowedUserIds` path.
- **PII (🔒):** `gender`, `dob`, `phone_number`, `email`, `street_address`,
  `ip/MAC/serial`. Hash or bucket (age-band, not raw DOB) before training; never ship
  raw PII into a served model.
- **Availability today:** only ✅ rows are queryable from social-service right now.
  🟡 rows need either (a) widening SSO `/basic` / adding an internal endpoint, or
  (b) mapping the fuller esports `PlayerDTO` (already returned on `develop`). No 🟡
  feature requires *new data collection* — the columns already exist upstream.
- **Recommended first model:** since labels exist (accept/decline/dismiss), a
  pairwise learning-to-rank (e.g. LightGBM/XGBoost ranker) over §10a+§10b features is
  a strong, cheap baseline. Graph-embedding methods (node2vec / GraphSAGE over the
  friendship+follow edges) are viable later given the edge tables in §3–§5.