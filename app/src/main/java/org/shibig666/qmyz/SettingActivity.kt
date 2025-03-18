package org.shibig666.qmyz

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shibig666.qmyz.data.CheckForBankUpdates
import org.shibig666.qmyz.data.UpdateStatus
import org.shibig666.qmyz.ui.theme.QmyzTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QmyzTheme {
                SettingsScreen(onBackPressed = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("qmyz_settings", Context.MODE_PRIVATE)
    var mirrorLink by remember { mutableStateOf(prefs.getString("mirror_link", "https://ghfast.top/") ?: "https://ghfast.top/") }
    var showMirrorDialog by remember { mutableStateOf(false) }
    var showBankListDialog by remember { mutableStateOf(false) }
    var bankFiles by remember { mutableStateOf<List<BankFile>>(emptyList()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<UpdateStatus?>(null) }
    var manualUpdateTriggered by remember { mutableStateOf(false) }

    // Get bank files when showing the list dialog
    LaunchedEffect(showBankListDialog) {
        if (showBankListDialog) {
            bankFiles = withContext(Dispatchers.IO) {
                val bankDir = File(context.filesDir, "bank")
                if (bankDir.exists() && bankDir.isDirectory) {
                    bankDir.listFiles()
                        ?.filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
                        ?.map {
                            BankFile(
                                name = it.nameWithoutExtension,
                                size = it.length(),
                                lastModified = it.lastModified()
                            )
                        } ?: emptyList()
                } else {
                    emptyList()
                }
            }
        }
    }

    // Mirror URL input dialog
    if (showMirrorDialog) {
        MirrorLinkDialog(
            currentLink = mirrorLink,
            onDismiss = { showMirrorDialog = false },
            onConfirm = { newLink ->
                mirrorLink = newLink
                prefs.edit().putString("mirror_link", newLink).apply()
                Toast.makeText(context, "镜像地址已保存", Toast.LENGTH_SHORT).show()
                showMirrorDialog = false
            }
        )
    }

    // Bank list dialog
    if (showBankListDialog) {
        BankListDialog(
            banks = bankFiles,
            onDismiss = { showBankListDialog = false }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除题库") },
            text = { Text("这将删除所有本地题库文件并重置版本。确认删除吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                val bankDir = File(context.filesDir, "bank")
                                if (bankDir.exists() && bankDir.isDirectory) {
                                    bankDir.deleteRecursively()
                                }
                                // Reset version
                                prefs.edit().remove("bank_version").apply()
                            }
                            Toast.makeText(context, "题库已删除", Toast.LENGTH_SHORT).show()
                            showDeleteConfirmDialog = false
                        }
                    }
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Update question bank button
                ElevatedButton(
                    onClick = {
                        manualUpdateTriggered = true
                        updateStatus = UpdateStatus(isUpdating = true, message = "开始检查题库更新...")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("更新题库")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Delete question bank button
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除题库")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Set mirror link button
                ElevatedButton(
                    onClick = { showMirrorDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("设置镜像地址")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前镜像地址: $mirrorLink",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Show question bank names button
                ElevatedButton(
                    onClick = { showBankListDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("查看题库文件")
                }

                val bankVersion = prefs.getInt("bank_version", 0)
                if (bankVersion > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "当前题库版本: v$bankVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // Manual update mechanism
            if (manualUpdateTriggered && updateStatus != null) {
                // Hide this composable once update is complete and not updating
                if (!updateStatus!!.isUpdating && updateStatus!!.progress >= 1f) {
                    LaunchedEffect(Unit) {
                        // Reset after a short delay
                        kotlinx.coroutines.delay(2000)
                        updateStatus = null
                        manualUpdateTriggered = false
                    }
                }

                CheckForBankUpdates(
                    context = context,
                    onUpdateComplete = { success ->
                        // This will be called when update completes
                        updateStatus = UpdateStatus(
                            isUpdating = false,
                            message = if (success) "题库更新成功" else "题库已是最新版本或更新失败",
                            progress = 1f,
                            details = updateStatus?.details ?: emptyList()
                        )
                    },
                    onStatusUpdate = { status ->
                        updateStatus = status
                    }
                )

                // Show update overlay
                UpdateProgressOverlay(updateStatus = updateStatus!!)
            }
        }
    }
}

@Composable
fun MirrorLinkDialog(
    currentLink: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var linkInput by remember { mutableStateOf(currentLink) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置镜像地址") },
        text = {
            Column {
                Text("请输入GitHub镜像地址，必须以/结尾或留空不使用镜像")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it; errorMessage = "" },
                    label = { Text("镜像地址") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage.isNotEmpty(),
                    supportingText = {
                        if (errorMessage.isNotEmpty()) {
                            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (linkInput.isNotBlank() && !linkInput.endsWith("/")) {
                        errorMessage = "地址必须以/结尾"
                    } else {
                        onConfirm(linkInput)
                    }
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun BankListDialog(
    banks: List<BankFile>,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("题库文件列表") },
        text = {
            if (banks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有找到题库文件",
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(banks) { bank ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = bank.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "大小: ${formatFileSize(bank.size)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "更新时间: ${dateFormat.format(Date(bank.lastModified))}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

// Helper function to format file size
fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${String.format("%.1f", size / 1024f)} KB"
        else -> "${String.format("%.1f", size / (1024 * 1024f))} MB"
    }
}

// Data class for bank file information
data class BankFile(
    val name: String,
    val size: Long,
    val lastModified: Long
)