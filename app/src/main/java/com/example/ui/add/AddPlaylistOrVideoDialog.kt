package com.example.ui.add

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.SearchHistoryEntity
import com.example.ui.components.SearchHistoryChips

@Composable
fun AddPlaylistDialog(
    searchHistory: List<SearchHistoryEntity> = emptyList(),
    onDeleteSearchHistoryItem: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var playlistUrlOrId by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add YouTube Playlist")
        },
        text = {
            Column {
                Text(
                    text = "Paste any YouTube Playlist URL (public or unlisted). The app will automatically fetch the playlist's videos.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = playlistUrlOrId,
                    onValueChange = { playlistUrlOrId = it },
                    label = { Text("Playlist Link or ID *") },
                    placeholder = { Text("https://www.youtube.com/playlist?list=PL...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (searchHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SearchHistoryChips(
                        historyList = searchHistory,
                        onSelectQuery = { playlistUrlOrId = it },
                        onDeleteItem = onDeleteSearchHistoryItem,
                        onClearAll = onClearSearchHistory
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Custom Title (Optional)") },
                    placeholder = { Text("Leave blank to use YouTube title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (playlistUrlOrId.isNotBlank()) {
                        onConfirm(playlistUrlOrId.trim(), title.trim())
                    }
                },
                enabled = playlistUrlOrId.isNotBlank()
            ) {
                Text("Fetch & Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddVideoGenericDialog(
    titlePrefix: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var videoUrlOrId by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Video to $titlePrefix")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = videoUrlOrId,
                    onValueChange = { videoUrlOrId = it },
                    label = { Text("YouTube Video Link or 11-char ID *") },
                    placeholder = { Text("https://youtu.be/... or video ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Video Title (Optional)") },
                    placeholder = { Text("e.g. Chapter 1: Introduction") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (videoUrlOrId.isNotBlank()) {
                        onConfirm(videoUrlOrId.trim(), title.trim())
                    }
                },
                enabled = videoUrlOrId.isNotBlank()
            ) {
                Text("Add Video")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
