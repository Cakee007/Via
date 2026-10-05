package dev.ujhhgtg.via.engine.gecko

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import androidx.core.net.toUri
import dev.ujhhgtg.via.engine.FileChooserRequest
import dev.ujhhgtg.via.engine.FormResubmissionRequest
import dev.ujhhgtg.via.engine.HttpAuthRequest
import dev.ujhhgtg.via.engine.JsDialogRequest
import dev.ujhhgtg.via.engine.PageEvents
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import java.util.Calendar
import java.util.Locale

/**
 * Page prompts. Dialogs WebView routes through its clients go to [PageEvents] as the same neutral requests;
 * form-control pickers that WebView renders itself are shown here with platform dialogs.
 */
internal class GeckoPrompts(private val page: GeckoPage, private val events: PageEvents) : PromptDelegate {
    private val context get() = page.view.context

    private fun jsDialog(kind: JsDialogRequest.Kind, message: String?, defaultValue: String?,
        confirm: (String) -> PromptResponse, cancel: () -> PromptResponse): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val request = object : JsDialogRequest(kind, page.url.orEmpty(), message, defaultValue) {
            private var done = false
            override fun confirm(text: String) { if (!done) { done = true; result.complete(confirm(text)) } }
            override fun cancel() { if (!done) { done = true; result.complete(cancel()) } }
        }
        if (!events.onJsDialog(request)) request.cancel()
        return result
    }

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) =
        jsDialog(JsDialogRequest.Kind.ALERT, prompt.message, null, { prompt.dismiss() }, { prompt.dismiss() })

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) =
        jsDialog(JsDialogRequest.Kind.CONFIRM, prompt.message, null,
            { prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE) },
            { prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE) })

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) =
        jsDialog(JsDialogRequest.Kind.PROMPT, prompt.message, prompt.defaultValue, { prompt.confirm(it) }, { prompt.dismiss() })

    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt) =
        jsDialog(JsDialogRequest.Kind.BEFORE_UNLOAD, prompt.title, null,
            { prompt.confirm(AllowOrDeny.ALLOW) }, { prompt.confirm(AllowOrDeny.DENY) })

    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        events.onFormResubmission(object : FormResubmissionRequest() {
            override fun resend() = result.complete(prompt.confirm(AllowOrDeny.ALLOW))
            override fun cancel() = result.complete(prompt.confirm(AllowOrDeny.DENY))
        })
        return result
    }

    override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val options = prompt.authOptions
        val host = options.uri.orEmpty().toUri().host ?: options.uri.orEmpty()
        val passwordOnly = options.flags and PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD != 0
        events.onHttpAuth(object : HttpAuthRequest(host, prompt.message) {
            override fun proceed(username: String, password: String) =
                result.complete(if (passwordOnly) prompt.confirm(password) else prompt.confirm(username, password))
            override fun cancel() = result.complete(prompt.dismiss())
        })
        return result
    }

    /** Popups go on to onNewSession, where the app's popup policy decides. */
    override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt): GeckoResult<PromptResponse> =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
        val types = prompt.mimeTypes?.filter(String::isNotEmpty).orEmpty()
        val request = object : FileChooserRequest(types, prompt.title) {
            private var done = false
            override fun createIntent(): Intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = types.singleOrNull()?.takeIf { '/' in it } ?: "*/*"
                if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.filter { '/' in it }.toTypedArray())
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            }
            override fun complete(resultCode: Int, data: Intent?) {
                if (resultCode != android.app.Activity.RESULT_OK || data == null) return complete(null)
                val clips = data.clipData
                val uris = if (clips != null) Array(clips.itemCount) { clips.getItemAt(it).uri } else data.data?.let { arrayOf(it) }
                complete(uris)
            }
            override fun cancel() = complete(null)
            override fun complete(uris: Array<Uri>?) {
                if (done) return
                done = true
                result.complete(if (uris.isNullOrEmpty()) prompt.dismiss() else prompt.confirm(context, uris))
            }
        }
        if (!events.onFileChooser(request)) request.cancel()
        return result
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        // Groups are flattened; their labels stay as disabled rows.
        val choices = prompt.choices.flatMap { choice -> if (choice.items != null) listOf(choice) + choice.items!! else listOf(choice) }
            .filterNot { it.separator }
        val labels = choices.map { if (it.items != null) it.label.uppercase(Locale.getDefault()) else it.label }.toTypedArray()
        val dialog = AlertDialog.Builder(context).setOnCancelListener { result.complete(prompt.dismiss()) }
        prompt.message?.takeIf(String::isNotEmpty)?.let(dialog::setTitle)
        when (prompt.type) {
            PromptDelegate.ChoicePrompt.Type.MULTIPLE -> {
                val checked = BooleanArray(choices.size) { choices[it].selected }
                dialog.setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        result.complete(prompt.confirm(choices.filterIndexed { index, choice -> checked[index] && choice.items == null }.toTypedArray()))
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.complete(prompt.dismiss()) }
            }
            else -> dialog.setSingleChoiceItems(labels, choices.indexOfFirst { it.selected }) { shown, which ->
                val choice = choices[which]
                if (choice.disabled || choice.items != null) return@setSingleChoiceItems
                result.complete(prompt.confirm(choice))
                shown.dismiss()
            }
        }
        dialog.show()
        return result
    }

    override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val input = EditText(context).apply {
            setText(prompt.defaultValue ?: "#000000")
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(context).setTitle(prompt.title).setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text.toString().trim()
                result.complete(if (Regex("#[0-9a-fA-F]{6}").matches(value)) prompt.confirm(value.lowercase(Locale.ROOT)) else prompt.dismiss())
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.complete(prompt.dismiss()) }
            .setOnCancelListener { result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val type = prompt.type
        val initial = parseDateTime(type, prompt.defaultValue)
        fun pickTime(date: Calendar?) {
            TimePickerDialog(context, { _, hour, minute ->
                val time = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                result.complete(prompt.confirm(if (date == null) time else formatDate(PromptDelegate.DateTimePrompt.Type.DATE, date) + "T" + time))
            }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), true).apply {
                setOnCancelListener { result.complete(prompt.dismiss()) }
            }.show()
        }
        if (type == PromptDelegate.DateTimePrompt.Type.TIME) { pickTime(null); return result }
        DatePickerDialog(context, { _, year, month, day ->
            val picked = Calendar.getInstance().apply { set(year, month, day) }
            if (type == PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL) pickTime(picked)
            else result.complete(prompt.confirm(formatDate(type, picked)))
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply {
            setOnCancelListener { result.complete(prompt.dismiss()) }
        }.show()
        return result
    }

    private fun formatDate(type: Int, date: Calendar): String = when (type) {
        PromptDelegate.DateTimePrompt.Type.MONTH -> String.format(Locale.ROOT, "%04d-%02d", date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1)
        PromptDelegate.DateTimePrompt.Type.WEEK -> {
            val iso = date.clone() as Calendar
            iso.firstDayOfWeek = Calendar.MONDAY
            iso.minimalDaysInFirstWeek = 4
            iso.time = date.time
            String.format(Locale.ROOT, "%04d-W%02d", iso.weekYear, iso.get(Calendar.WEEK_OF_YEAR))
        }
        else -> String.format(Locale.ROOT, "%04d-%02d-%02d", date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH))
    }

    private fun parseDateTime(type: Int, value: String?): Calendar {
        val calendar = Calendar.getInstance()
        if (value.isNullOrEmpty()) return calendar
        runCatching {
            if (type == PromptDelegate.DateTimePrompt.Type.TIME) {
                val (hour, minute) = value.split(':').map(String::toInt)
                calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute)
                return calendar
            }
            val date = value.substringBefore('T').split('-')
            calendar.set(Calendar.YEAR, date[0].toInt())
            if (date.size > 1 && !date[1].startsWith("W")) calendar.set(Calendar.MONTH, date[1].toInt() - 1)
            if (date.size > 2) calendar.set(Calendar.DAY_OF_MONTH, date[2].toInt())
            value.substringAfter('T', "").takeIf(String::isNotEmpty)?.split(':')?.let { time ->
                calendar.set(Calendar.HOUR_OF_DAY, time[0].toInt()); calendar.set(Calendar.MINUTE, time[1].toInt())
            }
        }
        return calendar
    }
}
