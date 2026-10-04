package dev.ujhhgtg.via.sync

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.attachPasswordVisibilityButton
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment

/** hb.a8 / layout m: in-place validation and inline agreement links. */
class CloudLoginDialogFragment : ViaDialogFragment() {
    override val blurMode = 2
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.cloud_login, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        applySyncFont(view)
        val preferences = BrowserPreferences(requireContext())
        val username = view.findViewById<EditText>(R.id.cloud_username).apply { setText(preferences.languageUserName) }
        val password = view.findViewById<EditText>(R.id.cloud_password).apply {
            attachPasswordVisibilityButton(settingsColor(context, R.attr.viaSubtleColor, 0xff444444.toInt()))
        }
        val agreed = view.findViewById<CheckBox>(R.id.cloud_agree)
        val agreement = view.findViewById<TextView>(R.id.cloud_agreement)
        val terms = getString(R.string.terms_of_use)
        val privacy = getString(R.string.privacy_policy)
        val words = getString(R.string.read_and_agree, terms, privacy)
        agreement.text = SpannableString(words).apply {
            for ((label, page) in listOf(terms to "terms-of-use.html", privacy to "privacy-policy.html")) {
                val start = words.indexOf(label)
                if (start < 0) continue
                val end = start + label.length
                setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val locale = if (preferences.cloudServer == 1) "zh-cn" else "en"
                        CloudPolicyDialogFragment.newInstance("https://viayoo.com/$locale/docs/$page")
                            .show(childFragmentManager, "CloudPolicy")
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(settingsColor(requireContext(), R.attr.viaAccentColor, 0xff6f8de1.toInt())), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        agreement.movementMethod = LinkMovementMethod.getInstance()
        agreement.setOnClickListener { if (agreement.selectionStart == agreement.selectionEnd) agreed.isChecked = !agreed.isChecked }
        view.findViewById<View>(R.id.cloud_login_cancel).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.cloud_login_ok).setOnClickListener {
            if (!agreed.isChecked) {
                shakeSyncView(agreed); shakeSyncView(agreement)
                ViaToast.makeText(requireContext(), words, ViaToast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val missing = when {
                username.text.isEmpty() -> username
                password.text.isEmpty() -> password
                else -> null
            }
            if (missing != null) {
                shakeSyncView(missing)
                ViaToast.makeText(requireContext(), getString(R.string.is_required, missing.hint), ViaToast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            parentFragmentManager.setFragmentResult(RESULT, Bundle().apply {
                putString("username", username.text.toString()); putString("password", password.text.toString())
            })
            dismiss()
        }
    }

    companion object { const val RESULT = "cloud_login_result" }
}
