package dev.ujhhgtg.via.tools

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.ViaToast
import java.net.URISyntaxException

/** s6.da -> z8.f1.f: the explicit Open app menu excludes Via from its candidates. */
object ExternalPageOpener {
    fun open(activity: Activity, url: String?, internalDocument: Boolean) {
        val intent = createIntent(activity, url, internalDocument)
        if (intent == null) { failed(activity); return }
        try { activity.startActivity(intent) }
        catch (_: ActivityNotFoundException) { failed(activity) }
    }
    fun createIntent(context: Context, url: String?, internalDocument: Boolean): Intent? {
        if (url.isNullOrEmpty() || internalDocument) return null
        return try {
            val base = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val targets = context.packageManager.queryIntentActivities(base, 0).mapNotNull { resolved ->
                resolved.activityInfo.packageName.takeUnless { it == context.packageName }?.let { Intent(base).setPackage(it) }
            }
            when (targets.size) {
                0 -> null
                1 -> targets[0]
                else -> Intent.createChooser(targets[0], context.getString(R.string.open_with)).apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray<Parcelable>())
                }
            }
        } catch (_: URISyntaxException) { null }
    }
    private fun failed(activity: Activity) = ViaToast.makeText(activity, R.string.open_app_failed, ViaToast.LENGTH_SHORT).show()
}
