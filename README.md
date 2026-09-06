Support this project on [OpenCollective](https://opencollective.com/pastiera)

<details>
<summary>Alternative direct support options</summary>

> **Notice:** The following payments are made directly to individual maintainers and are not administered through OpenCollective. Depending on the terms of Pastiera's future fiscal host, these options may be discontinued and all project contributions may subsequently be processed exclusively through OpenCollective.

### Current maintainer

| | |
|---|---|
| Account holder | Patrick Alexander Zauner |
| IBAN | DE25660702130058075300 |
| BIC | DEUTDESMP12 |

For everyone who sees an IBAN and quietly gives up:  
[Support via PayPal](https://www.paypal.me/zaunerpa)

### Original developer

[![Support the original developer on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C31OHWF2)

</details>

# Pastiera

Input method for physical keyboards android devices (e.g. Unihertz Titan 2), designed to make typing faster through shortcuts, gestures, and customization.

## Quick overview
- Compact status bar with LED indicators for Shift/SYM/Ctrl/Alt, variants/suggestions bar, and swipe-pad gestures to move the cursor.
- Multiple layouts (QWERTY/AZERTY/QWERTZ, Greek, Cyrillic, Arabic, translit, etc.) fully configurable; JSON import/export directly from the app. A web frontend for editing layouts is available at https://pastierakeyedit.vercel.app/
- SYM pages usable via touch or physical keys (emoji + symbols), reorderable/disableable, with an integrated layout editor.
- Clipboard support with multiple entries and pinnable items.
- Support for dictionary based suggestions/Autocorrections + swipe gestures to accept a suggestion (requires Shizuku)
- Full backup/restore (settings, layouts, variations, dictionaries), UI translated into multiple languages, and built-in GitHub update checks.

## Typing and modifiers
- Long press on a key can input Alt+key or Shift+Key (uppercase) timing configurable.
- Shift/Ctrl/Alt in one-shot or lock mode (double tap), option to clear Alt on space.
- Current behavior note: `Ctrl` used as a physically held shortcut modifier (e.g. hold `Ctrl` + `A`) intentionally follows the app shortcut path and is not the same flow as Nav Mode (`Ctrl` double-tap latch outside text fields). Nav Mode remains a separate implementation/state.
- Multi-tap support for keys with layout-defined variants (e.g. Cyrillic)
- Standard shortcuts: Ctrl+C/X/V, Ctrl+A, Ctrl+Backspace, Ctrl+E/D/S/F or I/J/K/L for arrows, Ctrl+W/R for selection, Ctrl+T for Tab, Ctrl+Y/H for Page Up/Down, Ctrl+Q for Esc (all customizable in the Customize Nav screen).

## QOL features
- **Nav Mode**: double tap Ctrl outside text fields to use ESDF or IJKL as arrows, and many more useful mappings (everything is customizable in Customize Nav Mode settings)
- **Variations bar as swipe pad**: drag to move the cursor, with adjustable threshold.
- **Launcher shortcuts**: in the launcher, press a letter to open/assign an app.
- **Power shortcuts**: press SYM (5s timeout) then a letter to use the same shortcuts anywhere, even outside the launcher.
- Change language with a tap on language code in the status bar, longpress to enter pastiera settings

## Keyboard layouts
- Included layouts: qwerty, azerty, qwertz, greek, arabic, russian/armenian phonetic translit, plus dedicated Alt maps for Titan 2.
- Layout switching: select from the enabled layouts list (configurable).
- Multi-tap support and mapping for complex characters.
- JSON import/export directly from the app, with visual preview and list management (enable/disable, delete).
- Layout maps are stored in `files/keyboard_layouts` and can also be edited manually. A web frontend for editing layouts is available at https://pastierakeyedit.vercel.app/
- Device/firmware behavior snapshots for physical keyboards are archived under [docs/device-archives](docs/device-archives/).

## Symbols, emoji, and variations
- Two touch-based SYM pages (emoji + symbols): reorderable/enableable, auto-close after input, customizable keycaps.
- In-app SYM editor with emoji grid and Unicode picker.
- Variations bar above the keyboard: shows accents/variants of the last typed letter or static sets (utility/email) when needed.
- Dedicated variations editor to replace/add variants via JSON or Unicode picker; optional static bar.

## Suggestions and autocorrection

- Experimental support for dictionary based autocorrection/suggestions
- User dictionary with search and edit abilities.
- Per-language auto substituion editor, quick search, and a global “Pastiera Recipes” set shared across all languages.
- Change language/keymap with a tap on the language code button or ctrl+space



## Comfort and extra input
- Double space → period + space + uppercase; 
- Swipe left on the keyboard to delete a word (Titan 2).
- Optional Alt+Ctrl shortcut to start Google Voice Typing; microphone always available on the variants bar.
- Compact status bar to minimize vertical space. With on-screen keyboard disabled from the IME selector, it uses even less space (aka Pastierina mode)
- Translated UI (it/en/de/es/fr/pl/ru/hy) and onboarding tutorial.

## Backup, updates, and data
- UI-based backup/restore in ZIP format: includes preferences, custom layouts, variations, SYM/Ctrl maps, and user dictionaries.
- Restore merges saved variations with defaults to avoid losing newly added keys.
- Built-in GitHub update check when opening settings (with option to ignore a release).
- Customizable files in `files/`: `variations.json`, `ctrl_key_mappings.json`, `sym_key_mappings*.json`, `keyboard_layouts/*.json`, user dictionaries.
- Android autobackup function 

## Installation
1. Build the APK or install an existing build.
2. Android Settings → System → Languages & input → Virtual keyboard → Manage keyboards.
3. Enable “Pastiera” and select it from the input selector when typing.

## Requirements
- Android 10 (API 29) or higher.
- Device with a physical keyboard (profiled on Unihertz Titan 2, adaptable via JSON).

## Development / Tests
- Run core + routing + service modifier regression tests:
  - `./gradlew :app:testStableDebugUnitTest --tests it.palsoftware.pastiera.core.ModifierStateControllerTest --tests it.palsoftware.pastiera.inputmethod.InputEventRouterModifierE2ETest --tests it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodServiceDeviceBehaviorTest`
- Run release/update flavor coverage tests:
  - `./gradlew :app:testStableDebugUnitTest --tests it.palsoftware.pastiera.FlavorBuildConfigTest --tests it.palsoftware.pastiera.update.UpdateCheckerFlavorLogicTest`
  - `./gradlew :app:testNightlyDebugUnitTest --tests it.palsoftware.pastiera.FlavorBuildConfigTest --tests it.palsoftware.pastiera.update.UpdateCheckerFlavorLogicTest`
- Run the stable F-Droid-path tests:
  - `./gradlew :app:testStableDebugUnitTest -PPASTIERA_FDROID_BUILD=true`
- Service-level (device-near) modifier behavior regressions:
  - `./gradlew :app:testStableDebugUnitTest --tests it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodServiceDeviceBehaviorTest`
- Router-level input pipeline modifier/SYM tests:
  - `./gradlew :app:testStableDebugUnitTest --tests it.palsoftware.pastiera.inputmethod.InputEventRouterModifierE2ETest`
- Core modifier state machine tests:
  - `./gradlew :app:testStableDebugUnitTest --tests it.palsoftware.pastiera.core.ModifierStateControllerTest`
- Build nightly debug APK with dynamic nightly version code:
  - `./scripts/build-nightly-debug.sh 0.86`
  - `./scripts/build-nightly-debug.sh 0.86 --install`
  - `./scripts/build-nightly-debug.sh 0.86 --install --device <adb-serial>`

## Continuous Integration
- Pushes to `main` and pull requests run `.github/workflows/ci.yml`.
- The CI job runs, in order:
  - `:app:testStableDebugUnitTest`
  - `:app:testStableDebugUnitTest -PPASTIERA_FDROID_BUILD=true`
  - `:app:testNightlyDebugUnitTest`

## Manual release CI
- The repository includes a manually triggered GitHub Actions workflow at `.github/workflows/release.yml`.
- Required GitHub Actions secrets:
  - `PASTIERA_KEYSTORE_B64`
  - `PASTIERA_KEYSTORE_PASSWORD`
  - `PASTIERA_KEY_ALIAS`
  - `PASTIERA_KEY_PASSWORD`
- The workflow:
  - runs stable flavor unit tests
  - optionally runs the stable F-Droid-path unit tests
  - builds a signed stable release APK
  - optionally builds an unsigned stable APK for the official F-Droid path
  - verifies APK signing
  - uploads the signed APK and its SHA256 checksum as artifacts
  - uploads the unsigned F-Droid APK and its SHA256 checksum as artifacts
  - optionally creates a GitHub Release
- Release versioning is injected via Gradle properties:
  - `-PPASTIERA_VERSION_CODE=...`
  - `-PPASTIERA_VERSION_NAME=...`
- Local release builds can use the same mechanism:
  - `./gradlew :app:assembleStableRelease -PPASTIERA_VERSION_CODE=86 -PPASTIERA_VERSION_NAME=0.86`
  - `./scripts/build-release.sh 0.86 86`
  - `./scripts/build-fdroid.sh 0.86 86`

### Local signing config (`release/keystore.properties`)
- Local wrapper scripts read signing config from `release/keystore.properties` (gitignored).
- You can provide file paths, embedded Base64, or both (path + B64 for parity with CI secrets storage).
- CI-style variable names are supported directly:
  - Stable:
    - `PASTIERA_KEYSTORE_FILE`, `PASTIERA_KEYSTORE_PASSWORD`, `PASTIERA_KEY_ALIAS`, `PASTIERA_KEY_PASSWORD`, optional `PASTIERA_KEYSTORE_B64`
  - Nightly:
    - `NIGHTLY_KEYSTORE_FILE`, `PASTIERA_NIGHTLY_KEYSTORE_PASSWORD`, `PASTIERA_NIGHTLY_KEY_ALIAS`, `PASTIERA_NIGHTLY_KEY_PASSWORD`, optional `PASTIERA_NIGHTLY_KEYSTORE_B64`
- Legacy Gradle property names are still supported (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`, `nightlyStoreFile`, `nightlyStorePassword`, `nightlyKeyAlias`, `nightlyKeyPassword`).
- When `PASTIERA_KEYSTORE_B64` or `PASTIERA_NIGHTLY_KEYSTORE_B64` is present, local scripts materialize the corresponding `.jks` only if the target file is missing.

## Manual nightly CI
- The repository includes a manually triggered nightly workflow at `.github/workflows/debug.yml`.
- Required GitHub Actions secrets:
  - `PASTIERA_NIGHTLY_KEYSTORE_B64`
  - `PASTIERA_NIGHTLY_KEYSTORE_PASSWORD`
  - `PASTIERA_NIGHTLY_KEY_ALIAS`
  - `PASTIERA_NIGHTLY_KEY_PASSWORD`
- The workflow:
  - runs nightly flavor debug-unit tests
  - builds a nightly release APK signed with the shared nightly key
  - computes a SHA256 checksum
  - uploads the APK and checksum as workflow artifacts
  - automatically turns a base version like `0.86` into a unique nightly version like `0.86-nightly.20260306.195412`
  - optionally publishes a GitHub pre-release under the `nightly/v*` tag scheme using that full nightly version
- The nightly flavor uses a separate application ID so it installs alongside the stable release.
- The nightly flavor is signed with a shared nightly key so local and CI nightly builds remain upgrade-compatible.
- Nightly version names follow the pattern `BASE-nightly.YYYYMMDD.HHMMSS`, for example `0.86-nightly.20260307.005731`.
- GitHub Nightly builds and private F-Droid Nightly builds share the same application ID and signing key, but F-Droid Nightly builds disable GitHub update checks so updates come from the F-Droid repo.
- Nightly pre-release disclaimer text is maintained in `.github/release-templates/debug-prerelease.md`.
- The same versioning can be generated locally:
  - `./scripts/nightly-version.sh 0.86`
  - `./gradlew :app:assembleNightlyRelease -PPASTIERA_VERSION_NAME=0.86 -PPASTIERA_NIGHTLY_VERSION_SUFFIX=-nightly.$(./scripts/nightly-version.sh 0.86 | awk -F= '/^timestamp=/{print $2}')`
- Local wrappers are available:
  - `./scripts/build-nightly.sh 0.86`
  - `./scripts/build-nightly.sh 0.86 --publish`
  - `./scripts/build-nightly-debug.sh 0.86`
  - `./scripts/build-nightly-debug.sh 0.86 --install`
  - `./scripts/build-nightly-debug.sh 0.86 --install --device <adb-serial>`
  - `./scripts/publish-private-fdroid-nightly.sh 0.86`
  - `./scripts/publish-private-fdroid-nightly.sh 0.86 ../palsoftware-web/apps/docs/public https://pastiera.eu/fdroid/nightly/repo`
  - `./scripts/publish-private-fdroid-nightly.sh 0.86 --timestamp 20260307.005731`
  - `./scripts/publish-private-fdroid-nightly.sh 0.86 ../palsoftware-web/apps/docs/public https://pastiera.eu/fdroid/nightly/repo --no-push-pages`
  - `./scripts/build-release.sh 0.86 86`
  - `./scripts/build-release.sh 0.86 86 --publish`

## Private F-Droid Nightly Repo
- Docs landing page:
  - `https://pastiera.eu/`
- Local Pages target:
  - `../palsoftware-web/apps/docs/public/fdroid/nightly/repo`
- Public repo URL:
  - `https://pastiera.eu/fdroid/nightly/repo`
- GitHub Nightly releases:
  - `https://github.com/palsoftware/pastiera/releases?q=nightly%2F`
- Local publish flow:
  - install `fdroidserver`
  - make sure nightly signing is configured
  - run `./scripts/publish-private-fdroid-nightly.sh 0.86`
  - pass `--timestamp YYYYMMDD.HHMMSS` when mirroring a GitHub Nightly pre-release so the F-Droid build uses the same version name and version code
  - optional: add `--no-push-pages` if you explicitly do not want the generated Pages repo changes committed and pushed
- The script:
  - builds the signed nightly APK
  - initializes or reuses a local F-Droid repo under `.fdroid/nightly`
  - stores each APK under a versioned filename so older Nightly builds can remain in the repo
  - updates the repo metadata with `fdroid update`
  - syncs the generated `repo/` contents into the Pages public directory
  - by default commits and pushes only `apps/docs/public/fdroid/nightly/repo` in `palsoftware-web`, which triggers the GitHub Pages deployment

## Signing Attestations
These attestations document the public signing certificates used for Nightly and official Release builds.
They are intended to strengthen the project's chain of trust: the markdown files are the browser-friendly reference version rendered directly on GitHub, and the signed PDFs are the archival verification artifacts.
The `_signed.pdf` variants do not turn the APK signing certificates themselves into identity certificates. They are private attestations: the signer states that the published public key is the one they currently trust for the respective build channel.
Where a qualified electronic signature is present, that attestation can be validated against the EU DSS validator and interpreted in the context of the eIDAS trust-services framework.

| Channel | Source | Signed PDF | Purpose |
| --- | --- | --- | --- |
| Nightly | [docs/nightly-signing-certificate-attestation.md](docs/nightly-signing-certificate-attestation.md) | [docs/nightly-signing-certificate-attestation_signed.pdf](docs/nightly-signing-certificate-attestation_signed.pdf) | Documents the shared Nightly signing certificate used by local and CI Nightly builds. |
| Release | [docs/release-signing-certificate-attestation.md](docs/release-signing-certificate-attestation.md) | [docs/release-signing-certificate-attestation_signed.pdf](docs/release-signing-certificate-attestation_signed.pdf) | Documents the official Release signing certificate used for stable public releases. |

External verification references:

| Reference | Link | Purpose |
| --- | --- | --- |
| EU DSS Validator Demo | [ec.europa.eu/digital-building-blocks/DSS/webapp-demo/validation](https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/validation) | Validate the signed PDF attestations with the European Commission DSS demo service. |
| eIDAS overview | [digital-strategy.ec.europa.eu/en/policies/eidas-regulation](https://digital-strategy.ec.europa.eu/en/policies/eidas-regulation) | Background on the EU trust-services framework under which qualified electronic signatures are defined. |
