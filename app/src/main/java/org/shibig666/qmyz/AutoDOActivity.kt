package org.shibig666.qmyz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import kotlinx.coroutines.*
import org.shibig666.qmyz.auto.AutoDo
import org.shibig666.qmyz.auto.AnswerResult
import org.shibig666.qmyz.auto.WebQuestion
import org.shibig666.qmyz.data.QuestionBank
import org.shibig666.qmyz.ui.theme.QmyzTheme
import kotlin.div

class AutoDOActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val jsessionId = intent.getStringExtra("JSESSIONID") ?: ""
        val userName = intent.getStringExtra("userName") ?: ""
        val courseId = intent.getStringExtra("courseId") ?: ""
        val courseName = intent.getStringExtra("courseName") ?: ""
        val questionCount = intent.getIntExtra("questionCount", 10)
        val answerDelay = intent.getIntExtra("answerDelay", 5)

        setContent {
            QmyzTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val factory = remember {
                        AutoAnswerViewModelFactory(
                            applicationContext,
                            jsessionId,
                            courseId.toInt(),
                            questionCount,
                            answerDelay * 1000L
                        )
                    }

                    val viewModel: AutoAnswerViewModel = viewModel(factory = factory)

                    AutoAnswerScreen(
                        courseId = courseId,
                        courseName = courseName,
                        questionCount = questionCount,
                        answerDelay = answerDelay,
                        viewModel = viewModel,
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }
}

class AutoAnswerViewModel(
    context: android.content.Context,
    private val jsessionId: String,
    private val courseId: Int,
    private val questionCount: Int,
    private val delayMillis: Long
) : ViewModel() {
    // 界面状态
    private val _uiState = mutableStateOf(AutoAnswerUIState())
    val uiState: State<AutoAnswerUIState> = _uiState

    // 题库和自动答题
    private val questionBank: QuestionBank
    private val autoDo: AutoDo
    private var job: Job? = null

    init {
        // 初始化题库
        val bankDirectory = File(context.filesDir, "bank")
        val bankFile = File(bankDirectory, "$courseId.csv")
        questionBank = QuestionBank(bankFile)

        // 初始化自动答题类
        autoDo = AutoDo(questionBank, jsessionId, courseId)
    }

    // 开始自动答题
    fun startAutoAnswer() {
        if (job != null && job?.isActive == true) return

        val currentAnswerCount = _uiState.value.answerCount
        _uiState.value = _uiState.value.copy(
            isRunning = true,
            logs = emptyList(),
            answerCount = currentAnswerCount
        )

        job = viewModelScope.launch(exceptionHandler) {
            val maxCount = if (questionCount == 0) Int.MAX_VALUE else questionCount
            var count = 0

            try {
                while (isActive && count < maxCount) {
                    try {
                        // 获取题目
                        addLog("正在获取题目...")
                        val question = autoDo.getQuestion()

                        if (question == null) {
                            addLog("获取题目失败，正在重试...")
                            delay(delayMillis)
                            continue
                        }

                        // 检查题目内容是否包含"防刷"或"刷题"
                        if (question.question.contains("防刷") || question.question.contains("刷题")) {
                            addLog("发现防刷题，自动跳过")
                            _uiState.value = _uiState.value.copy(
                                currentQuestion = question,
                                foundAnswer = null,
                                selectedAnswer = null,
                                answerResult = null
                            )
                            delay(delayMillis) // 延迟一段时间再继续
                            continue
                        }

                        // 更新UI状态
                        _uiState.value = _uiState.value.copy(
                            currentQuestion = question,
                            selectedAnswer = null,
                            answerResult = null
                        )

                        // 查找答案
                        val answer = autoDo.findAnswer(question)
                        if (answer == null) {
                            addLog("未找到答案：${question.question}")
                            _uiState.value = _uiState.value.copy(foundAnswer = null)
                        } else {
                            addLog("找到答案：$answer")
                            _uiState.value = _uiState.value.copy(foundAnswer = answer)

                            // 提交答案
                            val result = autoDo.submitAnswer(question.uuid, answer)
                            if (result != null) {
                                _uiState.value = _uiState.value.copy(
                                    selectedAnswer = answer,
                                    answerResult = result,
                                    answerCount = _uiState.value.answerCount + 1
                                )

                                addLog("提交答案结果: ${result.message}")

                                if (result.isCorrect) {
                                    addLog("回答正确")
                                } else {
                                    addLog("回答错误")
                                }

                                count++
                            } else {
                                addLog("提交答案失败，稍后重试")
                                delay(delayMillis / 2)
                            }
                        }

                        delay(delayMillis)
                    } catch (e: CancellationException) {
                        throw e // 重新抛出取消异常
                    } catch (e: Exception) {
                        addLog("处理中发生错误: ${e.message}")
                        delay(delayMillis)
                    }
                }
            } catch (e: CancellationException) {
                // 正常取消
                addLog("自动答题已暂停")
            } finally {
                // 确保状态更新
                _uiState.value = _uiState.value.copy(isRunning = false)
            }
        }
    }

    // 停止自动答题
    fun stopAutoAnswer() {
        job?.cancel()
        addLog("停止中...")
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        viewModelScope.launch {
            addLog("发生错误: ${throwable.message ?: "未知错误"}")
            _uiState.value = _uiState.value.copy(isRunning = false)
        }
    }

    // 添加日志
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message"

        _uiState.value = _uiState.value.copy(
            logs = _uiState.value.logs + logEntry
        )
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
    }
}

