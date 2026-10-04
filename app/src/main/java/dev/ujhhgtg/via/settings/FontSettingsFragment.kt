package dev.ujhhgtg.via.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.LruCache
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.fonts.FontEntry
import dev.ujhhgtg.via.fonts.FontRepository
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** hb.j2: imported-font previews, selection, replacement, rename and deletion. */
class FontSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var repository: FontRepository
    private lateinit var rows: SettingsRowsAdapter
    private val fonts = mutableListOf<FontEntry>()
    private val previews = LruCache<String, Typeface>(12)
    private val invalidFonts = hashSetOf<String>()
    private var selected = ""

    // x5.c: the original OpenDocument contract starts in the Downloads directory.
    private val importer = registerForActivityResult(object : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                "content://com.android.externalstorage.documents/document/primary:Download".toUri())
    }) { uri -> uri?.let(::importFont) }

    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(R.string.font)
        toolbar.addAction(R.drawable.plus, R.string.action_new) {
            try {
                importer.launch(arrayOf("font/ttf", "font/otf", "font/woff", "font/woff2"))
            } catch (error: ActivityNotFoundException) {
                android.util.Log.w("ViaFonts", "Unable to open font picker", error)
                ViaToast.makeText(requireContext(), R.string.toast_operation_failed, ViaToast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        repository = FontRepository(preferences.fontDirectory)
        rows = SettingsRowsAdapter { row ->
            fonts.firstOrNull { it.hashCode() == row.id }?.let { if (select(it)) bindRows() }
        }.apply {
            onRowBound = { rowView, row ->
                fonts.firstOrNull { it.hashCode() == row.id }?.let { font ->
                    val face = preview(font)
                    bindSettingsChoicePreview(rowView, face, if (isInvalid(font)) .5f else 1f)
                }
            }
            onLongClick = { anchor, row ->
                fonts.firstOrNull { it.hashCode() == row.id }?.takeIf { it.name.isNotEmpty() }?.let { actions(anchor, it) }
                true
            }
        }
        list.adapter = rows
        reload()
    }

    private fun reload() = background({ repository.list() }) { result ->
        selected = preferences.uiFont
        fonts.clear()
        fonts.addAll(result)
        bindRows()
    }

    private fun bindRows() = rows.submit(fonts.map {
        SettingsChoiceRow(it.hashCode(), it.name.ifEmpty { getString(R.string.default_set) }, it.name == selected)
    })

    /** hb.j2.u3/w3: failed fonts stay visible, with only the preview text dimmed. */
    private fun preview(font: FontEntry): Typeface {
        if (font.name.isEmpty() || isInvalid(font)) return Typeface.DEFAULT
        previews[font.path]?.let { return it }
        val loaded = try {
            Typeface.createFromFile(font.path)
        } catch (error: Exception) {
            android.util.Log.w("ViaFonts", "Unable to preview ${font.name}", error)
            null
        }
        if (loaded == null) {
            invalidFonts.add(font.path)
            return Typeface.DEFAULT
        }
        previews.put(font.path, loaded)
        return loaded
    }

    private fun isInvalid(font: FontEntry) = font.name.isNotEmpty() && font.path in invalidFonts

    private fun select(font: FontEntry): Boolean {
        if (font.name == selected || isInvalid(font)) return false
        selected = font.name
        preferences.uiFont = selected
        return true
    }

    private fun actions(anchor: View, font: FontEntry) {
        val options = buildList {
            if (!isInvalid(font)) add(ViaDialog.Item(1, getString(R.string.action_edit)))
            add(ViaDialog.Item(2, getString(R.string.action_delete)))
        }
        ViaDialog(requireActivity()).items(options, onClick = { index ->
            if (options[index].id == 1) rename(font) else delete(font)
        }).showAnchored(anchor)
    }

    /** hb.j2.t3 updates the visible list before attempting to delete the stored file. */
    private fun delete(font: FontEntry) {
        if (!fonts.remove(font)) return
        if (font.name == selected) select(fonts[0])
        bindRows()
        repository.delete(font.name)
    }

    private fun rename(font: FontEntry) {
        val dot = font.name.lastIndexOf('.')
        val stem = if (dot > 0) font.name.substring(0, dot) else font.name
        val suffix = if (dot > 0) font.name.substring(dot + 1) else ""
        ViaDialog(requireActivity()).title(R.string.rename).input(stem, stem, 1)
            .positive(android.R.string.ok) { _, result ->
                val name = result.edit?.firstOrNull().orEmpty()
                if (name.isEmpty() || name.equals(stem, ignoreCase = true)) return@positive
                val replacement = repository.rename(font.name, name + if (suffix.isEmpty()) "" else ".$suffix")
                    ?: return@positive
                val index = fonts.indexOf(font)
                if (index < 0) return@positive
                fonts[index] = replacement
                if (selected == font.name) select(replacement)
                // hb.j2.i3 retains the row's position until the next directory reload.
                bindRows()
            }.negative(android.R.string.cancel).show()
    }

    private fun importFont(uri: Uri) {
        val context = requireContext().applicationContext
        background({ repository.importFont(context.contentResolver, uri) }) { result ->
            val font = result.font
            if (font == null) {
                ViaToast.makeText(requireContext(), getString(R.string.unable_to_add_font, result.error), ViaToast.LENGTH_LONG).show()
            } else {
                invalidFonts.remove(font.path)
                previews.remove(font.path)
                if (selected == font.name && preview(font) === Typeface.DEFAULT && fonts.isNotEmpty()) select(fonts[0])
                reload()
            }
        }
    }

    private fun <T : Any> background(work: () -> T, consume: (T) -> Unit) {
        viewLifecycleOwner.launchIo(work, consume) { android.util.Log.w("ViaFonts", "Unable to update fonts", it) }
    }
}
