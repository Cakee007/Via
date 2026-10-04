package dev.ujhhgtg.via.sync

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.attachPasswordVisibilityButton
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import dev.ujhhgtg.via.ui.dp
import java.util.Locale

/** ob.l / k8.a: centered, non-swipeable two-page configuration dialog. */
class WebDavConfigurationDialogFragment : ViaDialogFragment() {
    override val blurMode = 2
    private lateinit var model: WebDavConfigurationModel

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        model = ViewModelProvider(this)[WebDavConfigurationModel::class.java]
        if (!model.initialized) model.initialize(arguments)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = ViewPager2(requireContext()).apply {
        layoutParams = FrameLayout.LayoutParams(-1, -1)
        isUserInputEnabled = false
        adapter = object : FragmentStateAdapter(this@WebDavConfigurationDialogFragment) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = if (position == 0) WebDavCredentialsFragment() else WebDavAdvancedFragment()
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        model.page.observe(viewLifecycleOwner) { (view as ViewPager2).setCurrentItem(it, true) }
        model.result.observe(viewLifecycleOwner) { result ->
            if (result != null) parentFragmentManager.setFragmentResult(RESULT, result)
            dismiss()
        }
    }

    companion object {
        const val RESULT = "webdav_configuration_result"
        fun newInstance(saved: SyncConfiguration?) = WebDavConfigurationDialogFragment().apply {
            arguments = saved?.let { Bundle().apply {
                putString("URL", it.baseUrl); putString("USERNAME", it.username); putString("PASSWORD", it.password)
                putBoolean("DIGEST_AUTH", it.digestAuth); putString("PATH", it.path)
            } }
        }
    }
}

/** ob.m: draft advanced values, unchanged-password rule, and original path normalization. */
class WebDavConfigurationModel : ViewModel() {
    var initialized = false
        private set
    var saved = Bundle()
        private set
    var digest = false
    var path = ""
    val page = MutableLiveData<Int>()
    val result = MutableLiveData<Bundle?>()

    fun initialize(arguments: Bundle?) {
        saved = arguments ?: Bundle().apply {
            if (Locale.getDefault().country.equals("CN", true)) putString("URL", "https://dav.jianguoyun.com/dav/")
        }
        digest = saved.getBoolean("DIGEST_AUTH")
        path = saved.getString("PATH").orEmpty()
        initialized = true
    }

    fun finish(url: String, username: String, password: String, logout: Boolean = false) {
        val unchanged = saved.getString("URL").orEmpty() == url && saved.getString("USERNAME").orEmpty() == username
        val secret = if (logout) "" else password.ifEmpty { if (unchanged) saved.getString("PASSWORD").orEmpty() else "" }
        result.value = Bundle().apply {
            putString("URL", url); putString("USERNAME", username); putString("PASSWORD", secret)
            putBoolean("DIGEST_AUTH", digest); putString("PATH", normalizedPath(path))
        }
    }

    private fun normalizedPath(value: String): String {
        var result = value.trim().replace(Regex("[\\\\:*?\"<>|]"), "")
        while ("//" in result) result = result.replace("//", "/")
        if (result.isNotEmpty() && !result.startsWith('/')) result = "/$result"
        if (result.length > 1 && result.endsWith('/')) result = result.dropLast(1)
        return result
    }
}

/** ob.i / layout o: credentials and the primary actions. */
class WebDavCredentialsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.webdav_configuration, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val model = ViewModelProvider(requireParentFragment())[WebDavConfigurationModel::class.java]
        applySyncFont(view)
        val url = view.findViewById<EditText>(R.id.webdav_url).apply { setText(model.saved.getString("URL")) }
        val username = view.findViewById<EditText>(R.id.webdav_username).apply { setText(model.saved.getString("USERNAME")) }
        val hasPassword = !model.saved.getString("PASSWORD").isNullOrEmpty()
        val password = view.findViewById<EditText>(R.id.webdav_password).apply {
            setText(""); setHint(if (hasPassword) R.string.hint_password_unchanged else R.string.hint_password)
            attachPasswordVisibilityButton(settingsColor(context, R.attr.viaSubtleColor, 0xff444444.toInt()))
        }
        view.findViewById<View>(R.id.webdav_advanced).setOnClickListener { model.page.value = 1 }
        view.findViewById<View>(R.id.webdav_cancel).setOnClickListener { model.result.value = null }
        view.findViewById<View>(R.id.webdav_logout).apply {
            visibility = if (hasPassword) View.VISIBLE else View.GONE
            setOnClickListener { model.finish(url.text.toString().trim(), username.text.toString().trim(), "", logout = true) }
        }
        view.findViewById<View>(R.id.webdav_ok).setOnClickListener {
            val server = url.text.toString().trim()
            val user = username.text.toString().trim()
            val secret = password.text.toString().trim()
            val unchanged = model.saved.getString("URL").orEmpty() == server && model.saved.getString("USERNAME").orEmpty() == user
            val missing = when {
                server.isEmpty() -> url
                user.isEmpty() -> username
                secret.isEmpty() && !unchanged -> password
                else -> null
            }
            if (missing != null) {
                shakeSyncView(missing)
                ViaToast.makeText(requireContext(), getString(R.string.is_required, missing.hint), ViaToast.LENGTH_LONG).show()
            } else model.finish(server, user, secret)
        }
    }
}

/** ob.d / layout n: advanced state updates stay in the same dialog model. */
class WebDavAdvancedFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.webdav_advanced, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val model = ViewModelProvider(requireParentFragment())[WebDavConfigurationModel::class.java]
        applySyncFont(view)
        view.findViewById<ImageView>(R.id.webdav_back).apply {
            setSkinImageResource(R.drawable.chevron_left)
            setOnClickListener { model.page.value = 0 }
        }
        val digest = view.findViewById<CheckBox>(R.id.webdav_digest).apply {
            isChecked = model.digest
            setOnCheckedChangeListener { _, checked -> model.digest = checked }
        }
        view.findViewById<View>(R.id.webdav_digest_row).setOnClickListener { digest.isChecked = !digest.isChecked }
        view.findViewById<EditText>(R.id.webdav_path).apply {
            setText(model.path)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(value: Editable?) { model.path = value.toString().trim() }
            })
        }
    }
}

internal fun applySyncFont(view: View) {
    if (view is TextView) {
        view.typeface = Typeface.create(BrowserPreferences(view.context).selectedTypeface(), view.typeface?.style ?: Typeface.NORMAL)
    }
    if (view is ViewGroup) for (index in 0 until view.childCount) applySyncFont(view.getChildAt(index))
}

/** g6.y.X: repeat the original 24dp horizontal validation movement. */
internal fun shakeSyncView(view: View) {
    ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, view.context.dp(24f).toFloat(), 0f).apply {
        repeatCount = 2; repeatMode = ValueAnimator.RESTART; duration = 280
        interpolator = PathInterpolator(.2f, .2f, .8f, .8f)
        start()
    }
}
