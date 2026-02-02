package com.github.cyrnicolase.showasjson.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/**
 * JSON 格式化工具类
 *
 * 提供 JSON 字符串的格式化功能，将压缩的 JSON 字符串格式化为可读的多行格式。
 *
 * @author cyrnicolase
 */
object JsonFormatter {
    /** Gson 实例（缓存，避免重复创建） */
    private val gson = GsonBuilder()
        .setPrettyPrinting()      // 启用美化输出
        .setLenient()              // 允许宽松的 JSON 解析
        .disableHtmlEscaping()     // 禁用 HTML 转义，保持原始字符串（如 base64 中的 =）
        .create()

    /**
     * 格式化 JSON 字符串
     *
     * 将输入的 JSON 字符串解析并格式化为带缩进的多行格式。
     * 如果格式化失败（例如 JSON 格式错误），则返回原始字符串。
     *
     * @param jsonString 原始 JSON 字符串（可以是压缩格式）
     * @return 格式化后的 JSON 字符串，如果格式化失败则返回原始字符串
     */
    fun format(jsonString: String): String {
        if (jsonString.isBlank()) {
            return jsonString
        }

        return try {
            val trimmed = jsonString.trim()
            val jsonElement = JsonParser.parseString(trimmed)
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            // 如果解析失败，返回原始字符串
            jsonString
        }
    }
}
