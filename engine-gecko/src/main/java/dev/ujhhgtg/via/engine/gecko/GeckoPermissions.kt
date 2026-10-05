package dev.ujhhgtg.via.engine.gecko

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.net.toUri
import dev.ujhhgtg.via.engine.LocationRequest
import dev.ujhhgtg.via.engine.MediaPermissionRequest
import dev.ujhhgtg.via.engine.PageEvents
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

/** Site permissions, mapped onto the prompts the app shows for WebView; others follow WebView's defaults. */
internal class GeckoPermissions(private val context: Context, private val events: PageEvents) : PermissionDelegate {
    /** Gecko can ask for Android permissions before delivering its site prompt. */
    override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<out String>?, callback: PermissionDelegate.Callback) {
        if (permissions.orEmpty().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) callback.grant()
        else events.onAndroidPermissions(permissions.orEmpty().map { it }.toTypedArray()) { allowed ->
            if (allowed) callback.grant() else callback.reject()
        }
    }

    override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int> = when (perm.permission) {
        PermissionDelegate.PERMISSION_GEOLOCATION -> GeckoResult<Int>().also { result ->
            events.onGeolocationPrompt(object : LocationRequest(origin(perm.uri)) {
                override fun respond(allow: Boolean, retain: Boolean) =
                    result.complete(if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
            })
        }
        PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> GeckoResult<Int>().also { result ->
            events.onPermissionRequest(object : MediaPermissionRequest(origin(perm.uri), setOf(Resource.PROTECTED_MEDIA)) {
                override fun grant(resources: Set<Resource>) =
                    result.complete(if (Resource.PROTECTED_MEDIA in resources) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
                override fun deny() = result.complete(ContentPermission.VALUE_DENY)
            })
        }
        // WebView plays media without a gesture, keeps storage, and leaves local-network checks to the app.
        PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE, PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
        PermissionDelegate.PERMISSION_PERSISTENT_STORAGE, PermissionDelegate.PERMISSION_STORAGE_ACCESS,
        PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS, PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS ->
            GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)
        else -> GeckoResult.fromValue(ContentPermission.VALUE_DENY)
    }

    override fun onMediaPermissionRequest(session: GeckoSession, uri: String, video: Array<out PermissionDelegate.MediaSource>?,
        audio: Array<out PermissionDelegate.MediaSource>?, callback: PermissionDelegate.MediaCallback) {
        val camera = video?.firstOrNull { it.source == PermissionDelegate.MediaSource.SOURCE_CAMERA } ?: video?.firstOrNull()
        val microphone = audio?.firstOrNull()
        val resources = buildSet {
            if (camera != null) add(MediaPermissionRequest.Resource.VIDEO_CAPTURE)
            if (microphone != null) add(MediaPermissionRequest.Resource.AUDIO_CAPTURE)
        }
        if (resources.isEmpty()) { callback.reject(); return }
        events.onPermissionRequest(object : MediaPermissionRequest(origin(uri), resources) {
            override fun grant(resources: Set<Resource>) {
                val grantedVideo = camera?.takeIf { Resource.VIDEO_CAPTURE in resources }
                val grantedAudio = microphone?.takeIf { Resource.AUDIO_CAPTURE in resources }
                if (grantedVideo == null && grantedAudio == null) callback.reject() else callback.grant(grantedVideo, grantedAudio)
            }
            override fun deny() = callback.reject()
        })
    }

    /** WebView reports origins as scheme://host[:port]/. */
    private fun origin(uri: String): String {
        val parsed = uri.toUri()
        val host = parsed.host ?: return uri
        val port = if (parsed.port > 0) ":${parsed.port}" else ""
        return "${parsed.scheme}://$host$port/"
    }
}
