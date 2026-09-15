# Luma Store

An Android app store for discovering, installing and updating apps from the Luma Store catalog and optional F-Droid repositories.

[![Android build](https://github.com/FreetimeMaker/Luma-Store-New/actions/workflows/build_and_co.yml/badge.svg)](https://github.com/FreetimeMaker/Luma-Store-New/actions/workflows/build_and_co.yml)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84)

## Features

- **Discover apps:** Browse the catalog and filter by category.
- **Search:** Find apps by name, description, package name or category.
- **App details:** View available metadata, versions and sources, with links supplied by the catalog.
- **Install and update:** Download APKs and open Android's installer, or launch apps already installed.
- **Manage sources:** Enable or disable built-in sources and add, edit or remove custom F-Droid repository URLs.
- **Cached catalog:** Browse previously loaded app information when available.
- **Developer area:** Sign in with GitHub or GitLab to view your submissions, review feedback and notifications.

## Get the app

Luma Store is under active development and requires **Android 7.0 (API 24) or newer**.

- **Published versions:** Check [GitHub Releases](https://github.com/FreetimeMaker/Luma-Store-New/releases) for release APKs.
- **Development builds:** Open a successful [build workflow run](https://github.com/FreetimeMaker/Luma-Store-New/actions/workflows/build_and_co.yml) and download its APK artifact. GitHub may require you to sign in to download artifacts. Extract the archive to access the signed APK.

If no release is listed, use a development build. Development builds may contain unfinished features.

### Installation

1. Download and open the Luma Store APK on your Android device.
2. If prompted, allow your browser or file manager to install apps from this source.
3. Open Luma Store and browse the catalog.
4. When installing an app through Luma Store, grant Luma Store permission to install apps if Android requests it, then confirm the installation.

## App sources

The Luma Store catalog is enabled by default. Additional repositories can be enabled in the **Sources** tab.

| Built-in source | Default state |
| --- | --- |
| Luma Store | Enabled |
| Freetime F-Droid | Disabled |
| F-Droid | Disabled |
| IzzyOnDroid | Disabled |

Custom repositories currently use the `index-v1.json` format. Enter a repository URL or a direct URL to its `index-v1.json` file. Compatibility depends on the repository's index format and available metadata.

App availability and metadata depend on the enabled sources. Google Play is currently a placeholder and does not supply apps.

## Developer area

The **Developer** tab supports GitHub and GitLab sign-in through Supabase. After signing in, you can:

- View submissions associated with your account and their review status.
- Read review messages and comments.
- View notifications and mark them as read.

This area displays existing submissions; creating a new submission inside the Android app is not currently implemented.

## Permissions

| Permission | Purpose |
| --- | --- |
| Internet access | Load catalogs, images and APKs, and connect to developer services. |
| Install packages | Request installation through Android's package installer. |
| Query installed packages | Compare installed versions with catalog entries and offer install, update or open actions. |
| Notifications | Show developer account notifications when permission is granted. |

## Build from source

The app uses Kotlin, Jetpack Compose and Material 3, with Coil for images and Supabase for developer authentication and data.

1. Clone this repository.
2. Open it in Android Studio and install the Android SDK required by [the app configuration](app/build.gradle.kts).
3. Let Gradle sync, then run the app on a device or emulator.

You can also build a debug APK with the included Gradle wrapper:

```sh
./gradlew :app:assembleDebug
```

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

The GitHub workflow builds release APKs, signs them and verifies their signatures. Its signing step uses repository secrets and a separate keystore repository. Local release builds need your own signing configuration.

## Contributing and support

Bug reports, feature suggestions and pull requests are welcome.

- [Report a bug or suggest a feature](https://github.com/FreetimeMaker/Luma-Store-New/issues)
- [Browse the source code](https://github.com/FreetimeMaker/Luma-Store-New)
- [Contact the maintainer](mailto:FreetimeMaker@proton.me)

For bug reports, include your Android version, the Luma Store version or build commit, the affected source or app, and steps to reproduce the issue.

---

Developed by [FreetimeMaker](https://github.com/FreetimeMaker).
