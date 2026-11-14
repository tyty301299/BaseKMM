package com.example.kmmbase.domain.model

/**
 * Shared hosted backend. Camera connection settings can override this origin,
 * including http://localhost:8001 for local development.
 */
const val DEFAULT_SHADOW_ORIGIN = "https://hackathon.trioxstudios.com"

/**
 * Deployment access codes belong in connection settings, never in the app binary.
 */
const val DEFAULT_SHADOW_ACCESS_CODE = ""
