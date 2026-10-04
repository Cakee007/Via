package dev.ujhhgtg.via.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import dev.ujhhgtg.via.R

/** hb.j4: original ordered library attribution list and project destinations. */
class OpenSourceLicensesFragment : SettingsListFragment() {
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.open_source_licenses)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val libraries = listOf(
            "androidx/androidx" to "AndroidX - AndroidX", "google/dagger" to "Dagger - Google", "Kotlin/kotlinx.coroutines" to "Kotlin Coroutines - JetBrains", "square/okhttp" to "OkHttp - Square", "square/leakcanary/" to "LeakCanary - Square", "shwenzhang/AndResGuard" to "AndResGuard - shwenzhang", "Tencent/VasDolly" to "VasDolly - Tencent", "JakeWharton/timber" to "Timber - Timber", "promeG/TinyPinyin" to "TinyPinyin - promeG", "zxing/zxing" to "ZXing - ZXing", "rburgst/okhttp-digest" to "okhttp-digest - rburgst", "thegrizzlylabs/sardine-android" to "sardine-android - The Grizzly Labs", "AirBashX/UserScript" to "AutoUnfold - AirBashX", "mozilla/readability" to "readability - mozilla", "afollestad/drag-select-recyclerview" to "drag-select-recyclerview - afollestad", "fengyuanchen/viewerjs" to "Viewer.js - Chen Fengyuan", "xnx3/translate" to "translate - xnx3"
        )
        list.adapter = SettingsRowsAdapter { row -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/${libraries[row.id].first}".toUri())) } }.apply {
            submit(libraries.mapIndexed { index, item -> SettingsRow(index, item.second, when (item.first) { "Tencent/VasDolly" -> "BSD 3-Clause License"; "fengyuanchen/viewerjs", "xnx3/translate" -> "MIT License"; else -> "Apache License 2.0" }) })
        }
    }
}
