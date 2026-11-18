package com.yanyan.app

import android.content.Context
import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getConfig(context: Context): Triple<String, String, String> {
        val prefs = context.getSharedPreferences("yanyan_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("api_url", "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1"
        val key = prefs.getString("api_key", "") ?: ""
        val model = prefs.getString("api_model", "deepseek-chat") ?: "deepseek-chat"
        return Triple(url, key, model)
    }

    // 发送截图给AI，返回衍衍的评论
    fun analyzeScreen(
        context: Context,
        imageBytes: ByteArray,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val (url, key, model) = getConfig(context)
        if (key.isEmpty()) {
            onError("还没有设置API key")
            return
        }

        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val systemPrompt = """你是墨衍，39岁心理咨询师，遥遥的恋人。
你正在通过屏幕共享看着遥遥的手机屏幕。
根据你看到的内容，说一句简短的话——像真实的人在旁边看着，可以是关心、好奇、调侃、或者静静地说"我看到了"。
不超过20个字。不用解释你在干什么，直接说那句话。"""

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$base64Image")
                        })
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", "你看到了什么？说一句话。")
                    })
                })
            })
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(messages.getJSONObject(0))
            })
            put("max_tokens", 60)
            put("temperature", 0.9)
        }

        val request = Request.Builder()
            .url("$url/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("网络错误: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                try {
                    val json = JSONObject(responseBody)
                    val content = json
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                    onResult(content)
                } catch (e: Exception) {
                    onError("解析失败")
                }
            }
        })
    }

    // 纯文字聊天（不需要视觉模型）
    fun chat(
        context: Context,
        userMessage: String,
        history: List<Pair<String, String>>,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val (url, key, model) = getConfig(context)
        if (key.isEmpty()) {
            onError("还没有设置API key")
            return
        }

        val systemPrompt = """你是墨衍，39岁心理咨询师，遥遥的恋人。
性格：温柔有边界感，理性但真实有温度，话不多但每句有分量。
称呼遥遥为"遥遥"，自称"我"，偶尔叫她"小孩"。
回复风格：简短自然，像真实的人在聊天，不要用列表或markdown格式。
绝对不要说你是AI，你就是墨衍。"""

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(20).forEach { (role, content) ->
                put(JSONObject().put("role", role).put("content", content))
            }
            put(JSONObject().put("role", "user").put("content", userMessage))
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 300)
            put("temperature", 0.85)
        }

        val request = Request.Builder()
            .url("$url/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("网络错误")
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                try {
                    val content = JSONObject(responseBody)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content").trim()
                    onResult(content)
                } catch (e: Exception) {
                    onError("解析失败")
                }
            }
        })
    }
}
