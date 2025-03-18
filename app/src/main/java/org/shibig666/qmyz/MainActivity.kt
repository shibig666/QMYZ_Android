package org.shibig666.qmyz

import android.content.Intent
import org.shibig666.qmyz.data.*
import org.shibig666.qmyz.utils.getCookieFromUrl
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import org.shibig666.qmyz.ui.theme.QmyzTheme
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp()
        }
    }
}

@Composable
fun MyApp() {
    val context = LocalContext.current
    var updateComplete by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf(UpdateStatus()) }

    // 检查题库更新
    CheckForBankUpdates(
        context,
        onUpdateComplete = { success ->
            updateComplete = true
            if (success) {
                Toast.makeText(context, "题库已更新到最新版本", Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(context, "题库更新失败，可以在设置设置镜像地址", Toast.LENGTH_SHORT).show()
            }
        },
        onStatusUpdate = { status ->
            updateStatus = status
        }
    )

    QmyzTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppContent()

            // 如果正在更新，显示加载指示器和详情
            if (updateStatus.isUpdating) {
                UpdateProgressOverlay(updateStatus = updateStatus)
            }
        }
    }
}

@Composable
fun UpdateProgressOverlay(updateStatus: UpdateStatus) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "题库更新中",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = updateStatus.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { updateStatus.progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 显示最近的3条更新记录
                val recentDetails = updateStatus.details.takeLast(3)
                if (recentDetails.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(8.dp),
                    ) {
                        recentDetails.forEach { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (detail != recentDetails.last()) {
                                Divider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    val context = LocalContext.current
    val itemsList = remember { mutableStateListOf<UserItem>() }
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<UserItem?>(null) }

    // 加载保存的数据
    LaunchedEffect(Unit) {
        loadUserItems(context)?.let { savedItems ->
            itemsList.clear()
            itemsList.addAll(savedItems)
        }
    }

    // 用户对话框
    AddUserDialog(
        showDialog = showDialog,
        onDismiss = { showDialog = false },
        onConfirm = { name, url ->
            val newItem = UserItem(name, url)
            itemsList.add(newItem)
            saveUserItems(context, itemsList)
            Toast.makeText(context, "$name 已添加", Toast.LENGTH_SHORT).show()
            showDialog = false
        },
        existingItems = itemsList // 传递现有用户列表
    )

    // 删除确认对话框
    if (showDeleteDialog && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("是否删除 ${selectedItem?.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedItem?.let { item ->
                        itemsList.remove(item)
                        saveUserItems(context, itemsList)
                        Toast.makeText(context, "${item.name} 已删除", Toast.LENGTH_SHORT).show()
                    }
                    showDeleteDialog = false
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                tonalElevation = 4.dp
            ) {
                TopAppBar(
                    title = { Text("青马易战自动答题") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = { MenuBox() }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        }
    ) { paddingValues ->
        if (itemsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无用户，点击右下角添加",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            ItemList(
                items = itemsList,
                modifier = Modifier.padding(paddingValues),
                onLongClick = { item ->
                    selectedItem = item
                    showDeleteDialog = true
                }
            )
        }
    }
}

@Composable
fun ItemList(
    items: List<UserItem>,
    modifier: Modifier = Modifier,
    onLongClick: (UserItem) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(items) { item ->
            ListItemRow(
                item = item,
                onLongClick = { onLongClick(item) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListItemRow(
    item: UserItem,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showCookieDialog by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }
    val maxRetries = 10

    CookieRetrievalDialog(
        isVisible = showCookieDialog,
        retryCount = retryCount,
        maxRetries = maxRetries,
        onDismiss = { showCookieDialog = false }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    showCookieDialog = true
                    retryCount = 0

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            var jsessionId: String? = null

                            for (i in 1..maxRetries) {
                                withContext(Dispatchers.Main) {
                                    retryCount = i
                                }

                                jsessionId = getCookieFromUrl(item.url)
                                if (jsessionId != null) {
                                    break
                                }
                                delay(1000) // 1秒重试间隔
                            }

                            withContext(Dispatchers.Main) {
                                showCookieDialog = false

                                if (jsessionId != null) {
                                    // 导航到ChoiceActivity，并传递cookie
                                    val intent = Intent(context, ChoiceActivity::class.java).apply {
                                        putExtra("JSESSIONID", jsessionId)
                                        putExtra("userName", item.name)
                                        putExtra("userUrl", item.url)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "无法获取会话，请检查网络连接或URL是否正确",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                showCookieDialog = false
                                Toast.makeText(
                                    context,
                                    "获取会话时出错: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                onLongClick = onLongClick
            )
            .padding(16.dp),
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}


@Composable
fun MenuBox() {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box(
        modifier = Modifier.padding(8.dp)
    ) {
        IconButton(
            onClick = { showMenu = !showMenu },
            modifier = Modifier
                .size(40.dp)
                .padding(8.dp)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多")
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.width(120.dp)
        ) {
            DropdownMenuItem(
                text = { Text("设置", fontSize = 16.sp) },
                onClick = {
                    val intent = Intent(context, SettingsActivity::class.java)
                    context.startActivity(intent)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("关于", fontSize = 16.sp) },
                onClick = {
                    val intent = Intent(context, AboutActivity::class.java)
                    context.startActivity(intent)
                    showMenu = false
                }
            )
        }
    }
}

@Composable
fun AddUserDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
    existingItems: List<UserItem>
) {
    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("添加用户") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            if (nameInput.isNotBlank()) nameError = ""
                        },
                        label = { Text("名称") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = nameError.isNotEmpty(),
                        supportingText = {
                            if (nameError.isNotEmpty()) {
                                Text(
                                    text = nameError,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            if (urlInput.isNotBlank()) urlError = ""
                        },
                        label = { Text("URL") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = urlError.isNotEmpty(),
                        supportingText = {
                            if (urlError.isNotEmpty()) {
                                Text(
                                    text = urlError,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 重置错误状态
                        nameError = ""
                        urlError = ""

                        // 验证输入
                        var isValid = true

                        if (nameInput.isBlank()) {
                            nameError = "名称不能为空"
                            isValid = false
                        } else if (existingItems.any { it.name == nameInput.trim() }) {
                            // 检查名称是否已存在
                            nameError = "该名称已存在，请使用其他名称"
                            isValid = false
                        }

                        if (urlInput.isBlank()) {
                            urlError = "URL不能为空"
                            isValid = false
                        } else if (!isValidUrl(urlInput)) {
                            urlError = "请输入有效的URL（以http://或https://开头）"
                            isValid = false
                        }

                        // 如果验证通过，确认添加
                        if (isValid) {
                            onConfirm(nameInput.trim(), urlInput.trim())
                            nameInput = ""
                            urlInput = ""
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        nameInput = ""
                        urlInput = ""
                        nameError = ""
                        urlError = ""
                        onDismiss()
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun CookieRetrievalDialog(
    isVisible: Boolean,
    retryCount: Int,
    maxRetries: Int,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("获取会话中") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp)
                    )
                    Text(
                        text = "正在尝试获取会话 ($retryCount/$maxRetries)",
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {}
        )
    }
}



@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyApp()
}
