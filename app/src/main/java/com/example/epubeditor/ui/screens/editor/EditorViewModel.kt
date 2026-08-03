package com.example.epubeditor.ui.screens.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubeditor.R
import com.example.epubeditor.data.epub.CommandManager
import com.example.epubeditor.data.epub.EditCommand
import com.example.epubeditor.data.epub.EpubWriter
import com.example.epubeditor.data.epub.model.EpubBook
import com.example.epubeditor.data.epub.model.EpubMetadata
import com.example.epubeditor.data.epub.model.ManifestItem
import com.example.epubeditor.data.epub.model.NavPoint
import com.example.epubeditor.ui.screens.editor.tabs.AssetCategory
import com.example.epubeditor.data.repository.EpubRepository
import com.example.epubeditor.util.sanitizeFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EpubRepository,
    private val writer: EpubWriter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _currentChapterHtml = MutableStateFlow("")
    val currentChapterHtml: StateFlow<String> = _currentChapterHtml.asStateFlow()

    private val commandManager = CommandManager()

    private var _book: EpubBook? = null
    val book: EpubBook? get() = _book
    private var commitJob: Job? = null

    init {
        val bookId: String? = savedStateHandle["bookId"]
        bookId?.let { id ->
            repository.getBook(id)?.let { setBook(it) }
        }
    }

    fun setBook(book: EpubBook) {
        _book = book
        commandManager.clear()
        commitJob?.cancel()
        val firstChapter = book.opf.spine.firstOrNull()
            ?: book.opf.manifest.firstOrNull { it.mediaType == "application/xhtml+xml" }?.id
        _uiState.update {
            it.copy(
                isLoading = false,
                selectedChapterId = firstChapter,
                canUndo = false,
                canRedo = false,
                hasUnsavedChanges = false
            )
        }
        _currentChapterHtml.value = currentChapterContent()
    }

    fun selectTab(tab: EditorTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSourceMode(sourceMode: Boolean) {
        _uiState.update { it.copy(sourceMode = sourceMode) }
    }

    fun selectChapter(id: String) {
        commitPendingHtml()
        _uiState.update { it.copy(selectedChapterId = id) }
        _currentChapterHtml.value = currentChapterContent()
    }

    fun undo() {
        commitJob?.cancel()
        commandManager.undo()
        updateUndoRedoState()
    }

    fun redo() {
        commitJob?.cancel()
        commandManager.redo()
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _uiState.update { it.copy(canUndo = commandManager.canUndo, canRedo = commandManager.canRedo) }
    }

    private fun execute(command: EditCommand) {
        commandManager.execute(command)
        updateUndoRedoState()
        markUnsaved()
    }

    private fun markUnsaved() {
        _uiState.update { it.copy(hasUnsavedChanges = true) }
    }

    //region Metadata

    fun updateMetadata(transform: (EpubMetadata) -> Unit) {
        val current = _book ?: return
        val oldMetadata = current.opf.metadata.copy(
            authors = current.opf.metadata.authors.toMutableList(),
            otherMeta = current.opf.metadata.otherMeta.toMutableMap()
        )
        val newMetadata = current.opf.metadata.copy(
            authors = current.opf.metadata.authors.toMutableList(),
            otherMeta = current.opf.metadata.otherMeta.toMutableMap()
        )
        transform(newMetadata)
        execute(object : EditCommand {
            override fun execute() {
                current.opf.metadata.title = newMetadata.title
                current.opf.metadata.authors = newMetadata.authors
                current.opf.metadata.publisher = newMetadata.publisher
                current.opf.metadata.language = newMetadata.language
                current.opf.metadata.identifier = newMetadata.identifier
                current.opf.metadata.date = newMetadata.date
                current.opf.metadata.description = newMetadata.description
                current.opf.metadata.rights = newMetadata.rights
                current.opf.metadata.coverManifestId = newMetadata.coverManifestId
                current.opf.metadata.otherMeta = newMetadata.otherMeta
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }

            override fun undo() {
                current.opf.metadata.title = oldMetadata.title
                current.opf.metadata.authors = oldMetadata.authors
                current.opf.metadata.publisher = oldMetadata.publisher
                current.opf.metadata.language = oldMetadata.language
                current.opf.metadata.identifier = oldMetadata.identifier
                current.opf.metadata.date = oldMetadata.date
                current.opf.metadata.description = oldMetadata.description
                current.opf.metadata.rights = oldMetadata.rights
                current.opf.metadata.coverManifestId = oldMetadata.coverManifestId
                current.opf.metadata.otherMeta = oldMetadata.otherMeta
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }
        })
    }

    fun updateCoverFromUri(uri: Uri) {
        val current = _book ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                withContext(Dispatchers.IO) {
                    val fileName = "cover_${System.currentTimeMillis()}.jpg"
                    val coverFile = File(current.baseDir, "images/$fileName")
                    coverFile.parentFile?.mkdirs()
                    current.opf.metadata.coverManifestId?.let { oldId ->
                        current.opf.manifest.find { it.id == oldId }?.let { oldItem ->
                            val oldFile = current.resolve(oldItem.href)
                            if (oldFile.exists()) oldFile.delete()
                            current.opf.manifest.removeAll { it.id == oldId }
                        }
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(coverFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val relative = current.relativize(coverFile)
                    val coverId = "cover-image"
                    current.opf.manifest.add(ManifestItem(coverId, relative, "image/jpeg"))
                    current.opf.metadata.coverManifestId = coverId
                }
                _uiState.update { it.copy(isLoading = false, bookVersion = it.bookVersion + 1, hasUnsavedChanges = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    //endregion

    //region TOC

    fun updateToc(transform: (MutableList<NavPoint>) -> Unit) {
        val current = _book ?: return
        val oldRoot = current.toc.rootPoints.map { it.deepCopy() }.toMutableList()
        execute(object : EditCommand {
            override fun execute() {
                transform(current.toc.rootPoints)
                recalculatePlayOrder(current.toc.rootPoints)
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }

            override fun undo() {
                current.toc.rootPoints.clear()
                current.toc.rootPoints.addAll(oldRoot.map { it.deepCopy() })
                recalculatePlayOrder(current.toc.rootPoints)
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }
        })
    }

    fun addTocNode(parentId: String?, label: String, src: String) {
        val newPoint = NavPoint(
            id = generateNavId(),
            label = label,
            src = src
        )
        updateToc { root ->
            if (parentId == null) {
                root.add(newPoint)
            } else {
                root.find { it.id == parentId }?.children?.add(newPoint)
                    ?: root.add(newPoint)
            }
        }
    }

    fun removeTocNode(id: String) {
        updateToc { root ->
            root.removeAll { it.id == id }
            root.forEach { removeFromParent(it, id) }
        }
    }

    fun moveTocNode(fromIndex: Int, toIndex: Int) {
        updateToc { root ->
            if (fromIndex in root.indices && toIndex in root.indices) {
                val item = root.removeAt(fromIndex)
                root.add(toIndex, item)
            }
        }
    }

    private fun removeFromParent(point: NavPoint, id: String) {
        point.children.removeAll { it.id == id }
        point.children.forEach { removeFromParent(it, id) }
    }

    private fun recalculatePlayOrder(points: List<NavPoint>) {
        var order = 1
        fun walk(list: List<NavPoint>) {
            list.forEach { point ->
                point.playOrder = order++
                walk(point.children)
            }
        }
        walk(points)
    }

    private fun NavPoint.deepCopy(): NavPoint = copy(
        children = children.map { it.deepCopy() }.toMutableList()
    )

    private fun generateNavId(): String = "nav_${UUID.randomUUID().toString().take(8)}"

    //endregion

    //region Chapter / HTML editing

    fun currentChapterContent(): String {
        val current = _book ?: return ""
        val id = _uiState.value.selectedChapterId ?: return ""
        val item = current.opf.manifest.find { it.id == id } ?: return ""
        val file = current.resolve(item.href)
        return if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }

    fun setChapterHtml(html: String, autoCommit: Boolean = true) {
        if (html == _currentChapterHtml.value) return
        _currentChapterHtml.value = html
        if (autoCommit) {
            scheduleCommit()
        }
    }

    private fun scheduleCommit() {
        commitJob?.cancel()
        commitJob = viewModelScope.launch {
            delay(600)
            commitPendingHtml()
        }
    }

    fun commitPendingHtml() {
        commitJob?.cancel()
        val html = _currentChapterHtml.value
        val current = _book ?: return
        val id = _uiState.value.selectedChapterId ?: return
        val item = current.opf.manifest.find { it.id == id } ?: return
        val file = current.resolve(item.href)
        val previous = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        if (html == previous) return
        execute(object : EditCommand {
            override fun execute() {
                file.parentFile?.mkdirs()
                file.writeText(html, Charsets.UTF_8)
                _currentChapterHtml.value = html
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }

            override fun undo() {
                file.writeText(previous, Charsets.UTF_8)
                _currentChapterHtml.value = previous
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }
        })
    }

    fun splitChapterAtCursor(cursorOffset: Int) {
        commitPendingHtml()
        val current = _book ?: return
        val id = _uiState.value.selectedChapterId ?: return
        val item = current.opf.manifest.find { it.id == id } ?: return
        val file = current.resolve(item.href)
        val original = if (file.exists()) file.readText(Charsets.UTF_8) else return
        if (cursorOffset <= 0 || cursorOffset >= original.length) return

        val part1 = original.substring(0, cursorOffset)
        val part2 = original.substring(cursorOffset)
        val newFileName = "chapter_${System.currentTimeMillis()}.xhtml"
        val newFile = File(file.parentFile, newFileName)
        val newId = "item_${UUID.randomUUID().toString().take(8)}"

        execute(object : EditCommand {
            val oldSpine = current.opf.spine.toList()
            val oldManifest = current.opf.manifest.map { it.copy() }.toMutableList()

            override fun execute() {
                file.writeText(wrapXhtml(part1), Charsets.UTF_8)
                newFile.writeText(wrapXhtml(part2), Charsets.UTF_8)
                val newHref = current.relativize(newFile)
                current.opf.manifest.add(ManifestItem(newId, newHref, "application/xhtml+xml"))
                val idx = current.opf.spine.indexOf(id)
                if (idx >= 0) {
                    current.opf.spine.add(idx + 1, newId)
                } else {
                    current.opf.spine.add(newId)
                }
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1, selectedChapterId = newId) }
            }

            override fun undo() {
                file.writeText(original, Charsets.UTF_8)
                if (newFile.exists()) newFile.delete()
                current.opf.manifest.clear()
                current.opf.manifest.addAll(oldManifest)
                current.opf.spine.clear()
                current.opf.spine.addAll(oldSpine)
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1, selectedChapterId = id) }
            }
        })
    }

    fun mergeWithNextChapter() {
        commitPendingHtml()
        val current = _book ?: return
        val id = _uiState.value.selectedChapterId ?: return
        val spineIndex = current.opf.spine.indexOf(id)
        if (spineIndex < 0 || spineIndex >= current.opf.spine.lastIndex) return
        val nextId = current.opf.spine[spineIndex + 1]
        val item = current.opf.manifest.find { it.id == id } ?: return
        val nextItem = current.opf.manifest.find { it.id == nextId } ?: return
        val file = current.resolve(item.href)
        val nextFile = current.resolve(nextItem.href)
        if (!file.exists() || !nextFile.exists()) return

        val original = file.readText(Charsets.UTF_8)
        val nextOriginal = nextFile.readText(Charsets.UTF_8)

        execute(object : EditCommand {
            val oldSpine = current.opf.spine.toList()
            val oldManifest = current.opf.manifest.map { it.copy() }.toMutableList()

            override fun execute() {
                val merged = mergeXhtml(original, nextOriginal)
                file.writeText(merged, Charsets.UTF_8)
                if (nextFile.exists()) nextFile.delete()
                current.opf.spine.removeAt(spineIndex + 1)
                current.opf.manifest.removeAll { it.id == nextId }
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }

            override fun undo() {
                file.writeText(original, Charsets.UTF_8)
                nextFile.writeText(nextOriginal, Charsets.UTF_8)
                current.opf.spine.clear()
                current.opf.spine.addAll(oldSpine)
                current.opf.manifest.clear()
                current.opf.manifest.addAll(oldManifest)
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }
        })
    }

    private fun wrapXhtml(bodyContent: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Chapter</title></head>
<body>
$bodyContent
</body>
</html>"""
    }

    private fun mergeXhtml(first: String, second: String): String {
        val doc1 = Jsoup.parse(first, "", Parser.xmlParser())
        val doc2 = Jsoup.parse(second, "", Parser.xmlParser())
        val body1 = doc1.select("body").firstOrNull() ?: return first
        val body2 = doc2.select("body").firstOrNull() ?: return first
        body2.children().forEach { child ->
            body1.appendChild(child.clone())
        }
        return docToXhtml(doc1)
    }

    private fun docToXhtml(doc: org.jsoup.nodes.Document): String {
        val html = doc.html()
        return if (html.contains("<?xml")) html else "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + html
    }

    //endregion

    //region Assets

    fun importAssetToCategory(uri: Uri, fileName: String, category: AssetCategory) {
        when (category) {
            AssetCategory.TEXT -> {
                val ext = fileName.substringAfterLast(".", "").lowercase()
                if (ext !in listOf("html", "htm", "xhtml")) {
                    _uiState.update { it.copy(error = context.getString(R.string.error_text_file_html_only)) }
                    return
                }
                importAssetToFolder(uri, fileName, "text")
            }
            AssetCategory.IMAGES -> {
                val mediaType = guessMediaType(fileName)
                if (!mediaType.startsWith("image/")) {
                    _uiState.update { it.copy(error = context.getString(R.string.error_image_file_only)) }
                    return
                }
                importAssetToFolder(uri, fileName, "images")
            }
            AssetCategory.STYLES -> importAssetToFolder(uri, fileName, "styles")
            AssetCategory.METADATA -> importAssetToFolder(uri, fileName, "")
            AssetCategory.OTHER -> importAssetToFolder(uri, fileName, "misc")
        }
    }

    fun createTextAsset(name: String) {
        val baseName = name.trim().sanitizeFileName()
            .removeSuffix(".html")
            .removeSuffix(".xhtml")
            .removeSuffix(".htm")
        if (baseName.isBlank()) return
        createAssetFile("text", "$baseName.html", "", "application/xhtml+xml")
    }

    fun createTitlePageAsset() {
        val content = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Title Page</title>
  </head>
  <body>
    <h1>Title Page</h1>
  </body>
</html>"""
        createAssetFile("", "titlepage.xhtml", content, "application/xhtml+xml")
    }

    fun createMetadataFile(fileName: String) {
        val sanitized = fileName.trim().sanitizeFileName()
        if (!sanitized.contains(".")) {
            _uiState.update { it.copy(error = context.getString(R.string.error_file_extension_required)) }
            return
        }
        createAssetFile("", sanitized, "", guessMediaType(sanitized))
    }

    private fun createAssetFile(folder: String, fileName: String, content: String, mediaType: String) {
        val current = _book ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                withContext(Dispatchers.IO) {
                    val assetFile = if (folder.isBlank()) {
                        File(current.baseDir, fileName)
                    } else {
                        File(current.baseDir, "$folder/$fileName")
                    }
                    assetFile.parentFile?.mkdirs()
                    assetFile.writeText(content, Charsets.UTF_8)
                    val relative = current.relativize(assetFile)
                    val id = "asset_${UUID.randomUUID().toString().take(8)}"
                    current.opf.manifest.add(ManifestItem(id, relative, mediaType))
                }
                _uiState.update { it.copy(isLoading = false, bookVersion = it.bookVersion + 1, hasUnsavedChanges = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun importAssetToFolder(uri: Uri, fileName: String, folder: String) {
        val current = _book ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                withContext(Dispatchers.IO) {
                    val sanitized = fileName.sanitizeFileName()
                    val mediaType = guessMediaType(fileName)
                    val assetFile = if (folder.isBlank()) {
                        File(current.baseDir, sanitized)
                    } else {
                        File(current.baseDir, "$folder/$sanitized")
                    }
                    assetFile.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(assetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val relative = current.relativize(assetFile)
                    val id = "asset_${UUID.randomUUID().toString().take(8)}"
                    current.opf.manifest.add(ManifestItem(id, relative, mediaType))
                }
                _uiState.update { it.copy(isLoading = false, bookVersion = it.bookVersion + 1, hasUnsavedChanges = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun renameAsset(oldItem: ManifestItem, newName: String) {
        val current = _book ?: return
        val file = current.resolve(oldItem.href)
        if (!file.exists()) return
        val newFile = File(file.parentFile, newName)
        execute(object : EditCommand {
            override fun execute() {
                file.renameTo(newFile)
                current.opf.manifest.removeAll { it.id == oldItem.id }
                current.opf.manifest.add(oldItem.copy(href = current.relativize(newFile)))
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }

            override fun undo() {
                newFile.renameTo(file)
                current.opf.manifest.removeAll { it.id == oldItem.id }
                current.opf.manifest.add(oldItem)
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }
        })
    }

    fun deleteAsset(item: ManifestItem) {
        val current = _book ?: return
        val file = current.resolve(item.href)
        execute(object : EditCommand {
            val oldManifest = current.opf.manifest.map { it.copy() }.toMutableList()
            override fun execute() {
                if (file.exists()) file.delete()
                current.opf.manifest.removeAll { it.id == item.id }
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }

            override fun undo() {
                current.opf.manifest.clear()
                current.opf.manifest.addAll(oldManifest)
                _uiState.update { it.copy(bookVersion = it.bookVersion + 1) }
            }
        })
    }

    fun cleanUnusedAssets(): Int {
        val current = _book ?: return 0
        val usedHrefs = mutableSetOf<String>()
        current.opf.spine.forEach { id ->
            current.opf.manifest.find { it.id == id }?.let { usedHrefs.add(it.href) }
        }
        current.toc.rootPoints.flatMap { it.flatten() }.forEach { usedHrefs.add(it.src.substringBefore("#")) }
        current.opf.guide.forEach { usedHrefs.add(it.href.substringBefore("#")) }

        current.opf.manifest.filter { it.mediaType == "text/css" }.forEach { usedHrefs.add(it.href) }

        val chapterFiles = current.opf.manifest.filter { it.mediaType == "application/xhtml+xml" }
        chapterFiles.forEach { chapter ->
            val file = current.resolve(chapter.href)
            if (file.exists()) {
                val doc = Jsoup.parse(file.readText(Charsets.UTF_8), "", Parser.xmlParser())
                doc.select("[src], [href]").forEach { el ->
                    el.attr("src").takeIf { it.isNotBlank() }?.let { usedHrefs.add(it.substringBefore("#")) }
                    el.attr("href").takeIf { it.isNotBlank() }?.let { usedHrefs.add(it.substringBefore("#")) }
                }
            }
        }

        val toRemove = current.opf.manifest.filter { item ->
            item.href !in usedHrefs && item.id != current.opf.metadata.coverManifestId && item.properties?.contains("nav") != true
        }
        var count = 0
        toRemove.forEach { item ->
            val f = current.resolve(item.href)
            if (f.exists()) {
                f.delete()
                count++
            }
            current.opf.manifest.removeAll { it.id == item.id }
        }
        if (count > 0) {
            _uiState.update { it.copy(bookVersion = it.bookVersion + 1, hasUnsavedChanges = true) }
        }
        return count
    }

    //endregion

    fun saveBook(clearHistory: Boolean = false) {
        val current = _book ?: return
        commitPendingHtml()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, saveFallback = false, lastExportedUri = null) }
            try {
                val result = repository.save(current)
                if (clearHistory) {
                    commandManager.clear()
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastSavedFile = result.file,
                        saveFallback = result.fallback,
                        canUndo = commandManager.canUndo,
                        canRedo = commandManager.canRedo,
                        hasUnsavedChanges = commandManager.canUndo
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun exportToUri(uri: Uri) {
        val current = _book ?: return
        commitPendingHtml()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, lastExportedUri = null) }
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        writer.write(current, output)
                    } ?: throw IllegalStateException("Cannot open output stream")
                    takePersistableUriPermission(uri)
                }
                _uiState.update { it.copy(isLoading = false, lastExportedUri = uri) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun takePersistableUriPermission(uri: Uri) {
        try {
            val mode = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, mode)
        } catch (_: SecurityException) {
            // Provider may not support persistable permission; ignore.
        }
    }

    private fun guessMediaType(name: String): String {
        return when (name.substringAfterLast(".", "").lowercase()) {
            "html", "xhtml", "htm" -> "application/xhtml+xml"
            "css" -> "text/css"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "ncx" -> "application/x-dtbncx+xml"
            else -> "application/octet-stream"
        }
    }
}

data class EditorUiState(
    val isLoading: Boolean = false,
    val selectedTab: EditorTab = EditorTab.TEXT,
    val selectedChapterId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val bookVersion: Int = 0,
    val lastSavedFile: File? = null,
    val saveFallback: Boolean = false,
    val lastExportedUri: Uri? = null,
    val error: String? = null,
    val sourceMode: Boolean = false
)

enum class EditorTab { TEXT, TOC, METADATA, ASSETS }
