package com.github.todd.laravelers.routesearch.ui

import com.github.todd.laravelers.routesearch.model.RouteItem
import com.github.todd.laravelers.routesearch.service.LaravelRouteIndexService
import com.github.todd.laravelers.routesearch.service.SearchScope
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
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
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.Timer

class LaravelRoutesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val indexService = LaravelRouteIndexService.getInstance(project)

    private val searchField = SearchTextField(true)
    private val methodCombo = ComboBox(arrayOf("All Methods", "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "ANY"))
    private val scopeCombo = ComboBox(arrayOf("Routes Folder", "All PHP Files"))
    private val refreshButton = JButton(AllIcons.Actions.Refresh)
    private val countLabel = JBLabel("0 routes")

    private val routesList = JBList<RouteItem>()

    // Preview / Details panel
    private val detailPanel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(4)))
    private val detailTitle = JBLabel("Select a route to view details")
    private val detailSnippet = JBLabel()
    private val jumpButton = JButton("Jump to Source", AllIcons.Actions.EditSource)
    private val copyButton = JButton("Copy URI", AllIcons.Actions.Copy)

    private val debounceTimer: Timer
    private var currentSearchGeneration = 0

    init {
        // Setup Debounce Timer (150ms)
        debounceTimer = Timer(150) {
            performSearch()
        }.apply { isRepeats = false }

        setupHeader()
        setupList()
        setupDetailPanel()

        // Assemble Layout
        val topContainer = JPanel(BorderLayout())
        topContainer.add(createSearchToolbar(), BorderLayout.NORTH)
        topContainer.add(createFiltersToolbar(), BorderLayout.SOUTH)

        add(topContainer, BorderLayout.NORTH)
        add(JBScrollPane(routesList), BorderLayout.CENTER)
        add(detailPanel, BorderLayout.SOUTH)

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

        searchField.textEditor.emptyText.text = "Search routes (e.g. /go/to/{}/edit, {id}, users)..."

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
                        openSelectedRoute()
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
        routesList.cellRenderer = RouteCellRenderer()
        routesList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        routesList.emptyText.text = "No routes found matching query. Try typing /go/to/{}/edit"

        routesList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedRoute()
                }
            }
        })

        routesList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    openSelectedRoute()
                    e.consume()
                }
            }
        })

        routesList.addListSelectionListener {
            val selected = routesList.selectedValue
            updateDetailPanel(selected)
        }
    }

    private fun setupDetailPanel() {
        detailPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            JBUI.Borders.empty(8, 10)
        )
        detailPanel.background = UIUtil.getPanelBackground()

        val textContainer = JPanel()
        textContainer.layout = BoxLayout(textContainer, BoxLayout.Y_AXIS)
        textContainer.isOpaque = false

        detailTitle.font = detailTitle.font.deriveFont(Font.BOLD, JBUI.scale(12).toFloat())
        detailSnippet.font = detailSnippet.font.deriveFont(Font.PLAIN, JBUI.scale(11).toFloat())
        detailSnippet.foreground = JBColor.GRAY

        textContainer.add(detailTitle)
        textContainer.add(Box.createVerticalStrut(JBUI.scale(3)))
        textContainer.add(detailSnippet)

        val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0))
        buttonsPanel.isOpaque = false

        jumpButton.isFocusable = false
        jumpButton.addActionListener { openSelectedRoute() }

        copyButton.isFocusable = false
        copyButton.addActionListener {
            val selected = routesList.selectedValue
            if (selected != null) {
                CopyPasteManager.getInstance().setContents(StringSelection(selected.normalizedUri))
            }
        }

        buttonsPanel.add(copyButton)
        buttonsPanel.add(jumpButton)

        detailPanel.add(textContainer, BorderLayout.CENTER)
        detailPanel.add(buttonsPanel, BorderLayout.EAST)
        detailPanel.isVisible = false
    }

    private fun updateDetailPanel(item: RouteItem?) {
        if (item == null) {
            detailPanel.isVisible = false
            return
        }
        detailPanel.isVisible = true
        detailTitle.text = "${item.method} ${item.normalizedUri}" + if (!item.name.isNullOrBlank()) " (${item.name})" else ""
        // Plain text to avoid heavy HTML rendering
        detailSnippet.text = "${item.filePath}:${item.lineNumber}  |  ${item.lineContent.trim()}"
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
                } else {
                    updateDetailPanel(null)
                }
            }
        }
    }

    private fun openSelectedRoute() {
        val selected = routesList.selectedValue ?: return
        openRouteInEditor(selected)
    }

    private fun openRouteInEditor(item: RouteItem) {
        val file = item.file
        if (!file.isValid) return

        val descriptor = OpenFileDescriptor(
            project,
            file,
            (item.lineNumber - 1).coerceAtLeast(0),
            0
        )
        if (descriptor.canNavigate()) {
            descriptor.navigate(true)
        }
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
