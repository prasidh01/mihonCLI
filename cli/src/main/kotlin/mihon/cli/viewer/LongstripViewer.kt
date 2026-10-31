package mihon.cli.viewer

import java.io.File

object LongstripViewer {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif", "gif")

    /**
     * Generates a zero-gap, continuous vertical Webtoon/Longstrip reader HTML file
     * inside [chapterDir] referencing all downloaded image files in natural order.
     */
    fun createHtml(
        chapterDir: File,
        mangaTitle: String,
        chapterName: String,
        nextChapterName: String? = null,
    ): File {
        val imageFiles = chapterDir.listFiles { file ->
            file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS
        }?.toList().orEmpty().sortedWith { a, b -> naturalCompare(a.name, b.name) }

        val htmlFile = File(chapterDir, "index.html")
        val escapedTitle = escapeHtml(mangaTitle)
        val escapedChapter = escapeHtml(chapterName)
        val escapedNext = if (nextChapterName != null) escapeHtml(nextChapterName) else null

        val imagesHtml = StringBuilder()
        for ((idx, img) in imageFiles.withIndex()) {
            val pageNum = idx + 1
            // Use relative path for maximum portability
            imagesHtml.appendLine(
                """        <img class="strip-img" src="./${img.name}" alt="Page $pageNum" loading="${if (idx < 5) "eager" else "lazy"}">"""
            )
        }

        val nextCardHtml = if (escapedNext != null) {
            """
            <div class="end-card">
                <div class="end-title">Completed $escapedChapter</div>
                <div class="end-next">Up Next: <strong>$escapedNext</strong></div>
                <div class="end-sub">Closing this window will automatically open the next chapter.</div>
                <button class="btn-next" onclick="window.close()">Next Chapter →</button>
            </div>
            """.trimIndent()
        } else {
            """
            <div class="end-card">
                <div class="end-title">Completed $escapedChapter</div>
                <div class="end-sub">You have caught up to the latest chapter!</div>
                <button class="btn-next" onclick="window.close()">Close Reader</button>
            </div>
            """.trimIndent()
        }

        val htmlContent = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$escapedTitle - $escapedChapter [Longstrip]</title>
    <style>
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }
        html, body {
            background-color: #0d0d0d;
            color: #eeeeee;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            overflow-x: hidden;
            scroll-behavior: smooth;
        }
        /* Top Progress Bar */
        #progress-bar {
            position: fixed;
            top: 0;
            left: 0;
            height: 3px;
            background: linear-gradient(90deg, #00d2ff 0%, #3a7bd5 100%);
            width: 0%;
            z-index: 9999;
            transition: width 0.1s ease;
        }
        /* Floating Header Overlay */
        #floating-header {
            position: fixed;
            top: 12px;
            right: 20px;
            background: rgba(18, 18, 18, 0.85);
            backdrop-filter: blur(8px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 20px;
            padding: 6px 16px;
            font-size: 13px;
            font-weight: 500;
            color: #ccc;
            z-index: 1000;
            display: flex;
            align-items: center;
            gap: 10px;
            box-shadow: 0 4px 16px rgba(0,0,0,0.6);
            opacity: 0.85;
            transition: opacity 0.3s ease;
        }
        #floating-header:hover {
            opacity: 1;
        }
        .header-title {
            color: #ffffff;
            font-weight: 600;
        }
        .header-badge {
            background: #2a3b4c;
            color: #64b5f6;
            padding: 2px 8px;
            border-radius: 10px;
            font-size: 11px;
            font-weight: bold;
        }
        /* Container for Longstrip Images */
        .strip-container {
            width: 100%;
            max-width: 860px; /* Standard manhwa reading column */
            margin: 0 auto;
            display: flex;
            flex-direction: column;
            align-items: center;
            background: #000000;
            box-shadow: 0 0 30px rgba(0,0,0,0.8);
            transition: max-width 0.2s ease;
        }
        /* Image styling - strictly NO gaps, NO margins, NO borders */
        .strip-img {
            display: block;
            width: 100%;
            height: auto;
            margin: 0;
            padding: 0;
            border: none;
            outline: none;
            vertical-align: bottom; /* Crucial: removes 4px inline baseline gap */
        }
        /* End of Chapter Card */
        .end-card {
            width: 100%;
            max-width: 860px;
            margin: 0 auto;
            padding: 50px 20px 80px;
            text-align: center;
            background: #141414;
            border-top: 1px solid #222222;
        }
        .end-title {
            font-size: 22px;
            font-weight: 700;
            color: #ffffff;
            margin-bottom: 8px;
        }
        .end-next {
            font-size: 16px;
            color: #64b5f6;
            margin-bottom: 8px;
        }
        .end-sub {
            font-size: 13px;
            color: #888888;
            margin-bottom: 24px;
        }
        .btn-next {
            display: inline-block;
            background: #1976d2;
            color: #ffffff;
            text-decoration: none;
            padding: 12px 32px;
            border-radius: 8px;
            font-size: 15px;
            font-weight: 600;
            cursor: pointer;
            border: none;
            transition: background 0.2s, transform 0.1s;
        }
        .btn-next:hover {
            background: #1565c0;
            transform: translateY(-1px);
        }
        .keyboard-hints {
            margin-top: 28px;
            font-size: 12px;
            color: #666666;
            line-height: 1.8;
        }
        .key {
            background: #252525;
            padding: 2px 7px;
            border-radius: 4px;
            color: #bbbbbb;
            border: 1px solid #333333;
            font-family: monospace;
        }
    </style>
