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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.media3.transformer.Effects
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

    var selectedFilter by remember { mutableStateOf(VideoFilter.ORIGINAL) }
    var showFilterBar by remember { mutableStateOf(false) }

    var overlayText by remember { mutableStateOf("") }
    var showTextDialog by remember { mutableStateOf(false) }
    var tempTextInput by remember { mutableStateOf("") }

    var videoDurationMs by remember { mutableStateOf(10000L) }
    var currentPosMs by remember { mutableStateOf(0L) }
    var trimRange by remember { mutableStateOf(0f..10000f) }
    var isPlaying by remember { mutableStateOf(false) }

    var isExporting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("वीडियो चुनने के लिए Video पर टैप करें") }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val audioPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && exoPlayer.duration > 0) {
                    val dur = exoPlayer.duration
                    if (videoDurationMs != dur) {
                        videoDurationMs = dur
                        trimRange = 0f..dur.toFloat()
                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    audioPlayer.pause()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            audioPlayer.release()
        }
    }

    // Apply live filter to preview when filter changes
    LaunchedEffect(selectedFilter) {
        val uri = videoUri ?: return@LaunchedEffect
        val pos = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.isPlaying
        exoPlayer.pause()
        audioPlayer.pause()
        exoPlayer.setVideoEffects(buildFilterEffects(selectedFilter))
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.seekTo(pos)
        exoPlayer.volume = if (isMuted || audioUri != null) 0f else 1f
        if (wasPlaying) {
            exoPlayer.play()
            if (audioUri != null) {
                audioPlayer.seekTo((pos - trimRange.start.toLong()).coerceAtLeast(0L))
                audioPlayer.play()
            }
        }
    }

    LaunchedEffect(isPlaying, trimRange) {
        while (isPlaying) {
            currentPosMs = exoPlayer.currentPosition
            if (currentPosMs >= trimRange.endInclusive.toLong()) {
                exoPlayer.seekTo(trimRange.start.toLong())
                exoPlayer.pause()
                audioPlayer.pause()
                audioPlayer.seekTo(0L)
            }
            delay(150)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            videoUri = uri
            selectedFilter = VideoFilter.ORIGINAL
            exoPlayer.setVideoEffects(emptyList())
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.volume = if (isMuted || audioUri != null) 0f else 1f
            exoPlayer.playWhenReady = true
            if (audioUri != null) {
                audioPlayer.seekTo(0L)
                audioPlayer.play()
            }
            statusText = "वीडियो लोड हो गया!"
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            audioUri = uri
            audioPlayer.setMediaItem(MediaItem.fromUri(uri))
            audioPlayer.prepare()
            exoPlayer.volume = 0f
            if (exoPlayer.isPlaying) {
                val audioPos = (exoPlayer.currentPosition - trimRange.start.toLong()).coerceAtLeast(0L)
                audioPlayer.seekTo(audioPos)
                audioPlayer.play()
            }
            statusText = "लाइव म्यूज़िक जुड़ गया!"
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = Color(0xFF1E1E24),
            title = { Text("वीडियो पर टेक्स्ट लिखें", color = Color.White, fontSize = 17.sp) },
            text = {
                OutlinedTextField(
                    value = tempTextInput,
                    onValueChange = { tempTextInput = it },
                    placeholder = { Text("यहाँ नाम या शायरी लिखें...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2979FF)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    overlayText = tempTextInput.trim()
                    showTextDialog = false
                    statusText = if (overlayText.isNotEmpty()) "टेक्स्ट जुड़ गया!" else "टेक्स्ट हटा दिया गया"
                }) {
                    Text("Save", color = Color(0xFF2979FF))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    overlayText = ""
                    tempTextInput = ""
                    showTextDialog = false
                }) {
                    Text("Clear", color = Color.Gray)
                }
            }
        )
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
                text = "MiniVN v2",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = selectedFilter.title,
                color = Color(0xFFFFD600),
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
                        audioPlayer.pause()
                        isExporting = true
                        statusText = "वीडियो Export हो रहा है..."
                        exportVideo(
                            context = context,
                            videoUri = vUri,
                            audioUri = audioUri,
                            startMs = trimRange.start.toLong(),
                            endMs = trimRange.endInclusive.toLong(),
                            muteOriginal = isMuted,
                            filter = selectedFilter,
                            overlayText = overlayText,
                            onSuccess = {
                                isExporting = false
                                statusText = "सेव हो गया! Movies/MiniVN"
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

        // 2. Video Preview Area + Live Text Overlay
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

            // Live Text Preview
            if (overlayText.isNotBlank()) {
                Text(
                    text = overlayText,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Color(0x99000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
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
                        Text("Filter + Text के साथ MP4 बन रहा है...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // 3. Playback Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151518))
                .padding(horizontal = 14.dp, vertical = 6.dp),
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
                            audioPlayer.pause()
                        } else {
                            if (exoPlayer.currentPosition < trimRange.start.toLong() ||
                                exoPlayer.currentPosition >= trimRange.endInclusive.toLong()
                            ) {
                                exoPlayer.seekTo(trimRange.start.toLong())
                            }
                            exoPlayer.play()
                            if (audioUri != null) {
                                val audioOffset = (exoPlayer.currentPosition - trimRange.start.toLong()).coerceAtLeast(0L)
                                audioPlayer.seekTo(audioOffset)
                                audioPlayer.play()
                            }
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

        // Optional Filter Strip (VN Style)
        if (showFilterBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF18181D))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoFilter.values().forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF2979FF) else Color(0xFF2A2A32))
                            .clickable {
                                selectedFilter = filter
                                statusText = "Filter: ${filter.title}"
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 4. VN-Style Timeline Tracks
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121215))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Music Track
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222228))
                    .clickable { audioPicker.launch(arrayOf("audio/*")) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    text = if (audioUri != null) "म्यूज़िक जुड़ा है (बदलने के लिए टैप करें)" else "Tap to add music",
                    color = if (audioUri != null) Color(0xFF81C784) else Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            // Subtitle / Text Track
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222228))
                    .clickable {
                        tempTextInput = overlayText
                        showTextDialog = true
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Title,
                    contentDescription = "Text",
                    tint = if (overlayText.isNotBlank()) Color(0xFFFFD600) else Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (overlayText.isNotBlank()) "Text: $overlayText" else "Tap to add subtitle / text",
                    color = if (overlayText.isNotBlank()) Color(0xFFFFD600) else Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(8.dp))

            // Video Trim Track (Yellow VN Border)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E24))
                    .border(2.dp, Color(0xFFFFD600), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Trim: ${formatTime(trimRange.start.toLong())}", color = Color(0xFFFFD600), fontSize = 11.sp)
                    Text("End: ${formatTime(trimRange.endInclusive.toLong())}", color = Color(0xFFFFD600), fontSize = 11.sp)
                }

                val maxRange = videoDurationMs.coerceAtLeast(1000L).toFloat()
                val safeRange = trimRange.start.coerceIn(0f, maxRange)..trimRange.endInclusive.coerceIn(0f, maxRange)

                RangeSlider(
                    value = safeRange,
                    onValueChange = { newRange ->
                        if (newRange.endInclusive - newRange.start >= 500f) {
                            trimRange = newRange
                            exoPlayer.seekTo(newRange.start.toLong())
                            audioPlayer.seekTo(0L)
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
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolButton(Icons.Default.VideoCall, "Video") {
                videoPicker.launch(arrayOf("video/*"))
            }
            ToolButton(
                icon = Icons.Default.AutoAwesome,
                label = "Filter",
                tint = if (showFilterBar || selectedFilter != VideoFilter.ORIGINAL) Color(0xFFFFD600) else Color.White
            ) {
                showFilterBar = !showFilterBar
            }
            ToolButton(
                icon = Icons.Default.Title,
                label = "Text",
                tint = if (overlayText.isNotBlank()) Color(0xFFFFD600) else Color.White
            ) {
                tempTextInput = overlayText
                showTextDialog = true
            }
            ToolButton(Icons.Default.MusicNote, "Music") {
                audioPicker.launch(arrayOf("audio/*"))
            }
            ToolButton(
                icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                label = if (isMuted) "Muted" else "Sound"
            ) {
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted || audioUri != null) 0f else 1f
            }
        }
    }
}

@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.LightGray, fontSize = 10.sp)
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
    filter: VideoFilter,
    overlayText: String,
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

    val exportEffects = buildExportEffects(filter, overlayText)

    val editedVideo = EditedMediaItem.Builder(videoMediaItem)
        .setRemoveAudio(muteOriginal || audioUri != null)
        .setEffects(Effects(emptyList(), exportEffects))
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
