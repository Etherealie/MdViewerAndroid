package com.mdviewer.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdviewer.data.DocEntry
import com.mdviewer.data.DocNames
import com.mdviewer.data.DocStore
import com.mdviewer.data.SettingsStore
import com.mdviewer.markdown.MdBlock
import com.mdviewer.markdown.MdDocument
import com.mdviewer.markdown.InlineText
import com.mdviewer.markdown.ListItem
import com.mdviewer.markdown.MarkdownParser
import com.mdviewer.markdown.TextStats
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// 公共小工具
// ---------------------------------------------------------------------------

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("md", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        Toast.makeText(context, "没有能打开这个链接的应用", Toast.LENGTH_SHORT).show()
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / 1048576.0)
}

private fun humanTime(millis: Long): String {
    if (millis <= 0) return ""
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(millis))
}

// ---------------------------------------------------------------------------
// 选择文件夹
// ---------------------------------------------------------------------------

@Composable
fun PickFolderScreen(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MD 阅读器", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "选一个存放 .md 文件的文件夹，之后可以随时更换。\n授权会被系统记住，不需要任何存储权限。",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onPick) {
            Text("选择 Markdown 文件夹", fontSize = 16.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// 文件列表
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    rootName: String,
    entries: List<DocEntry>,
    recent: List<DocEntry>,
    loading: Boolean,
    onOpen: (DocEntry) -> Unit,
    onCreate: (String) -> Unit,
    onChangeFolder: () -> Unit,
    onRefresh: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showNewDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 文件列表是主页，返回键按两下才退出，避免误触
    var lastBackAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = true) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2000L) {
            context.findActivity()?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, "再按一次返回退出", Toast.LENGTH_SHORT).show()
        }
    }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { it.relPath.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(rootName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                        Text(
                            "${entries.size} 个文件",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新") }
                    TextButton(onClick = onChangeFolder) { Text("换文件夹", fontSize = 13.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newName = ""; showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建 Markdown 文件")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索文件名", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清空") }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (entries.isEmpty()) "这个文件夹里没有找到 Markdown 文件" else "没有匹配的文件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            LazyColumn(Modifier.fillMaxSize()) {
                if (query.isBlank() && recent.isNotEmpty()) {
                    item {
                        SectionLabel("最近打开")
                    }
                    items(recent, key = { "recent-" + it.docId }) { entry ->
                        FileRow(entry) { onOpen(entry) }
                    }
                    item { SectionLabel("全部文件") }
                }
                items(filtered, key = { it.docId }) { entry ->
                    FileRow(entry) { onOpen(entry) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // 新建文件对话框
    if (showNewDialog) {
        val preview = DocNames.normalize(newName)
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建 Markdown 文件") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("文件名") },
                        placeholder = { Text("例如 我的笔记") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (preview == null) "请输入文件名（会自动补 .md）" else "将创建：$preview",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "文件会建在当前文件夹的根目录，创建后直接进入编辑。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview != null,
                    onClick = {
                        if (preview != null) {
                            showNewDialog = false
                            onCreate(preview)
                            newName = ""
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun FileRow(entry: DocEntry, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = entry.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = listOf(
                entry.relPath.substringBeforeLast('/', ""),
                humanSize(entry.size),
                humanTime(entry.lastModified),
            ).filter { it.isNotBlank() }.joinToString(" · "),
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// ---------------------------------------------------------------------------
// 阅读页
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    entry: DocEntry,
    store: DocStore,
    settings: SettingsStore,
    onBack: () -> Unit,
    startInEdit: Boolean = false,
    onStartEditConsumed: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var raw by remember(entry) { mutableStateOf("") }
    var doc by remember(entry) { mutableStateOf<MdDocument?>(null) }
    var error by remember(entry) { mutableStateOf<String?>(null) }
    var editing by remember(entry) { mutableStateOf(false) }
    var draft by remember(entry) { mutableStateOf(TextFieldValue("")) }
    var tocOpen by remember(entry) { mutableStateOf(false) }
    var settingsOpen by remember(entry) { mutableStateOf(false) }
    var loading by remember(entry) { mutableStateOf(true) }
    var menuOpen by remember(entry) { mutableStateOf(false) }
    var searchOpen by remember(entry) { mutableStateOf(false) }
    var query by remember(entry) { mutableStateOf("") }
    var matchIndex by remember(entry) { mutableIntStateOf(0) }
    var pendingLeave by remember(entry) { mutableStateOf(false) }
    var previewInEdit by remember(entry) { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // 命中的块下标（一个块里可能命中多处，导航按块跳）
    val matches = remember(doc, query) {
        val d = doc
        if (d == null || query.isBlank()) emptyList() else searchMatchIndices(d, query)
    }
    val dirty = editing && draft.text != raw
    val wordCount = remember(raw) { TextStats.countWords(raw) }

    // 查找：输入后自动跳到第一处命中
    LaunchedEffect(query, doc) {
        matchIndex = 0
        if (query.isNotBlank() && matches.isNotEmpty()) {
            listState.scrollToItem(matches.first())
        }
    }

    fun saveDraft(onDone: () -> Unit = {}) {
        scope.launch {
            try {
                store.write(entry, draft.text)
                val text = store.read(entry)
                raw = text
                doc = MarkdownParser.parse(text)
                editing = false
                previewInEdit = false
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                onDone()
            } catch (e: Exception) {
                Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun requestLeave() {
        when {
            dirty -> pendingLeave = true
            editing -> { editing = false; previewInEdit = false }
            searchOpen -> { searchOpen = false; query = "" }
            settingsOpen -> settingsOpen = false
            tocOpen -> tocOpen = false
            else -> onBack()
        }
    }

    fun jumpToMatch(index: Int) {
        if (matches.isEmpty()) return
        val safe = ((index % matches.size) + matches.size) % matches.size
        matchIndex = safe
        scope.launch { listState.animateScrollToItem(matches[safe]) }
    }

    // 点任务框：直接改磁盘上那一行，不用进编辑模式
    fun toggleTask(item: ListItem, checked: Boolean) {
        val label = (item.blocks.firstOrNull() as? MdBlock.Paragraph)?.text?.text ?: ""
        scope.launch {
            val ok = try {
                store.toggleTask(entry, item.sourceLine, label, checked)
            } catch (e: Exception) {
                false
            }
            if (ok) {
                try {
                    val text = store.read(entry)
                    raw = text
                    doc = MarkdownParser.parse(text)
                } catch (_: Exception) { }
            } else {
                Toast.makeText(context, "定位不到这一行，请进「编辑」手动改", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(entry) {
        loading = true
        error = null
        try {
            val text = store.read(entry)
            raw = text
            doc = MarkdownParser.parse(text)
            store.markRecent(entry)
            if (startInEdit) {
                draft = TextFieldValue(text)
                editing = true
                onStartEditConsumed()
            }
        } catch (e: Exception) {
            error = e.message ?: "读取失败"
        }
        loading = false
    }

    // 返回键顺序：未保存确认 → 退出编辑 → 关面板/查找 → 回文件列表
    BackHandler(enabled = true) { requestLeave() }

    // 阅读时保持屏幕常亮（离开页面自动撤销）
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        val window = view.context.findActivity()?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (settings.keepScreenOn) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { requestLeave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                title = {
                    Column {
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                        if (editing) {
                            val now = TextStats.countWords(draft.text)
                            Text(
                                text = if (dirty) "● 未保存 · $now 字" else "$now 字",
                                fontSize = 11.sp,
                                color = if (dirty) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ReaderStatusLine(listState, wordCount, settings.showProgress)
                        }
                    }
                },
                actions = {
                    if (editing) {
                        TextButton(onClick = { previewInEdit = !previewInEdit }) {
                            Text(if (previewInEdit) "编辑" else "预览")
                        }
                        TextButton(onClick = { saveDraft() }) { Text("保存") }
                        TextButton(onClick = { requestLeave() }) { Text("取消") }
                    } else {
                        IconButton(onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        }) { Icon(Icons.Default.Search, "查找") }
                        IconButton(onClick = { tocOpen = true }) { Icon(Icons.AutoMirrored.Filled.List, "大纲") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "更多") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("编辑") },
                                    onClick = {
                                        menuOpen = false
                                        draft = TextFieldValue(raw, TextRange(raw.length))
                                        previewInEdit = false
                                        editing = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("阅读设置") },
                                    onClick = { menuOpen = false; settingsOpen = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("分享原文") },
                                    onClick = { menuOpen = false; shareText(context, entry.name, raw) },
                                )
                                DropdownMenuItem(
                                    text = { Text("复制全文") },
                                    onClick = { menuOpen = false; copyToClipboard(context, raw) },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("读取失败：$error", color = MaterialTheme.colorScheme.error)
                }

                // 编辑态也可以看一眼渲染效果（看不明白的表格/公式免盲写）
                editing && previewInEdit -> {
                    val previewDoc = remember(draft.text) { MarkdownParser.parse(draft.text) }
                    LazyColumn(
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(settings.marginDp.dp, 8.dp, settings.marginDp.dp + 6.dp, 64.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(previewDoc.blocks) { _, block -> BlockView(block) }
                    }
                }

                editing -> Column(Modifier.fillMaxSize()) {
                    MarkdownEditToolbar(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = rememberMarkdownSourceHighlight(),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )
                }

                else -> Column(Modifier.fillMaxSize()) {
                    if (searchOpen) {
                        ReaderSearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            current = if (matches.isEmpty()) 0 else matchIndex + 1,
                            total = matches.size,
                            onPrev = { jumpToMatch(matchIndex - 1) },
                            onNext = { jumpToMatch(matchIndex + 1) },
                            onClose = { searchOpen = false; query = "" },
                        )
                    }
                    val blocks = doc?.blocks ?: emptyList()
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalSearchQuery provides query) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = settings.marginDp.dp,
                                    end = settings.marginDp.dp + 6.dp,
                                    top = 8.dp,
                                    bottom = 64.dp,
                                ),
                            ) {
                                itemsIndexed(blocks) { _, block ->
                                    BlockView(
                                        block,
                                        onCopy = { copyToClipboard(context, it) },
                                        onToggleTask = { item, checked -> toggleTask(item, checked) },
                                    )
                                }
                                item {
                                    Spacer(Modifier.height(24.dp))
                                    Text(
                                        text = "$wordCount 字 · ${blocks.size} 个块 · 右上角菜单里可以编辑",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        // 右侧滚动条（长度和位置随内容实时变化）
                        ScrollIndicator(
                            state = listState,
                            enabled = settings.showScrollbar,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        ReaderSettingsSheet(settings = settings, onDismiss = { settingsOpen = false })
    }

    if (tocOpen) {
        ModalBottomSheet(onDismissRequest = { tocOpen = false }) {
            val toc = doc?.toc ?: emptyList()
            val indexMap = remember(doc) { headingIndexMap(doc) }
            if (toc.isEmpty()) {
                Text(
                    "本文档没有标题",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(toc) { heading ->
                        Text(
                            text = heading.text,
                            fontSize = if (heading.level == 1) 15.sp else 14.sp,
                            fontWeight = if (heading.level == 1) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tocOpen = false
                                    indexMap[heading.id]?.let { index ->
                                        scope.launch { listState.animateScrollToItem(index) }
                                    }
                                }
                                .padding(
                                    start = (12 + (heading.level - 1) * 14).dp,
                                    end = 16.dp,
                                    top = 10.dp,
                                    bottom = 10.dp,
                                ),
                        )
                    }
                }
            }
        }
    }

    // 改了没保存就想走？先问一句，别白写
    if (pendingLeave) {
        AlertDialog(
            onDismissRequest = { pendingLeave = false },
            title = { Text("还没保存") },
            text = { Text("这篇文档的改动还没写进文件，离开就会丢掉。") },
            confirmButton = {
                TextButton(onClick = { pendingLeave = false; saveDraft() }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingLeave = false
                        draft = TextFieldValue(raw)
                        editing = false
                        previewInEdit = false
                    }) { Text("放弃修改") }
                    TextButton(onClick = { pendingLeave = false }) { Text("继续编辑") }
                }
            },
        )
    }
}

/** 标题下的状态行：字数（+ 可选的阅读进度） */
@Composable
private fun ReaderStatusLine(state: LazyListState, wordCount: Int, showProgress: Boolean) {
    val percent = if (showProgress) readPercent(state) else null
    val text = when {
        wordCount > 0 && percent != null -> "$wordCount 字 · $percent% 已读"
        wordCount > 0 -> "$wordCount 字"
        percent != null -> "$percent% 已读"
        else -> ""
    }
    if (text.isEmpty()) return
    Text(text = text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 已读百分比。只读 layoutInfo，滚动时只重组这一小块 */
@Composable
private fun readPercent(state: LazyListState): Int? {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total <= 1) return null
    val first = info.visibleItemsInfo.firstOrNull() ?: return null
    val inItem = ((first.offset - info.viewportStartOffset).toFloat() /
        first.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    return ((first.index + inItem) / total * 100).roundToInt().coerceIn(0, 100)
}

/** 把原文交给系统分享面板（微信/邮件/笔记类应用都能接） */
private fun shareText(context: Context, title: String, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "分享到").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        Toast.makeText(context, "没有可分享的应用", Toast.LENGTH_SHORT).show()
    }
}

/** 文档内查找的输入条 */
@Composable
private fun ReaderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    current: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "查找…",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            Text(
                text = if (total == 0) "无结果" else "$current/$total",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onPrev, enabled = total > 0) { Icon(Icons.Default.KeyboardArrowUp, "上一个") }
            IconButton(onClick = onNext, enabled = total > 0) { Icon(Icons.Default.KeyboardArrowDown, "下一个") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭查找") }
        }
    }
}

/** 命中查询词的顶层块下标（大小写不敏感） */
private fun searchMatchIndices(doc: MdDocument, query: String): List<Int> {
    val needle = query.lowercase()
    val out = ArrayList<Int>()
    doc.blocks.forEachIndexed { index, block ->
        if (blockText(block).lowercase().contains(needle)) out.add(index)
    }
    return out
}

/** 把一个块（含嵌套）的文字抖平，只用于查找 */
private fun blockText(block: MdBlock): String = when (block) {
    is MdBlock.Heading -> block.text.text
    is MdBlock.Paragraph -> block.text.text
    is MdBlock.Code -> block.code
    is MdBlock.Quote -> block.children.joinToString(" ") { blockText(it) }
    is MdBlock.BulletList -> block.items.joinToString(" ") { item ->
        item.blocks.joinToString(" ") { blockText(it) }
    }

    is MdBlock.Table -> (block.header + block.rows.flatten()).joinToString(" ") { it.text }
    is MdBlock.FrontMatter -> block.entries.joinToString(" ") { "${it.first}: ${it.second}" }
    MdBlock.Divider -> ""
}

/** 从 Compose 的 Context 里一路往内找 Activity（可能是被包装过的） */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** 标题 id -> 它在块列表里的下标，用于点大纲跳转 */
private fun headingIndexMap(doc: MdDocument?): Map<String, Int> {
    if (doc == null) return emptyMap()
    val map = HashMap<String, Int>()
    var nth = 0
    doc.blocks.forEachIndexed { index, block ->
        if (block is MdBlock.Heading) {
            doc.toc.getOrNull(nth)?.let { map[it.id] = index }
            nth++
        }
    }
    return map
}
