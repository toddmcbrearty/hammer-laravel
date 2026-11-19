package com.github.todd.laravelers.routesearch

import com.github.todd.laravelers.routesearch.matcher.RoutePatternMatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoutePasteTest {

    @Test
    fun `test stripQuery removes simple query parameter`() {
        assertEquals("users", RoutePatternMatcher.stripQuery("users?x=1"))
        assertEquals("/users", RoutePatternMatcher.stripQuery("/users?x=1"))
        assertEquals("admin/company/188/accounts", RoutePatternMatcher.stripQuery("admin/company/188/accounts?x=1"))
    }

    @Test
    fun `test stripQuery removes multiple query parameters`() {
        assertEquals("/api/v1/orders", RoutePatternMatcher.stripQuery("/api/v1/orders?tab=billing&sort=desc&page=2"))
        assertEquals("users", RoutePatternMatcher.stripQuery("users?a=1&b=2"))
    }

    @Test
    fun `test stripQuery preserves optional route parameter syntax`() {
        assertEquals("/users/{name?}", RoutePatternMatcher.stripQuery("/users/{name?}"))
        assertEquals("/users/{id?}/posts", RoutePatternMatcher.stripQuery("/users/{id?}/posts"))
        assertEquals("{id?}", RoutePatternMatcher.stripQuery("{id?}"))
    }

    @Test
    fun `test stripQuery preserves optional route parameter while stripping query string`() {
        assertEquals("/users/{name?}", RoutePatternMatcher.stripQuery("/users/{name?}?x=1"))
        assertEquals("/users/{id?}/posts", RoutePatternMatcher.stripQuery("/users/{id?}/posts?x=1&foo=bar"))
    }

    @Test
    fun `test stripQuery with method prefix`() {
        assertEquals("GET /users", RoutePatternMatcher.stripQuery("GET /users?x=1"))
        assertEquals("POST /api/orders", RoutePatternMatcher.stripQuery("POST /api/orders?filter=true"))
    }

    @Test
    fun `test stripQuery with empty or bare query`() {
        assertEquals("", RoutePatternMatcher.stripQuery("?x=1"))
        assertEquals("/users", RoutePatternMatcher.stripQuery("/users?"))
        assertEquals("users", RoutePatternMatcher.stripQuery("users?"))
    }

    @Test
    fun `test stripQuery with full url`() {
        assertEquals("https://app.test/admin/company/188/accounts", RoutePatternMatcher.stripQuery("https://app.test/admin/company/188/accounts?x=1"))
        assertEquals("http://localhost:8000/api/users", RoutePatternMatcher.stripQuery("http://localhost:8000/api/users?tab=info&x=1"))
    }

    @Test
    fun `test stripDomain with various schemes and hosts`() {
        assertEquals("/admin/company/188/accounts", RoutePatternMatcher.stripDomain("https://app.test/admin/company/188/accounts"))
        assertEquals("/api/users", RoutePatternMatcher.stripDomain("http://localhost:8000/api/users"))
        assertEquals("/orders", RoutePatternMatcher.stripDomain("http://127.0.0.1:8000/orders"))
        assertEquals("/dashboard", RoutePatternMatcher.stripDomain("//example.com/dashboard"))
        assertEquals("/users", RoutePatternMatcher.stripDomain("localhost:8000/users"))
        assertEquals("/company/188", RoutePatternMatcher.stripDomain("app.test/company/188"))
        assertEquals("/", RoutePatternMatcher.stripDomain("http://localhost:8000/"))
        assertEquals("/", RoutePatternMatcher.stripDomain("http://localhost:8000"))
    }

    @Test
    fun `test stripDomain preserves method prefix`() {
        assertEquals("GET /api/users", RoutePatternMatcher.stripDomain("GET https://example.com/api/users"))
        assertEquals("POST /orders", RoutePatternMatcher.stripDomain("POST http://localhost:8000/orders"))
    }

    @Test
    fun `test stripDomain preserves route names and paths without domain`() {
        assertEquals("users.index", RoutePatternMatcher.stripDomain("users.index"))
        assertEquals("admin/company/188/accounts", RoutePatternMatcher.stripDomain("admin/company/188/accounts"))
        assertEquals("/admin/company/188/accounts", RoutePatternMatcher.stripDomain("/admin/company/188/accounts"))
        assertEquals("UserController", RoutePatternMatcher.stripDomain("UserController"))
    }

    @Test
    fun `test cleanPastedRoute removes both domain and query string`() {
        assertEquals("/admin/company/188/accounts", RoutePatternMatcher.cleanPastedRoute("http://localhost:8000/admin/company/188/accounts?x=1"))
        assertEquals("/api/users", RoutePatternMatcher.cleanPastedRoute("https://app.test/api/users?tab=info&x=1"))
        assertEquals("/orders", RoutePatternMatcher.cleanPastedRoute("localhost:8000/orders?status=pending"))
        assertEquals("/users/{id?}", RoutePatternMatcher.cleanPastedRoute("https://app.test/users/{id?}?x=1"))
        assertEquals("GET /api/users", RoutePatternMatcher.cleanPastedRoute("GET https://example.com/api/users?x=1"))
    }

    @Test
    fun `test setupSearchPasteHandler strips domain and query on transferHandler importData`() {
        val textField = javax.swing.JTextField()
        com.github.todd.laravelers.routesearch.ui.LaravelRoutesPanel.setupSearchPasteHandler(textField)

        val transferable = java.awt.datatransfer.StringSelection("http://localhost:8000/admin/company/188/accounts?x=1")
        val support = javax.swing.TransferHandler.TransferSupport(textField, transferable)

        val imported = textField.transferHandler.importData(support)
        org.junit.jupiter.api.Assertions.assertTrue(imported)
        assertEquals("/admin/company/188/accounts", textField.text)
    }

    @Test
    fun `test setupSearchPasteHandler strips bare domain on transferHandler importData`() {
        val textField = javax.swing.JTextField()
        com.github.todd.laravelers.routesearch.ui.LaravelRoutesPanel.setupSearchPasteHandler(textField)

        val transferable = java.awt.datatransfer.StringSelection("localhost:8000/admin/company/188/accounts")
        val support = javax.swing.TransferHandler.TransferSupport(textField, transferable)

        val imported = textField.transferHandler.importData(support)
        org.junit.jupiter.api.Assertions.assertTrue(imported)
        assertEquals("/admin/company/188/accounts", textField.text)
    }

    @Test
    fun `test setupSearchPasteHandler strips query on transferHandler importData`() {
        val textField = javax.swing.JTextField()
        com.github.todd.laravelers.routesearch.ui.LaravelRoutesPanel.setupSearchPasteHandler(textField)

        val transferable = java.awt.datatransfer.StringSelection("admin/company/188/accounts?x=1")
        val support = javax.swing.TransferHandler.TransferSupport(textField, transferable)

        val imported = textField.transferHandler.importData(support)
        org.junit.jupiter.api.Assertions.assertTrue(imported)
        assertEquals("admin/company/188/accounts", textField.text)
    }

    @Test
    fun `test setupSearchPasteHandler strips bare query string on paste`() {
        val textField = javax.swing.JTextField()
        com.github.todd.laravelers.routesearch.ui.LaravelRoutesPanel.setupSearchPasteHandler(textField)

        val transferable = java.awt.datatransfer.StringSelection("?x=1")
        val support = javax.swing.TransferHandler.TransferSupport(textField, transferable)

        val imported = textField.transferHandler.importData(support)
        org.junit.jupiter.api.Assertions.assertTrue(imported)
        assertEquals("", textField.text)
    }

    @Test
    fun `test setupSearchPasteHandler preserves optional route param while stripping query`() {
        val textField = javax.swing.JTextField()
        com.github.todd.laravelers.routesearch.ui.LaravelRoutesPanel.setupSearchPasteHandler(textField)

        val transferable = java.awt.datatransfer.StringSelection("/users/{id?}/posts?x=1&foo=bar")
        val support = javax.swing.TransferHandler.TransferSupport(textField, transferable)

        val imported = textField.transferHandler.importData(support)
        org.junit.jupiter.api.Assertions.assertTrue(imported)
        assertEquals("/users/{id?}/posts", textField.text)
    }

    @Test
    fun `test typing optional param character by character is preserved`() {
        val textField = javax.swing.JTextField()
        com.github.todd.laravelers.routesearch.ui.LaravelRoutesPanel.setupSearchPasteHandler(textField)

        val doc = textField.document
        doc.insertString(doc.length, "{", null)
        doc.insertString(doc.length, "i", null)
        doc.insertString(doc.length, "d", null)
        doc.insertString(doc.length, "?", null)
        doc.insertString(doc.length, "}", null)

        assertEquals("{id?}", textField.text)
    }

    @Test
    fun `test setText strips domain and query via document filter`() {
        val textField = javax.swing.JTextField()
        com.github.todd.laravelers.routesearch.ui.LaravelRoutesPanel.setupSearchPasteHandler(textField)

        textField.text = "http://localhost:8000/admin/company/188/accounts?x=1"
        assertEquals("/admin/company/188/accounts", textField.text)
    }

    @Test
    fun `test route matching works seamlessly when query contains query parameter`() {
        val dummyFile = com.intellij.testFramework.LightVirtualFile("web.php", "")
        val routeItem = com.github.todd.laravelers.routesearch.model.RouteItem(
            method = "GET",
            uri = "admin/company/{}/accounts",
            rawUri = "admin/company/{}/accounts",
            file = dummyFile,
            filePath = "routes/web.php",
            lineNumber = 10,
            lineContent = "Route::get('admin/company/{company}/accounts', ...);"
        )

        // Searching with pasted query like "http://localhost:8000/admin/company/188/accounts?x=1"
        val parsed = RoutePatternMatcher.parseQuery("http://localhost:8000/admin/company/188/accounts?x=1")
        val score = RoutePatternMatcher.match(routeItem, parsed)
        org.junit.jupiter.api.Assertions.assertNotNull(score)
        org.junit.jupiter.api.Assertions.assertTrue(score!! >= 90)
    }
}
