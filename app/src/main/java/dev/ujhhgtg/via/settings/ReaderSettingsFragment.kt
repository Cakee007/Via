package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.common.GeneratedDocumentState

/** hb.o5: confirmation, original palette, live size sample, and CSS editor result. */
class ReaderSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter
    private lateinit var paletteRow: ReaderColorRow
    private lateinit var sizeRow: TextSizeSampleRow

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.reader_mode)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        GeneratedDocumentState.initialize(preferences)
        paletteRow = ReaderColorRow(preferences.readerThemeColor)
        sizeRow = TextSizeSampleRow(2, getString(R.string.size), "%dpx", getString(if (java.util.Locale.getDefault().country == "CN") R.string.night_filter_preview_china else R.string.night_filter_preview), preferences.readerTextSize, 10, 30, 1, 17)
        rows = SettingsRowsAdapter { row ->
            if (row is SettingsToggleRow) { preferences.readerConfirmation = !row.checked; bindRows() }
            else if (row.id == 3) editCss()
        }.apply {
            onColorSelected = { preferences.readerThemeColor = it; GeneratedDocumentState.mark(GeneratedDocumentState.BLANK_AND_PAGE_STATE) }
            onTextSizeChanged = { preferences.readerTextSize = it; GeneratedDocumentState.mark(GeneratedDocumentState.BLANK_AND_PAGE_STATE) }
        }
        list.adapter = rows
        bindRows()
    }
    private fun bindRows() = rows.submit(listOf(
        SettingsToggleRow(1, getString(R.string.require_confirmation_to_enable_reader_mode), checked = preferences.readerConfirmation,
            checkedSummary = getString(R.string.require_confirmation_to_enable_reader_mode_description_enabled),
            uncheckedSummary = getString(R.string.require_confirmation_to_enable_reader_mode_description_disabled)),
        SettingsHeadingRow(getString(R.string.appearance)), paletteRow, sizeRow,
        SettingsRow(3, getString(R.string.custom_reader_css)),
    ))
    private fun editCss() {
        parentFragmentManager.setFragmentResultListener("edit_text_result", this) { _, result ->
            result.getString("text")?.let { preferences.readerCustomCss = it; GeneratedDocumentState.mark(GeneratedDocumentState.BLANK_AND_PAGE_STATE) }
            parentFragmentManager.clearFragmentResultListener("edit_text_result")
        }
        (requireActivity() as Shell).navigate(TextEditorFragment.newInstance(getString(R.string.custom_reader_css), preferences.readerCustomCss, getString(R.string.custom_reader_css), true))
    }
}
