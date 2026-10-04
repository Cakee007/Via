package dev.ujhhgtg.via.tools

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import dev.ujhhgtg.via.ui.ViaToast
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.net.toUri
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest

/** a8.u1 / z8.w3: validates a page and requests (or updates) a pinned launcher shortcut. */
object AddToHomeScreen {
    fun show(activity: FragmentActivity, url: String?, pageTitle: String?) {
        // s6.Ia/Ja: internal pages still open the editor, with an empty URL.
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager?.isRequestPinShortcutSupported != true) {
            ViaToast.makeText(activity, R.string.need_shortcut_permission, ViaToast.LENGTH_SHORT).show(); return
        }
        val internal = url.isNullOrEmpty() || dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(activity, url)
        val address = if (internal) "" else url.orEmpty()
        val title = (if (internal) null else pageTitle)?.takeIf(String::isNotEmpty) ?: activity.getString(R.string.app_name)
        AddToHomeScreenFragment.newInstance(address, title).show(activity.supportFragmentManager, "add_to_home_screen")
    }

    fun request(activity: Activity, url: String, label: String, favicon: Bitmap? = null) {
        val manager = activity.getSystemService(ShortcutManager::class.java) ?: return
        if (!manager.isRequestPinShortcutSupported) {
            ViaToast.makeText(activity, R.string.need_shortcut_permission, ViaToast.LENGTH_SHORT).show(); return
        }
        val id = "sc_" + md5(url)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri(), activity, Shell::class.java).apply {
            setPackage(activity.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // a8.u1.w3 uses IconCompat.d (TYPE_ADAPTIVE_BITMAP), not the bitmap icon type.
        val icon = favicon?.let { Icon.createWithAdaptiveBitmap(padded(it)) } ?: Icon.createWithResource(activity, activity.applicationInfo.icon)
        val shortcut = ShortcutInfo.Builder(activity, id).setShortLabel(label).setIcon(icon).setIntent(intent).build()
        val host = try { manager.pinnedShortcuts.any { it.id == id } } catch (_: Throwable) { false }
        if (host) {
            manager.updateShortcuts(listOf(shortcut))
            ViaToast.makeText(activity, activity.getString(R.string.update_shortcut_successfully, domain(url)), ViaToast.LENGTH_SHORT).show()
        } else {
            manager.requestPinShortcut(shortcut, null)
            ViaToast.makeText(activity, activity.getString(R.string.create_shortcut_successfully, domain(url)), ViaToast.LENGTH_SHORT).show()
        }
    }

    private fun domain(url: String): String = runCatching { url.toUri().host?.takeIf { it.isNotEmpty() } ?: url.take(32) }.getOrDefault(url.take(32))
    /** a8.u1.u3: preserve the source corner pixel and inset the favicon by 32%. */
    private fun padded(source: Bitmap): Bitmap {
        val input = source.copy(Bitmap.Config.ARGB_8888, false)
        val output = createBitmap(input.width, input.height)
        output.eraseColor(input[0, 0])
        val insetW = (input.width * .32f).toInt(); val insetH = (input.height * .32f).toInt()
        val scaled = Bitmap.createScaledBitmap(input, input.width - insetW, input.height - insetH, true)
        Canvas(output).drawBitmap(scaled, (insetW / 2).toFloat(), (insetH / 2).toFloat(), null)
        if (scaled !== input) scaled.recycle()
        if (input !== source) input.recycle()
        return output
    }
    private fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
