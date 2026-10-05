package dev.ujhhgtg.via.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {
    @Test fun untaggedStateBelongsToWebView() {
        assertTrue(SessionState.writtenBy(null, "webview"))
        assertFalse(SessionState.writtenBy(null, "gecko"))
    }

    @Test fun taggedStateOnlyMatchesItsBackend() {
        assertTrue(SessionState.writtenBy("gecko", "gecko"))
        assertFalse(SessionState.writtenBy("gecko", "webview"))
        assertFalse(SessionState.writtenBy("webview", "gecko"))
    }
}
