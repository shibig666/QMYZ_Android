package org.shibig666.qmyz.data

/**
 * 表示一个通用题目类型.
 *
 * @property questionDescription 题目.
 * @property id 题目的唯一标识符.
 * @property type 题目的类型.
 * @property courseId 题目所属课程的ID.
 * @property answerCount 题目的选项数量.
 * @property answer 题目的答案.
 */
open class Question(
    val questionDescription: String,    // 题目
    val id: Int,
    val type: String,   // 题目类型
    val courseId: Int,  // 课程ID
    val answerCount: Int,   // 答案数量
    val answer: String  // 答案
) {
    /**
     * 返回问题的字符串表示.
     *
     * @return 格式为 "type [id]: questionDescription" 的字符串.
     */
    override fun toString(): String {
        return "$type [$id]: $questionDescription"
    }
}

