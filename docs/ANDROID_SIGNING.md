# Android APK signing

The GitHub Actions workflow signs every CI debug APK with one persistent PKCS12 keystore. Never commit the keystore or its passwords to this repository.

## Required repository Actions secrets

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and create these four secrets:

| Secret name | Value |
|---|---|
| `NOREEL_KEYSTORE_BASE64` | The complete one-line Base64 content of the private `upload-key.p12.base64.txt` file supplied to the repository owner |
| `NOREEL_KEYSTORE_PASSWORD` | The keystore password supplied with the key |
| `NOREEL_KEY_ALIAS` | `noreel-release` |
| `NOREEL_KEY_PASSWORD` | The key password supplied with the key (same as the keystore password for this key) |

Do not paste the keystore or passwords into issues, commits, workflow logs, or chat messages. Keep an offline backup of the PKCS12 file and its passwords. Anyone with the keystore and passwords can publish APKs that Android treats as updates from this signing identity.

After all four secrets are saved, use **Actions → Build Android APK → Run workflow**. The build will fail deliberately if the Base64 keystore secret is missing. The generated APK is in the `NoReel-debug-apk` artifact.

## Important migration note

The previous CI builds used ephemeral GitHub runner debug keys. This persistent key has a different certificate, so it cannot update an installation signed by one of those previous keys. Android will report a signature mismatch. Back up any app data/settings first; uninstall the old app only if necessary, then install the APK signed with this persistent key. Future builds using this same key can update it normally.

The current workflow uses this signing identity for the debug APK. Local builds without the CI signing environment variables continue to use Android's normal local debug key.
