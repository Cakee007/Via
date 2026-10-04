package dev.ujhhgtg.via.reader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.ujhhgtg.via.data.BrowserPreferences
import java.util.Locale
import java.util.UUID

data class ReadAloudTask(
    val id: String,
    val url: String,
    val title: String,
    val sentences: List<String>,
    var index: Int = 0,
    var isPlaying: Boolean = false,
) {
    val isFinished: Boolean get() = index !in sentences.indices
}

/** One process-wide task and TTS engine, matching j8/c and z8/q3. Calls stay on the main thread. */
class ReadAloudController private constructor(context: Context) {
    private val context = context.applicationContext
    private val preferences = BrowserPreferences(this.context)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<(ReadAloudTask?) -> Unit>()
    private var ready = false
    private var pendingPlay = false
    private var failed = false
    private var utteranceId: String? = null
    private var speech: TextToSpeech? = null
    var task: ReadAloudTask? = null
        private set
    val isInitializing: Boolean get() = !ready && !failed
    /** s6.ja accepts q3 states 0/1/2 and reports state 3 before extracting text. */
    val initializationFailed: Boolean get() = failed
    var speed: Float
        get() = (preferences.readAloudSpeedPercent / 100f).coerceIn(.25f, 5f)
        set(value) {
            preferences.readAloudSpeedPercent = (value.coerceIn(.25f, 5f) * 100).toInt()
            speech?.setSpeechRate(speed)
        }

    init {
        speech = TextToSpeech(this.context) { status -> main.post { initialized(status) } }
    }

    fun addListener(listener: (ReadAloudTask?) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (ReadAloudTask?) -> Unit) { listeners.remove(listener) }

    fun start(url: String, title: String, sentences: List<String>): ReadAloudTask? {
        if (sentences.isEmpty()) return null
        speech?.stop()
        task = ReadAloudTask(UUID.randomUUID().toString().replace("-", ""), url, title, sentences)
        ReadAloudService.send(context, ReadAloudService.PLAY, task?.id)
        return task
    }

    fun play(index: Int? = null) = ReadAloudService.send(context, ReadAloudService.PLAY, task?.id, index)
    fun pause() = ReadAloudService.send(context, ReadAloudService.PAUSE, task?.id)
    fun stop() = ReadAloudService.send(context, ReadAloudService.STOP)

    internal fun playNow(id: String?, index: Int? = null) {
        val current = task ?: return
        if (id == null || id != current.id) return
        if (index != null) current.index = index
        else if (current.isFinished) current.index = 0
        if (failed || current.isFinished) { current.isPlaying = false; notifyChanged(); return }
        if (!ready) { pendingPlay = true; current.isPlaying = false; notifyChanged(); return }
        speakCurrent()
    }

    internal fun pauseNow(id: String? = null): Boolean {
        val current = task
        // j8.c.h only changes/announces tasks that are actually playing.
        if (id != null && current?.id == id && !current.isPlaying) return false
        pendingPlay = false
        utteranceId = null
        speech?.stop()
        if (current?.isPlaying != true) return false
        current.isPlaying = false
        notifyChanged()
        return true
    }

    internal fun stopNow() {
        pendingPlay = false
        utteranceId = null
        speech?.stop()
        task = null
        notifyChanged()
    }

    private fun initialized(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        failed = !ready
        if (!ready) {
            task?.isPlaying = false
            // q3 records initialization failure; the visible browser entry owns the toast.
            return
        }
        speech?.setSpeechRate(speed)
        speech?.language = Locale.getDefault()
        speech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { main.post { advance(id) } }
            @Deprecated("Android callback")
            override fun onError(id: String?) { main.post { advance(id) } }
        })
        // q3's deferred initialization speaks its pending utterance directly;
        // j8.c changes task state again only at the next sentence callback.
        if (pendingPlay) { pendingPlay = false; speakCurrent(updateTask = false) }
    }

    private fun speakCurrent(updateTask: Boolean = true) {
        val current = task ?: return
        if (current.isFinished) { current.isPlaying = false; notifyChanged(); return }
        val id = UUID.randomUUID().toString()
        utteranceId = id
        val text = current.sentences[current.index]
        speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        // q3.h's return value reflects its state, not TextToSpeech.speak's integer result.
        if (updateTask) { current.isPlaying = true; notifyChanged() }
    }

    private fun advance(id: String?) {
        if (id == null || id != utteranceId) return
        val current = task ?: return
        current.index++
        speakCurrent()
    }

    private fun notifyChanged() { listeners.toList().forEach { it(task) } }

    companion object {
        // Only ever holds the application context.
        @SuppressLint("StaticFieldLeak")
        private var instance: ReadAloudController? = null
        fun get(context: Context): ReadAloudController = instance ?: ReadAloudController(context).also { instance = it }
    }
}
