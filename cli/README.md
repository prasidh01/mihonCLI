# Mihon CLI

A standalone, pure Kotlin/JVM desktop CLI tool that connects Mihon to the [Keiyoushi Extensions Repository](https://keiyoushi.github.io/docs/guides/getting-started). It allows browsing, downloading, and executing Android Keiyoushi extensions directly on desktop Linux, macOS, and Windows without needing an Android emulator or the Android SDK.

---

## Features

- **On-the-Fly Bytecode Transpilation:** Translates Android Dalvik bytecode (`classes.dex` inside `.apk` extensions) into standard JVM bytecode in memory using `dex-translator` and ASM tree transformation.
- **Bytecode Repair Pipeline:** Automatically repairs Kotlin value-class name mangling, synthesizes missing constructors for abstract classes/interfaces, fixes obfuscated companion/singleton field assignments, and handles asset loading.
- **Embedded Android Compatibility Runtime:** Minimal, zero-overhead stubs for `android.content.Context`, `ContextWrapper`, `Application`, `SharedPreferences` (JSON-backed persistence), `androidx.preference.Preferences`, `android.os.Build`, and `eu.kanade.tachiyomi.AppInfo`.
- **Keiyoushi Store Integration:** Decodes the compressed Keiyoushi repository index (`index.pb`) covering 1,440+ extensions and thousands of manga sources.
- **Manga Search & Catalog:** Query single or multiple manga sources concurrently with paginated results and formatted tables.
- **CBZ Downloader:** Concurrently downloads manga chapter images with rate limiting, retries, and natural sort order, packaging them into valid `.cbz` comic book archives complete with `ComicInfo.xml` metadata.
- **Integrated Reader Launcher:** Directly launches local image/comic viewers (`imv`, `zathura`, `feh`, or `xdg-open`) for downloaded chapters.

---

## Building and Running

### Requirements
- JDK 17 or JDK 21+

### Quick Run
An executable script is provided in the repository root:
```bash
./mihon help
```
*(On first execution, it automatically builds the distribution via Gradle).*

### Manual Build
```bash
# Compile and run test suite
./gradlew :cli:test

# Build runnable distribution
./gradlew :cli:installDist

# Run directly
./cli/build/install/cli/bin/cli help
```

---

## Commands & Usage

### 1. Extensions Management
```bash
# Browse available extensions from Keiyoushi
./mihon extension list --remote -q mangadex
./mihon extension list --remote -q asura

# Install an extension from Keiyoushi
./mihon extension install eu.kanade.tachiyomi.extension.all.mangadex
./mihon extension install eu.kanade.tachiyomi.extension.en.asurascans

# List installed extensions and their sources
./mihon sources
# or
./mihon extension list

# Uninstall an extension
./mihon extension uninstall eu.kanade.tachiyomi.extension.all.mangadex
```

### 2. Searching Manga
```bash
# Search across installed sources (English prioritized by default)
./mihon search "Chainsaw Man"
./mihon search "One Piece"

# Search a specific source
./mihon search "Swordmaster" -s "Asura Scans"

# Pagination
./mihon search "Romance" -s "MangaDex" --page 2
```

### 3. Manga Details & Chapters
```bash
# View manga details, synopsis, and chapter listing
./mihon info "/manga/e896c48c-3150-437d-ba57-d8567eb399ae" -s "MangaDex"
./mihon info "/series/star-embracing-swordmaster" -s "Asura Scans"
```

### 4. Downloading Chapters
```bash
# Download a specific chapter to .cbz
./mihon download "/series/star-embracing-swordmaster" -c "144" -s "Asura Scans"

# Download a range of chapters
./mihon download "/series/star-embracing-swordmaster" -c "140-144" -s "Asura Scans"

# Download the latest chapter
./mihon download "/series/star-embracing-swordmaster" -c latest -s "Asura Scans"

# Download all chapters
./mihon download "/manga/e896c48c-3150-437d-ba57-d8567eb399ae" -c all -s "MangaDex"

# Custom destination directory
./mihon download "/series/star-embracing-swordmaster" -c "144" -s "Asura Scans" --dest ~/Manga

# Keep raw image files instead of CBZ
./mihon download "/series/star-embracing-swordmaster" -c "144" -s "Asura Scans" --raw
```

### 5. Reading Chapters
```bash
# Downloads (if not cached) and opens in your system's viewer
./mihon read "/series/star-embracing-swordmaster" -c "144" -s "Asura Scans"

# Specify a custom viewer (e.g., imv, zathura, feh)
./mihon read "/series/star-embracing-swordmaster" -c "144" -s "Asura Scans" -v imv
```

---

## File Storage Locations

- **Extensions:** `~/.local/share/mihon/extensions/`
- **Preferences:** `~/.local/share/mihon/prefs/`
- **Transpiled Bytecode Cache:** `~/.local/share/mihon/cache/`
- **Default Download Destination:** `~/Manga/<Manga Title>/<Chapter Name>.cbz`

---

## Testing

Run the test suite with:
```bash
./gradlew :cli:test
```
Includes:
- `KeiyoushiRepoTest`: Protobuf repository parsing.
- `ExtensionInstantiationTest`: Transpilation and dynamic class loading.
- `SourceExecutionLiveTest`: Live network query verification.
- `CbzPackagerTest`: Natural sorting, archive integrity, and `ComicInfo.xml` validation.
