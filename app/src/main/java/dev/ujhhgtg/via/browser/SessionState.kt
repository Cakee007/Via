package dev.ujhhgtg.via.browser

import android.content.Context
import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import android.webkit.WebView
import dev.ujhhgtg.via.data.SessionTab
import dev.ujhhgtg.via.search.UrlInputText
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** na.j.c/d and r4.d.b: the original Parcel-of-Bundle session file, with no alternate encoding. */
internal object SessionState {
    /** c8.ua.f1 / i6.i0.i: exclude built-in documents and about: pages, not other URL schemes. */
    fun acceptsUrl(context: Context, url: String?): Boolean = !url.isNullOrEmpty() &&
        !UrlInputText.isInternalDocument(url, context.filesDir.path) &&
        !(url.length > 6 && url.startsWith("about:", true))

    fun directory(context: Context): File = (context.getExternalFilesDir("tabs") ?: File(context.filesDir, "tabs")).apply { mkdirs() }

    fun read(path: String?): Bundle? {
        if (path.isNullOrEmpty()) return null
        return try {
            FileInputStream(path).use { stream ->
                val data = ByteArray(stream.channel.size().toInt())
                stream.read(data)
                val parcel = Parcel.obtain()
                try {
                    parcel.unmarshall(data, 0, data.size)
                    parcel.setDataPosition(0)
                    parcel.readBundle(ClassLoader.getSystemClassLoader())?.also { it.putAll(it) }
                } finally { parcel.recycle() }
            }
        } catch (error: IOException) { failed(error) }
          catch (error: NullPointerException) { failed(error) }
          catch (error: IllegalStateException) { failed(error) }
          catch (error: BadParcelableException) { failed(error) }
    }

    fun write(bundle: Bundle, path: File): Boolean = try {
        FileOutputStream(path).use { stream ->
            val parcel = Parcel.obtain()
            try { parcel.writeBundle(bundle); stream.write(parcel.marshall()); stream.flush() }
            finally { parcel.recycle() }
        }
        true
    } catch (error: IOException) { failed(error); false }

    private fun failed(error: Exception): Nothing? {
        Log.w("ViaSessions", "Cannot read or write a tab state", error)
        return null
    }

    /** t4.c.l/t4.b.saveState: COLOR accompanies a successful WebView state, url and scroll are fallbacks. */
    fun capture(view: WebView, color: Int): Bundle = Bundle().apply {
        if (view.saveState(this) != null) putInt("COLOR", color)
        view.url?.takeIf(String::isNotEmpty)?.let { putString("url", it) }
        val scroll = view.scrollX.toLong() or (view.scrollY.toLong() shl 32)
        if (scroll != 0L) putLong("scroll", scroll)
    }

    /** t4.c.k: use the restored current history item, then the stored URL, with the original scroll delays. */
    fun restore(view: WebView, state: Bundle, loadFallback: (String) -> Unit): String? {
        if (state.isEmpty) return null
        PageColorSampler.restoreColor(view, state.getInt("COLOR", PageColorSampler.colorOf(view)))
        val restored = view.restoreState(state)?.currentItem?.url
        val fallback = if (restored.isNullOrEmpty()) state.getString("url")?.takeIf(String::isNotEmpty) else null
        if (fallback != null) loadFallback(fallback)
        val scroll = state.getLong("scroll", 0L)
        if (scroll != 0L) view.postDelayed({
            if (view.scrollY <= 1000) view.scrollTo(scroll.toInt(), (scroll ushr 32).toInt())
        }, if (fallback == null) 100L else 500L)
        return restored
    }
}

/** WebView capture is performed on the UI thread; writeFiles may then run on the browser's IO worker. */
class PendingSessionSnapshot internal constructor(
    private val directory: File,
    private val entries: List<Entry>,
) {
    internal class SavedRow(var value: SessionTab?)
    internal data class Entry(val row: SessionTab, val state: Bundle, val saved: SavedRow)

    fun writeFiles(): List<SessionTab> = entries.map { (row, state, saved) ->
        val target = File(directory, row.id)
        val result = if (!state.isEmpty && SessionState.write(state, target)) row.copy(filePath = target.path)
            else row.copy(filePath = saved.value?.filePath, lastVisitedAt = saved.value?.lastVisitedAt ?: 0L)
        // ua.C/c updates the file reference and timestamp only when the Parcel write succeeds.
        saved.value = result
        result
    }
}
