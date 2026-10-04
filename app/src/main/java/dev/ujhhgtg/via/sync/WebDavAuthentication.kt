package dev.ujhhgtg.via.sync

import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/** DigestAuthenticator and its bundled y0.d header parser from the supplied APK. */
object WebDavAuthentication {
    /** y0.d.g/e: quoted delimiters and escapes are scanned, but escape bytes are retained. */
    fun challenge(value: String): Map<String, String> {
        require(value.startsWith("Digest")) { "unsupported auth scheme: $value" }
        var position = 7
        val fields = linkedMapOf<String, String>()
        fun pair(): Triple<String, String?, Char?> {
            val nameStart = position
            while (position < value.length && value[position] != '=' && value[position] != ',' && value[position] != ';') position++
            val name = value.substring(nameStart, position).trim(::headerWhitespace)
            if (position == value.length) return Triple(name, null, null)
            val separator = value[position++]
            if (separator != '=') return Triple(name, null, separator)
            val start = position
            var quoted = false
            var escaped = false
            while (position < value.length) {
                val character = value[position]
                if (character == '"' && !escaped) quoted = !quoted
                if (!quoted && !escaped && (character == ',' || character == ';')) break
                escaped = !escaped && quoted && character == '\\'
                position++
            }
            var text = value.substring(start, position).trim(::headerWhitespace)
            if (text.length >= 2 && text.first() == '"' && text.last() == '"') text = text.substring(1, text.length - 1)
            val delimiter = if (position < value.length) value[position++] else null
            return Triple(name, text, delimiter)
        }
        while (position < value.length) {
            val (name, text, delimiter) = pair()
            // Header element parameters following ';' belong to that element; p() reads only its name/value.
            if (delimiter == ';') while (position < value.length) { if (pair().third == ',') break }
            if (name.isEmpty() && text == null) continue
            // The original ConcurrentHashMap rejects a valueless challenge parameter.
            fields[name] = text ?: throw NullPointerException()
        }
        require(fields.isNotEmpty()) { "Authentication challenge is empty" }
        return fields
    }

    internal fun digestHeader(method: String, path: String, username: String, password: String, fields: Map<String, String>,
        count: Int, cnonce: String, credentialCharset: String?, hasBody: Boolean): String {
        val realm = requireNotNull(fields["realm"]) { "Digest challenge has no realm" }
        val nonce = requireNotNull(fields["nonce"]) { "missing nonce in challenge" }
        val algorithm = fields["algorithm"] ?: "MD5"
        val digestAlgorithm = if (algorithm.equals("MD5-sess", true)) "MD5" else algorithm
        val digest = try { MessageDigest.getInstance(digestAlgorithm) } catch (error: Exception) {
            throw IllegalArgumentException("Unsupported algorithm in HTTP Digest authentication: $digestAlgorithm", error)
        }
        // b() supplies k(request), whose default l() is ASCII. j() uses the platform charset for an unknown name.
        val charset = runCatching { Charset.forName(fields["charset"] ?: credentialCharset ?: "US-ASCII") }
            .getOrElse { Charset.defaultCharset() }
        fun hash(value: String, encoding: Charset = charset): String = hex(digest.digest(value.toByteArray(encoding)))
        var first = hash("$username:$realm:$password")
        if (algorithm.equals("MD5-sess", true)) first = hash("$first:$nonce:$cnonce")
        val qop = fields["qop"]
        val qops = qop?.split(',')?.filter(String::isNotEmpty)?.map { it.trim().lowercase(Locale.US) }.orEmpty()
        if (qop != null && "auth" !in qops) {
            if (hasBody && "auth-int" in qops) throw IllegalStateException("Qop auth-int cannot be used with a non-repeatable entity")
            throw IllegalStateException("None of the qop methods is supported: $qop")
        }
        val nc = String.format(Locale.US, "%08x", count)
        val second = hash("$method:$path")
        val responseText = if (qop == null) "$first:$nonce:$second" else "$first:$nonce:$nc:$cnonce:auth:$second"
        // DigestAuthenticator.i encodes the final response material as US-ASCII even with UTF-8 credentials.
        val response = hash(responseText, Charsets.US_ASCII)
        fun parameter(name: String, value: String, forceQuotes: Boolean = true): String {
            val quoted = forceQuotes || value.any { it in " ;,:@()<>\"/[]?={}\t" }
            val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
            return "$name=" + if (quoted) "\"$escaped\"" else escaped
        }
        return "Digest " + buildList {
            add(parameter("username", username)); add(parameter("realm", realm)); add(parameter("nonce", nonce))
            add(parameter("uri", path)); add(parameter("response", response))
            if (qop != null) { add(parameter("qop", "auth", false)); add(parameter("nc", nc, false)); add(parameter("cnonce", cnonce)) }
            add(parameter("algorithm", algorithm, false))
            fields["opaque"]?.let { add(parameter("opaque", it)) }
        }.joinToString(", ")
    }

    internal fun randomNonce(): String = ByteArray(8).also { SecureRandom().nextBytes(it) }.let(::hex)
    private fun headerWhitespace(character: Char) = character == ' ' || character == '\t' || character == '\r' || character == '\n'
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
}
