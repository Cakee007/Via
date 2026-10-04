package dev.ujhhgtg.via.search

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dp

/** tb.w0: multiline URL editor with independent collapse/go actions and IME animation. */
class ExpandedUrlInputFragment : Fragment() {
    private lateinit var input: EditText
    private lateinit var controls: LinearLayout
    private fun dp(value: Int) = requireContext().dp(value.toFloat())
    private fun color(attribute: Int): Int = requireContext().obtainStyledAttributes(intArrayOf(attribute)).let { values -> try { values.getColor(0, 0) } finally { values.recycle() } }
    private fun icon(resource: Int, label: Int, action: () -> Unit) = ImageView(requireContext()).apply {
        setSkinImageResource(resource); contentDescription = getString(label); setColorFilter(color(R.attr.viaSubtleColor))
        setPadding(dp(13), dp(13), dp(13), dp(13)); setBackgroundResource(R.drawable.rounded_rect_ripple)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(3), 0, dp(3), 0) }
        setOnClickListener { action() }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        controls = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM) }
        controls.addView(icon(R.drawable.search_collapse, R.string.desc_collapse_text) { deliver(false) })
        controls.addView(Space(context), LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(icon(R.drawable.search_go, R.string.search_hint) { deliver(true) })
        input = EditText(context).apply {
            id = View.generateViewId(); setBackgroundColor(0); setPadding(dp(16), dp(16), dp(16), dp(16))
            setHint(R.string.search_hint); imeOptions = 2; inputType = 655360; setSelectAllOnFocus(true)
            setTextColor(color(R.attr.viaPrimaryTextColor)); setHintTextColor(color(R.attr.viaPrimaryTextColor))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO; setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.search_input_text_size).toFloat())
            maxLines = 999; setSingleLine(false); setHorizontallyScrolling(false); gravity = Gravity.TOP
            setLineSpacing(lineSpacingExtra, 1.2f); typeface = BrowserPreferences(context).selectedTypeface()
            layoutParams = FrameLayout.LayoutParams(-1, -2)
        }
        val scroll = ScrollView(context).apply { isFillViewport = true; addView(input); layoutParams = FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(48) } }
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1); setBackgroundColor(color(R.attr.viaBackgroundColor))
            addView(controls); addView(scroll); descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            isFocusable = false; isFocusableInTouchMode = false; setOnClickListener {}
        }
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { parentFragmentManager.popBackStack() }
        })
        val value = state?.getString("text") ?: arguments?.getString("url").orEmpty()
        input.setText(value)
        val selection = state?.getIntArray("selection") ?: arguments?.getIntArray("selection")
        if (selection == null || selection.size != 2 || selection[0] == -1) input.setSelection(value.length)
        else input.setSelection(selection[0].coerceIn(0, value.length), selection[1].coerceIn(0, value.length))
        input.setOnKeyListener { _, key, event ->
            if (key != KeyEvent.KEYCODE_ENTER) false else { if (event.action == KeyEvent.ACTION_UP) deliver(true); true }
        }
        input.setOnEditorActionListener { _, action, event -> if (action == 2 && event == null) { deliver(true); true } else false }
        input.post { if (input.requestFocus()) (input.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
        WindowInsetsHelper.apply(view)
        ViewCompat.setWindowInsetsAnimationCallback(controls, object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            private var start = 0f
            private var end = 0f
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) start = controls.bottom.toFloat()
            }
            override fun onStart(animation: WindowInsetsAnimationCompat, bounds: WindowInsetsAnimationCompat.BoundsCompat): WindowInsetsAnimationCompat.BoundsCompat {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    val insets = ViewCompat.getRootWindowInsets(controls)
                    end = if (insets == null) start else controls.bottom + (bounds.upperBound.bottom - bounds.lowerBound.bottom) * if (insets.isVisible(WindowInsetsCompat.Type.ime())) -1f else 1f
                }
                return bounds
            }
            override fun onProgress(insets: WindowInsetsCompat, animations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
                if (start != end) animations.firstOrNull { it.typeMask and WindowInsetsCompat.Type.ime() != 0 }?.let { controls.translationY = (start - end) * (1f - it.interpolatedFraction) }
                return insets
            }
        })
    }
    private fun deliver(go: Boolean) {
        parentFragmentManager.setFragmentResult("resultUrl", Bundle().apply {
            putString("resultUrl", input.text.toString()); putBoolean("resultGo", go); putIntArray("resultSelection", intArrayOf(input.selectionStart, input.selectionEnd))
        })
        parentFragmentManager.popBackStack()
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("text", input.text.toString()); out.putIntArray("selection", intArrayOf(input.selectionStart, input.selectionEnd)); super.onSaveInstanceState(out) }
    companion object {
        fun newInstance(url: String, selection: IntArray) = ExpandedUrlInputFragment().apply { arguments = Bundle().apply { putString("url", url); putIntArray("selection", selection) } }
    }
}
