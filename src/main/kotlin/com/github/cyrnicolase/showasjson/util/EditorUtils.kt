package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import java.awt.Font

/**
 * 编辑器工具类
 *
 * 提供编辑器相关的工具方法。
 *
 * @author cyrnicolase
 */
internal object EditorUtils {
    /**
     * 创建 PSI 文件
     *
     * @param project 项目实例
     * @param content JSON 内容
     * @return PSI 文件
     * @throws IllegalStateException 如果项目已关闭或无法创建 PSI 文件
     */
    fun createPsiFile(project: Project, content: String): PsiFile {
        if (project.isDisposed) {
            throw IllegalStateException("项目已关闭")
        }

        val jsonFileType = FileTypeManager.getInstance().getFileTypeByExtension("json")

        return try {
            PsiFileFactory.getInstance(project).createFileFromText(
                jsonFileType,
                "show-as-json-temp.json",
                content,
                0,
                0
            )
        } catch (e: Exception) {
            throw IllegalStateException("无法创建 PSI 文件：${e.message}", e)
        }
    }

    /**
     * 同步文档内容并提交 PSI
     *
     * @param project 项目实例
     * @param document 文档实例
     * @param content 内容
     * @throws IllegalStateException 如果项目已关闭
     */
    fun syncDocumentAndCommitPsi(project: Project, document: Document, content: String) {
        if (project.isDisposed) {
            throw IllegalStateException("项目已关闭")
        }

        try {
            WriteCommandAction.writeCommandAction(project).run<Throwable> {
                if (document.text != content) {
                    document.setText(content)
                }
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        } catch (e: Exception) {
            throw IllegalStateException("无法同步文档：${e.message}", e)
        }
    }

    /**
     * 配置编辑器基本设置
     *
     * @param editor 编辑器实例
     * @param project 项目实例（用于重新高亮）
     */
    fun configureBasicSettings(editor: Editor, project: Project) {
        // 设置只读模式（仅 EditorEx 支持 setViewer）
        // 注意：即使设置为 viewer 模式，Ctrl+F 搜索功能仍然可用
        val editorEx = editor as? EditorEx
        editorEx?.setViewer(true)

        // 配置编辑器显示选项
        editor.settings.apply {
            isLineNumbersShown = true       // 显示行号
            isRightMarginShown = false      // 不显示右边距
            isUseSoftWraps = false         // 不使用软换行
        }

        // 创建自定义配色方案并应用
        try {
            if (editorEx != null) {
                // 获取全局配色方案
                val globalScheme = EditorColorsManager.getInstance().globalScheme
                // 创建可修改的副本
                val customScheme = globalScheme.clone() as EditorColorsScheme
                customScheme.name = "JSON Viewer Custom Scheme"
                
                // 应用增强的 JSON 配色到自定义方案
                JsonColorSchemeUtils.enhanceJsonColors(customScheme)
                
                // 将自定义方案设置到编辑器
                editorEx.colorsScheme = customScheme
            }
        } catch (e: Exception) {
            // 如果自定义配色失败，使用默认配色
        }
        
        // 重新高亮文档以应用新的配色
        try {
            val document = editor.document
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            if (psiFile != null) {
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        } catch (e: Exception) {
            // 忽略重新高亮错误
        }
    }

    /**
     * 应用字体到编辑器
     *
     * @param editor 编辑器实例
     * @param font 要应用的字体
     */
    fun applyFont(editor: Editor, font: Font) {
        try {
            editor.colorsScheme.setEditorFontName(font.name)
            editor.colorsScheme.setEditorFontSize(font.size)
        } catch (e: Exception) {
            // 字体设置失败时忽略，使用默认字体
        }
    }

    /**
     * 检查编辑器是否仍然有效
     *
     * @param editor 编辑器实例
     * @return 如果编辑器有效返回 true
     */
    fun isEditorValid(editor: Editor): Boolean {
        return try {
            !editor.isDisposed
        } catch (e: Exception) {
            false
        }
    }
}

