# HearYet (Android project)

This directory contains the Android project (13 Gradle modules). The full project
README — what HearYet is, how the sync works, status, and build instructions —
lives at the [repository root](../README.md).

Quick start:

```bash
./gradlew assembleDebug          # build
./gradlew :app:installDebug      # install on a device
./gradlew test                   # JVM + Robolectric tests
./gradlew ktlintCheck            # lint
```
