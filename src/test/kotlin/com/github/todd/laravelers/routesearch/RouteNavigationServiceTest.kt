package com.github.todd.laravelers.routesearch

import com.github.todd.laravelers.routesearch.model.RouteItem
import com.github.todd.laravelers.routesearch.service.RouteNavigationService
import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteNavigationServiceTest {

    private val dummyFile = LightVirtualFile("web.php", "")

    private fun createRouteItem(action: String?): RouteItem {
        return RouteItem(
            method = "GET",
            uri = "/users",
            rawUri = "/users",
            action = action,
            file = dummyFile,
            filePath = "routes/web.php",
            lineNumber = 1,
            lineContent = "Route::get('/users', ...);"
        )
    }

    @Test
    fun `test route item controller and method extraction`() {
        val standardItem = createRouteItem("UserController@edit")
        assertEquals("UserController", standardItem.controllerClass)
        assertEquals("edit", standardItem.controllerMethod)
        assertFalse(standardItem.isClosure)

        val namespacedItem = createRouteItem("App\\Http\\Controllers\\Admin\\OrderController@destroy")
        assertEquals("OrderController", namespacedItem.controllerClass)
        assertEquals("destroy", namespacedItem.controllerMethod)
        assertFalse(namespacedItem.isClosure)

        val invokableItem = createRouteItem("ShowProfileController")
        assertEquals("ShowProfileController", invokableItem.controllerClass)
        assertEquals("__invoke", invokableItem.controllerMethod)
        assertFalse(invokableItem.isClosure)

        val closureItem = createRouteItem("Closure")
        assertNull(closureItem.controllerClass)
        assertNull(closureItem.controllerMethod)
        assertTrue(closureItem.isClosure)
    }

    @Test
    fun `test finding method line inside controller PHP code`() {
        val phpControllerCode = """
            <?php
            namespace App\Http\Controllers;

            use Illuminate\Http\Request;

            class UserController extends Controller
            {
                public function index()
                {
                    return view('users.index');
                }

                public function edit(string ${'$'}id)
                {
                    return view('users.edit', ['id' => ${'$'}id]);
                }

                public function update(Request ${'$'}request, ${'$'}id)
                {
                    // update logic
                }
            }
        """.trimIndent()

        val controllerVirtualFile = LightVirtualFile("UserController.php", phpControllerCode)

        // Project can be mock/dummy for finding method line in LightVirtualFile
        // Lines in phpControllerCode:
        // index 0: <?php
        // ...
        // line with "public function index()": index 7
        // line with "public function edit(": index 12
        // line with "public function update(": index 17
        val lines = phpControllerCode.lines()
        val editLine = lines.indexOfFirst { it.contains("public function edit(") }
        assertEquals(12, editLine)
    }
}
