package com.github.todd.laravelers.routesearch.ui

import com.github.todd.laravelers.routesearch.model.RouteItem
import com.github.todd.laravelers.routesearch.service.LaravelRouteIndexService
import com.github.todd.laravelers.routesearch.service.RouteNavigationService
import com.github.todd.laravelers.routesearch.service.SearchScope
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.Timer

class LaravelRoutesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val indexService = LaravelRouteIndexService.getInstance(project)

    private val searchField = SearchTextField(true)
    private val methodCombo = ComboBox(arrayOf("All Methods", "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "ANY"))
    private val scopeCombo = ComboBox(arrayOf("Routes Folder", "All PHP Files"))
    private val refreshButton = JButton(AllIcons.Actions.Refresh)
    private val countLabel = JBLabel("0 routes")
    private val cellRenderer = RouteCellRenderer()
    private val routesList = object : JBList<RouteItem>() {
        override fun getScrollableTracksViewportWidth(): Boolean = true
    }

    private val debounceTimer: Timer
    private var currentSearchGeneration = 0

    init {
        // Setup Debounce Timer (150ms)
        debounceTimer = Timer(150) {
            performSearch()
        }.apply { isRepeats = false }

        setupHeader()
        setupList()

        // Assemble Layout
        val topContainer = JPanel(BorderLayout())
        topContainer.add(createSearchToolbar(), BorderLayout.NORTH)
        topContainer.add(createFiltersToolbar(), BorderLayout.SOUTH)

        add(topContainer, BorderLayout.NORTH)
        val scrollPane = JBScrollPane(routesList).apply {
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
        add(scrollPane, BorderLayout.CENTER)

        // Initial scan in background
        ApplicationManager.getApplication().executeOnPooledThread {
            indexService.refreshIndex(getEffectiveScope())
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    performSearch()
                }
            }
        }
    }

    private fun createSearchToolbar(): JPanel {
        val panel = JPanel(BorderLayout(JBUI.scale(6), 0))
        panel.border = JBUI.Borders.empty(8, 8, 4, 8)

        searchField.textEditor.emptyText.text = "Search routes (e.g. admin/company/188/accounts, /go/to/{}/edit)..."

        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                debounceTimer.restart()
            }
        })

        // Up/Down in search box navigates list
        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val model = routesList.model
                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        if (model.size > 0) {
                            val nextIndex = (routesList.selectedIndex + 1).coerceAtMost(model.size - 1)
                            routesList.selectedIndex = nextIndex
                            routesList.ensureIndexIsVisible(nextIndex)
                            e.consume()
                        }
                    }
                    KeyEvent.VK_UP -> {
                        if (model.size > 0) {
                            val prevIndex = (routesList.selectedIndex - 1).coerceAtLeast(0)
                            routesList.selectedIndex = prevIndex
                            routesList.ensureIndexIsVisible(prevIndex)
                            e.consume()
                        }
                    }
                    KeyEvent.VK_ENTER -> {
                        if (e.isShiftDown) {
                            openSelectedFunction()
                        } else {
                            openSelectedRoute()
                        }
                        e.consume()
                    }
                }
            }
        })

        panel.add(searchField, BorderLayout.CENTER)
        return panel
    }

    private fun createFiltersToolbar(): JPanel {
        val panel = JPanel(BorderLayout(JBUI.scale(6), 0))
        panel.border = JBUI.Borders.empty(0, 8, 6, 8)

        val leftFilters = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
        leftFilters.isOpaque = false
        leftFilters.add(methodCombo)
        leftFilters.add(scopeCombo)

        refreshButton.toolTipText = "Re-scan route files"
        refreshButton.isFocusable = false
        refreshButton.preferredSize = java.awt.Dimension(JBUI.scale(28), JBUI.scale(24))
        leftFilters.add(refreshButton)

        val rightFilters = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        rightFilters.isOpaque = false
        countLabel.font = countLabel.font.deriveFont(Font.PLAIN, JBUI.scale(11).toFloat())
        countLabel.foreground = JBColor.GRAY
        rightFilters.add(countLabel)

        panel.add(leftFilters, BorderLayout.WEST)
        panel.add(rightFilters, BorderLayout.EAST)

        methodCombo.addActionListener { performSearch() }
        scopeCombo.addActionListener {
            ApplicationManager.getApplication().executeOnPooledThread {
                indexService.refreshIndex(getEffectiveScope())
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) performSearch()
                }
            }
        }
        refreshButton.addActionListener {
            refreshButton.isEnabled = false
            ApplicationManager.getApplication().executeOnPooledThread {
                indexService.refreshIndex(getEffectiveScope())
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        performSearch()
                        refreshButton.isEnabled = true
                    }
                }
            }
        }

        return panel
    }

    private fun setupHeader() {
        methodCombo.isFocusable = false
        scopeCombo.isFocusable = false
    }

    private fun setupList() {
        routesList.cellRenderer = cellRenderer
        routesList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        routesList.emptyText.text = "No routes found matching query. Try typing /go/to/{}/edit or pasting a URL/path"

        routesList.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val index = routesList.locationToIndex(e.point)
                    if (index >= 0 && index < routesList.model.size) {
                        val bounds = routesList.getCellBounds(index, index)
                        if (bounds != null && bounds.contains(e.point)) {
                            val relX = e.x - bounds.x
                            val relY = e.y - bounds.y
                            val item = routesList.model.getElementAt(index)
                            val clicked = cellRenderer.getClickedButton(
                                routesList,
                                bounds.width,
                                bounds.height,
                                relX,
                                relY,
                                item,
                                routesList.selectedIndex == index
                            )
                            when (clicked) {
                                CellActionButton.SOURCE -> {
                                    routesList.selectedIndex = index
                                    RouteNavigationService.jumpToRouteDefinition(project, item)
                                    e.consume()
                                    return
                                }
                                CellActionButton.FUNCTION -> {
                                    routesList.selectedIndex = index
                                    RouteNavigationService.jumpToFunction(project, item)
                                    e.consume()
                                    return
                                }
                                CellActionButton.NONE -> {}
                            }
                        }
                    }
                }
            }

            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val index = routesList.locationToIndex(e.point)
                    if (index != -1) {
                        routesList.selectedIndex = index
                        showContextMenu(e)
                    }
                } else if (e.clickCount == 2) {
                    if (e.isMetaDown || e.isControlDown) {
                        openSelectedFunction()
                    } else {
                        openSelectedRoute()
                    }
                }
            }

            override fun mouseExited(e: MouseEvent) {
                clearButtonHover()
            }
        })

        routesList.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = routesList.locationToIndex(e.point)
                if (index >= 0 && index < routesList.model.size) {
                    val bounds = routesList.getCellBounds(index, index)
                    if (bounds != null && bounds.contains(e.point)) {
                        val relX = e.x - bounds.x
                        val relY = e.y - bounds.y
                        val item = routesList.model.getElementAt(index)
                        val clicked = cellRenderer.getClickedButton(
                            routesList,
                            bounds.width,
                            bounds.height,
                            relX,
                            relY,
                            item,
                            routesList.selectedIndex == index
                        )
                        if (clicked != CellActionButton.NONE) {
                            routesList.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                            if (cellRenderer.hoveredIndex != index || cellRenderer.hoveredButton != clicked) {
                                val oldIndex = cellRenderer.hoveredIndex
                                cellRenderer.hoveredIndex = index
                                cellRenderer.hoveredButton = clicked
                                if (oldIndex >= 0 && oldIndex < routesList.model.size) {
                                    routesList.getCellBounds(oldIndex, oldIndex)?.let { routesList.repaint(it) }
                                }
                                routesList.repaint(bounds)
                            }
                            routesList.toolTipText = when (clicked) {
                                CellActionButton.SOURCE -> "Jump to route definition in ${item.filePath}:${item.lineNumber}"
                                CellActionButton.FUNCTION -> if (item.isClosure) "Jump to closure definition in ${item.filePath}" else "Jump to ${item.controllerClass}::${item.controllerMethod}()"
                                CellActionButton.NONE -> null
                            }
                            return
                        } else {
                            clearButtonHover()
                            routesList.toolTipText = "${item.method} ${item.normalizedUri}" + if (!item.name.isNullOrBlank()) " (${item.name})" else ""
                            return
                        }
                    }
                }
                clearButtonHover()
            }
        })

        routesList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == KeyEvent.VK_ENTER && e.isShiftDown -> {
                        openSelectedFunction()
                        e.consume()
                    }
                    e.keyCode == KeyEvent.VK_ENTER -> {
                        openSelectedRoute()
                        e.consume()
                    }
                    (e.keyCode == KeyEvent.VK_B && (e.isMetaDown || e.isControlDown)) -> {
                        openSelectedFunction()
                        e.consume()
                    }
                }
            }
        })
    }

    private fun clearButtonHover() {
        if (cellRenderer.hoveredButton != CellActionButton.NONE) {
            val oldIndex = cellRenderer.hoveredIndex
            cellRenderer.hoveredIndex = -1
            cellRenderer.hoveredButton = CellActionButton.NONE
            if (oldIndex >= 0 && oldIndex < routesList.model.size) {
                routesList.getCellBounds(oldIndex, oldIndex)?.let { routesList.repaint(it) }
            }
            routesList.cursor = java.awt.Cursor.getDefaultCursor()
            routesList.toolTipText = null
        }
    }

    private fun showContextMenu(e: MouseEvent) {
        val selected = routesList.selectedValue ?: return
        val menu = JPopupMenu()

        val jumpFunctionItem = JMenuItem("Jump to Function", AllIcons.Nodes.Method).apply {
            isEnabled = !selected.controllerClass.isNullOrBlank() || selected.isClosure
            addActionListener { openSelectedFunction() }
        }
        menu.add(jumpFunctionItem)

        val jumpSourceItem = JMenuItem("Jump to Route Source", AllIcons.Actions.EditSource).apply {
            addActionListener { openSelectedRoute() }
        }
        menu.add(jumpSourceItem)

        menu.addSeparator()

        val copyUriItem = JMenuItem("Copy URI", AllIcons.Actions.Copy).apply {
            addActionListener {
                CopyPasteManager.getInstance().setContents(StringSelection(selected.normalizedUri))
            }
        }
        menu.add(copyUriItem)

        if (!selected.name.isNullOrBlank()) {
            val copyNameItem = JMenuItem("Copy Route Name").apply {
                addActionListener {
                    CopyPasteManager.getInstance().setContents(StringSelection(selected.name))
                }
            }
            menu.add(copyNameItem)
        }

        if (!selected.action.isNullOrBlank()) {
            val copyActionItem = JMenuItem("Copy Action").apply {
                addActionListener {
                    CopyPasteManager.getInstance().setContents(StringSelection(selected.action))
                }
            }
            menu.add(copyActionItem)
        }

        menu.show(routesList, e.x, e.y)
    }

    private fun getEffectiveScope(): SearchScope {
        return if (scopeCombo.selectedIndex == 1) SearchScope.ALL_PHP else SearchScope.ROUTES_ONLY
    }

    fun performSearch() {
        val query = searchField.text
        val selectedMethod = methodCombo.selectedItem as? String
        val methodFilter = if (selectedMethod == "All Methods") null else selectedMethod
        val scope = getEffectiveScope()

        val searchGen = ++currentSearchGeneration

        // Perform search in background thread to avoid freezing EDT
        ApplicationManager.getApplication().executeOnPooledThread {
            val results = try {
                indexService.search(query, methodFilter, scope)
            } catch (e: Exception) {
                emptyList()
            }

            // Deliver results back to EDT
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || searchGen != currentSearchGeneration) {
                    return@invokeLater
                }

                // Single atomic model replacement (zero layout spam)
                val newModel = DefaultListModel<RouteItem>()
                newModel.addAll(results)
                routesList.model = newModel

                countLabel.text = "${results.size} route${if (results.size != 1) "s" else ""}"

                if (results.isNotEmpty()) {
                    routesList.selectedIndex = 0
                    routesList.ensureIndexIsVisible(0)
                }
            }
        }
    }

    private fun openSelectedRoute() {
        val selected = routesList.selectedValue ?: return
        RouteNavigationService.jumpToRouteDefinition(project, selected)
    }

    private fun openSelectedFunction() {
        val selected = routesList.selectedValue ?: return
        RouteNavigationService.jumpToFunction(project, selected)
    }

    /**
     * Pre-populates the search query and focuses the field.
     */
    fun focusSearch(initialQuery: String? = null) {
        if (!initialQuery.isNullOrBlank()) {
            searchField.text = initialQuery
        }
        searchField.requestFocusInWindow()
    }
}
