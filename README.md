# NoReel

NoReel is an Android app designed to keep Instagram messages, profiles, and regular posts available while blocking the Reels tab and full-screen Reels player.

## What it does

- Uses an Android AccessibilityService scoped to Instagram.
- Covers the Reels bottom-navigation item while a commitment is active.
- Detects the full-screen Reels player through multiple Instagram accessibility resource-ID patterns, with a screen-size check to avoid confusing small thumbnails or Stories with the viewer.
- Uses the same full-screen-player detection for Reels opened from the Reels tab, feed, profiles, and DMs. When detected, it presses Back to return to the previous Instagram screen.
- Includes a commitment timer in days and hours.
- Restores the timer after reboot and expires it automatically.
- Includes an optional Device Owner hardening mode for supported devices.

## Important limitations

- Instagram does not publish stable accessibility IDs. An Instagram update can change its view hierarchy and require new detection hints.
- Reels opened in the full-screen player are the target. Inline autoplay previews inside the feed or a DM may still be visible before the full-screen player opens.
- The app has not been verified on every Instagram version or phone. A successful APK build does not prove that detection works on a particular device.
- The ordinary Device Admin step adds friction but is not equivalent to Device Owner mode. Device Owner provisioning is optional and requires a fresh/unprovisioned device and ADB.
- Device Owner restrictions are stronger but not impossible to bypass; factory reset, recovery, privileged ADB, manufacturer behavior, and bootloader-level actions may still defeat them.
- The debug APK is for direct testing, not a Play Store release.

## Build

GitHub Actions builds app-debug.apk after pushes to main. Open the Actions tab, select the latest successful Build Android APK run, and download the NoReel-debug-apk artifact.

## Install and setup

1. Download and install the debug APK.
2. Open NoReel and enable the uninstall guard when prompted.
3. Tap Accessibility access and enable NoReel.
4. Return to NoReel and set a commitment in days and hours.
5. Start the commitment.

For stronger protection, read docs/DEVICE_OWNER.md before provisioning. It requires a fresh/unprovisioned device and ADB. Back up your phone first.

## Status

The Android project and automated APK build are in development. The APK must build successfully and then pass real-device testing before it should be treated as dependable.
