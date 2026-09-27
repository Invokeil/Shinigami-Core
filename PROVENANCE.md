# PROVENANCE — Shinigami Core release signing & integrity

Every official **Shinigami Core** APK is signed with the single release key
below. The key never changes between versions — an update will always
install over a previous install, and you can always verify that an APK you
downloaded is the genuine article.

## Signing certificate (v0.1.0 → present)

```
Alias name: shinigami
Owner:      CN=Shinigami Core, OU=Invokeil, O=Invokeil, L=Dhaka, C=BD
Valid:      Sun Sep 27 2026 → Thu Feb 12 2054
```

| Fingerprint | Value |
|-------------|-------|
| **SHA-256** | `93:A9:59:4D:1B:D0:FD:2C:8F:8E:69:F3:E6:AC:18:58:64:AB:3B:5F:63:4A:6E:E4:16:1E:A9:E6:0F:49:01:5B` |
| **SHA-1**   | `BF:9B:64:AD:93:3C:14:40:43:FA:BD:12:0D:C3:03:ED:85:87:84:E4` |

## Verify an APK you downloaded

Using `keytool` (ships with any JDK):

```bash
keytool -printcert -jarfile Shinigami-Core-v0.2.0.apk
```

The `SHA-256:` line of the signing certificate must match the fingerprint
above. The app's in-app updater additionally verifies each downloaded APK
against the SHA-256 digest GitHub computes for the release asset
(`digest: sha256:…`) before offering installation.

## Play Protect — what to expect & what to do

Shinigami Core is a self-published open-source app, so Google Play Protect
may show an **"unknown developer"** scan prompt on first install. This is
expected for every sideloaded app that is new to Play Protect's reputation
system, regardless of what the app does.

- The warning is not a detection. Choosing **Install anyway / Send for scan**
  once lets Play Protect learn the app; the prompts fade as the install base
  grows.
- Shinigami deliberately avoids every dynamic-code and executable pattern
  that triggers hard blocks: no `DexClassLoader`, no downloaded binaries,
  no bundled shell tools. Post-install downloads are data files only
  (models/voices), verified by SHA-256 against the published
  `components.json`.
- If your device ever hard-blocks the install, you can report a false
  positive to Google at:
  https://support.google.com/googleplay/android-developer/contact/protectappeals
- Accessibility / notification-listener access on Android 13+ may require
  **App info → ⋮ → Allow restricted settings** — this is standard Android
  behavior for sideloaded apps, not a Shinigami restriction.

## Brand asset provenance (v0.2.1 → present)

The shipped artwork comes from the official Shinigami Core identity files,
published by the project owner:

| Source asset | SHA-256 |
| --- | --- |
| `shinigami-logo-exact.svg` (pixel-preserving container, embedded 1254×1254 PNG extracted for rendering) | `e2f0bfe15c1de5874b6d69673287f1626d57eab896047d602dd03b5d29370d47` |
| `shinigami-image.png` (official dark-tile rendering, used for the Play Store 512 icon) | `e00e3f5a84f14b1bcd1a28e3b97f63e21ee3f68d7c653fb67254f30df68ffd54` |
| `shinigami_assistant_animation.json` as shipped in `res/raw` (authentic `loading-shinigami-colors` animation; embedded rasters re-encoded to **lossless** WebP — pixels bit-exact, motion data untouched) | `1ccada0d55629de8493dba8221567d5649488983c1fab49f805c5e791c94ed0d` |

## Why you should care

If anyone distributes a "Shinigami Core" APK whose certificate does not
match this fingerprint, it did not come from this project.
