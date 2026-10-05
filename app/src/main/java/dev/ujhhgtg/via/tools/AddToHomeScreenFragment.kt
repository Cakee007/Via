package dev.ujhhgtg.via.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.home.HomeFavoriteIcons
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.common.launchIo
import java.io.File

/** a8.u1 / layout g: editable shortcut title, URL, and replaceable launch icon. */
class AddToHomeScreenFragment : ViaDialogFragment() {
    private lateinit var title: EditText
    private lateinit var url: EditText
    private lateinit var icon: ImageView
    private var chosen: Bitmap? = null
    private val picker = registerForActivityResult(dev.ujhhgtg.via.common.ImagePickerContract()) { uri ->
        if (uri != null) {
            val context = requireContext().applicationContext
            viewLifecycleOwner.launchIo(load@{
                val folder = File(context.externalCacheDir ?: context.cacheDir, "icons")
                if (!folder.exists() && !folder.mkdirs()) return@load false
                val file = File(folder, "cache.png")
                if (file.exists() && !file.delete()) return@load false
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) } ?: return@load false
                if (HomeFavoriteIcons.normalizeStoredFile(file)) chosen = BitmapFactory.decodeFile(file.path)
                true
            }, { if (it && chosen != null) icon.setImageBitmap(chosen) }) { android.util.Log.w("ViaShortcut", "Cannot load shortcut image", it) }
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.favorite_editor, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        title = view.findViewById(R.id.favorite_dialog_title)
        url = view.findViewById(R.id.favorite_dialog_url)
        icon = view.findViewById(R.id.favorite_dialog_icon)
        view.findViewById<TextView>(R.id.favorite_dialog_heading).setText(R.string.add_shortcut)
        val font = BrowserPreferences(requireContext()).selectedTypeface()
        fun style(child: View) {
            if (child is TextView) child.setTypeface(font, child.typeface?.style ?: Typeface.NORMAL)
            if (child is EditText && Build.VERSION.SDK_INT >= 35) child.isLocalePreferredLineHeightForMinimumUsed = false
            if (child is ViewGroup) for (i in 0 until child.childCount) style(child.getChildAt(i))
        }
        style(view)
        icon.layoutParams = icon.layoutParams.apply { width = requireContext().dp(48f); height = width }
        title.setText(state?.getString("title") ?: arguments?.getString("title"))
        url.setText(state?.getString("url") ?: arguments?.getString("url"))
        icon.setImageBitmap(loadIcon())
        icon.setOnClickListener { try { picker.launch(null) } catch (_: android.content.ActivityNotFoundException) { } }
        view.findViewById<View>(R.id.favorite_dialog_cancel).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.favorite_dialog_save).setOnClickListener { save() }
    }
    private fun loadIcon() = HomeFavoriteIcons.loadOrTile(requireContext(), url.text.toString().ifEmpty { "http://viayoo.com/" }, title.text.toString(), requireContext().dp(108f), 0)
    private fun save() {
        val address = url.text.toString().trim()
        val label = title.text.toString()
        val missing = if (address.isEmpty()) url else if (label.isEmpty()) title else null
        if (missing != null) {
            dev.ujhhgtg.via.home.shakeFavoriteInput(missing)
            ViaToast.makeText(requireContext(), getString(R.string.is_required, missing.hint), ViaToast.LENGTH_LONG).show()
            return
        }
        val context = requireContext().applicationContext
        val activity = requireActivity()
        icon.setImageBitmap(loadIcon())
        viewLifecycleOwner.launchIo({ chosen ?: HomeFavoriteIcons.loadOrTile(context, address, label, context.dp(108f), 0) },
            { bitmap -> dismiss(); AddToHomeScreen.request(activity, address, label, bitmap) }) { android.util.Log.w("ViaShortcut", "Cannot create shortcut", it) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::title.isInitialized) { outState.putString("title", title.text.toString()); outState.putString("url", url.text.toString()) }
        super.onSaveInstanceState(outState)
    }
    companion object {
        fun newInstance(url: String, title: String) = AddToHomeScreenFragment().apply { arguments = Bundle().apply { putString("url", url); putString("title", title) } }
    }
}
