package dev.ujhhgtg.via.browser.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmApiSourceTest {
    @Test fun removeListenerGrantStillGetsListenerStorage() {
        val source = GmApiSource.scriptApi("script", "secret", 16384)
        assertTrue(source.contains("valueListeners"))
        assertTrue(source.contains("GM_removeValueChangeListener"))
        assertFalse(source.contains("GM_addValueChangeListener"))
    }

    @Test fun notifyNameIsStableAndDoesNotRevealTheSecret() {
        val secret = "0f1e2d3c-secret"
        val name = GmApiSource.notifyName("script", secret)
        assertEquals(name, GmApiSource.notifyName("script", secret))
        assertFalse(name.contains("0f1e2d3c"))
        assertTrue(name != GmApiSource.notifyName("other", secret))
        assertTrue(Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name))
    }

    @Test fun noFramesIsReadFromTheMetadataBlockOnly() {
        fun script(body: String) = UserScript(scriptId = "s", content = body)
        assertTrue(script("// ==UserScript==\n// @name x\n// @noframes\n// ==/UserScript==\n").noFrames)
        assertFalse(script("// ==UserScript==\n// @name x\n// ==/UserScript==\n// @noframes\n").noFrames)
        assertFalse(script("// ==UserScript==\n// @noframesX\n// ==/UserScript==\n").noFrames)
    }
}
