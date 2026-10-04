package dev.ujhhgtg.via.common

import android.os.Bundle
import android.os.SystemClock

/** v5.b: process-local bundles, including URL drafts and downloaded-file names. */
object TransientState {
    private val values = HashMap<String, Bundle>()

    fun remove(name: String) { values.remove(name) }

    fun get(name: String): Bundle? {
        val value = values[name] ?: return null
        if (value.containsKey("expired_time") && value.getLong("expired_time") < SystemClock.elapsedRealtime() / 1000) {
            remove(name)
            return null
        }
        if (value.getBoolean("throwaway", false)) remove(name)
        return value
    }

    fun put(name: String, value: Bundle) { values[name] = value }

    fun builder() = Builder()

    class Builder {
        private var name = ""
        private val value = Bundle()
        fun name(name: String) = apply { this.name = name }
        fun putInt(key: String, number: Int) = apply { value.putInt(key, number) }
        fun putLong(key: String, number: Long) = apply { value.putLong(key, number) }
        fun putString(key: String, text: String?) = apply { value.putString(key, text) }
        fun expiresAfter(seconds: Int) = putLong("expired_time", SystemClock.elapsedRealtime() / 1000 + seconds)
        fun save() { if (value.isEmpty) remove(name) else put(name, value) }
    }
}
