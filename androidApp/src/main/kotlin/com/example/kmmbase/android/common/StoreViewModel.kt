package com.example.kmmbase.android.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

private class StoreViewModel<S : AutoCloseable>(val store: S) : ViewModel(store)

// Keeps the store through rotation and closes it when the destination leaves the back stack.
@Composable
internal fun <S : AutoCloseable> rememberStore(create: () -> S): S =
    viewModel<StoreViewModel<S>> { StoreViewModel(create()) }.store
