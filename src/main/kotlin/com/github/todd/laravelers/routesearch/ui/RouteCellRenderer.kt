package com.github.todd.laravelers.routesearch.ui

import com.github.todd.laravelers.routesearch.model.RouteItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities

enum class CellActionButton { NONE, SOURCE, FUNCTION }

/**
 * High-performance cell renderer for Laravel routes.
 * Features inline 'Jump to Source' and 'Jump to Function' buttons.
 */
class RouteCellRenderer : ListCellRenderer<RouteItem> {

    companion object {
        val ROUTE_ICON = IconLoader.getIcon("/icons/route.svg", RouteCellRenderer::class.java.classLoader)
    }

    private val panel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(4)))
    private val topRow = JPanel(BorderLayout(JBUI.scale(8), 0))
    private val leftTop = JPanel(BorderLayout(JBUI.scale(6), 0))
    private val rightTopPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0))
    private val bottomRow = JPanel(BorderLayout(JBUI.scale(8), 0))

    private val methodBadge = MethodBadgeLabel()
    private val uriComponent = SimpleColoredComponent()
    private val actionLabel = JLabel()
    private val fileLabel = JLabel()

    val sourceButton = ItemActionButton(ROUTE_ICON)
    val functionButton = ItemActionButton(AllIcons.Nodes.Method)

    var hoveredIndex: Int = -1
    var hoveredButton: CellActionButton = CellActionButton.NONE

    private val paramPattern = Regex("""\{[^/}]+\}""")

    private val content = JPanel()

    init {
        panel.isOpaque = true
        topRow.isOpaque = false
        leftTop.isOpaque = false
        rightTopPanel.isOpaque = false
        bottomRow.isOpaque = false
        uriComponent.isOpaque = false

        leftTop.add(methodBadge, BorderLayout.WEST)
        leftTop.add(uriComponent, BorderLayout.CENTER)

        rightTopPanel.add(sourceButton)
        rightTopPanel.add(functionButton)

        topRow.add(leftTop, BorderLayout.CENTER)
        topRow.add(rightTopPanel, BorderLayout.EAST)

        bottomRow.add(actionLabel, BorderLayout.CENTER)
        bottomRow.add(fileLabel, BorderLayout.EAST)

        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.isOpaque = false
        content.add(topRow)
        content.add(Box.createVerticalStrut(JBUI.scale(3)))
        content.add(bottomRow)

        panel.add(content, BorderLayout.CENTER)
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor(Color(230, 230, 230), Color(55, 55, 55))),
            JBUI.Borders.empty(6, 10)
        )
    }

    override fun getListCellRendererComponent(
        list: JList<out RouteItem>,
        value: RouteItem?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        if (value == null) return panel

        if (isSelected) {
            panel.background = UIUtil.getListSelectionBackground(true)
            actionLabel.foreground = UIUtil.getListSelectionForeground(true)
            fileLabel.foreground = UIUtil.getListSelectionForeground(true)
            sourceButton.foreground = Color.WHITE
            functionButton.foreground = Color.WHITE
        } else {
            panel.background = if (index % 2 == 0) UIUtil.getListBackground() else UIUtil.getDecoratedRowColor()
            actionLabel.foreground = JBColor(Color(70, 70, 70), Color(180, 180, 180))
            fileLabel.foreground = JBColor(Color(130, 130, 130), Color(140, 140, 140))
            sourceButton.foreground = JBColor(Color(35, 35, 35), Color(215, 215, 215))
            functionButton.foreground = JBColor(Color(35, 35, 35), Color(215, 215, 215))
        }

        sourceButton.isSelectedRow = isSelected
        functionButton.isSelectedRow = isSelected
        sourceButton.isHovered = (index == hoveredIndex && hoveredButton == CellActionButton.SOURCE)
        functionButton.isHovered = (index == hoveredIndex && hoveredButton == CellActionButton.FUNCTION)

        // Method Badge
        methodBadge.setMethod(value.method)

        // Native URI rendering with variable parameter highlighting and optional route name
        uriComponent.clear()
        renderUri(value.normalizedUri, isSelected, value.name)

        sourceButton.icon = ROUTE_ICON
        sourceButton.isVisible = true

        // Jump to Function / Closure button setup (icon only)
        val hasTargetFunction = !value.controllerClass.isNullOrBlank() || value.isClosure
        if (hasTargetFunction) {
            functionButton.icon = if (value.isClosure) AllIcons.Nodes.Function else AllIcons.Nodes.Method
            functionButton.isVisible = true
        } else {
            functionButton.isVisible = false
        }

        // Action / Controller
        actionLabel.text = value.action ?: "Closure"
        actionLabel.font = actionLabel.font.deriveFont(Font.PLAIN, JBUI.scale(11).toFloat())

        // File and line
        fileLabel.text = "${value.filePath}:${value.lineNumber}"
        fileLabel.font = fileLabel.font.deriveFont(Font.PLAIN, JBUI.scale(11).toFloat())

        return panel
    }

    /**
     * Determines whether a point (relX, relY) inside a cell clicked an inline action button.
     */
    fun getClickedButton(
        list: JList<out RouteItem>,
        cellWidth: Int,
        cellHeight: Int,
        relX: Int,
        relY: Int,
        item: RouteItem,
        isSelected: Boolean
    ): CellActionButton {
        panel.setBounds(0, 0, cellWidth, cellHeight)
        getListCellRendererComponent(list, item, 0, isSelected, false)
        panel.doLayout()
        content.doLayout()
        topRow.doLayout()
        rightTopPanel.doLayout()

        val pt = Point(relX, relY)

        if (sourceButton.isVisible && sourceButton.parent != null) {
            val srcBounds = SwingUtilities.convertRectangle(sourceButton.parent, sourceButton.bounds, panel)
            if (srcBounds.contains(pt)) {
                return CellActionButton.SOURCE
            }
        }

        if (functionButton.isVisible && functionButton.parent != null) {
            val fnBounds = SwingUtilities.convertRectangle(functionButton.parent, functionButton.bounds, panel)
            if (fnBounds.contains(pt)) {
                return CellActionButton.FUNCTION
            }
        }

        return CellActionButton.NONE
    }

    private fun renderUri(uri: String, isSelected: Boolean, routeName: String? = null) {
        val baseAttr = if (isSelected) {
            SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getListSelectionForeground(true))
        } else {
            SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getListForeground())
        }

        val paramAttr = if (isSelected) {
            SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color(255, 238, 173))
        } else {
            SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor(Color(230, 81, 0), Color(255, 167, 38)))
        }

        var lastIdx = 0
        for (match in paramPattern.findAll(uri)) {
            if (match.range.first > lastIdx) {
                uriComponent.append(uri.substring(lastIdx, match.range.first), baseAttr)
            }
            uriComponent.append(match.value, paramAttr)
            lastIdx = match.range.last + 1
        }
        if (lastIdx < uri.length) {
            uriComponent.append(uri.substring(lastIdx), baseAttr)
        }

        if (!routeName.isNullOrBlank()) {
            val nameAttr = if (isSelected) {
                SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, Color(220, 225, 235))
            } else {
                SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, JBColor(Color(110, 110, 110), Color(150, 150, 150)))
            }
            uriComponent.append("  ($routeName)", nameAttr)
        }
    }

    class ItemActionButton(
        icon: javax.swing.Icon?
    ) : JLabel(icon, CENTER) {
        var isHovered: Boolean = false
        var isSelectedRow: Boolean = false

        init {
            isOpaque = false
            border = JBUI.Borders.empty(3, 4)
            preferredSize = java.awt.Dimension(JBUI.scale(22), JBUI.scale(20))
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val bg = when {
                isSelectedRow && isHovered -> JBColor(Color(255, 255, 255, 90), Color(255, 255, 255, 80))
                isSelectedRow -> JBColor(Color(255, 255, 255, 45), Color(255, 255, 255, 35))
                isHovered -> JBColor(Color(215, 220, 228), Color(75, 78, 83))
                else -> JBColor(Color(235, 238, 242), Color(58, 61, 65))
            }

            val borderColor = when {
                isSelectedRow && isHovered -> JBColor(Color(255, 255, 255, 220), Color(255, 255, 255, 200))
                isSelectedRow -> JBColor(Color(255, 255, 255, 120), Color(255, 255, 255, 90))
                isHovered -> JBColor(Color(170, 178, 190), Color(105, 110, 118))
                else -> JBColor(Color(205, 210, 218), Color(78, 82, 87))
            }

            g2.color = bg
            g2.fillRoundRect(0, 0, width - 1, height - 1, JBUI.scale(5), JBUI.scale(5))

            g2.color = borderColor
            g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(5), JBUI.scale(5))

            g2.dispose()
            super.paintComponent(g)
        }
    }

    private class MethodBadgeLabel : JLabel() {
        private var badgeBg: Color = Color.GRAY
        private var badgeFg: Color = Color.WHITE

        init {
            isOpaque = false
            font = font.deriveFont(Font.BOLD, JBUI.scale(10).toFloat())
            border = JBUI.Borders.empty(2, 6)
        }

        fun setMethod(method: String) {
            text = method.uppercase()
            val m = method.uppercase()
            when {
                m == "GET" -> {
                    badgeBg = JBColor(Color(39, 133, 68), Color(46, 125, 50))
                    badgeFg = Color.WHITE
                }
                m == "POST" -> {
                    badgeBg = JBColor(Color(25, 118, 210), Color(30, 136, 229))
                    badgeFg = Color.WHITE
                }
                m == "PUT" || m == "PATCH" || m.contains("PUT") -> {
                    badgeBg = JBColor(Color(230, 119, 0), Color(245, 124, 0))
                    badgeFg = Color.WHITE
                }
                m == "DELETE" -> {
                    badgeBg = JBColor(Color(211, 47, 47), Color(229, 57, 53))
                    badgeFg = Color.WHITE
                }
                else -> {
                    badgeBg = JBColor(Color(123, 31, 162), Color(142, 36, 170))
                    badgeFg = Color.WHITE
                }
            }
            foreground = badgeFg
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = badgeBg
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(6), JBUI.scale(6))
            g2.dispose()
            super.paintComponent(g)
        }
    }
}
