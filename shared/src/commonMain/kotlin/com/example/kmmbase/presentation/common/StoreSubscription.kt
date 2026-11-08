package com.example.kmmbase.presentation.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Lets Swift stop a Flow observation. Callbacks arrive on the store's main dispatcher. */
class StoreSubscription internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

internal fun <T> Flow<T>.subscribeIn(scope: CoroutineScope, onEach: (T) -> Unit) =
    StoreSubscription(scope.launch { collect { onEach(it) } })
