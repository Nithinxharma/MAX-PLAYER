package xyz.mpv.rex.ui.browser.cinehub

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.cinehub.download.ActiveDownloadTask
import xyz.mpv.rex.cinehub.download.CineDownloadManager
import xyz.mpv.rex.cinehub.download.DownloadedVideoItem
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.cinehub.components.MaxStreamDownloadsSection
import xyz.mpv.rex.utils.media.MediaUtils

object CineDownloadManagerScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val isDark = isSystemInDarkTheme()

        var activeTasks by remember { mutableStateOf<List<ActiveDownloadTask>>(emptyList()) }
        var completedVideos by remember { mutableStateOf<List<DownloadedVideoItem>>(emptyList()) }

        fun refreshDownloads() {
            scope.launch(Dispatchers.IO) {
                val tasks = CineDownloadManager.getActiveDownloads(context)
                val list = CineDownloadManager.getDownloadedVideos(context)
                withContext(Dispatchers.Main) {
                    activeTasks = tasks
                    completedVideos = list
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshDownloads()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) Color(0xFF0D0E12) else MaterialTheme.colorScheme.background)
                .padding(top = 16.dp)
                .testTag("cine_download_manager_screen")
        ) {
            MaxStreamDownloadsSection(
                activeTasks = activeTasks,
                completedVideos = completedVideos,
                modifier = Modifier.fillMaxSize(),
                onPlayVideo = { video ->
                    MediaUtils.playFile(
                        source = video.file.absolutePath,
                        context = context,
                        launchSource = "downloads",
                        title = video.name
                    )
                },
                onDeleteVideo = { video ->
                    scope.launch(Dispatchers.IO) {
                        CineDownloadManager.deleteDownloadedVideo(video.file)
                        refreshDownloads()
                    }
                },
                onCancelTask = { task ->
                    CineDownloadManager.cancelDownload(context, task.id)
                    refreshDownloads()
                }
            )
        }
    }
}
