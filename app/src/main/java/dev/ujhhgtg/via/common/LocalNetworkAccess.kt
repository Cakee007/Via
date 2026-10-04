package dev.ujhhgtg.via.common

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.os.Build
import androidx.core.net.toUri
import dev.ujhhgtg.via.ui.ViaActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

/** Required by target 37; the original APK predates Android's local-network permission. */
object LocalNetworkAccess {
    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 37 ||
        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    /** No DNS on the browser's ordinary navigation path. */
    fun isKnownLocalUrl(context: Context, url: String): Boolean {
        val name = host(url) ?: return false
        numericAddress(name)?.let { return isLocalAddress(context, it) }
        return !(name == "localhost" || name.endsWith(".localhost")) && ('.' !in name || name.endsWith(".local"))
    }

    /** Resolve only explicit transfer/sync endpoints or a failed main-document request. */
    // One-shot DNS lookup whose result is always delivered through [completed].
    fun resolveLocalUrl(context: Context, url: String, completed: (Boolean) -> Unit) {
        val name = host(url)
        if (name == null) { completed(false); return }
        if (isKnownLocalUrl(context, url)) { completed(true); return }
        if (numericAddress(name) != null || name == "localhost" || name.endsWith(".localhost")) {
            completed(false)
            return
        }
        val app = context.applicationContext
        applicationIoScope.launch {
            val local = try { InetAddress.getAllByName(name).any { isLocalAddress(app, it) } }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { false }
            withContext(Dispatchers.Main) { completed(local) }
        }
    }

    fun check(context: Context, url: String, resolveHost: Boolean = true, allowPrompt: Boolean = true,
        completed: (Boolean) -> Unit) {
        if (hasPermission(context)) { completed(true); return }
        fun checked(local: Boolean) {
            when {
                !local || hasPermission(context) -> completed(true)
                !allowPrompt -> completed(false)
                else -> request(context, completed)
            }
        }
        if (resolveHost) resolveLocalUrl(context, url, ::checked)
        else checked(isKnownLocalUrl(context, url))
    }

    fun request(context: Context, completed: (Boolean) -> Unit) {
        if (hasPermission(context)) { completed(true); return }
        activity(context)?.requestLocalNetworkPermission(completed) ?: completed(false)
    }

    fun activity(context: Context): ViaActivity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is ViaActivity) return current
            current = current.baseContext
        }
        return null
    }

    private fun host(url: String): String? {
        val uri = url.toUri()
        if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) return null
        return uri.host?.removeSurrounding("[", "]")?.substringBefore('%')?.trimEnd('.')
            ?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
    }

    private fun numericAddress(host: String): InetAddress? =
        if (InetAddresses.isNumericAddress(host)) InetAddresses.parseNumericAddress(host) else null

    private fun isLocalAddress(context: Context, address: InetAddress): Boolean {
        // Loopback never crosses the network interface on which Android installs LNP rules.
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return false
        if (address.isSiteLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress) return true
        if (address is Inet6Address) {
            if (address.address[0].toInt() and 0xfe == 0xfc) return true
            // A LAN may have globally routed IPv6. ConnectivityService protects the link prefix,
            // not just ULA/link-local ranges (getLocalNetworkPrefixesForAddress).
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val network = manager.activeNetwork ?: return false
            return manager.getLinkProperties(network)?.linkAddresses?.any {
                it.address is Inet6Address && it.prefixLength > 0 && sharesPrefix(it.address, it.prefixLength, address)
            } == true
        }
        return false
    }

    /** android.net.IpPrefix(prefix, length).contains(address) without its API 33 requirement. */
    private fun sharesPrefix(prefix: InetAddress, length: Int, address: InetAddress): Boolean {
        val expected = prefix.address
        val actual = address.address
        if (expected.size != actual.size) return false
        val whole = length / 8
        for (index in 0 until whole) if (expected[index] != actual[index]) return false
        val remaining = length % 8
        if (remaining == 0) return true
        val mask = (0xff shl (8 - remaining)) and 0xff
        return expected[whole].toInt() and mask == actual[whole].toInt() and mask
    }
}
