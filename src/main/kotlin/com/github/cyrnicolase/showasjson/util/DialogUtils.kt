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
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.event.KeyEvent
import java.awt.event.KeyAdapter
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.InputEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.WindowConstants

/**
 * 对话框工具类
 *
 * 提供对话框创建和配置的工具方法。
 *
 * @author cyrnicolase
 */
internal object DialogUtils {
    /** 对话框宽度 */
    const val DIALOG_WIDTH = 900

    /** 对话框高度 */
    const val DIALOG_HEIGHT = 700

    /**
     * 创建并配置对话框
     *
     * @param project 项目实例
     * @param title 对话框标题
     * @param onClose 窗口关闭时的回调
     * @return 配置好的对话框
     */
    fun createDialog(project: Project, title: String, onClose: () -> Unit): JDialog {
        val ownerFrame = WindowManager.getInstance().getFrame(project) as? Frame
        val dialog = JDialog(ownerFrame, title, false)

        dialog.setSize(DIALOG_WIDTH, DIALOG_HEIGHT)
        dialog.setLocationRelativeTo(ownerFrame)
        dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        // 注册 ESC 键关闭对话框（使用现代的 InputMap/ActionMap 方式）
        val escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
        val inputMap = dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actionMap = dialog.rootPane.actionMap
        
        inputMap.put(escapeKeyStroke, "closeDialog")
        actionMap.put("closeDialog", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                dialog.dispose()
            }
        })

        // 添加窗口关闭监听器
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                onClose()
            }
        })

        return dialog
    }

    /**
     * 创建包含编辑器的滚动面板
     *
     * @param editor 编辑器实例
     * @return 配置好的滚动面板
     * @throws IllegalStateException 如果编辑器组件为 null
     */
    fun createScrollPane(editor: Editor): JScrollPane {
        val component = editor.component
        
        return JScrollPane(component).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED

            // 设置滚动速度：单位增量 16 像素，块增量 80 像素
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBar.unitIncrement = 16
            verticalScrollBar.blockIncrement = 80
            horizontalScrollBar.blockIncrement = 80
        }
    }

    /**
     * 创建主面板
     *
     * @param scrollPane 滚动面板
     * @param searchToolbar 搜索工具栏（可选）
     * @return 配置好的主面板
     */
    fun createMainPanel(scrollPane: JScrollPane, searchToolbar: JPanel? = null): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            searchToolbar?.let {
                add(it, BorderLayout.NORTH)
                // 默认隐藏搜索工具栏
                it.isVisible = false
            }
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    /**
     * 创建搜索工具栏
     *
     * @param editor 编辑器实例
     * @param dialog 对话框实例（用于注册快捷键和清理资源）
     * @return 配置好的搜索工具栏面板
     */
    fun createSearchToolbar(editor: Editor, dialog: JDialog): JPanel {
        val searchField = JTextField(20).apply {
            toolTipText = "搜索 JSON 内容（支持 Cmd+F / Ctrl+F）"
        }
        val prevButton = JButton("上一个").apply {
            isEnabled = false
        }
        val nextButton = JButton("下一个").apply {
            isEnabled = false
        }
        val matchLabel = JLabel("匹配: 0/0").apply {
            foreground = JBColor.GRAY
        }

        val highlighters = mutableListOf<RangeHighlighter>()
        var currentMatchIndex = -1
        var matches = emptyList<Int>()
        var currentSearchTextLength = 0  // 保存当前搜索文本的长度，用于选中文本

        // 清理高亮器的函数
        fun clearHighlighters() {
            if (!EditorUtils.isEditorValid(editor)) return
            
            highlighters.forEach { highlighter ->
                try {
                    editor.markupModel.removeHighlighter(highlighter)
                } catch (e: Exception) {
                    // 忽略已释放的高亮器
                }
            }
            highlighters.clear()
        }

        // 滚动到匹配位置并选中匹配的文本
        fun scrollToMatch(editor: Editor, offset: Int, searchTextLength: Int) {
            if (!EditorUtils.isEditorValid(editor)) return
            
            try {
                // 移动光标到匹配开始位置
                editor.caretModel.moveToOffset(offset)
                
                // 选中匹配的文本范围
                editor.selectionModel.setSelection(offset, offset + searchTextLength)
                
                // 滚动到光标位置（居中显示）
                editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
            } catch (e: Exception) {
                // 忽略编辑器操作错误
            }
        }

        // 在对话框关闭时清理高亮器
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                clearHighlighters()
            }
        })

        // 搜索文本
        fun performSearch() {
            if (!EditorUtils.isEditorValid(editor)) return
            
            val searchText = searchField.text.trim()
            if (searchText.isEmpty()) {
                // 清除高亮
                clearHighlighters()
                matches = emptyList()
                currentMatchIndex = -1
                currentSearchTextLength = 0
                prevButton.isEnabled = false
                nextButton.isEnabled = false
                matchLabel.text = "匹配: 0/0"
                return
            }

            // 保存搜索文本长度
            currentSearchTextLength = searchText.length

            // 清除旧的高亮
            clearHighlighters()

            // 查找所有匹配项
            val document = editor.document
            val text = document.text
            val searchTextLower = searchText.lowercase()
            val textLower = text.lowercase()

            matches = mutableListOf<Int>().apply {
                var index = 0
                while (true) {
                    index = textLower.indexOf(searchTextLower, index)
                    if (index == -1) break
                    add(index)
                    index += searchText.length
                }
            }

            // 高亮所有匹配项
            // 使用 JBColor.YELLOW 创建高亮背景色
            val textAttributes = TextAttributes().apply {
                backgroundColor = JBColor.YELLOW
            }
            
            matches.forEach { offset ->
                try {
                    val highlighter = editor.markupModel.addRangeHighlighter(
                        offset,
                        offset + searchText.length,
                        HighlighterLayer.SELECTION - 1,  // 使用较高的层级确保可见
                        textAttributes,
                        HighlighterTargetArea.EXACT_RANGE
                    )
                    highlighters.add(highlighter)
                } catch (e: Exception) {
                    // 忽略高亮器添加错误（可能编辑器已释放）
                }
            }

            // 更新 UI
            if (matches.isNotEmpty()) {
                currentMatchIndex = 0
                scrollToMatch(editor, matches[0], currentSearchTextLength)
                prevButton.isEnabled = true
                nextButton.isEnabled = true
                matchLabel.text = "匹配: ${currentMatchIndex + 1}/${matches.size}"
            } else {
                currentMatchIndex = -1
                prevButton.isEnabled = false
                nextButton.isEnabled = false
                matchLabel.text = "匹配: 0/0"
            }
        }

        // 导航到上一个匹配
        fun navigateToPrevious() {
            if (!EditorUtils.isEditorValid(editor) || matches.isEmpty() || currentMatchIndex < 0) return
            currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else matches.size - 1
            scrollToMatch(editor, matches[currentMatchIndex], currentSearchTextLength)
            matchLabel.text = "匹配: ${currentMatchIndex + 1}/${matches.size}"
        }

        // 导航到下一个匹配
        fun navigateToNext() {
            if (!EditorUtils.isEditorValid(editor) || matches.isEmpty() || currentMatchIndex < 0) return
            currentMatchIndex = if (currentMatchIndex < matches.size - 1) currentMatchIndex + 1 else 0
            scrollToMatch(editor, matches[currentMatchIndex], currentSearchTextLength)
            matchLabel.text = "匹配: ${currentMatchIndex + 1}/${matches.size}"
        }

        // 绑定事件
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent?) {
                performSearch()
            }
        })

        prevButton.addActionListener { navigateToPrevious() }
        nextButton.addActionListener { navigateToNext() }

        // 创建工具栏面板
        val toolbarPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)).apply {
            border = JBUI.Borders.empty(5)
            add(JLabel("搜索:"))
            add(searchField)
            add(prevButton)
            add(nextButton)
            add(matchLabel)
        }

        // 切换搜索工具栏显示/隐藏的函数
        fun toggleSearchToolbar() {
            if (!EditorUtils.isEditorValid(editor)) return
            
            val isVisible = toolbarPanel.isVisible
            toolbarPanel.isVisible = !isVisible
            
            if (toolbarPanel.isVisible) {
                // 显示搜索工具栏时，聚焦搜索框并清空之前的高亮
                searchField.requestFocus()
                searchField.selectAll()
                clearHighlighters()
                matches = emptyList()
                currentMatchIndex = -1
                currentSearchTextLength = 0
                prevButton.isEnabled = false
                nextButton.isEnabled = false
                matchLabel.text = "匹配: 0/0"
            } else {
                // 隐藏搜索工具栏时，清除搜索内容和高亮
                searchField.text = ""
                clearHighlighters()
                matches = emptyList()
                currentMatchIndex = -1
                currentSearchTextLength = 0
            }
            
            // 刷新对话框布局
            toolbarPanel.parent?.revalidate()
            toolbarPanel.parent?.repaint()
        }

        // 检测操作系统类型（Mac 使用 Cmd，其他使用 Ctrl）
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        val modifierKey = if (isMac) {
            InputEvent.META_DOWN_MASK
        } else {
            InputEvent.CTRL_DOWN_MASK
        }

        // 注册 Cmd+F (Mac) 或 Ctrl+F (Windows/Linux) 快捷键切换搜索工具栏显示/隐藏
        // 使用现代的 InputMap/ActionMap 方式替代已弃用的 registerKeyboardAction
        val searchKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, modifierKey)
        val inputMap = dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actionMap = dialog.rootPane.actionMap
        
        inputMap.put(searchKeyStroke, "toggleSearch")
        actionMap.put("toggleSearch", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                toggleSearchToolbar()
            }
        })

        // 注册 ESC 键处理（当搜索框获得焦点时，使用现代的 InputMap/ActionMap 方式）
        val escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
        val searchFieldInputMap = searchField.getInputMap(JComponent.WHEN_FOCUSED)
        val searchFieldActionMap = searchField.actionMap
        
        searchFieldInputMap.put(escapeKeyStroke, "clearOrHideSearch")
        searchFieldActionMap.put("clearOrHideSearch", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val searchText = searchField.text.trim()
                if (searchText.isEmpty()) {
                    // 如果搜索框为空，隐藏搜索工具栏
                    toolbarPanel.isVisible = false
                    clearHighlighters()
                    currentSearchTextLength = 0
                    toolbarPanel.parent?.revalidate()
                    toolbarPanel.parent?.repaint()
                } else {
                    // 如果搜索框有内容，清除搜索内容和高亮
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
        })

        return toolbarPanel
    }
}

