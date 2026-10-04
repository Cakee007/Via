package dev.ujhhgtg.via.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isGone
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsRecyclerView
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.settings.settingsRipple
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialogFragment
import dev.ujhhgtg.via.ui.dp
import java.util.Locale

/** s6.V9: callers in the browser pass its child manager, as for the original j8.p. */
object ReadAloudDialog {
    fun show(manager: FragmentManager) { ReadAloudDialogFragment().show(manager, "read_aloud") }
}

/** j8.p -> k8.a, layout/k: the resident task is inspected once when the panel is created. */
class ReadAloudDialogFragment : ViaDialogFragment() {
    override val blurMode = 2
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var controller: ReadAloudController
    private lateinit var list: RecyclerView
    private lateinit var play: ImageView
    private lateinit var controls: LinearLayout
    private lateinit var empty: TextView
    private var sentences = emptyList<String>()
    private var taskId: String? = null
    private var selected = 0
    private var preview = -1
    private var resumeScrollingAt = 0L
    private val adapter = Sentences()
    private val commitSeek = Runnable {
        if (sentences.isNotEmpty()) {
            resumeScrollingAt = 0
            playIndex(preview.coerceIn(0, sentences.lastIndex))
            preview = -1
        }
    }
    private val listener: (ReadAloudTask?) -> Unit = { task ->
        if (task != null) {
            if (task.id == taskId && preview < 0) select(task.index, smooth = true)
            updatePlay(!task.isFinished && task.isPlaying)
        }
    }

