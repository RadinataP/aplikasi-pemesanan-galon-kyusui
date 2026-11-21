package com.kyusui.app.core

import com.kyusui.app.BuildConfig

/** Konstanta lintas lapisan aplikasi. URL API ditentukan per build variant. */
object Constants {
    /** URL API yang aktif pada build debug/release saat ini. */
    val API_BASE_URL: String get() = BuildConfig.API_BASE_URL

    const val AUTH_HEADER = "Authorization"
    const val ACCEPT_HEADER = "Accept"
    const val BEARER_PREFIX = "Bearer "
    const val CONTENT_TYPE_JSON = "application/json"
    const val ACCEPT_JSON = "application/json"
    const val DEFAULT_TIMEOUT_SECONDS = 30L
    const val MAX_RETRIES = 3
    const val DATASTORE_NAME = "kyusui_preferences"
    const val REDACTED = "***REDACTED***"
}