data class AutoAnswerUIState(
    val isRunning: Boolean = false,
    val currentQuestion: WebQuestion? = null,
    val foundAnswer: String? = null,
    val selectedAnswer: String? = null,
    val answerResult: AnswerResult? = null,
    val answerCount: Int = 0,
    val logs: List<String> = emptyList()
)

class AutoAnswerViewModelFactory(
    private val context: android.content.Context,
    private val jsessionId: String,
    private val courseId: Int,
    private val questionCount: Int,
    private val delayMillis: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AutoAnswerViewModel::class.java)) {
            return AutoAnswerViewModel(
                context,
                jsessionId,
                courseId,
                questionCount,
                delayMillis
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoAnswerScreen(
    courseId: String,
    courseName: String,
    questionCount: Int,
    answerDelay: Int,
    viewModel: AutoAnswerViewModel,
    onBackPressed: () -> Unit
) {
    val uiState by viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动答题 - $courseName") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.isRunning) {
                        IconButton(onClick = { viewModel.stopAutoAnswer() }) {
                            Icon(Icons.Default.Pause, contentDescription = "暂停")
                        }
                    } else {
                        IconButton(onClick = { viewModel.startAutoAnswer() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "开始")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 设置信息卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "答题设置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("课程ID: $courseId")
                    Text("答题数量: ${if (questionCount == 0) "无限" else questionCount}")
                    Text("答题延迟: ${answerDelay}秒")
                    Text("已答题数: ${uiState.answerCount}")
                }
            }

            // 当前题目卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (uiState.currentQuestion == null) {
                        Text(
                            text = "等待题目...",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val currentQuestion = uiState.currentQuestion
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "当前题目",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            currentQuestion?.let {
                                Text(
                                    text = it.question,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 选项列表
                            if (currentQuestion != null) {
                                currentQuestion.options.forEachIndexed { index, option ->
                                    val optionLetter = ('A' + index).toString()
                                    val isSelected = option == uiState.selectedAnswer
                                    val isCorrect = option == uiState.foundAnswer && uiState.answerResult?.isCorrect == true

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$optionLetter. ",
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isCorrect -> Color.Green
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = option,
                                            color = when {
                                                isCorrect -> Color.Green
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )

                                        if (isCorrect) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "正确",
                                                tint = Color.Green
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 答案区域
                            if (uiState.foundAnswer != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "找到答案: ",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(text = uiState.foundAnswer!!)
                                }

                                // 答题结果
                                uiState.answerResult?.let { result ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "提交结果: ",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = if (result.isCorrect)
                                                Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = "结果",
                                            tint = if (result.isCorrect) Color.Green else Color.Red
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = result.message,
                                            color = if (result.isCorrect) Color.Green else Color.Red
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "题库中没有找到答案",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 日志卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "操作日志",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val logsListState =  rememberLazyListState()

                    LaunchedEffect(uiState.logs.size) {
                        if (uiState.logs.isNotEmpty()) {
                            logsListState.animateScrollToItem(uiState.logs.size - 1)
                        }
                    }

                    LazyColumn(
                        state = logsListState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        if (uiState.isRunning) {
                            viewModel.stopAutoAnswer()
                        } else {
                            viewModel.startAutoAnswer()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isRunning)
                            Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (uiState.isRunning) "暂停" else "开始")
                }

                OutlinedButton(
                    onClick = onBackPressed,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("返回")
                }
            }
        }
    }
}