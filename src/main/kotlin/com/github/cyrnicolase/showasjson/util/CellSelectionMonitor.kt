package com.github.cyrnicolase.showasjson.util

import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import javax.swing.JTable
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import java.awt.Font
import java.util.Timer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.timer

/**
 * 单元格选择监听器
 *
 * 监听 DataGrip 查询结果表格的单元格选择变化，当检测到新选择时触发回调。
 * 采用混合监听策略：优先使用事件监听，降级为定时轮询。
 *
 * @param project 项目实例
 * @param onCellChanged 单元格内容变化时的回调函数
 * @author cyrnicolase
 */
class CellSelectionMonitor(
    private val project: Project,
    private val onCellChanged: (cellValue: String, font: Font?) -> Unit
) {
    /** 当前单元格值（用于检测变化） */
    @Volatile
    private var currentCellValue: String? = null

    /** 定时器（轮询模式） */
    @Volatile
    private var monitorTimer: Timer? = null

    /** 表格选择监听器（事件模式） */
    @Volatile
    private var tableListener: ListSelectionListener? = null
    @Volatile
    private var columnListener: ListSelectionListener? = null

    /** 当前监听的表格实例 */
    @Volatile
    private var monitoredTable: JTable? = null

    /** 监听器是否正在运行 */
    @Volatile
    private var isRunning = false

    /** 轮询间隔（毫秒） - 使用较短间隔以减少延迟 */
    private val pollingInterval = 100L

    /** 上次检查时间（用于防抖） */
    @Volatile
    private var lastCheckTime = 0L

    /** 防抖间隔（毫秒） - 事件模式下的防抖 */
    private val debounceInterval = 50L

    /** 避免并发触发多个异步 DataContext 检查 */
    private val checkInProgress = AtomicBoolean(false)
    @Volatile
    private var checkStartedAtMs = 0L
    private val checkRequestSequence = AtomicLong(0)
    @Volatile
    private var activeCheckRequestId = -1L
    private val pollCheckCounter = AtomicLong(0)

    /** 异步检查超时兜底，防止门闩卡死 */
    private val checkTimeoutMs = 3000L

    /** 轮询自适应间隔（毫秒） */
    @Volatile
    private var adaptivePollingIntervalMs = pollingInterval
    private val maxPollingIntervalMs = 600L

    /** JSON 面板编辑器引用，用于避免从面板中提取选中的文本 */
    @Volatile
    private var jsonPanelEditor: com.intellij.openapi.editor.Editor? = null

    /**
     * 启动监听器
     *
     * 尝试使用事件监听模式，失败时降级为轮询模式。
     *
     * @param editorToExclude JSON 面板编辑器，避免从面板中提取选中的文本
     */
    fun start(editorToExclude: com.intellij.openapi.editor.Editor? = null) {
        if (isRunning || project.isDisposed) {
            return
        }

        jsonPanelEditor = editorToExclude
        isRunning = true

        // 先立即尝试启动事件监听模式
        tryStartEventMode()
        
        // 同时启动轮询模式作为后备，轮询过程中会持续尝试升级到事件模式
        startPollingMode()
    }

    /**
     * 停止监听器
     *
     * 清理所有资源，移除监听器，停止定时器。
     */
    fun stop() {
        isRunning = false

        // 移除表格监听器
        removeTableListener()

        // 停止定时器
        stopPollingTimer()

        // 清空引用和状态
        currentCellValue = null
        monitoredTable = null
        lastCheckTime = 0L
        jsonPanelEditor = null
        checkInProgress.set(false)
        checkStartedAtMs = 0L
        activeCheckRequestId = -1L
        pollCheckCounter.set(0L)
        adaptivePollingIntervalMs = pollingInterval
    }

    /**
     * 尝试启动事件监听模式
     *
     * @return 如果成功启动事件监听返回 true，否则返回 false
     */
    private fun tryStartEventMode(): Boolean {
        try {
            // 异步获取当前焦点的 DataContext
            DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
                if (!isRunning || project.isDisposed) {
                    return@onSuccess
                }

                // 尝试获取表格实例
                val table = CellValueExtractor.extractTable(dataContext)
                if (table != null) {
                    // 成功获取表格，设置事件监听
                    setupTableListener(table)
                } else {
                    // 无法获取表格，启动轮询模式
                    if (isRunning && monitorTimer == null) {
                        startPollingMode()
                    }
                }
            }
            return true
        } catch (e: Exception) {
            // 事件监听模式失败
            return false
        }
    }

    /**
     * 设置表格选择监听器
     *
     * @param table 表格实例
     */
    private fun setupTableListener(table: JTable) {
        // 如果已经监听了同一个表格，不重复设置
        if (monitoredTable == table && tableListener != null) {
            return
        }
        
        // 移除旧的监听器（如果有）
        removeTableListener()
        
        // 停止轮询模式（已升级到事件模式）
        stopPollingTimer()

        // 创建新的监听器
        val rowSelectionListener = createSelectionListener()
        val colSelectionListener = createSelectionListener()

        // 添加监听器到表格（行 + 列）
        try {
            table.selectionModel.addListSelectionListener(rowSelectionListener)
            table.columnModel.selectionModel.addListSelectionListener(colSelectionListener)
            monitoredTable = table
            tableListener = rowSelectionListener
            columnListener = colSelectionListener

            // 立即检查一次当前选择
            checkCellChangeImmediate()
        } catch (e: Exception) {
            // 添加监听器失败，降级为轮询模式
            if (monitorTimer == null) {
                startPollingMode()
            }
        }
    }

    private fun createSelectionListener(): ListSelectionListener {
        return ListSelectionListener { e: ListSelectionEvent? ->
            if (e?.valueIsAdjusting == true || !isRunning || project.isDisposed) {
                return@ListSelectionListener
            }
            checkCellChangeImmediate()
        }
    }

    /**
     * 移除表格监听器
     */
    private fun removeTableListener() {
        val table = monitoredTable
        val rowListener = tableListener
        val colListener = columnListener

        if (table != null) {
            try {
                rowListener?.let { table.selectionModel.removeListSelectionListener(it) }
                colListener?.let { table.columnModel.selectionModel.removeListSelectionListener(it) }
            } catch (e: Exception) {
                // 忽略移除错误
            }
        }

        monitoredTable = null
        tableListener = null
        columnListener = null
    }

    /**
     * 启动轮询模式
     */
    private fun startPollingMode() {
        // 如果已有定时器，先停止
        stopPollingTimer()

        if (!isRunning || project.isDisposed) {
            return
        }

        try {
            monitorTimer = timer(
                name = "CellSelectionMonitor-${System.currentTimeMillis()}",
                daemon = true,
                initialDelay = 0L,
                period = pollingInterval
            ) {
                if (!isRunning || project.isDisposed) {
                    this.cancel()
                    return@timer
                }

                // 检查单元格变化
                checkCellChange()
            }
        } catch (e: Exception) {
            // 定时器创建失败，停止监听
            isRunning = false
        }
    }

    /**
     * 停止轮询定时器
     */
    private fun stopPollingTimer() {
        monitorTimer?.cancel()
        monitorTimer = null
    }

    /**
     * 检查单元格内容是否变化（带防抖，用于轮询模式）
     *
     * 如果变化则触发回调。使用防抖机制避免频繁检查。
     */
    private fun checkCellChange() {
        if (!isRunning || project.isDisposed) {
            return
        }

        // 防抖：如果距离上次检查时间太短，跳过
        val currentTime = System.currentTimeMillis()
        val minInterval = maxOf(debounceInterval, adaptivePollingIntervalMs)
        if (currentTime - lastCheckTime < minInterval) {
            return
        }
        lastCheckTime = currentTime

        performCheck()
    }

    /**
     * 立即检查单元格内容（无防抖，用于事件模式）
     */
    private fun checkCellChangeImmediate() {
        if (!isRunning || project.isDisposed) {
            return
        }

        performCheck()
    }

    /**
     * 执行实际的单元格内容检查
     */
    private fun performCheck() {
        val now = System.currentTimeMillis()
        if (checkInProgress.get()) {
            if (now - checkStartedAtMs > checkTimeoutMs) {
                forceResetCheckState()
            } else {
                return
            }
        }
        if (!checkInProgress.compareAndSet(false, true)) {
            return
        }
        val requestId = checkRequestSequence.incrementAndGet()
        activeCheckRequestId = requestId
        checkStartedAtMs = now

        try {
            // 异步获取 DataContext
            DataManager.getInstance().dataContextFromFocusAsync
                .onSuccess { dataContext ->
                    try {
                        if (requestId != activeCheckRequestId) {
                            return@onSuccess
                        }
                        if (!isRunning || project.isDisposed) {
                            return@onSuccess
                        }

                        // 检查焦点是否在 JSON 面板编辑器上
                        // 如果是，则忽略此次检查，避免面板内的文本选择触发更新
                        val currentEditor = com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR.getData(dataContext)
                        if (currentEditor != null && currentEditor == jsonPanelEditor) {
                            return@onSuccess
                        }

                        // 提取单元格值和字体（监控路径禁用动态兜底，降低高频轮询开销）
                        val table = CellValueExtractor.extractTable(dataContext)
                        val checkCount = pollCheckCounter.incrementAndGet()
                        val allowDynamicLookup = shouldUseDynamicLookup(table, checkCount)
                        val cellValue = CellValueExtractor.extractCellValue(dataContext, allowDynamicLookup = allowDynamicLookup)
                        val tableFont = table?.font ?: CellValueExtractor.extractTableFont(dataContext)
                        updatePollingCadence(table != null, cellValue != null)

                        // 如果在轮询模式下且成功获取到表格，尝试切换到事件模式
                        if (monitoredTable == null && tableListener == null) {
                            if (table != null) {
                                setupTableListener(table)
                            }
                        }

                        // 检查是否有变化
                        if (cellValue != null && cellValue != currentCellValue) {
                            // 内容变化，更新记录
                            currentCellValue = cellValue

                            // 在 EDT 中触发回调
                            ApplicationManager.getApplication().invokeLater {
                                if (isRunning && !project.isDisposed) {
                                    try {
                                        onCellChanged(cellValue, tableFont)
                                    } catch (e: Exception) {
                                        // 回调执行失败，忽略
                                    }
                                }
                            }
                        }
                    } finally {
                        completeCheck(requestId)
                    }
                }
                .onError {
                    completeCheck(requestId)
                }
        } catch (e: Exception) {
            // 检查失败，忽略此次检查
            completeCheck(requestId)
        }
    }

    private fun updatePollingCadence(hasTable: Boolean, hasCellValue: Boolean) {
        if (hasTable || hasCellValue) {
            adaptivePollingIntervalMs = pollingInterval
            return
        }
        adaptivePollingIntervalMs = minOf(adaptivePollingIntervalMs + 80L, maxPollingIntervalMs)
    }

    private fun forceResetCheckState() {
        checkInProgress.set(false)
        checkStartedAtMs = 0L
        activeCheckRequestId = -1L
    }

    private fun completeCheck(requestId: Long) {
        if (activeCheckRequestId == requestId) {
            checkInProgress.set(false)
            checkStartedAtMs = 0L
            activeCheckRequestId = -1L
        }
    }

    private fun shouldUseDynamicLookup(table: JTable?, checkCount: Long): Boolean {
        if (table != null || tableListener != null) {
            return false
        }
        // 大多数检查走轻量路径；周期性开启一次动态查找，兼容非常规 DataContext 场景。
        return checkCount % 10L == 0L
    }
}
