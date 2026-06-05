# Gradle Commands Reference

## Building

```bash
./gradlew :app:assembleDebug        # Build debug APK
./gradlew :app:assembleRelease      # Build release APK
./gradlew :app:bundleDebug          # Build debug AAB (App Bundle)
./gradlew :app:bundleRelease        # Build release AAB
./gradlew :app:clean                # Delete build outputs
./gradlew :app:clean assembleDebug  # Clean then build
```

## Installing

```bash
./gradlew :app:installDebug         # Build + install on connected device/emulator
./gradlew :app:uninstallDebug       # Uninstall from device
```

## Testing

```bash
./gradlew :app:test                         # Run unit tests
./gradlew :app:testDebugUnitTest            # Run debug unit tests only
./gradlew :app:connectedDebugAndroidTest    # Run instrumented tests on device
./gradlew :app:lint                         # Run lint checks
./gradlew :app:lintDebug                    # Lint debug variant only
```

## Code Generation

```bash
./gradlew :app:kspDebugKotlin              # Run KSP (generates Hilt code)
./gradlew :app:hiltAggregateDepsDebug      # Hilt dependency aggregation
```

## Inspection

```bash
./gradlew tasks                            # List all available tasks
./gradlew :app:tasks                       # List tasks for the app module only
./gradlew :app:dependencies                # Print full dependency tree
./gradlew :app:assembleDebug --info        # Verbose output
./gradlew :app:assembleDebug --stacktrace  # Full stack trace on failure
```

## Useful Flags

Append to any command:

| Flag | Effect |
|---|---|
| `--no-daemon` | Don't use Gradle daemon (good for debugging) |
| `--rerun-tasks` | Force re-run even if outputs are up-to-date |
| `--parallel` | Build modules in parallel |
| `--offline` | Use cached dependencies, no network |
| `--build-cache` | Enable build cache for faster incremental builds |

## Finding Dependencies

### Where to look

| Source | URL | What lives here |
|--------|-----|-----------------|
| **Google Maven** | `maven.google.com` | Android/Jetpack libs (Hilt, Compose, AndroidX) |
| **Maven Central** | `central.sonatype.com` | Everything else (Retrofit, OkHttp, Kotlin libs) |
| **Library's GitHub README** | varies | Exact copy-paste setup instructions |

### Key library docs

| Library | Reference |
|---------|-----------|
| Retrofit | `github.com/square/retrofit` |
| OkHttp | `square.github.io/okhttp` |
| Kotlin Serialization | `github.com/Kotlin/kotlinx.serialization` |
| Hilt | `developer.android.com/training/dependency-injection/hilt-android` |

### How to read a dependency string

When a library's README shows:
```
implementation("com.squareup.retrofit2:retrofit:2.11.0")
```
The three parts map to `libs.versions.toml` like this:
```
group   = "com.squareup.retrofit2"   ← everything before the first :
name    = "retrofit"                 ← between the colons
version = "2.11.0"                   ← after the last :
```

### Kotlin Serialization — two separate versions

It has two parts with independent version numbers:
- **Plugin** version → always matches your Kotlin version (reuse `version.ref = "kotlin"`)
- **Library** (`kotlinx-serialization-json`) version → find latest at `github.com/Kotlin/kotlinx.serialization/releases`

---

## Notes

- Pattern: `./gradlew :<module>:<task><Variant>` — swap `:app` for `:feature:login` etc. in multi-module projects.
- No system Java needed — use Android Studio's bundled JDK: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>`
- If the **Run button is disabled** in Android Studio, run `./gradlew :app:assembleDebug` in the terminal to surface the raw Gradle error, then do **File → Sync Project with Gradle Files** after fixing it.
