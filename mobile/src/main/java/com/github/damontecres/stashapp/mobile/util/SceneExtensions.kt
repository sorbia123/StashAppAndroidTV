package com.github.damontecres.stashapp.mobile.util

import com.github.damontecres.stashapp.mobile.api.fragment.SlimSceneData
import java.util.Locale

val String.fileNameFromPath: String
    get() {
        val cleanPath = this.replace('\\', '/')
        val index = cleanPath.lastIndexOf('/')
        return if (index != -1) cleanPath.substring(index + 1) else cleanPath
    }

val SlimSceneData.titleOrFilename: String
    get() {
        if (!title.isNullOrBlank()) return title
        val filePath = files.firstOrNull()?.videoFile?.path
        return if (!filePath.isNullOrBlank()) filePath.fileNameFromPath else "Untitled Scene"
    }

fun formatDuration(seconds: Double?): String {
    if (seconds == null || seconds <= 0.0) return ""
    val totalSecs = seconds.toLong()
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}

fun formatResolution(height: Int?): String {
    if (height == null || height <= 0) return ""
    return when {
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        else -> "${height}p"
    }
}

fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%d KB", kb.toLong())
    }
}
