package dev.ujhhgtg.via.browser

import dev.ujhhgtg.via.engine.InterceptDecision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the blocked-subresource response. The original compares the extension *with* its leading dot
 * against "html|htm|css|js", which never matches, so every blocked subresource gets the 1×1 GIF.
 * The reconstruction keeps that behavior deliberately (see the z8.b0.F note in RequestInterceptor).
 */
class RequestInterceptorTest {
    @Test fun everyBlockedSubresourceGetsThePixel() {
        listOf(
            "https://ads.example/a.js", "https://ads.example/x/b.css?v=1", "https://ads.example/frame.html",
            "https://ads.example/banner.png", "https://ads.example/track", "https://ads.example/v.mp4",
        ).forEach { assertEquals(it, InterceptDecision.BlockImage, blockedResourceDecision(it)) }
    }

    @Test fun queryAndOverlongExtensionsDoNotChangeTheResult() {
        assertEquals(InterceptDecision.BlockImage, blockedResourceDecision("https://ads.example/a.js?x=y.png"))
        assertEquals(InterceptDecision.BlockImage, blockedResourceDecision("https://ads.example/a.javascript"))
    }
}
