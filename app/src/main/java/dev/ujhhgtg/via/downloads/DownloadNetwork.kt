package dev.ujhhgtg.via.downloads

import android.content.Context
import android.net.ConnectivityManager
import java.net.HttpURLConnection
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** k5.t and l5.b.c/i5.c/a. This policy belongs only to the original download client. */
internal object DownloadNetwork {
    fun isHttp(url: String?) = url != null && (url.startsWith("http://", true) || url.startsWith("https://", true))

    fun isAvailable(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val active = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(active) != null
    }

    fun configure(connection: HttpURLConnection) {
        if (connection !is HttpsURLConnection) return
        // Original l5.b.c ignores factory-creation exceptions but always installs i5.a afterwards.
        // FIXME: verbatim from the original: downloads trust every certificate and host name, so a forged
        //  certificate is accepted silently. Review whether to keep this behavior.
        runCatching {
            @android.annotation.SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
            val trust = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate>? = null
            }
            connection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trust), SecureRandom())
            }.socketFactory
        }
        connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
    }

    /** l5.b.f: total after '/', or the inclusive returned range when its total is '*'. */
    fun contentRangeLength(value: String?): Long {
        if (value.isNullOrEmpty()) return -1
        val range = value.substring(6)
        val slash = range.indexOf('/')
        val dash = range.indexOf('-')
        if (slash > 0 && range.substring(slash + 1) != "*") return range.substring(slash + 1).toLongOrNull() ?: -1
        if (!"*".startsWith(range) && dash > 0) {
            val first = range.substring(0, dash).toLongOrNull() ?: -1
            val last = (if (slash > 0) range.substring(dash + 1, slash) else range.substring(dash + 1)).toLongOrNull() ?: -1
            return (last - first + 1).takeIf { it >= 0 } ?: -1
        }
        return -1
    }
}
