package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JDialog
import javax.swing.JPanel

/**
 * JSON 编辑器对话框工具类（单例模式）
 *
 * 负责创建和显示 JSON 编辑器对话框，提供 JSON 语法高亮等功能。
 * 全局只有一个编辑器实例，当光标切换时自动更新显示内容。
 *
 * @author cyrnicolase
 */
object JsonEditorDialog {
    /** 单例对话框实例 */
    @Volatile
    private var dialogInstance: JDialog? = null

    /** 单例编辑器实例 */
    @Volatile
    private var editorInstance: Editor? = null

    /** 同步锁 */
    private val lock = Any()

    /** 单元格选择监听器 */
    @Volatile
    private var cellMonitor: CellSelectionMonitor? = null

    /** 原始 JSON 内容（未格式化，用于格式切换） */
    @Volatile
    private var originalJsonContent: String? = null

    /** 当前格式是否为美化格式 */
    @Volatile
    private var isPrettyFormat: Boolean = true

    /** 内容更新序列，避免异步任务乱序覆盖 */
    private val contentUpdateSequence = AtomicLong(0)

    private fun formatByCurrentMode(content: String): String {
        return if (isPrettyFormat) {
            JsonFormatter.formatPretty(content)
        } else {
            JsonFormatter.formatCompact(content)
        }
    }

    private fun stopCellMonitor() {
        cellMonitor?.stop()
        cellMonitor = null
    }

    private fun resetDialogState() {
        originalJsonContent = null
        isPrettyFormat = true
    }

    /**
     * 显示 JSON 编辑器对话框（单例模式）
     *
     * 如果对话框已存在且可见，则更新内容；否则创建新对话框。
     *
     * @param project 项目实例
     * @param jsonContent JSON 内容字符串
     * @param sourceFont 源字体（查询结果表格的字体，用于保持字体大小一致）
     */
    fun show(project: Project, jsonContent: String?, sourceFont: Font? = null) {
        if (project.isDisposed) {
            return
        }

        if (jsonContent.isNullOrBlank() || jsonContent == "(NULL)") {
            Messages.showInfoMessage(project, "选中的单元格为空。", "Show as JSON")
            return
        }

        // 保存原始内容
        val trimmedContent = jsonContent.trim()
        originalJsonContent = trimmedContent
        
        // 根据当前格式选择格式化方法
        val formattedJson = formatByCurrentMode(trimmedContent)

        synchronized(lock) {
            // 再次检查项目是否已关闭（可能在等待锁时项目被关闭）
            if (project.isDisposed) {
                return
            }

            val dialog = dialogInstance
            val editor = editorInstance

            // 如果对话框存在且可见，更新内容
            if (dialog != null && dialog.isVisible && editor != null && EditorUtils.isEditorValid(editor)) {
                updateContent(project, formattedJson, sourceFont, editor)
                // 确保对话框显示在最前面
                try {
                    dialog.toFront()
                } catch (e: Exception) {
                    // 如果对话框已被释放，忽略错误
                }
                return
            }

            // 如果对话框存在但编辑器无效，清理并重新创建
            if (dialog != null) {
                disposeEditor()
                try {
                    dialog.dispose()
                } catch (e: Exception) {
                    // 如果对话框已被释放，忽略错误
                }
            }

            // 创建新对话框
            createDialog(project, formattedJson, sourceFont)
        }
    }

