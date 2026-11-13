package com.example.kmmbase.domain.model

/**
 * Local backend for iOS Simulator and Android with `adb reverse tcp:8001 tcp:8001`.
 * The camera connection settings persist a different HTTPS origin when needed.
 */
const val DEFAULT_SHADOW_ORIGIN = "http://localhost:8001"

/**
 * Local development has no access code. Deployment access codes belong in connection settings.
 */
const val DEFAULT_SHADOW_ACCESS_CODE = ""
