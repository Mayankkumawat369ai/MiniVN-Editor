package com.mayank.minivn

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream

class MainActivity : ComponentActivity() {
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MiniVNApp()
            }
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniVNApp() {
    val context = LocalContext.current

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var isMuted by remember { mutableStateOf(false) }

    var videoDurationMs by remember { mutableStateOf(10000L) }
    var currentPosMs by remember { mutableStateOf(0L) }
    var trimRange by remember { mutableStateOf(0f..10000f) }
    var isPlaying by remember { mutableStateOf(false) }

    var isExporting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("वीडियो चुनने के लिए नीचे Video पर टैप करें") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && exoPlayer.duration > 0) {
                    val dur = exoPlayer.duration
                    videoDurationMs = dur
                    trimRange = 0f..dur.toFloat()
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying, trimRange) {
        while (isPlaying) {
            currentPosMs = exoPlayer.currentPosition
            if (currentPosMs >= trimRange.endInclusive.toLong()) {
                exoPlayer.seekTo(trimRange.start.toLong())
                exoPlayer.pause()
            }
            delay(150)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            videoUri = uri
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            statusText = "वीडियो लोड हो गया!"
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            audioUri = uri
            statusText = "म्यूज़िक जुड़ गया! Export पर सेव होगा।"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. VN-Style Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MiniVN",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Original",
                color = Color.LightGray,
                fontSize = 13.sp,
                modifier = Modifier
                    .border(1.dp, Color(0xFF3A3A40), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            Button(
                onClick = {
                    val vUri = videoUri
                    if (vUri == null) {
                        Toast.makeText(context, "पहले कोई वीडियो चुनें", Toast.LENGTH_SHORT).show()
                    } else if (!isExporting) {
                        exoPlayer.pause()
                        isExporting = true
                        statusText = "वीडियो Export हो रहा है..."
                        exportVideo(
                            context = context,
                            videoUri = vUri,
                            audioUri = audioUri,
                            startMs = trimRange.start.toLong(),
                            endMs = trimRange.endInclusive.toLong(),
                            muteOriginal = isMuted,
                            onSuccess = {
                                isExporting = false
                                statusText = "सेव हो गया! Gallery > Movies > MiniVN"
                                Toast.makeText(context, "वीडियो गैलरी में सेव हो गया!", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isExporting = false
                                statusText = "Error: $err"
                                Toast.makeText(context, "Export फेल: $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = "Export", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isExporting) "..." else "Export", fontSize = 13.sp)
            }
        }

        // 2. Video Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (videoUri != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("कोई वीडियो नहीं चुना गया", color = Color.Gray, fontSize = 14.sp)
                }
            }

            if (isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAA000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF2979FF))
                        Spacer(Modifier.height(10.dp))
                        Text("MP4 वीडियो बन रहा है...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // 3. Playback Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151518))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatTime(currentPosMs)} / ${formatTime(videoDurationMs)}",
                color = Color.LightGray,
                fontSize = 12.sp
            )

            IconButton(
                onClick = {
                    if (videoUri != null) {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.currentPosition < trimRange.start.toLong() ||
                                exoPlayer.currentPosition >= trimRange.endInclusive.toLong()
                            ) {
                                exoPlayer.seekTo(trimRange.start.toLong())
                            }
                            exoPlayer.play()
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = statusText,
                color = Color(0xFF8AB4F8),
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        // 4. VN-Style Timeline Tracks
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121215))
                .padding(12.dp)
        ) {
            // Music Track
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222228))
                    .clickable { audioPicker.launch(arrayOf("audio/*")) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = "Music",
                    tint = if (audioUri != null) Color(0xFF4CAF50) else Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (audioUri != null) "म्यूज़िक चुना गया (बदलने के लिए टैप करें)" else "Tap to add music",
                    color = if (audioUri != null) Color(0xFF81C784) else Color.Gray,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Video Trim Track (Yellow VN Border)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E24))
                    .border(2.dp, Color(0xFFFFD600), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Trim: ${formatTime(trimRange.start.toLong())}", color = Color(0xFFFFD600), fontSize = 12.sp)
                    Text("End: ${formatTime(trimRange.endInclusive.toLong())}", color = Color(0xFFFFD600), fontSize = 12.sp)
                }

                val maxRange = videoDurationMs.coerceAtLeast(1000L).toFloat()
                val safeRange = trimRange.start.coerceIn(0f, maxRange)..trimRange.endInclusive.coerceIn(0f, maxRange)

                RangeSlider(
                    value = safeRange,
                    onValueChange = { newRange ->
                        if (newRange.endInclusive - newRange.start >= 500f) {
                            trimRange = newRange
                            exoPlayer.seekTo(newRange.start.toLong())
                            currentPosMs = newRange.start.toLong()
                        }
                    },
                    valueRange = 0f..maxRange,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD600),
                        activeTrackColor = Color(0xFFFFD600),
                        inactiveTrackColor = Color(0xFF44444C)
                    )
                )
            }
        }

        // 5. VN-Style Bottom Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E0E10))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolButton(Icons.Default.VideoCall, "Video") {
                videoPicker.launch(arrayOf("video/*"))
            }
            ToolButton(Icons.Default.MusicNote, "Music") {
                audioPicker.launch(arrayOf("audio/*"))
            }
            ToolButton(
                icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                label = if (isMuted) "Muted" else "Sound"
            ) {
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted) 0f else 1f
            }
            ToolButton(Icons.Default.ContentCut, "Reset Trim") {
                trimRange = 0f..videoDurationMs.coerceAtLeast(1000L).toFloat()
                exoPlayer.seekTo(0L)
            }
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.LightGray, fontSize = 11.sp)
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@UnstableApi
fun exportVideo(
    context: Context,
    videoUri: Uri,
    audioUri: Uri?,
    startMs: Long,
    endMs: Long,
    muteOriginal: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val tempFile = File(context.cacheDir, "MiniVN_${System.currentTimeMillis()}.mp4")
    val clipDuration = (endMs - startMs).coerceAtLeast(500L)

    val videoMediaItem = MediaItem.Builder()
        .setUri(videoUri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        )
        .build()

    val editedVideo = EditedMediaItem.Builder(videoMediaItem)
        .setRemoveAudio(muteOriginal || audioUri != null)
        .build()

    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                try {
                    saveToGallery(context, tempFile)
                    tempFile.delete()
                    onSuccess()
                } catch (e: Exception) {
                    onError(e.message ?: "Save failed")
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                tempFile.delete()
                onError(exportException.message ?: "Export error")
            }
        })
        .build()

    if (audioUri != null) {
        val audioMediaItem = MediaItem.Builder()
            .setUri(audioUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0L)
                    .setEndPositionMs(clipDuration)
                    .build()
            )
            .build()
        val editedAudio = EditedMediaItem.Builder(audioMediaItem)
            .setRemoveVideo(true)
            .build()

        val videoSequence = EditedMediaItemSequence(listOf(editedVideo))
        val audioSequence = EditedMediaItemSequence(listOf(editedAudio))
        val composition = Composition.Builder(listOf(videoSequence, audioSequence)).build()
        transformer.start(composition, tempFile.absolutePath)
    } else {
        transformer.start(editedVideo, tempFile.absolutePath)
    }
}

fun saveToGallery(context: Context, videoFile: File) {
    val fileName = "MiniVN_${System.currentTimeMillis()}.mp4"
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MiniVN")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw Exception("गैलरी में फाइल नहीं बन सकी")

    resolver.openOutputStream(uri)?.use { out ->
        FileInputStream(videoFile).use { input ->
            input.copyTo(out)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
