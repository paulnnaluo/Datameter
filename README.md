# Datameter

Datameter is a local-only Android app for answering two questions:

- Where did my mobile data go?
- Did my network deduct the right amount?

V0.1 uses Android usage statistics on-device through `NetworkStatsManager`, stores audit state locally, and does not require an account or backend.

## Build

This repository contains the Android project source. To build it, install Android Studio or the Android SDK command-line tools, then run:

```bash
gradle :app:assembleDebug
```

The current workspace does not include the Android SDK or a Gradle executable, so local APK verification requires installing those tools first.
