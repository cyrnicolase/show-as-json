package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import javax.swing.JTable
import java.awt.Component

/**
 * 单元格值提取工具类
 *
 * 负责从 DataGrip 的 DataContext 中提取查询结果表格的单元格值和字体信息。
 * 使用多种方法尝试提取，确保在不同场景下都能正常工作。
 *
 * @author cyrnicolase
 */
object CellValueExtractor {
    /**
     * 从 DataContext 中提取选中的单元格值
     *
     * 按优先级尝试以下方法：
     * 1. 从查询结果查看器获取（最可靠）
     * 2. 直接从表格组件获取
     *
     * 注意：不从通用编辑器获取选中文本，避免错误捕获编辑器中的文本选择。
     * 只从查询结果表格获取完整的单元格内容。
     *
     * @param dataContext DataGrip 的 DataContext
     * @return 单元格值，如果无法获取则返回 null
     */
    fun extractCellValue(dataContext: DataContext): String? {
        // 方法1: 尝试从查询结果查看器获取
        extractFromResultViewer(dataContext)?.let { return it }

        // 方法2: 尝试从表格组件获取
        extractFromTable(dataContext)?.let { return it }

        return null
    }

    /**
     * 从 DataContext 中提取表格字体
     *
     * 用于保持 JSON 编辑器中的字体大小与查询结果表格一致。
     *
     * @param dataContext DataGrip 的 DataContext
     * @return 表格字体，如果无法获取则返回 null
     */
    fun extractTableFont(dataContext: DataContext): java.awt.Font? {
        return try {
            val component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext) ?: return null
            val table = findTableComponent(component) ?: return null
            table.font
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 DataContext 中提取查询结果表格实例
     *
     * 用于为表格添加选择监听器，实现自动更新功能。
     *
     * @param dataContext DataGrip 的 DataContext
     * @return 表格实例，如果无法获取则返回 null
     */
    fun extractTable(dataContext: DataContext): JTable? {
        return try {
            val component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext) ?: return null
            findTableComponent(component)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从查询结果查看器提取单元格值
     *
     * 通过反射访问 DataGrip 内部的查询结果查看器类，
     * 尝试获取选中的单元格值。
     *
     * @param dataContext DataContext
     * @return 单元格值，如果无法获取返回 null
     */
    private fun extractFromResultViewer(dataContext: DataContext): String? {
        val possibleClasses = listOf(
            "com.intellij.database.view.DbResultViewPanel",
            "com.intellij.database.view.resultset.ResultSetViewer",
            "com.intellij.database.view.resultset.ResultSetViewerImpl",
            "com.intellij.database.view.resultset.ResultSetTable",
            "com.intellij.database.view.resultset.ResultSetTableModel",
            "com.intellij.database.view.resultset.ResultSetPanel"
        )

        for (className in possibleClasses) {
            @Suppress("UNCHECKED_CAST")
            try {
                val clazz = Class.forName(className)
                val dataKey = getDataKey(clazz) ?: continue
                val viewer = dataContext.getData(dataKey) ?: continue
                return extractValueFromViewer(viewer)
            } catch (e: ClassNotFoundException) {
                // 继续尝试下一个类
            } catch (e: Exception) {
                // 继续尝试下一个类
            }
        }

        // 尝试通过 getDataKeys() 动态查找
        try {
            val getDataKeysMethod = dataContext.javaClass.getMethod("getDataKeys")
            val dataKeys = getDataKeysMethod.invoke(dataContext) as? Array<*>
            dataKeys?.forEach { key ->
                if (key != null) {
                    try {
                        val value = dataContext.getData(key as com.intellij.openapi.actionSystem.DataKey<*>)
                        value?.let {
                            val valueClass = it.javaClass.name
                            if (valueClass.contains("ResultSet") ||
                                valueClass.contains("Database") ||
                                valueClass.contains("Table")
                            ) {
                                return extractValueFromViewer(it)
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略
        }

        return null
    }

    /**
     * 从编辑器提取选中的文本
     *
     * 已禁用此方法，避免错误捕获代码编辑器中的文本选择。
     * 只应从查询结果表格获取完整的单元格内容。
     *
     * @param dataContext DataContext
     * @return 始终返回 null
     */
    @Deprecated("不再使用，避免错误捕获编辑器文本选择", ReplaceWith("null"))
    private fun extractFromEditor(dataContext: DataContext): String? {
        return null
    }

    /**
     * 从表格组件提取选中的单元格值
     *
     * @param dataContext DataContext
     * @return 单元格值，如果无法获取返回 null
     */
    private fun extractFromTable(dataContext: DataContext): String? {
        return try {
            val component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext) ?: return null
            val table = findTableComponent(component) ?: return null
            getSelectedCellValue(table)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 通过反射获取类的 DATA_KEY 字段
     *
     * @param clazz 目标类
     * @return DATA_KEY 字段的值，如果获取失败返回 null
     */
    private fun getDataKey(clazz: Class<*>): com.intellij.openapi.actionSystem.DataKey<*>? {
        return try {
            val dataKeyField = clazz.getField("DATA_KEY")
            dataKeyField.get(null) as? com.intellij.openapi.actionSystem.DataKey<*>
        } catch (e: Exception) {
            try {
                val dataKeyField = clazz.getDeclaredField("DATA_KEY")
                dataKeyField.isAccessible = true
                dataKeyField.get(null) as? com.intellij.openapi.actionSystem.DataKey<*>
            } catch (ex: Exception) {
                null
            }
        }
    }

    /**
     * 从查看器对象中提取单元格值
     *
     * 使用反射尝试多种方法获取值：
     * 1. 调用包含 "Cell" 和 "Value" 的方法
     * 2. 获取表格组件并从中提取值
     * 3. 获取选中行和列，然后调用 getCellValue 方法
     *
     * @param viewer 查看器对象
     * @return 单元格值，如果无法获取返回 null
     */
    private fun extractValueFromViewer(viewer: Any): String? {
        // 方法1: 尝试调用 getSelectedCellValue 或类似方法
        viewer.javaClass.methods
            .filter { it.name.contains("Cell") && it.name.contains("Value") && it.parameterCount == 0 }
            .forEach { method ->
                try {
                    method.invoke(viewer)?.toString()?.let { return it }
                } catch (e: Exception) {
                    // 继续尝试下一个方法
                }
            }

        // 方法2: 尝试获取表格并从中获取值
        try {
            val table = viewer.javaClass.getMethod("getTable").invoke(viewer) as? JTable
            table?.let { return getSelectedCellValue(it) }
        } catch (e: Exception) {
            // 继续尝试下一个方法
        }

        // 方法3: 尝试通过反射获取选中行和列，然后获取值
        try {
            val row = viewer.javaClass.getMethod("getSelectedRow").invoke(viewer) as? Int ?: -1
            val column = viewer.javaClass.getMethod("getSelectedColumn").invoke(viewer) as? Int ?: -1
            if (row >= 0 && column >= 0) {
                val value = viewer.javaClass.getMethod("getCellValue", Int::class.java, Int::class.java)
                    .invoke(viewer, row, column)
                return value?.toString()
            }
        } catch (e: Exception) {
            // 所有方法都失败
        }

        return null
    }

    /**
     * 在组件层次结构中查找 JTable 组件
     *
     * @param component 起始组件
     * @return 找到的 JTable 组件，如果未找到返回 null
     */
    private fun findTableComponent(component: Component): JTable? {
        var current: Component? = component
        while (current != null) {
            if (current is JTable) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /**
     * 从表格获取选中的单元格值
     *
     * @param table 表格组件
     * @return 单元格值，如果未选中单元格返回 null
     */
    private fun getSelectedCellValue(table: JTable): String? {
        val selectedRow = table.selectedRow
        val selectedColumn = table.selectedColumn

        // 检查行和列是否有效
        if (selectedRow < 0 || selectedColumn < 0 ||
            selectedRow >= table.rowCount || selectedColumn >= table.columnCount
        ) {
            return null
        }

        return try {
            table.getValueAt(selectedRow, selectedColumn)?.toString()
        } catch (e: Exception) {
            // 如果获取值失败（例如索引越界），返回 null
            null
        }
    }
}

