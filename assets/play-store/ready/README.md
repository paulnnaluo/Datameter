# Datameter Play Store Upload Pack

Privacy policy: https://datameter.app/privacy

## Graphics

- App icon: `icon/app-icon-512.png`
- Feature graphic: `graphics/feature-graphic.jpg`
- Phone screenshots:
  - `screenshots/phone/phone-01.jpg`
  - `screenshots/phone/phone-02.jpg`
  - `screenshots/phone/phone-03.jpg`
  - `screenshots/phone/phone-04.jpg`

## Listing

- App name: `listing/app-name.txt`
- Short description: `listing/short-description.txt`
- Full description: `listing/full-description.txt`
- Privacy policy content: `listing/privacy-policy.md`
- Privacy policy URL: `listing/privacy-policy-url.txt`
- Release notes: `listing/release-notes.txt`
- Permissions justification: `listing/permissions-justification.txt`

## Release Artifact

Google Play needs a signed release Android App Bundle.

Expected local output after building:

`app/build/outputs/bundle/release/app-release.aab`

Build locally with:

`./gradlew :app:bundleRelease`

If Gradle cannot delete Kotlin cache files, stop daemons and retry:

`./gradlew --stop`

`rm -rf app/build/kotlin/compileReleaseKotlin app/build/kotlin/compileDebugKotlin`

`./gradlew :app:bundleRelease`
