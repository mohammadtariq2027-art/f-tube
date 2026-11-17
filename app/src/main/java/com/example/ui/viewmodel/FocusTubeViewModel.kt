package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FocusTubeDatabase
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.data.local.entity.VideoEntity
import com.example.data.repository.FocusTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusTubeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FocusTubeRepository
    val accountManager: com.example.util.GoogleAccountManager
    val googleUserProfile: StateFlow<com.example.util.GoogleUserProfile>

    init {
        val db = FocusTubeDatabase.getDatabase(application, viewModelScope)
        repository = FocusTubeRepository(db.dao())
        accountManager = com.example.util.GoogleAccountManager(db.dao())
        googleUserProfile = accountManager.userProfile

        // 🔑 Restore permanent Google session (auto-login on startup) & sync playlists/channels
        viewModelScope.launch(Dispatchers.IO) {
            try {
                accountManager.restoreSession()
                val prof = accountManager.userProfile.value
                if (prof.isSignedIn || com.example.util.YouTubeSyncHelper.hasYouTubeWebSession()) {
                    repository.syncUserAccountChannelsAndPlaylists(application, prof.email, prof.youtubeOAuthToken)
                }
                if (prof.isNightlySyncEnabled && com.example.util.NightlySyncScheduler.isNightTimeOrDue()) {
                    com.example.util.NightlySyncScheduler.performNightlySync(application, repository, accountManager, viewModelScope) {
                        _statusMessage.value = it
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FocusTubeViewModel", "Session restore/nightly sync check skipped: ${e.message}")
            }
        }

        // Safely clean up duplicate video entries on startup in background
        viewModelScope.launch {
            try {
                val dupesPurged = repository.purgeDuplicateVideos()
                if (dupesPurged > 0) {
                    android.util.Log.d("FocusTubeViewModel", "Auto-cleaned $dupesPurged duplicate video(s) on startup")
                }
            } catch (e: Exception) {
                android.util.Log.w("FocusTubeViewModel", "Startup duplicate check skipped: ${e.message}")
            }
        }

        // 🔄 Automatic Backup Check & Header Timestamp Initialization on Startup:
        // If app is reinstalled or opened with an empty database, auto-restore from detected backup!
        viewModelScope.launch {
            try {
                val restored = com.example.util.AutoBackupHelper.checkAndAutoRestoreIfEmpty(application, repository)
                if (restored) {
                    _statusMessage.value = "🔄 Previous backup found! All groups, channels, and playlists have been restored."
                }
                com.example.util.AutoBackupHelper.initializeBackupStatus(application, repository)
            } catch (e: Exception) {
                android.util.Log.w("FocusTubeViewModel", "Auto-restore check skipped: ${e.message}")
            }
        }

        // PocketTube / YouTube style Automatic Sync (runs smoothly in background)
        startAutomaticSyncLoop()
    }

    val allGroups: StateFlow<List<SubscriptionGroupEntity>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedGroup: StateFlow<SubscriptionGroupEntity?> = repository.pinnedGroup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allPlaylists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedPlaylist: StateFlow<PlaylistEntity?> = repository.pinnedPlaylist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allChannels: StateFlow<List<ChannelEntity>> = repository.allChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSavedVideos: StateFlow<List<VideoEntity>> = repository.getAllVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentPlaylistIds: StateFlow<List<String>> = repository.recentPlaylistIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current playing video
    private val _currentPlayingVideo = MutableStateFlow<VideoEntity?>(null)
    val currentPlayingVideo: StateFlow<VideoEntity?> = _currentPlayingVideo.asStateFlow()

    // Loading / status message
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val lastBackupTimestamp: StateFlow<String> = com.example.util.AutoBackupHelper.lastBackupTimestampFlow
    val isBackupInProgress: StateFlow<Boolean> = com.example.util.AutoBackupHelper.isBackupInProgressFlow

    // Home Active Title in English
    val homeTitle: StateFlow<String> = combine(pinnedGroup, pinnedPlaylist) { group, playlist ->
        when {
            group != null -> "Group: ${group.name}"
            playlist != null -> "Playlist: ${playlist.title}"
            else -> "Select a Group or Playlist"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Home")

    // Home Videos: whichever is active (Group or Playlist)
    val homeVideos: StateFlow<List<VideoEntity>> = combine(pinnedGroup, pinnedPlaylist) { group, playlist ->
        Pair(group, playlist)
    }.flatMapLatest { (group, playlist) ->
        when {
            group != null -> repository.getVideosForGroup(group.id)
            playlist != null -> repository.getVideosForPlaylist(playlist.playlistId)
            else -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Pinning
    fun setPinnedGroup(groupId: Long) {
        viewModelScope.launch {
            repository.setPinnedGroup(groupId)
            _statusMessage.value = "Active Home set to selected Channel Group"
        }
    }

    fun setPinnedPlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.setPinnedPlaylist(playlistId)
            _statusMessage.value = "Active Home set to selected Playlist"
        }
    }

    // Groups & Channels Management
    fun createGroup(name: String) {
        viewModelScope.launch {
            val isFirst = allGroups.value.isEmpty() && allPlaylists.value.isEmpty()
            val newId = repository.insertGroup(name.trim())
            if (isFirst) {
                repository.setPinnedGroup(newId)
            }
            _statusMessage.value = "Created group '${name.trim()}'"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
            _statusMessage.value = "Group removed"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    /**
     * Real fetch and add channel with its YouTube videos
     */
    fun addChannelToGroup(rawInput: String, groupId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveSearchQuery(rawInput, "CHANNEL")
                val msg = repository.fetchAndAddChannelToGroup(rawInput, groupId)
                _statusMessage.value = msg
                com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
            } catch (e: IllegalArgumentException) {
                _statusMessage.value = "🛡️ Focus Shield: ${e.message}"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to fetch channel: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteChannel(channelId: Long) {
        viewModelScope.launch {
            repository.deleteChannel(channelId)
            _statusMessage.value = "Channel removed"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    fun moveChannelToGroup(channelId: Long, newGroupId: Long) {
        viewModelScope.launch {
            repository.moveChannelToGroup(channelId, newGroupId)
            _statusMessage.value = "Channel moved to new group"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    /**
     * PocketTube-style Checkbox Toggle:
     * Ticks or unticks a subscribed channel into/out of a specific Group!
     */
    fun toggleChannelInGroup(channelName: String, handleOrUrl: String, groupId: Long, assign: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val changed = repository.toggleChannelInGroup(channelName, handleOrUrl, groupId, assign)
            val grpName = allGroups.value.find { it.id == groupId }?.name ?: "Group"
            if (assign && changed) {
                _statusMessage.value = "✓ '$channelName' को '$grpName' ग्रुप में जोड़ दिया गया"
                // Fetch latest videos for this group in background
                try {
                    repository.syncGroupChannels(groupId)
                } catch (_: Exception) {}
            } else if (!assign && changed) {
                _statusMessage.value = "'$channelName' को '$grpName' ग्रुप से हटा दिया गया"
            }
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    /**
     * Fetches ALL Subscribed Channels (A-Z) and ALL Playlists (A-Z) from the user's YouTube account!
     */
    fun syncMyYouTubeChannelsAndPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                accountManager.updateYouTubeAutoSaveSession()
                val prof = googleUserProfile.value
                val (chCount, plCount) = repository.syncUserAccountChannelsAndPlaylists(
                    context = getApplication(),
                    email = prof.email,
                    oauthToken = prof.youtubeOAuthToken
                )
                _statusMessage.value = "✅ YouTube सिंक पूरा! कुल $chCount चैनल्स (A–Z) और $plCount प्लेलिस्ट (A–Z) उपलब्ध हैं।"
                com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
            } catch (e: Exception) {
                _statusMessage.value = "YouTube सिंक त्रुटि: ${e.localizedMessage ?: "पुनः प्रयास करें"}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Real fetch and add playlist with its YouTube videos
     */
    fun addPlaylist(rawInput: String, customTitle: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveSearchQuery(rawInput, "PLAYLIST")
                val msg = repository.fetchAndAddPlaylist(rawInput, customTitle)
                _statusMessage.value = msg
                com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to fetch playlist: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            _statusMessage.value = "Playlist removed"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    fun syncPlaylist(playlistId: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.syncPlaylistVideos(playlistId)
                result.fold(
                    onSuccess = { addedCount ->
                        _statusMessage.value = if (addedCount > 0) {
                            "Synced! Added $addedCount new videos from playlist."
                        } else {
                            "All playlist videos are up to date."
                        }
                        com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
                    },
                    onFailure = { e ->
                        _statusMessage.value = "Sync failed: ${e.localizedMessage ?: "Network error"}"
                    }
                )
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun syncGroup(groupId: Long) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.syncGroupChannels(groupId)
                result.fold(
                    onSuccess = { addedCount ->
                        _statusMessage.value = if (addedCount > 0) {
                            "Synced! Added $addedCount latest video(s) at the top of your feed."
                        } else {
                            "Feed refreshed: All channel videos are up to date."
                        }
                        com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
                    },
                    onFailure = { e ->
                        _statusMessage.value = "Sync failed: ${e.localizedMessage ?: "Network error"}"
                    }
                )
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Automatic Sync Loop (YouTube & PocketTube style):
     * Runs silently in the background every 5 minutes.
     * Also runs 3 seconds after app startup.
     * Fetches new videos for the active group, active playlist, and all groups
     * so that latest videos appear automatically without requiring manual button presses.
     */
    private fun startAutomaticSyncLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            // Initial auto-sync 45s after launch so app startup is instant and non-blocking
            delay(45000)
            autoSyncAllActiveContent()

            // Periodic background sync loop every 10 minutes
            while (isActive) {
                delay(10 * 60 * 1000L)
                autoSyncAllActiveContent()
            }
        }
    }

    private suspend fun autoSyncAllActiveContent() = withContext(Dispatchers.IO) {
        try {
            // Do not run background sync if the user is actively watching a video, ensuring 100% smooth playback
            if (currentPlayingVideo.value != null) {
                return@withContext
            }

            // 1. Sync only the currently pinned/active group or playlist to avoid background CPU/network stutter
            val activeGroup = pinnedGroup.value
            val activePlaylist = pinnedPlaylist.value
            if (activeGroup != null) {
                val groupResult = repository.syncGroupChannels(activeGroup.id)
                groupResult.onSuccess { added ->
                    if (added > 0) {
                        _statusMessage.value = "⚡ Auto-sync: $added new video(s) appeared in ${activeGroup.name}"
                    }
                }
            } else if (activePlaylist != null) {
                val playlistResult = repository.syncPlaylistVideos(activePlaylist.playlistId)
                playlistResult.onSuccess { added ->
                    if (added > 0) {
                        _statusMessage.value = "⚡ Auto-sync: $added new video(s) appeared in ${activePlaylist.title}"
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("FocusTubeViewModel", "Auto sync cycle skipped: ${e.message}")
        }
    }

    fun addVideoFromHome(title: String, videoUrlOrId: String) {
        val trimmedInput = videoUrlOrId.trim()
        // If user pasted a playlist link on Home, import it as a playlist
        if ((trimmedInput.contains("list=") && FocusTubeRepository.extractVideoId(trimmedInput) == null) || trimmedInput.startsWith("PL")) {
            addPlaylist(trimmedInput, title)
            return
        }
        val activeGrp = pinnedGroup.value
        val activePl = pinnedPlaylist.value
        when {
            activeGrp != null -> addVideoToGroup(activeGrp.id, title, activeGrp.name, trimmedInput)
            activePl != null -> addVideoToPlaylist(activePl.playlistId, title, activePl.title, trimmedInput)
            allPlaylists.value.isNotEmpty() -> {
                val firstPl = allPlaylists.value.first()
                setPinnedPlaylist(firstPl.playlistId)
                addVideoToPlaylist(firstPl.playlistId, title, firstPl.title, trimmedInput)
            }
            allGroups.value.isNotEmpty() -> {
                val firstGrp = allGroups.value.first()
                setPinnedGroup(firstGrp.id)
                addVideoToGroup(firstGrp.id, title, firstGrp.name, trimmedInput)
            }
            else -> {
                // Create a default study/work playlist, pin it to Home, and add the video
                viewModelScope.launch(Dispatchers.IO) {
                    val defaultPlId = "custom_home_study"
                    repository.createCustomPlaylist(defaultPlId, "Important Study & Work Videos")
                    repository.setPinnedPlaylist(defaultPlId)
                    addVideoToPlaylist(defaultPlId, title, "Important Study & Work Videos", trimmedInput)
                }
            }
        }
    }

    fun addVideoToGroup(groupId: Long, title: String, channelName: String, videoUrlOrId: String) {
        val trimmedInput = videoUrlOrId.trim()
        // If user pasted a channel handle or channel URL here, add it as a channel to the group
        if (trimmedInput.contains("/@") || trimmedInput.startsWith("@") || trimmedInput.contains("/channel/")) {
            addChannelToGroup(trimmedInput, groupId)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val id = FocusTubeRepository.extractVideoId(trimmedInput) ?: trimmedInput
            if (id.isNotBlank()) {
                var finalTitle = title.trim()
                var finalChannel = channelName.trim()
                val info = repository.fetchSingleVideoInfo(id)
                if (info != null) {
                    if (finalTitle.isBlank() || finalTitle.startsWith("Video #")) {
                        finalTitle = info.title
                    }
                    if (finalChannel.isBlank() || finalChannel == "Group Video" || finalChannel == "Channel") {
                        finalChannel = info.channelName
                    }
                }

                // Gemini AI Context Analysis: Understands true intent even if title has words like "4K", "song", "comedy", or clickbait
                val aiVerdict = com.example.util.AiContentVerifier.analyzeVideoWithAi(
                    title = finalTitle.ifBlank { id },
                    channelName = finalChannel
                )
                if (!aiVerdict.isProductive) {
                    _statusMessage.value = "🛡️ Gemini AI Shield (${aiVerdict.detectedCategory}): ${aiVerdict.reason}"
                    return@launch
                }

                val cleanDur = com.example.util.DurationUtils.formatCleanDuration("", id)
                val rowId = repository.insertVideo(
                    VideoEntity(
                        videoId = id,
                        groupId = groupId,
                        title = finalTitle.ifBlank { "Study & Work Video ($id)" },
                        channelName = finalChannel.ifBlank { "YouTube" },
                        duration = cleanDur,
                        thumbnailUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg",
                        uploadDate = "Recently added",
                        publishedTimestamp = System.currentTimeMillis()
                    )
                )
                if (rowId == -1L) {
                    _statusMessage.value = "⚠️ This video is already in this group!"
                } else {
                    _statusMessage.value = "✅ AI Verified (${aiVerdict.detectedCategory}): Added '${finalTitle.ifBlank { id }}' to group!"
                    com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
                }
            }
        }
    }

    fun addVideoToPlaylist(playlistId: String, title: String, channelName: String, videoUrlOrId: String) {
        val trimmedInput = videoUrlOrId.trim()
        viewModelScope.launch(Dispatchers.IO) {
            val id = FocusTubeRepository.extractVideoId(trimmedInput) ?: trimmedInput
            if (id.isNotBlank()) {
                val targetPlaylist = allPlaylists.value.find { it.playlistId == playlistId }
                val plName = targetPlaylist?.title ?: "Playlist"
                if (repository.checkVideoExistsInPlaylist(playlistId, id)) {
                    _statusMessage.value = "⚠️ This video is already in '$plName'!"
                    return@launch
                }

                // Fetch real video metadata
                var finalTitle = title.trim()
                var finalChannel = channelName.trim()
                val info = repository.fetchSingleVideoInfo(id)
                if (info != null) {
                    if (finalTitle.isBlank() || finalTitle.startsWith("Video #")) {
                        finalTitle = info.title
                    }
                    if (finalChannel.isBlank() || finalChannel == "Playlist" || finalChannel == "Playlist Video") {
                        finalChannel = info.channelName
                    }
                }

                // Gemini AI Context Analysis: Allows useful videos even if title mentions "4K"/"song"/"comedy", blocks disguised distractions
                val aiVerdict = com.example.util.AiContentVerifier.analyzeVideoWithAi(
                    title = finalTitle.ifBlank { id },
                    channelName = finalChannel
                )
                if (!aiVerdict.isProductive) {
                    _statusMessage.value = "🛡️ Gemini AI Shield (${aiVerdict.detectedCategory}): ${aiVerdict.reason}"
                    return@launch
                }

                val cleanDur = com.example.util.DurationUtils.formatCleanDuration("", id)
                val rowId = repository.insertVideo(
                    VideoEntity(
                        videoId = id,
                        playlistId = playlistId,
                        title = finalTitle.ifBlank { "Study & Work Video ($id)" },
                        channelName = finalChannel.ifBlank { "YouTube" },
                        duration = cleanDur,
                        thumbnailUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg",
                        uploadDate = "Recently added",
                        publishedTimestamp = System.currentTimeMillis()
                    )
                )
                if (rowId == -1L) {
                    _statusMessage.value = "⚠️ This video is already in '$plName'!"
                } else {
                    repository.markPlaylistRecentlyUsed(playlistId)
                    val profile = googleUserProfile.value
                    val syncedToYt = if (com.example.util.YouTubeSyncHelper.isRealYouTubePlaylist(playlistId)) {
                        com.example.util.YouTubeSyncHelper.autoAddVideoToYouTubePlaylist(
                            context = getApplication(),
                            playlistId = playlistId,
                            videoId = id,
                            email = profile.email,
                            oauthToken = profile.youtubeOAuthToken
                        )
                    } else false

                    if (syncedToYt) {
                        repository.updateVideoYouTubeSync(rowId, true)
                        _statusMessage.value = "✅ AI Verified (${aiVerdict.detectedCategory}): Saved to '$plName' & synced to YouTube!"
                    } else {
                        _statusMessage.value = "✅ AI Verified (${aiVerdict.detectedCategory}): Added '${finalTitle.ifBlank { id }}' to '$plName'!"
                    }
                    com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
                }
            }
        }
    }

    fun saveVideoToPlaylist(video: VideoEntity, playlistId: String, andOpenYouTube: Boolean = false, context: android.content.Context? = null) {
        viewModelScope.launch {
            val aiVerdict = withContext(Dispatchers.IO) {
                com.example.util.AiContentVerifier.analyzeVideoWithAi(
                    title = video.title,
                    channelName = video.channelName,
                    description = video.description
                )
            }
            if (!aiVerdict.isProductive) {
                _statusMessage.value = "🛡️ Gemini AI Shield (${aiVerdict.detectedCategory}): ${aiVerdict.reason}"
                return@launch
            }
            val targetPlaylist = allPlaylists.value.find { it.playlistId == playlistId }
            val plName = targetPlaylist?.title ?: "Playlist"
            if (repository.checkVideoExistsInPlaylist(playlistId, video.videoId)) {
                repository.markPlaylistRecentlyUsed(playlistId)
                _statusMessage.value = "⚠️ This video is already in '$plName'!"
                return@launch
            }
            val rowId = repository.copyVideoToPlaylist(video, playlistId)
            if (rowId == -1L) {
                repository.markPlaylistRecentlyUsed(playlistId)
                _statusMessage.value = "⚠️ This video is already in '$plName'!"
                return@launch
            }
            repository.markPlaylistRecentlyUsed(playlistId)

            val profile = googleUserProfile.value
            val isRealYtPlaylist = com.example.util.YouTubeSyncHelper.isRealYouTubePlaylist(playlistId)
            val syncedToYt = if (isRealYtPlaylist) {
                com.example.util.YouTubeSyncHelper.autoAddVideoToYouTubePlaylist(
                    context = getApplication(),
                    playlistId = playlistId,
                    videoId = video.videoId,
                    email = profile.email,
                    oauthToken = profile.youtubeOAuthToken
                )
            } else false

            if (syncedToYt) {
                repository.updateVideoYouTubeSync(rowId, true)
                _statusMessage.value = "✅ Saved '${video.title}' to '$plName' & auto-synced to YouTube!"
            } else if (isRealYtPlaylist) {
                _statusMessage.value = "✓ Saved to '$plName'! (Enable 'YouTube Auto-Save' in the Google icon to sync to YouTube)"
            } else {
                _statusMessage.value = "Saved '${video.title}' to '$plName'"
            }

            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)

            if (andOpenYouTube && context != null) {
                com.example.util.YouTubeSyncHelper.openInYouTubeApp(context, video.videoId, playlistId)
            }
        }
    }

    fun moveVideoToPlaylist(video: VideoEntity, targetPlaylistId: String) {
        if (video.playlistId == targetPlaylistId) {
            val targetPl = allPlaylists.value.find { it.playlistId == targetPlaylistId }
            _statusMessage.value = "⚠️ This video is already in '${targetPl?.title ?: "this playlist"}'"
            return
        }
        viewModelScope.launch {
            val targetPlaylist = allPlaylists.value.find { it.playlistId == targetPlaylistId }
            val plName = targetPlaylist?.title ?: "Playlist"
            val newRowId = repository.moveVideoBetweenPlaylists(video, targetPlaylistId)
            repository.markPlaylistRecentlyUsed(targetPlaylistId)

            val profile = googleUserProfile.value
            val isRealYtPlaylist = com.example.util.YouTubeSyncHelper.isRealYouTubePlaylist(targetPlaylistId)
            val syncedToYt = if (isRealYtPlaylist) {
                com.example.util.YouTubeSyncHelper.autoAddVideoToYouTubePlaylist(
                    context = getApplication(),
                    playlistId = targetPlaylistId,
                    videoId = video.videoId,
                    email = profile.email,
                    oauthToken = profile.youtubeOAuthToken
                )
            } else false

            if (syncedToYt && newRowId != -1L) {
                repository.updateVideoYouTubeSync(newRowId, true)
            }
            _statusMessage.value = "📦 Moved '${video.title}' to playlist '$plName'!"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    fun createPlaylistAndSaveVideo(title: String, video: VideoEntity, andOpenYouTube: Boolean = false, context: android.content.Context? = null) {
        viewModelScope.launch {
            val aiVerdict = withContext(Dispatchers.IO) {
                com.example.util.AiContentVerifier.analyzeVideoWithAi(
                    title = video.title,
                    channelName = video.channelName,
                    description = video.description
                )
            }
            if (!aiVerdict.isProductive) {
                _statusMessage.value = "🛡️ Gemini AI Shield (${aiVerdict.detectedCategory}): ${aiVerdict.reason}"
                return@launch
            }
            val cleanTitle = title.trim().ifBlank { "My Saved Videos" }
            val profile = googleUserProfile.value
            val createdYtPlaylistId = com.example.util.YouTubeSyncHelper.autoCreateYouTubePlaylist(
                context = getApplication(),
                title = cleanTitle,
                videoId = video.videoId,
                email = profile.email,
                oauthToken = profile.youtubeOAuthToken
            )
            val finalPlaylistId = createdYtPlaylistId ?: ("custom_pl_" + System.currentTimeMillis())
            repository.createCustomPlaylist(finalPlaylistId, cleanTitle)
            val rowId = repository.copyVideoToPlaylist(video, finalPlaylistId)
            if (createdYtPlaylistId != null && rowId != -1L) {
                repository.updateVideoYouTubeSync(rowId, true)
            }
            repository.markPlaylistRecentlyUsed(finalPlaylistId)
            _statusMessage.value = if (createdYtPlaylistId != null) {
                "✅ Created playlist '$cleanTitle' in FocusTube & YouTube and saved video!"
            } else {
                "Created '$cleanTitle' and saved video"
            }
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)

            if (andOpenYouTube && context != null) {
                com.example.util.YouTubeSyncHelper.openInYouTubeApp(context, video.videoId, finalPlaylistId)
            }
        }
    }

    fun purgeVideoIfDistraction(videoId: Long, title: String, channelName: String = "") {
        viewModelScope.launch {
            if (repository.purgeSingleVideoIfDistraction(videoId, title, channelName)) {
                _statusMessage.value = "🛡️ Focus Shield: Distraction video purged immediately"
                com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
            }
        }
    }

    fun markVideoSynced(videoId: Long, synced: Boolean = true) {
        viewModelScope.launch {
            repository.updateVideoYouTubeSync(videoId, synced)
            _statusMessage.value = if (synced) "Marked as synced with YouTube" else "Sync status reset"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    fun markAllVideosInPlaylistSynced(playlistId: String) {
        viewModelScope.launch {
            repository.markAllPlaylistVideosSynced(playlistId)
            _statusMessage.value = "All videos in playlist marked as synced"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    /**
     * 1-Click Batch YouTube Sync:
     * Opens all video IDs together as a single batch queue in the official YouTube app,
     * allowing the user to save all videos into their YouTube playlist with 1 click on the '+' icon!
     */
    fun syncBatchToYouTube(context: android.content.Context, playlistId: String, videoIds: List<String>) {
        if (videoIds.isEmpty()) {
            _statusMessage.value = "No videos to sync"
            return
        }
        val opened = com.example.util.YouTubeSyncHelper.openBatchInYouTubeApp(context, videoIds)
        if (opened) {
            viewModelScope.launch {
                repository.markAllPlaylistVideosSynced(playlistId)
                _statusMessage.value = "⚡ YouTube App opened! Tap '+' (Save) at the top to save all videos in 1 click."
                com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
            }
        }
    }

    fun deleteVideo(videoId: Long) {
        viewModelScope.launch {
            repository.deleteVideo(videoId)
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
        }
    }

    fun toggleCompleted(video: VideoEntity) {
        viewModelScope.launch {
            repository.toggleVideoCompleted(video.id, !video.isCompleted)
            if (_currentPlayingVideo.value?.id == video.id) {
                _currentPlayingVideo.value = video.copy(isCompleted = !video.isCompleted)
            }
        }
    }

    fun updateVideoNotes(videoId: Long, notes: String) {
        viewModelScope.launch {
            repository.updateVideoNotes(videoId, notes)
            if (_currentPlayingVideo.value?.id == videoId) {
                _currentPlayingVideo.value = _currentPlayingVideo.value?.copy(notes = notes)
            }
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
            _statusMessage.value = "✓ Note linked to this video, saved, and backed up!"
        }
    }

    suspend fun fetchVideoDescription(videoId: String): String {
        return repository.fetchVideoDescription(videoId)
    }

    // Playback
    private val _lastPlayedVideoId = MutableStateFlow<String?>(null)
    val lastPlayedVideoId: StateFlow<String?> = _lastPlayedVideoId.asStateFlow()

    private val _initialSeekSeconds = MutableStateFlow<Int?>(null)
    val initialSeekSeconds: StateFlow<Int?> = _initialSeekSeconds.asStateFlow()

    fun playVideo(video: VideoEntity, startAtSeconds: Int? = null) {
        _lastPlayedVideoId.value = video.videoId
        _initialSeekSeconds.value = startAtSeconds
        _currentPlayingVideo.value = video
    }

    fun closePlayer() {
        _currentPlayingVideo.value = null
        _initialSeekSeconds.value = null
    }

    fun playNextVideo() {
        val current = _currentPlayingVideo.value ?: return
        val list = homeVideos.value
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex in 0 until list.lastIndex) {
            _currentPlayingVideo.value = list[currentIndex + 1]
        }
    }

    fun playPreviousVideo() {
        val current = _currentPlayingVideo.value ?: return
        val list = homeVideos.value
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            _currentPlayingVideo.value = list[currentIndex - 1]
        }
    }

    fun getChannelsForGroup(groupId: Long) = repository.getChannelsForGroup(groupId)
    fun getVideosForGroup(groupId: Long) = repository.getVideosForGroup(groupId)
    fun getVideosForPlaylist(playlistId: String) = repository.getVideosForPlaylist(playlistId)

    // Search History
    fun getSearchHistory(type: String) = repository.getSearchHistory(type)

    fun deleteSearchHistoryItem(query: String, type: String) {
        viewModelScope.launch {
            repository.deleteSearchHistoryItem(query, type)
        }
    }

    fun clearSearchHistory(type: String) {
        viewModelScope.launch {
            repository.clearSearchHistory(type)
        }
    }

    // Export & Import Backup
    suspend fun exportDataJson(): String {
        return repository.exportBackupJson()
    }

    fun importDataJson(jsonString: String, replaceExisting: Boolean = false, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.importBackupJson(jsonString, replaceExisting)
            _isLoading.value = false
            result.onSuccess { count ->
                _statusMessage.value = "Successfully imported backup ($count items restored)"
                onComplete(true, "Restored $count items successfully!")
            }.onFailure { err ->
                _statusMessage.value = "Import failed: ${err.localizedMessage ?: "Invalid file"}"
                onComplete(false, err.localizedMessage ?: "Import failed")
            }
        }
    }

    // === Google Account & Google Drive Nightly Sync Management ===

    fun onGoogleAccountPicked(email: String) {
        viewModelScope.launch {
            accountManager.saveUserProfile(email)
            _statusMessage.value = "✓ Connected Google Account: $email"
            com.example.util.NightlySyncScheduler.performNightlySync(getApplication(), repository, accountManager, viewModelScope)
        }
    }

    fun onYouTubeSessionConnected(oauthToken: String = "") {
        viewModelScope.launch {
            accountManager.updateYouTubeAutoSaveSession(oauthToken)
            _statusMessage.value = "✅ YouTube Playlist Auto-Save enabled! Syncing unsaved videos to YouTube..."
            com.example.util.NightlySyncScheduler.performNightlySync(getApplication(), repository, accountManager, viewModelScope) { msg ->
                _statusMessage.value = msg
            }
        }
    }

    fun linkGoogleDriveBackupFile(uri: android.net.Uri) {
        viewModelScope.launch {
            accountManager.saveGoogleDriveBackupUri(getApplication(), uri)
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository, uri.toString())
            _statusMessage.value = "☁️ Google Drive auto-backup file linked! All videos & notes saved."
        }
    }

    fun restoreFromGoogleDriveBackupFile(uri: android.net.Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                accountManager.saveGoogleDriveBackupUri(getApplication(), uri)
                val result = com.example.util.AutoBackupHelper.restoreFromGoogleDriveUri(getApplication(), repository, uri)
                result.fold(
                    onSuccess = { count ->
                        _statusMessage.value = "✓ Restored $count items (groups, playlists & notes) from Google Drive!"
                    },
                    onFailure = { err ->
                        _statusMessage.value = "Google Drive restore failed: ${err.localizedMessage ?: "Invalid file"}"
                    }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            accountManager.signOut()
            _statusMessage.value = "Signed out of Google Account"
        }
    }

    fun toggleNightlySync(enabled: Boolean) {
        viewModelScope.launch {
            accountManager.setNightlySync(enabled)
            _statusMessage.value = if (enabled) "🌙 Nightly Auto-Sync enabled" else "Nightly Auto-Sync disabled"
        }
    }

    fun syncAllNowWithAccount() {
        viewModelScope.launch {
            _isSyncing.value = true
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
            com.example.util.NightlySyncScheduler.performNightlySync(getApplication(), repository, accountManager, viewModelScope) {
                _statusMessage.value = it
                _isSyncing.value = false
            }
        }
    }
}
