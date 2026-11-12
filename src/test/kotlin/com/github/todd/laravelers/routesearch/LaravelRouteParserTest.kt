package com.github.todd.laravelers.routesearch

import com.github.todd.laravelers.routesearch.matcher.RoutePatternMatcher
import com.github.todd.laravelers.routesearch.parser.LaravelRouteParser
import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LaravelRouteParserTest {

    private val dummyFile = LightVirtualFile("web.php", "")

    @Test
    fun `test parsing standard routes and matching with empty variable syntax`() {
        val phpCode = """
            <?php
            use Illuminate\Support\Facades\Route;
            use App\Http\Controllers\UserController;

            Route::get('/', function () {
                return view('welcome');
            });

            Route::get('/go/to/{id}/edit', [UserController::class, 'edit'])->name('user.edit');
            Route::post('/go/to/{user}/edit', [UserController::class, 'update']);
            Route::delete('/users/{id}', [UserController::class, 'destroy']);
        """.trimIndent()

        val routes = LaravelRouteParser.parse(phpCode, dummyFile, "routes/web.php")
        assertEquals(4, routes.size)

        val editRoute = routes.firstOrNull { it.uri == "/go/to/{id}/edit" }
        assertNotNull(editRoute)
        assertEquals("GET", editRoute?.method)
        assertEquals("user.edit", editRoute?.name)
        assertEquals("UserController@edit", editRoute?.action)
        assertEquals(9, editRoute?.lineNumber)

        // Verify that searching /go/to/{}/edit matches the route!
        val query = RoutePatternMatcher.parseQuery("/go/to/{}/edit")
        val matchResult = RoutePatternMatcher.match(editRoute!!, query)
        assertNotNull(matchResult)
    }

    @Test
    fun `test parsing route prefix groups`() {
        val phpCode = """
            <?php
            Route::prefix('admin')->group(function () {
                Route::get('/dashboard', [AdminController::class, 'index']);
                Route::get('/users/{user}/edit', [AdminController::class, 'editUser']);
            });
        """.trimIndent()

        val routes = LaravelRouteParser.parse(phpCode, dummyFile, "routes/web.php")
        assertEquals(2, routes.size)

        val adminUserEdit = routes.firstOrNull { it.uri == "/admin/users/{user}/edit" }
        assertNotNull(adminUserEdit)

        // Searching /admin/users/{}/edit should match
        val query = RoutePatternMatcher.parseQuery("/admin/users/{}/edit")
        assertNotNull(RoutePatternMatcher.match(adminUserEdit!!, query))
    }

    @Test
    fun `test parsing resource routes`() {
        val phpCode = """
            <?php
            Route::resource('photos', PhotoController::class);
        """.trimIndent()

        val routes = LaravelRouteParser.parse(phpCode, dummyFile, "routes/web.php")
        // Standard resource has 7 routes: index, create, store, show, edit, update, destroy
        assertEquals(7, routes.size)

        val editResourceRoute = routes.firstOrNull { it.uri == "/photos/{photo}/edit" }
        assertNotNull(editResourceRoute)
        assertEquals("GET", editResourceRoute?.method)

        // Searching /photos/{}/edit should match the resource route!
        val query = RoutePatternMatcher.parseQuery("/photos/{}/edit")
        assertNotNull(RoutePatternMatcher.match(editResourceRoute!!, query))
    }
}
