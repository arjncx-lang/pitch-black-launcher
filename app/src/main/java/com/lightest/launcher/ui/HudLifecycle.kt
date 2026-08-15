package com.lightest.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * HUD listeners run only while the launcher is RESUMED (home visible).
 * [onStop] unregisters when the user leaves home — no battery/tick work
 * in the background. [onRefresh] is the Back-button poke: re-read without
 * tearing listeners down.
 */
@Composable
internal fun HudLifecycleEffect(
    enabled: Boolean,
    refreshKey: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    DisposableEffect(lifecycleOwner, enabled) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }
        var running = false
        fun start() {
            if (running) return
            running = true
            currentOnStart()
        }
        fun stop() {
            if (!running) return
            running = false
            currentOnStop()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            start()
        }
        onDispose {
            stop()
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshKey, enabled) {
        if (enabled && refreshKey > 0) currentOnRefresh()
    }
}
