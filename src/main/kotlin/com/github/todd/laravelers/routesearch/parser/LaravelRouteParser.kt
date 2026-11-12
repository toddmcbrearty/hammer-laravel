package com.github.todd.laravelers.routesearch.parser

import com.github.todd.laravelers.routesearch.model.RouteItem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Robust parser for Laravel route definitions in PHP files.
 * Extracts standard routes, match routes, resource routes, prefixes, actions, and names.
 */
object LaravelRouteParser {

    private val STANDARD_METHODS = listOf("get", "post", "put", "patch", "delete", "options", "any")

    // Matches Route::get('path', ...) or $router->post("path", ...)
    private val ROUTE_METHOD_REGEX = Regex(
        """(?:Route|\${'$'}router|\${'$'}app)(?:::|->)(get|post|put|patch|delete|options|any|view|redirect)\s*\(\s*['"]([^'"]+)['"]""",
        RegexOption.IGNORE_CASE
    )

    // Matches Route::match(['get', 'post'], 'path', ...)
    private val ROUTE_MATCH_REGEX = Regex(
        """(?:Route|\${'$'}router|\${'$'}app)(?:::|->)match\s*\(\s*(?:\[([^\]]*)\]|['"]([^'"]+)['"])\s*,\s*['"]([^'"]+)['"]""",
        RegexOption.IGNORE_CASE
    )

    // Matches Route::resource('photos', ...) or Route::apiResource('posts', ...)
    private val ROUTE_RESOURCE_REGEX = Regex(
        """(?:Route|\${'$'}router|\${'$'}app)(?:::|->)(resource|apiResource)\s*\(\s*['"]([^'"]+)['"](?:\s*,\s*([^,)\n]+))?""",
        RegexOption.IGNORE_CASE
    )

