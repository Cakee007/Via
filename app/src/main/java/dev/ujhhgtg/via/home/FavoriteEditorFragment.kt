package dev.ujhhgtg.via.home

import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isGone
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.Favorite
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.common.ImagePickerContract
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import dev.ujhhgtg.via.ui.dp
import java.io.File
import java.util.concurrent.Executors

/** qa.h0 + layout g, including stored-icon editing and fav_result on successful save. */
class FavoriteEditorFragment : ViaDialogFragment() {
    override val blurMode = 2
    private lateinit var database: BrowserDatabase
    private lateinit var repository: FavoritesRepository
    private lateinit var preferences: BrowserPreferences
    private lateinit var title: EditText
    private lateinit var url: EditText
    private lateinit var heading: TextView
    private lateinit var icon: ImageView
    private lateinit var clear: ImageView
    private var favorite = Favorite(url = "", title = "")
    private var pickedIcon: String? = null
    private var lastSave = 0L
    private val worker = Executors.newSingleThreadExecutor()
    private val picker = registerForActivityResult(ImagePickerContract()) { uri ->
        if (uri == null || uri.authority == null) return@registerForActivityResult
        val host = requireActivity()
        worker.execute {
            val file = File(HomeFavoriteIcons.directory(host), "cache.png")
            val copied = runCatching {
                val directory = file.parentFile ?: return@runCatching null
                if (directory.exists() && !directory.isDirectory && !directory.delete()) return@runCatching null
                if (!directory.exists() && !directory.mkdirs()) return@runCatching null
                if (file.exists() && !file.delete()) return@runCatching null
                host.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output, 8192); output.flush() }
                } ?: return@runCatching null
                file.path
            }.getOrNull()
            host.runOnUiThread { if (copied != null && view != null) {
                GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT) // qa.h0.y3, even if editing is later cancelled.
                pickedIcon = copied; showIcon()
            } }
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        preferences = BrowserPreferences(requireContext())
        GeneratedDocumentState.initialize(preferences)
        database = BrowserDatabase(requireContext()); repository = FavoritesRepository(database)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.favorite_editor, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        title = view.findViewById(R.id.favorite_dialog_title)
        url = view.findViewById(R.id.favorite_dialog_url)
        heading = view.findViewById(R.id.favorite_dialog_heading)
        icon = view.findViewById(R.id.favorite_dialog_icon)
        clear = view.findViewById(R.id.favorite_dialog_clear)
        fun applyTypeface(child: View) {
            if (child is TextView) child.setTypeface(preferences.selectedTypeface(), child.typeface?.style ?: Typeface.NORMAL)
            if (child is ViewGroup) for (index in 0 until child.childCount) applyTypeface(child.getChildAt(index))
        }
        applyTypeface(view)
        view.findViewById<View>(R.id.favorite_dialog_cancel).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.favorite_dialog_save).setOnClickListener { save() }
        icon.setOnClickListener {
            if (clear.isGone) {
                try { picker.launch(null) }
                catch (_: android.content.ActivityNotFoundException) { }
            }
        }
        icon.setOnLongClickListener { pickedIcon = ""; showIcon(); true }
        clear.setOnClickListener { pickedIcon = ""; showIcon() }
        if (preferences.favoritesInfo and 2097152 != 0) { icon.visibility = View.GONE; clear.visibility = View.GONE }
        val address = arguments?.getString("url").orEmpty()
        val initialTitle = arguments?.getString("title")
        val id = arguments?.getInt("id", -1) ?: -1
        if (id < 0 && address.isEmpty()) { heading.setText(R.string.add_favorite); return }
        val host = requireActivity()
        worker.execute {
            val found = if (id >= 0) repository.list().firstOrNull { it.id == id } else repository.findByUrl(address)
            val loaded = found ?: Favorite(url = address, title = initialTitle)
            host.runOnUiThread {
                if (this.view == null) return@runOnUiThread
                favorite = loaded
                if (loaded.id <= 0) heading.setText(R.string.action_add_to_homepage)
                title.setText(loaded.title); url.setText(loaded.url); showIcon()
            }
        }
    }
    private fun showIcon() {
        if (preferences.favoritesInfo and 2097152 != 0) return
        val context = requireContext()
        val host = requireActivity()
        val owner = viewLifecycleOwner.lifecycle
        // h0.p3/v3 decodes at 42dp, while g.xml displays in its 36dp ImageView.
        val size = context.dp(42f)
        val radius = ((preferences.favoritesInfo shr 14 and 127) / 100f * size / 2f).toInt()
        val path = pickedIcon
        val address = favorite.url
        // qa.h0.C3/D3 perform both stored and picked image decoding on the IO scheduler.
        worker.execute {
            val bitmap = if (path == null) HomeFavoriteIcons.load(context, address, size, radius)
                else path.takeIf(String::isNotEmpty)?.let { HomeFavoriteIcons.loadFile(it, size, size, radius) }
            host.runOnUiThread {
                if (owner.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED) return@runOnUiThread
                icon.setImageDrawable(bitmap?.toDrawable(resources) ?: HomeFavoriteIcons.placeholder(context, radius * 2))
                clear.visibility = if (bitmap == null) View.GONE else View.VISIBLE
            }
        }
    }
    private fun save() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSave < 300L) return
        lastSave = now
        val name = title.text.toString()
        val address = url.text.toString().trim()
        val missing = if (name.isEmpty()) title else if (address.isEmpty()) url else null
        if (missing != null) {
            shakeFavoriteInput(missing)
            ViaToast.makeText(requireContext(), getString(R.string.is_required, missing.hint), ViaToast.LENGTH_SHORT).show()
            return
        }
        val updated = favorite.copy(title = name, url = UrlResolver.normalizeInput(address, preferences.effectiveSearchUrl()) ?: address)
        val iconPath = pickedIcon
        val host = requireActivity()
        worker.execute {
            val id = repository.save(updated)
            if (id > 0 && iconPath != null) {
                if (iconPath.isEmpty()) HomeFavoriteIcons.remove(host, updated.url) else HomeFavoriteIcons.adoptCache(host, iconPath, updated.url)
            }
            host.runOnUiThread {
                if (view == null) return@runOnUiThread
                if (id < 0) ViaToast.makeText(host, getString(R.string.toast_operation_failed), ViaToast.LENGTH_SHORT).show()
                else {
                    GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT) // qa.h0.m3
                    (host as androidx.fragment.app.FragmentActivity).supportFragmentManager.setFragmentResult("fav_result",
                        Bundle().apply { putInt("result_id", if (id > 0) id else favorite.id) })
                    dismiss()
                }
            }
        }
    }
    override fun onDestroy() { worker.execute { database.close() }; worker.shutdown(); super.onDestroy() }
    companion object {
        fun newInstance(url: String, title: String? = null) = FavoriteEditorFragment().apply {
            arguments = Bundle().apply { putString("url", url); putString("title", title) }
        }
    }
}

/** g6.y.X: validation shakes the field; the error message is a separate Via toast. */
internal fun shakeFavoriteInput(view: View) {
    android.animation.ObjectAnimator.ofFloat(view, "translationX", 0f, view.context.dp(24f).toFloat(), 0f).apply {
        repeatCount = 2
        repeatMode = android.animation.ValueAnimator.RESTART
        duration = 280L
        interpolator = android.view.animation.PathInterpolator(.2f, .2f, .8f, .8f)
        start()
    }
}
