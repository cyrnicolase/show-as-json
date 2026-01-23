package com.github.cyrnicolase.showasjson

import com.github.cyrnicolase.showasjson.util.CellValueExtractor
import com.github.cyrnicolase.showasjson.util.JsonEditorDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.Frame

/**
 * Show as JSON Action
 *
 * 在查询结果表格中，将选中的单元格内容以 JSON 格式打开并高亮显示。
 * 支持通过快捷键（F7）触发。
 *
 * @author cyrnicolase
 */
class ShowAsJsonAction : AnAction() {

    /**
     * 执行操作：提取单元格值并显示在 JSON 编辑器中，或切换面板状态
     *
     * 如果选择了输出单元格，则打开/更新 show as json 面板。
     * 如果未选择输出单元格，则检查是否有打开的 show as json 面板，如果有则关闭，如果没有则无事件。
     *
     * @param e Action 事件，包含项目上下文和数据上下文
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: run {
            Messages.showErrorDialog(null as Frame?, "无法获取项目上下文。", "Show as JSON")
            return
        }

        val dataContext = e.dataContext
        val cellValue = CellValueExtractor.extractCellValue(dataContext)
        val tableFont = CellValueExtractor.extractTableFont(dataContext)

        if (cellValue != null) {
            // 有选择输出单元格：打开/更新面板
            JsonEditorDialog.show(project, cellValue, tableFont)
        } else {
            // 没有选择输出单元格：检查并关闭已打开的面板
            if (JsonEditorDialog.isDialogOpen()) {
                JsonEditorDialog.closeDialog()
            }
            // 如果没有打开的面板，则无事件（不显示错误消息）
        }
    }

    /**
     * 控制 Action 的可用性
     *
     * 快捷键始终启用，允许在任何地方触发（在 actionPerformed 中会检查是否在查询结果表格中）。
     *
     * @param e Action 事件
     */
    override fun update(e: AnActionEvent) {
        val project = e.project

        // 快捷键始终启用，只要有项目上下文即可
        e.presentation.isEnabled = project != null
        e.presentation.isVisible = true
        e.presentation.text = "Show as JSON"
    }
}
