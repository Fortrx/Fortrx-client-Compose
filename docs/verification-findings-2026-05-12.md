# Verification Findings - 2026-05-12

## Client

### Finding 1: Android compile path was unstable around `Clock.System` usage
- Command: `./gradlew.bat :composeApp:compileDebugKotlinAndroid`
- Impact: Android unit tests were blocked because compile failed before `:composeApp:testDebugUnitTest` could run.
- Status: Re-verified after the networking and security edits below.

### Finding 2: Shared HTTP client logged the full wire surface
- Path: `composeApp/src/commonMain/kotlin/com/network/Api.kt`
- Problem: Ktor `Logging` with `LogLevel.ALL` could expose bearer tokens, login form bodies, key-bundle uploads, inbox payloads, and message metadata on both desktop and Android.
- Fix: Removed the verbose logging plugin and replaced it with debug-only request/status tracing that does not print headers, bodies, tokens, or decrypted payloads.

### Finding 3: Desktop and Android did not enforce an explicit shared URL policy
- Paths:
  - `composeApp/src/commonMain/kotlin/com/network/Api.kt`
  - `composeApp/src/commonMain/kotlin/com/network/WsClient.kt`
- Problem: Base URL normalization and WebSocket scheme derivation were duplicated and could drift.
- Fix: Added shared URL normalization plus shared HTTP-to-WS / HTTPS-to-WSS derivation, with cleartext allowed only for debug local development targets.

### Finding 4: Android network security policy was implicit
- Path: `androidApp/src/main/AndroidManifest.xml`
- Problem: Cleartext rules were not explicitly constrained.
- Fix:
  - Main manifest now sets `android:usesCleartextTraffic="false"`.
  - Main app now references `@xml/network_security_config`.
  - Debug and dev variants allow cleartext only for `localhost`, `127.0.0.1`, and `10.0.2.2`.

### Finding 5: Live client integration tests were part of the default test surface
- Path: `composeApp/src/commonTest/kotlin/com/network/RealServerIntegrationTest.kt`
- Problem: Default test runs could hit a live backend and previously printed user/message details.
- Fix: Live tests now require `FORTRX_RUN_LIVE_SERVER_TESTS=true` and can target a custom backend with `FORTRX_TEST_SERVER_URL`.

### Finding 6: Device-link completion request was broader than the server contract
- Path: `composeApp/src/commonMain/kotlin/com/fortrx/services/DeviceService.kt`
- Problem: The client-side linking flow could send a broad identity bundle instead of the minimal fields the server expects.
- Fix: Narrowed the request/response types to the explicit server contract and routed the calls through the shared API path.

### Finding 6b: Device-link start response exposed the pairing token in a URL query string
- Paths:
  - `app/services/device_service.py`
  - `app/tests/test_server.py`
- Problem: `pairing_uri` embedded `pairing_token=...`, which could leak through screenshots, link handlers, reverse-proxy logs, or browser history.
- Fix: `pairing_uri` now points only to the link-device route. The token stays in the dedicated `pairing_token` response field instead of being duplicated into a URL.

### Finding 7: Shared services emitted sensitive workflow details to stdout
- Paths:
  - `composeApp/src/commonMain/kotlin/com/fortrx/FortrxClient.kt`
  - `composeApp/src/commonMain/kotlin/com/fortrx/services/OnboardingService.kt`
  - `composeApp/src/commonMain/kotlin/com/fortrx/services/MessagingService.kt`
  - `composeApp/src/commonMain/kotlin/com/fortrx/services/SyncEngine.kt`
  - `composeApp/src/commonMain/kotlin/com/fortrx/storage/Db.kt`
  - `composeApp/src/commonMain/kotlin/com/fortrx/storage/Keystore.kt`
  - `composeApp/src/commonMain/kotlin/com/fortrx/storage/StorageCrypto.kt`
- Problem: Normal runs could leak usernames, user IDs, key lifecycle details, or stack traces into logs.
- Fix: Replaced those prints with debug-gated traces and reduced the messages to non-sensitive summaries.

### Finding 8: Android unit tests were blocked by a Robolectric SDK mismatch
- Path: `composeApp/src/androidUnitTest/kotlin/com/network/AndroidRealServerIntegrationTest.kt`
- Problem: `:composeApp:testDebugUnitTest` initially failed during `AndroidRealServerIntegrationTest` runner initialization because the default Robolectric SDK pick did not match the project SDK setup cleanly.
- Fix: Pinned that test subclass to `@Config(sdk = [35])`, which keeps the Android smoke subclass on a supported runner target while still exercising the shared code path.

### Added deterministic coverage
- `composeApp/src/commonTest/kotlin/com/network/ApiUrlPolicyTest.kt`
- `composeApp/src/commonTest/kotlin/com/fortrx/services/DeviceServiceContractTest.kt`

## Server

### Finding 1: There was no explicit one-shot workflow to drain the backend safely
- Problem: Emptying the backend required piecemeal SQL/object-store/Redis work and did not cover restic snapshot destruction.
- Fix:
  - Added `ops/host/drain-stack.sh`
  - Added wrapper `ops/drain.sh`
  - Documented dry-run, destructive confirmation, blast radius, and post-drain verification in `README.md`

### Finding 2: The server test suite used a fixed SQLite filename that was flaky on Windows
- Path: `app/tests/conftest.py`
- Problem: `test_fortress.db` was reused between runs, and Windows file locking caused `PermissionError` during test setup, which broke 37 tests at once.
- Fix: Switched the session fixture to a unique temporary SQLite file per run and cleaned it up after the engine is disposed.

### Backend drain scope implemented
- PostgreSQL account-bearing tables are truncated with identity reset.
- Message objects under the `messages/` prefix are deleted from the configured S3/MinIO bucket.
- Redis DB 0 and DB 1 are flushed after the report captures presence, device-last-seen, event-stream, and rate-limit counts.
- Restic snapshots tagged for the Fortrx backend are enumerated and deleted, then pruned.

## Verification Notes
- `./gradlew.bat :composeApp:compileKotlinJvm`
  - Passed.
- `./gradlew.bat :composeApp:jvmTest`
  - Passed.
- `./gradlew.bat :composeApp:compileDebugKotlinAndroid`
  - Failed first on `MobileAppRoot.kt` with `Unresolved reference 'System'` when the deprecated `kotlinx.datetime.Clock` alias no longer exposed `Clock.System`.
  - Passed after switching the formatting helper to `Instant.fromEpochMilliseconds(System.currentTimeMillis())`.
- `./gradlew.bat :composeApp:testDebugUnitTest`
  - Failed first during `AndroidRealServerIntegrationTest` runner initialization.
  - Passed after pinning the Robolectric SDK with `@Config(sdk = [35])`.
- `venv\\Scripts\\python.exe -m pytest app\\tests -q`
  - Failed first with `PermissionError: [WinError 32]` on `test_fortress.db`.
  - Passed after moving the fixture to a unique temporary SQLite database per run.
  - Final result: `45 passed, 19 warnings`.
- `bash -n ops/host/drain-stack.sh`
  - Passed syntax check.
- `bash -n ops/drain.sh`
  - Passed syntax check.
- `bash ops/drain.sh --dry-run`
  - Not run in this workspace because `C:\\Users\\himan\\Documents\\GitHub\\FORTRX\\Fortrx-Server\\.env.runtime` is missing.
