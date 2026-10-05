package dev.ujhhgtg.via.settings

import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Environment
import androidx.core.net.toUri
import dev.ujhhgtg.via.R

/** Original mark.via.download.z1 package/component registry and j1 delegation. */
object ExternalDownloadManagers {
    data class Manager(val packageName: String, val mainActivity: String?, val editors: List<String>, val fallbackName: String)
    data class Choice(val id: String, val label: String)
    val managers = listOf(
        Manager("system", null, emptyList(), "System download manager"),
        Manager("com.dv.adm.pay", "com.dv.get.Main", listOf("com.dv.get.AEditor", "com.dv.adm.AEditor"), "ADM+"),
        Manager("com.dv.adm", "com.dv.get.Main", listOf("com.dv.get.AEditor", "com.dv.adm.AEditor"), "ADM"),
        Manager("idm.internet.download.manager.plus", "idm.internet.download.manager.MainActivity", listOf("idm.internet.download.manager.Downloader", "idm.internet.download.manager.UrlHandlerDownloader"), "1DM+"),
        Manager("idm.internet.download.manager", "idm.internet.download.manager.MainActivity", listOf("idm.internet.download.manager.Downloader", "idm.internet.download.manager.UrlHandlerDownloader"), "1DM"),
        Manager("idm.internet.download.manager.adm.lite", "idm.internet.download.manager.MainActivity", listOf("idm.internet.download.manager.Downloader", "idm.internet.download.manager.UrlHandlerDownloader"), "1DM Lite"),
        Manager("com.vanda_adm.vanda", "com.vanda_adm.vanda.MainActivity", listOf("com.vanda_adm.vanda.ClipActivity"), "QKADM"),
        Manager("org.freedownloadmanager.fdm", "org.freedownloadmanager.fdm.MyActivity", listOf("org.freedownloadmanager.fdm.SendActivity"), "FDM"),
        Manager("com.dv.get", "com.dv.get.Main", listOf("com.dv.get.AEditor"), "DVGet"),
        Manager("com.tachibana.downloader", "com.tachibana.downloader.ui.main.MainActivity", listOf("com.tachibana.downloader.ui.adddownload.AddDownloadActivity"), "Download Navi"),
        Manager("com.gianlu.aria2app", "com.gianlu.aria2app.main.MainActivity", listOf("com.gianlu.aria2app.LoadingActivity"), "Aria2App"),
        Manager("com.gopeed", "com.gopeed.MainActivity", listOf("com.gopeed.MainActivity"), "Gopeed"),
        Manager("com.gopeed.gopeed", "com.gopeed.gopeed.MainActivity", listOf("com.gopeed.gopeed.MainActivity"), "Gopeed"),
        Manager("com.abdownloadmanager", "com.abdownloadmanager.android.ui.MainActivity", listOf("com.abdownloadmanager.android.pages.add.AddDownloadActivity"), "AB DM"),
        Manager("com.fluxdown.app", "com.fluxdown.app.MainActivity", listOf("com.fluxdown.app.MainActivity"), "FluxDown"),
    )

    fun choices(context: Context): List<Choice> = buildList {
        add(Choice("", context.getString(R.string.built_in_download_manager)))
        for ((packageName) in managers) {
            if (packageName == "system") add(Choice("system", context.getString(R.string.system_download_manager)))
            else runCatching { context.packageManager.getPackageInfo(packageName, 1).applicationInfo }.getOrNull()
                ?.takeIf { it.enabled }?.let { add(Choice(packageName, context.packageManager.getApplicationLabel(it).toString())) }
        }
    }

    /** j1.a sends only ACTION_SEND/text/plain/EXTRA_TEXT to the known editor components. */
    fun sendLink(context: Context, manager: Manager, url: String): Boolean {
        if (url.isEmpty()) return false
        for (editor in manager.editors) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                addFlags(FLAG_ACTIVITY_NEW_TASK); type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url); component = ComponentName(manager.packageName, editor)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    /** j1.c deliberately uses public Download and the URL itself as Referer. */
    suspend fun downloadWithSystem(context: Context, url: String, name: String, userAgent: String?, mimeType: String?): Long {
        if (url.isEmpty() || name.isEmpty()) return 0L
        val cookies = dev.ujhhgtg.via.engine.Engines.backend.cookies.get(url)
        return runCatching { downloadWithSystem(context, url, name, userAgent, mimeType, cookies) }.getOrDefault(0L)
    }

    private fun downloadWithSystem(context: Context, url: String, name: String, userAgent: String?, mimeType: String?, cookies: String?): Long {
        val request = DownloadManager.Request(url.toUri()).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setTitle(name).setDescription(url).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        if (mimeType != null) request.setMimeType(mimeType)
        request.addRequestHeader("Cookie", cookies)
        request.addRequestHeader("Referer", url)
        if (userAgent != null) request.addRequestHeader("User-Agent", userAgent)
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    fun openDownloads(context: Context, managerId: String?): Boolean {
        val manager = managers.firstOrNull { it.packageName == managerId } ?: return false
        val intent = if (manager.packageName == "system") Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            else manager.mainActivity?.let { Intent().setClassName(manager.packageName, it) } ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

/** hb.c4.B4 and c8.s6.N7 share z8.f1.h's video intent, selected package and fallback. */
object ExternalVideoPlayers {
    data class Choice(val packageName: String, val label: String)
    private const val PROBE_URL = "https://file-examples.com/wp-content/uploads/2017/04/file_example_MP4_480_1_5MG.mp4"

    fun intent(url: String, title: String? = null): Intent = Intent(Intent.ACTION_VIEW).apply {
        addFlags(FLAG_ACTIVITY_NEW_TASK)
        setDataAndType(url.toUri(), "video/*")
        if (!title.isNullOrEmpty()) { putExtra(Intent.EXTRA_TITLE, title); putExtra("title", title) }
    }

    fun choices(context: Context): List<Choice> = buildList {
        // hb.c4.B4: the empty package choice is "System sharing", the platform chooser.
        add(Choice("", context.getString(R.string.player_system_sharing)))
        context.packageManager.queryIntentActivities(intent(PROBE_URL), 0).forEach { item ->
            if (item.activityInfo.enabled) add(Choice(item.activityInfo.packageName, item.activityInfo.loadLabel(context.packageManager).toString()))
        }
    }

    fun open(context: Context, url: String, title: String?, packageName: String?): Boolean {
        if (url.isEmpty()) return false
        val request = intent(url, title)
        if (!packageName.isNullOrEmpty()) {
            request.setPackage(packageName)
            if (runCatching { context.startActivity(request) }.isSuccess) return true
            request.setPackage(null)
        }
        return runCatching { context.startActivity(request) }.isSuccess
    }
}
