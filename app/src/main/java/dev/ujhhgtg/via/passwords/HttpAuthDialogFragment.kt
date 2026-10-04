package dev.ujhhgtg.via.passwords

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import dev.ujhhgtg.via.ui.dp

/** c8.gb and original layout h: HTTP sign-in and authenticated saved accounts. */
class HttpAuthDialogFragment : ViaDialogFragment() {
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var authentication: PasswordAuthenticator
    private var credentialRequest = 0
    private val credentials = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (::authentication.isInitialized) authentication.onActivityResult(credentialRequest, result.resultCode)
    }
    private var answer: Bundle = Bundle()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.password_auth_dialog, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        dialog?.setCanceledOnTouchOutside(false)
        authentication = PasswordAuthenticator(requireActivity()) { intent, request ->
            credentialRequest = request
            credentials.launch(intent)
        }
        username = view.findViewById<EditText>(R.id.auth_username).apply { setAutofillHints(View.AUTOFILL_HINT_USERNAME) }
        password = view.findViewById<EditText>(R.id.auth_password).apply {
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            inputType = 524417
            if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
        }
        applyFont(view)
        view.findViewById<View>(R.id.auth_cancel).setOnClickListener { answer = Bundle(); dismiss() }
        view.findViewById<View>(R.id.auth_ok).setOnClickListener {
            answer = Bundle().apply {
                putString("username", username.text.toString().trim())
                putString("password", password.text.toString().trim())
            }
            dismiss()
        }
        val repository = PasswordRepository.get(requireContext())
        parentFragmentManager.setFragmentResultListener(PICK_RESULT, viewLifecycleOwner) { _, result ->
            result.getString("id")?.let { id -> repository.async({ repository.get(id) }) { it.getOrNull()?.let(::fill) } }
        }
        val picker = view.findViewById<ImageView>(R.id.auth_passwords)
        picker.setOnClickListener {
            PasswordPickerFragment.newInstance(arguments?.getString("url").orEmpty(), PICK_RESULT)
                .show(parentFragmentManager, "PasswordPicker")
        }
        val list = view.findViewById<RecyclerView>(R.id.auth_suggestions).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            overScrollMode = if (Build.VERSION.SDK_INT >= 32) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(out: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    out.set(context.dp(6f), context.dp(4f), context.dp(6f), context.dp(4f))
                }
            })
        }
        repository.async({ repository.list().isEmpty() to repository.forUrl(arguments?.getString("url").orEmpty()) }) { result ->
            if (this.view !== view) return@async
            val (empty, records) = result.getOrNull() ?: return@async
            picker.visibility = if (empty) View.GONE else View.VISIBLE
            list.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
            list.adapter = object : RecyclerView.Adapter<AccountHolder>() {
                override fun getItemCount() = records.size
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) = AccountHolder(TextView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(-2, -1)
                    setPadding(context.dp(8f), 0, context.dp(8f), 0)
                    maxWidth = context.dp(120f); gravity = Gravity.CENTER; setSingleLine()
                    isHorizontalFadingEdgeEnabled = true; setFadingEdgeLength(context.dp(24f))
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                    setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
                    typeface = BrowserPreferences(context).selectedTypeface()
                    background = ContextCompat.getDrawable(context, R.drawable.flat_ripple)
                })
                override fun onBindViewHolder(holder: AccountHolder, position: Int) {
                    holder.text.text = records[position].username
                    holder.text.setOnClickListener { fill(records[position]) }
                }
            }
        }
    }

    private fun fill(record: PasswordRecord) {
        authentication.authenticate(getString(R.string.fill_password), getString(R.string.unlock_device_to_fill_password)) {
            PasswordRepository.get(requireContext()).async({ PasswordRepository.get(requireContext()).get(record.id, true) }) { loaded ->
                if (view != null) loaded.getOrNull()?.let { username.setText(it.username); password.setText(it.password) }
            }
        }
    }

    private fun applyFont(view: View) {
        if (view is TextView) view.typeface = Typeface.create(BrowserPreferences(requireContext()).selectedTypeface(), view.typeface?.style ?: Typeface.NORMAL)
        if (view is ViewGroup) for (index in 0 until view.childCount) applyFont(view.getChildAt(index))
    }

    override fun onDismiss(dialog: DialogInterface) {
        parentFragmentManager.setFragmentResult(RESULT, answer)
        super.onDismiss(dialog)
    }
    override fun onDestroyView() { authentication.dispose(); super.onDestroyView() }

    private class AccountHolder(val text: TextView) : RecyclerView.ViewHolder(text)
    companion object {
        const val RESULT = "http_auth_result"
        private const val PICK_RESULT = "http_auth_password"
        fun newInstance(url: String) = HttpAuthDialogFragment().apply { arguments = Bundle().apply { putString("url", url) } }
    }
}
