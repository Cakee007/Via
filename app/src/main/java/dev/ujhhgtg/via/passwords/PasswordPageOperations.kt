package dev.ujhhgtg.via.passwords

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.ViaToast

/** The va pages share v9.g; each owns its wa.r authentication and document result launcher. */
internal class PasswordPageOperations(private val fragment: Fragment) {
    val repository get() = PasswordRepository.get(fragment.requireContext())
    private var authenticator: PasswordAuthenticator? = null
    val authentication: PasswordAuthenticator
        get() = authenticator ?: PasswordAuthenticator(fragment.requireActivity(), ::launch).also { authenticator = it }
    private var pendingRequest = 0
    var onDocumentResult: (Int, Int, Intent?) -> Unit = { _, _, _ -> }
    private val document = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (authenticator?.onActivityResult(pendingRequest, result.resultCode) != true) {
            onDocumentResult(pendingRequest, result.resultCode, result.data)
        }
    }

    fun launch(intent: Intent, request: Int) { pendingRequest = request; document.launch(intent) }
    fun restoreState(state: Bundle?) { pendingRequest = state?.getInt("password_request") ?: 0 }
    fun saveState(state: Bundle) { state.putInt("password_request", pendingRequest) }
    fun <T> work(block: () -> T, success: (T) -> Unit) {
        val lifecycle = fragment.viewLifecycleOwner.lifecycle
        repository.async(block) { result ->
            if (lifecycle.currentState != Lifecycle.State.DESTROYED && fragment.isAdded) {
                result.fold(success) { toast(fragment.getString(R.string.toast_operation_failed)) }
            }
        }
    }
    fun toast(message: String) = ViaToast.makeText(fragment.requireContext(), message, ViaToast.LENGTH_LONG).show()
    fun destroyView() { authenticator?.dispose(); authenticator = null }
}
