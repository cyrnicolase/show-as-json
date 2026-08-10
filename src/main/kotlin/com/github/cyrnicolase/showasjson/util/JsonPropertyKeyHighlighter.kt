package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import java.awt.Font

/**
 * JSON 属性名语义高亮器。
 *
 * 根因说明：
 * 在当前 viewer 链路中，JSON 词法高亮通常将属性名和字符串值都映射为字符串 token，
 * 仅依赖配色 scheme 无法稳定区分 key 与 value。
 *
 * 该高亮器在不改动业务逻辑的前提下，额外对“紧跟冒号的双引号字符串”施加前景色覆盖，
 * 从而稳定实现属性名颜色区分。
 */
internal object JsonPropertyKeyHighlighter {
    private const val MAX_HIGHLIGHT_TEXT_LENGTH = 1_000_000
    private const val MAX_HIGHLIGHT_REGIONS = 1_500
    private val KEY_HIGHLIGHTERS_KEY = Key.create<MutableList<RangeHighlighter>>("SHOW_AS_JSON_KEY_HIGHLIGHTERS")

    fun apply(editor: Editor, jsonText: String) {
        if (!EditorUtils.isEditorValid(editor)) return

        clear(editor)
        if (jsonText.length > MAX_HIGHLIGHT_TEXT_LENGTH) return

        val keyColor = resolvePropertyKeyColor(editor) ?: return
        val keyRanges = findPropertyKeyRanges(jsonText)
        if (keyRanges.isEmpty()) return

        val attrs = TextAttributes().apply {
            foregroundColor = keyColor
            fontType = Font.BOLD
        }

        val created = mutableListOf<RangeHighlighter>()
        for ((start, endExclusive) in keyRanges) {
            val region = runCatching {
                editor.markupModel.addRangeHighlighter(
                    start,
                    endExclusive,
                    HighlighterLayer.SYNTAX + 1,
                    attrs,
                    HighlighterTargetArea.EXACT_RANGE
                )
            }.getOrNull()
            if (region != null) created.add(region)
        }

        if (created.isNotEmpty()) {
            editor.putUserData(KEY_HIGHLIGHTERS_KEY, created)
        }
    }

    fun clear(editor: Editor) {
        val existing = editor.getUserData(KEY_HIGHLIGHTERS_KEY) ?: return
        existing.forEach { runCatching { editor.markupModel.removeHighlighter(it) } }
        existing.clear()
        editor.putUserData(KEY_HIGHLIGHTERS_KEY, null)
    }

    private fun resolvePropertyKeyColor(editor: Editor): java.awt.Color? {
        val scheme = editor.colorsScheme
        val propertyKey = TextAttributesKey.find("JSON.PROPERTY_KEY")
        val attrs = scheme.getAttributes(propertyKey) ?: propertyKey.defaultAttributes
        return attrs?.foregroundColor ?: scheme.defaultForeground
    }

    private fun findPropertyKeyRanges(text: String): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var inString = false
        var escapeNext = false
        var stringStart = -1

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inString) {
                if (escapeNext) {
                    escapeNext = false
                    i++
                    continue
                }
                if (ch == '\\') {
                    escapeNext = true
                    i++
                    continue
                }
                if (ch == '"') {
                    val endExclusive = i + 1
                    if (isPropertyKeyToken(text, endExclusive)) {
                        ranges.add(stringStart to endExclusive)
                        if (ranges.size >= MAX_HIGHLIGHT_REGIONS) {
                            return ranges
                        }
                    }
                    inString = false
                    stringStart = -1
                }
                i++
                continue
            }

            when {
                ch == '"' -> {
                    inString = true
                    stringStart = i
                    i++
                }
                else -> i++
            }
        }
        return ranges
    }

    private fun isPropertyKeyToken(text: String, afterQuoteOffset: Int): Boolean {
        var i = afterQuoteOffset
        while (i < text.length && text[i].isWhitespace()) i++
        return i < text.length && text[i] == ':'
    }

}

