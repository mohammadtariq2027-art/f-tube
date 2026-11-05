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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusTubeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FocusTubeRepository

    init {
        val db = FocusTubeDatabase.getDatabase(application, viewModelScope)
        repository = FocusTubeRepository(db.dao())

        // Real-time instant purge of any existing duplicate copies & distractions from database
        viewModelScope.launch {
            val dupesPurged = repository.purgeDuplicateVideos()
            if (dupesPurged > 0) {
                android.util.Log.d("FocusTubeViewModel", "Auto-cleaned $dupesPurged duplicate video(s) on startup")
            }
            val purged = repository.purgeAllDistractions()
            if (purged > 0) {
                android.util.Log.d("FocusTubeViewModel", "Purged $purged distraction video(s) on startup")
            }
        }

        // PocketTube / YouTube style Automatic Sync:
        // Automatically checks and fetches new videos for channels and playlists
        // without requiring the user to press the sync button manually.
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

    val allSavedVideos: StateFlow<List<VideoEntity>> = repository.getAllVideos()
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
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
            _statusMessage.value = "Group removed"
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
        }
    }

    fun moveChannelToGroup(channelId: Long, newGroupId: Long) {
        viewModelScope.launch {
            repository.moveChannelToGroup(channelId, newGroupId)
            _statusMessage.value = "Channel moved to new group"
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
        viewModelScope.launch {
            // Initial auto-sync shortly after launch to populate any new uploads
            delay(3000)
            autoSyncAllActiveContent()

            // Periodic background sync loop every 5 minutes
            while (isActive) {
                delay(5 * 60 * 1000L) // 5 minutes interval
                autoSyncAllActiveContent()
            }
        }
    }

    private suspend fun autoSyncAllActiveContent() {
        try {
            // Do not run background sync if the user is actively watching a video, ensuring 100% smooth playback
            if (currentPlayingVideo.value != null) {
                return
            }

            // 1. Sync pinned/active group first
            val activeGroup = pinnedGroup.value ?: allGroups.value.firstOrNull()
            if (activeGroup != null) {
                val groupResult = repository.syncGroupChannels(activeGroup.id)
                groupResult.onSuccess { added ->
                    if (added > 0) {
                        _statusMessage.value = "⚡ Auto-sync: $added new video(s) appeared in ${activeGroup.name}"
                    }
                }
            }

            // 2. Sync pinned playlist if any
            val activePlaylist = pinnedPlaylist.value
            if (activePlaylist != null) {
                val playlistResult = repository.syncPlaylistVideos(activePlaylist.playlistId)
                playlistResult.onSuccess { added ->
                    if (added > 0) {
                        _statusMessage.value = "⚡ Auto-sync: $added new video(s) appeared in ${activePlaylist.title}"
                    }
                }
            }

            // 3. Silently sync any other groups in background
            val otherGroups = allGroups.value.filter { it.id != activeGroup?.id }
            for (other in otherGroups) {
                repository.syncGroupChannels(other.id)
            }
        } catch (e: Exception) {
            android.util.Log.d("FocusTubeViewModel", "Auto sync cycle skipped: ${e.message}")
        }
    }

    fun addVideoToGroup(groupId: Long, title: String, channelName: String, videoUrlOrId: String) {
        if (title.isNotBlank() && com.example.util.FocusShield.isDistractionVideo(title)) {
            _statusMessage.value = "🛡️ Focus Shield: Video blocked (matches movies, songs, comedy, roasting, gaming, or masala news)"
            return
        }
        viewModelScope.launch {
            val id = FocusTubeRepository.extractVideoId(videoUrlOrId) ?: videoUrlOrId.trim()
            if (id.isNotBlank()) {
                val cleanDur = com.example.util.DurationUtils.formatCleanDuration("", id)
                repository.insertVideo(
                    VideoEntity(
                        videoId = id,
                        groupId = groupId,
                        title = title.ifBlank { "Video #$id" },
                        channelName = channelName.ifBlank { "Channel" },
                        duration = cleanDur,
                        thumbnailUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg",
                        uploadDate = "Recently added",
                        publishedTimestamp = System.currentTimeMillis()
                    )
                )
                _statusMessage.value = "Video added to group"
            }
        }
    }

    fun addVideoToPlaylist(playlistId: String, title: String, channelName: String, videoUrlOrId: String) {
        if (title.isNotBlank() && com.example.util.FocusShield.isDistractionVideo(title, channelName)) {
            _statusMessage.value = "🛡️ Focus Shield: Video blocked (matches music, movie, comedy, roasting, gaming, or gossip)"
            return
        }
        viewModelScope.launch {
            val id = FocusTubeRepository.extractVideoId(videoUrlOrId) ?: videoUrlOrId.trim()
            if (id.isNotBlank()) {
                val targetPlaylist = allPlaylists.value.find { it.playlistId == playlistId }
                val plName = targetPlaylist?.title ?: "Playlist"
                if (repository.checkVideoExistsInPlaylist(playlistId, id)) {
                    _statusMessage.value = "⚠️ यह वीडियो '$plName' में पहले से मौजूद है! (Already added)"
                    return@launch
                }

                // Fetch real video metadata to prevent sneaky music videos with blank/fake titles
                var finalTitle = title.trim()
                var finalChannel = channelName.trim()
                val info = repository.fetchSingleVideoInfo(id)
                if (info != null) {
                    if (finalTitle.isBlank() || finalTitle.startsWith("Video #")) {
                        finalTitle = info.title
                    }
                    if (finalChannel.isBlank() || finalChannel == "Playlist") {
                        finalChannel = info.channelName
                    }
                }

                // Strict validation against music and distractions
                if (com.example.util.FocusShield.isDistractionVideo(finalTitle.ifBlank { "Video #$id" }, finalChannel)) {
                    _statusMessage.value = "🛡️ Focus Shield: Video blocked (matches music, movie, comedy, or entertainment)"
                    return@launch
                }

                val cleanDur = com.example.util.DurationUtils.formatCleanDuration("", id)
                val rowId = repository.insertVideo(
                    VideoEntity(
                        videoId = id,
                        playlistId = playlistId,
                        title = finalTitle.ifBlank { "Video #$id" },
                        channelName = finalChannel.ifBlank { "Playlist" },
                        duration = cleanDur,
                        thumbnailUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg",
                        uploadDate = "Recently added",
                        publishedTimestamp = System.currentTimeMillis()
                    )
                )
                if (rowId == -1L) {
                    _statusMessage.value = "⚠️ यह वीडियो '$plName' में पहले से मौजूद है! (Already added)"
                } else {
                    _statusMessage.value = "Video added to playlist"
                    com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)
                }
            }
        }
    }

    fun saveVideoToPlaylist(video: VideoEntity, playlistId: String, andOpenYouTube: Boolean = false, context: android.content.Context? = null) {
        if (com.example.util.FocusShield.isDistractionVideo(video.title, video.channelName)) {
            _statusMessage.value = "🛡️ Focus Shield: Cannot save music or distraction video"
            return
        }
        viewModelScope.launch {
            val targetPlaylist = allPlaylists.value.find { it.playlistId == playlistId }
            val plName = targetPlaylist?.title ?: "Playlist"
            if (repository.checkVideoExistsInPlaylist(playlistId, video.videoId)) {
                _statusMessage.value = "⚠️ यह वीडियो '$plName' में पहले से मौजूद है! (Already added)"
                return@launch
            }
            val result = repository.copyVideoToPlaylist(video, playlistId)
            if (result == -1L) {
                _statusMessage.value = "⚠️ यह वीडियो '$plName' में पहले से मौजूद है! (Already added)"
                return@launch
            }
            _statusMessage.value = "Saved '${video.title}' to '$plName'"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)

            if (andOpenYouTube && context != null) {
                com.example.util.YouTubeSyncHelper.openInYouTubeApp(context, video.videoId, playlistId)
            }
        }
    }

    fun createPlaylistAndSaveVideo(title: String, video: VideoEntity, andOpenYouTube: Boolean = false, context: android.content.Context? = null) {
        if (com.example.util.FocusShield.isDistractionVideo(video.title, video.channelName)) {
            _statusMessage.value = "🛡️ Focus Shield: Cannot save music or distraction video"
            return
        }
        viewModelScope.launch {
            val cleanTitle = title.trim().ifBlank { "My Saved Videos" }
            val generatedId = "custom_pl_" + System.currentTimeMillis()
            repository.createCustomPlaylist(generatedId, cleanTitle)
            repository.copyVideoToPlaylist(video, generatedId)
            _statusMessage.value = "Created '$cleanTitle' and saved video"
            com.example.util.AutoBackupHelper.triggerAutoBackup(getApplication(), repository)

            if (andOpenYouTube && context != null) {
                com.example.util.YouTubeSyncHelper.openInYouTubeApp(context, video.videoId, generatedId)
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
                _statusMessage.value = "⚡ YouTube ऐप खुला! ऊपर '+' (Save) दबाकर 1 क्लिक में अपनी प्लेलिस्ट में जोड़ें।"
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
            _statusMessage.value = "Notes saved"
        }
    }

    suspend fun fetchVideoDescription(videoId: String): String {
        return repository.fetchVideoDescription(videoId)
    }

    // Playback
    private val _lastPlayedVideoId = MutableStateFlow<String?>(null)
    val lastPlayedVideoId: StateFlow<String?> = _lastPlayedVideoId.asStateFlow()

    fun playVideo(video: VideoEntity) {
        _lastPlayedVideoId.value = video.videoId
        _currentPlayingVideo.value = video
    }

    fun closePlayer() {
        _currentPlayingVideo.value = null
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
}
