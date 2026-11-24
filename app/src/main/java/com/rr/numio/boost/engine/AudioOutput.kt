package com.rr.numio.boost.engine

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CopyOnWriteArraySet

/** Where the sound is going. Each output keeps its own boost level. */
enum class AudioOutput(val label: String, val isHeadphones: Boolean) {
    SPEAKER("Phone speaker", false),
    WIRED("Wired headphones", true),
    BLUETOOTH("Bluetooth", true),
}

/**
 * Watches which output is connected. Android plays media on the most recently connected
 * accessory, so Bluetooth wins over wired, and wired wins over the phone speaker.
 *
 * Android can't tell a Bluetooth speaker from Bluetooth earbuds without the Bluetooth
 * permission, so every Bluetooth device counts as headphones (safer for ears).
 */
object OutputMonitor {
    private val _current = MutableStateFlow(AudioOutput.SPEAKER)
    val current: StateFlow<AudioOutput> = _current

    private val listeners = CopyOnWriteArraySet<(AudioOutput) -> Unit>()
    private var audio: AudioManager? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    /** Safe to call many times; only the first call registers. */
    fun start(context: Context) {
        if (audio != null) return
        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio = am
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun addListener(l: (AudioOutput) -> Unit) { listeners += l }
    fun removeListener(l: (AudioOutput) -> Unit) { listeners -= l }

    private fun refresh() {
        val am = audio ?: return
        val types = try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        } catch (_: Throwable) {
            emptySet()
        }
        val now = when {
            types.any { it in BLUETOOTH_TYPES } -> AudioOutput.BLUETOOTH
            types.any { it in WIRED_TYPES } -> AudioOutput.WIRED
            else -> AudioOutput.SPEAKER
        }
        if (now != _current.value) {
            _current.value = now
            listeners.forEach { it(now) }
        }
    }

    private val BLUETOOTH_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        26, // TYPE_BLE_HEADSET (API 31)
        27, // TYPE_BLE_SPEAKER (API 31)
        30, // TYPE_BLE_BROADCAST (API 33)
        23, // TYPE_HEARING_AID (API 28)
    )

    private val WIRED_TYPES = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
    )
}
