package dev.ujhhgtg.via.video

import android.app.Activity
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Rational
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.ViaIntents

/** The original z8.g2 PiP policy, kept separate from the fullscreen control surface. */
object VideoPictureInPicture {
    /** g2.a: set by [supported] (g2.e) and consulted by every other entry point. */
    private var enabled = false

    private fun activity(context: Context): Activity? {
        var current: Context? = context
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /** g2.e */
    fun supported(context: Context): Boolean {
        enabled = context.packageManager.hasSystemFeature("android.software.picture_in_picture")
        return enabled
    }

    /** g2.c */
    fun canEnter(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return appOps.checkOpNoThrow("android:picture_in_picture", android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** g2.a */
    fun enter(context: Context): Boolean {
        if (!enabled) return false
        val activity = activity(context) ?: return false
        return activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
    }

    /** g2.d */
    fun isInPip(context: Context): Boolean {
        if (!enabled) return false
        val activity = activity(context) ?: return false
        return activity.isInPictureInPictureMode
    }

    /**
     * g2.f (and the widget's own I(int), which omits the explicit package): state 1 is paused
     * (show PLAY), state 2 is playing (show PAUSE), anything else clears the actions.
     */
    fun updateActions(context: Context, state: Int, explicitPackage: Boolean = true) {
        if (!enabled) return
        val activity = activity(context) ?: return
        if (state <= 0) {
            activity.setPictureInPictureParams(PictureInPictureParams.Builder().setActions(emptyList()).build())
            return
        }
        val paused = state == 1
        val actions = listOf(
            action(activity, ViaIntents.ACTION_MEDIA_REWIND, R.drawable.ac, R.string.rz, explicitPackage),
            action(activity, ViaIntents.ACTION_MEDIA_PLAY, if (paused) R.drawable.cw else R.drawable.cu, if (paused) R.string.gq else R.string.gp, explicitPackage),
            action(activity, ViaIntents.ACTION_MEDIA_FASTFORWARD, R.drawable.bp, R.string.it, explicitPackage),
        )
        activity.setPictureInPictureParams(PictureInPictureParams.Builder().setActions(actions).build())
    }

    /** g2.g */
    fun updateVideoSize(context: Context, width: Int, height: Int) {
        if (!enabled) return
        val activity = activity(context) ?: return
        val ratio = nearestRatio(width, height)
        activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(Rational(ratio.first, ratio.second)).build())
    }

    /** The widget's layout-change listener (v.j) hands the control host's visible rectangle to the Activity. */
    fun updateSourceRect(context: Context, rect: Rect) {
        val activity = activity(context) ?: return
        activity.setPictureInPictureParams(PictureInPictureParams.Builder().setSourceRectHint(rect).build())
    }

    private fun action(activity: Activity, action: String, icon: Int, label: Int, explicitPackage: Boolean): RemoteAction {
        val intent = Intent(action)
        if (explicitPackage) intent.setPackage(activity.packageName)
        // Original g2.f uses requestCode 0 and 0xC000000 (UPDATE_CURRENT|IMMUTABLE).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getBroadcast(activity, 0, intent, flags)
        return RemoteAction(Icon.createWithResource(activity, icon), activity.getString(label), activity.getString(label), pending)
    }

    /** z8.g2.b: snap the source dimensions to Via's seven supported PiP ratios. */
    private fun nearestRatio(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 16 to 9
        val ratios = arrayOf(4 to 3, 3 to 4, 16 to 9, 9 to 16, 1 to 1, 21 to 9, 2 to 1)
        val actual = width.toDouble() / height.toDouble()
        return ratios.minBy { kotlin.math.abs(actual - it.first.toDouble() / it.second.toDouble()) }
    }
}
