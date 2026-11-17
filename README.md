# NoReel

NoReel is an Android app designed to close Instagram's full-screen Reels viewer while keeping ordinary feed posts, profiles, and direct messages available.

## Current approach

- Uses Android AccessibilityService scoped to Instagram.
- Detects Instagram's full-screen Reels pager by its accessibility resource ID and closes the viewer with Back.
- The same full-screen viewer is used when a Reel is opened from the Reels tab, a profile, the feed, or a DM. This must still be checked against the current Instagram version on a real phone.
- Supports a commitment timer in days and hours.
- Saves the timer and restores it after reboot. It expires automatically.
- Hides the timer controls while a commitment is active.
- Optional Device Owner mode can restrict uninstalling, app-management settings, safe boot, factory reset, debugging, and manual clock changes on supported Android versions.

## Important limitations

- Instagram can change internal accessibility IDs at any time. Detection may stop working after an Instagram update.
- NoReel has not yet been tested on the target phone. A successful APK build does not prove that Reel detection works on every Instagram screen.
- Some DM Reel previews may play inline before the full-screen viewer opens. AccessibilityService cannot reliably remove every preview from the DM itself.
- A normal app install cannot prevent the user from uninstalling it, clearing its data, or changing system time. Stronger restrictions require optional Device Owner provisioning.
- Device Owner mode is not impossible to bypass. Factory reset, recovery, privileged ADB, manufacturer behavior, and bootloader-level actions may still defeat it.
- The debug APK is for direct testing, not a Play Store release.

## Build

GitHub Actions builds app-debug.apk on pushes to main. Open the Actions tab, select the latest successful Build Android APK run, and download the NoReel-debug-apk artifact.

## Install and setup

1. Download and install the debug APK.
2. Open NoReel.
3. Tap Open Accessibility Settings and enable NoReel.
4. Return to NoReel and set a commitment in days and hours.
5. Start the commitment.

For stronger protection, read docs/DEVICE_OWNER.md before provisioning. It requires a fresh/unprovisioned device and ADB. Back up your phone first.

## Status

The Android project and automated APK build are in development. The APK must build successfully and then pass real-device testing before it should be treated as dependable.
