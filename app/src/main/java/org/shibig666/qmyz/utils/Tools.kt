package org.shibig666.qmyz.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.regex.Pattern


private object Logger {
    private const val TAG = "QmyzTools"

    fun debug(message: () -> String) = Log.d(TAG, message())
    fun info(message: () -> String) = Log.i(TAG, message())
    fun error(message: () -> String) = Log.e(TAG, message())
}

suspend fun getCookieFromUrl(url: String): String? = withContext(Dispatchers.IO) {
    val client = OkHttpClient()

    // 构建请求
    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    // 尝试获取Cookie
    Logger.info { "向服务器获取Cookie" }
    try {
        // 执行请求
        val response: Response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Logger.error { "请求失败，状态码: ${response.code}" }
            null
        } else {
            // 提取Set-Cookie头
            val setCookieHeader = response.headers["Set-Cookie"] ?: ""
            val regex = Regex("JSESSIONID=([\\w-]+)", RegexOption.IGNORE_CASE)
            val JSESSIONID = regex.find(setCookieHeader)?.groups?.get(1)?.value

            if (JSESSIONID == null) {
                Logger.error { "未找到JSESSIONID" }
                null
            } else {
                Logger.debug { "成功获取Cookie: $JSESSIONID" }
                JSESSIONID
            }
        }
    } catch (e: Exception) {
        Logger.error { "获取Cookie时出现异常: ${e.message}" }
        null
    }
}

suspend fun getCookieFromUrlWithRetry(url: String, retryTimes: Int, retryInterval: Long): String? {
    var JSESSIONID: String? = null
    for (i in 0 until retryTimes) {
        JSESSIONID = getCookieFromUrl(url)
        if (JSESSIONID != null) {
            break
        }
        delay(retryInterval)
    }
    return JSESSIONID
}


suspend fun getCourses(JSESSIONID: String): Map<String, String>? = withContext(Dispatchers.IO) {
    val client = OkHttpClient()

    // 构建请求
    val url = "http://112.5.88.114:31101/yiban-web/stu/toCourse.jhtml"
    val request = Request.Builder()
        .url(url)
        .addHeader(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
        )
        .addHeader("Accept-Language", "en-US,en;q=0.9")
        .addHeader("Cache-Control", "max-age=0")
        .addHeader("Connection", "keep-alive")
        .addHeader("Referer", "http://112.5.88.114:31101/yiban-web/stu/homePage.jhtml")
        .addHeader("Upgrade-Insecure-Requests", "1")
        .addHeader(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        )
        .addHeader("Cookie", "JSESSIONID=$JSESSIONID")
        .build()

    // 执行请求
    Logger.info { "开始获取课程" }
    try {
        val response: Response = client.newCall(request).execute()

        // 检查响应状态
        if (!response.isSuccessful) {
            Logger.error { "请求失败，状态码: ${response.code}" }
            return@withContext null
        }

        // 解析HTML内容
        val responseBody = response.body?.string() ?: return@withContext null
        val pattern = Pattern.compile(
            """href="toSubject\.jhtml\?courseId=(\d+)".*?<div class="mui-media-body".*?>(.*?)</div>""",
            Pattern.DOTALL
        )
        val matcher = pattern.matcher(responseBody)

        val courseDict = mutableMapOf<String, String>()
        while (matcher.find()) {
            val courseId = matcher.group(1)?.trim() ?: continue
            val courseName = matcher.group(2)?.trim() ?: continue
            courseDict[courseId] = courseName
        }

        Logger.info { "成功获取课程列表: $courseDict" }

        courseDict
    } catch (e: Exception) {
        Logger.error { "请求异常类型: ${e.javaClass.name}" }
        Logger.error { "请求异常: ${e.message ?: "无错误信息"}" }
        Logger.error { "异常堆栈: ${e.stackTraceToString()}" }
        null
    }
}

