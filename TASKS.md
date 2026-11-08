# Task Queue

Standard queue for planned work in this project. New work is appended below and stays `queued`
until it has been implemented and verified. Each entry is self-contained: context, files,
constraints, acceptance criteria, and verification commands.

## Queue

| ID | Status | Priority | Task |
|----|--------|----------|------|
| TASK-2026-11-07-SPORTS-REPLAYS | done | P2 | Sports Replays section on the Sports page (YouTube-only) |

---

## TASK-2026-11-07-SPORTS-REPLAYS — Sports Replays section on the Sports page

- **Status:** done — implemented 2026-11-08 with the app's YouTube tabs engine (no API key), plus
  pre/post-event YouTube rows on each game's page.
- **Created:** 2026-11-07
- **Project:** `/Users/robbdeeze/Documents/projects/NuvioPlusIPTV-Mobile-old-main`
- **Area:** Live TV → Sports
- **Depends on:** a YouTube Data API v3 key for live results. The keyless mode must still work.
- **Spec:** `/Users/robbdeeze/.hermes/profiles/web-reach-scout/workspace/sports-replays-api.md`

### Implementation (2026-11-08)

- **Replays tab:** directly after Following, with a sport filter and one YouTube row per sport in
  the region (plus followed sports). Each row is cached for an hour.
- **Game page:** directly under Watch on, a **Pre-event videos** row for upcoming/live games
  (commentary, previews, press conferences, weigh-ins) and a **Post-event videos** row for
  finished games (highlights, interviews, reaction).
- **Engine:** `YouTubeHost.searchVideos` / `playVideo`, backed by `YouTubeViewModel` and
  MediaServiceCore — the same search and playback path as the YouTube tabs. No YouTube Data API
  key, Highlightly, ScoreBat or other provider is used.

> The dated requirements below describe the earlier YouTube Data API plan. The shipped version
> follows the engine-only approach above: no API key, no keyless mode, and no date picker are
> needed because the app's YouTube engine searches without one.

### Goal

Add a new **Replays** tab to the Sports page, directly after the existing **Following** tab.
The tab lists recent official sports replays/highlights from YouTube, filtered by date and sport,
and plays them in the app's existing in-app YouTube player. When no API key is configured, the
tab must remain usable without any API call: show the official-channel shortcuts and open
YouTube externally.

### Hard constraints

- **YouTube only.** Use only the spec section **“Official YouTube Channels (Free Fallback — No
  API Key)”** and its **YouTube Data API** endpoints/embed format.
- **Do NOT use Highlightly, Highlight API, ScoreBat, or any other replay provider** — not even as
  a hidden fallback. Ignore the spec's Highlightly/backend sections for this task.
- No new heavy dependencies. Reuse Ktor (`ktor-client-*` 3.4.1 already present), `StateFlow`,
  Material3/Nuvio tokens, and the existing YouTube player hook.
- Verify official YouTube channel IDs before shipping. The spec explicitly marks several IDs as
  `(verify)` and several are placeholder-looking values. Do not ship an unverified `UC...` ID.

### Current code / where to work

- Sports page and tab enum:
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/livetv/ui/sports/SportsScreen.kt`
  - `private enum class SportsTab { MINE, ALL, SEARCH, FOLLOWING }` (around line 97).
  - Chip row (around lines 150–167) renders My Sports, All Events, Search, Following.
  - `when (tab)` dispatches to `MySportsTab`, `AllEventsTab`, `SportsSearchTab`, `SportsFollowingTab`.
- Existing Sports settings sheet:
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/livetv/ui/sports/SportsFollowing.kt`
  (`SportsSettingsSheet` around line 411; add the YouTube key row/field there or in the Sports
  settings page, whichever matches the surrounding patterns).
- Sports settings model:
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/livetv/sports/SportsModels.kt`
  (`data class SportsSettings` around line 170). Do **not** put the API key in this JSON model;
  it is stored plain-text through `LiveTvPreferences` and would leak the secret.
- Sports HTTP access:
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/livetv/sports/SportsPlatform.kt`
  (`suspend fun get(url: String): String`) and the existing `httpGetText` helper in
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/`.
- Existing in-app YouTube player:
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/youtube/YouTubeHost.kt`
  - `YouTubePlayRequest(url, title, poster, videoId, startPositionMs)`.
  - `YouTubeHost.openVideo: ((YouTubePlayRequest) -> Unit)?` is wired in
    `composeApp/src/commonMain/kotlin/com/nuvio/app/MainAppContent.kt`
    (`fun openYouTubeVideo(request)` around line 997).
  - If `openVideo` is null (non-Android platform), open the YouTube watch URL externally.
