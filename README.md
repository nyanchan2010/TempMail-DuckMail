# TempMail — DuckMail client (unofficial)

A complete, fast, Material 3 Expressive temporary-email app for Android, using
**only** the DuckMail API at `https://api.duckmail.sbs`. No sign-up, no ads, no
analytics, no tracking.

> Unofficial client. Not affiliated with duckmail.sbs. Mailbox passwords and
> tokens are stored encrypted with Android Keystore (AES/GCM) on this device only.

## Features

- One-tap random address, custom address (username + domain picker), import existing mailbox
- Multi-mailbox vault with labels, unread counts, rename, server delete
- Auto-polling inbox (foreground every 2/3/5/10 s, configurable)
- Background watching via a user opt-in Foreground Service (15 s, 30 s in power-save)
  with a persistent notification and Stop action. A WorkManager watchdog runs every
  15 minutes (Android's minimum) for one refresh + service restart — it is NOT the
  fast polling path and this README does not claim otherwise.
- Strict OTP extraction (keyword-anchored 4–8 digits, years rejected), OTP chip
  tap-to-copy, optional auto-copy, notification with Copy action
- Strict verification-link check (verification keyword + security parameter only),
  never auto-opened
- Message detail: sanitized HTML in a WebView with JavaScript disabled, images
  off by default, plus a plain-text tab
- Search, All / Unread / Codes filters (real `ButtonGroup`), swipe-to-delete,
  pull-to-refresh, offline cache
- Material 3 Expressive + Pixel style: `MaterialExpressiveTheme` with
  `MotionScheme.expressive()`, `ButtonGroup`, `SplitButtonLayout`,
  `FloatingActionButtonMenu`, `LoadingIndicator`, `MediumFlexibleTopAppBar`,
  `LinearWavyProgressIndicator` (only for refresh progress), dynamic color,
  expressive shapes and typography
- Material Icons only, everywhere — including the launcher icon: adaptive icon
  (vector mail glyph) + round icon + monochrome layer for Pixel Themed Icons
  on Android 13+
- English / Arabic setting (RTL mirroring via system locale handling in Compose),
  Light / Dark / System theme, dynamic color toggle

## DuckMail API used (exact)

Base: `https://api.duckmail.sbs`

- `GET /domains` → `hydra:member` / `domains` / `data`, fallback `["duckmail.sbs"]`
- `POST /accounts {address, password}` → 200/201, then `POST /token {address, password}` → token + id
- `GET /messages?page=1` with `Authorization: Bearer <mailbox token>`
- `GET /messages/{id}`, `PATCH /messages/{id} {"seen": true}`, `DELETE /messages/{id}`, `DELETE /accounts/{id}`
- Optional user-entered `dk_` API key in Settings for private domains. Never hardcoded.
- 401/404 on messages → one silent re-login with stored credentials, then one retry.
- The web routes `/api/mail` and `/api/sse` are NOT used (same-origin only, 403 for apps).

## Pinned versions (verified buildable set)

- AGP 9.3.0, Kotlin Compose plugin 2.3.21, Gradle 9.5.0, JDK 17
- compileSdk 37, targetSdk 36, minSdk 26
- Compose BOM 2026.06.00, material3 1.5.0-alpha24
- activity-compose 1.13.0, work-runtime-ktx 2.9.1, okhttp 4.12.0, zxing core 3.5.3

Note on compileSdk: material3 1.5.0-alpha24 requires compileSdk 37. targetSdk
stays 36. Do not "fix" this by dropping to material3 1.4.0 — its expressive APIs
(`ButtonGroup`, `LoadingIndicator`, `MotionScheme.expressive()`) are internal there
and the app will not compile. `gradlew` must stay executable in git (mode 100755);
CI also runs `chmod +x gradlew` before building.

## Build locally

```bash
git clone <this repo>
cd TempMail-DuckMail
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Open in Android Studio (JDK 17, Android SDK with platform 37) and Run.

## Build on GitHub Actions

Push to `main` (or open a PR, or run the workflow manually). The workflow
`.github/workflows/build.yml` runs unit tests, builds the debug APK, and uploads
it as the `TempMail-DuckMail-debug-apk` artifact. No secrets are required for
the debug build.

## Enable background watching

1. Create a mailbox.
2. Settings → turn ON "Background watching".
3. Allow notifications when asked. A persistent "Watching …" notification appears
   with Stop / Open actions. Swiping the app away does not stop it.
4. If you want it to survive aggressive battery optimisation, the system may ask
   you to exempt the app — that prompt appears only once, and declining simply
   falls back to the 15-minute WorkManager refresh.

## Manual acceptance tests

1. Fresh install → New address in 1 tap → address appears, polling starts.
2. Send a real mail to that address → appears in <10 s in foreground; OTP chip +
   notification appear if it contains a code.
3. Swipe the app away with background watching ON → new mail still notifies;
   Stop from the notification ends watching.
4. Reboot with watching ON → watching resumes; with OFF → it does not.
5. Open a message → marked read, OTP Copy works, HTML renders without scripts.
6. Delete mailbox → gone locally and `DELETE /accounts/{id}` was called.
7. Rotate, switch Light/Dark, switch EN/AR → no crash, no lost inbox.
8. Airplane mode → cached inbox visible + "Offline" state, recovers on reconnect.

## Privacy

No ads, no analytics SDKs, no Firebase, no crash reporter uploading message
content. The only network destination for mail data is `api.duckmail.sbs`.
Deleting a mailbox deletes it from the server and from this device.
