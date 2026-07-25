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
                mediaVolumePercentage = getVolumePercentage(audioManager, AudioManager.STREAM_MUSIC),
                ringVolumePercentage = getVolumePercentage(audioManager, AudioManager.STREAM_RING),
                ringerMode = audioManager.ringerMode
            )
        ) 
    }

    DisposableEffect(context) {
        // 1. Listen for Ringer Mode Changes
        val ringerReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    volumeState.value = volumeState.value.copy(
                        ringerMode = audioManager.ringerMode
                    )
                }
            }
        }
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        context.registerReceiver(ringerReceiver, filter)

        // 2. Listen for Media Volume Changes
        val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                volumeState.value = volumeState.value.copy(
                    mediaVolumePercentage = getVolumePercentage(audioManager, AudioManager.STREAM_MUSIC),
                    ringVolumePercentage = getVolumePercentage(audioManager, AudioManager.STREAM_RING)
                )
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver
        )

        onDispose {
            try {
                context.unregisterReceiver(ringerReceiver)
                context.contentResolver.unregisterContentObserver(volumeObserver)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    return volumeState
}

private fun getVolumePercentage(audioManager: AudioManager, streamType: Int): Int {
    val current = audioManager.getStreamVolume(streamType)
    val max = audioManager.getStreamMaxVolume(streamType)
    return if (max > 0) {
        (current * 100) / max
    } else 0
}