    /**
     * 创建并配置对话框
     *
     * @param project 项目实例
     * @param formattedJson 格式化后的 JSON 字符串
     * @param sourceFont 源字体
     */
    private fun createDialog(project: Project, formattedJson: String, sourceFont: Font?) {
        val dialog = DialogUtils.createDialog(project, "Show as JSON") {
            synchronized(lock) {
                stopCellMonitor()
                resetDialogState()
                disposeEditor()
                dialogInstance = null
            }
        }

        try {
            val editor = createEditor(project, formattedJson, sourceFont)
            val scrollPane = DialogUtils.createScrollPane(editor)
            
            // 创建格式工具栏
            val formatToolbar = DialogUtils.createFormatToolbar(
                onFormatChange = { isPretty ->
                    // 格式切换回调
                    isPrettyFormat = isPretty
                    originalJsonContent?.let { original ->
                        val newFormatted = formatByCurrentMode(original)
                        updateContent(project, newFormatted, sourceFont, editor)
                    }
                },
                onExpandAll = {
                    // 展开所有折叠
                    JsonFoldingHelper.expandAll(editor)
                },
                onCollapseAll = {
                    // 折叠所有
                    JsonFoldingHelper.collapseAll(editor)
                },
                onCopy = {
                    if (EditorUtils.isEditorValid(editor)) {
                        CopyPasteManager.getInstance()
                            .setContents(StringSelection(editor.document.text))
                    }
                }
            )
            
            val searchToolbar = DialogUtils.createSearchToolbar(editor, dialog)
            searchToolbar.isVisible = false

            val mainPanel = JPanel(java.awt.BorderLayout()).apply {
                border = com.intellij.util.ui.JBUI.Borders.empty(10)
                val topPanel = JPanel(java.awt.BorderLayout()).apply {
                    add(formatToolbar, java.awt.BorderLayout.NORTH)
                    add(searchToolbar, java.awt.BorderLayout.SOUTH)
                }
                add(topPanel, java.awt.BorderLayout.NORTH)
                add(scrollPane, java.awt.BorderLayout.CENTER)
            }

            dialog.contentPane = mainPanel

            // 保存单例引用
            dialogInstance = dialog
            editorInstance = editor

            // 创建并启动单元格选择监听器
            cellMonitor = CellSelectionMonitor(project) { cellValue, font ->
                // 自动更新内容
                editorInstance?.let { ed ->
                    if (EditorUtils.isEditorValid(ed)) {
                        // 保存原始内容
                        val trimmed = cellValue.trim()
                        originalJsonContent = trimmed

                        // 根据当前格式偏好格式化
                        val formatted = formatByCurrentMode(trimmed)
                        updateContent(project, formatted, font, ed)
                    }
                }
            }.apply { 
                // 传入编辑器引用，避免从面板中提取选中的文本
                start(editor) 
            }

            // 显示对话框
            dialog.isVisible = true
        } catch (e: Exception) {
            // 如果创建编辑器失败，清理资源
            stopCellMonitor()
            dialog.dispose()
            Messages.showErrorDialog(project, "创建 JSON 编辑器失败：${e.message}", "Show as JSON")
        }
    }

    /**
     * 创建 JSON 编辑器（使用 EditorFactory）
     *
     * 确保 PSI 文件正确创建，以启用 JSON 语法高亮功能。
     *
     * @param project 项目实例
     * @param content JSON 内容
     * @param sourceFont 源字体（用于保持字体大小一致）
     * @return 配置好的编辑器实例
     * @throws IllegalStateException 如果项目已关闭或无法创建编辑器
     */
    private fun createEditor(project: Project, content: String, sourceFont: Font?): Editor {
        if (project.isDisposed) {
            throw IllegalStateException("项目已关闭")
        }

        // 创建 PSI 文件
        val psiFile = EditorUtils.createPsiFile(project, content)

        // 获取或创建 Document
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: EditorFactory.getInstance().createDocument(content)

        // 同步文档内容并提交 PSI
        EditorUtils.syncDocumentAndCommitPsi(project, document, content)

        // 创建并配置编辑器
        val editor = createEditorInstance(project, document, psiFile)
        EditorUtils.configureBasicSettings(editor)
        sourceFont?.let { EditorUtils.applyFont(editor, it) }

        // 设置 JSON 折叠功能
        JsonFoldingHelper.setupFolding(editor, content)

        return editor
    }