- Secure credential storage pattern to mirror:
  `composeApp/src/androidMain/kotlin/com/nuvio/app/features/mdblist/MdbListAuthPersistence.android.kt`
  (Android Keystore AES/GCM, SharedPreferences stores ciphertext only) plus the common
  `expect object` / iOS actual in
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/mdblist/MdbListPlatform.kt` and
  `composeApp/src/iosMain/.../MdbListAuthPersistence.ios.kt`.
- Strings: `composeApp/src/commonMain/composeResources/values/strings.xml` (existing Sports
  strings start around `livetv_sports_*`, e.g. `livetv_sports_following`).
- Cache directory constant: `LiveTvDirs.SPORTS` (used by `SportsRepository`).

### Requirements

#### 1. Tab and UI

- Add `REPLAYS` to `SportsTab` **after** `FOLLOWING` so the order is:
  `MINE, ALL, SEARCH, FOLLOWING, REPLAYS`.
- Add a `Chip` labeled **Replays** after the Following chip. Use a new string resource
  `livetv_sports_replays`; add translations only if the project's normal flow requires it.
- Default selection stays as today (My Sports when follows exist, otherwise All Events). Do not
  force Replays on launch.
- Preserve the selected tab across rotation/process recreation using the existing
  `rememberSaveable` pattern.
- Create a `SportsReplaysTab` (new file `SportsReplaysUi.kt` in the same `ui/sports` package) with:
  - **Date controls:** `Today`, `Yesterday`, and an optional date-picker entry for an older date.
    Make “future” dates unavailable. Keep the selected date in `rememberSaveable` (epoch day or
    `YYYY-MM-DD`).
  - **Sport filter chips:** `All`, `NBA`, `NFL`, `NHL`, `MLB`, `EPL`, `UFC`, `BKFC`, `IPL` (and
    optionally `F1`, `La Liga`, `Bundesliga` if they are wired to official channels from the
    spec). Single-select is enough; horizontal scroll like the tab row.
  - **Replay cards:** thumbnail (16:9), title (2 lines max), channel name, published date.
    Cards should follow `NuvioTokens` colors/radii and use the existing card/surface patterns.
  - **States:** initial loading, empty (no results for that date/sport), error with Retry,
    keyless empty state, and a cached-results notice when offline.
  - **No keyless API calls.** If no key is stored, do not construct/call YouTube URLs. Show:
    - a short explanation (“Add a YouTube Data API key in Sports settings to load replays”);
    - a button to open Sports settings;
    - buttons/links to the official channels and a YouTube search page for the selected sport.
- Cards must be clickable with the same ripple/selection behavior as other Sports list items.

#### 2. Data source (YouTube Data API v3 only)

- Official channel mapping from the spec (handles):
  - NBA `@NBA`, NFL `@NFL`, NHL `@NHL`, MLB `@MLB`, EPL `@PL`, La Liga `@LaLiga`,
    Bundesliga `@BundesligaEN`, F1 `@Formula1`, UFC `@UFC`, BKFC `@BKFC`,
    Cricket/IPL `@StarSportsIPL`.
- Resolve real numeric channel IDs before shipping. Recommended one-time resolution:
  `GET https://www.googleapis.com/youtube/v3/channels?part=id,snippet&forHandle=@NBA&key=...`
  (or `search?part=snippet&type=channel&q=NBA%20official&key=...`), then store the verified
  `UC...` IDs in a Kotlin constant/map. Cache them locally so resolution isn't repeated.
- Primary fetch: latest uploads from the selected official channel:
  `GET https://www.googleapis.com/youtube/v3/search?part=snippet&channelId={channelId}&type=video&order=date&maxResults=20&key={key}`
- Optional team/query search (only when the user has typed a query; the first version does not
  need a text field): `q={teams} highlights {date}` with `type=video&order=date`.
