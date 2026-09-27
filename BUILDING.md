# Building Shinigami Core

This guide covers building the app from source: toolchain, day-to-day Gradle commands, project configuration, and how release signing works — both locally and in CI.

## Prerequisites

| Requirement | Version |
| --- | --- |
| JDK | 17 or newer (Temurin/Adoptium recommended) |
| Android SDK | API 35 (compile), with Platform 35 and Build-Tools installed |
| Gradle | 8.10.2 (via the included wrapper — no local install needed) |
| Android Studio | Ladybug (2024.2) or newer recommended |
| AGP / Kotlin | 8.7.3 / 2.0.x (pinned in the version catalog) |

The app targets `minSdk 29` (Android 10), `targetSdk 34`, `compileSdk 35`.

## Clone and open

```bash
git clone https://github.com/Invokeil/Shinigami-Core.git
cd Shinigami-Core
```

**Android Studio:** File → Open… → select the project root. Let Studio sync the Gradle project; the version catalog resolves all dependencies. If prompted, accept the SDK license and install the missing API 35 platform.

**Command line only:** ensure `ANDROID_HOME` (or `local.properties` with `sdk.dir=`) points at your SDK, then use the commands below.

## Common commands

```bash
./gradlew assembleDebug          # debug APK  -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # release APK (see signing below)
./gradlew testDebugUnitTest      # JVM unit tests (parsers, registry, redactor, budgeter)
./gradlew connectedDebugAndroidTest  # instrumented tests (device/emulator required)
./gradlew lint                   # Android lint
./gradlew clean                  # clean build outputs
./gradlew installDebug           # install the debug build on a connected device
```

Debug builds use a `.debug` application-id suffix, so they install alongside a release build without conflict.

## Project configuration overview

- **Version catalog:** all dependency coordinates and plugin versions live in `gradle/libs.versions.toml` (AGP, Kotlin, KSP, Hilt, Compose BOM, Room, DataStore, OkHttp, kotlinx.serialization, coroutines, WorkManager, Biometric, test libraries). To bump a dependency, edit the catalog — modules reference aliases like `libs.androidx.room.runtime`, so there are no hardcoded versions in module files.
- **Module layout:** a single `app` module (`:app`). Package structure is documented in [ARCHITECTURE.md](ARCHITECTURE.md).
- **Gradle Kotlin DSL** everywhere (`build.gradle.kts`, `settings.gradle.kts`).
- **R8** is enabled for release builds (`isMinifyEnabled`, resource shrinking) with rules in `app/proguard-rules.pro`.

## Release signing

Release builds are signed in one of two ways. Without either, `assembleRelease` falls back to the debug key (useful for local smoke tests, but never distribute that output).

### Option A — local signing with `keystore.properties`

Create a file named `keystore.properties` in the repository root (**do not commit it** — keep it in `.gitignore`):

```properties
storeFile=/absolute/path/to/shinigami-release.keystore
storePassword=your-keystore-password
keyAlias=shinigami
keyPassword=your-key-password
```

If that file exists, the release build type uses it automatically. Generate a keystore once with:

```bash
keytool -genkeypair -v \
  -keystore shinigami-release.keystore \
  -alias shinigami \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Shinigami Core, O=Invokeil"
```

Store the keystore file and its passwords somewhere safe — a lost keystore means you cannot update your published app.

### Option B — CI signing with GitHub Actions secrets

The build script detects four environment variables; when all four are present, a keystore is materialised from them and used for signing:

| Secret name | Contents |
| --- | --- |
| `SHINIGAMI_KEYSTORE_BASE64` | The keystore file, base64-encoded (`base64 -w0 shinigami-release.keystore`) |
| `SHINIGAMI_KEYSTORE_PASSWORD` | The keystore (store) password |
| `SHINIGAMI_KEY_ALIAS` | The key alias inside the keystore (e.g. `shinigami`) |
| `SHINIGAMI_KEY_PASSWORD` | The key password |

Add these under **Repository → Settings → Secrets and variables → Actions → New repository secret**. They are used only by the release workflow and never printed to logs.

## CI workflows

- **`.github/workflows/build.yml`** — on every push to `main` and every pull request: assembles the debug build, runs unit tests, runs lint, and uploads the debug APK as a workflow artifact. The badge in the README reflects this workflow. It runs without any signing secrets.
- **`.github/workflows/release.yml`** — triggered by pushing a tag matching `v*` (e.g. `v0.1.0`): builds the **signed release APK** using the four secrets above, then creates/updates a GitHub Release and attaches the APK. Cutting a release is therefore:

```bash
git tag v0.1.1
git push origin v0.1.1
```

If the signing secrets are absent, the release workflow's signing step fails loudly rather than publishing a debug-signed APK.

## Troubleshooting builds

| Problem | Fix |
| --- | --- |
| `SDK location not found` | Create `local.properties` with `sdk.dir=/path/to/android-sdk`, or set `ANDROID_HOME` |
| `Unsupported class file major version` | Use JDK 17+ (`java -version`), and match Android Studio's Gradle JDK setting |
| KSP/Hilt version mismatch | Versions are pinned in `libs.versions.toml`; keep Kotlin and KSP aligned (`2.0.x-1.0.x`) |
| Lint aborts the build | Run `./gradlew lint` locally, fix or (with justification) baseline the issues |
| Offline builds | Pass `--offline` after a successful first sync to build without network |
