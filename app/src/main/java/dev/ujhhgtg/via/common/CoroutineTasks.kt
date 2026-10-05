package dev.ujhhgtg.via.common

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Work that must finish even after the initiating screen closes. */
internal val applicationIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal fun <T> LifecycleOwner.launchIo(
    work: suspend () -> T,
    onSuccess: (T) -> Unit,
    onError: (Throwable) -> Unit = {},
) = lifecycleScope.launch {
    val result = try { withContext(Dispatchers.IO) { work() } }
    catch (error: CancellationException) { throw error }
    catch (error: Exception) { onError(error); return@launch }
    onSuccess(result)
}
