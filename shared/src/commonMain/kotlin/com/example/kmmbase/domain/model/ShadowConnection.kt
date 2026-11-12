package com.example.kmmbase.domain.model

class ShadowConnectionException(message: String) : Exception(message)

data class ShadowHealth(val mode: String, val accessRequired: Boolean, val visionModel: String = "")
