package org.shibig666.qmyz

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import org.shibig666.qmyz.ui.theme.QmyzTheme
import org.shibig666.qmyz.utils.getCourses

class ChoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 获取传递过来的会话信息
        val jsessionId = intent.getStringExtra("JSESSIONID") ?: ""
        val userName = intent.getStringExtra("userName") ?: ""
        val userUrl = intent.getStringExtra("userUrl") ?: ""

        // 验证会话ID
        if (jsessionId.isEmpty()) {
            Toast.makeText(this, "无效的会话，请重试", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            QmyzTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CourseChoiceScreen(
                        jsessionId = jsessionId,
                        userName = userName,
                        userUrl = userUrl
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseChoiceScreen(
    jsessionId: String,
    userName: String,
    userUrl: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 课程列表状态
    var isLoading by remember { mutableStateOf(true) }
    var courses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 添加答题设置
    var questionCount by remember { mutableStateOf(10) }
    var answerDelay by remember { mutableStateOf(5) }

    // 加载课程
    LaunchedEffect(jsessionId) {
        isLoading = true
        coroutineScope.launch {
            try {
                val result = getCourses(jsessionId)
                if (result != null && result.isNotEmpty()) {
                    courses = result
                    errorMessage = null
                } else {
                    errorMessage = "无法获取课程列表，请检查网络连接或重试"
                }
            } catch (e: Exception) {
                errorMessage = "获取课程列表时出错: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择课程") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 添加设置区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "答题设置",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "答题数量: ${if (questionCount == 0) "无限" else questionCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = questionCount.toFloat(),
                        onValueChange = { questionCount = it.toInt() },
                        valueRange = 0f..1000f,
                        steps = 50
                    )
                    Text(
                        text = "注意: 设置为0表示无限答题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "答题延迟(秒): $answerDelay",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = answerDelay.toFloat(),
                        onValueChange = { answerDelay = it.toInt() },
                        valueRange = 1f..30f,
                        steps = 29
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在获取课程列表...")
                        }
                    }
                    errorMessage != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    try {
                                        val result = getCourses(jsessionId)
                                        if (result != null && result.isNotEmpty()) {
                                            courses = result
                                        } else {
                                            errorMessage = "无法获取课程列表，请检查网络连接或重试"
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "获取课程列表时出错: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }) {
                                Text("重试")
                            }
                        }
                    }
                    courses.isEmpty() -> {
                        Text(
                            text = "未找到可用课程",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    else -> {
                        CourseList(
                            courses = courses,
                            onCourseSelected = { courseId, courseName ->
                                val intent = android.content.Intent(context, AutoDOActivity::class.java).apply {
                                    putExtra("JSESSIONID", jsessionId)
                                    putExtra("userName", userName)
                                    putExtra("courseId", courseId)
                                    putExtra("courseName", courseName)
                                    putExtra("questionCount", questionCount)
                                    putExtra("answerDelay", answerDelay)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun CourseList(
    courses: Map<String, String>,
    onCourseSelected: (String, String) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(courses.entries.toList()) { entry ->
            val courseId = entry.key
            val courseName = entry.value

            // 修正为正确的题库文件路径和格式
            val bankDirectory = File(context.filesDir, "bank")
            val courseFile = File(bankDirectory, "$courseId.csv")
            val isAvailable = courseFile.exists() && courseFile.length() > 0

            CourseItem(
                courseId = courseId,
                courseName = courseName,
                isAvailable = isAvailable,
                onCourseSelected = onCourseSelected
            )
        }
    }
}

@Composable
fun CourseItem(
    courseId: String,
    courseName: String,
    isAvailable: Boolean,
    onCourseSelected: (String, String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(
                if (isAvailable) {
                    Modifier.clickable { onCourseSelected(courseId, courseName) }
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isAvailable) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = courseName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isAvailable)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "课程ID: $courseId",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            if (!isAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "题库不存在，无法选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}