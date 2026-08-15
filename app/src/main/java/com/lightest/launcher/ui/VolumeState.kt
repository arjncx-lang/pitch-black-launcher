package com.lightest.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.runtime.Composable
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

private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

@Composable
fun rememberVolumeState(
    refreshKey: Int = 0,
    enabled: Boolean = true
): State<VolumeData> {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val volumeState = remember {
        mutableStateOf(readVolumeData(audioManager))
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        val mode = audioManager.ringerMode
                        if (mode != volumeState.value.ringerMode) {
                            volumeState.value = volumeState.value.copy(ringerMode = mode)
                        }
                    }
                    ACTION_VOLUME_CHANGED -> {
                        val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                        if (stream != AudioManager.STREAM_MUSIC &&
                            stream != AudioManager.STREAM_RING &&
                            stream != -1
                        ) return
                        val next = readVolumeData(audioManager)
                        if (next != volumeState.value) volumeState.value = next
                    }
                }
            }
        }
    }

    HudLifecycleEffect(
        enabled = enabled,
        refreshKey = refreshKey,
        onStart = {
            volumeState.value = readVolumeData(audioManager)
            val filter = IntentFilter().apply {
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(ACTION_VOLUME_CHANGED)
            }
            context.registerReceiver(receiver, filter)
        },
        onStop = {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) { }
        },
        onRefresh = { volumeState.value = readVolumeData(audioManager) }
    )

    return volumeState
}

private fun readVolumeData(audioManager: AudioManager): VolumeData {
    return VolumeData(
        mediaVolumePercentage = volumePct(audioManager, AudioManager.STREAM_MUSIC),
        ringVolumePercentage = volumePct(audioManager, AudioManager.STREAM_RING),
        ringerMode = audioManager.ringerMode
    )
}

private fun volumePct(audioManager: AudioManager, streamType: Int): Int {
    val max = audioManager.getStreamMaxVolume(streamType)
    return if (max > 0) (audioManager.getStreamVolume(streamType) * 100) / max else 0
}
