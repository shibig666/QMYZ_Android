package org.shibig666.qmyz.data

class SingleChoiceQuestion(
    questionDescription: String,
    id: Int,
    courseId: Int,
    answerCount: Int,
    answer: String,
    private val options: List<String>
) : Question(questionDescription, id, "单选题",courseId, answerCount, answer) {
    init {
        require(options.isNotEmpty()) { "选项不能为空" }
        require(options.size == answerCount) {
            "选项数量(${options.size})与声明数($answerCount)不符"
        }
    }

    fun getOption(index: Int): String = options.getOrElse(index) {
        throw IndexOutOfBoundsException("选项索引越界: $index")
    }

    fun getAllOptions(): List<String> = options.toList()
}
