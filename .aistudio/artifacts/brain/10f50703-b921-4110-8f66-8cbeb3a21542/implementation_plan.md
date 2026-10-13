# FocusTube UI Polish, Video Notes & Duration Fix Plan

Distraction-free YouTube feed and playlist manager update to eliminate the "HD" thumbnail badge, display real video runtime/duration on thumbnails and metadata, attach personal notes to videos, eliminate YouTube suggestion overlays, fix overlapping player controls, introduce global search across all saved videos, and redesign video cards.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The plan has been updated with your latest feedback:
> 1. **Complete Elimination of "HD" Badge**:
>    - The "HD" (High Definition) tag on video thumbnails is **completely removed** across all screens (`HomeScreen`, `PlaylistDetailSheet`, and `FocusPlayerView`).
>    - In its place, the actual **video duration/time** (e.g., `12:45`, `04:18`, `1:02:30`) will be displayed in the thumbnail time badge overlay.
>    - `YouTubeDataFetcher` will parse and format the real runtime duration from YouTube metadata (ISO 8601 duration `PT#M#S`, `lengthSeconds`, or JSON `lengthText`).
> 2. **Personal Video Notes (वीडियो नोट्स)**:
>    - Directly attach personal notes, takeaways, or timestamps to any video while watching it in the player. Notes are saved permanently in the local Room database and are also searchable from the global search bar.
> 3. **Player Control Overlap Fix**:
>    - Resolve the collision between the player top header and video action buttons by using disciplined status bar insets (`WindowInsets.statusBars`) and clean vertical scroll flow.
> 4. **Global Local Search**:
>    - Search across **all saved groups, playlists, and channels** in your local database with instant live filtering and direct playback.
> 5. **Video Item Card Presentation**:
>    - Add-to-Playlist button and watched checkmark removed from list cards to eliminate clutter.
>    - Thumbnails enlarged to 145.dp with 16:9 aspect ratio, and video titles displayed in full without 3-line clipping.
>    - Dedicated metadata row displaying `[Channel Name] • [⏱ Runtime Duration]`.

---

### 1. Overview & Core Concept

FocusTube is built for intentional, distraction-free YouTube study and entertainment without algorithmic rabbit holes. This update delivers:
- **Accurate Video Duration (No "HD" or "Video" placeholders)**: Real runtime timestamps (e.g. `14:25`) displayed both directly on the thumbnail badge overlay and beside the channel name.
- **Personal Video Notes (वीडियो नोट्स)**: Jot down personal reflections, timestamps, code snippets, or study notes while watching any video. The note is permanently attached to the video.
- **Zero Distractions**: Ensuring YouTube's end-screens, pause overlays, and related video suggestions never appear.
- **Clean Layout**: Eliminating header and action button overlaps in portrait and fullscreen modes.
- **Unified Global Search**: Allowing users to search any saved video across all playlists and channel groups from a single fast search bar.
- **High-Information Video Cards**: Making list items easier to scan with larger thumbnails, full unclipped titles, and accurate runtime duration.

---

### 2. User Experience & Visual Design

#### A. Thumbnail Duration Badge (Replacing "HD")
1. **Thumbnail Time Badge**:
   - The badge on the bottom-right of the video thumbnail will now **strictly display the video runtime** (e.g. `08:42`, `24:15`, `1:15:30`).
   - The fallback string `"HD"` is completely removed.
   - If duration is still being resolved for an older entry, it displays a neat duration placeholder (e.g., `--:--`) while asynchronously updating from YouTube metadata.
2. **Metadata Row**:
   - `[Channel Name] • [⏱ 12:45]` rendered in readable, subtle secondary typography below the full video title.

#### B. Video-Attached Notes (वीडियो नोट्स)
1. **Interactive Notes Card in Player Screen**:
   - Located directly below the video action buttons in `FocusPlayerView`.
   - Displays existing notes formatted cleanly with an "Edit Notes" / "Add Note" button.
   - If empty, displays a welcoming call-to-action: *"Attach personal notes, key takeaways, or timestamps to this video"*.
2. **Notes Editor Dialog**:
   - A distraction-free editing sheet or dialog with quick formatting helpers (e.g., "Add Timestamp" button).
   - Instant local saving into Room database upon typing or tapping Save.
   - Searchable from the search bar so you can find videos by what you wrote in your notes!

#### C. Video Player Experience (Fixing Overlaps & Suggestions)
1. **Isolated Video Frame**:
   - The embedded player maintains a pure 16:9 container with strict CSS distraction purging (`.ytp-pause-overlay`, `.ytp-endscreen-content`, `.ytp-suggestion-set`, etc.).
   - Sandboxed iframe with `rel=0&modestbranding=1&iv_load_policy=3` and automatic distraction shield on playback completion.
