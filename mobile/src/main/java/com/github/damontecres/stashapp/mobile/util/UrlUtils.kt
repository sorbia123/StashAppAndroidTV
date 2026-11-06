package com.github.damontecres.stashapp.mobile.util

import java.net.URI

fun fixStashMediaUrl(rawUrl: String?, serverUrl: String, apiKey: String = ""): String {
    if (rawUrl.isNullOrBlank()) return ""
    val cleanServer = serverUrl.trim().trimEnd('/')
    var url = try {
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            val uri = URI(rawUrl)
            val pathAndQuery = (uri.rawPath ?: "") + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
            "$cleanServer$pathAndQuery"
        } else if (rawUrl.startsWith("/")) {
            "$cleanServer$rawUrl"
        } else {
            "$cleanServer/$rawUrl"
        }
    } catch (e: Exception) {
        if (rawUrl.startsWith("/")) "$cleanServer$rawUrl" else "$cleanServer/$rawUrl"
    }

    if (apiKey.isNotBlank() && !url.contains("apikey=", ignoreCase = true)) {
        val separator = if (url.contains("?")) "&" else "?"
        url = "$url${separator}apikey=$apiKey"
    }

    return url
}
