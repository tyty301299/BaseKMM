package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*

/** Platform adapter owns Agora only. It never performs business decisions or HTTP requests. */
interface LiveVideoEngine {
    fun start(session: LiveTryOnSession, listener: LiveVideoListener)
    fun stop()
}

interface LiveVideoListener {
    fun onRemoteVideo()
    fun onFailure(message: String)
    fun onStats(framesPerSecond: Int)
}
