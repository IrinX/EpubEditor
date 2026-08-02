package com.example.epubeditor.data.epub

import com.example.epubeditor.data.epub.model.EpubBook
import com.example.epubeditor.data.epub.model.EpubMetadata
import com.example.epubeditor.data.epub.model.GuideReference
import com.example.epubeditor.data.epub.model.ManifestItem
import com.example.epubeditor.data.epub.model.NavPoint
import com.example.epubeditor.data.epub.model.OpfModel
import com.example.epubeditor.data.epub.model.TocModel
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class EpubParser {

    suspend fun parse(file: File, workingRoot: File, bookId: String, originalUri: Uri? = null): EpubBook = withContext(Dispatchers.IO) {
        val workingDir = File(workingRoot, bookId)
        workingDir.mkdirs()
        unzip(file, workingDir)

        val containerFile = File(workingDir, "META-INF/container.xml")
        val containerXml = if (containerFile.exists()) containerFile.readText(Charsets.UTF_8) else EpubBook.DEFAULT_CONTAINER
        val opfRelativePath = extractOpfPath(containerXml) ?: "OEBPS/content.opf"
        val opfFile = File(workingDir, opfRelativePath)
        val packageDir = opfFile.parentFile?.relativeTo(workingDir)?.path?.replace("\\", "/") ?: ""
        val opfFileName = opfFile.name

        val opf = parseOpf(opfFile, packageDir, opfFileName)
        val toc = parseToc(workingDir, opf)

        EpubBook(
            id = bookId,
            sourceFile = file,
            workingDir = workingDir,
            opf = opf,
            toc = toc,
            containerXml = containerXml,
            originalUri = originalUri
        )
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun extractOpfPath(containerXml: String): String? {
        val doc = Jsoup.parse(containerXml, "", Parser.xmlParser())
        val rootfile = doc.select("rootfile").firstOrNull() ?: return null
        return rootfile.attr("full-path")
    }

    private fun parseOpf(opfFile: File, packageDir: String, opfFileName: String): OpfModel {
        val doc = Jsoup.parse(opfFile.readText(Charsets.UTF_8), "", Parser.xmlParser())
        val packageElement = doc.select("package").first()
        val uniqueIdentifier = packageElement?.attr("unique-identifier") ?: ""

        val metadataElement = doc.select("metadata").firstOrNull()
        val metadata = parseMetadata(metadataElement)

        val manifest = doc.select("manifest > item").map { item ->
            ManifestItem(
                id = item.attr("id"),
                href = item.attr("href"),
                mediaType = item.attr("media-type"),
                properties = item.attr("properties").takeIf { it.isNotBlank() },
                fallback = item.attr("fallback").takeIf { it.isNotBlank() }
            )
        }.toMutableList()

        val spine = doc.select("spine > itemref").map { it.attr("idref") }.toMutableList()
        val spineToc = doc.select("spine").firstOrNull()?.attr("toc")

        val guide = doc.select("guide > reference").map { ref ->
            GuideReference(
                type = ref.attr("type"),
                title = ref.attr("title"),
                href = ref.attr("href")
            )
        }.toMutableList()

        return OpfModel(
            packageDir = packageDir,
            opfFileName = opfFileName,
            uniqueIdentifier = uniqueIdentifier,
            metadata = metadata,
            manifest = manifest,
            spine = spine,
            spineToc = spineToc,
            guide = guide
        )
    }

    private fun parseMetadata(metadataElement: Element?): EpubMetadata {
        val metadata = EpubMetadata()
        metadataElement ?: return metadata

        metadata.title = metadataElement.select("dc|title").firstOrNull()?.text() ?: "Untitled"
        metadata.authors = metadataElement.select("dc|creator").map { it.text() }.toMutableList()
        metadata.publisher = metadataElement.select("dc|publisher").firstOrNull()?.text() ?: ""
        metadata.language = metadataElement.select("dc|language").firstOrNull()?.text() ?: "en"
        metadata.identifier = metadataElement.select("dc|identifier").firstOrNull()?.text() ?: ""
        metadata.date = metadataElement.select("dc|date").firstOrNull()?.text() ?: ""
        metadata.description = metadataElement.select("dc|description").firstOrNull()?.text() ?: ""
        metadata.rights = metadataElement.select("dc|rights").firstOrNull()?.text() ?: ""

        val coverMeta = metadataElement.select("meta[name=cover]").firstOrNull()
            ?: metadataElement.select("meta").firstOrNull { it.attr("name").equals("cover", true) }
        metadata.coverManifestId = coverMeta?.attr("content")

        metadataElement.select("meta").forEach { meta ->
            val name = meta.attr("name")
            val content = meta.attr("content")
            if (name.isNotBlank() && content.isNotBlank() && name != "cover") {
                metadata.otherMeta[name] = content
            }
        }
        return metadata
    }

    private fun parseToc(workingDir: File, opf: OpfModel): TocModel {
        val ncxItem = opf.manifest.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        if (ncxItem != null) {
            val ncxFile = File(workingDir, opf.packageDir).resolve(ncxItem.href)
            if (ncxFile.exists()) return parseNcx(ncxFile.readText(Charsets.UTF_8))
        }

        val navItem = opf.manifest.firstOrNull { it.properties?.contains("nav") == true }
            ?: opf.manifest.firstOrNull { it.href.contains("nav.xhtml", true) }
        if (navItem != null) {
            val navFile = File(workingDir, opf.packageDir).resolve(navItem.href)
            if (navFile.exists()) return parseNavXhtml(navFile.readText(Charsets.UTF_8))
        }

        return TocModel()
    }

    private fun parseNcx(ncxXml: String): TocModel {
        val doc = Jsoup.parse(ncxXml, "", Parser.xmlParser())
        val title = doc.select("docTitle > text").firstOrNull()?.text() ?: "Table of Contents"
        val navMap = doc.select("navMap").firstOrNull() ?: return TocModel(title)

        fun parsePoint(element: Element): NavPoint {
            val label = element.select("navLabel > text").firstOrNull()?.text() ?: ""
            val content = element.select("content").firstOrNull()
            val src = content?.attr("src") ?: ""
            val point = NavPoint(
                id = element.attr("id").ifBlank { generateId() },
                playOrder = element.attr("playOrder").toIntOrNull() ?: 0,
                label = label,
                src = src
            )
            element.select("> navPoint").forEach { child ->
                point.children.add(parsePoint(child))
            }
            return point
        }

        val rootPoints = navMap.select("> navPoint").map { parsePoint(it) }.toMutableList()
        return TocModel(title, rootPoints)
    }

    private fun parseNavXhtml(navXml: String): TocModel {
        val doc: Document = Jsoup.parse(navXml, "", Parser.xmlParser())
        val title = doc.select("h1, h2, title").firstOrNull()?.text() ?: "Table of Contents"
        val ol = doc.select("nav[epub|type=toc] ol, nav ol").firstOrNull()
            ?: return TocModel(title)

        fun parseLi(li: Element): NavPoint {
            val a = li.select("> a").firstOrNull()
            val label = a?.text() ?: li.select("> span").firstOrNull()?.text() ?: ""
            val src = a?.attr("href") ?: ""
            val point = NavPoint(
                id = generateId(),
                label = label,
                src = src
            )
            li.select("> ol > li").forEach { child ->
                point.children.add(parseLi(child))
            }
            return point
        }

        val rootPoints = ol.select("> li").map { parseLi(it) }.toMutableList()
        return TocModel(title, rootPoints)
    }

    private fun generateId(): String = "nav_${System.currentTimeMillis()}_${(0..9999).random()}"
}
