package dev.ujhhgtg.via.passwords

import android.content.Intent
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.ui.dp
import org.json.JSONObject
import java.util.WeakHashMap

/** c8.mb form scripts and c8.s6 actions 109/110, T7, L7, b5 and fill authentication. */
class PasswordFormController(
    private val activity: FragmentActivity,
    launchForResult: (Intent, Int) -> Unit,
    private val indicator: (WebView, Boolean) -> Unit,
) {
    private val repository = PasswordRepository.get(activity)
    private val authentication = PasswordAuthenticator(activity, launchForResult)
    private val secrets = WeakHashMap<WebView, String>()
    private val fill = activity.assets.open("passwords/autofill.js").bufferedReader().use { it.readText() }
    private fun text(key: Int) = activity.getString(key)

    /** The host's existing window.via bridge owns authentication and dispatch. */
    fun attach(webView: WebView, secret: String) { secrets[webView] = secret }
    fun handleMessage(view: WebView, message: JSONObject): Boolean {
        return secrets.containsKey(view) && when (message.optInt("action")) {
            109 -> {
                val show = message.optInt("show") > 0
                activity.runOnUiThread { if (!activity.isFinishing) indicator(view, show) }
                true
            }

            110 -> {
                val url = message.optString("url")
                val username = message.optString("user")
                val password = message.optString("pass")
                activity.runOnUiThread { if (!activity.isFinishing) offer(url, username, password) }
                true
            }

            else -> false
        }
    }

    fun offer(url: String, username: String, password: String) {
        if (url.isBlank() || username.isEmpty() || password.isEmpty()) return
        repository.async({
            if (!repository.canOfferSaving(url)) false to null
            else true to repository.findId(PasswordCsv.host(url), username)?.let { repository.get(it, true) }
        }) { result ->
            if (activity.isFinishing || activity.isDestroyed) return@async
            val (allowed, existing) = result.getOrNull() ?: return@async
            if (!allowed || existing?.password == password) return@async
            val domain = PasswordCsv.host(url)
            fun save() {
                val record = existing?.copy(url = url, password = password, updatedAt = System.currentTimeMillis())
                    ?: PasswordRecord(name = domain, url = url, username = username, password = password)
                repository.async({ repository.save(record) }) { saved ->
                    if (saved.getOrDefault(false)) ViaToast.makeText(activity, text(if (existing == null) R.string.password_saved else R.string.password_updated), ViaToast.LENGTH_SHORT).show()
                }
            }
            if (existing != null) {
                ViaDialog(activity).title(R.string.update_password)
                    .message(activity.getString(R.string.update_password_message, domain))
                    .positive(android.R.string.ok) { _, _ -> save() }.negative(android.R.string.cancel).show()
            } else {
                ViaDialog(activity).title(activity.getString(R.string.save_password_message, domain))
                    .centered(true).maximumWidth(activity.dp(268f))
                    .items(arrayOf(text(R.string.save_password), text(R.string.never_for_this_site), activity.getString(android.R.string.cancel)), onClick = { which ->
                        if (which == 0) save()
                        if (which == 1) repository.async({ repository.setSavingAllowed(url, false) }) {}
                    }).show()
            }
        }
    }

    fun choose(webView: WebView) {
        val url = webView.url.orEmpty()
        val fragmentActivity = activity as? androidx.fragment.app.FragmentActivity ?: return
        val manager = fragmentActivity.supportFragmentManager
        manager.setFragmentResultListener(PasswordPickerFragment.RESULT, fragmentActivity) { _, result ->
            val id = result.getString("id") ?: return@setFragmentResultListener
            authentication.authenticate(text(R.string.fill_password), text(R.string.unlock_device_to_fill_password)) {
                repository.async({ repository.get(id, true) }) { loaded ->
                    val record = loaded.getOrNull() ?: return@async
                    if (webView.url != url) return@async
                    val script = fill.replace("\"__USER__\"", JSONObject.quote(record.username))
                        .replace("\"__PASS__\"", JSONObject.quote(record.password.orEmpty()))
                    webView.evaluateJavascript(script, null)
                }
            }
        }
        PasswordPickerFragment.newInstance(url).show(manager, "PasswordPicker")
    }

    fun onActivityResult(requestCode: Int, resultCode: Int) = authentication.onActivityResult(requestCode, resultCode)
    fun dispose() { authentication.dispose(); secrets.clear() }
}
