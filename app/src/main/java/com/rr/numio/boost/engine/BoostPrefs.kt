package com.rr.numio.boost.engine

import android.content.Context
import android.content.SharedPreferences

/** Small wrapper around SharedPreferences so the service, tile and UI agree on the settings. */
class BoostPrefs(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("boost", Context.MODE_PRIVATE)

    /** Phone-speaker boost, 0..100. This is the same key v1.0.0 used, so existing levels carry over. */
    var boostPercent: Int
        get() = prefs.getInt(KEY_PERCENT, 30)
        set(value) = prefs.edit().putInt(KEY_PERCENT, value.coerceIn(0, 100)).apply()

    /** When true the slider goes up to [HIGH_MAX_MB] instead of [NORMAL_MAX_MB]. */
    var highBoost: Boolean
        get() = prefs.getBoolean(KEY_HIGH, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH, value).apply()

    /** Remember a separate boost level for the speaker, wired headphones and Bluetooth. */
    var perOutput: Boolean
        get() = prefs.getBoolean(KEY_PER_OUTPUT, true)
        set(value) = prefs.edit().putBoolean(KEY_PER_OUTPUT, value).apply()

    /** Cap the boost at [HEADPHONE_MAX_MB] on headphones and Bluetooth. */
    var headphoneLimit: Boolean
        get() = prefs.getBoolean(KEY_HEADPHONE_LIMIT, true)
        set(value) = prefs.edit().putBoolean(KEY_HEADPHONE_LIMIT, value).apply()

    /** The one-time safety notice has been read. */
    var safetyNoticeSeen: Boolean
        get() = prefs.getBoolean(KEY_SAFETY_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SAFETY_SEEN, value).apply()

    private fun keyFor(output: AudioOutput): String =
        if (!perOutput) KEY_PERCENT else when (output) {
            AudioOutput.SPEAKER -> KEY_PERCENT
            AudioOutput.WIRED -> KEY_PERCENT_WIRED
            AudioOutput.BLUETOOTH -> KEY_PERCENT_BLUETOOTH
        }

    /** Boost slider position (0..100) for [output]. */
    fun percentFor(output: AudioOutput): Int {
        val key = keyFor(output)
        val default = if (key == KEY_PERCENT) 30 else HEADPHONE_DEFAULT_PERCENT
        return prefs.getInt(key, default)
    }

    fun setPercent(output: AudioOutput, value: Int) {
        prefs.edit().putInt(keyFor(output), value.coerceIn(0, 100)).apply()
    }

    /** True when the headphone limit is holding the boost down on [output]. */
    fun isLimited(output: AudioOutput): Boolean = headphoneLimit && output.isHeadphones

    /** Largest boost the slider can reach on [output], in millibels. */
    fun maxMb(output: AudioOutput): Int = when {
        isLimited(output) -> HEADPHONE_MAX_MB
        highBoost -> HIGH_MAX_MB
        else -> NORMAL_MAX_MB
    }

    /** Target gain in millibels (1 dB = 100 mB) for [output]. */
    fun gainMb(output: AudioOutput): Int = percentFor(output) * maxMb(output) / 100

    companion object {
        const val KEY_PERCENT = "boost_percent"
        const val KEY_PERCENT_WIRED = "boost_percent_wired"
        const val KEY_PERCENT_BLUETOOTH = "boost_percent_bluetooth"
        const val KEY_HIGH = "high_boost"
        const val KEY_PER_OUTPUT = "per_output"
        const val KEY_HEADPHONE_LIMIT = "headphone_limit"
        const val KEY_SAFETY_SEEN = "safety_notice_seen"

        /** +12 dB: loud, but most phone speakers stay clean. */
        const val NORMAL_MAX_MB = 1200

        /** +24 dB: unlocked behind a warning. Expect distortion. */
        const val HIGH_MAX_MB = 2400

        /** +6 dB: the most Decibel adds on headphones while the limit is on. */
        const val HEADPHONE_MAX_MB = 600

        /** Headphones and Bluetooth start quiet the first time they're used. */
        const val HEADPHONE_DEFAULT_PERCENT = 20
    }
}
