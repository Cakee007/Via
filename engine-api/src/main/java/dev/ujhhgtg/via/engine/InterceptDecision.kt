package dev.ujhhgtg.via.engine

import java.io.InputStream

/** The outcome of request interception, mapped by each backend to its own response type. */
sealed interface InterceptDecision {
    /** Let the engine load the request normally. */
    data object Allow : InterceptDecision
    /** Blocked script/style/document subresource: an empty text body. */
    data object BlockEmpty : InterceptDecision
    /** Blocked image or other subresource: a 1×1 transparent GIF. */
    data object BlockImage : InterceptDecision
    /** Answer with generated content. [open] is called once, on the engine's network thread. */
    class Serve(val mime: String, val headers: Map<String, String> = emptyMap(), val open: () -> InputStream) : InterceptDecision
}
