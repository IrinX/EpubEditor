package com.example.epubeditor.data.repository

import android.content.Context
import android.net.Uri
import com.example.epubeditor.data.epub.EpubParser
import com.example.epubeditor.data.epub.EpubWriter
import com.example.epubeditor.data.epub.model.EpubBook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class SaveResult(
    val file: File,
    val fallback: Boolean = false
)

@Singleton
class EpubRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: EpubParser,
    private val writer: EpubWriter
) {
    private val workingRootDir: File
        get() = File(context.cacheDir, "epub_working").apply { mkdirs() }

    private val importDir: File
        get() = File(context.filesDir, "epub_imports").apply { mkdirs() }

    private val exportDir: File
        get() = File(context.filesDir, "epub_exports").apply { mkdirs() }

    private val openedBooks = mutableMapOf<String, EpubBook>()

    fun rememberBook(book: EpubBook) {
        openedBooks[book.id] = book
    }

    fun getBook(id: String): EpubBook? = openedBooks[id]

    suspend fun openFromUri(uri: Uri): EpubBook = withContext(Dispatchers.IO) {
        val bookId = stableId(uri.toString())
        val importedFile = File(importDir, "$bookId.epub")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(importedFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot open URI: $uri")
        parser.parse(importedFile, workingRootDir, bookId, originalUri = uri).also { rememberBook(it) }
    }

    suspend fun openFromFile(file: File, bookId: String = stableId(file.absolutePath), originalUri: Uri? = null): EpubBook = withContext(Dispatchers.IO) {
        parser.parse(file, workingRootDir, bookId, originalUri = originalUri).also { rememberBook(it) }
    }

    suspend fun save(book: EpubBook, file: File? = null): SaveResult = withContext(Dispatchers.IO) {
        if (file != null) {
            return@withContext SaveResult(writer.write(book, file))
        }

        // 优先写回原始 URI（用户从文件选择器打开的位置）
        book.originalUri?.let { uri ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    writer.write(book, output)
                }
                // 同时更新内部副本，保持一致
                book.sourceFile?.let { writer.write(book, it) }
                return@withContext SaveResult(
                    book.sourceFile ?: File(exportDir, "${book.opf.metadata.title.replace("\\s+".toRegex(), "_")}.epub"),
                    fallback = false
                )
            } catch (_: SecurityException) {
                // 原位置权限失效，降级到 sourceFile 或导出目录
            }
        }

        val target = book.sourceFile ?: File(exportDir, "${book.opf.metadata.title.replace("\\s+".toRegex(), "_")}.epub")
        SaveResult(writer.write(book, target), fallback = book.originalUri != null)
    }

    private fun stableId(source: String): String {
        return MessageDigest.getInstance("MD5").run {
            update(source.toByteArray(Charsets.UTF_8))
            digest().joinToString("") { "%02x".format(it) }
        }
    }
}
