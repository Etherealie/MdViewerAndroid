package com.mdviewer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/** 一个 Markdown 文件在 SAF 里的定位信息 */
data class DocEntry(
    val docId: String,
    val uri: Uri,
    val name: String,
    val relPath: String,
    val size: Long,
    val lastModified: Long,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

/**
 * 用「存储访问框架」(SAF) 读自己选的文件夹。
 *
 * 为什么不用路径 + 权限：
 *   Android 10 起是分区存储，拿不到任意路径的读写权限；
 *   SAF 让用户手动挑一个文件夹并授权，之后靠持久化的 URI 权限访问，
 *   既不用申请任何危险权限，也不用管 Android 版本差异。
 */
class DocStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var treeUri: Uri?
        get() = prefs.getString(KEY_TREE, null)?.let(Uri::parse)
        private set(value) {
            prefs.edit().putString(KEY_TREE, value?.toString()).apply()
        }

    val rootName: String
        get() = prefs.getString(KEY_ROOT_NAME, "所选文件夹") ?: "所选文件夹"

    /** 记住用户选的文件夹，并申请持久化权限（重启 App 后仍然有效） */
    fun setFolder(uri: Uri, displayName: String?) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // 有些文件提供方（如部分网盘）不支持持久化，忽略即可，当次会话仍可用
            Log.w(TAG, "takePersistableUriPermission 失败: ${e.message}")
        }
        treeUri = uri
        prefs.edit().putString(KEY_ROOT_NAME, displayName ?: uri.lastPathSegment ?: "所选文件夹").apply()
    }

    // ------------------------------------------------------------------ 列出文件

    suspend fun listMarkdown(): List<DocEntry> = withContext(Dispatchers.IO) {
        val root = treeUri ?: return@withContext emptyList()
        val out = ArrayList<DocEntry>()
        try {
            val rootId = DocumentsContract.getTreeDocumentId(root)
            walk(root, rootId, "", out, 0)
        } catch (e: Exception) {
            Log.e(TAG, "遍历文件夹失败", e)
        }
        out.sortedWith(compareBy({ it.relPath.lowercase() }))
    }

    private fun walk(
        tree: Uri,
        parentDocId: String,
        prefix: String,
        out: MutableList<DocEntry>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (out.size >= MAX_FILES) return
                val docId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                val size = cursor.getLong(3)
                val modified = cursor.getLong(4)
                val rel = if (prefix.isEmpty()) name else "$prefix/$name"

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (name.startsWith(".")) continue
                    walk(tree, docId, rel, out, depth + 1)
                } else if (name.substringAfterLast('.', "").lowercase() in EXTENSIONS) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                    out.add(DocEntry(docId, docUri, name, rel, size, modified))
                }
            }
        }
    }

    // ------------------------------------------------------------------ 读写

    suspend fun read(entry: DocEntry): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(entry.uri)?.use { it.readBytes() }
            ?: error("打不开文件：${entry.name}")
        decode(bytes)
    }

    suspend fun write(entry: DocEntry, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(entry.uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("没有写入权限：${entry.name}")
    }

    /**
     * 在所选文件夹的根目录新建一个 Markdown 文件。
     *
     * 有些文件提供方不认 "text/markdown" 这个 MIME，所以失败时退回 "text/plain"。
     * 同名文件已存在时，系统会自动改名（例如 "笔记 (1).md"），
     * 所以创建后要把真实的显示名读回来。
     */
    suspend fun createMarkdown(fileName: String, initialContent: String): DocEntry = withContext(Dispatchers.IO) {
        val tree = treeUri ?: error("还没有选择文件夹")
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )

        val created = runCatching {
            DocumentsContract.createDocument(resolver, parent, "text/markdown", fileName)
        }.getOrNull() ?: runCatching {
            DocumentsContract.createDocument(resolver, parent, "text/plain", fileName)
        }.getOrNull() ?: error("创建失败：这个文件夹可能不允许写入")

        resolver.openOutputStream(created)?.use { it.write(initialContent.toByteArray(Charsets.UTF_8)) }

        val realName = runCatching {
            resolver.query(
                created,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: fileName

        DocEntry(
            docId = DocumentsContract.getDocumentId(created),
            uri = created,
            name = realName,
            relPath = realName,
            size = initialContent.toByteArray(Charsets.UTF_8).size.toLong(),
            lastModified = System.currentTimeMillis(),
        )
    }

    /** 宽松解码：优先 UTF-8，遇到替换字符就退回 GB18030（很多中文 md 是 GBK 存的） */
    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return try {
            String(bytes, Charset.forName("GB18030"))
        } catch (e: Exception) {
            utf8
        }
    }

    // ------------------------------------------------------------------ 最近打开

    fun recentPaths(): List<String> =
        prefs.getString(KEY_RECENT, "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun markRecent(entry: DocEntry) {
        val list = recentPaths().toMutableList()
        list.remove(entry.relPath)
        list.add(0, entry.relPath)
        prefs.edit().putString(KEY_RECENT, list.take(MAX_RECENT).joinToString("\n")).apply()
    }

    companion object {
        private const val TAG = "DocStore"
        private const val PREFS = "mdviewer"
        private const val KEY_TREE = "tree_uri"
        private const val KEY_ROOT_NAME = "root_name"
        private const val KEY_RECENT = "recent"
        private const val MAX_DEPTH = 8
        private const val MAX_FILES = 2000
        private const val MAX_RECENT = 12

        val EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "mdx", "txt", "rst", "log")
    }
}
