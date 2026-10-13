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
import kotlinx.coroutines.launch

class FocusTubeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FocusTubeRepository

    init {
        val db = FocusTubeDatabase.getDatabase(application, viewModelScope)
        repository = FocusTubeRepository(db.dao())
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

    // Direct Video Adding
    fun addVideoToGroup(groupId: Long, title: String, channelName: String, videoUrlOrId: String) {
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
                        thumbnailUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg"
                    )
                )
                _statusMessage.value = "Video added to group"
            }
        }
    }

    fun addVideoToPlaylist(playlistId: String, title: String, channelName: String, videoUrlOrId: String) {
        viewModelScope.launch {
            val id = FocusTubeRepository.extractVideoId(videoUrlOrId) ?: videoUrlOrId.trim()
            if (id.isNotBlank()) {
                val cleanDur = com.example.util.DurationUtils.formatCleanDuration("", id)
                repository.insertVideo(
                    VideoEntity(
                        videoId = id,
                        playlistId = playlistId,
                        title = title.ifBlank { "Video #$id" },
                        channelName = channelName.ifBlank { "Playlist" },
                        duration = cleanDur,
                        thumbnailUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg"
                    )
                )
                _statusMessage.value = "Video added to playlist"
            }
        }
    }

    fun saveVideoToPlaylist(video: VideoEntity, playlistId: String) {
        viewModelScope.launch {
            val targetPlaylist = allPlaylists.value.find { it.playlistId == playlistId }
            repository.copyVideoToPlaylist(video, playlistId)
            _statusMessage.value = "Saved '${video.title}' to '${targetPlaylist?.title ?: "Playlist"}'"
        }
    }

    fun createPlaylistAndSaveVideo(title: String, video: VideoEntity) {
        viewModelScope.launch {
            val cleanTitle = title.trim().ifBlank { "My Saved Videos" }
            val generatedId = "custom_pl_" + System.currentTimeMillis()
            repository.createCustomPlaylist(generatedId, cleanTitle)
            repository.copyVideoToPlaylist(video, generatedId)
            _statusMessage.value = "Created '$cleanTitle' and saved video"
        }
    }

    fun deleteVideo(videoId: Long) {
        viewModelScope.launch {
            repository.deleteVideo(videoId)
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
    fun playVideo(video: VideoEntity) {
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
