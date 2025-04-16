package org.shibig666.qmyz.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import androidx.core.content.edit


private const val TAG = "UpdateQuestionBank"

// 定义更新状态数据类
data class UpdateStatus(
    val isUpdating: Boolean = true,
    val message: String = "正在检查题库更新...",
    val progress: Float = 0f,
    val details: List<String> = emptyList()
)

// 在应用启动时检查更新
@Composable
fun CheckForBankUpdates(
    context: Context,
    onUpdateComplete: (Boolean) -> Unit = {},
    onStatusUpdate: (UpdateStatus) -> Unit = {}
) {
    var isChecking by remember { mutableStateOf(false) }
    val statusDetails = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        if (!isChecking) {
            isChecking = true
            onStatusUpdate(UpdateStatus(isUpdating = true, message = "正在检查题库更新...", progress = 0.05f))

            val result = withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences("qmyz_settings", Context.MODE_PRIVATE)
                val previousVersion = prefs.getInt("bank_version", 0)
                val mirrorLink = prefs.getString("mirror_link", null)

                val updated = updateBank(
                    context,
                    previousVersion,
                    mirrorLink
                ) { status, detail ->
                    withContext(Dispatchers.Main) {
                        if (detail != null) {
                            statusDetails.add(detail)
                        }

                        val progress = when {
                            status.contains("%") -> {
                                try {
                                    status.substringBefore("%").trim().toFloatOrNull()?.div(100f) ?: 0.1f
                                } catch (e: Exception) {
                                    0.1f
                                }
                            }
                            status.contains("正在获取题库信息") -> 0.1f
                            status.contains("正在连接服务器") -> 0.15f
                            status.contains("解析题库信息") -> 0.3f
                            status.contains("安装题库文件") -> 0.9f
                            status.contains("题库更新完成") -> 1f
                            else -> 0.3f
                        }

                        onStatusUpdate(UpdateStatus(
                            isUpdating = true,
                            message = status,
                            progress = progress,
                            details = statusDetails.toList()
                        ))
                    }
                }

                if (updated.first) {
                    prefs.edit() { putInt("bank_version", updated.second) }
                }

                updated.first
            }

            onStatusUpdate(UpdateStatus(
                isUpdating = false,
                message = if (result) "题库更新完成" else "题库已是最新版本",
                progress = 1f,
                details = statusDetails.toList()
            ))

            isChecking = false
            onUpdateComplete(result)
        }
    }
}

