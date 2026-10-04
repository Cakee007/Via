package dev.ujhhgtg.via.passwords

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.ujhhgtg.via.ui.ViaToast

/** wa/r + wa/c: device authentication and the original three-minute successful-auth cache. */
class PasswordAuthenticator(
    private val activity: FragmentActivity,
    private val launchForResult: (android.content.Intent, Int) -> Unit = { intent, request -> activity.startActivityForResult(intent, request) },
) {
    private var success: (() -> Unit)? = null
    private var cancelled: (() -> Unit)? = null
    private var prompt: BiometricPrompt? = null

    fun authenticate(title: String, message: String, onCancel: () -> Unit = {}, onSuccess: () -> Unit) {
        if (System.currentTimeMillis() < authenticatedUntil) { onSuccess(); return }
        success = onSuccess
        cancelled = onCancel
        runCatching {
            val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(message)
                // wa.r.j: 33023; AndroidX maps this to deviceCredentialAllowed on API 29.
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = complete(true)
                    override fun onAuthenticationFailed() = fail(null)
                    override fun onAuthenticationError(code: Int, messageText: CharSequence) {
                        when (code) {
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> complete(false)
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> cancel()
                            BiometricPrompt.ERROR_HW_NOT_PRESENT -> deviceCredential(title, message)
                            else -> fail(messageText.toString())
                        }
                    }
                }).also { it.authenticate(info) }
        }.onFailure { deviceCredential(title, message) }
    }

    @Suppress("DEPRECATION")
    private fun deviceCredential(title: String, message: String) {
        val manager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val intent = if (manager?.isKeyguardSecure == true) manager.createConfirmDeviceCredentialIntent(title, message) else null
        if (intent == null) complete(false) else launchForResult(intent, REQUEST)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST) return false
        if (resultCode == Activity.RESULT_OK) complete(true) else fail(null)
        return true
    }

    fun dispose() { success = null; cancelled = null; prompt?.cancelAuthentication(); prompt = null }
    private fun complete(cache: Boolean) {
        val callback = success ?: return
        callback()
        if (cache) authenticatedUntil = System.currentTimeMillis() + 180000
        success = null; cancelled = null
    }
    private fun cancel() { val callback = cancelled; callback?.invoke(); success = null; cancelled = null }
    private fun fail(message: String?) {
        // wa.r.l ignores callbacks after the request was completed or disposed.
        if (success == null) return
        // wa.s.a -> g6.n.s displays the supplied text; failure is distinct from onCancel.
        if (!message.isNullOrEmpty()) ViaToast.show(activity, message)
        success = null; cancelled = null
    }

    companion object {
        private const val REQUEST = 6201
        private var authenticatedUntil = 0L
    }
}
