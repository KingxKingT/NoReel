# NoReel

Android app that helps block Instagram Reels while leaving ordinary posts and messaging available.

## Important limitations

- Blocking uses Android AccessibilityService and Instagram UI text/content descriptions. Instagram updates can change its interface and may require rule updates.
- This is not a guaranteed way to remove every Reel preview from DMs.
- Stronger uninstall protection requires provisioning the app as a Device Owner on a freshly reset/unprovisioned device. This is an advanced Android setup and is not enabled automatically.
- No Android app can make itself impossible to uninstall or bypass a factory reset.
- The first GitHub Actions build is a debug APK for testing, not a Play Store release.

## Build

GitHub Actions builds `app-debug.apk` on pushes to `main`. Download it from the Actions run's Artifacts section.

## Install and setup

1. Install the debug APK on your Android phone.
2. Open NoReel and enable its Accessibility service in Android Settings.
3. Set a commitment duration and start protection.
4. For Device Owner hardening, see `docs/DEVICE_OWNER.md`. This requires a fresh/unprovisioned phone and ADB; do not attempt it on a phone containing data you have not backed up.

## Status

This project is an initial implementation. It must be tested on the target phone and current Instagram version before relying on it.
