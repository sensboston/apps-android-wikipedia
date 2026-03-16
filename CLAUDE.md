@AGENTS.md

## ⚠️ BUILD RULE — READ FIRST

**App name MUST always be "Wikipedia" (package: org.wikipedia).**
NEVER use `alpha`, `beta` flavors for builds intended for users:
- `alpha` → "Wikipedia Alpha" (org.wikipedia.alpha) — SEPARATE app, does NOT update existing installs
- `beta` → "Wikipedia Beta" (org.wikipedia.beta) — SEPARATE app

**For local development/testing:** `assembleDevDebug` → `org.wikipedia.dev`
**For demoscene/user deployment:** `assembleProdRelease` → `org.wikipedia` ("Wikipedia")
Both will update over existing Wikipedia installs without creating a duplicate app.

---

## Auto-translate Feature (branch: feature/llm-translate)

### Overview
Translates the current Wikipedia article into a user-selected language directly within the app.
Uses Google Translate (unofficial `client=gtx` endpoint) via a personal proxy to avoid rate-limiting.

### Architecture

**Entry point**: `PageFragment.kt` → `startAutoTranslation()` (called on article load if `autoTranslateOnLoad = true`)

**Translation pipeline**:
1. Bridge extracts article HTML from WebView (`JavaScriptActionHandler.getArticleHtml()`)
2. `TranslationTextExtractor.extractForGoogle(html, maxChunkSize=12000)` marks DOM elements with `wt="N"` attributes, splits into ≤12000-char chunks
3. Chunks sent in **parallel** (up to 5 concurrent) via `Semaphore(5)` + `coroutineScope { chunks.mapIndexed { async { ... } }.awaitAll() }`
4. Each chunk goes to `GoogleTranslationProvider.translate()` → POST `https://senssoft.com/gtranslate`
5. Response parsed by `TranslationTextExtractor.parseGoogleResponse()` → `Map<Int, String>` (wt-index → translated HTML)
6. `injectTranslationsGoogle(parsed)` injected progressively into main WebView after each chunk completes
7. Result cached in Room DB (`TranslationCacheEntry`) keyed by `title|sourceLang|targetLang` + revisionId

**Key files**:
- `translation/GoogleTranslationProvider.kt` — HTTP client, proxy URL, response parsing
- `translation/TranslationTextExtractor.kt` — `extractForGoogle()`, `parseGoogleResponse()`, `isFootnote()` filter
- `translation/TranslationCache.kt` / `TranslationCacheDao.kt` / `TranslationCacheEntry.kt` — Room cache
- `translation/TranslationManager.kt` — provider factory, `isGoogleProvider()` / `isElementJsProvider()` flags
- `page/PageFragment.kt` — orchestration, snackbar progress, JS injection
- `settings/SettingsPreferenceLoader.kt` — language picker, cache clear UI
- `settings/Prefs.kt` — `translateLanguageCode`, `autoTranslateLanguageCode`

**Proxy**: `https://senssoft.com/gtranslate` (Flask on Oracle Cloud, forces IPv6 to avoid rate limits)
- Source: `/home/ubuntu/gtproxy.py` on `ubuntu@129.80.19.104`
- Direct device IP (e.g. 96.230.244.18) gets 429 after the very first request
- element.js (`client=te`) has much higher rate limits but requires a different approach (see below)

**Chunking**: `wt` attribute (not `data-wt` — Google translates the "data" prefix away) marks each translatable element with a sequential integer index. Footnotes/references filtered out by `isFootnote()`. Chunk size 12000 chars → typical article: 10–45 chunks. WW2 article: ~44 chunks, translates in ~7–10s with 5x parallelism.

**Translation cache**: Room DB table `TranslationCacheEntry`. 30-day expiry. Cache hit skips all network calls. Settings UI shows cache entry count and allows clearing. Cache keyed by article revisionId — stale on article edit.

**Progressive rendering**: `injectTranslationsGoogle(parsed)` called after each chunk → user sees translations appear incrementally. Snackbar shows "Translating N/M chunks".

**Known issues / limitations**:
- Proxy is a personal server on Oracle Cloud — not production-grade. If it goes down, translation fails.
- Very long articles (100+ chunks at 4500 char) were slow; fixed by increasing chunk size to 12000.
- Some inline elements (footnotes) still included if not caught by `isFootnote()` selector.

---

### element.js Approach (branch: elementjs_translate — EXPERIMENTAL, NOT WORKING YET)

**Goal**: Use Google's `element.js` website translator instead of the `client=gtx` API. This runs in the `client=te` rate-limit tier (used by millions of websites) and requires no proxy.