    // Matches Route::prefix('admin')->group(...) or Route::group(['prefix' => 'admin'], ...)
    private val PREFIX_METHOD_REGEX = Regex(
        """(?:Route|\${'$'}router|\${'$'}app)(?:::|->)prefix\s*\(\s*['"]([^'"]+)['"]\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val PREFIX_ARRAY_REGEX = Regex(
        """['"]prefix['"]\s*=>\s*['"]([^'"]+)['"]""",
        RegexOption.IGNORE_CASE
    )

    // Matches ->name('...')
    private val ROUTE_NAME_REGEX = Regex(
        """->\s*name\s*\(\s*['"]([^'"]+)['"]\s*\)""",
        RegexOption.IGNORE_CASE
    )

    // Matches controller actions like [UserController::class, 'edit'] or 'UserController@edit'
    private val CONTROLLER_CLASS_REGEX = Regex(
        """\[\s*([a-zA-Z0-9_\\]+)::class\s*,\s*['"]([a-zA-Z0-9_]+)['"]\s*\]"""
    )
    private val CONTROLLER_STRING_REGEX = Regex(
        """['"]([a-zA-Z0-9_\\]+Controller)@([a-zA-Z0-9_]+)['"]"""
    )

    /**
     * Parses the text content of a route file and extracts all RouteItems.
     */
    fun parse(content: String, file: VirtualFile, relativePath: String): List<RouteItem> {
        val lines = content.lines()
        val routes = mutableListOf<RouteItem>()

        val prefixStack = mutableListOf<String>()
        val braceDepthStack = mutableListOf<Int>()
        var currentBraceDepth = 0

        var lineOffset = 0
        for (lineIndex in lines.indices) {
            val line = lines[lineIndex]
            val lineNumber = lineIndex + 1

            // Track braces to maintain prefix scopes
            for (char in line) {
                if (char == '{') {
                    currentBraceDepth++
                } else if (char == '}') {
                    currentBraceDepth--
                    if (braceDepthStack.isNotEmpty() && currentBraceDepth < braceDepthStack.last()) {
                        braceDepthStack.removeAt(braceDepthStack.size - 1)
                        if (prefixStack.isNotEmpty()) {
                            prefixStack.removeAt(prefixStack.size - 1)
                        }
                    }
                }
            }

            // Check for prefix group start
            val prefixMatch = PREFIX_METHOD_REGEX.find(line) ?: PREFIX_ARRAY_REGEX.find(line)
            if (prefixMatch != null && (line.contains("group") || line.contains("function"))) {
                val prefix = prefixMatch.groupValues[1].trim('/')
                if (prefix.isNotEmpty()) {
                    prefixStack.add(prefix)
                    braceDepthStack.add(currentBraceDepth)
                }
            }

            // Check multi-line context (look ahead up to 5 lines for chained calls like ->name())
            val contextBlock = buildString {
                for (offset in 0 until 5) {
                    if (lineIndex + offset < lines.size) {
                        append(lines[lineIndex + offset]).append(" ")
                    }
                }
            }

            val routeName = ROUTE_NAME_REGEX.find(contextBlock)?.groupValues?.get(1)
            val action = extractAction(contextBlock)

            // 1. Check Standard Route methods (get, post, put, etc.)
            val standardMatch = ROUTE_METHOD_REGEX.find(line)
            if (standardMatch != null) {
                val method = standardMatch.groupValues[1].uppercase()
                val rawUri = standardMatch.groupValues[2]
                val fullUri = buildFullUri(prefixStack, rawUri)

                routes.add(
                    RouteItem(
                        method = method,
                        uri = fullUri,
                        rawUri = rawUri,
                        action = action,
                        name = routeName,
                        file = file,
                        filePath = relativePath,
                        lineNumber = lineNumber,
                        lineContent = line.trim(),
                        textOffset = lineOffset + standardMatch.range.first
                    )
                )
                lineOffset += line.length + 1
                continue
            }

            // 2. Check Match Route
            val matchMatch = ROUTE_MATCH_REGEX.find(line)
            if (matchMatch != null) {
                val methodsArray = matchMatch.groupValues[1].ifEmpty { matchMatch.groupValues[2] }
                val methods = methodsArray.replace("'", "").replace("\"", "").replace(" ", "").uppercase()
                val rawUri = matchMatch.groupValues[3]
                val fullUri = buildFullUri(prefixStack, rawUri)

                routes.add(
                    RouteItem(
                        method = if (methods.isNotEmpty()) methods else "MATCH",
                        uri = fullUri,
                        rawUri = rawUri,
                        action = action,
                        name = routeName,
                        file = file,
                        filePath = relativePath,
                        lineNumber = lineNumber,
                        lineContent = line.trim(),
                        textOffset = lineOffset + matchMatch.range.first
                    )
                )
                lineOffset += line.length + 1
                continue
            }

            // 3. Check Resource Route
            val resourceMatch = ROUTE_RESOURCE_REGEX.find(line)
            if (resourceMatch != null) {
                val isApi = resourceMatch.groupValues[1].equals("apiResource", ignoreCase = true)
                val rawResource = resourceMatch.groupValues[2].trim('/')
                val controller = resourceMatch.groupValues.getOrNull(3)?.trim()?.let { cleanController(it) } ?: action

                // Expand resource route to individual actions
                val expandedRoutes = expandResource(
                    rawResource = rawResource,
                    prefixStack = prefixStack,
                    isApi = isApi,
                    controller = controller,
                    file = file,
                    filePath = relativePath,
                    lineNumber = lineNumber,
                    lineContent = line.trim(),
                    textOffset = lineOffset + resourceMatch.range.first
                )
                routes.addAll(expandedRoutes)
                lineOffset += line.length + 1
                continue
            }

            lineOffset += line.length + 1
        }

        return routes
    }

    private fun buildFullUri(prefixStack: List<String>, uri: String): String {
        val cleanUri = uri.trim().trimStart('/')
        if (prefixStack.isEmpty()) {
            return "/$cleanUri"
        }
        val prefix = prefixStack.joinToString("/") { it.trim('/') }
        return if (cleanUri.isEmpty()) "/$prefix" else "/$prefix/$cleanUri"
    }

    private fun extractAction(block: String): String? {
        val classMatch = CONTROLLER_CLASS_REGEX.find(block)
        if (classMatch != null) {
            val className = classMatch.groupValues[1].substringAfterLast('\\')
            val method = classMatch.groupValues[2]
            return "$className@$method"
        }

        val stringMatch = CONTROLLER_STRING_REGEX.find(block)
        if (stringMatch != null) {
            val className = stringMatch.groupValues[1].substringAfterLast('\\')
            val method = stringMatch.groupValues[2]
            return "$className@$method"
        }

        if (block.contains("function") || block.contains("fn(")) {
            return "Closure"
        }

        return null
    }

    private fun cleanController(raw: String): String {
        val trimmed = raw.removeSuffix("::class").trim()
        return trimmed.substringAfterLast('\\')
    }

    private fun expandResource(
        rawResource: String,
        prefixStack: List<String>,
        isApi: Boolean,
        controller: String?,
        file: VirtualFile,
        filePath: String,
        lineNumber: Int,
        lineContent: String,
        textOffset: Int
    ): List<RouteItem> {
        val singular = if (rawResource.endsWith("ies")) {
            rawResource.removeSuffix("ies") + "y"
        } else if (rawResource.endsWith("s")) {
            rawResource.removeSuffix("s")
        } else {
            rawResource
        }

        val paramName = "{$singular}"
        val baseUri = buildFullUri(prefixStack, rawResource)

        val controllerName = controller?.substringBefore('@')

        val actions = mutableListOf<ResourceActionDef>()
        actions.add(ResourceActionDef("GET", baseUri, "index", "$rawResource.index"))
        if (!isApi) {
            actions.add(ResourceActionDef("GET", "$baseUri/create", "create", "$rawResource.create"))
        }
        actions.add(ResourceActionDef("POST", baseUri, "store", "$rawResource.store"))
        actions.add(ResourceActionDef("GET", "$baseUri/$paramName", "show", "$rawResource.show"))
        if (!isApi) {
            actions.add(ResourceActionDef("GET", "$baseUri/$paramName/edit", "edit", "$rawResource.edit"))
        }
        actions.add(ResourceActionDef("PUT/PATCH", "$baseUri/$paramName", "update", "$rawResource.update"))
        actions.add(ResourceActionDef("DELETE", "$baseUri/$paramName", "destroy", "$rawResource.destroy"))

        return actions.map { def ->
            RouteItem(
                method = def.method,
                uri = def.uri,
                rawUri = def.uri,
                action = if (controllerName != null) "$controllerName@${def.actionMethod}" else def.actionMethod,
                name = def.routeName,
                file = file,
                filePath = filePath,
                lineNumber = lineNumber,
                lineContent = lineContent,
                textOffset = textOffset
            )
        }
    }

    private data class ResourceActionDef(
        val method: String,
        val uri: String,
        val actionMethod: String,
        val routeName: String
    )
}
