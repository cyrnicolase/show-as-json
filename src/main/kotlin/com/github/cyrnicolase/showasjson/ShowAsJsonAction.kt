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
     * 执行操作：提取单元格值并显示在 JSON 编辑器中
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
            JsonEditorDialog.show(project, cellValue, tableFont)
        } else {
            showErrorMessage(project)
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

    /**
     * 显示错误消息对话框
     *
     * @param project 项目实例
     */
    private fun showErrorMessage(project: Project) {
        val errorMsg = """
            无法获取查询结果。请确保在查询结果表格中选中一个单元格。
            
            提示: 请确保:
              1. 在查询结果表格中选中一个单元格
              2. 单元格包含数据（不为空）
        """.trimIndent()

        Messages.showErrorDialog(project, errorMsg, "Show as JSON")
    }
}
