package org.shibig666.qmyz.auto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import org.shibig666.qmyz.data.QuestionBank
import org.shibig666.qmyz.utils.Decrypt
import org.shibig666.qmyz.utils.KEY_BASE64


private const val TAG = "AutoDo"

/**
 * 题目模型
 */
data class WebQuestion(
    val question: String,
    val type: String,
    val uuid: String,
    val options: List<String>
) {
    override fun toString(): String {
        return "题目: $question\n类型: $type\nUUID: $uuid\n选项: $options"
    }
}

/**
 * 答题结果模型
 */
data class AnswerResult(
    val isCorrect: Boolean,
    val message: String
)

/**
 * 自动答题类
 */
class AutoDo(
    private val questionBank: QuestionBank,
    private val jsessionid: String,
    private val courseId: Int
) {
    private val decrypt = Decrypt(KEY_BASE64)
    private val client = OkHttpClient()
    private val baseUrl = "http://112.5.88.114:31101"
    private var isRunning = false

    /**
     * 获取题目
     */
    suspend fun getQuestion(): WebQuestion? = withContext(Dispatchers.IO) {
        try {
            val headers = createHeaders()
            val data = FormBody.Builder()
                .add("courseId", courseId.toString())
                .build()

            val request = Request.Builder()
                .url("$baseUrl/yiban-web/stu/nextSubject.jhtml")
                .headers(headers)
                .post(data)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "获取题目请求失败: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            val json = Json.parseToJsonElement(responseBody).jsonObject

            val subDescriptEncrypted = json["data"]?.jsonObject?.get("nextSubject")?.jsonObject?.get("subDescript")?.toString()
            if (subDescriptEncrypted.isNullOrBlank()) {
                Log.e(TAG, "题目描述为空")
                return@withContext null
            }

            val question = decrypt.decryptOne(subDescriptEncrypted)
            val type = json["data"]?.jsonObject?.get("nextSubject")?.jsonObject?.get("subType")?.toString()?.replace("\"", "") ?: "未知类型"
            val uuid = json["data"]?.jsonObject?.get("uuid")?.toString()?.replace("\"", "") ?: ""

            val optionCount = json["data"]?.jsonObject?.get("nextSubject")?.jsonObject?.get("optionCount")?.toString()?.toIntOrNull() ?: 0
            val options = mutableListOf<String>()

            for (i in 0 until optionCount) {
                val optionEncrypted = json["data"]?.jsonObject?.get("nextSubject")?.jsonObject?.get("option$i")?.toString()
                if (!optionEncrypted.isNullOrBlank()) {
                    options.add(decrypt.decryptOne(optionEncrypted))
                }
            }

            Log.d(TAG, "获取题目成功: $question")
            WebQuestion(question, type, uuid, options)
        } catch (e: Exception) {
            Log.e(TAG, "获取题目异常: ${e.message}", e)
            null
        }
    }

    /**
     * 查找答案
     */
    fun findAnswer(question: WebQuestion): String? {
        return try {
            val answer = questionBank.getByDescript(question.question)?.answer
            Log.d(TAG, "查找答案: ${answer ?: "未找到"}")
            answer
        } catch (e: Exception) {
            Log.e(TAG, "查找答案异常: ${e.message}", e)
            null
        }
    }

    /**
     * 提交答案
     */
    suspend fun submitAnswer(uuid: String, answer: String): AnswerResult? = withContext(Dispatchers.IO) {
        try {
            val data = FormBody.Builder()
                .add("answer", answer)
                .add("courseId", courseId.toString())
                .add("uuid", uuid)
                .add("deviceUuid", "")
                .build()

            val request = Request.Builder()
                .url("$baseUrl/yiban-web/stu/changeSituation.jhtml")
                .headers(createHeaders())
                .post(data)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "提交答案请求失败: ${response.code}")
                return@withContext null
            }

            val json = Json.parseToJsonElement(response.body?.string() ?: return@withContext null).jsonObject
            val message = json["message"]?.jsonPrimitive?.content ?: ""
            val isCorrect = message == "回答正确！"

            Log.d(TAG, "提交答案结果: $message")
            AnswerResult(isCorrect, message)
        } catch (e: Exception) {
            Log.e(TAG, "提交答案异常: ${e.message}", e)
            null
        }
    }

    /**
     * 创建请求头
     */
    private fun createHeaders(): Headers {
        return Headers.Builder()
            .add("Accept", "application/json")
            .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .add("Cache-Control", "no-cache")
            .add("Connection", "keep-alive")
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("Origin", baseUrl)
            .add("Pragma", "no-cache")
            .add("Cookie", "JSESSIONID=$jsessionid")
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .add("Referer", "$baseUrl/yiban-web/stu/toSubject.jhtml?courseId=$courseId")
            .add("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    /**
     * 停止自动答题
     */
    fun stopAutoAnswer() {
        isRunning = false
    }

    /**
     * 判断是否正在运行
     */
    fun isRunning(): Boolean = isRunning
}