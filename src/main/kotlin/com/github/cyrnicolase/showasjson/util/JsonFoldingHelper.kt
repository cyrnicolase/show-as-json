package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * JSON 折叠辅助工具类
 *
 * 提供 JSON 编辑器的折叠功能，支持折叠和展开 JSON 对象和数组。
 *
 * @author cyrnicolase
 */
object JsonFoldingHelper {

    /** 最大折叠区域数量，防止性能问题 */
    private const val MAX_FOLD_REGIONS = 1000
    /** 折叠任务序列号，用于丢弃过期异步任务 */
    private val foldingTaskSequence = AtomicLong(0)

    /**
     * 折叠区域信息
     */
    private data class FoldableRegion(
        val startOffset: Int,
        val endOffset: Int,
        val placeholder: String
    )

    /**
     * 开始括号信息（用于线性扫描）
     */
    private data class OpenToken(
        val startOffset: Int,
        val startLine: Int,
        val openChar: Char
    )

    /**
     * 为编辑器设置 JSON 折叠功能
     *
     * @param editor 编辑器实例
     * @param jsonText JSON 文本内容
     */
    fun setupFolding(editor: Editor, jsonText: String) {
        if (!EditorUtils.isEditorValid(editor)) return
        val taskId = foldingTaskSequence.incrementAndGet()

        // 折叠区域解析放到后台线程，避免阻塞 EDT。
        ApplicationManager.getApplication().executeOnPooledThread {
            val regions = runCatching { findFoldableRegions(jsonText) }.getOrElse { emptyList() }
            ApplicationManager.getApplication().invokeLater {
                if (!EditorUtils.isEditorValid(editor)) return@invokeLater
                // 只允许最新一次请求更新折叠，避免旧任务覆盖新内容
                if (taskId != foldingTaskSequence.get()) return@invokeLater
                // 文本已变化时忽略，避免把旧文本计算出的 region 应用到新文档
                if (editor.document.text != jsonText) return@invokeLater
                runCatching {
                    editor.foldingModel.runBatchFoldingOperation {
                        editor.foldingModel.allFoldRegions.forEach { editor.foldingModel.removeFoldRegion(it) }
                        regions.forEach { region ->
                            val foldRegion = editor.foldingModel.addFoldRegion(
                                region.startOffset,
                                region.endOffset,
                                region.placeholder
                            )
                            foldRegion?.isExpanded = true
                        }
                    }
                }
            }
        }
    }

    /**
     * 展开所有折叠区域
     *
     * @param editor 编辑器实例
     */
    fun expandAll(editor: Editor) {
        if (!EditorUtils.isEditorValid(editor)) return

        ApplicationManager.getApplication().invokeLater {
            if (!EditorUtils.isEditorValid(editor)) return@invokeLater

            try {
                editor.foldingModel.runBatchFoldingOperation {
                    editor.foldingModel.allFoldRegions.forEach { region ->
                        region.isExpanded = true
                    }
                }
            } catch (e: Exception) {
                // 忽略错误
            }
        }
    }

    /**
     * 折叠所有折叠区域
     *
     * @param editor 编辑器实例
     */
    fun collapseAll(editor: Editor) {
        if (!EditorUtils.isEditorValid(editor)) return

        ApplicationManager.getApplication().invokeLater {
            if (!EditorUtils.isEditorValid(editor)) return@invokeLater

            try {
                editor.foldingModel.runBatchFoldingOperation {
                    editor.foldingModel.allFoldRegions.forEach { region ->
                        region.isExpanded = false
                    }
                }
            } catch (e: Exception) {
                // 忽略错误
            }
        }
    }

    /**
     * 查找 JSON 中所有可折叠的区域
     *
     * @param jsonText JSON 文本内容
     * @return 可折叠区域列表
     */
    private fun findFoldableRegions(jsonText: String): List<FoldableRegion> {
        val regions = mutableListOf<FoldableRegion>()
        val stack = ArrayDeque<OpenToken>()
        var inString = false
        var escapeNext = false
        var currentLine = 0

        for (i in jsonText.indices) {
            val char = jsonText[i]

            if (escapeNext) {
                escapeNext = false
                if (char == '\n') currentLine++
                continue
            }

            if (inString && char == '\\') {
                escapeNext = true
                continue
            }

            if (char == '"') {
                inString = !inString
                continue
            }

            if (!inString) {
                when (char) {
                    '{', '[' -> stack.addLast(OpenToken(i, currentLine, char))
                    '}', ']' -> {
                        val expectedOpen = if (char == '}') '{' else '['
                        while (stack.isNotEmpty() && stack.last().openChar != expectedOpen) {
                            stack.removeLast()
                        }
                        if (stack.isNotEmpty()) {
                            val openToken = stack.removeLast()
                            val endOffset = i + 1
                            val endLine = currentLine
                            if (endLine > openToken.startLine) {
                                val placeholder = if (openToken.openChar == '{') "{...}" else "[...]"
                                regions.add(FoldableRegion(openToken.startOffset, endOffset, placeholder))
                                if (regions.size >= MAX_FOLD_REGIONS) {
                                    return regions
                                }
                            }
                        }
                    }
                }
            }

            if (char == '\n') currentLine++
        }

        return regions
    }
}
