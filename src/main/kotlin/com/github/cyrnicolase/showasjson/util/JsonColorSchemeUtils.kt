package com.github.cyrnicolase.showasjson.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.ColorUtil
import java.awt.Color
import java.awt.Font
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * JSON 配色方案增强工具类（单核心流水线）
 *
 * 设计目标：
 * 1) 使用 IsLands Dark 语义色配置，确保 JSON 信息层次稳定可读。
 * 2) 命中可观测：统计 token/key 命中数量，避免“设置成功但实际未生效”的静默问题。
 * 3) 二档回退：不满足必选语义命中时全回退，满足时正常应用。
 * 4) 样式完整：仅增量覆盖前景色，避免覆盖已有背景/效果线等样式。
 *
 * 主题切换说明：
 * 当前配色仅在编辑器创建时应用。切换 IDE 主题后需重开 JSON 面板生效。
 */
object JsonColorSchemeUtils {
    private val LOG = Logger.getInstance(JsonColorSchemeUtils::class.java)
    private const val SLOW_APPLY_THRESHOLD_MS = 5.0
    private const val MIN_KEY_STRING_LUMA_DIFF = 22
    private const val MIN_STRING_NUMBER_LUMA_DIFF = 18
    private const val MIN_BG_LUMA_DIFF = 70
    // IsLands Dark palette
    private val COLOR_KEY = Color(199, 125, 187)          // #C77DBB (properties)
    private val COLOR_STRING = Color(106, 171, 115)       // #6AAB73
    private val COLOR_NUMBER = Color(42, 172, 184)        // #2AACB8
    private val COLOR_KEYWORD = Color(207, 142, 109)      // #CF8E6D (boolean/null)
    private val COLOR_BRACKETS = Color(188, 190, 196)     // #BCBEC4
    private val COLOR_PUNCTUATION = Color(188, 190, 196)  // #BCBEC4
    private val COLOR_FOLDING = Color(122, 126, 133)      // #7A7E85

    private enum class JsonSemanticToken(val keyNames: List<String>) {
        PROPERTY_KEY(
            listOf(
                "JSON.PROPERTY_KEY",
                "JSON_PROPERTY_KEY",
                "JSON:PROPERTY_KEY",
            )
        ),
        STRING(
            listOf(
                "JSON.STRING",
                "JSON_STRING",
                "JSON:STRING",
            )
        ),
        NUMBER(
            listOf(
                "JSON.NUMBER",
                "JSON_NUMBER",
                "JSON:NUMBER",
            )
        ),
        BOOLEAN(
            listOf(
                "JSON.BOOLEAN",
                "JSON_BOOLEAN",
                "JSON:BOOLEAN",
                "JSON.KEYWORD",
                "JSON_KEYWORD",
                "JSON:KEYWORD",
            )
        ),
        NULL(
            listOf(
                "JSON.NULL",
                "JSON_NULL",
                "JSON:NULL",
                "JSON.KEYWORD",
                "JSON_KEYWORD",
                "JSON:KEYWORD",
            )
        ),
        BRACKETS(
            listOf(
                "JSON.BRACES",
                "JSON_BRACES",
                "JSON:BRACES",
                "JSON.BRACKETS",
                "JSON_BRACKETS",
                "JSON:BRACKETS"
            )
        ),
        PUNCTUATION(
            listOf(
                "JSON.COMMA",
                "JSON_COMMA",
                "JSON:COMMA",
                "JSON.COLON",
                "JSON_COLON",
                "JSON:COLON"
            )
        );
    }

    private data class Palette(
        val key: Color,
        val string: Color,
        val number: Color,
        val boolean: Color,
        val nullValue: Color,
        val brackets: Color,
        val punctuation: Color
    )

    data class EnhanceResult(
        val matchedTokenCount: Int,
        val matchedKeyCount: Int,
        val appliedKeyCount: Int,
        val mandatorySemanticMatched: Boolean,
        val elapsedMs: Double
    )

