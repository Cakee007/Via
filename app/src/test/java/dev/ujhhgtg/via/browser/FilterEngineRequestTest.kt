package dev.ujhhgtg.via.browser

import dev.ujhhgtg.via.browser.filter.FilterEngine
import dev.ujhhgtg.via.browser.filter.FilterRequest
import dev.ujhhgtg.via.browser.filter.ResourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The filter is now driven only through engine-neutral [FilterRequest]s. */
class FilterEngineRequestTest {
    private val engine = FilterEngine().apply { addList("||ads.example^\n@@||ads.example/allowed.js") }

    @Test fun blocksMatchingRequest() {
        assertTrue(engine.shouldBlock(FilterRequest("https://ads.example/banner.png", "https://news.example/", ResourceType.IMAGE, isThirdParty = true)))
    }

    @Test fun exceptionWins() {
        assertFalse(engine.shouldBlock(FilterRequest("https://ads.example/allowed.js", "https://news.example/", ResourceType.SCRIPT, isThirdParty = true)))
    }

    @Test fun unrelatedRequestPasses() {
        assertFalse(engine.shouldBlock(FilterRequest("https://news.example/app.js", "https://news.example/", ResourceType.SCRIPT)))
    }
}
