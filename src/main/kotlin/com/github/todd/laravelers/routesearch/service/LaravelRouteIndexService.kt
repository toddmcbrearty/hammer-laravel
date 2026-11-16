package com.github.todd.laravelers.routesearch.service

import com.github.todd.laravelers.routesearch.matcher.RoutePatternMatcher
import com.github.todd.laravelers.routesearch.model.RouteItem
import com.github.todd.laravelers.routesearch.parser.LaravelRouteParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.concurrent.atomic.AtomicBoolean

enum class SearchScope(val displayName: String) {
    ROUTES_ONLY("Routes Directory (routes/**/*.php)"),
    ALL_PHP("All PHP Files")
}

@Service(Service.Level.PROJECT)
class LaravelRouteIndexService(private val project: Project) {

    private val LOG = Logger.getInstance(LaravelRouteIndexService::class.java)

    private val cachedRoutes = mutableListOf<RouteItem>()
    private val isIndexing = AtomicBoolean(false)
    private var lastScannedTimestamp: Long = 0

    init {
        // Register file listener for automatic route file change detection
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (project.isDisposed) return
                    val affectsRoutes = events.any { event ->
                        val path = event.path
                        (path.endsWith(".php") && (path.contains("/routes/") || path.contains("\\routes\\")))
                    }
                    if (affectsRoutes) {
                        ApplicationManager.getApplication().executeOnPooledThread {
                            refreshIndex()
                            updateToolWindowAvailability()
                        }
                    }
                }
            }
        )
    }

    /**
     * Quickly checks whether the project contains any Laravel route definitions.
     * Checks cached routes first; if not scanned yet, performs a fast check of route files.
     */
    fun hasLaravelRoutes(): Boolean {
        if (project.isDisposed) return false
        synchronized(cachedRoutes) {
            if (cachedRoutes.isNotEmpty()) return true
            if (lastScannedTimestamp != 0L) return false
        }

        val projectDir = project.guessProjectDir() ?: return false
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val routeFiles = findRouteFiles(projectDir, SearchScope.ROUTES_ONLY)
            for (file in routeFiles) {
                if (!file.isValid) continue
                try {
                    val document = FileDocumentManager.getInstance().getDocument(file)
                    val content = document?.text ?: VfsUtil.loadText(file)
                    val parsed = LaravelRouteParser.parse(content, file, file.name)
                    if (parsed.isNotEmpty()) return@runReadAction true
                } catch (e: Exception) {
                    // ignore
                }
            }
            false
        }
    }

    /**
     * Updates the ToolWindow availability in PhpStorm.
     */
    fun updateToolWindowAvailability() {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Laravel Routes") ?: return@invokeLater
            val available = hasLaravelRoutes()
            if (toolWindow.isAvailable != available) {
                toolWindow.isAvailable = available
            }
        }
    }

    /**
     * Re-indexes the project's routes in a thread-safe, read-action-protected manner.
     */
    fun refreshIndex(scope: SearchScope = SearchScope.ROUTES_ONLY): List<RouteItem> {
        if (project.isDisposed) return emptyList()
        val projectDir = project.guessProjectDir() ?: return emptyList()

        if (!isIndexing.compareAndSet(false, true)) {
            // Already indexing, return cached
            return synchronized(cachedRoutes) { cachedRoutes.toList() }
        }

        try {
            val scannedRoutes = ApplicationManager.getApplication().runReadAction<List<RouteItem>> {
                if (project.isDisposed) return@runReadAction emptyList()
                val routeFiles = findRouteFiles(projectDir, scope)
                val list = mutableListOf<RouteItem>()

                for (file in routeFiles) {
                    if (!file.isValid) continue
                    try {
                        val document = FileDocumentManager.getInstance().getDocument(file)
                        val content = document?.text ?: VfsUtil.loadText(file)
                        val relativePath = VfsUtil.getRelativePath(file, projectDir) ?: file.name
                        val fileRoutes = LaravelRouteParser.parse(content, file, relativePath)
                        list.addAll(fileRoutes)
                    } catch (e: Exception) {
                        LOG.warn("Failed to parse route file: ${file.path}", e)
                    }
                }
                list
            }

            synchronized(cachedRoutes) {
                cachedRoutes.clear()
                cachedRoutes.addAll(scannedRoutes)
                lastScannedTimestamp = System.currentTimeMillis()
            }
            return scannedRoutes
        } catch (e: Exception) {
            LOG.error("Error indexing Laravel routes", e)
            return synchronized(cachedRoutes) { cachedRoutes.toList() }
        } finally {
            isIndexing.set(false)
        }
    }

    /**
     * Returns all currently indexed routes, refreshing if never scanned.
     */
    fun getAllRoutes(scope: SearchScope = SearchScope.ROUTES_ONLY): List<RouteItem> {
        synchronized(cachedRoutes) {
            if (cachedRoutes.isNotEmpty() || lastScannedTimestamp != 0L) {
                return cachedRoutes.toList()
            }
        }
        return refreshIndex(scope)
    }

    /**
     * Searches routes matching a query and optional filters.
     * Ranked by match quality (Exact > Prefix > Substring > Name > Action).
     */
    fun search(
        query: String,
        methodFilter: String? = null,
        scope: SearchScope = SearchScope.ROUTES_ONLY
    ): List<RouteItem> {
        if (project.isDisposed) return emptyList()
        val routes = getAllRoutes(scope)
        val trimmed = query.trim()

        if (trimmed.isEmpty() && (methodFilter == null || methodFilter.equals("ALL", ignoreCase = true))) {
            return routes
        }

        val parsed = RoutePatternMatcher.parseQuery(trimmed)
        val effectiveMethodFilter = when {
            methodFilter != null && !methodFilter.equals("ALL", ignoreCase = true) -> methodFilter
            parsed.methodFilter != null -> parsed.methodFilter
            else -> null
        }
        val queryWithEffectiveMethod = parsed.copy(methodFilter = effectiveMethodFilter)

        val scoredResults = mutableListOf<Pair<RouteItem, Int>>()
        for (item in routes) {
            try {
                val score = RoutePatternMatcher.match(item, queryWithEffectiveMethod)
                if (score != null) {
                    scoredResults.add(item to score)
                }
            } catch (e: Exception) {
                // Ignore any matching error on a malformed item
            }
        }

        // Sort descending by relevance score, then alphabetically by URI
        scoredResults.sortWith(
            compareByDescending<Pair<RouteItem, Int>> { it.second }
                .thenBy { it.first.normalizedUri }
        )

        return scoredResults.map { it.first }
    }

    private fun findRouteFiles(projectDir: VirtualFile, scope: SearchScope): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()

        if (scope == SearchScope.ROUTES_ONLY) {
            // 1. Direct projectDir/routes folder
            val routesDir = projectDir.findChild("routes")
            if (routesDir != null && routesDir.isDirectory) {
                collectPhpFiles(routesDir, files)
            }

            // 2. Search for any nested routes folders (e.g. app/Modules/*/routes or src/routes)
            projectDir.children?.forEach { child ->
                if (child.isDirectory && child.name != "vendor" && child.name != "node_modules" && child.name != ".git" && child.name != "storage") {
                    findSubRoutesFolders(child, files, depth = 1)
                }
            }
        } else {
            // ALL_PHP scope: scan project PHP files excluding vendor/node_modules/build
            collectAllPhpFiles(projectDir, files, depth = 0)
        }

        return files.distinctBy { it.path }
    }

    private fun findSubRoutesFolders(dir: VirtualFile, collector: MutableList<VirtualFile>, depth: Int) {
        if (depth > 4) return
        if (dir.name == "vendor" || dir.name == "node_modules" || dir.name.startsWith(".")) return

        if (dir.name.equals("routes", ignoreCase = true)) {
            collectPhpFiles(dir, collector)
            return
        }

        dir.children?.forEach { child ->
            if (child.isDirectory) {
                findSubRoutesFolders(child, collector, depth + 1)
            }
        }
    }

    private fun collectPhpFiles(dir: VirtualFile, collector: MutableList<VirtualFile>) {
        dir.children?.forEach { child ->
            if (child.isDirectory) {
                collectPhpFiles(child, collector)
            } else if (child.extension.equals("php", ignoreCase = true)) {
                collector.add(child)
            }
        }
    }

    private fun collectAllPhpFiles(dir: VirtualFile, collector: MutableList<VirtualFile>, depth: Int) {
        if (depth > 6) return
        val name = dir.name
        if (name == "vendor" || name == "node_modules" || name == "storage" || name == "build" || name.startsWith(".")) {
            return
        }

        dir.children?.forEach { child ->
            if (child.isDirectory) {
                collectAllPhpFiles(child, collector, depth + 1)
            } else if (child.extension.equals("php", ignoreCase = true)) {
                collector.add(child)
            }
        }
    }

    companion object {
        fun getInstance(project: Project): LaravelRouteIndexService {
            return project.getService(LaravelRouteIndexService::class.java)
        }
    }
}
