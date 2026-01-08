# Lumina - Jellyfin Android TV Client With Premiumize Streaming

[![License: GPL v2](https://img.shields.io/badge/License-GPL_v2-blue?labelColor=555555&style=for-the-badge)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![Latest Release](https://img.shields.io/github/v/release/Sam42a/DUNE?label=Latest%20Release&labelColor=555555&style=for-the-badge)](https://github.com/Spark-NV/DUNE/releases/latest)
[![Support Sam42a](https://img.shields.io/badge/Support_Me-Buy_a_Coffee-orange?labelColor=555555&style=for-the-badge)](https://coff.ee/sam42)

## About

**Lumina** is a modified version of DUNE by Sam42a which is itself a fork of the official [Jellyfin](https://jellyfin.org/) Android TV client with baked in support for premiumize streaming utlizing jellyfin as the library manager. Currently built off DUNE 0.1.1 source(highly doubt i update to new dune source unless something drastically changes)

> **Note**: This is an unofficial fork not affiliated with the Jellyfin project or Sam42a's fork. The official Jellyfin Android TV client can be found at [jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv). Sam42a's client can be found at [jellyfin/jellyfin-androidtv](https://github.com/Sam42a/DUNE)

> **Note**: This fork is not for use by most people this only makes sense if you have a jellyfin server setup to create libraries from stuff like trakt or simkl or other means. I personally have made a few modifications to other plugins for jellyfin which creates extremely small snub files to be used to create jellyfin libraries, which allows jellyfin to handle all metadata fetching and this app handles playback. This has 0 things in common with the other lumina app, the other lumina app i will prbably abandon as managing metadata myself is pretty hard i found, its so much easier to let jellyfin handle it all.



### LUMINA added features
- Baked in torrentio and AIOStreams api handling
- Removed internal player usage due to missing jellyfin server metadata
- Added custom intents to send to a [custom justplayer fork](https://github.com/Spark-NV/Jellyfin.Player/tree/main/compiled) to allow for server playback tracking
- UI slightly slimmed down, options removed to be as simple as possible for the less tech litterate

## DUNE Key Features

### Visual & Interface
**Modernized UI Framework**
- Redesigned home screen with improved content hierarchy
- Enhanced login experience with visual feedback
- Default avatars for users without profile images
- Intuitive search interface with voice input
- Multiple theme options including OLED-optimized dark mode, based on [Jellyfin Android TV OLED](https://github.com/LitCastVlog/jellyfin-androidtv-OLED)

### Customization
**Library Presentation**
- Toggle between classic and modern layouts
- Dynamic backdrops from media artwork
- Customizable homescreen rows (genres, favorites, collections)

### Media Experience
**Enhanced Playback**
- Advanced subtitle controls
- Customizable background effects
- Optimized performance

### Technical Improvements
- Reduced memory usage
- Faster app startup
- Side by side installation alongside official client
- Built in automatic updates

## Building from Source

### Requirements
- Android Studio Giraffe (2022.3.1+)
- Android SDK (API 35)
- OpenJDK 21+

### Build Instructions
```bash
# Clone repository
git clone https://github.com/Sam42a/DUNE.git
cd DUNE-main

# Build standard version
./gradlew assembleStandardRelease

# Or build enhanced version (coexists with official app)
./gradlew assembleEnhancedRelease
```

### Updating version
```
app/src/main/java/org/jellyfin/androidtv/ui/preference/category/about.kt
app/build.gradle.kts
```

**Note:** This version uses package ID `Dune.lumina.tv` which allows it to be installed alongside the original Jellyfin app and the original DUNE app.

## Third-Party Libraries

This project uses the following third-party libraries:

- **Jellyfin SDK** - [GPL-2.0](https://github.com/jellyfin/sdk-kotlin)
- **AndroidX Libraries** - [Apache-2.0](https://developer.android.com/jetpack/androidx)
- **Kotlin Coroutines** - [Apache-2.0](https://github.com/Kotlin/kotlinx.coroutines)
- **Koin** - [Apache-2.0](https://insert-koin.io/)
- **Coil** - [Apache-2.0](https://coil-kt.github.io/coil/)
- **Markwon** - [Apache-2.0](https://noties.io/Markwon/)
- **Timber** - [Apache-2.0](https://github.com/JakeWharton/timber)
- **ACRA** - [Apache-2.0](https://github.com/ACRA/acra)
- **Kotest** - [Apache-2.0](https://kotest.io/)
- **MockK** - [Apache-2.0](https://mockk.io/)

## Acknowledgments

This project is based on the work of the Jellyfin Contributors. Special thanks to all the developers and community members who have contributed to the Jellyfin Android TV project.

## License

This project is licensed under the **GNU General Public License v2.0 (GPL-2.0)**. See the [LICENSE](LICENSE) file for details.