// 更新题库
suspend fun updateBank(
    context: Context,
    previousVersion: Int,
    mirrorLink: String?,
    onProgressUpdate: suspend (String, String?) -> Unit
): Pair<Boolean, Int> {
    return withContext(Dispatchers.IO) {
        try {
            onProgressUpdate("正在获取题库信息...", null)

            val url = "https://raw.githubusercontent.com/shibig666/QMYZ/refs/heads/master/qmyz/"
            var infoUrl = url + "info.json"

            if (!mirrorLink.isNullOrBlank()) {
                infoUrl = mirrorLink + url + "info.json"
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(infoUrl)
                .get()
                .build()

            onProgressUpdate("正在连接服务器...", null)

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "请求失败，状态码: ${response.code}"
                Log.e(TAG, errorMsg)
                onProgressUpdate(errorMsg, errorMsg)
                return@withContext Pair(false, previousVersion)
            }

            val json = response.body?.string()
            if (json == null) {
                val errorMsg = "获取题库信息失败"
                Log.e(TAG, errorMsg)
                onProgressUpdate(errorMsg, errorMsg)
                return@withContext Pair(false, previousVersion)
            }

            onProgressUpdate("解析题库信息...", null)

            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val version = jsonObject["version"]?.jsonPrimitive?.content?.toInt()
            if (version == null) {
                val errorMsg = "解析版本信息失败"
                Log.e(TAG, errorMsg)
                onProgressUpdate(errorMsg, errorMsg)
                return@withContext Pair(false, previousVersion)
            }

            if (version <= previousVersion) {
                val msg = "题库已是最新版本 (v$version)"
                Log.i(TAG, msg)
                onProgressUpdate(msg, msg)
                return@withContext Pair(true, previousVersion)
            }

            val versionMsg = "获取到最新题库版本: $version (当前版本: $previousVersion)"
            Log.i(TAG, versionMsg)
            onProgressUpdate(versionMsg, versionMsg)

            val bankList: List<String> = jsonObject["data"]?.jsonArray?.map {
                it.jsonPrimitive.content
            } ?: emptyList()

            // 在应用内部缓存目录创建临时目录
            val tempDir = File(context.cacheDir, "qmyz_bank_update")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            var allDownloaded = true

            onProgressUpdate("开始下载题库文件...", "共需下载 ${bankList.size} 个题库文件")

            // 下载每个题库文件
            bankList.forEachIndexed { index, bankId ->
                val progress = ((index + 1) * 100 / bankList.size)
                onProgressUpdate("下载进度: $progress% (${index + 1}/${bankList.size})", null)

                val downloadUrl = if (mirrorLink.isNullOrBlank()) {
                    "$url$bankId.csv"
                } else {
                    "$mirrorLink$url$bankId.csv"
                }

                if (!downloadBank(downloadUrl, tempDir, bankId) { msg ->
                        onProgressUpdate(msg, "$bankId: $msg")
                    }) {
                    allDownloaded = false
                    onProgressUpdate("下载失败，取消更新", "题库 $bankId 下载失败")
                    return@withContext Pair(false, previousVersion)
                }
            }

            if (allDownloaded) {
                // 将临时文件移动到应用内部文件目录
                onProgressUpdate("正在安装题库文件...", null)
                moveTempFilesToBank(context, tempDir, bankList) { msg ->
                    onProgressUpdate(msg, msg)
                }
                onProgressUpdate("题库更新完成 (v$version)", "题库已更新至版本 $version")
                return@withContext Pair(true, version)
            }

            return@withContext Pair(false, previousVersion)
        } catch (e: Exception) {
            val errorMsg = "更新题库时发生异常: ${e.message}"
            Log.e(TAG, errorMsg, e)
            onProgressUpdate("更新失败: ${e.message}", errorMsg)
            return@withContext Pair(false, previousVersion)
        }
    }
}

// 下载单个题库文件
private suspend fun downloadBank(
    url: String,
    tempDir: File,
    bankId: String,
    onProgressUpdate: suspend (String) -> Unit
): Boolean {
    onProgressUpdate("正在下载题库: $bankId")
    val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "下载题库 $bankId 失败，状态码: ${response.code}")
            onProgressUpdate("下载失败，状态码: ${response.code}")
            return false
        }

        val content = response.body?.string()
        if (content.isNullOrEmpty()) {
            Log.e(TAG, "下载题库 $bankId 内容为空")
            onProgressUpdate("下载内容为空")
            return false
        }

        // 保存到临时目录
        val tempFile = File(tempDir, "$bankId.csv")
        tempFile.writeText(content)

        val successMsg = "下载题库 $bankId 成功 (${content.length} 字节)"
        Log.i(TAG, successMsg)
        onProgressUpdate(successMsg)
        return true
    } catch (e: Exception) {
        Log.e(TAG, "下载题库 $bankId 异常: ${e.message}", e)
        onProgressUpdate("下载异常: ${e.message}")
        return false
    }
}

// 将临时文件移动到正式目录
private suspend fun moveTempFilesToBank(
    context: Context,
    tempDir: File,
    bankIds: List<String>,
    onProgressUpdate: suspend (String) -> Unit
) {
    // 使用应用内部文件存储
    val bankDir = File(context.filesDir, "bank")
    if (!bankDir.exists()) bankDir.mkdirs()

    for (bankId in bankIds) {
        val tempFile = File(tempDir, "$bankId.csv")
        val targetFile = File(bankDir, "$bankId.csv")
        tempFile.copyTo(targetFile, overwrite = true)
        onProgressUpdate("题库 $bankId 已安装")
    }

    // 清理临时目录
    tempDir.deleteRecursively()
    onProgressUpdate("题库安装完成")
}