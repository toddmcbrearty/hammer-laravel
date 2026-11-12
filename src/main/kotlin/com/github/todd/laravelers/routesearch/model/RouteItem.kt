package com.github.todd.laravelers.routesearch.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * Represents a Laravel route definition discovered in a project file.
 */
data class RouteItem(
    val method: String,            // e.g. "GET", "POST", "PUT", "DELETE", "ANY", "MATCH", "RESOURCE"
    val uri: String,               // e.g. "/go/to/{user}/edit" or "go/to/{id}/edit"
    val rawUri: String,            // The raw string in the source file before prefix resolution
    val action: String? = null,    // e.g. "UserController@edit", "Closure"
    val name: String? = null,      // e.g. "users.edit" (from ->name('users.edit'))
    val middleware: List<String> = emptyList(),
    val file: VirtualFile,
    val filePath: String,          // Relative project path e.g. "routes/web.php"
    val lineNumber: Int,           // 1-based line number in file
    val lineContent: String,       // Full line content or snippet
    val textOffset: Int = 0        // Character offset in document
) {
    /**
     * Normalized URI always beginning with '/' for consistent matching and presentation.
     */
    val normalizedUri: String = if (uri.startsWith("/")) uri else "/$uri"

    /**
     * Display label for UI lists.
     */
    val displayName: String
        get() = "$method $normalizedUri"

    /**
     * Whether the route action is an anonymous closure.
     */
    val isClosure: Boolean
        get() = action?.contains("Closure", ignoreCase = true) == true

    /**
     * Name of the controller class, e.g. "UserController".
     */
    val controllerClass: String?
        get() {
            if (isClosure) return null
            val act = action ?: return null
            val rawClass = act.substringBefore('@').trim()
            return rawClass.substringAfterLast('\\').takeIf { it.isNotEmpty() && it != "Closure" }
        }

    /**
     * Name of the method/function within the controller, e.g. "edit" or "__invoke".
     */
    val controllerMethod: String?
        get() {
            if (isClosure) return null
            val act = action ?: return null
            if (!act.contains('@')) return "__invoke"
            val rawMethod = act.substringAfter('@').trim()
            return rawMethod.takeIf { it.isNotEmpty() } ?: "__invoke"
        }
}
