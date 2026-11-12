package com.github.todd.laravelers.routesearch.service

import com.github.todd.laravelers.routesearch.model.RouteItem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

object RouteNavigationService {

    /**
     * Navigates to the controller function/method (or closure) for the given route item.
     * Returns true if successfully navigated, false otherwise.
     */
    fun jumpToFunction(project: Project, item: RouteItem): Boolean {
        if (project.isDisposed) return false

        // 1. If it's a closure, navigate to the route declaration line where the closure is defined
        if (item.isClosure) {
            return jumpToRouteDefinition(project, item)
        }

        val controllerName = item.controllerClass
        val methodName = item.controllerMethod

        if (controllerName.isNullOrBlank()) {
            return jumpToRouteDefinition(project, item)
        }

        // 2. Locate the controller file in project
        val controllerFile = findControllerFile(project, controllerName)
        if (controllerFile == null || !controllerFile.isValid) {
            Messages.showInfoMessage(
                project,
                "Could not find controller file for '$controllerName' in the project.",
                "Controller Not Found"
            )
            return false
        }

        // 3. Locate the method line inside the controller file
        val targetLine = findMethodLine(project, controllerFile, methodName ?: "__invoke")

        val descriptor = OpenFileDescriptor(
            project,
            controllerFile,
            targetLine.coerceAtLeast(0),
            0
        )

        if (descriptor.canNavigate()) {
            descriptor.navigate(true)
            return true
        }

        return false
    }

    /**
     * Navigates to the route definition line in the route file.
     */
    fun jumpToRouteDefinition(project: Project, item: RouteItem): Boolean {
        val file = item.file
        if (!file.isValid) return false

        val descriptor = OpenFileDescriptor(
            project,
            file,
            (item.lineNumber - 1).coerceAtLeast(0),
            0
        )
        if (descriptor.canNavigate()) {
            descriptor.navigate(true)
            return true
        }
        return false
    }

    /**
     * Searches for the controller file in the project.
     */
    fun findControllerFile(project: Project, controllerName: String): VirtualFile? {
        val fileName = if (controllerName.endsWith(".php")) controllerName else "$controllerName.php"

        return ApplicationManager.getApplication().runReadAction<VirtualFile?> {
            try {
                // 1. Try FilenameIndex in project scope
                val matchingFiles = FilenameIndex.getVirtualFilesByName(
                    fileName,
                    GlobalSearchScope.projectScope(project)
                )

                // Prioritize files in app/Http/Controllers or Controllers
                val preferred = matchingFiles.firstOrNull { file ->
                    val path = file.path
                    path.contains("/Controllers/") || path.contains("\\Controllers\\") ||
                            path.contains("/Http/") || path.contains("\\Http\\")
                }
                if (preferred != null) return@runReadAction preferred

                // If any non-vendor match exists, use it
                val nonVendor = matchingFiles.firstOrNull { file ->
                    !file.path.contains("/vendor/") && !file.path.contains("/node_modules/")
                }
                if (nonVendor != null) return@runReadAction nonVendor

                if (matchingFiles.isNotEmpty()) return@runReadAction matchingFiles.first()
            } catch (e: Exception) {
                // Fallback to manual VFS lookup below
            }

            // 2. Direct lookup in common Laravel directories
            val projectDir = project.guessProjectDir() ?: return@runReadAction null

            val appControllers = projectDir.findFileByRelativePath("app/Http/Controllers/$fileName")
            if (appControllers != null && appControllers.isValid) return@runReadAction appControllers

            val appDir = projectDir.findChild("app")
            if (appDir != null && appDir.isDirectory) {
                val found = findFileRecursive(appDir, fileName, depth = 0)
                if (found != null) return@runReadAction found
            }

            null
        }
    }

    /**
     * Scans controller file content to find the line number of the method definition.
     */
    fun findMethodLine(project: Project, file: VirtualFile, methodName: String): Int {
        return ApplicationManager.getApplication().runReadAction<Int> {
            try {
                val document = FileDocumentManager.getInstance().getDocument(file)
                val content = document?.text ?: VfsUtil.loadText(file)
                val lines = content.lines()

                // Regex matches: public function methodName(, function methodName(, etc.
                val regex = Regex(
                    """(?i)\bfunction\s+${Regex.escape(methodName)}\s*\(""",
                    RegexOption.IGNORE_CASE
                )

                for (index in lines.indices) {
                    if (regex.containsMatchIn(lines[index])) {
                        return@runReadAction index
                    }
                }

                // If specific method not found, try finding class declaration
                val classRegex = Regex("""(?i)\bclass\s+[a-zA-Z0-9_]+""", RegexOption.IGNORE_CASE)
                for (index in lines.indices) {
                    if (classRegex.containsMatchIn(lines[index])) {
                        return@runReadAction index
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            0
        }
    }

    private fun findFileRecursive(dir: VirtualFile, targetName: String, depth: Int): VirtualFile? {
        if (depth > 6) return null
        val name = dir.name
        if (name == "vendor" || name == "node_modules" || name == "storage" || name.startsWith(".")) return null

        val match = dir.findChild(targetName)
        if (match != null && !match.isDirectory) return match

        dir.children?.forEach { child ->
            if (child.isDirectory) {
                val found = findFileRecursive(child, targetName, depth + 1)
                if (found != null) return found
            }
        }
        return null
    }
}
