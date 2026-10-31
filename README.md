<div align="center">

<a href="https://mihon.app">
    <img src="./.github/assets/logo.png" alt="Mihon logo" title="Mihon logo" width="80"/>
</a>

# Mihon

### Full-featured Manga Reader & Desktop CLI Engine
Discover, read, and download manga, webtoons, and comics – on your Android device and on desktop Linux / macOS / Windows terminal.

[![Discord server](https://img.shields.io/discord/1195734228319617024.svg?label=&labelColor=6A7EC2&color=7389D8&logo=discord&logoColor=FFFFFF)](https://discord.gg/mihon)
[![GitHub downloads](https://img.shields.io/github/downloads/mihonapp/mihon/total?label=downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://mihon.app/download)
[![License: Apache-2.0](https://img.shields.io/github/license/mihonapp/mihon?labelColor=27303D&color=0877d2)](/LICENSE)

</div>

---

## 🖥️ Mihon CLI (Desktop & Terminal)

Mihon CLI allows you to connect directly to the [Keiyoushi Extensions Repository](https://keiyoushi.github.io/docs/guides/getting-started), install extensions, search catalogs, download manga to `.cbz` archives, and read chapters in desktop viewers without needing an Android emulator or the Android SDK.

It achieves this through an **in-memory Dalvik-to-JVM bytecode transpiler** and Android runtime stubs.

### Quick Start
An executable script is included in the root directory:
```bash
./mihon help
```

### Essential Commands
```bash
# 1. Search and install extensions from Keiyoushi repo
./mihon extension list --remote -q mangadex
./mihon extension install eu.kanade.tachiyomi.extension.all.mangadex
./mihon extension install eu.kanade.tachiyomi.extension.en.asurascans

# 2. View installed sources
./mihon sources

# 3. Search manga across installed sources
./mihon search "Chainsaw Man"
./mihon search "One Piece"

# 4. View manga metadata and available chapters
./mihon info "/manga/e896c48c-3150-437d-ba57-d8567eb399ae" -s "MangaDex"

# 5. Download chapter(s) to CBZ with ComicInfo.xml metadata
./mihon download "/manga/e896c48c-3150-437d-ba57-d8567eb399ae" -c 1 -s "MangaDex"

# 6. Read chapter in desktop viewer (imv, zathura, or system default)
./mihon read "/manga/e896c48c-3150-437d-ba57-d8567eb399ae" -c 1 -s "MangaDex"
```

For full documentation and technical details, see the [CLI Documentation](./cli/README.md).

---

## 📱 Android App

### Download

[![Mihon Stable](https://img.shields.io/github/release/mihonapp/mihon.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://mihon.app/download)
[![Mihon Beta](https://img.shields.io/github/v/release/mihonapp/mihon-preview.svg?maxAge=3600&label=Beta&labelColor=2c2c47&color=1c1c39)](https://mihon.app/download)

*Requires Android 8.0 or higher.*

### Features

* Local reading of content.
* A configurable reader with multiple viewers, reading directions and other settings.
* Tracker support: [MangaBaka](https://mangabaka.org), [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [Kitsu](https://kitsu.app/), [MangaUpdates](https://mangaupdates.com), [Shikimori](https://shikimori.one), [Bangumi](https://bgm.tv/), and [Hikka](https://hikka.io/) support.
* Categories to organize your library.
* Light and dark themes.
* Schedule updating your library for new chapters.
* Create backups locally to read offline or to your desired cloud service.
* Plus much more...

---

## Contributing

[Code of conduct](./CODE_OF_CONDUCT.md) · [Contributing guide](./CONTRIBUTING.md)

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Before reporting a new issue, take a look at the [FAQ](https://mihon.app/docs/faq/general), the [changelog](https://mihon.app/changelogs/) and the already opened [issues](https://github.com/mihonapp/mihon/issues); if you got any questions, join our [Discord server](https://discord.gg/mihon).

### Credits

Thank you to all the people who have contributed!

<div align="center">
<a href="https://github.com/mihonapp/mihon/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=mihonapp/mihon" alt="Mihon app contributors" title="Mihon app contributors" width="800"/>
</a>
</div>

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

### License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>
