package com.example.kmmbase.android.platform.video

import android.view.View
import com.example.kmmbase.domain.repository.LiveVideoEngine

internal interface NativeLiveVideoEngine : LiveVideoEngine {
    val localView: View
    val remoteView: View
}
