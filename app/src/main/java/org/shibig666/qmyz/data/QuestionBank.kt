package org.shibig666.qmyz.data

import android.content.Context
import android.util.Log
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.File

private const val TAG = "QuestionBank"

/**
 * 题库类，用于加载和管理题目
 */
class QuestionBank {
    private val questions: MutableList<Question> = mutableListOf()  // 问题列表

    // 构造函数重载支持多种初始化方式
    constructor(context: Context, relativePath: String) {
        val file = File(context.filesDir, relativePath)
        loadFromFile(file)
    }

    constructor(file: File) {
        loadFromFile(file)
    }

    constructor(absolutePath: String) {
        loadFromFile(File(absolutePath))
    }

    // 读取CSV文件加载题库
    private fun loadFromFile(file: File) {
        Log.i(TAG, "开始加载题库: ${file.absolutePath}")

        if (!file.exists()) {
            Log.e(TAG, "题库文件不存在: ${file.absolutePath}")
            return
        }

        try {
            csvReader {
                charset = "UTF-8"
                skipEmptyLine = true
            }.open(file) {
                readAllWithHeaderAsSequence().forEach { row ->
                    try {
                        processRow(row)
                    } catch (e: Exception) {
                        Log.e(TAG, "处理题目行失败: $row", e)
                    }
                }
            }
            Log.i(TAG, "题库加载完成，共 ${questions.size} 个题目")
        } catch (e: Exception) {
            Log.e(TAG, "加载题库失败: ${e.message}", e)
        }
    }

    private fun processRow(row: Map<String, String>) {
        validateRequiredFields(row)

        when (row["subType"]) {
            "单选题" -> addSingleChoice(row)
            else -> Log.w(TAG, "跳过未知题型: ${row["subType"]}")
        }
    }

    // 校验行
    private fun validateRequiredFields(row: Map<String, String>) {
        listOf("courseId", "id", "subType", "subDescript", "answer").forEach { key ->
            require(row.containsKey(key) && !row[key].isNullOrBlank()) {
                "缺少必要字段或字段为空: $key"
            }
        }
    }

    private fun addSingleChoice(row: Map<String, String>) {
        // 类型转换校验
        val courseId = row["courseId"]!!.toIntOrNull()
            ?: throw IllegalArgumentException("无效的课程ID: ${row["courseId"]}")
        val id = row["id"]!!.toIntOrNull()
            ?: throw IllegalArgumentException("无效的题目ID: ${row["id"]}")
        val answerCount = row["optionCount"]!!.toIntOrNull()
            ?: throw IllegalArgumentException("无效的选项数量: ${row["optionCount"]}")

        // 动态获取选项
        val options = (0 until answerCount).mapNotNull { index ->
            row["option$index"]?.takeIf { it.isNotBlank() }
        }

        questions.add(
            SingleChoiceQuestion(
                questionDescription = row["subDescript"]!!,
                id = id,
                courseId = courseId,
                answerCount = answerCount,
                answer = row["answer"]!!,
                options = options
            )
        )
        Log.v(TAG, "添加单选题: ID=$id, 描述=${row["subDescript"]?.take(20)}...")
    }

    // 实用方法
    fun getAll() = questions.toList()
    fun getByCourse(courseId: Int) = questions.filter { it.courseId == courseId }
    fun getById(id: Int) = questions.find { it.id == id }
    fun size() = questions.size
    fun getByDescript(descript: String): Question? = questions.find {
        it.questionDescription == descript
    }

    // 判断是否为空
    fun isEmpty() = questions.isEmpty()
}