2. **Top Bar & Action Buttons Safe Separation**:
   - TopAppBar is assigned dedicated status bar insets and standard height (56.dp).
   - Video actions (`Full Screen`, `Reload`, `Mark Done`, `Save to Playlist`) reside in the scrollable content container with explicit vertical padding, ensuring they never overlap or collide with the top header or video frame.

#### D. Global Local Search Screen & Bar
1. **Top Search Bar**:
   - A unified search interface on the Home screen that searches across all stored videos in the Room SQLite database.
   - Live query matching against video titles, channel names, descriptions, and user notes.
2. **Search Results with Context Badges**:
   - Each search result card displays the playlist or channel group it belongs to (e.g., `[Coding Group]`, `[Flutter Tutorials]`), allowing users to immediately know where a video is stored and tap to play instantly.

#### E. Redesigned Video List Cards
1. **Enlarged Thumbnail**:
   - Increased to 145.dp width with 16:9 aspect ratio, smooth rounded corners (12.dp), and thumbnail duration badge.
2. **Full Title & Channel Metadata**:
   - Video title is rendered in `MaterialTheme.typography.titleSmall` with `fontWeight = FontWeight.SemiBold` and allowed to wrap across all lines (`maxLines = Int.MAX_VALUE`, no ellipsis cut-off).
   - Watched checkmark and Add-to-Playlist icon buttons are completely removed from list items, reserving list space for reading the title.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Removal of "HD" Label and Duration Extraction**
  - *Chosen Approach*: Replace all instances of `"HD"` on thumbnail badges with the parsed video duration string (`M:SS` or `H:MM:SS`). Update `YouTubeDataFetcher` to parse ISO 8601 (`PT#M#S`), `lengthSeconds`, or JSON `lengthText`.
  - *Why*: The user highlighted that "HD" is confusing and should be the video's actual duration.
- **Decision 2: Video Notes Storage Model**
  - *Chosen Approach*: Store notes directly in `VideoEntity.notes: String` in Room database, updating instantly via Room DAO.
  - *Why*: Fast, offline-first, completely private on-device, and automatically persists with the video.
- **Decision 3: Global vs Local Search Scope**
  - *Chosen Approach*: Search across all stored videos in the database rather than just the currently pinned playlist/group.
  - *Why*: Users often save videos in different playlists and need to find a specific video quickly.
- **Decision 4: Removal of List-Level Action Buttons**
  - *Chosen Approach*: Remove the watched checkmark and add-to-playlist icon buttons from list cards. Save to Playlist and Mark Completed remain accessible inside the player view.
  - *Why*: Gives 100% of horizontal card space to the video title and thumbnail.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                       HomeScreen / UI                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Global Search Bar (Live Room DB Query across all feeds)│  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Enlarged Video Cards:                                 │  │
│  │ [145dp Thumb + Time Badge (12:45)] [Full Title (All) ]│  │
│  │                                    [Channel • ⏱ 12:45] │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                     FocusPlayerView                         │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ TopAppBar (Explicit Status Bar Insets - No Collisions)│  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ 16:9 ZeroSuggestionPlayer (DOM Purge + Replay Shield) │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ Scrollable Actions Column:                            │  │
│  │ [Full Screen] [Reload]                                │  │
│  │ [Mark Done]   [Save to Playlist]                      │  │
│  │ [Personal Video Notes Card & Editor (New!)]           │  │
│  │ [Expandable Video Description]                        │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│           FocusTubeDao / YouTubeDataFetcher (Room DB)       │
│  - updateVideoNotes(videoId: Long, notes: String)           │
│  - searchAllVideos(query: String): Flow<List<VideoEntity>>  │
│  - Real duration extraction (PT#M#S / lengthSeconds / regex)│
│  - Migration / Default value for notes in Room DB           │
└─────────────────────────────────────────────────────────────┘
```

#### State & Implementation Touchpoints
- **`Entities.kt`**: Add `notes: String = ""` to `VideoEntity`.
- **`FocusTubeDao.kt`**: Add `@Query("UPDATE videos SET notes = :notes WHERE id = :id")` and global search query matching title, channel, and notes.
- **`YouTubeDataFetcher.kt`**: Parse ISO 8601 duration (`PT#M#S`), `lengthSeconds`, or HTML `lengthText` into formatted time string (`M:SS` or `H:MM:SS`), replacing any `"Video"` or fallback.
- **`HomeScreen.kt` & `PlaylistDetailSheet.kt`**: 
  - Remove `"HD"` text completely from thumbnail badges; display the computed video duration.
  - Render enlarged 145dp thumbnails, full unclipped titles, and `[Channel Name] • [⏱ Duration]` metadata row.
- **`FocusPlayerView.kt`**: Add Video Notes card with an interactive editor dialog, fix top header & action button vertical spacing, and reinforce zero-suggestion player styling.
