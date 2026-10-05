package dev.ujhhgtg.via.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import dev.ujhhgtg.via.R

/** Library attributions generated at build time by the AboutLibraries Gradle plugin. */
class OpenSourceLicensesFragment : SettingsListFragment() {
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.open_source_licenses)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val libraries = Libs.Builder().withJson(requireContext(), R.raw.aboutlibraries).build().libraries
            .sortedBy { it.name.lowercase() }
        list.adapter = SettingsRowsAdapter { row ->
            val library = libraries[row.id]
            val url = library.website ?: library.scm?.url ?: library.licenses.firstOrNull()?.url
            if (url != null) runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        }.apply {
            submit(libraries.mapIndexed { index, library ->
                val author = library.organization?.name ?: library.developers.firstOrNull()?.name
                SettingsRow(
                    index,
                    if (author != null) "${library.name} - $author" else library.name,
                    library.licenses.joinToString { it.name }.ifEmpty { null },
                )
            })
        }
    }
}
