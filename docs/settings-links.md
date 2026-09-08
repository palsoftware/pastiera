# Settings links

Newly copied and shared links use `https://pastiera.eu/settings/<id>`.
The app also accepts `https://pkb.rocks/settings/<id>` and the existing
`pastiera://setting/<id>` links. Preserve both aliases and the stable setting
IDs when renaming the app to Plektra. Opening a link navigates and highlights;
it does not change the setting.

## Website association

Each HTTPS domain must serve its own
`https://<domain>/.well-known/assetlinks.json`, without redirects, using
`application/json`. Include a Digital Asset Links statement with relation
`delegate_permission/common.handle_all_urls` for each supported application
ID and its actual APK signing certificate SHA-256 fingerprints. Stable and
Nightly are separate packages; account for signing-key rotation. Add Plektra's
actual package IDs and certificates when those builds are available, while
retaining Pastiera's associations.

The Android manifest registers only the `/settings/` path prefix. Domain
association and a browser fallback page require separate website deployment;
the Android change alone does not provide either.

As of 2026-09-07, pastiera.eu publishes the association for official Stable and
Nightly signing certificates. Its browser fallback attempts the corresponding
`pastiera://` link once and keeps a clickable link visible; GitHub Pages serves
it through the custom 404 page. The website is maintained in
`palsoftware-web/palsoftware-web.github.io`. pkb.rocks association remains pending.
Local debug signing certificates are not included in the website association.

Verify on a device after deployment with:

```sh
adb shell pm verify-app-links --re-verify it.palsoftware.pastiera.nightly
adb shell pm get-app-links it.palsoftware.pastiera.nightly
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://pkb.rocks/settings/main.about'
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://pastiera.eu/settings/main.about'
```

Do not use an explicit activity or package for the end-to-end routing check:
that bypasses normal Android link resolution. Multiple verified apps claiming
the same host and path do not guarantee an Android chooser. A possible future
in-app selection between installed variants is noted in the manifest; it is
not implemented.

Reference: [Android App Links association](https://developer.android.com/training/app-links/configure-assetlinks).
