package com.lightest.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Immutable
data class VolumeData(
    val mediaVolumePercentage: Int = 0,
    val ringVolumePercentage: Int = 0,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL
)

@Composable
fun rememberVolumeState(): State<VolumeData> {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val volumeState = remember {
        mutableStateOf(
            VolumeData(
                mediaVolumePercentage = volumePct(audioManager, AudioManager.STREAM_MUSIC),
                ringVolumePercentage = volumePct(audioManager, AudioManager.STREAM_RING),
                ringerMode = audioManager.ringerMode
            )
        )
    }

    DisposableEffect(context) {
        val ringerReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    volumeState.value = volumeState.value.copy(
                        ringerMode = audioManager.ringerMode
                    )
                }
            }
        }
        context.registerReceiver(
            ringerReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        )

        // Narrow observer: only volume settings, not brightness/rotation/etc.
        val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val newMedia = volumePct(audioManager, AudioManager.STREAM_MUSIC)
                val newRing = volumePct(audioManager, AudioManager.STREAM_RING)
                val old = volumeState.value
                // Skip state update if nothing actually changed — avoids recomposition.
                if (newMedia != old.mediaVolumePercentage || newRing != old.ringVolumePercentage) {
                    volumeState.value = old.copy(
                        mediaVolumePercentage = newMedia,
                        ringVolumePercentage = newRing
                    )
                }
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, volumeObserver
        )

        onDispose {
            try {
                context.unregisterReceiver(ringerReceiver)
                context.contentResolver.unregisterContentObserver(volumeObserver)
            } catch (_: Exception) { }
        }
    }

    return volumeState
}

private fun volumePct(audioManager: AudioManager, streamType: Int): Int {
    val max = audioManager.getStreamMaxVolume(streamType)
    return if (max > 0) (audioManager.getStreamVolume(streamType) * 100) / max else 0
}
