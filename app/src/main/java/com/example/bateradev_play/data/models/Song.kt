package com.example.bateradev_play.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "Desconhecido",
    val duration: Long = 0, // em segundos
    val filePath: String,
    val thumbnailUrl: String? = null,
    val bpm: Int? = null,
    val key: String? = null,
    val hasNoDrums: Boolean = false, // versão sem bateria
    val originalPath: String? = null, // caminho do arquivo original se for versão sem bateria
    val downloadedAt: Long = System.currentTimeMillis()
)

@Serializable
data class DownloadState(
    val url: String = "",
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val error: String? = null,
    @kotlinx.serialization.Transient
    val lastDownloadedFile: java.io.File? = null
)

@Serializable
data class AnalysisResult(
    val bpm: Int,
    val key: String,
    val confidence: Float,
    val duration: Long,
    val waveformData: List<Float> = emptyList()
)

@Serializable
data class VideoInfo(
    val title: String,
    val channel: String,
    val duration: Long,
    val thumbnailUrl: String,
    val viewCount: Long
)
