# Luma Store

Luma Store is a Kotlin Multiplatform app store project targeting **Android, Windows and Linux**.

The catalog experience is shared with Compose Multiplatform. Android, Windows and Linux use the same KMP models, Luma Store/F-Droid repository loader, Discover screen, Search screen, Sources screen and app-details UI. Android keeps its platform-specific My Apps, developer area, notifications and APK installation flow.

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
├── app/            # Android shell and Android-only features
├── shared/         # Shared models, networking, sources and Compose UI
│   └── src/
│       ├── commonMain/
│       ├── androidMain/
│       └── desktopMain/
└── desktopApp/     # Windows/Linux Compose Multiplatform shell
```

## Shared features

- Discover apps from the platform-specific Luma Store catalog.
- Load F-Droid `index-v1.json` repositories.
- Enable or disable sources.
- Add and remove custom F-Droid-compatible sources in the shared repository layer.
- Search by app name, package ID, summary or category.
- App details with version, source, categories, license, author and anti-features.
- Website, source-code, issue-tracker and download actions.
- Shared Ktor networking and JSON parsing.

## Android-only features

- APK installation and updating from shared app details.
- My Apps / installed-app handling.
- Developer area backed by Supabase.
- System notifications and background notification sync.

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

GitHub Actions checks KMP compilation and native desktop packaging. Windows packages are built on a Windows runner, Linux packages on Ubuntu, and the Android release workflow builds and signs the APK using repository signing secrets.

## Contributing

Bug reports, feature suggestions and pull requests are welcome.

Developed by [FreetimeMaker](https://github.com/FreetimeMaker).
