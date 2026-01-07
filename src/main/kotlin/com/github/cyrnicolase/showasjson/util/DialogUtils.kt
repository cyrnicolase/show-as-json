package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Frame
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JScrollPane
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

        // 注册 ESC 键关闭对话框
        dialog.rootPane.registerKeyboardAction(
            { dialog.dispose() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        )

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
        val component = editor.component ?: throw IllegalStateException("编辑器组件为 null")
        
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
     * @return 配置好的主面板
     */
    fun createMainPanel(scrollPane: JScrollPane): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(scrollPane, BorderLayout.CENTER)
        }
    }
}

