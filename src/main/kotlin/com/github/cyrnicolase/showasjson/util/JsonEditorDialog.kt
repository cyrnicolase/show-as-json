package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.awt.Font
import javax.swing.JDialog

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

    /** PSI 文件（用于语法高亮等基于 PSI 的能力） */
    @Volatile
    private var psiFileInstance: PsiFile? = null

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

        val formattedJson = JsonFormatter.format(jsonContent.trim())

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
                disposeEditor()
                dialogInstance = null
            }
        }

        try {
            val editor = createEditor(project, formattedJson, sourceFont)
            val scrollPane = DialogUtils.createScrollPane(editor)
            val mainPanel = DialogUtils.createMainPanel(scrollPane)

            dialog.contentPane = mainPanel

            // 保存单例引用
            dialogInstance = dialog
            editorInstance = editor

            // 显示对话框
            dialog.isVisible = true
        } catch (e: Exception) {
            // 如果创建编辑器失败，关闭对话框并显示错误消息
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
        psiFileInstance = psiFile

        // 获取或创建 Document
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: EditorFactory.getInstance().createDocument(content)

        // 同步文档内容并提交 PSI
        EditorUtils.syncDocumentAndCommitPsi(project, document, content)

        // 创建并配置编辑器
        val editor = createEditorInstance(project, document, psiFile)
        EditorUtils.configureBasicSettings(editor)
        sourceFont?.let { EditorUtils.applyFont(editor, it) }

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

        val document = editor.document

        ApplicationManager.getApplication().invokeLater {
            // 检查项目是否已关闭或编辑器是否无效
            if (project.isDisposed || !EditorUtils.isEditorValid(editor)) {
                return@invokeLater
            }

            try {
                WriteCommandAction.writeCommandAction(project).run<Throwable> {
                    document.setText(formattedJson)
                    psiFileInstance = EditorUtils.createPsiFile(project, formattedJson)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                }

                if (EditorUtils.isEditorValid(editor)) {
                    sourceFont?.let { EditorUtils.applyFont(editor, it) }
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
            psiFileInstance = null
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

