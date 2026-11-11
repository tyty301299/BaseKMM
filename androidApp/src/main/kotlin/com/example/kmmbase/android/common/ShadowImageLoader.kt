package com.example.kmmbase.android.common

import android.content.Context
import coil.request.ImageRequest

/**
 * Server media sits behind the access gate, so each image request carries the headers the
 * repository supplies. The code itself never reaches UI state.
 */
fun shadowImageRequest(context: Context, url: String, headers: Map<String, String>): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
        .crossfade(true)
        .build()
