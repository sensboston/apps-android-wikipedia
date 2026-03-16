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
**Current provider: element.js** (Google Translate website widget injected into the article WebView).
The old proxy-based Google Translate approach (`client=gtx`) is still in code but not active.

### Architecture — element.js (active)

**Entry point**: `PageFragment.kt` → `startAutoTranslation()` → `startElementJsTranslation(sourceLang, targetLang)`

**How it works**:
1. Show progress bar (`updateProgressBar(true)`)
2. Set `googtrans=/src/tgt` cookie on the article host + inject `<meta name="googtrans">` tag via JS
3. Inject `google_translate_element` div, element.js `<script>` tag → element.js loads and translates DOM in-place
4. `script.onload` → `_elJsBridge.onLoaded()` → hide progress bar
5. `MutationObserver` in JS watches h2/h3 headings → reports translated titles via `_elJsBridge.onTitlesTranslated(json)` → ToC updated in real time
6. `autoTranslateOnLoad = true` → sticky: translation repeats on every subsequent article load (internal links only)
7. `applyContentsTitleTranslation()` provides initial ToC translation via LibreTranslate (dict + async fallback)

**Cookie optimization** (2026-03-16):
- `lastCookieLang` companion object caches last set cookie key (`sourceLang/targetLang@host`)
- `setCookie` only called when language actually changes — eliminates per-article delay
- One-time `removeAllCookies` migration on first translation (`Prefs.googtransCookiesCleared` flag) — cleans up accumulated stale cookies from old code, then never runs again

**Hyphenation fix**: `lang` attributes set AFTER `TranslateElement` constructor (inside `googleTranslateElementInit`) so element.js reads the original `lang=en` before we override to `lang=targetLang`. PCS sets `lang="en"` on inner containers — we reset ALL `[lang]` elements.

**Cast section fix**: Before element.js loads, sections with heading "Cast" where list items match `/ as /` pattern are renamed to "Film cast" to prevent mistranslation.

**Key files**:
- `page/PageFragment.kt` — `startElementJsTranslation()`, `_elJsBridge` JavascriptInterface, `clearAutoTranslate()`, `lastCookieLang` companion
- `page/SidePanelHandler.kt` — `updateTranslatedTitles(map, merge)` for ToC
- `language/LangLinksViewModel.kt` — `buildAutoTranslateItem()`, `LangLinksItem(isAutoTranslate=true)`
- `language/LangLinksActivity.kt` — `ACTIVITY_RESULT_AUTO_TRANSLATE=2`, `ACTIVITY_RESULT_CLEAR_TRANSLATE=3`
- `page/PageActivity.kt` — handles both result codes
- `settings/SettingsPreferenceLoader.kt` — `showTranslateLanguagePicker()` with "None" as first item
- `settings/Prefs.kt` — `translateLanguageCode`, `googtransCookiesCleared`
- `bridge/CommunicationBridge.kt` — JS bridge infrastructure
- `translation/TranslationManager.kt` — `isElementJsProvider()` flag (currently hardcoded true)

**JS bridge** (`_elJsBridge`, added to WebView in `PageFragment.setupMessageHandlers()`):
- `onError(msg)` — logs + hides progress bar
- `onLoaded()` — hides progress bar
- `onTitlesTranslated(json)` — updates ToC via `sidePanelHandler.updateTranslatedTitles()`

**"None" option**: In `Settings → Auto translate` language picker (`showTranslateLanguagePicker()`), "None" is first item — sets `translateLanguageCode = ""`, disables auto-translation.

**LangLinks menu** (Language button in article):
- First item: `"<LangName> (Auto)"` with subtitle "Auto-translate" → triggers `startAutoTranslation()`
- Second item: source language → navigate back to original
- Then: "Your Wikipedia languages" + "All languages"

---

### Old Google Translate Proxy Approach (inactive, code preserved)

Uses `client=gtx` endpoint via personal proxy at `https://senssoft.com/gtranslate`.
- Proxy source: `/home/ubuntu/gtproxy.py` on `ubuntu@129.80.19.104` (forces IPv6)
- Chunking: `wt` attributes on DOM elements, 12000-char chunks, 5x parallelism
- Still usable: set `TranslationManager.isElementJsProvider() = false`

---

### Settings UI
- `preference_key_translate_language` — picker for target language ("None" + MRU languages)
- `preference_key_translate_clear_cache` — shows cache entry count, clears on tap
- `preference_key_googtrans_cookies_cleared` — one-time migration flag
- Strings: `auto_translate_*`, `preference_*_translate_*` in `strings.xml`

### Build & Deploy
```bash
# Dev testing:
./gradlew assembleDevDebug
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb shell am start -n org.wikipedia.dev/org.wikipedia.main.MainActivity

# Demoscene/user release:
./gradlew assembleProdRelease
scp app/build/outputs/apk/prod/release/app-prod-release.apk ubuntu@129.80.19.104:/var/www/html/demos/wiki/wiki.apk
scp app/build/outputs/apk/prod/release/app-prod-release.apk ubuntu@129.80.19.104:/var/www/html/wiki.apk
```

Check `adb logcat -d -s Wikipedia:V *:E` for errors.
After reinstall: verify `Prefs.translateLanguageCode` not reset (`adb shell run-as org.wikipedia.dev sh -c 'grep translateLanguage shared_prefs/org.wikipedia.dev_preferences.xml'`).

**Signing** (prod release, `~/.sign/signing.properties`):
```
keystore=C:/Users/Ichiro/.android/debug.keystore
store.pass=android
key.alias=androiddebugkey
key.pass=android
```

**GitHub fork**: `git push myfork feature/llm-translate` (remote `myfork` = `sensboston/apps-android-wikipedia`)

### Recent commits (2026-03-16 session)
- `05121a8d` — Move None option from Language list to Settings language picker
- `fe5a8091` — Optimize translation startup: skip cookies if lang unchanged, add progress bar, add None option
- `eea377a5` — TODO: translation start delay due to removeAllCookies async
- `ecfaa24b` — Remove redundant webView.post in translation start
- `3083b049` — Fix hyphenation in translated articles (set lang AFTER TranslateElement constructor)
- `8ae37747` — Fix element.js translation on fresh devices: set googtrans cookie
- `036acfee` — Fix auto-translate for non-Cyrillic languages (removeAllCookies before setCookie)
