package com.mayank.minivn

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineSong(
    val title: String,
    val artist: String,
    val previewUrl: String
)

suspend fun searchOnlineSongs(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    val encoded = URLEncoder.encode(query.trim(), "UTF-8")
    val urlStr = "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=15&country=IN"
    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8000
        readTimeout = 8000
    }
    try {
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        val songs = mutableListOf<OnlineSong>()
        for (i in 0 until results.length()) {
            val obj = results.getJSONObject(i)
            val preview = obj.optString("previewUrl", "")
            if (preview.isNotBlank()) {
                songs.add(
                    OnlineSong(
                        title = obj.optString("trackName", "Unknown"),
                        artist = obj.optString("artistName", "Artist"),
                        previewUrl = preview
                    )
                )
            }
        }
        songs
    } catch (e: Exception) {
        emptyList()
    } finally {
        conn.disconnect()
    }
}

suspend fun downloadSongToCache(context: Context, song: OnlineSong): Uri? = withContext(Dispatchers.IO) {
    try {
        val outFile = File(context.cacheDir, "online_music_${System.currentTimeMillis()}.m4a")
        val conn = (URL(song.previewUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
        }
        conn.inputStream.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()
        Uri.fromFile(outFile)
    } catch (e: Exception) {
        null
    }
}
