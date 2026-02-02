package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.ColorUtil
import java.awt.Color
import java.awt.Font

/**
 * JSON 配色方案增强工具类
 *
 * 为 JSON 编辑器提供丰富的配色方案，增强不同 JSON 元素的视觉区分度。
 * 支持明亮和暗黑主题自适应。
 *
 * @author cyrnicolase
 */
object JsonColorSchemeUtils {
    
    /**
     * 增强 JSON 配色方案
     *
     * 根据当前主题（明亮/暗黑）为不同的 JSON 元素设置合适的颜色。
     * 直接修改传入的配色方案对象。
     *
     * @param scheme 编辑器配色方案（必须是可修改的副本）
     */
    fun enhanceJsonColors(scheme: EditorColorsScheme) {
        try {
            val isDark = ColorUtil.isDark(scheme.defaultBackground)
            
            // 为不同的 JSON 元素设置颜色
            setJsonPropertyKeyColor(scheme, isDark)
            setJsonStringColor(scheme, isDark)
            setJsonNumberColor(scheme, isDark)
            setJsonKeywordColor(scheme, isDark)
            setJsonBracesColor(scheme, isDark)
            setJsonCommaColor(scheme, isDark)
        } catch (e: Exception) {
            // 如果配色设置失败，使用默认配色，不影响功能
        }
    }
    
    /**
     * 设置 JSON 属性键的颜色
     *
     * 明亮主题：鲜艳蓝色 - 醒目突出
     * 暗黑主题：明亮紫蓝色 - 高对比度
     */
    private fun setJsonPropertyKeyColor(scheme: EditorColorsScheme, isDark: Boolean) {
        val color = if (isDark) {
            Color(180, 140, 255)  // #B48CFF - 明亮紫蓝色
        } else {
            Color(0, 80, 255)      // #0050FF - 鲜艳蓝色
        }
        
        setColorForKeys(scheme, color, Font.BOLD, listOf(
            "JSON:PROPERTY_KEY",
            "JSON.PROPERTY_KEY"
        ))
    }
    
    /**
     * 设置 JSON 字符串的颜色
     *
     * 明亮主题：鲜艳绿色 - 醒目突出
     * 暗黑主题：明亮绿色 - 清晰可见
     */
    private fun setJsonStringColor(scheme: EditorColorsScheme, isDark: Boolean) {
        val color = if (isDark) {
            Color(152, 195, 121)   // #98C379 - 明亮绿色
        } else {
            Color(34, 139, 34)     // #228B22 - 鲜艳绿色
        }
        
        setColorForKeys(scheme, color, Font.PLAIN, listOf(
            "JSON:STRING",
            "JSON.STRING"
        ))
    }
    
    /**
     * 设置 JSON 数字的颜色
     *
     * 明亮主题：明亮青蓝色 - 数值突出
     * 暗黑主题：鲜艳天蓝色 - 高对比度
     */
    private fun setJsonNumberColor(scheme: EditorColorsScheme, isDark: Boolean) {
        val color = if (isDark) {
            Color(86, 182, 194)    // #56B6C2 - 鲜艳天蓝色
        } else {
            Color(0, 119, 170)     // #0077AA - 明亮青蓝色
        }
        
        setColorForKeys(scheme, color, Font.PLAIN, listOf(
            "JSON:NUMBER",
            "JSON.NUMBER"
        ))
    }
    
    /**
     * 设置 JSON 关键字的颜色（true, false, null）
     *
     * 明亮主题：鲜艳紫红色 - 关键字突出
     * 暗黑主题：明亮橙色 - 特殊值醒目
     */
    private fun setJsonKeywordColor(scheme: EditorColorsScheme, isDark: Boolean) {
        val color = if (isDark) {
            Color(229, 192, 123)   // #E5C07B - 明亮金橙色
        } else {
            Color(170, 13, 145)    // #AA0D91 - 鲜艳紫红色
        }
        
        setColorForKeys(scheme, color, Font.BOLD, listOf(
            "JSON:KEYWORD",
            "JSON.KEYWORD",
            "JSON:NULL",
            "JSON.NULL",
            "JSON:BOOLEAN",
            "JSON.BOOLEAN"
        ))
    }
    
    /**
     * 设置 JSON 花括号和方括号的颜色
     *
     * 明亮主题：深灰色 - 结构清晰
     * 暗黑主题：明亮灰色 - 清晰可见
     */
    private fun setJsonBracesColor(scheme: EditorColorsScheme, isDark: Boolean) {
        val color = if (isDark) {
            Color(198, 208, 218)   // #C6D0DA - 明亮灰色
        } else {
            Color(80, 80, 80)      // #505050 - 深灰色
        }
        
        setColorForKeys(scheme, color, Font.PLAIN, listOf(
            "JSON:BRACES",
            "JSON.BRACES",
            "JSON:BRACKETS",
            "JSON.BRACKETS"
        ))
    }
    
    /**
     * 设置 JSON 逗号和冒号的颜色
     *
     * 明亮主题：灰色 - 分隔清晰
     * 暗黑主题：明亮灰色 - 清晰可见
     */
    private fun setJsonCommaColor(scheme: EditorColorsScheme, isDark: Boolean) {
        val color = if (isDark) {
            Color(198, 208, 218)   // #C6D0DA - 明亮灰色
        } else {
            Color(100, 100, 100)   // #646464 - 中灰色
        }
        
        setColorForKeys(scheme, color, Font.PLAIN, listOf(
            "JSON:COMMA",
            "JSON.COMMA",
            "JSON:COLON",
            "JSON.COLON"
        ))
    }
    
    /**
     * 为指定的 TextAttributesKey 设置颜色
     *
     * 使用 TextAttributesKey.createTextAttributesKey 来获取或创建 key，
     * 并直接设置到 scheme 中。这样可以确保配色被正确应用。
     *
     * @param scheme 编辑器颜色方案
     * @param color 前景色
     * @param fontType 字体样式（普通/粗体/斜体）
     * @param keyNames TextAttributesKey 的名称列表
     */
    private fun setColorForKeys(
        scheme: EditorColorsScheme,
        color: Color,
        fontType: Int,
        keyNames: List<String>
    ) {
        for (keyName in keyNames) {
            try {
                // 使用 createTextAttributesKey 获取或创建 key
                // 如果 key 已存在，会返回已存在的实例
                val key = TextAttributesKey.createTextAttributesKey(keyName)
                
                // 创建新的 TextAttributes 或克隆现有的
                val attributes = TextAttributes()
                attributes.foregroundColor = color
                attributes.fontType = fontType
                
                // 设置到 scheme 中
                scheme.setAttributes(key, attributes)
            } catch (e: Exception) {
                // 如果某个 key 设置失败，继续尝试其他的
            }
        }
    }
}
