package com.github.todd.laravelers.routesearch

import com.github.todd.laravelers.routesearch.matcher.RoutePatternMatcher
import com.github.todd.laravelers.routesearch.model.RouteItem
import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoutePatternMatcherTest {

    private val dummyFile = LightVirtualFile("web.php", "")

    private fun createItem(method: String, uri: String, name: String? = null, action: String? = null): RouteItem {
        return RouteItem(
            method = method,
            uri = uri,
            rawUri = uri,
            action = action,
            name = name,
            file = dummyFile,
            filePath = "routes/web.php",
            lineNumber = 10,
            lineContent = "Route::get('$uri', ...);"
        )
    }

    @Test
    fun `test matches empty parameter placeholder with variable`() {
        // Requirement: /go/to/{}/edit matches route with variable parameter like /go/to/{user}/edit
        val query = RoutePatternMatcher.parseQuery("/go/to/{}/edit")

        val match1 = createItem("GET", "/go/to/{user}/edit")
        val match2 = createItem("GET", "/go/to/{id}/edit")
        val match3 = createItem("GET", "/go/to/{post:slug}/edit")
        val match4 = createItem("GET", "/go/to/{id?}/edit")
        val matchWithoutSlash = createItem("GET", "go/to/{id}/edit")

        assertNotNull(RoutePatternMatcher.match(match1, query))
        assertNotNull(RoutePatternMatcher.match(match2, query))
        assertNotNull(RoutePatternMatcher.match(match3, query))
        assertNotNull(RoutePatternMatcher.match(match4, query))
        assertNotNull(RoutePatternMatcher.match(matchWithoutSlash, query))

        // Non matches
        val noMatch1 = createItem("GET", "/go/to/static/edit")
        val noMatch2 = createItem("GET", "/go/to/{id}/delete")
        val noMatch3 = createItem("GET", "/other/path")

        assertNull(RoutePatternMatcher.match(noMatch1, query))
        assertNull(RoutePatternMatcher.match(noMatch2, query))
        assertNull(RoutePatternMatcher.match(noMatch3, query))
    }

    @Test
    fun `test matches with multiple empty variable placeholders`() {
        val query = RoutePatternMatcher.parseQuery("/organizations/{}/teams/{}/members")

        val match = createItem("GET", "/organizations/{org}/teams/{team}/members")
        val matchUuid = createItem("GET", "/organizations/{organization:uuid}/teams/{team_id}/members")

        assertNotNull(RoutePatternMatcher.match(match, query))
        assertNotNull(RoutePatternMatcher.match(matchUuid, query))

        val nonMatch = createItem("GET", "/organizations/fixed/teams/{team}/members")
        assertNull(RoutePatternMatcher.match(nonMatch, query))
    }

    @Test
    fun `test matches named variable specifically`() {
        val query = RoutePatternMatcher.parseQuery("/users/{id}")

        val match1 = createItem("GET", "/users/{id}")
        val match2 = createItem("GET", "/users/{id?}")
        val match3 = createItem("GET", "/users/{userId}")

        assertNotNull(RoutePatternMatcher.match(match1, query))
        assertNotNull(RoutePatternMatcher.match(match2, query))
        assertNotNull(RoutePatternMatcher.match(match3, query))

        val differentParam = createItem("GET", "/users/{slug}")
        assertNull(RoutePatternMatcher.match(differentParam, query))
    }

    @Test
    fun `test method filtering in query`() {
        val query = RoutePatternMatcher.parseQuery("POST /go/to/{}/edit")

        val postItem = createItem("POST", "/go/to/{id}/edit")
        val getItem = createItem("GET", "/go/to/{id}/edit")

        assertNotNull(RoutePatternMatcher.match(postItem, query))
        assertNull(RoutePatternMatcher.match(getItem, query))
    }

    @Test
    fun `test search by route name and controller`() {
        val queryName = RoutePatternMatcher.parseQuery("users.edit")
        val itemWithName = createItem("GET", "/users/{id}/edit", name = "users.edit")
        assertNotNull(RoutePatternMatcher.match(itemWithName, queryName))

        val queryCtrl = RoutePatternMatcher.parseQuery("UserController")
        val itemWithCtrl = createItem("GET", "/users/{id}", action = "UserController@show")
        assertNotNull(RoutePatternMatcher.match(itemWithCtrl, queryCtrl))
    }

    @Test
    fun `test matchesUri helper function`() {
        assertTrue(RoutePatternMatcher.matchesUri("/go/to/{user}/edit", "/go/to/{}/edit"))
        assertTrue(RoutePatternMatcher.matchesUri("go/to/{id}/edit", "/go/to/{}/edit"))
        assertTrue(RoutePatternMatcher.matchesUri("/go/to/{user:slug}/edit", "go/to/{}/edit"))
        assertFalse(RoutePatternMatcher.matchesUri("/go/to/view", "/go/to/{}/edit"))
    }
}
