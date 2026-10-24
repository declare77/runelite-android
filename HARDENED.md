# Hardened fork

Changes from `zeruth/runelite-android`:

1. **In-app Jagex sign-in removed.** It used the official OSRS Android app's OAuth client
   ID. The only way to log in is importing a `credentials.properties` written by desktop
   RuneLite (launched through the Jagex Launcher with `--insecure-write-credentials`).
2. **Firebase removed.** No Crashlytics, no Analytics, no `google-services.json`. Crash
   info stays in logcat on the device.
3. **Self-updater disabled.** The app never checks, downloads or installs updates from the
   upstream releases, and no longer requests `REQUEST_INSTALL_PACKAGES`. Update by
   syncing this fork and rebuilding.
4. **CI is build-only.** No release publishing or signing secrets; each run uploads a
   debug-signed APK as a workflow artifact.

Unchanged on purpose: the Plugin Hub (still served from `zeruth/plugin-hub-dex`) and
token storage (app-private storage, not encrypted).