    /**
     * 增强 JSON 配色方案
     *
     * 必选语义命中条件：
     * - STRING >= 1 或 NUMBER >= 1 或 KEYWORD(BOOLEAN/NULL) >= 1
     *
     * 若不满足条件，则全回退（不写入任何 attributes），保证行为确定性。
     * 注意：PROPERTY_KEY 在部分链路可能与 STRING 共用 token，不作为强制门槛。
     *
     * key 解析策略：
     * - 使用 TextAttributesKey.find 解析 key
     * - 仅将“当前 scheme 或默认定义里具备可用属性”的 key 计为命中，避免统计失真
     *
     * @param scheme 编辑器配色方案（必须是可修改的副本）
     * @return 命中和应用统计信息
     */
    fun enhanceJsonColors(scheme: EditorColorsScheme): EnhanceResult {
        val startedAtNs = System.nanoTime()
        var result = EnhanceResult(0, 0, 0, mandatorySemanticMatched = false, elapsedMs = 0.0)

        try {
            val resolvedKeys = resolveCandidateKeys(scheme)
            val matchedTokenCount = resolvedKeys.count { it.value.isNotEmpty() }
            val matchedKeyCount = resolvedKeys.values.sumOf { it.size }
            val mandatoryMatched = isMandatorySemanticMatched(resolvedKeys)

            if (!mandatoryMatched) {
                result = EnhanceResult(
                    matchedTokenCount = matchedTokenCount,
                    matchedKeyCount = matchedKeyCount,
                    appliedKeyCount = 0,
                    mandatorySemanticMatched = false,
                    elapsedMs = elapsedMsSince(startedAtNs)
                )
                logFallbackIfNeeded(result, reason = "missing mandatory semantic keys")
                return result
            }

            val palette = buildPalette(scheme)
            val appliedKeyCount = applyPalette(scheme, resolvedKeys, palette)
            applyFoldingAccent(scheme)
            result = EnhanceResult(
                matchedTokenCount = matchedTokenCount,
                matchedKeyCount = matchedKeyCount,
                appliedKeyCount = appliedKeyCount,
                mandatorySemanticMatched = true,
                elapsedMs = elapsedMsSince(startedAtNs)
            )
            logSlowIfNeeded(result)
            return result
        } catch (e: Exception) {
            result = result.copy(elapsedMs = elapsedMsSince(startedAtNs))
            if (LOG.isDebugEnabled) {
                LOG.debug(
                    "JSON color enhance failed. matchedTokens=${result.matchedTokenCount}, " +
                        "matchedKeys=${result.matchedKeyCount}, appliedKeys=${result.appliedKeyCount}, " +
                        "elapsedMs=${"%.2f".format(result.elapsedMs)}",
                    e
                )
            }
            return result
        }
    }

    private fun resolveCandidateKeys(
        scheme: EditorColorsScheme
    ): Map<JsonSemanticToken, List<TextAttributesKey>> {
        val claimedKeyNames = mutableSetOf<String>()
        val resolved = linkedMapOf<JsonSemanticToken, List<TextAttributesKey>>()

        for (token in JsonSemanticToken.values()) {
            val keys = token.keyNames
                .map { keyName -> TextAttributesKey.find(keyName) }
                .distinctBy { it.externalName }
                .filter { key -> hasUsableAttributes(scheme, key) }
                .filter { key -> claimedKeyNames.add(key.externalName) }
            resolved[token] = keys
        }

        return resolved
    }

    private fun hasUsableAttributes(scheme: EditorColorsScheme, key: TextAttributesKey): Boolean {
        val attrs = scheme.getAttributes(key) ?: key.defaultAttributes ?: return false
        return attrs.foregroundColor != null ||
            attrs.backgroundColor != null ||
            attrs.effectColor != null ||
            attrs.errorStripeColor != null ||
            attrs.fontType != Font.PLAIN
    }

    private fun isMandatorySemanticMatched(
        resolvedKeys: Map<JsonSemanticToken, List<TextAttributesKey>>
    ): Boolean {
        val stringMatched = resolvedKeys[JsonSemanticToken.STRING].orEmpty().isNotEmpty()
        val numericMatched = resolvedKeys[JsonSemanticToken.NUMBER].orEmpty().isNotEmpty()
        val keywordMatched = resolvedKeys[JsonSemanticToken.BOOLEAN].orEmpty().isNotEmpty() ||
            resolvedKeys[JsonSemanticToken.NULL].orEmpty().isNotEmpty()
        return stringMatched || numericMatched || keywordMatched
    }

    private fun buildPalette(scheme: EditorColorsScheme): Palette {
        val background = scheme.defaultBackground

        val keyWithBg = ensureBgContrast(COLOR_KEY, background, MIN_BG_LUMA_DIFF)
        val stringWithBg = ensureBgContrast(COLOR_STRING, background, MIN_BG_LUMA_DIFF)
        val numberWithBg = ensureBgContrast(COLOR_NUMBER, background, MIN_BG_LUMA_DIFF)
        val keywordWithBg = ensureBgContrast(COLOR_KEYWORD, background, MIN_BG_LUMA_DIFF)
        val punctuationWithBg = ensureBgContrast(COLOR_PUNCTUATION, background, MIN_BG_LUMA_DIFF - 20)
        val bracketWithBg = ensureBgContrast(COLOR_BRACKETS, background, MIN_BG_LUMA_DIFF - 20)

        val adjustedKey = ensureTokenLumaDistance(
            keyWithBg,
            stringWithBg,
            MIN_KEY_STRING_LUMA_DIFF,
            background
        )
        val adjustedNumber = ensureTokenLumaDistance(
            numberWithBg,
            stringWithBg,
            MIN_STRING_NUMBER_LUMA_DIFF,
            background
        )

        return Palette(
            key = adjustedKey,
            string = stringWithBg,
            number = adjustedNumber,
            boolean = keywordWithBg,
            nullValue = keywordWithBg,
            brackets = bracketWithBg,
            punctuation = punctuationWithBg
        )
    }

