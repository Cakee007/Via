package dev.ujhhgtg.via.home

import java.security.MessageDigest
import java.util.Locale

/** i9/a..e packed preferences, transformed exactly as r8/d.l into the renderer's values. */
data class HomeDesign(
    val logoBits: Int = 193766400,
    val favoriteBits: Int = 1638446,
    val searchBits: Int = 2490468,
    val customBits: Int = 0,
    val backgroundBits: Int = 0,
    val accentColor: Int = -1,
    val backgroundPath: String? = null,
    val dark: Boolean = false,
    val rtl: Boolean = false,
    val extraCss: String = "",
) {
    val logoWidth get() = logoBits and 127
    val logoHeight get() = ((logoBits shr 7) and 127).let { if (it == 0 && logoWidth == 0) 72 else it }
    val logoRadius get() = (maxOf(logoWidth, logoHeight) / 2f * ((logoBits shr 14 and 127) / 100f)).toInt()
    val logoFontSize get() = logoBits shr 21 and 63
    val logoBold get() = logoBits and 134217728 != 0
    val logoItalic get() = logoBits and 268435456 != 0
    val favoriteHeight get() = (favoriteBits shr 7 and 127).let { if (it == 0) (favoriteBits and 127).takeIf { width -> width != 0 } ?: 54 else it }
    val favoriteWidth get() = (favoriteBits and 127).takeIf { it != 0 } ?: favoriteHeight
    val favoriteRadius get() = (minOf(favoriteWidth, favoriteHeight) / 2f * ((favoriteBits shr 14 and 127) / 100f)).toInt()
    val favoriteIconDisabled get() = favoriteBits and 2097152 != 0
    val favoriteColorDisabled get() = favoriteBits and 4194304 != 0
    val searchLine get() = searchBits and 16777216 != 0
    val searchAlpha get() = if (searchLine) 0f else (searchBits shr 7 and 127) / 100f
    val strokeWidth get() = searchBits shr 21 and 7
    val strokeAlpha get() = (searchBits shr 14 and 127) / 100f
    val searchRadius get() = if (searchLine) 0 else ((searchBits and 127) / 100f * (strokeWidth + 23f)).toInt()
    val blur get() = customBits and 1 != 0
    val searchEnabled get() = customBits and 2 == 0
    val lightForegroundBackground get() = if (backgroundBits and 128 != 0) backgroundBits and 256 != 0 else isLight(accentColor)

    companion object {
        fun bits(value: Int, shift: Int, width: Int, setting: Int): Int {
            val mask = (1 shl width) - 1
            require(setting in 0..mask)
            return value and (mask shl shift).inv() or (setting shl shift)
        }
        fun bit(value: Int, mask: Int, enabled: Boolean): Int = if (enabled) value or mask else value and mask.inv()
        fun isLight(color: Int) = (color shr 16 and 255) * .299 + (color shr 8 and 255) * .587 + (color and 255) * .114 >= 192
        fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
        fun favoriteColor(url: String): String {
            val hash = md5(url)
            val r = hash.take(3).toInt(16) % 128 + 90
            val g = hash.substring(hash.length / 2 - 3, hash.length / 2).toInt(16) % 128 + 90
            val b = hash.takeLast(3).toInt(16) % 128 + 90
            return "#" + ((r shl 16) or (g shl 8) or b).toString(16)
        }

        /** r8.d.j/m: the homepage color as a paintable argb value. */
        fun favoriteColorInt(url: String): Int {
            val hash = md5(url)
            val r = hash.take(3).toInt(16) % 128 + 90
            val g = hash.substring(hash.length / 2 - 3, hash.length / 2).toInt(16) % 128 + 90
            val b = hash.takeLast(3).toInt(16) % 128 + 90
            return (0xff000000).toInt() or (r shl 16) or (g shl 8) or b
        }
        /** r8.d.h and z8.h0.d both delegate to the same w3.r key as touch/custom icon writes. */
        fun iconName(url: String): String = HomeFavoriteIcons.key(url)
    }
}
