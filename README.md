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
<img width="1174" height="426" alt="image" src="https://github.com/user-attachments/assets/84be1a3b-0ac0-4050-b050-37269f7fc7c2" />
