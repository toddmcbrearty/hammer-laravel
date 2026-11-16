package com.github.todd.laravelers.routesearch

import com.github.todd.laravelers.routesearch.matcher.RoutePatternMatcher
import com.github.todd.laravelers.routesearch.model.RouteItem
import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteRankingTest {

    private val dummyFile = LightVirtualFile("web.php", "")

    private fun createItem(uri: String): RouteItem {
        return RouteItem(
            method = "GET",
            uri = uri,
            rawUri = uri,
            file = dummyFile,
            filePath = "routes/web.php",
            lineNumber = 1,
            lineContent = "Route::get('$uri', ...);"
        )
    }

    @Test
    fun `test exact match ranks higher than subpath match`() {
        val query = RoutePatternMatcher.parseQuery("/go/to/{}/edit")
        val exactItem = createItem("/go/to/{id}/edit")
        val subpathItem = createItem("/go/to/{id}/edit/comments")

        val exactScore = RoutePatternMatcher.match(exactItem, query) ?: 0
        val subpathScore = RoutePatternMatcher.match(subpathItem, query) ?: 0

        assertTrue(exactScore > subpathScore, "Exact match score ($exactScore) should be higher than subpath score ($subpathScore)")
    }

    @Test
    fun `test concrete path exact match ranks higher than subpath or child route`() {
        val query = RoutePatternMatcher.parseQuery("admin/company/188/accounts")
        val exactItem = createItem("admin/company/{}/accounts")
        val childItem = createItem("admin/company/{}/accounts/details")

        val exactScore = RoutePatternMatcher.match(exactItem, query) ?: 0
        val childScore = RoutePatternMatcher.match(childItem, query) ?: 0

        assertTrue(exactScore > childScore, "Exact structural match score ($exactScore) should be higher than child route score ($childScore)")
    }
}
