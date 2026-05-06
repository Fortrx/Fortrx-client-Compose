# Fortrx — Kotlin Multiplatform client

Signal-inspired desktop + Android client for the Fortrx server
(`https://fortrx-server.duckdns.org`).

## Modules

- `shared/` — KMP code: crypto, network, storage, services. Targets JVM, Android, iOS.
- `desktopApp/` — Compose for Desktop app (3-pane Signal-style layout).
- `androidApp/` — Android app (Compose Material 3).
- `iosApp/` — Xcode project shell (consumes the `shared` framework).

## Run desktop

```
./gradlew :desktopApp:run
```

Package native installers:

```
./gradlew :desktopApp:packageDistributionForCurrentOS
```

## Build Android

```
./gradlew :androidApp:assembleDebug
```

## iOS

Open `iosApp/iosApp.xcodeproj` after running `./gradlew :shared:linkDebugFrameworkIosArm64`.

## Server-side changes

See `SERVER_CHANGES.md`.
