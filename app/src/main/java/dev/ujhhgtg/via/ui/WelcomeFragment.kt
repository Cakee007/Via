package dev.ujhhgtg.via.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** y7.c: original res/layout/p.xml, agreement spans, keyboard focus and accept/decline handlers. */
class WelcomeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.welcome, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        WindowInsetsHelper.apply(view)
        val context = requireContext()
        val colors = context.obtainStyledAttributes(R.styleable.ViaAccentColors)
        val accent: Int
        val pressed: Int
        try { accent = colors.getColor(R.styleable.ViaAccentColors_viaAccentColor, Color.TRANSPARENT); pressed = colors.getColor(R.styleable.ViaAccentColors_viaDarkAccentColor, Color.TRANSPARENT) } finally { colors.recycle() }
        val language = if (BrowserPreferences(context).cloudServer == 1) "zh-cn" else "en"
        val terms = getString(R.string.terms_of_use)
        val privacy = getString(R.string.privacy_policy)
        val message = getString(R.string.message_splash_agreement, terms, privacy)
        val agreement = view.findViewById<TextView>(R.id.welcome_agreement)
        agreement.text = SpannableString(message).apply {
            fun link(label: String, page: String) {
                val start = message.indexOf(label)
                if (start < 0) return
                val end = start + label.length
                setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        (requireActivity() as Shell).navigate(PolicyDocumentFragment.newInstance("https://viayoo.com/$language/docs/$page", label))
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(accent), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            link(terms, "terms-of-use.html")
            link(privacy, "privacy-policy.html")
        }
        agreement.movementMethod = LinkMovementMethod.getInstance()
        agreement.setOnKeyListener { target, code, event ->
            val text = target as TextView
            val previous = text.selectionEnd
            if (event.action == KeyEvent.ACTION_DOWN) target.onKeyDown(code, event)
            if (event.action == KeyEvent.ACTION_UP) target.onKeyUp(code, event)
            if (previous == text.selectionEnd && event.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN && target.nextFocusDownId != -1)
                view.findViewById<View>(target.nextFocusDownId)?.requestFocus()
            true
        }
        val accept = view.findViewById<TextView>(R.id.welcome_accept)
        fun shape(color: Int) = GradientDrawable().apply { cornerRadius = resources.getDimension(R.dimen.menu_corner_radius); setColor(color) }
        accept.background = RippleDrawable(ColorStateList.valueOf(pressed), shape(accent), shape(Color.BLACK))
        accept.setOnClickListener {
            BrowserPreferences(context).agreementLevel = 1
            (requireActivity() as Shell).showBrowser()
        }
        view.findViewById<View>(R.id.welcome_decline).setOnClickListener {
            ViaDialog(requireActivity()).title(R.string.dialog_message).message(R.string.agreement_message_short)
                .positive(android.R.string.ok).negative(R.string.exit) { requireActivity().finish() }.show()
        }
    }
}
