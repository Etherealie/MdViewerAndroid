package com.mdviewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mdviewer.data.DocEntry
import com.mdviewer.data.DocStore
import com.mdviewer.data.SettingsStore
import com.mdviewer.ui.FileListScreen
import com.mdviewer.ui.LocalReaderStyle
import com.mdviewer.ui.MdViewerTheme
import com.mdviewer.ui.PickFolderScreen
import com.mdviewer.ui.ReaderScreen
import com.mdviewer.ui.ReaderStyle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MdViewerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val settings = remember { SettingsStore(applicationContext) }
                    CompositionLocalProvider(
                        LocalReaderStyle provides ReaderStyle(
                            fontSizeSp = settings.fontSizeSp,
                            marginDp = settings.marginDp,
                            lineHeightPercent = settings.lineHeightPercent,
                        ),
                    ) {
                        AppRoot(settings)
                    }
                }
            }
        }
    }
}

/**
 * 整个应用只有三个页面，用一个状态变量切换就够了，
 * 所以这里刻意不引入 Navigation 库 —— 少一个依赖，也少一层概念。
 *
 *   treeUri == null      -> 选文件夹
 *   current == null      -> 文件列表
 *   current != null      -> 阅读页
 */
@Composable
fun AppRoot(settings: SettingsStore) {
    val context = LocalContext.current
    val store = remember { DocStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<DocEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<DocEntry?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var openForEdit by remember { mutableStateOf(false) }

    // 系统的文件夹选择器（SAF），返回值是用户授权后的目录 URI
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            store.setFolder(uri, queryDisplayName(context, uri))
            entries = emptyList()
            reloadTick++
        }
    }

    LaunchedEffect(reloadTick) {
        if (store.treeUri == null) return@LaunchedEffect
        loading = true
        entries = store.listMarkdown()
        loading = false
    }

    val recent = remember(entries, reloadTick) {
        val byPath = entries.associateBy { it.relPath }
        store.recentPaths().mapNotNull { byPath[it] }
    }

    val opened = current
    when {
        opened != null -> ReaderScreen(
            entry = opened,
            store = store,
            settings = settings,
            onBack = { current = null },
            startInEdit = openForEdit,
            onStartEditConsumed = { openForEdit = false },
        )

        store.treeUri == null -> PickFolderScreen(onPick = { picker.launch(null) })

        else -> FileListScreen(
            rootName = store.rootName,
            entries = entries,
            recent = recent,
            loading = loading,
            onOpen = { current = it },
            onCreate = { fileName ->
                scope.launch {
                    try {
                        val title = fileName.substringBeforeLast('.')
                        val entry = store.createMarkdown(fileName, "# $title\n\n")
                        reloadTick++
                        current = entry          // 建好直接进去
                        openForEdit = true       // 并直接进入编辑状态
                    } catch (e: Exception) {
                        toast(context, "新建失败：${e.message}")
                    }
                }
            },
            onChangeFolder = { picker.launch(store.treeUri) },
            onRefresh = { reloadTick++ },
        )
    }
}

private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
}

/** 从目录 URI 里查出文件夹显示名，查不到就退回路径尾段 */
private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
    context.contentResolver.query(
        docUri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (e: Exception) {
    uri.lastPathSegment
}
