package dev.ujhhgtg.via.data

data class BookmarkItem(val id: String, val url: String, val title: String?, val folderId: String = "", val ordering: Int = 0, val lastUpdatedAt: Long = 0, val createdAt: Long = 0)
data class BookmarkFolder(val id: String, val title: String?, val parentFolderId: String = "", val ordering: Int = 0, val createdAt: Long = 0, val lastUpdatedAt: Long = 0)
data class HistoryEntry(val id: Int, val url: String?, val title: String?, val updatedAt: Long)
data class Favorite(val id: Int = 0, val url: String, val title: String?, val order: Int = -1, val updatedAt: Long = 0, val createdAt: Long = 0)
data class SiteConfiguration(val domain: String, val data: String?) {
    private val parsed = SiteConfigurationCodec.decode(data)
    private fun value(name: String, fallback: Int = 0): Int = parsed.optInt(name, fallback)
    val userAgentChoice: Int get() = value("uachoice", -1000)
    val customUserAgent: String? get() = parsed.optString("uastring", "").ifEmpty { null }
    val textZoomOverride: Int get() = value("textsize", 0)
    val enabledFlags: Int get() = value("enabled")
    val flags: Int get() = value("flags")
    private fun effective(bit: Int, global: Boolean): Boolean = if (enabledFlags and bit != 0) flags and bit != 0 else global
    val isEnabled: Boolean get() = effective(1, false)
    fun desktopMode(globalWebFlags: Int): Boolean = effective(8, globalWebFlags and 2048 != 0)
    fun adBlocking(globalWebFlags: Int): Boolean = effective(16, globalWebFlags and 1 != 0)
    fun incognito(globalWebFlags: Int): Boolean = effective(32, globalWebFlags and 64 != 0)
    fun allowRedirection(globalWebFlags: Int): Boolean = !effective(1024, globalWebFlags and 134217728 != 0)
    fun backWithoutReload(globalWebFlags: Int): Boolean = effective(131072, globalWebFlags and 512 != 0)
    val isEmpty: Boolean get() = customUserAgent == null && userAgentChoice == -1000 && enabledFlags shr 1 == 0 && flags shr 1 == 0 && textZoomOverride == 0
    fun overrides(bit: Int): Boolean = enabledFlags and bit == bit
    fun booleanOverride(bit: Int): Boolean? = if (overrides(bit)) flags and bit == bit else null
    /** 0 = global default, 1 = allow, 2 = block, 3 = ask. */
    fun permissionMode(decisionBit: Int, askBit: Int): Int = when {
        effective(askBit, false) -> 3
        effective(decisionBit, false) -> 2
        !effective(decisionBit, true) -> 1
        else -> 0
    }
    val clipboardMode: Int get() = permissionMode(64, 128)
    val openAppMode: Int get() = permissionMode(256, 512)
    val microphoneMode: Int get() = permissionMode(2048, 4096)
    val cameraMode: Int get() = permissionMode(8192, 16384)
    val locationMode: Int get() = permissionMode(65536, 32768)
    fun withBoolean(bit: Int, enabled: Boolean?): SiteConfiguration {
        val changedEnabled = if (enabled == null) enabledFlags and bit.inv() else enabledFlags or bit
        val changedFlags = if (enabled == true) flags or bit else flags and bit.inv()
        return edited { put("enabled", changedEnabled); put("flags", changedFlags) }
    }
    fun withEnabled(enabled: Boolean) = withBoolean(1, enabled)
    fun withPermission(decisionBit: Int, askBit: Int, mode: Int): SiteConfiguration {
        require(mode in 0..3)
        return withBoolean(decisionBit, if (mode == 1 || mode == 2) mode == 2 else null)
            .withBoolean(askBit, if (mode == 3) true else null)
    }
    fun withUserAgent(choice: Int, custom: String? = customUserAgent): SiteConfiguration = edited {
        if (choice == -1000) remove("uachoice") else put("uachoice", choice)
        if (custom.isNullOrEmpty()) remove("uastring") else put("uastring", SiteConfigurationCodec.sanitizeUserAgent(custom))
    }
    fun withTextZoom(zoom: Int): SiteConfiguration = edited { if (zoom <= 0) remove("textsize") else put("textsize", zoom) }
    private fun edited(block: org.json.JSONObject.() -> Unit): SiteConfiguration {
        val json = org.json.JSONObject(parsed.toString())
        json.block()
        if (!json.has("flags")) json.put("flags", 0)
        return copy(data = json.toString())
    }
}
data class SessionTab(val id: String, val url: String?, val title: String?, val filePath: String?, val flags: Int = 0, val lastVisitedAt: Long = 0) {
    val isIncognito: Boolean get() = flags and 1 != 0
    val isOpen: Boolean get() = flags and 2 != 0
}
