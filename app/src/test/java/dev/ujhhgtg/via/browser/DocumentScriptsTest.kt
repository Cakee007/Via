package dev.ujhhgtg.via.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentScriptsTest {
    @Test fun lineBreaksDoNotEndTheLiteral() {
        assertEquals("\"a{}\\nb{}\\r\"", DocumentScripts.jsString("a{}\nb{}\r"))
    }

    @Test fun quotesAndCssEscapesSurvive() {
        assertEquals("\"[class^=\\\"ad\\\"],#\\\\31 x\"", DocumentScripts.jsString("[class^=\"ad\"],#\\31 x"))
    }

    @Test fun scriptLineTerminatorsAreEscaped() {
        assertEquals("\"\\u2028\\u2029\"", DocumentScripts.jsString("  "))
    }
}