- Parse: `items[].id.videoId`, `items[].snippet.title`, `channelTitle`, `publishedAt`,
  `thumbnails.medium.url` (fall back to `default`/`high`), and build:
  - watch URL: `https://www.youtube.com/watch?v={videoId}`
  - embed/player URL: `https://www.youtube.com/embed/{videoId}?rel=0` (use `autoplay=1` only when
    the app's player expects it; the in-app player takes the watch URL + videoId today).
- `search.list` costs **100 units** per call (10,000/day default). Do not call it per keystroke or
  per recomposition.
- Filter results to the selected date window client-side using `publishedAt` (the API's `order=date`
  is not a date filter). A replay is “Today” if `publishedAt` falls on the device's local today;
  “Yesterday” likewise. For older dates, match the local calendar day.
- Prefer official channel results; if a result comes from a non-official channel, either drop it or
  label it clearly. The spec's copyright note says official league/team channels only.
- Handle `error.errors[].reason` values: `quotaExceeded`, `keyInvalid`, `ipRefererBlocked`,
  `videoNotFound`, etc. Show a human-readable error; never crash.

#### 3. Repository and caching

- Add `SportsReplaysRepository` (object or class) in
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/livetv/sports/`.
- Expose `StateFlow<SportsReplaysUiState>` containing: selected date, selected sport, loading,
  items, error, `fromCache`, and `hasApiKey`.
- Use the existing `SportsPlatform.get` or `httpGetText` for HTTP. Do not create a second Ktor
  client unless there is no reusable helper.
- **Memory cache:** key = `channelId|sport|localDate` (or `query|date`). Keep at most ~30 entries.
- **Persisted cache:** store JSON under `LiveTvDirs.SPORTS` (e.g.
  `replays-{sport}-{yyyy-MM-dd}.json` or one JSON document with entries). Include `fetchedAt`.
  TTL:
  - Today: 15–30 minutes (scores/replays keep appearing).
  - Yesterday and older: 6–24 hours.
  - On a cache hit, render immediately and refresh in the background only if TTL expired.
- **Debounce:** 400–500 ms on date/sport changes; cancel the previous request job. Rapid chip
  tapping must result in at most one in-flight request for the final selection.
- In-flight de-duplication: a `Mutex`/`Semaphore` or map of jobs keyed by cache key so two
  composables/screens don't fire duplicate searches.
- On network failure with a cached value: show cached items plus a non-blocking “Showing saved
  results” note. On failure without cache: error + Retry, not a blank screen.
- No background polling. Fetch only when the Replays tab is visible and a filter changes or the
  user retries.
- Unit-test URL building, JSON parsing, local-date filtering, cache TTL, and debounce logic with
  fake data. No live API calls in tests.

#### 4. Settings and API key

- Add a **YouTube Data API key** field, either:
  - in the existing `SportsSettingsSheet` (Sports page → Tune icon), or
  - in the app's existing provider/API key settings page if that is a better fit.
- Store it securely, not in `SportsSettings` JSON and not in plain `SharedPreferences`. Mirror the
  MdbList credential pattern:
  - common `expect object` + Android actual using `AndroidKeyStore` AES/GCM (ciphertext only in
    prefs), iOS actual using Keychain;
  - expose `hasKey`, `readKey`, `saveKey`, `clearKey`.
- Mask the key in the UI (show last 4 characters only), with Save/Clear actions and a short
  “Create a key in Google Cloud Console → YouTube Data API v3” hint/link.
- When no key is set:
  - `hasApiKey` is false;
  - the Replays tab shows the keyless empty state (official channel/search buttons);
  - zero YouTube API calls are made;
  - no crash, no infinite spinner.
- When the key is cleared, immediately return to keyless mode and drop in-memory results that
  required the key.
- Make sure the key never appears in logs, analytics, crash reports, exported settings, or the
  `SportsSettings` backup JSON.

#### 5. Playback

- On card tap, build `YouTubePlayRequest`:
  - `url` = watch URL (or the player URL the current player expects),
  - `title` = video title,
  - `poster` = thumbnail URL,
  - `videoId` = YouTube video ID.
- If `YouTubeHost.openVideo != null`, call it so the app's existing in-app YouTube player opens.
- Otherwise open the watch URL externally with the existing external-URL action/`Intent.ACTION_VIEW`
  path (add an expect/actual helper if none is exposed to common code). Do not add a WebView.
- Do not autoplay audio before the player screen is ready; use the existing player behavior.
- If a video is age/region restricted or embedding is disabled, fall back to the external watch
  URL and show a brief toast/message.

#### 6. Quality and patterns

- Follow the app's existing patterns: Ktor, `StateFlow`, `collectAsStateWithLifecycle`,
  `rememberSaveable`, `NuvioTokens`, `Chip`, `NuvioScreenHeader`, `NuvioModalBottomSheet`.
- Keep all new UI strings in `strings.xml`; don't hardcode user-visible English in composables
  (the existing Sports code already uses string resources for tab labels).
- Avoid new Gradle dependencies. `kotlinx.serialization`/`org.json` and Ktor are already present;
  use whichever the surrounding Sports code uses.
- Use stable keys in `LazyColumn` (`videoId`) and avoid re-fetching on every recomposition.
- Respect `nuvioSafeBottomPadding` and the existing side insets.
- iPad/tablet and phone layouts should both work; reuse the existing `isTablet` patterns where
  relevant.

### Acceptance criteria

- [ ] `SportsTab.REPLAYS` exists after `FOLLOWING`; a **Replays** chip appears after **Following**
      and selects the tab.
- [ ] Tab selection survives rotation/process recreation.
- [ ] Replays tab shows Today / Yesterday / date-picker controls and the sport filter chips
      (`All`, `NBA`, `NFL`, `NHL`, `MLB`, `EPL`, `UFC`, `BKFC`, `IPL` at minimum).
- [ ] Cards show thumbnail, title, channel name, and published date; tapping a card opens the
      in-app YouTube player when available, otherwise the external YouTube watch URL.
- [ ] With a valid key: results load for the selected sport/date; changing filters is debounced;
      cache hits avoid extra API calls; quota errors show a useful message and Retry.
- [ ] With no key: **zero** YouTube API calls; the tab shows a graceful keyless state with
      working buttons that open the official channel/search on YouTube.
- [ ] API key is stored encrypted (Keystore/Keychain) and never appears in `SportsSettings` JSON,
      logs, or backups; Clear restores keyless behavior immediately.
- [ ] Channel IDs are verified and documented in code (no placeholder IDs).
- [ ] No Highlightly, Highlight API, ScoreBat, or other replay provider code is added.
- [ ] No new heavy dependencies; existing Ktor/Compose patterns are reused.
- [ ] Build and focused tests pass (see below).
- [ ] `TASKS.md` entry is moved to `done` only after the manual checks below pass.

### Verification

JDK 17 is required on this machine:

```bash
export JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home
cd /Users/robbdeeze/Documents/projects/NuvioPlusIPTV-Mobile-old-main
```

Compile / build:

```bash
./gradlew :composeApp:compileAndroidMain -Pnuvio.android.distribution=full --console=plain
./gradlew :composeApp:testAndroidHostTest \
  --tests 'com.nuvio.app.features.livetv.sports.SportsReplays*' \
  -Pnuvio.android.distribution=full --console=plain
./gradlew :androidApp:assembleFullDebug -Pnuvio.android.distribution=full --console=plain
```

Manual checks (emulator or device):

1. Fresh install with no key → Sports → Replays: keyless empty state appears, no network calls
   to `googleapis.com` (verify with a proxy/Logcat), and the official-channel/search buttons open
   YouTube.
2. Add a YouTube Data API key in Sports settings → Replays → Today → NBA: cards load with
   thumbnails/titles/channels/dates; tap one → in-app YouTube player opens and plays.
3. Switch date/sport rapidly → only the final selection's request reaches the network (debounce).
4. Re-open the same sport/date → cached results appear immediately; airplane mode still shows
   cached results with a “saved results” note.
5. Clear the key → keyless state returns; no API calls; tab still works.
6. Rotate the device / background and return → selected tab, date, and sport persist.
7. Force an invalid key or quota error → readable error + Retry; no crash.

### Out of scope

- Highlightly/Highlight API/ScoreBat or any other replay API.
- Full-game paid sources (League Pass, Sunday Ticket, DAZN, JioCinema, etc.).
- Backend proxy deployment, server-side caching, or a new web service.
- Notifications, downloads/offline video, DVR, or playback position syncing for replays.
- Free-text search inside Replays (the sport/date filters are enough for v1).

### References

- Spec, YouTube-only section: `sports-replays-api.md` lines ~213–268.
  - YouTube Data API endpoints: lines ~235–241.
  - Embed URL format: lines ~244–253.
  - Channel map / “verify” note: lines ~258–268.
  - YouTube search fallback implementation (reference only; do not copy the Highlightly parts):
    lines ~405–438.
- Current Sports tabs/chips: `SportsScreen.kt` lines ~97 and ~150–167.
- Existing YouTube host/player: `YouTubeHost.kt`; `MainAppContent.kt` `openYouTubeVideo` (~997).
- Secure storage reference: `MdbListAuthPersistence.android.kt` and `MdbListAuthPersistence.ios.kt`.
- Settings sheet: `SportsFollowing.kt` `SportsSettingsSheet` (~411).