**Approach**: Load `wt`-marked HTML in a **hidden WebView** (`loadDataWithBaseURL("https://en.wikipedia.org/", ...)`, no window attachment). element.js runs, translates DOM in-place. A `MutationObserver` watches `#_c [wt]` elements and reports changes via JS bridge (`_elBridge.onTranslated(json)`). Kotlin `callbackFlow` emits `Map<Int, String>` progressively.

**Implementation**: `translation/ElementJsProvider.kt`
- `callbackFlow` bridges async WebView callbacks to Kotlin coroutines
- Hidden WebView with `javaScriptEnabled`, `domStorageEnabled`, `WebChromeClient` (for JS console logging)
- JS bridge `_elBridge`: `onTranslated(json)`, `onComplete()`, `onError(msg)`
- `buildPage()` generates HTML with element.js `<script>` tag, MutationObserver, completion poll
- JS scroll loop: 600px steps every 150ms to force element.js to process lazy-loaded content

**Why it's not working**: element.js uses lazy translation (visible content first). A hidden WebView has a tiny viewport, so element.js only translates what's "visible" (very little). Attempted fix: JS scroll through all content. But the scroll doesn't seem to trigger element.js to translate off-screen content in a headless WebView.

**Attempted but reverted**: Calling `wv.measure()` + `wv.layout()` to give the hidden WebView a real size caused a **silent crash on Samsung Android** (WebView not attached to a window). No logs appeared when this code was present.

**What was tried**:
1. `wv.measure(MeasureSpec.makeMeasureSpec(1080, EXACTLY), MeasureSpec.makeMeasureSpec(19200, EXACTLY))` + `wv.layout(0, 0, 1080, 19200)` → silent crash, removed
2. JS `window.scrollTo(0, scrollPos)` every 150ms → not yet confirmed to work after crash fix
3. `WebChromeClient` console logging → no ElementJs logs appeared (because of crash)

**TODO for element.js**:
- [ ] After removing `measure/layout`, rebuild and check logcat for `ElementJs` tag — are any JS console messages appearing?
- [ ] If logs appear, verify whether MutationObserver fires and `onTranslated()` is called
- [ ] Try alternative approach: attach hidden WebView to a non-visible window overlay (requires `SYSTEM_ALERT_WINDOW` permission — not ideal)
- [ ] Try alternative: use `WebView.postVisualStateCallback()` or `evaluateJavascript()` to poll translated element count instead of MutationObserver
- [ ] Consider: render all content as `display:block` in the hidden WebView page (remove lazy loading CSS) so element.js processes all elements
- [ ] Test with a short article first (Cat) to isolate "is element.js even loading?" from "is lazy loading the issue?"
- [ ] Consider using `IntersectionObserver` mock to fake that all elements are visible
- [ ] Alternative: serve all chunks to a single visible-height WebView by setting `window.innerHeight` via JS before element.js loads

**Current state of elementjs_translate branch**: `TranslationManager.isElementJsProvider() = true` (not committed), `ElementJsProvider.kt` added (not committed), `PageFragment.kt` has element.js flow (not committed). To switch back to proxy: `git checkout feature/llm-translate` (discards uncommitted changes) or commit the elementjs_translate state first.

---

### Settings UI
- `preference_key_translate_language` — picker for target language (from app MRU languages)
- `preference_key_translate_clear_cache` — shows cache entry count, clears on tap
- Strings in `strings.xml`: `auto_translate_*`, `preference_*_translate_*`

### Build & Deploy
```bash
./gradlew assembleDevDebug
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb shell am start -n org.wikipedia.dev/org.wikipedia.main.MainActivity
```
Check `adb logcat -d -s Wikipedia:V *:E` for errors.
After reinstall: verify `Prefs.translateLanguageCode` not reset (adb `run-as` + grep shared_prefs).

**Demoscene deployment** (mydemoscene.com/demos/wiki/wiki.apk):
- ALWAYS use `assembleProdRelease` — package `org.wikipedia`, app name "Wikipedia"
- Requires `~/.sign/signing.properties` with debug keystore (created 2026-03-15):
  ```
  keystore=C:/Users/Ichiro/.android/debug.keystore
  store.pass=android
  key.alias=androiddebugkey
  key.pass=android
  ```
- NEVER use alpha/beta/dev flavor for demoscene — alpha is `org.wikipedia.alpha` ("Wikipedia Alpha"),
  installs as a SEPARATE app and does NOT update users' existing Wikipedia installation
- Deploy: `scp app/build/outputs/apk/prod/release/app-prod-release.apk ubuntu@129.80.19.104:/var/www/html/demos/wiki/wiki.apk`
