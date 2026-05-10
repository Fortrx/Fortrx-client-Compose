# Backend Audit - 2026-05-10

This note is derived from the current client codebase and the existing [server_changes.md](../server_changes.md). There is no backend source code in this workspace, so the items below are the concrete server-side fixes implied by current client behavior.

## 1. Failure Chain: Expired Token

Current client behavior:

- The client stores one bearer access token locally.
- REST requests and WebSocket auth both use that same token.
- If the token expires, every protected endpoint can fail at once:
  `/auth/me`
  `/messages/inbox`
  `/keys/{user_id}`
  `/messages/send`
  `/ws/{user_id}`
- The client now retries by logging in again with the saved username/password, but that is only a recovery patch. It is not the right long-term backend contract.

Server fixes:

1. Add a real refresh flow.
   - `POST /auth/refresh`
   - input: refresh token
   - output: new access token, optional rotated refresh token, explicit `expires_at`

2. Separate token classes.
   - short-lived access token for API and WebSocket connect
   - longer-lived refresh token for renewal

3. Return explicit token metadata on login.
   - `access_token`
   - `refresh_token`
   - `token_type`
   - `expires_at`
   - optionally `issued_at`

4. Make WebSocket expiry explicit.
   - if auth expires after connect, close with a clear code/reason
   - do not silently drop or loop ambiguous failures

5. Add revocation semantics.
   - logout current session
   - logout all sessions
   - refresh token rotation and revocation

Why this matters:

- Reusing username/password for silent recovery is weaker than refresh-token rotation.
- It also makes device/session control harder and creates extra login load during token expiry spikes.

## 2. Failure Chain: Invalid Or Expired Key Bundle

Current client behavior:

- A sender fetches one bundle from `GET /keys/{user_id}` and builds a session from it.
- The current project notes already flag the real issue: bundle ownership/versioning is ambiguous and may be returning the "latest bundle across device ids" while messaging still targets a single user inbox.
- After restore/rekey, the sender may encrypt against stale or wrong peer material.
- When that happens, message decrypt can fail, session recovery becomes ambiguous, and users see:
  `fetch key bundle invalid`
  `expired token`
  or generic send/decrypt failures depending on where the request breaks.

Server fixes:

1. Choose one identity model and enforce it end-to-end.
   - Option A: one active bundle per user
   - Option B: one bundle per device with device-targeted delivery fanout

2. Add explicit bundle versioning.
   Every key bundle response should include at least:
   - `user_id`
   - `device_id` if using multi-device bundles
   - `identity_version`
   - `bundle_version`
   - `signed_prekey_id`
   - `one_time_prekey_id` when consumed
   - `kyber_prekey_id` if PQ keys are active
   - `published_at`
   - `expires_at`

3. Persist which bundle version a message was encrypted against.
   For every outbound message enqueue, record:
   - recipient user/device target
   - recipient bundle version
   - recipient identity version
   - consumed one-time-prekey id if any

4. Make one-time prekey consumption atomic.
   The server should do this in one DB transaction:
   - reserve or consume the chosen prekey
   - enqueue the message
   - persist delivery metadata

5. Add explicit peer re-key signalling.
   When a user restores or re-uploads identity keys:
   - bump `identity_version`
   - invalidate old active bundle pointers
   - notify peers with a websocket sync event
   - reject stale-session sends with a typed error, not a generic 400/500

6. Add a sender recovery path.
   Example:
   - sender uses stale session
   - server detects target identity/bundle mismatch
   - server responds with a typed error like `peer_rekey_required`
   - sender fetches the latest bundle and re-establishes session

7. Distinguish bundle errors from auth errors.
   Do not overload token expiry and bundle invalidity into similar HTTP responses.
   Use distinct status/detail values so the client can decide whether to:
   - refresh auth
   - fetch a new bundle
   - discard ratchet session
   - prompt the user about peer reinstallation or restore

## 3. Failure Chain: Duplicate Message Delivery

Current client behavior:

- Inbox sync and WebSocket hints can race.
- The client now deduplicates local inserts by `server_message_id`.
- That fixes the crash locally, but the server should still make delivery semantics cleaner.

Server fixes:

1. Keep `message_id` globally stable and immutable.

2. Make delivery confirmation idempotent.
   `DELETE /messages/{message_id}/confirm` should be safe to call multiple times.

3. Avoid redelivery after confirm.
   Once confirmed, inbox fetch should not keep returning the same message.

4. If websocket only sends a hint, keep it as a hint.
   Recommended:
   - websocket event says `message_available`
   - client fetches inbox
   - inbox returns only unconfirmed messages

5. Add per-recipient delivery records.
   If you later support multi-device or groups, model delivery state separately from the logical message row.

