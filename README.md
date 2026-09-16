# Luma Store

Luma Store is a Kotlin Multiplatform app store project targeting **Android, Windows and Linux**.

The existing Android client remains available with its app discovery, F-Droid sources, install/update flow and developer area. Shared platform-independent code lives in the `shared` KMP module, while the desktop client uses Compose Multiplatform for Windows and Linux.

## Targets

| Platform | Module | Output |
| --- | --- | --- |
| Android | `app` | APK |
| Windows | `desktopApp` | MSI / EXE |
| Linux | `desktopApp` | DEB / RPM |

The shared module detects the active platform and selects the matching Luma Store API catalog (`android`, `windows` or `linux`).

## Project structure

```text
Luma-Store-KMP/
├── app/            # Android application
├── shared/         # Kotlin Multiplatform shared code
│   └── src/
│       ├── commonMain/
│       ├── androidMain/
│       └── desktopMain/
└── desktopApp/     # Compose Multiplatform desktop application
```

## Features

- Discover apps from the Luma Store catalog.
- Optional F-Droid-compatible repositories.
- Search and category filtering.
- App details and metadata.
- Android APK installation and updating.
- Developer area backed by Supabase.
- Shared KMP platform/API configuration for Android, Windows and Linux.
- Native desktop packaging for Windows and Linux.

## Build from source

Requirements:

- JDK 17
- Android SDK for Android builds
- A supported Windows or Linux host for native desktop packaging

### Android

```sh
./gradlew :app:assembleDebug
```

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

### Run desktop app

```sh
./gradlew :desktopApp:run
```

### Windows packages

Run on Windows:

```powershell
.\gradlew.bat :desktopApp:packageMsi :desktopApp:packageExe
```

The packages are written below `desktopApp/build/compose/binaries/main/`.

### Linux packages

Run on Linux:

```sh
./gradlew :desktopApp:packageDeb :desktopApp:packageRpm
```

The packages are written below `desktopApp/build/compose/binaries/main/`.

## CI

GitHub Actions contains separate checks for Kotlin Multiplatform compilation and native desktop packaging. Windows packages are built on a Windows runner and Linux packages on an Ubuntu runner.

The existing Android release workflow continues to build and sign the APK using repository signing secrets.

## App sources

The Android client supports the Luma Store catalog and optional F-Droid-compatible sources. Custom repositories currently use the `index-v1.json` format.

## Contributing

Bug reports, feature suggestions and pull requests are welcome.

Developed by [FreetimeMaker](https://github.com/FreetimeMaker).