    /**
     * 创建编辑器实例
     *
     * 优先使用 VirtualFile，确保 PSI 文件正确关联。
     *
     * @param project 项目实例
     * @param document 文档实例
     * @param psiFile PSI 文件
     * @return 编辑器实例
     * @throws IllegalStateException 如果无法创建编辑器
     */
    private fun createEditorInstance(project: Project, document: Document, psiFile: PsiFile): Editor {
        val jsonFileType = FileTypeManager.getInstance().getFileTypeByExtension("json")
        val editorFactory = EditorFactory.getInstance()

        // 优先尝试使用 VirtualFile 创建编辑器
        psiFile.virtualFile?.let { virtualFile ->
            try {
                return editorFactory.createEditor(document, project, virtualFile, false)
            } catch (e: Exception) {
                // 如果失败，继续尝试其他方法
            }
        }

        // 尝试使用文件类型创建编辑器
        try {
            return editorFactory.createEditor(document, project, jsonFileType, false)
        } catch (e: Exception) {
            // 最后尝试创建通用编辑器
            try {
                return editorFactory.createEditor(document, project)
            } catch (e2: Exception) {
                throw IllegalStateException("无法创建编辑器：${e2.message}", e2)
            }
        }
    }

    /**
     * 更新编辑器内容
     *
     * 重新创建 PSI 文件并更新文档，确保语法高亮正确工作。
     *
     * @param project 项目实例
     * @param formattedJson 格式化后的 JSON 字符串
     * @param sourceFont 源字体
     * @param editor 编辑器实例
     */
    private fun updateContent(project: Project, formattedJson: String, sourceFont: Font?, editor: Editor) {
        if (!EditorUtils.isEditorValid(editor) || project.isDisposed) {
            return
        }

        val updateId = contentUpdateSequence.incrementAndGet()
        val document = editor.document

        ApplicationManager.getApplication().invokeLater {
            // 检查项目是否已关闭或编辑器是否无效
            if (project.isDisposed || !EditorUtils.isEditorValid(editor)) {
                return@invokeLater
            }
            if (updateId != contentUpdateSequence.get()) {
                return@invokeLater
            }

            try {
                WriteCommandAction.writeCommandAction(project).run<Throwable> {
                    if (document.text != formattedJson) {
                        document.setText(formattedJson)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }
                }

                if (EditorUtils.isEditorValid(editor)) {
                    sourceFont?.let { EditorUtils.applyFont(editor, it) }
                    
                    // 重新设置折叠功能
                    JsonFoldingHelper.setupFolding(editor, formattedJson)
                }
            } catch (e: Exception) {
                // 如果更新失败（例如项目已关闭），忽略错误
            }
        }
    }

    /**
     * 释放编辑器资源
     */
    private fun disposeEditor() {
        synchronized(lock) {
            editorInstance?.let { editor ->
                try {
                    if (EditorUtils.isEditorValid(editor)) {
                        EditorFactory.getInstance().releaseEditor(editor)
                    }
                } catch (e: Exception) {
                    // 忽略释放错误（编辑器可能已经被释放）
                }
            }
            editorInstance = null
        }
    }

    /**
     * 检查对话框是否打开且可见
     *
     * @return 如果对话框存在且可见返回 true，否则返回 false
     */
    fun isDialogOpen(): Boolean {
        synchronized(lock) {
            val dialog = dialogInstance
            return dialog != null && dialog.isVisible
        }
    }

    /**
     * 关闭当前打开的对话框
     *
     * 如果对话框存在且可见，则关闭它并释放相关资源。
     */
    fun closeDialog() {
        synchronized(lock) {
            val dialog = dialogInstance
            if (dialog != null && dialog.isVisible) {
                stopCellMonitor()
                resetDialogState()

                // 释放编辑器资源
                disposeEditor()

                // 关闭对话框
                try {
                    dialog.dispose()
                } catch (e: Exception) {
                    // 如果对话框已被释放，忽略错误
                }

                // 清空引用
                dialogInstance = null
            }
        }
    }
}

