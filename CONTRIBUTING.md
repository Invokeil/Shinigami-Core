# Contributing to Shinigami Core

Thank you for helping build a private, user-controlled Android assistant. This document covers how to contribute: style, commits, pull requests, testing, and where help is needed most.

## Ground rules

- Read [ARCHITECTURE.md](ARCHITECTURE.md) before writing code — especially the security pipeline. The non-negotiable invariant: **the AI never controls Android directly.** All capability goes through the `ToolRegistry` → `ActionPolicyEngine` → `ActionEngine` pipeline. PRs that add shell executors, free-form command surfaces, or bypasses of the policy engine will be declined.
- Be honest in docs and marketing claims: roadmap items are roadmap items.
- No telemetry, no analytics SDKs, no new network dependencies that call home.

## Getting started

```bash
git clone https://github.com/Invokeil/Shinigami-Core.git
cd Shinigami-Core
./gradlew assembleDebug        # verify the project builds
./gradlew testDebugUnitTest    # run the unit tests
```

Requirements: JDK 17+, Android SDK 35. See [BUILDING.md](BUILDING.md) for the full toolchain, IDE setup, and signing details.

## How to contribute

1. **Open or find an issue first** for anything non-trivial, so design can be agreed before code lands.
2. **Fork and branch** from `main` with a short, descriptive branch name (`feat/timer-labels`, `fix/redactor-utf8`).
3. **Keep PRs focused** — one feature or fix per PR, small diffs preferred.
4. **Add or update tests** for behaviour changes (see Testing below).
5. **Update docs** when you change behaviour the docs describe: README, [AUTOMATION.md](AUTOMATION.md), [PERMISSIONS.md](PERMISSIONS.md), [PROVIDERS.md](PROVIDERS.md), [PRIVACY.md](PRIVACY.md), [CHANGELOG.md](CHANGELOG.md).
6. **Open the PR** with a clear description, screenshots for UI changes, and a note on any permission-mode or risk-level implications.

### Security-sensitive changes

Anything touching `core.security`, `core.actions`, `core.ai` (envelope parsing), the notification listener, or secret handling gets extra scrutiny and may require a design note in the PR description. Please do not open public issues for vulnerabilities — use the private reporting channel in [SECURITY.md](SECURITY.md).

## Code style

- **Kotlin official coding conventions** (enforced by the IDE and ktlint conventions where configured). No wildcard imports; prefer explicit imports.
- **KDoc for all public APIs** — classes, functions, and non-obvious properties. The codebase documents *why*, not just *what*.
- Follow the existing structure: `core.*` packages are UI-free where noted (`core.actions` must stay UI-free; UI hooks go through interfaces like `ConfirmationRequester`).
- Error handling uses `AppResult`/`AppError` in `core.util`; do not throw across module boundaries.
- Compose: keep screens thin, state in ViewModels, theme tokens from `core.ui.theme` (no hardcoded colors); respect the reduce-animations setting.
- New user-visible strings go in `strings.xml`; no emojis in code, docs, or UI copy.

## Commit conventions

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(actions): add label summariser for set_alarm
fix(offline): handle 24-hour alarm times in wake-me phrases
docs(readme): clarify Ollama LAN cleartext warning
refactor(provider): extract auth header builder
test(redactor): cover bearer tokens in JSON payloads
chore(deps): bump okhttp to 4.12.0
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`. Scope with the package or area name where it helps. Squash-if-needed is fine; maintainers will handle merge strategy.

## Testing expectations

- **Unit tests** (`app/src/test`) run on the JVM: `./gradlew testDebugUnitTest`. Parsers, the registry validator, the redactor, and the context budgeter are all pure-JVM testable — new logic in those areas needs tests (Turbine is available for Flow testing).
- **Instrumented tests** (`app/src/androidTest`) for Compose UI where practical: `./gradlew connectedDebugAndroidTest`.
- **Lint must pass:** `./gradlew lint`. New warnings introduced by a PR should be fixed, not suppressed, unless justified in the PR description.
- PRs should keep the existing suite green and add coverage for new branches — especially argument validation, policy decisions (mode/permission/risk), and offline parsing patterns.

## Areas needing help

The roadmap (all currently *not* in v1 — see README) is where contributions move the needle most:

- **Accessibility automation** — policy-gated UI automation; heavy security review, great first design discussion.
- **Screen context** — opt-in, privacy-first screen awareness.
- **Shizuku support** — elevated operations behind explicit user opt-in.
- **Routines editor** — chains of tool calls with user-defined triggers.
- **Skills/plugins SDK** — declared tools and permissions, user approval, Action Engine brokering (see [SKILLS.md](SKILLS.md)).
- **Smart home** — Home Assistant integration.
- **File assistant, vision/camera input.**
- **Web search skill API, native function-calling** support for providers that offer it.
- **Advanced model routing, cost dashboard** — smarter profile selection and usage visibility.
- **Docs and translations** — non-native-English reviews of this documentation set and localising the UI are hugely valuable.

If you want a smaller entry point: provider templates, offline parser patterns (with tests), permission-card copy, and the audit screen are all approachable.

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0, same as the project ([LICENSE](LICENSE)).
