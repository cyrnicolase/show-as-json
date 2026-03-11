package com.github.cyrnicolase.showasjson.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/**
 * JSON 格式化工具类
 *
 * 提供 JSON 字符串的格式化功能，支持美化格式和紧凑格式。
 *
 * @author cyrnicolase
 */
object JsonFormatter {
    private val prettyGson = GsonBuilder()
        .setPrettyPrinting()
        .setLenient()
        .disableHtmlEscaping()
        .create()

    private val compactGson = GsonBuilder()
        .setLenient()
        .disableHtmlEscaping()
        .create()

    /**
     * 美化格式化 JSON 字符串（带缩进换行）。
     * 如果解析失败，返回原始字符串。
     */
    fun formatPretty(jsonString: String): String = format(jsonString, pretty = true)

    /**
     * 紧凑格式化 JSON 字符串（单行无多余空格）。
     * 如果解析失败，返回原始字符串。
     */
    fun formatCompact(jsonString: String): String = format(jsonString, pretty = false)

    private fun format(jsonString: String, pretty: Boolean): String {
        if (jsonString.isBlank()) return jsonString
        return try {
            val element = JsonParser.parseString(jsonString.trim())
            if (pretty) prettyGson.toJson(element) else compactGson.toJson(element)
        } catch (e: Exception) {
            jsonString
        }
    }
}
