# Mihon CLI

A standalone, pure Kotlin/JVM desktop CLI and interactive TUI that brings the full Mihon Android manga experience to desktop Linux, macOS, and Windows. It connects directly to the [Keiyoushi Extensions Repository](https://keiyoushi.github.io/docs/guides/getting-started), allowing you to browse, search, download, and read manga, manhwa, and webtoons without an emulator or the Android SDK.

---

## 📱 Android Experience in the Terminal

Mihon CLI features an interactive terminal workflow designed to mirror the Mihon Android app:

```
================================================================================
  Mihon - Android Manga Experience on Desktop
  Select an option to begin
================================================================================
  [1] Browse Manga / Manhwa (Choose from installed extensions)
  [2] Extension Store (Browse & install extensions from Keiyoushi)
  [3] Quick Search (Search across all installed sources)
  [4] Manage Extensions (View installed extensions / uninstall)
  [h] CLI Help (Show command-line usage manual)
  [q] Quit (Exit Mihon)
```

### 1. Interactive Manga & Manhwa Browsing (`extension list` or `mihon extension list`)
Typing `extension list` presents an interactive navigation flow matching the Android app:
1. **Choose an Extension:** Displays all installed extensions with source and language details (e.g. Asura Scans, Flame Comics, MangaDex).
2. **Choose Source Options:**
   - **Popular Manga / Manhwa:** Browse trending and top titles with pagination.
   - **Latest Updates:** View recently updated series.
   - **Search Manga / Manhwa:** Search titles within that specific extension.
3. **Select a Title:** Displays full details (Author, Artist, Status, Genres, Synopsis, Total chapters).
4. **Read or Download:**
   - **`[r]` Read Chapter:**
     - **Viewing Modes:** Choose **Longstrip (Webtoon / Continuous Vertical)** with zero gaps and seamless scrolling, or **Standard Paginated** (`imv`).
     - **Auto Next Chapter:** Automatically advances into the next available chapter upon completing the current one.
     - **5-Minute Auto-Cleanup:** Chapter cache is automatically purged from your system 5 minutes after finishing reading it.
   - **`[d]` Download Chapter(s):** Download single chapters, ranges (e.g. `1-10`), or `all` chapters into permanent `.cbz` comic book archives in `~/Manga`.
   - **`[l]` List Chapters:** Paginate and browse all chapters.

### 2. Interactive Extension Store (`extension install` or `mihon extension install`)
Typing `extension install` displays all available extensions in the Keiyoushi repository (~1,440+ extensions):
- **Browse & Paginate:** View numbered extensions, version numbers, package IDs, and language codes.
- **Search & Filter:** Press `[s]` to search by keyword (e.g. `manhwa`, `asura`, `flame`), or `[l]` to filter by language (`en`, `ko`, `ja`, etc.).
- **1-Click Install:** Enter the number of any extension to download, transpile, and install it.
- **Immediate Browsing:** Automatically prompts you to start browsing manga in the newly installed extension right away.

---

## 📖 Enhanced Reading Features

### 🌟 Longstrip (Webtoon) Mode
Manhwa and Webtoons are designed to be read vertically as one continuous canvas. Mihon CLI's Longstrip mode:
- **Zero Gaps & Zero Pagebreaks:** Seamlessly stacks panels vertically with 0px margins.
- **Automatic Smooth Scroll:** Press <kbd>CapsLock</kbd> to toggle butter-smooth hands-free reading.
- **Scroll Speed Controls:** Press <kbd>Super</kbd> + <kbd>+</kbd> / <kbd>-</kbd> (or direct <kbd>+</kbd> / <kbd>-</kbd>) to increase or decrease scroll speed with live HUD visual feedback.
- **Progress Tracking:** Floating status pill and top progress bar indicating scroll percentage.
- **Full Keyboard Navigation:**
  - `CapsLock`: Toggle automatic smooth scroll
  - `Super` + `+` / `-`: Increase / decrease scroll speed
  - `J` / `↓`: Smooth scroll down
  - `K` / `↑`: Smooth scroll up
  - `Space` / `PageDown`: Page down
  - `W`: Cycle width mode (Standard 860px → Wide 1200px → Full 100%)
  - `F`: Toggle Fullscreen
  - `Q` / `Esc`: Close reader & proceed to next chapter
- **Frameless App Window:** Opens directly as a native-feeling standalone window via Chromium app mode or your system default browser.

### ⏱️ Automatic 5-Minute Cache Cleanup
- When reading manga, chapters are downloaded to temporary cache (`~/.cache/mihon/reader`).
- The moment you close the reader after finishing a chapter, a background timer is armed to automatically delete the downloaded chapter files **5 minutes later**.
- Spawns as a detached background task, guaranteeing cleanup even if you exit the CLI. Permanent downloads (`mihon download`) remain safely kept in `~/Manga`.

### 🔄 Direct Auto-Advance to Next Chapter
- When you finish reading a chapter, Mihon detects chapter completion and automatically queues the next chronological chapter.
- A 2-second countdown prompt lets you hit Enter to open immediately, or press `q` to stop and return to the menu.

---

## Features

- **On-the-Fly Bytecode Transpilation:** Translates Android Dalvik bytecode (`classes.dex` inside `.apk` extensions) into standard JVM bytecode in memory using `dex-translator` and ASM tree transformation.
- **Bytecode Repair Pipeline:** Automatically repairs Kotlin value-class name mangling, synthesizes missing constructors for abstract classes/interfaces, fixes obfuscated companion/singleton field assignments, provides no-op comparators, and handles asset loading.
- **Embedded Android Compatibility Runtime:** Minimal, zero-overhead stubs for `android.content.Context`, `ContextWrapper`, `Application`, `SharedPreferences` (JSON-backed persistence), `androidx.preference.Preferences`, `android.os.Build`, and `eu.kanade.tachiyomi.AppInfo`.
- **Keiyoushi Store Integration:** Decodes the compressed Keiyoushi repository index (`index.pb`) covering 1,440+ extensions and thousands of manga sources.
- **CBZ Downloader:** Concurrently downloads manga chapter images with rate limiting, retries, and natural sort order, packaging them into valid `.cbz` comic book archives complete with `ComicInfo.xml` metadata.
- **Integrated Reader Launcher:** Directly launches local image/comic viewers (`imv`, `zathura`, `feh`, or `xdg-open`) for downloaded chapters.

---

## Building and Running

### Requirements
- JDK 17 or JDK 21+

### Quick Run
```bash
# Launch interactive dashboard
./mihon

# Or run extension commands directly
extension list
extension install
```

### Manual Build
```bash
./gradlew :cli:installDist
```

---

## CLI Commands Reference

Both interactive and scripted non-interactive execution are fully supported:

### Extensions
```bash
# Interactive store browser (shows all available extensions)
mihon extension install

# Search store and install
mihon extension install asura

# Interactive installed extensions & manga browser
mihon extension list

# Non-interactive remote listing
mihon extension list --remote -q mangadex --no-interactive

# Uninstall extension
mihon extension uninstall eu.kanade.tachiyomi.extension.en.asurascans
```

### Search & Read
```bash
# Search across installed sources
mihon search "Chainsaw Man"

# View details for a specific manga URL
mihon info "/series/127" -s "Flame Comics"

# Download chapter to CBZ
mihon download "/series/127" -c 1 -s "Flame Comics"

# Read chapter in viewer
mihon read "/series/127" -c 1 -s "Flame Comics"
```
