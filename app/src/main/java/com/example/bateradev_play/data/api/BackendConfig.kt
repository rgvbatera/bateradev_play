package com.example.bateradev_play.data.api

import com.example.bateradev_play.BuildConfig

internal object BackendConfig {
    val baseUrl: String = BuildConfig.BACKEND_BASE_URL
        .trim()
        .trimEnd('/')
        .ifBlank { "http://10.0.2.2:5000" }
}
