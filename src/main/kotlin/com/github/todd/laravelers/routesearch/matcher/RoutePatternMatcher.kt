package com.github.todd.laravelers.routesearch.matcher

import com.github.todd.laravelers.routesearch.model.RouteItem

/**
 * Parses user search query and matches against route definitions.
 *
 * Query Syntax:
 * - `{}`: Matches any parameter variable, e.g. `/go/to/{}/edit` matches `/go/to/{id}/edit`, `/go/to/{user}/edit`.
 * - `{name}`: Matches parameter containing 'name', e.g. `{id}` matches `{id}` or `{id?}`.
 * - Leading slash: Matches both `/path` and `path`.
 * - Method prefix: e.g. `GET /users` filters only GET routes.
 * - Name search: e.g. `users.index` matches named routes.
 * - Wildcard `*`: Matches any character sequence within or across segments.
 */
object RoutePatternMatcher {

    data class ParsedQuery(
        val methodFilter: String? = null,
        val rawPattern: String,
        val uriRegex: Regex?,
        val exactUriRegex: Regex?
    )

    private val HTTP_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD", "ANY", "MATCH")

    /**
     * Parses the raw user query string into a ParsedQuery.
     */
    fun parseQuery(query: String): ParsedQuery {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return ParsedQuery(null, "", null, null)
        }

        // Check for method prefix like "GET /users" or "post /go/to/{}"
        val firstSpace = trimmed.indexOf(' ')
        var method: String? = null
        var pathQuery = trimmed

        if (firstSpace > 0) {
            val potentialMethod = trimmed.substring(0, firstSpace).uppercase()
            if (HTTP_METHODS.contains(potentialMethod)) {
                method = potentialMethod
                pathQuery = trimmed.substring(firstSpace + 1).trim()
            }
        }

        val uriRegex = buildRegex(pathQuery, exact = false)
        val exactRegex = buildRegex(pathQuery, exact = true)

        return ParsedQuery(method, pathQuery, uriRegex, exactRegex)
    }

    /**
     * Converts a user URI pattern into a regex.
     * `{}` becomes `\{[^/}]+\}` to match any variable parameter.
     */
    fun buildRegex(query: String, exact: Boolean = false): Regex? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        val sb = StringBuilder()
        sb.append("(?i)") // Case-insensitive

        val hasLeadingSlash = trimmed.startsWith("/")
        val input = if (hasLeadingSlash) trimmed.substring(1) else trimmed

        if (exact) {
            sb.append("^/?")
        } else {
            if (hasLeadingSlash) {
                sb.append("^/?")
            } else {
                sb.append("(?:^|/|(?<=/))")
            }
        }

        var i = 0
        while (i < input.length) {
            when {
                // Wildcard parameter `{}`
                input.startsWith("{}", i) -> {
                    sb.append("""\{[^/}]+\}""")
                    i += 2
                }
                // Named parameter or partial parameter `{id}`
                input[i] == '{' -> {
                    val closeIdx = input.indexOf('}', i)
                    if (closeIdx != -1) {
                        val paramName = input.substring(i + 1, closeIdx).trim()
                        if (paramName.isEmpty()) {
                            sb.append("""\{[^/}]+\}""")
                        } else {
                            sb.append("""\{[^/}]*""").append(Regex.escape(paramName)).append("""[^/}]*\}""")
                        }
                        i = closeIdx + 1
                    } else {
                        // Unclosed `{`
                        sb.append("""\{[^/}]*""")
                        i++
                    }
                }
                input[i] == '*' -> {
                    sb.append(""".*""")
                    i++
                }
                input[i] == '/' -> {
                    sb.append("""/""")
                    i++
                }
                else -> {
                    sb.append(Regex.escape(input[i].toString()))
                    i++
                }
            }
        }

        if (exact) {
            sb.append("""/?$""")
        }

        return try {
            Regex(sb.toString())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Determines whether a RouteItem matches the query, and computes a relevance score.
     * Higher score = better match.
     * Returns null if does not match.
     */
    fun match(item: RouteItem, query: ParsedQuery): Int? {
        if (query.rawPattern.isEmpty() && query.methodFilter == null) {
            return 100 // Empty query matches all
        }

        // Method filter check
        if (query.methodFilter != null) {
            val itemMethod = item.method.uppercase()
            if (itemMethod != query.methodFilter && itemMethod != "ANY" && !itemMethod.contains(query.methodFilter)) {
                return null
            }
        }

        if (query.rawPattern.isEmpty()) {
            return 90
        }

        val rawUri = item.rawUri
        val normalizedUri = item.normalizedUri
        val exactRegex = query.exactUriRegex
        val uriRegex = query.uriRegex

        // 1. Exact full regex match on normalized URI
        if (exactRegex != null) {
            if (exactRegex.matches(normalizedUri) || exactRegex.matches(rawUri)) {
                return 100
            }
        }

        // 2. Partial regex match
        if (uriRegex != null) {
            if (uriRegex.containsMatchIn(normalizedUri) || uriRegex.containsMatchIn(rawUri)) {
                // If it starts with the match, rank higher
                val isPrefix = normalizedUri.startsWith(query.rawPattern, ignoreCase = true) ||
                        rawUri.startsWith(query.rawPattern, ignoreCase = true)
                return if (isPrefix) 80 else 60
            }
        }

        // 3. Route Name match (e.g. searching "users.edit")
        val routeName = item.name
        if (routeName != null && routeName.contains(query.rawPattern, ignoreCase = true)) {
            return 50
        }

        // 4. Action / Controller match (e.g. searching "UserController")
        val action = item.action
        if (action != null && action.contains(query.rawPattern, ignoreCase = true)) {
            return 40
        }

        return null
    }

    /**
     * Convenience function to test matching against a URI directly.
     */
    fun matchesUri(uri: String, query: String): Boolean {
        val parsed = parseQuery(query)
        val regex = parsed.uriRegex ?: return true
        val normalized = if (uri.startsWith("/")) uri else "/$uri"
        return regex.containsMatchIn(normalized) || regex.containsMatchIn(uri)
    }

    /**
     * Convenience function to test exact matching against a URI.
     */
    fun matchesUriExact(uri: String, query: String): Boolean {
        val parsed = parseQuery(query)
        val regex = parsed.exactUriRegex ?: return true
        val normalized = if (uri.startsWith("/")) uri else "/$uri"
        return regex.matches(normalized) || regex.matches(uri)
    }
}
