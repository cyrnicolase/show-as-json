package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * 对话框工具类
 *
 * 提供对话框创建和配置的工具方法。
 *
 * @author cyrnicolase
 */
internal object DialogUtils {
    const val DIALOG_WIDTH = 900
    const val DIALOG_HEIGHT = 700

    /**
     * 创建并配置对话框
     *
     * @param project  项目实例
     * @param title    对话框标题
     * @param onClose  窗口关闭时的回调
     */
    fun createDialog(project: Project, title: String, onClose: () -> Unit): JDialog {
        val ownerFrame = WindowManager.getInstance().getFrame(project) as? Frame
        val dialog = JDialog(ownerFrame, title, false)

        dialog.setSize(DIALOG_WIDTH, DIALOG_HEIGHT)
        dialog.setLocationRelativeTo(ownerFrame)
        dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        // ESC 关闭对话框
        val escKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
        dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escKeyStroke, "closeDialog")
        dialog.rootPane.actionMap.put("closeDialog", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = dialog.dispose()
        })

        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) = onClose()
        })

        return dialog
    }

    /**
     * 创建包含编辑器的滚动面板
     */
    fun createScrollPane(editor: Editor): JScrollPane {
        return JScrollPane(editor.component).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBar.unitIncrement = 16
            verticalScrollBar.blockIncrement = 80
            horizontalScrollBar.blockIncrement = 80
        }
    }

    /**
     * 创建格式工具栏（美化/紧凑切换、全部展开/折叠）
     *
     * @param onFormatChange  格式切换回调，参数 isPretty
     * @param onExpandAll     全部展开回调
     * @param onCollapseAll   全部折叠回调
     */
    fun createFormatToolbar(
        onFormatChange: (isPretty: Boolean) -> Unit,
        onExpandAll: () -> Unit,
        onCollapseAll: () -> Unit
    ): JPanel {
        var isPrettyFormat = true

        val prettyButton = JButton("美化").apply {
            toolTipText = "切换到美化格式（带缩进和换行）"
            isEnabled = false
        }
        val compactButton = JButton("紧凑").apply {
            toolTipText = "切换到紧凑格式（单行无多余空格）"
        }

        prettyButton.addActionListener {
            if (!isPrettyFormat) {
                isPrettyFormat = true
                prettyButton.isEnabled = false
                compactButton.isEnabled = true
                onFormatChange(true)
            }
        }
        compactButton.addActionListener {
            if (isPrettyFormat) {
                isPrettyFormat = false
                prettyButton.isEnabled = true
                compactButton.isEnabled = false
                onFormatChange(false)
            }
        }

        val expandButton = JButton("全部展开").apply {
            toolTipText = "展开所有折叠的 JSON 节点"
            addActionListener { onExpandAll() }
        }
        val collapseButton = JButton("全部折叠").apply {
            toolTipText = "折叠所有 JSON 对象和数组"
            addActionListener { onCollapseAll() }
        }

        return JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)).apply {
            add(JLabel("格式:"))
            add(prettyButton)
            add(compactButton)
            add(JLabel("  |  ").apply { foreground = JBColor.GRAY })
            add(expandButton)
            add(collapseButton)
        }
    }

    /**
     * 创建搜索工具栏，并将 Cmd+F / Ctrl+F 快捷键注册到对话框。
     *
     * 快捷键拦截策略：
     * - `IdeEventQueue.addDispatcher`：在 IntelliJ Action System 之前拦截，确保编辑器
     *   内容组件（EditorImpl）获得焦点时也能触发（原 KeyboardFocusManager 方案的盲区）。
     * - `dialog.rootPane` WHEN_IN_FOCUSED_WINDOW：作为普通 Swing 组件获得焦点时的兜底。
     *
     * @param editor  编辑器实例
     * @param dialog  对话框实例
     * @return 搜索工具栏面板（默认隐藏）
     */
    fun createSearchToolbar(editor: Editor, dialog: JDialog): JPanel {
        val searchField = JTextField(20).apply {
            toolTipText = "搜索 JSON 内容（支持 Cmd+F / Ctrl+F）"
        }
        val prevButton = JButton("上一个").apply { isEnabled = false }
        val nextButton = JButton("下一个").apply { isEnabled = false }
        val matchLabel = JLabel("匹配: 0/0").apply { foreground = JBColor.GRAY }

        val highlighters = mutableListOf<RangeHighlighter>()
        var currentMatchIndex = -1
        var matches = emptyList<Int>()
        var currentSearchTextLength = 0

        fun clearHighlighters() {
            if (!EditorUtils.isEditorValid(editor)) return
            highlighters.forEach { runCatching { editor.markupModel.removeHighlighter(it) } }
            highlighters.clear()
        }

        fun scrollToMatch(offset: Int) {
            if (!EditorUtils.isEditorValid(editor)) return
            runCatching {
                editor.caretModel.moveToOffset(offset)
                editor.selectionModel.setSelection(offset, offset + currentSearchTextLength)
                editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
            }
        }

        fun performSearch() {
            if (!EditorUtils.isEditorValid(editor)) return
            val searchText = searchField.text.trim()
            if (searchText.isEmpty()) {
                clearHighlighters()
                matches = emptyList()
                currentMatchIndex = -1
                currentSearchTextLength = 0
                prevButton.isEnabled = false
                nextButton.isEnabled = false
                matchLabel.text = "匹配: 0/0"
                return
            }

            currentSearchTextLength = searchText.length
            clearHighlighters()

            val text = editor.document.text
            val searchLower = searchText.lowercase()
            val textLower = text.lowercase()

            matches = buildList {
                var idx = 0
                while (true) {
                    idx = textLower.indexOf(searchLower, idx)
                    if (idx == -1) break
                    add(idx)
                    idx += searchText.length
                }
            }

            val attrs = TextAttributes().apply { backgroundColor = JBColor.YELLOW }
            matches.forEach { offset ->
                runCatching {
                    highlighters.add(
                        editor.markupModel.addRangeHighlighter(
                            offset, offset + searchText.length,
                            HighlighterLayer.SELECTION - 1, attrs,
                            HighlighterTargetArea.EXACT_RANGE
                        )
                    )
                }
            }

            if (matches.isNotEmpty()) {
                currentMatchIndex = 0
                scrollToMatch(matches[0])
                prevButton.isEnabled = true
                nextButton.isEnabled = true
                matchLabel.text = "匹配: 1/${matches.size}"
            } else {
                currentMatchIndex = -1
                prevButton.isEnabled = false
                nextButton.isEnabled = false
                matchLabel.text = "匹配: 0/0"
            }
        }

        fun navigateToPrevious() {
            if (!EditorUtils.isEditorValid(editor) || matches.isEmpty() || currentMatchIndex < 0) return
            currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else matches.size - 1
            scrollToMatch(matches[currentMatchIndex])
            matchLabel.text = "匹配: ${currentMatchIndex + 1}/${matches.size}"
        }

        fun navigateToNext() {
            if (!EditorUtils.isEditorValid(editor) || matches.isEmpty() || currentMatchIndex < 0) return
            currentMatchIndex = if (currentMatchIndex < matches.size - 1) currentMatchIndex + 1 else 0
            scrollToMatch(matches[currentMatchIndex])
            matchLabel.text = "匹配: ${currentMatchIndex + 1}/${matches.size}"
        }

        val toolbarPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)).apply {
            border = JBUI.Borders.empty(5)
            add(JLabel("搜索:"))
            add(searchField)
            add(prevButton)
            add(nextButton)
            add(matchLabel)
        }

        fun hideSearchToolbar() {
            if (!EditorUtils.isEditorValid(editor)) return
            toolbarPanel.isVisible = false
            searchField.text = ""
            clearHighlighters()
            matches = emptyList()
            currentMatchIndex = -1
            currentSearchTextLength = 0
            prevButton.isEnabled = false
            nextButton.isEnabled = false
            matchLabel.text = "匹配: 0/0"
            toolbarPanel.parent?.revalidate()
            toolbarPanel.parent?.repaint()
        }

        fun openSearchToolbar(prefillFromSelection: Boolean) {
            if (!EditorUtils.isEditorValid(editor)) return

            val selectedText = editor.selectionModel.selectedText?.takeIf { it.isNotBlank() }
            val shouldPrefill = prefillFromSelection && selectedText != null

            if (!toolbarPanel.isVisible) {
                toolbarPanel.isVisible = true
                if (shouldPrefill) {
                    searchField.text = selectedText
                    performSearch()
                } else {
                    clearHighlighters()
                    matches = emptyList()
                    currentMatchIndex = -1
                    currentSearchTextLength = 0
                    prevButton.isEnabled = false
                    nextButton.isEnabled = false
                    matchLabel.text = "匹配: 0/0"
                }
            } else if (shouldPrefill && searchField.text != selectedText) {
                searchField.text = selectedText
                performSearch()
            }

            searchField.requestFocus()
            searchField.selectAll()
            toolbarPanel.parent?.revalidate()
            toolbarPanel.parent?.repaint()
        }

        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent?) = performSearch()
        })
        prevButton.addActionListener { navigateToPrevious() }
        nextButton.addActionListener { navigateToNext() }

        // ESC：清空搜索内容 或 隐藏工具栏
        val escAction = object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (searchField.text.trim().isEmpty()) {
                    hideSearchToolbar()
                } else {
                    searchField.text = ""
                    clearHighlighters()
                    matches = emptyList()
                    currentMatchIndex = -1
                    currentSearchTextLength = 0
                    prevButton.isEnabled = false
                    nextButton.isEnabled = false
                    matchLabel.text = "匹配: 0/0"
                }
            }
        }
        searchField.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearOrHideSearch")
        searchField.actionMap.put("clearOrHideSearch", escAction)

        // ── 快捷键注册 ─────────────────────────────────────────────────────────
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        val modifierMask = if (isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
        val allowedModifierMask = InputEvent.META_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or
            InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK
        val searchKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, modifierMask)
        val searchAction = object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = openSearchToolbar(prefillFromSelection = true)
        }
        fun isExactSearchShortcut(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.VK_F) return false
            val normalizedModifiers = event.modifiersEx and allowedModifierMask
            return normalizedModifiers == modifierMask
        }

        // 兜底：普通 Swing 组件获得焦点时（如工具栏按钮）
        dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(searchKeyStroke, "toggleSearch")
        dialog.rootPane.actionMap.put("toggleSearch", searchAction)

        // 主路径：IdeEventQueue 在 IntelliJ Action System 之前拦截，
        // 解决编辑器内容组件（EditorImpl）获得焦点时 Cmd+F 被 Action System 消费的问题。
        val ideEventDispatcher = com.intellij.ide.IdeEventQueue.EventDispatcher { event ->
            if (event is KeyEvent && event.id == KeyEvent.KEY_PRESSED && dialog.isVisible) {
                val source = event.source
                val isInDialog = source is java.awt.Component &&
                    SwingUtilities.isDescendingFrom(source, dialog)
                if (isInDialog) {
                    if (isExactSearchShortcut(event)) {
                        SwingUtilities.invokeLater {
                            if (dialog.isVisible) {
                                openSearchToolbar(prefillFromSelection = true)
                            }
                        }
                        return@EventDispatcher true
                    }
                }
            }
            false
        }
        com.intellij.ide.IdeEventQueue.getInstance().addDispatcher(ideEventDispatcher, null)

        // 对话框关闭时清理资源
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                runCatching {
                    com.intellij.ide.IdeEventQueue.getInstance().removeDispatcher(ideEventDispatcher)
                    clearHighlighters()
                }
            }
        })

        return toolbarPanel
    }
}
