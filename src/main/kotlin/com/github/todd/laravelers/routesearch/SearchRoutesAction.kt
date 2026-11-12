package com.github.todd.laravelers.routesearch

import com.github.todd.laravelers.routesearch.ui.LaravelRoutesPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action to activate the Laravel Routes panel and focus the search box.
 * Pre-populates the search if text is selected in the active editor.
 */
class SearchRoutesAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Laravel Routes") ?: return

        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectedText = editor?.selectionModel?.selectedText?.trim()

        toolWindow.activate {
            val content = toolWindow.contentManager.selectedContent ?: return@activate
            val panel = content.component as? LaravelRoutesPanel ?: return@activate
            panel.focusSearch(selectedText)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = (e.project != null)
    }
}
