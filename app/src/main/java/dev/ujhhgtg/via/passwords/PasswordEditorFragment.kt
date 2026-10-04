package dev.ujhhgtg.via.passwords

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.attachPasswordVisibilityButton
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp

/** va.s -> o8.h: id-addressed secure editor with its own back-stack entry and result. */
class PasswordEditorFragment : SettingsPageFragment() {
    private val operations = PasswordPageOperations(this)
    private val repository get() = operations.repository
    private lateinit var body: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var noteInput: EditText
    private var editing: PasswordRecord? = null
    private var draft: PasswordRecord? = null
    private var needsAuthentication = false
    private var editorChanged: () -> Boolean = { false }
    private var editorSnapshot: () -> PasswordRecord? = { null }
    private var saveEditor: () -> Unit = {}
    private fun text(id: Int) = getString(id)
    private fun dp(value: Float) = requireContext().dp(value)
    private fun <T> work(block: () -> T, success: (T) -> Unit) = operations.work(block, success)
    // va.s starts with an empty v9.f; its UUID and timestamps are assigned by b3 on save.
    private fun newRecord() = PasswordRecord(id = "", name = "", updatedAt = 0, createdAt = 0)

    override fun onCreate(state: Bundle?) { super.onCreate(state); operations.restoreState(state) }
    override fun configureToolbar(toolbar: SettingsToolbar) {
        toolbar.setTitle(if (arguments?.getString("id").isNullOrEmpty()) R.string.action_new else R.string.action_edit)
        toolbar.addAction(null, R.string.action_save) { saveEditor() }
    }
    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View {
        body = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        renderEditor(editing ?: newRecord(), draft)
        return body
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // o8.i routes button Back to va.s.T2; keep that confirmation on older platforms too.
        if (!predictiveBackSupported()) requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = onToolbarBack() })
        if (editing?.id.isNullOrEmpty() && draft == null) arguments?.getString("id")?.let(::loadRecord)
    }
    private fun loadRecord(id: String) {
        work({ repository.get(id, true) }) { record ->
            draft = null
            val loaded = record ?: newRecord()
            editing = loaded
            toolbar.setTitle(if (loaded.id.isEmpty()) R.string.action_new else R.string.action_edit)
            nameInput.setText(loaded.name)
            usernameInput.setText(loaded.username)
            passwordInput.setText(loaded.password.orEmpty())
            noteInput.setText(loaded.note.orEmpty())
        }
    }
    private fun finishEditor() { super.onToolbarBack() }
    override fun allowPredictiveBack() = !editorChanged()
    override fun onToolbarBack() {
        if (!editorChanged()) finishEditor()
        else ViaDialog(requireActivity()).title(R.string.dialog_message).message(R.string.modified_content_has_not_been_saved_message)
            .positive(R.string.save_and_exit) { _, _ -> saveEditor() }
            .negative(R.string.exit) { finishEditor() }.show()
    }
    override fun onResume() {
        super.onResume()
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (needsAuthentication) {
            body.visibility = View.GONE
            operations.authentication.authenticate(text(R.string.view_password), text(R.string.unlock_device_to_view_password), ::finishEditor) {
                body.visibility = View.VISIBLE
            }
        }
    }
    override fun onPause() {
        val input = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        input.hideSoftInputFromWindow(body.windowToken, 0)
        super.onPause()
    }
    override fun onStop() {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        needsAuthentication = true
        super.onStop()
    }
    override fun onSaveInstanceState(out: Bundle) { operations.saveState(out); super.onSaveInstanceState(out) }
    override fun onDestroyView() {
        draft = editorSnapshot()
        operations.destroyView()
        super.onDestroyView()
    }
    private fun renderEditor(record: PasswordRecord, draft: PasswordRecord? = null) {
        body.removeAllViews()
        toolbar.setTitle(if (record.id.isEmpty()) R.string.action_new else R.string.action_edit)
        editing = record
        val displayed = draft ?: record
        body.setPadding(0, dp(12f), 0, 0)
        fun field(hintName: Int, initial: String, input: Int, scrolling: Boolean = false): EditText {
            val field = EditText(requireContext()).apply {
                if (!scrolling) {
                    // va.s.X2 calls h6.a.S before h6.a.d: record the platform
                    // start/end padding before the underline background resolves
                    // its own padding, retaining the explicit relative padding.
                    setPaddingRelative(paddingStart, dp(10f), paddingEnd, dp(10f))
                    setBackgroundResource(R.drawable.via_dialog_input)
                }
                hint = text(hintName); setText(initial)
                setTextColor(settingsColor(requireContext(), R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
                setHintTextColor(settingsColor(requireContext(), R.attr.viaSecondaryTextColor, 0xff666666.toInt()))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                if (android.os.Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                // va.s.f3 sets line limits before setInputType(129). Calling
                // setSingleLine afterwards replaces PasswordTransformationMethod.
                if (input and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0) {
                    setLines(1); minLines = 1; maxLines = 1
                    if (scrolling) { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END }
                }
                inputType = input
                typeface = BrowserPreferences(requireContext()).selectedTypeface()
                gravity = Gravity.CENTER_VERTICAL; textDirection = View.TEXT_DIRECTION_LOCALE
                if (scrolling) { setBackgroundColor(android.graphics.Color.TRANSPARENT); setPadding(0, 0, 0, 0) }
            }
            // va.s uses l1 for the first two fields, a horizontal viewport whose EditText
            // is unpadded; the original does not attach a clear-text icon to these fields.
            val container = if (scrolling) HorizontalScrollView(requireContext()).apply {
                isHorizontalScrollBarEnabled = false; isVerticalScrollBarEnabled = false; isFillViewport = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                setBackgroundResource(R.drawable.via_dialog_input); setPadding(0, dp(10f), 0, dp(10f))
                addView(field, FrameLayout.LayoutParams(-2, -2))
            } else field
            body.addView(container, LinearLayout.LayoutParams(-1, -2).apply {
                marginStart = dp(16f); marginEnd = dp(16f); bottomMargin = dp(12f)
            })
            return field
        }
        val name = field(R.string.hint_url, displayed.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, scrolling = true)
        val username = field(R.string.hint_username, displayed.username, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, scrolling = true)
        val password = field(R.string.hint_password, displayed.password.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        password.attachPasswordVisibilityButton(settingsColor(requireContext(), R.attr.viaSubtleColor, 0xff444444.toInt()))
        val note = field(R.string.note, displayed.note.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply { setLines(5); minLines = 5; maxLines = 5; setHorizontallyScrolling(false); gravity = Gravity.TOP; textDirection = View.TEXT_DIRECTION_LTR }
        nameInput = name; usernameInput = username; passwordInput = password; noteInput = note
        password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
        editorSnapshot = { requireNotNull(editing).copy(name = name.text.toString(), username = username.text.toString(),
            password = password.text.toString(), note = note.text.toString()) }
        editorChanged = {
            val original = requireNotNull(editing)
            PasswordCsv.normalizeName(name.text.toString()) != original.name || username.text.toString().trim() != original.username ||
                password.text.toString().trim() != original.password.orEmpty() || note.text.toString().trim() != original.note.orEmpty()
        }
        saveEditor = save@{
            if (!editorChanged()) { finishEditor(); return@save }
            val original = requireNotNull(editing)
            val host = PasswordCsv.normalizeName(name.text.toString())
            val user = username.text.toString().trim()
            val secret = password.text.toString().trim()
            val invalid = when { host.isEmpty() -> name; user.isEmpty() -> username; secret.isEmpty() -> password; else -> null }
            if (invalid != null) {
                // g6.n.j(field, true): the hint supplies the toast label; focus is unchanged.
                dev.ujhhgtg.via.ui.ViaToast.makeText(requireContext(), getString(R.string.is_required, invalid.hint),
                    dev.ujhhgtg.via.ui.ViaToast.LENGTH_SHORT).show()
                android.animation.ObjectAnimator.ofFloat(invalid, View.TRANSLATION_X, 0f, dp(24f).toFloat(), 0f).apply {
                    repeatCount = 2; repeatMode = android.animation.ValueAnimator.RESTART; duration = 280L
                    interpolator = android.view.animation.PathInterpolator(.2f, .2f, .8f, .8f)
                    start()
                }
                return@save
            }
            val update = original.copy(name = host, username = user, password = secret, note = note.text.toString().trim())
            work({ repository.findId(host, user) }) { duplicate ->
                if (duplicate != null && duplicate != original.id) {
                    ViaDialog(requireActivity()).title(text(R.string.password_exists))
                        .message(requireContext().getString(R.string.password_exists_message, host))
                        .positive(android.R.string.ok) { _, _ ->
                            loadRecord(duplicate)
                            android.animation.ObjectAnimator.ofFloat(password, "alpha", 1f, 0f, 1f, 0f, 1f).apply { duration = 1200L; start() }
                        }
                        .negative(android.R.string.cancel).show()
                } else work({
                    val now = System.currentTimeMillis()
                    val saved = if (update.id.isEmpty()) update.copy(id = java.util.UUID.randomUUID().toString().lowercase(java.util.Locale.ROOT),
                        updatedAt = now, createdAt = now) else update.copy(updatedAt = now)
                    if (repository.save(saved)) saved.id else null
                }) { savedId ->
                    if (savedId != null) {
                        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString("result", savedId) })
                        finishEditor()
                    }
                }
            }
        }
    }

    companion object {
        internal const val RESULT = "password_editor_result"
        fun newInstance(id: String?) = PasswordEditorFragment().apply {
            if (!id.isNullOrEmpty()) arguments = Bundle().apply { putString("id", id) }
        }
    }
}
