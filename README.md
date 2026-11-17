# NoReel

NoReel is an Android app that keeps Instagram messages, profiles, and regular posts available while blocking the dedicated Reels tab and full-screen Reels viewer.

## Blocking approach

- Uses an Android AccessibilityService scoped to Instagram.
- Covers the Reels bottom-navigation item when the exact `clips_tab` marker or a matching bottom-navigation description is exposed.
- Detects the dedicated full-screen viewer using Instagram's `clips_viewer_view_pager` and `clips_viewer_fragment_container` markers. A Reel opened from a DM is closed with Back, returning to the previous chat when Instagram preserves that navigation stack.
- Does not match generic `reel_*` IDs because those can belong to Stories.
- Displays a local block counter and the last detected surface in NoReel to help diagnose Instagram UI changes.
- Includes a commitment timer and optional Device Owner hardening.

## Important limitations

- Instagram's internal view IDs are undocumented and can change. The new detector uses specific known markers, but it still needs testing against the exact Instagram build and device.
- Inline autoplay previews embedded inside a DM conversation are not the same as the full-screen Reels viewer and may remain visible.
- If Instagram stops exposing the known viewer marker, the app may not detect that version. The in-app Blocking Check shows the last detection result.
- The app has not been verified on every phone. A successful APK build does not prove that Reels detection works on a particular device.
- Device Admin is only a small uninstall barrier. Stronger restrictions require optional Device Owner provisioning on a fresh/unprovisioned device and ADB.

## Install and setup

1. Install the APK.
2. Enable the uninstall guard when prompted.
3. Enable NoReel in Android Accessibility settings.
4. Return to NoReel and start a commitment.
5. Confirm the Blocking Check says Accessibility is ON.
6. Open Instagram and test the Reels tab and a Reel opened from a DM.

## Build

GitHub Actions builds `app-debug.apk` on pushes to `main`. Open the Actions tab, select the latest successful **Build Android APK** run, and download the `NoReel-debug-apk` artifact.