    override fun onCreate(state: Bundle?) { super.onCreate(state); controller = ReadAloudController.get(requireContext()) }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val root = FrameLayout(context)
        empty = TextView(context).apply {
            text = getString(R.string.empty_hint); gravity = Gravity.CENTER; visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_empty_state_size).toFloat())
            setTextColor(color(R.attr.viaSecondaryTextColor))
        }
        root.addView(empty, FrameLayout.LayoutParams(-1, -1))
        list = SettingsRecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context); itemAnimator = null
            clipToPadding = false; setPadding(0, context.dp(16f), 0, context.dp(16f))
            adapter = this@ReadAloudDialogFragment.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(view: RecyclerView, state: Int) {
                    if (state == RecyclerView.SCROLL_STATE_DRAGGING) resumeScrollingAt = -1
                    else if (state == RecyclerView.SCROLL_STATE_IDLE && resumeScrollingAt == -1L) resumeScrollingAt = SystemClock.elapsedRealtime() + 3000
                }
            })
        }
        root.addView(list, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = context.dp(48f) })
        controls = LinearLayout(context)
        root.addView(controls, FrameLayout.LayoutParams(-1, context.dp(48f), Gravity.BOTTOM))
        fun button(icon: Int, description: Int, clicked: () -> Unit): ImageView = ImageView(context).apply {
            setImageResource(icon); setColorFilter(color(R.attr.viaSubtleColor)); background = settingsRipple(context)
            setPadding(context.dp(13f), context.dp(13f), context.dp(13f), context.dp(13f))
            contentDescription = getString(description)
            setOnClickListener { clicked() }
            controls.addView(this, LinearLayout.LayoutParams(0, -1, 1f))
        }
        val settings = button(R.drawable.reader_settings, R.string.settings) {
            if (controller.task?.id == taskId && controller.task?.isPlaying == true) controller.pause()
            try { startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { ViaToast.show(context, R.string.toast_operation_failed) }
        }
        val slider = SeekBar(ContextThemeWrapper(context, R.style.OriginalSeekbar)).apply {
            max = 13; progress = maxOf(0, (controller.speed / .25f).toInt() - 1); visibility = View.GONE
            ReaderControls.styleSeekBar(this)
        }
        controls.addView(slider, LinearLayout.LayoutParams(0, -1, 4f))
        val rewind = button(R.drawable.reader_rewind, R.string.rewind) { seek(-1) }
        play = button(R.drawable.reader_play, R.string.download_action_resume) {
            val task = controller.task?.takeIf { it.id == taskId } ?: return@button
            if (task.isPlaying) controller.pause() else controller.play()
        }
        val forward = button(R.drawable.reader_forward, R.string.fastforward) { seek(1) }
        val speed = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.night_preview_text_size).toFloat())
            typeface = BrowserPreferences(context).selectedTypeface()
            setTextColor(color(R.attr.viaPrimaryTextColor)); background = settingsRipple(context)
            text = String.format(Locale.ROOT, "%.2fx", controller.speed)
            setOnClickListener {
                val expanded = slider.isGone
                typeface = if (expanded) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(color(if (expanded) R.attr.viaAccentColor else R.attr.viaPrimaryTextColor))
                for (child in listOf(settings, slider, rewind, play, forward)) {
                    val shown = if (expanded) child === slider else child !== slider
                    child.visibility = if (shown) View.VISIBLE else View.GONE
                    if (shown) { child.alpha = 0f; child.animate().alpha(1f).setDuration(200L).start() }
                }
            }
        }
        controls.addView(speed, LinearLayout.LayoutParams(0, -1, 1f))
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar, progress: Int, user: Boolean) { speed.text = String.format(Locale.ROOT, "%.2fx", (progress + 1) * .25f) }
            override fun onStartTrackingTouch(view: SeekBar) = Unit
            override fun onStopTrackingTouch(view: SeekBar) {
                controller.speed = (view.progress + 1) * .25f
                if (controller.task?.id == taskId && controller.task?.isPlaying == true) playIndex(selected)
            }
        })
        return root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        controller.addListener(listener)
        val task = controller.task
        taskId = task?.id
        sentences = task?.sentences.orEmpty().toList()
        adapter.notifyDataSetChanged()
        val isEmpty = sentences.isEmpty()
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        list.visibility = if (isEmpty) View.GONE else View.VISIBLE
        controls.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (task != null) { updatePlay(!task.isFinished && task.isPlaying); select(task.index, smooth = false) }
    }
    override fun onResume() {
        super.onResume()
        val metrics = resources.displayMetrics
        val dimension = minOf(metrics.widthPixels, metrics.heightPixels) * 4 / 5
        dialog?.window?.setLayout(minOf(dimension, requireContext().dp(450f)), minOf(dimension, requireContext().dp(600f)))
    }
    private fun color(attribute: Int) = settingsColor(requireContext(), attribute, Color.GRAY)
    private fun updatePlay(playing: Boolean) {
        play.setImageResource(if (playing) R.drawable.reader_pause else R.drawable.reader_play)
        play.contentDescription = getString(if (playing) R.string.download_action_pause else R.string.download_action_resume)
    }
    private fun playIndex(index: Int) { if (controller.task?.id == taskId) controller.play(index) }
    private fun seek(delta: Int) {
        if (preview < 0) preview = selected
        preview = if (delta < 0) maxOf(0, preview - 1) else preview + 1
        select(preview, smooth = true)
        handler.removeCallbacksAndMessages(null); handler.postDelayed(commitSeek, 300L)
    }
    private fun select(index: Int, smooth: Boolean) {
        if (index == selected) return
        val old = selected; selected = index
        if (old in sentences.indices) adapter.notifyItemChanged(old)
        if (index in sentences.indices) {
            adapter.notifyItemChanged(index)
            if (resumeScrollingAt >= 0 && SystemClock.elapsedRealtime() > resumeScrollingAt) list.post {
                if (smooth) list.smoothScrollToPosition(index) else list.scrollToPosition(index)
            }
        }
    }
    private inner class Sentences : RecyclerView.Adapter<SentenceHolder>() {
        override fun getItemCount() = sentences.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SentenceHolder(TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2); gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            setPadding(context.dp(16f), context.dp(8f), context.dp(16f), context.dp(8f))
            textDirection = View.TEXT_DIRECTION_LOCALE; background = settingsRipple(context)
            setSingleLine(false)
        })
        override fun onBindViewHolder(holder: SentenceHolder, position: Int) {
            holder.text.apply {
                text = sentences[position]
                setTypeface(BrowserPreferences(context).selectedTypeface(), if (position == selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(color(if (position == selected) R.attr.viaAccentColor else R.attr.viaDisabledTextColor))
                setOnClickListener { playIndex(holder.bindingAdapterPosition) }
                setOnLongClickListener {
                    resumeScrollingAt = SystemClock.elapsedRealtime() + 3000L
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", sentences[position]))
                    ViaToast.show(context, R.string.toast_copy_text_successful)
                    true
                }
            }
        }
    }
    private class SentenceHolder(val text: TextView) : RecyclerView.ViewHolder(text)
    override fun onDestroyView() { handler.removeCallbacksAndMessages(null); controller.removeListener(listener); super.onDestroyView() }
}
