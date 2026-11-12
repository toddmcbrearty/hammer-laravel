package com.github.todd.laravelers.routesearch.ui

import com.github.todd.laravelers.routesearch.model.RouteItem
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * High-performance cell renderer for Laravel routes.
 * Uses native SimpleColoredComponent (zero HTML parsing) for instantaneous,
 * crash-free rendering even with thousands of routes.
 */
class RouteCellRenderer : ListCellRenderer<RouteItem> {

    private val panel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(4)))
    private val topRow = JPanel(BorderLayout(JBUI.scale(8), 0))
    private val leftTop = JPanel(BorderLayout(JBUI.scale(6), 0))
    private val bottomRow = JPanel(BorderLayout(JBUI.scale(8), 0))

    private val methodBadge = MethodBadgeLabel()
    private val uriComponent = SimpleColoredComponent()
    private val nameLabel = JLabel()
    private val actionLabel = JLabel()
    private val fileLabel = JLabel()

    private val paramPattern = Regex("""\{[^/}]+\}""")

    init {
        panel.isOpaque = true
        topRow.isOpaque = false
        leftTop.isOpaque = false
        bottomRow.isOpaque = false
        uriComponent.isOpaque = false

        leftTop.add(methodBadge, BorderLayout.WEST)
        leftTop.add(uriComponent, BorderLayout.CENTER)

        topRow.add(leftTop, BorderLayout.CENTER)
        topRow.add(nameLabel, BorderLayout.EAST)

        bottomRow.add(actionLabel, BorderLayout.WEST)
        bottomRow.add(fileLabel, BorderLayout.EAST)

        val content = JPanel()
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
            nameLabel.foreground = UIUtil.getListSelectionForeground(true)
            actionLabel.foreground = UIUtil.getListSelectionForeground(true)
            fileLabel.foreground = UIUtil.getListSelectionForeground(true)
        } else {
            panel.background = if (index % 2 == 0) UIUtil.getListBackground() else UIUtil.getDecoratedRowColor()
            nameLabel.foreground = JBColor(Color(110, 110, 110), Color(150, 150, 150))
            actionLabel.foreground = JBColor(Color(70, 70, 70), Color(180, 180, 180))
            fileLabel.foreground = JBColor(Color(130, 130, 130), Color(140, 140, 140))
        }

        // Method Badge
        methodBadge.setMethod(value.method)

        // Native URI rendering with variable parameter highlighting (Zero HTML)
        uriComponent.clear()
        renderUri(value.normalizedUri, isSelected)

        // Route Name
        if (!value.name.isNullOrBlank()) {
            nameLabel.text = value.name
            nameLabel.font = nameLabel.font.deriveFont(Font.ITALIC, JBUI.scale(11).toFloat())
            nameLabel.isVisible = true
        } else {
            nameLabel.isVisible = false
        }

        // Action / Controller
        actionLabel.text = value.action ?: "Closure"
        actionLabel.font = actionLabel.font.deriveFont(Font.PLAIN, JBUI.scale(11).toFloat())

        // File and line
        fileLabel.text = "${value.filePath}:${value.lineNumber}"
        fileLabel.font = fileLabel.font.deriveFont(Font.PLAIN, JBUI.scale(11).toFloat())

        return panel
    }

    private fun renderUri(uri: String, isSelected: Boolean) {
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