## 4. Search Safety: Username, User Id, Message Search

Current client behavior:

- Username lookup is exact-match and URL-encoded client-side.
- Local message search is local only.
- Client now rejects blank and obviously invalid ids before hitting the server.

Server fixes:

1. Normalize usernames consistently.
   - trim
   - case-fold policy
   - canonical storage and unique index

2. Rate limit search endpoints.
   Especially if you add partial username search.

3. Enforce minimum query length for partial search.
   Recommended: 2 or 3 characters minimum.

4. Return only bounded fields.
   Avoid leaking extra account metadata from search or lookup.

5. Make lookup authorization explicit.
   Decide whether `GET /auth/users/{id}` and `GET /auth/users/by-username/{username}` are public to authenticated users or subject to privacy rules.

6. Add abuse monitoring.
   Track:
   - search rate per IP
   - search rate per account
   - high miss-rate enumeration patterns

## 5. Required Backend Error Contract

Right now the client mostly sees generic HTTP failures and string details. The backend should standardize error shapes.

Recommended JSON shape:

```json
{
  "error_code": "peer_rekey_required",
  "detail": "Recipient identity changed",
  "context": {
    "user_id": 123,
    "identity_version": 8,
    "bundle_version": 31
  }
}
```

Minimum typed errors to add:

- `token_expired`
- `token_invalid`
- `refresh_token_invalid`
- `peer_rekey_required`
- `key_bundle_expired`
- `key_bundle_unavailable`
- `signed_prekey_invalid`
- `one_time_prekey_exhausted`
- `message_already_confirmed`
- `message_not_found`

## 6. Capacity: Where This Stack Will Bottleneck First

Your architecture as described:

- FastAPI
- Uvicorn
- Docker
- PostgreSQL
- MinIO

Likely bottlenecks, in order:

1. WebSocket concurrency and heartbeat overhead
2. PostgreSQL write/read pressure from:
   - inbox fetch
   - delivery confirm
   - presence writes
   - key bundle fetch/update
3. Uvicorn worker count and event-loop saturation
4. CPU spent on auth, encryption-related payload validation, and JSON serialization
5. MinIO, but usually only once attachments become large and frequent

Important distinction:

- Total registered users is not the main limit.
- Concurrent active users and message rate are the real limits.

Without host specs, the best estimate is order-of-magnitude only:

- Small single node, roughly 2 vCPU / 4 GB:
  expect bottlenecks in the low hundreds of concurrently active users.

- Moderate single node, roughly 4 vCPU / 8 GB:
  expect high hundreds to low thousands of concurrently active users if websocket and DB access are efficient.

- Beyond that:
  you usually need Redis-backed fanout/presence, separate Postgres tuning, horizontal app scaling, and likely dedicated websocket strategy.

MinIO is probably not your first bottleneck unless:

- attachments are frequent
- files are large
- you proxy file traffic through the app container

## 7. What To Measure Before Guessing Capacity Further

You need these numbers from the running server:

1. App host:
   - vCPU
   - RAM
   - Uvicorn worker count
   - max open file descriptors

2. PostgreSQL:
   - CPU and RAM
   - connection pool size
   - average query time for:
     - login
     - inbox fetch
     - confirm delivery
     - key bundle fetch
     - presence heartbeat

3. Traffic shape:
   - concurrently connected websocket clients
   - average heartbeats per minute
   - average messages per second
   - average inbox fetches per second
   - average attachment upload/download size

4. Error rates:
   - 401 count
   - websocket reconnect rate
   - key bundle fetch failures
   - decryption failure rate after restore/rekey

## 8. Load Test Plan

Before production claims, run this sequence:

1. 100 concurrent websocket clients, low traffic
2. 250 concurrent websocket clients, low traffic
3. 500 concurrent websocket clients, low traffic
4. Same steps with realistic heartbeat and inbox polling
5. Then add message traffic:
   - 5 msg/s
   - 20 msg/s
   - 50 msg/s
6. Then add restore/rekey cases and stale-session sends

Measure at each step:

- p50/p95/p99 latency
- websocket disconnect rate
- DB CPU
- app CPU
- memory growth
- queue depth
- duplicate delivery count

## 9. Immediate Backend Priority Order

If you want the shortest path to stability, do this first:

1. Add refresh-token flow and explicit token expiry metadata.
2. Fix key-bundle ownership semantics and add bundle/identity version fields.
3. Make stale-session sends return typed `peer_rekey_required`.
4. Make one-time prekey consumption atomic.
5. Make inbox/confirm delivery fully idempotent.
6. Add search rate limiting before partial username search ships.
7. Add load testing and observability before claiming user capacity.