</head>
<body>
    <div id="progress-bar"></div>
    <div id="floating-header">
        <span class="header-title">$escapedTitle</span>
        <span>•</span>
        <span>$escapedChapter</span>
        <span class="header-badge" id="percent-badge">0%</span>
    </div>

    <div class="strip-container" id="strip-container">
$imagesHtml
    </div>

    $nextCardHtml

    <div class="keyboard-hints" style="text-align: center; padding-bottom: 40px; background: #141414;">
        <span class="key">J</span> / <span class="key">↓</span> Scroll Down &nbsp;|&nbsp;
        <span class="key">K</span> / <span class="key">↑</span> Scroll Up &nbsp;|&nbsp;
        <span class="key">Space</span> Page Down &nbsp;|&nbsp;
        <span class="key">W</span> Width Toggle &nbsp;|&nbsp;
        <span class="key">F</span> Fullscreen &nbsp;|&nbsp;
        <span class="key">Q</span> / <span class="key">Esc</span> Close
    </div>

    <script>
        // Track reading progress bar & badge
        function updateProgress() {
            const h = document.documentElement;
            const b = document.body;
            const st = 'scrollTop';
            const sh = 'scrollHeight';
            const percent = ((h[st]||b[st]) / ((h[sh]||b[sh]) - h.clientHeight)) * 100;
            const cleanPct = Math.min(100, Math.max(0, Math.round(percent)));
            document.getElementById('progress-bar').style.width = cleanPct + '%';
            document.getElementById('percent-badge').innerText = cleanPct + '%';
        }
        window.addEventListener('scroll', updateProgress);
        window.addEventListener('resize', updateProgress);

        // Width modes: 0 = Standard (860px), 1 = Wide (1200px), 2 = Full (100%)
        let widthMode = 0;
        const widths = ['860px', '1200px', '100%'];
        function toggleWidth() {
            widthMode = (widthMode + 1) % widths.length;
            document.getElementById('strip-container').style.maxWidth = widths[widthMode];
        }

        // Keyboard navigation
        window.addEventListener('keydown', (e) => {
            if (e.key === 'j' || e.key === 'ArrowDown') {
                window.scrollBy({ top: 220, behavior: 'smooth' });
                e.preventDefault();
            } else if (e.key === 'k' || e.key === 'ArrowUp') {
                window.scrollBy({ top: -220, behavior: 'smooth' });
                e.preventDefault();
            } else if (e.key === ' ' || e.key === 'PageDown') {
                window.scrollBy({ top: window.innerHeight * 0.85, behavior: 'smooth' });
                e.preventDefault();
            } else if (e.key === 'PageUp') {
                window.scrollBy({ top: -window.innerHeight * 0.85, behavior: 'smooth' });
                e.preventDefault();
            } else if (e.key === 'w' || e.key === 'W') {
                toggleWidth();
                e.preventDefault();
            } else if (e.key === 'f' || e.key === 'F') {
                if (!document.fullscreenElement) {
                    document.documentElement.requestFullscreen();
                } else {
                    document.exitFullscreen();
                }
                e.preventDefault();
            } else if (e.key === 'q' || e.key === 'Q' || e.key === 'Escape') {
                window.close();
            }
        });
    </script>
</body>
</html>
        """.trimIndent()

        htmlFile.writeText(htmlContent, Charsets.UTF_8)
        return htmlFile
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun naturalCompare(a: String, b: String): Int {
        val numRegex = Regex("\\d+")
        val matchA = numRegex.find(a)
        val matchB = numRegex.find(b)
        if (matchA != null && matchB != null && a.substring(0, matchA.range.first) == b.substring(0, matchB.range.first)) {
            val numA = matchA.value.toIntOrNull()
            val numB = matchB.value.toIntOrNull()
            if (numA != null && numB != null && numA != numB) {
                return numA.compareTo(numB)
            }
        }
        return a.compareTo(b, ignoreCase = true)
    }
}
