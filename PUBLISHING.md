# Publishing

The project publishes two Android library artifacts:

| Module | Maven artifact |
| --- | --- |
| `:notificationhelper` | `io.github.huann305:notificationhelper:1.0.0` |
| `:notificationhelper-fullscreen` | `io.github.huann305:notificationhelper-fullscreen:1.0.0` |

The full-screen artifact depends on the base artifact. Keep them versioned together.

## Configure Coordinates

Default values are in `gradle.properties`:

```properties
POM_GROUP_ID=io.github.huann305
POM_VERSION=1.0.0
POM_URL=https://github.com/huann305/NotificationHelper
POM_SCM_URL=https://github.com/huann305/NotificationHelper
```

Override them at publish time if needed:

```bash
./gradlew publishToMavenLocal -PPOM_VERSION=1.0.1
```

On Windows:

```powershell
.\gradlew.bat publishToMavenLocal -PPOM_VERSION=1.0.1
```

## Publish To Maven Local

```bash
./gradlew clean publishToMavenLocal
```

On Windows:

```powershell
.\gradlew.bat clean publishToMavenLocal
```

Then consume from another project:

```kotlin
repositories {
    mavenLocal()
    google()
    mavenCentral()
}

dependencies {
    implementation("io.github.huann305:notificationhelper:1.0.0")
    implementation("io.github.huann305:notificationhelper-fullscreen:1.0.0")
}
```

## Publish To A Local Build Repository

Each library module also has a `localBuild` Maven repository under its build directory:

```powershell
.\gradlew.bat :notificationhelper:publishReleasePublicationToLocalBuildRepository
.\gradlew.bat :notificationhelper-fullscreen:publishReleasePublicationToLocalBuildRepository
```

Outputs:

```text
notificationhelper/build/repo
notificationhelper-fullscreen/build/repo
```

## Release Checklist

1. Update `POM_VERSION` in `gradle.properties`.
2. Run `.\gradlew.bat clean assembleRelease publishToMavenLocal`.
3. Verify both artifacts exist in Maven Local:
   - `~/.m2/repository/io/github/huann305/notificationhelper`
   - `~/.m2/repository/io/github/huann305/notificationhelper-fullscreen`
4. Test a separate sample app with only `notificationhelper`.
5. Test another sample app with `notificationhelper-fullscreen` and verify `USE_FULL_SCREEN_INTENT` appears only when the full-screen artifact is imported.

## Permission Boundary

The base artifact declares:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
```

The optional full-screen artifact adds:

```xml
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

Do not move full-screen APIs into the base artifact, otherwise apps that do not need full-screen notifications may still inherit the sensitive permission.
