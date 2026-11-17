# Optional Device Owner hardening

Device Owner is an Android enterprise-management role. It is **not** granted by installing an APK or turning on Accessibility. Provisioning normally requires a new or factory-reset device with no accounts already added, plus ADB from another computer.

> Back up your phone first. Provisioning as Device Owner can restrict Settings and app management. Do not try this on your everyday phone unless you understand the reset and recovery steps.

## Provisioning outline

1. Back up the phone.
2. Factory-reset it and complete only the minimum setup needed to enable Developer options and USB debugging. Do not add a Google account.
3. Install the NoReel APK with ADB.
4. Set NoReel as Device Owner using the Android test-device provisioning command for its admin receiver:
   `adb shell dpm set-device-owner com.kingxkingt.noreel/.NoReelAdminReceiver`
5. Reboot, open NoReel, and check that Device Owner mode is shown.
6. Enable NoReel's Accessibility service and start a commitment.

If Android reports that the device is already provisioned or has accounts, stop. Do not try random reset/provisioning commands.

## What this app asks Device Owner to restrict

While a commitment is active, it asks Android to block app-management settings, uninstalling apps, safe boot, factory reset, debugging features, and manual date/time changes (where supported). It also marks NoReel as uninstall-blocked and enables automatic time on supported Android versions.

At expiry, NoReel clears the restrictions it added and restores the previous automatic-time setting.

## Limits

- Device Owner is stronger than a normal app, but it is not impossible to bypass. Recovery/reset methods, privileged access, manufacturer-specific behavior, and bootloader-level operations may still defeat it.
- This does not guarantee that Instagram exposes the same Reels pager ID on every version.
- Device Owner provisioning is intentionally optional. The ordinary APK cannot silently grant itself this role.
- Removing Device Owner status is an administrative operation and may require ADB or a reset. Keep a backup and understand recovery before provisioning.
