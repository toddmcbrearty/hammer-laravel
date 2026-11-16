package com.github.todd.laravelers.routesearch

import com.github.todd.laravelers.routesearch.service.LaravelRouteIndexService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

/**
 * Menu group for Hammer Laravel in the Tools menu.
 * Only shown if Laravel routes exist in the project.
 */
class HammerLaravelActionGroup : DefaultActionGroup(), DumbAware {

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.isDisposed) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val hasRoutes = LaravelRouteIndexService.getInstance(project).hasLaravelRoutes()
        e.presentation.isEnabledAndVisible = hasRoutes
    }
}