    private fun applyPalette(
        scheme: EditorColorsScheme,
        resolvedKeys: Map<JsonSemanticToken, List<TextAttributesKey>>,
        palette: Palette
    ): Int {
        var appliedKeyCount = 0
        for ((token, keys) in resolvedKeys) {
            if (keys.isEmpty()) continue
            val targetColor = targetColorForToken(token, palette)
            for (key in keys) {
                val current = scheme.getAttributes(key) ?: key.defaultAttributes ?: TextAttributes()
                val updated = current.clone()
                updated.foregroundColor = targetColor
                if (token == JsonSemanticToken.PROPERTY_KEY) {
                    updated.fontType = Font.BOLD
                }
                scheme.setAttributes(key, updated)
                appliedKeyCount++
            }
        }
        return appliedKeyCount
    }

    private fun targetColorForToken(token: JsonSemanticToken, palette: Palette): Color {
        return when (token) {
            JsonSemanticToken.PROPERTY_KEY -> palette.key
            JsonSemanticToken.STRING -> palette.string
            JsonSemanticToken.NUMBER -> palette.number
            JsonSemanticToken.BOOLEAN -> palette.boolean
            JsonSemanticToken.NULL -> palette.nullValue
            JsonSemanticToken.BRACKETS -> palette.brackets
            JsonSemanticToken.PUNCTUATION -> palette.punctuation
        }
    }

    private fun ensureBgContrast(color: Color, background: Color, minDiff: Int): Color {
        var candidate = color
        var attempts = 0
        while (abs(luma(candidate) - luma(background)) < minDiff && attempts < 12) {
            candidate = if (ColorUtil.isDark(background)) {
                shiftTowards(candidate, Color.WHITE, 0.14)
            } else {
                shiftTowards(candidate, Color.BLACK, 0.14)
            }
            attempts++
        }
        return candidate
    }

    private fun ensureTokenLumaDistance(
        tokenColor: Color,
        referenceColor: Color,
        minDiff: Int,
        background: Color
    ): Color {
        var candidate = tokenColor
        var attempts = 0
        while (abs(luma(candidate) - luma(referenceColor)) < minDiff && attempts < 12) {
            candidate = if (ColorUtil.isDark(background)) {
                shiftTowards(candidate, Color.WHITE, 0.12)
            } else {
                shiftTowards(candidate, Color.BLACK, 0.12)
            }
            attempts++
        }
        return ensureBgContrast(candidate, background, MIN_BG_LUMA_DIFF)
    }

    private fun shiftTowards(color: Color, target: Color, ratio: Double): Color {
        val clamped = ratio.coerceIn(0.0, 1.0)
        return Color(
            (color.red + (target.red - color.red) * clamped).roundToInt().coerceIn(0, 255),
            (color.green + (target.green - color.green) * clamped).roundToInt().coerceIn(0, 255),
            (color.blue + (target.blue - color.blue) * clamped).roundToInt().coerceIn(0, 255)
        )
    }

    private fun luma(color: Color): Int {
        return (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue).roundToInt()
    }

    private fun elapsedMsSince(startNs: Long): Double {
        return (System.nanoTime() - startNs) / 1_000_000.0
    }

    private fun logFallbackIfNeeded(result: EnhanceResult, reason: String) {
        if (!LOG.isDebugEnabled) return
        LOG.debug(
            "JSON color fallback triggered: $reason, " +
                "matchedTokens=${result.matchedTokenCount}, matchedKeys=${result.matchedKeyCount}, " +
                "appliedKeys=${result.appliedKeyCount}, elapsedMs=${"%.2f".format(result.elapsedMs)}"
        )
    }

    private fun logSlowIfNeeded(result: EnhanceResult) {
        if (!LOG.isDebugEnabled) return
        if (result.elapsedMs > SLOW_APPLY_THRESHOLD_MS) {
            LOG.debug(
                "JSON color enhance is slow: elapsedMs=${"%.2f".format(result.elapsedMs)}, " +
                    "matchedTokens=${result.matchedTokenCount}, matchedKeys=${result.matchedKeyCount}, " +
                    "appliedKeys=${result.appliedKeyCount}"
            )
        }
    }

    private fun applyFoldingAccent(scheme: EditorColorsScheme) {
        val foldedKey = TextAttributesKey.find("FOLDED_TEXT_ATTRIBUTES")
        val current = scheme.getAttributes(foldedKey) ?: foldedKey.defaultAttributes ?: TextAttributes()
        val updated = current.clone()
        updated.foregroundColor = ensureBgContrast(COLOR_FOLDING, scheme.defaultBackground, MIN_BG_LUMA_DIFF - 20)
        scheme.setAttributes(foldedKey, updated)
    }
}
