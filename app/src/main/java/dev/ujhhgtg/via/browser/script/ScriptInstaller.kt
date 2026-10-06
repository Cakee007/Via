package dev.ujhhgtg.via.browser.script

import android.util.Log
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.settings.ScriptDetailsFragment
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** z8.t2 and sa.d1: shared confirmation/install flow, with their distinct existing-script lookups. */
class ScriptInstaller(private val fragment: Fragment) {
    /** c8.s6.q -> z8.t2.p/i: a website installation matches script ID OR download URL. */
    /** [onDownload] saves the script file instead, from the dialog's Download button. */
    fun fromBrowserUrl(url: String, onDownload: (() -> Unit)? = null, onSaved: (UserScript) -> Unit = {}) {
        if (url.isEmpty()) return
        fromUrl(url, 0, true, onSaved, onDownload)
    }

    /** sa.d1.j4/E3: settings matches an explicit row ID, otherwise the script ID alone. */
    fun fromSettingsUrl(url: String, existingId: Int = 0, onSaved: (UserScript) -> Unit = {}) =
        fromUrl(url, existingId, false, onSaved)

    private fun fromUrl(url: String, existingId: Int, matchDownloadUrl: Boolean, onSaved: (UserScript) -> Unit,
        onDownload: (() -> Unit)? = null) {
        ViaToast.show(fragment.requireContext(), R.string.toast_parsing_script)
        val context = fragment.requireContext().applicationContext
        execute({
            ScriptStore(context).use { store ->
                val parsed = ScriptManager(store).fetchSource(url)?.let { UserScript.parse(it, url) }
                val existing = when {
                    existingId > 0 -> store.find(existingId)
                    parsed == null -> null
                    matchDownloadUrl -> store.findByIdentity(parsed.scriptId, parsed.downloadUrl)
                    else -> store.findByScriptId(parsed.scriptId)
                }
                Candidate(parsed, existing)
            }
        }, { confirm(it.script, it.existing, onSaved, fromBrowser = matchDownloadUrl, onDownload = onDownload) })
    }

    /** z8.t2.n/q and sa.d1.q4: only the version changes which confirmation is shown. */
    fun confirm(parsed: UserScript?, existing: UserScript?, onSaved: (UserScript) -> Unit = {}, fromBrowser: Boolean = false,
        onDownload: (() -> Unit)? = null) {
        val context = fragment.requireContext()
        // sa.d1.q4 reports a parse failure itself; the browser's z8.t2.q reaches t2.n, which says "Script invalid".
        if (parsed == null) { ViaToast.show(context, if (fromBrowser) R.string.script_invalid else R.string.toast_install_script_failed_parse_error); return }
        val version = parsed.version ?: existing?.version ?: "0.1"
        val sameVersion = version == existing?.version
        val title = when {
            existing == null -> R.string.title_install_script
            sameVersion -> R.string.title_reinstall_script
            else -> R.string.title_update_script
        }
        val message = when {
            existing == null -> context.getString(R.string.message_install_script, parsed.name, version)
            sameVersion -> context.getString(R.string.message_reinstall_script, parsed.name, version)
            else -> context.getString(R.string.message_update_script, parsed.name, existing.version ?: "0.1", version)
        }
        val dialog = ViaDialog(fragment.requireActivity()).title(title).message(message)
            .positive(android.R.string.ok) { _, _ -> save(parsed, existing, onSaved) }
            .negative(android.R.string.cancel)
        // A script link opened in a tab can also be saved as an ordinary file.
        if (fromBrowser && onDownload != null) dialog.neutral(R.string.action_download_file) { onDownload() }
        dialog.show()
    }

    private fun save(parsed: UserScript, existing: UserScript?, onSaved: (UserScript) -> Unit) {
        val context = fragment.requireContext().applicationContext
        execute({
            ScriptStore(context).use { store ->
                val value = parsed.copy(id = existing?.id ?: 0, enabled = existing?.enabled ?: parsed.enabled,
                    userOverrides = existing?.userOverrides)
                // p5.b.h inserts directly: settings must not silently merge a different identity
                // merely because it happens to use the same download URL.
                val id = if (existing == null) store.insert(value) else store.save(value)
                Saved(store.find(id))
            }
        }, { result ->
            val saved = result.script
            if (saved == null) installFailed(parsed.name)
            else {
                // z8.t2.j / sa.d1.H3 announce the saved script before fetching dependencies.
                ViaToast.show(fragment.requireContext(), fragment.getString(R.string.toast_install_script_successfully, parsed.name))
                onSaved(saved)
                ensureDependencies(saved)
            }
        }, { error ->
            Log.e("ViaScripts", "Cannot install userscript", error)
            installFailed(parsed.name)
        })
    }

    private fun ensureDependencies(script: UserScript) {
        if (script.scriptId.isEmpty()) return
        val context = fragment.requireContext().applicationContext
        execute({ ScriptResources(context).ensure(script) }, { complete ->
            if (!complete) ViaToast.show(fragment.requireContext(),
                fragment.getString(R.string.toast_install_script_failed_dependency_error, script.name),
                actionText = fragment.getString(R.string.view_downloads), action = {
                    // z8.t2.a / sa.d1.r3 identify this page by script_id and scroll to a missing resource.
                    (fragment.requireActivity() as Shell).navigate(ScriptDetailsFragment.forScriptId(script.scriptId, true))
                })
        })
    }

    private fun installFailed(name: String) = ViaToast.show(fragment.requireContext(),
        fragment.getString(R.string.toast_install_script_failed_unknown, name))

    private fun <T : Any> execute(task: suspend () -> T, completed: (T) -> Unit,
        failed: (Throwable) -> Unit = { Log.e("ViaScripts", "Userscript operation failed", it) }) {
        fragment.viewLifecycleOwner.launchIo(task, completed, failed)
    }

    private data class Candidate(val script: UserScript?, val existing: UserScript?)
    private data class Saved(val script: UserScript?)
}
