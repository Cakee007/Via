package dev.ujhhgtg.via.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import okhttp3.Interceptor
import java.util.Base64

/** Response header carrying the URL OkHttp ended at after following redirects; never sent over the network. */
internal const val FINAL_URL_HEADER = "X-Via-Final-Url"

/** HttpURLConnection's default agent, so servers see the same client as before when no agent is given. */
internal val platformUserAgent: String = System.getProperty("http.agent") ?: "Mozilla/5.0"

private val finalUrlInterceptor = Interceptor { chain ->
    val response = chain.proceed(chain.request())
    response.newBuilder().header(FINAL_URL_HEADER, response.request.url.toString()).build()
}

/** Process-wide Ktor client; requests are cancelled together with the calling coroutine. */
internal val httpClient = HttpClient(OkHttp) {
    // Redirects stay inside OkHttp: it follows them per method (POST -> GET, PROPFIND keeps its body)
    // like HttpURLConnection and the former OkHttp client did, unlike Ktor's HttpRedirect.
    followRedirects = false
    engine {
        config {
            followRedirects(true)
            followSslRedirects(true)
            addInterceptor(finalUrlInterceptor)
        }
    }
    install(HttpTimeout)
    install(UserAgent) { agent = platformUserAgent }
}

/** URL of the last hop when OkHttp followed redirects. */
internal val HttpResponse.finalUrl: String get() = headers[FINAL_URL_HEADER] ?: request.url.toString()

/** Equivalent of okhttp3.Credentials.basic: ISO-8859-1 encoded "username:password". */
internal fun basicAuthorization(username: String, password: String): String =
    "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.ISO_8859_1))
