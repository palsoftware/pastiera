# Pastiera Enhanced

Pastiera Enhanced is a development/testing fork of [Pastiera](https://github.com/palsoftware/pastiera), an Android input method for physical keyboard devices such as the Unihertz Titan 2.

This fork is kept close to the latest upstream Pastiera base while testing extra features faster before they are split out or merged upstream. The current Enhanced feature set has also been submitted back to the main project as [PR #259](https://github.com/palsoftware/pastiera/pull/259).

## Download

- Latest Pastiera Enhanced builds are posted on the [Releases page](https://github.com/astroboii47/pastiera/releases).
- Install the APK, then enable it from Android Settings -> System -> Languages & input -> Virtual keyboard -> Manage keyboards.
- In-app update checks in this fork point at the Pastiera Enhanced releases while the fork is active.

## What's Included

Pastiera Enhanced 4.x is a rewrite of the Enhanced fork on top of the newer Pastiera base, with the fork-specific features reapplied and cleaned up for testing and upstream review.

### Text, Predictions, and Shortcuts

- Emoji and symbol shortcodes while typing, including inline suggestions and shortcut completion.
- Snippets for quick text shortcuts such as emails, usernames, links, templates, and common phrases.
- Searchable snippet management page.
- Improved predictive text with local next-word learning.
- Bundled common phrase fallback data so predictions are useful before personal learning builds up.
- Unified Mode, which can show predictions in the existing candidate/variation bar instead of stacking a second row.
- Better prediction insertion, including replacing the current word where appropriate and advancing suggestions after accepting a prediction.
- Long-press removal for unwanted prediction suggestions.
- Klipy API key setting under Smart Features, without shipping a default/shared key.

### Emoji, Media, and SYM Pages

- GIF, sticker, and local media support from the emoji/media panel.
- Klipy-powered GIF/sticker search with trending and search results.
- Animated media previews in the picker.
- Tap-to-preview before sending media.
- Rich GIF/sticker sending when the target app supports it.
- Link/file fallback handling for apps that do not accept direct rich content.
- Media tab inside the emoji picker, separate from whether the standalone GIF SYM page is enabled.
- Updated emoji data and search entries, including newer emoji support.
- Optional custom emoji font rendering in the emoji picker and candidate/variation UI.
- Improved emoji, GIF, symbols, clipboard, and SYM popup layout.
- Close buttons and better sizing/padding for emoji and media pages.
- Improved SYM/status bar page cycling and long-press selector behavior.

### Themes and Appearance

- Per-app keyboard themes for hardware and software keyboard modes.
- Searchable per-app theme manager with overridden apps pinned first.
- Direct theme picker for each app override.
- Theme clone, rename, and delete actions.
- Key tap color now drives accent-style highlights across more of the keyboard UI.
- User-adjustable key tap/accent color.
- User-adjustable modifier indicator strip thickness.
- Translucency/frosted glass controls for keyboard bars and popups.
- Better handling for popup translucency so snippet/shortcode pages remain readable.
- Candidate, variation, status, and emoji/media UI colors follow the selected keyboard theme more consistently.

### Physical Keyboard and Titan 2 Behavior

- Fixed and overhauled Shift/Ctrl/Alt one-shot, held, and latch behavior.
- More reliable double-tap latch handling for hardware modifiers.
- Optional Keymapper modifier double-tap guard for setups where Keymapper maps Shift/Ctrl/Alt combos.
- Configurable Shift/Alt/Ctrl + Backspace delete behavior, including normal, word, and line delete modes.
- Configurable SYM/F12 + Backspace delete behavior where supported by the device/keymap path.
- Better handling around Tab/F12/SYM integrations for Titan 2-style hardware mappings.
- Improved modifier LED/status behavior so held keys are less likely to remain visually stuck after shortcuts.
- Tuned for Unihertz Titan 2 usage, while keeping Pastiera's configurable layout system.

## Core Pastiera Features

- Compact status bar with LED indicators for Shift/SYM/Ctrl/Alt.
- Variations/suggestions bar with swipe-pad cursor movement.
- Multiple layouts including QWERTY, AZERTY, QWERTZ, Greek, Cyrillic, Arabic, translit, and Titan 2-specific Alt maps.
- JSON import/export for layouts and editable SYM/Ctrl mappings.
- Clipboard support with multiple entries and pinned items.
- Dictionary-based suggestions/autocorrection, substitutions, user dictionary, and Pastiera Recipes.
- Nav Mode and customizable physical-key shortcuts.
- Backup/restore for settings, layouts, variations, SYM/Ctrl maps, and dictionaries.

## Klipy API Setup

GIF, sticker, and online media search uses Klipy. This fork does not ship a default Klipy API key, so users need to add their own key if they want online media search.

1. Get a Klipy API key from Klipy.
2. Open Pastiera Enhanced settings.
3. Go to Smart Features.
4. Open Klipy API key.
5. Paste your key and save.

Local media picking does not require a Klipy API key.

## Custom Emoji Fonts

Pastiera Enhanced can render the emoji picker and candidate/variation emoji with an optional custom emoji font. Add a compatible emoji TTF from settings and make sure you have the right to use any font you import.

## Requirements

- Android 10 (API 29) or higher.
- A physical keyboard device. The fork is mainly tested on Unihertz Titan 2, but the layout system is configurable.

## Support

Donate to the current Pastiera maintainer:

Account holder | Patrick Alexander Zauner |
|---|---|
IBAN | DE25660702130058075300
BIC | DEUTDESMP12

Or, for everyone who sees an IBAN and quietly gives up:\
[via PayPal](https://www.paypal.me/zaunerpa)\
`<OpenCollective Profile coming soon>`

Donate to the original developer:\
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C31OHWF2)

## Credits

- Pastiera Enhanced is based on [Pastiera](https://github.com/palsoftware/pastiera).
- Original Pastiera work and ongoing upstream maintenance belong to the Pastiera project and its contributors.
- Media search is powered by Klipy when the user provides their own API key.
- Emoji metadata/search data is derived from the emoji assets bundled in this repository and updated by the included emoji asset script.

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
