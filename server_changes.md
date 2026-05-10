# Server Changes

Current working list for backend changes needed to support the client roadmap.

## Search And Contacts

- Add a partial username search endpoint instead of only exact username lookup.
- Support ranked search results with prefix matches first, then substring matches.
- Return lightweight contact cards: `id`, `username`, `is_online`, `avatar_url` later.
- Add rate limiting and minimum query length for search endpoints.

## Attachments

- Add first-class attachment metadata to message payloads on the server.
- Support larger encrypted attachments than inline message payloads.
- Add upload/download flow for encrypted blobs with message-linked metadata.
- WhatsApp-scale attachment sizes need a dedicated encrypted upload/download path; inline message payloads should stay closer to Signal-class limits.
- Validate MIME type, file size, and retention policies.
- Consider thumbnail metadata for images so clients can render previews cheaply.

## Messages

- Add server-side message deletion semantics.
- Decide between:
  `delete_for_me`
  `delete_for_everyone`
- Add edit history or explicit “edited” flag if message editing is planned.
- Add pagination cursors for conversation history.

## Presence

- Keep Redis presence as the source of truth for live availability.
- Add `last_seen_at` in presence responses for offline contacts.
- Broadcast richer presence events:
  `online`
  `offline`
  `typing_started`
  `typing_stopped`

## Notifications

- Add device-targeted push notification support for Android.
- Only notify when a message is not already acknowledged by an active foreground session.
- Add notification collapse keys by conversation.

## Groups

- Add group domain models:
  `groups`
  `group_members`
  `group_roles`
  `group_events`
- Support create group, rename group, add/remove member, leave group, promote admin.
- Add membership versioning so clients can rotate group keys when membership changes.
- Prefer sender-key style encryption for group fanout instead of N individual sends.
- Publish Redis events per group for message delivery and membership changes.

## Audio And Video Calls

- Add WebSocket signaling events for:
  `call_invite`
  `call_ringing`
  `call_accept`
  `call_decline`
  `call_end`
  `webrtc_offer`
  `webrtc_answer`
  `ice_candidate`
- Add ephemeral call-session storage in Redis.
- Add TURN/STUN configuration delivery from the backend.
- Add device-aware ringing so the correct logged-in devices ring together.
- For group calls, use an SFU architecture instead of pure mesh WebRTC.

## Recovery And Account

- Add a recovery endpoint flow that is documented end-to-end for clients.
- Add backup phrase status endpoint so the client can confirm server-side registration state.
- Add logout-all and delete-account audit visibility for clients.
- Fix key-bundle ownership semantics:
  `GET /keys/{user_id}` currently returns the latest bundle across device ids, while messages are still delivered to a single user inbox.
- Decide one model and enforce it end-to-end:
  single active bundle per user
  or device-targeted bundles plus device-targeted message fanout.
- Add explicit bundle version / identity version fields so clients can detect peer re-key events instead of silently continuing with stale ratchet sessions.
- Add a sender-facing re-key recovery path when a recipient can no longer decrypt an existing session.

## Nice To Have

- Avatar upload and profile endpoint.
- Read receipts beyond simple delivery confirmation.
- Per-conversation mute settings synced across devices.
- Block list and privacy settings endpoints.
