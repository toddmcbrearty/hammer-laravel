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
        val exactUriRegex: Regex?,
        val cleanedPath: String = rawPattern,
        val pathSegments: List<String> = emptyList()
    )

    private val HTTP_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD", "ANY", "MATCH")

    private val BARE_HOST_REGEX = Regex(
        """^(?:localhost|127\.0\.0\.1|[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*\.[a-zA-Z]{2,})(?::\d+)?(/.*)?$""",
        RegexOption.IGNORE_CASE
    )
    private val HOST_WITH_PORT_REGEX = Regex(
        """^(?:localhost|127\.0\.0\.1|[a-zA-Z0-9.-]+):\d+(/.*)?$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Strips any URL query parameters (starting with '?') from a route or URL string,
     * while preserving Laravel optional parameter syntax like '{id?}' or '{name?}'.
     */
    fun stripQuery(query: String): String {
        var inBraces = false
        for (i in query.indices) {
            when (query[i]) {
                '{' -> inBraces = true
                '}' -> inBraces = false
                '?' -> {
                    if (!inBraces) {
                        return query.substring(0, i).trimEnd()
                    }
                }
            }
        }
        return query
    }

    /**
     * Strips scheme and domain/port (e.g. 'https://example.com:8000', 'http://localhost', or 'localhost:8000')
     * from a route or URL string, returning the path component.
     */
    fun stripDomain(query: String): String {
        var s = query.trim()
        if (s.isEmpty()) return ""

        // Preserve HTTP method prefix if present (e.g. "GET https://example.com/users")
        val firstSpace = s.indexOf(' ')
        var methodPrefix = ""
        if (firstSpace > 0) {
            val potentialMethod = s.substring(0, firstSpace).uppercase()
            if (HTTP_METHODS.contains(potentialMethod)) {
                methodPrefix = s.substring(0, firstSpace + 1)
                s = s.substring(firstSpace + 1).trim()
            }
        }

        // Scheme with :// (e.g. http://localhost:8000/users, https://app.test/api)
        val protoIdx = s.indexOf("://")
        if (protoIdx != -1) {
            val afterProto = s.substring(protoIdx + 3)
            val slashIdx = afterProto.indexOf('/')
            val path = if (slashIdx != -1) afterProto.substring(slashIdx) else "/"
            return methodPrefix + path
        }

        // Protocol-relative // (e.g. //example.com/users)
        if (s.startsWith("//")) {
            val afterSlashes = s.substring(2)
            val slashIdx = afterSlashes.indexOf('/')
            val path = if (slashIdx != -1) afterSlashes.substring(slashIdx) else "/"
            return methodPrefix + path
        }

        // Bare domain with port (e.g. localhost:8000/users, 127.0.0.1:8000/users, app.test:8080/users)
        val portMatch = HOST_WITH_PORT_REGEX.matchEntire(s)
        if (portMatch != null) {
            val path = portMatch.groupValues[1]
            return methodPrefix + if (path.isNotEmpty()) path else "/"
        }

        // Bare domain without scheme (e.g. localhost/users, app.test/users, example.com/api/users)
        // Must contain a slash after the host to distinguish from route names like "users.index"
        val hostMatch = BARE_HOST_REGEX.matchEntire(s)
        if (hostMatch != null) {
            val path = hostMatch.groupValues[1]
            if (path.isNotEmpty() && path.startsWith("/")) {
                return methodPrefix + path
            }
        }

        return methodPrefix + s
    }

    /**
     * Cleans a pasted route string by removing any domain/host and any query string,
     * while preserving optional route parameter syntax like '{id?}' or '{name?}'.
     */
    fun cleanPastedRoute(query: String): String {
        return stripDomain(stripQuery(query))
    }

    /**
     * Cleans query paths by stripping scheme, domain/port, query strings, and fragments,
     * while preserving route syntax like '{id?}'.
     */
    fun cleanPath(query: String): String {
        var s = query.trim()
        if (s.isEmpty()) return ""

        s = cleanPastedRoute(s)

        // Strip fragment '#anchor'
        val hashIdx = s.indexOf('#')
        if (hashIdx != -1) {
            s = s.substring(0, hashIdx)
        }

        // Remove trailing slash unless it's just "/"
        if (s.length > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length - 1)
        }

        return s.trim()
    }

    /**
     * Splits a path string into non-empty trimmed segments.
     */
    fun splitSegments(path: String): List<String> {
        return path.split('/').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Parses the raw user query string into a ParsedQuery.
     */
    fun parseQuery(query: String): ParsedQuery {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return ParsedQuery(null, "", null, null, "", emptyList())
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

        val pathCleaned = cleanPastedRoute(pathQuery)
        val cleaned = cleanPath(pathCleaned)
        val segments = splitSegments(cleaned)

        val uriRegex = buildRegex(if (cleaned.isNotEmpty()) cleaned else pathCleaned, exact = false)
        val exactRegex = buildRegex(if (cleaned.isNotEmpty()) cleaned else pathCleaned, exact = true)

        return ParsedQuery(method, pathCleaned, uriRegex, exactRegex, cleaned, segments)
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

    private fun isParamSegment(seg: String): Boolean {
        return seg.contains('{') && seg.contains('}')
    }

    /**
     * Determines whether an individual route segment matches a query segment.
     */
    fun matchesSegment(routeSeg: String, querySeg: String, isLastSegment: Boolean = false): Boolean {
        if (routeSeg.equals(querySeg, ignoreCase = true)) {
            return true
        }

        val routeHasParam = isParamSegment(routeSeg)
        val queryHasParam = isParamSegment(querySeg)

        // Wildcard '*' in query matches any segment
        if (querySeg == "*") {
            return true
        }

        // Query specifies a parameter placeholder like '{}' or '{id}'
        if (queryHasParam) {
            if (!routeHasParam) {
                // Query explicitly asked for a parameter, but route is static
                return false
            }
            if (querySeg == "{}") {
                return true
            }
            val queryParamName = querySeg.trim('{', '}', ' ').trim()
            if (queryParamName.isEmpty()) {
                return true
            }
            val routeParamName = routeSeg.trim('{', '}', ' ').trim()
            return routeParamName.contains(queryParamName, ignoreCase = true)
        }

        // Route specifies a parameter placeholder like '{company}' or '{}', and query provides a concrete value (e.g. '188')
        if (routeHasParam) {
            // Pure parameter placeholder: matches any concrete non-empty segment without braces
            if (routeSeg.startsWith("{") && routeSeg.endsWith("}")) {
                return querySeg.isNotEmpty() && !querySeg.contains('{') && !querySeg.contains('}')
            }
            // Mixed parameter segment like 'prefix-{id}.json': convert to regex
            return try {
                val pattern = "^" + Regex.escape(routeSeg).replace(Regex("""\\\{[^/}]*\\\}"""), """[^/]+""") + "$"
                Regex(pattern, RegexOption.IGNORE_CASE).matches(querySeg)
            } catch (e: Exception) {
                false
            }
        }

        // As-you-type prefix match on the final segment (e.g. 'admin/company/188/acc' matching 'accounts')
        if (isLastSegment && routeSeg.startsWith(querySeg, ignoreCase = true)) {
            return true
        }

        return false
    }

    /**
     * Performs structural segment matching between a route URI and a parsed query.
     * Supports matching concrete paths (e.g. 'admin/company/188/accounts') against routes with parameter placeholders ('admin/company/{}/accounts').
     */
    fun matchSegments(routeUri: String, query: ParsedQuery): Int? {
        val qSegs = query.pathSegments
        if (qSegs.isEmpty()) return null

        val rSegs = splitSegments(routeUri)
        if (rSegs.isEmpty()) return null

        val qLen = qSegs.size
        val rLen = rSegs.size

        // 1. Check match starting at offset 0 (Full match or Prefix match)
        if (qLen <= rLen) {
            var allMatch = true
            var literalCount = 0
            var paramCount = 0

            for (i in 0 until qLen) {
                val rSeg = rSegs[i]
                val qSeg = qSegs[i]
                val isLast = (i == qLen - 1)
                if (matchesSegment(rSeg, qSeg, isLast)) {
                    if (isParamSegment(rSeg) || isParamSegment(qSeg) || qSeg == "*") {
                        paramCount++
                    } else {
                        literalCount++
                    }
                } else {
                    allMatch = false
                    break
                }
            }

            if (allMatch) {
                // If only parameters matched without any literal matches, require multiple segments
                // to prevent a lone number like '188' from matching every route in the project.
                val hasConfidence = literalCount > 0 || (qLen >= 2 && !query.rawPattern.contains('{'))

                if (hasConfidence) {
                    if (qLen == rLen) {
                        // Exact full structural match (e.g. 'admin/company/188/accounts' -> 'admin/company/{}/accounts')
                        return 95
                    }

                    // Check if all remaining route segments are optional parameters
                    val remainingAreOptional = (qLen until rLen).all { idx ->
                        val seg = rSegs[idx]
                        seg.endsWith("?}") || seg.contains("?")
                    }
                    if (remainingAreOptional) {
                        return 92
                    }

                    // Prefix match (e.g. 'admin/company/188' matching 'admin/company/{}/accounts')
                    return 80
                }
            }
        }

        // 2. Subpath match: query is inside the route (offset > 0)
        // e.g. 'company/188/accounts' matching 'admin/company/{}/accounts'
        if (qLen < rLen) {
            val maxOffset = rLen - qLen
            for (offset in 1..maxOffset) {
                var allMatch = true
                var literalCount = 0

                for (i in 0 until qLen) {
                    val rSeg = rSegs[offset + i]
                    val qSeg = qSegs[i]
                    val isLast = (i == qLen - 1)
                    if (matchesSegment(rSeg, qSeg, isLast)) {
                        if (!isParamSegment(rSeg) && !isParamSegment(qSeg) && qSeg != "*") {
                            literalCount++
                        }
                    } else {
                        allMatch = false
                        break
                    }
                }

                if (allMatch && literalCount > 0) {
                    val isSuffix = (offset + qLen == rLen)
                    return if (isSuffix) 75 else 65
                }
            }
        }

        return null
    }

    fun matchSegments(item: RouteItem, query: ParsedQuery): Int? {
        return matchSegments(item.normalizedUri, query)
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

        // 2. Structural segment match (supports concrete parameters e.g. 'admin/company/188/accounts' matching 'admin/company/{}/accounts')
        val segmentScore = matchSegments(item, query)
        if (segmentScore != null) {
            return segmentScore
        }

        // 3. Partial regex match
        if (uriRegex != null) {
            if (uriRegex.containsMatchIn(normalizedUri) || uriRegex.containsMatchIn(rawUri)) {
                // If it starts with the match, rank higher
                val isPrefix = normalizedUri.startsWith(query.rawPattern, ignoreCase = true) ||
                        rawUri.startsWith(query.rawPattern, ignoreCase = true)
                return if (isPrefix) 80 else 60
            }
        }

        // 4. Route Name match (e.g. searching "users.edit")
        val routeName = item.name
        if (routeName != null && routeName.contains(query.rawPattern, ignoreCase = true)) {
            return 50
        }

        // 5. Action / Controller match (e.g. searching "UserController")
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
        val regex = parsed.uriRegex
        val normalized = if (uri.startsWith("/")) uri else "/$uri"
        if (regex != null && (regex.containsMatchIn(normalized) || regex.containsMatchIn(uri))) {
            return true
        }
        return matchSegments(normalized, parsed) != null
    }

    /**
     * Convenience function to test exact matching against a URI.
     */
    fun matchesUriExact(uri: String, query: String): Boolean {
        val parsed = parseQuery(query)
        val regex = parsed.exactUriRegex
        val normalized = if (uri.startsWith("/")) uri else "/$uri"
        if (regex != null && (regex.matches(normalized) || regex.matches(uri))) {
            return true
        }
        val score = matchSegments(normalized, parsed)
        return score != null && score >= 90
    }
}
