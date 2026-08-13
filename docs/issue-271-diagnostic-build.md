# Issue #271 diagnostic build

This branch provides a narrowly scoped diagnostic build for the keyboard-window flicker reported in
[issue #271](https://github.com/palsoftware/pastiera/issues/271).

## Privacy scope

The diagnostic build automatically retains the newest 500 IME surface-state events in app-private
storage. Its export contains:

- Pastiera build and Android device information;
- the keyboard surface configuration relevant to this issue;
- external keyboard type information with device-specific name suffixes removed; and
- IME surface decisions, window callbacks, and show requests.

It does **not** record typed text, keycodes, suggestions, editor contents, clipboard contents, or target
app names. The diagnostic log can be cleared or shared as a text file directly from the main screen.

## Tester workflow

1. Install the diagnostic APK over the matching Pastiera Nightly.
2. Reproduce the status-bar flicker using the physical keyboard.
3. Open Pastiera and tap **Share log** in the **Issue #271 diagnostic build** panel.
4. Review the text file if desired, then attach it to issue #271 or send it to the maintainer.
5. Install the matching regular Nightly APK again after the log has been collected.

The diagnostic APK intentionally uses the same application ID, version code, and signing certificate as
the Nightly it targets. This permits both installation and restoration without clearing app data or
requesting an Android downgrade.

## Building

Provide the timestamp of the Nightly release that the diagnostic APK should replace:

```bash
scripts/build-nightly-diagnostic.sh 0.86 --target-timestamp 20260811.214801
```

The script runs the `nightlyDiagnostic` unit tests, builds a non-debuggable APK, verifies the expected
Nightly signing certificate, and writes a SHA-256 checksum next to the APK. Signing credentials are read
through the same local properties or environment variables as regular Nightly releases.
