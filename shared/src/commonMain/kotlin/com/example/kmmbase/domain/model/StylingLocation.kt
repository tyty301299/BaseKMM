package com.example.kmmbase.domain.model

/** A foreground device location, kept in memory only. */
data class StylingLocation(val lat: Double, val lon: Double) {
    init { require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) }
}
