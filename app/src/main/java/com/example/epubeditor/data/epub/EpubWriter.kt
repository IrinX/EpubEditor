package com.example.epubeditor.data.epub

import com.example.epubeditor.data.epub.model.EpubBook
import com.example.epubeditor.data.epub.model.NavPoint
import com.example.epubeditor.data.epub.model.TocModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubWriter {

    suspend fun write(book: EpubBook, outputFile: File): File = withContext(Dispatchers.IO) {
        writeOpf(book)
        writeToc(book)
        writeContainer(book)
        FileOutputStream(outputFile).use { output ->
            zipBook(book, output)
        }
        outputFile
    }

    suspend fun write(book: EpubBook, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        writeOpf(book)
        writeToc(book)
        writeContainer(book)
        zipBook(book, outputStream)
    }

    suspend fun writeWorkingState(book: EpubBook) = withContext(Dispatchers.IO) {
        writeOpf(book)
        writeToc(book)
        writeContainer(book)
    }

    private fun writeOpf(book: EpubBook) {
        val opf = book.opf
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"${opf.uniqueIdentifier.ifBlank { "bookid" }}\" xml:lang=\"${opf.metadata.language}\">")
        sb.appendLine("    <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
        sb.appendDc("title", opf.metadata.title)
        opf.metadata.authors.forEach { sb.appendDc("creator", it) }
        sb.appendDc("publisher", opf.metadata.publisher)
        sb.appendDc("language", opf.metadata.language)
        sb.appendDc("identifier", opf.metadata.identifier.ifBlank { "urn:uuid:${book.id}" })
        sb.appendDc("date", opf.metadata.date)
        sb.appendDc("description", opf.metadata.description)
        sb.appendDc("rights", opf.metadata.rights)

        opf.metadata.coverManifestId?.let { coverId ->
            sb.appendLine("        <meta name=\"cover\" content=\"${escapeXml(coverId)}\"/>")
        }
        opf.metadata.otherMeta.forEach { (name, content) ->
            sb.appendLine("        <meta name=\"${escapeXml(name)}\" content=\"${escapeXml(content)}\"/>")
        }
        sb.appendLine("    </metadata>")

        if (opf.manifest.none { it.properties?.contains("nav") == true }) {
            val navItem = opf.manifest.firstOrNull { it.href.contains("nav.xhtml", true) }
            navItem?.let { item ->
                opf.manifest.removeAll { it.id == navItem.id }
                opf.manifest.add(item.copy(properties = (item.properties ?: "") + " nav".trim()))
            }
        }

        sb.appendLine("    <manifest>")
        opf.manifest.forEach { item ->
            sb.append("        <item id=\"${escapeXml(item.id)}\" href=\"${escapeXml(item.href)}\" media-type=\"${escapeXml(item.mediaType)}\"")
            item.properties?.let { sb.append(" properties=\"${escapeXml(it)}\"") }
            item.fallback?.let { sb.append(" fallback=\"${escapeXml(it)}\"") }
            sb.appendLine("/>")
        }
        sb.appendLine("    </manifest>")

        sb.append("    <spine")
        opf.spineToc?.let { sb.append(" toc=\"${escapeXml(it)}\"") }
        sb.appendLine(">")
        opf.spine.forEach { idref ->
            sb.appendLine("        <itemref idref=\"${escapeXml(idref)}\"/>")
        }
        sb.appendLine("    </spine>")

        if (opf.guide.isNotEmpty()) {
            sb.appendLine("    <guide>")
            opf.guide.forEach { ref ->
                sb.appendLine("        <reference type=\"${escapeXml(ref.type)}\" title=\"${escapeXml(ref.title)}\" href=\"${escapeXml(ref.href)}\"/>")
            }
            sb.appendLine("    </guide>")
        }

        sb.appendLine("</package>")

        val opfFile = File(book.workingDir, book.opf.opfPath)
        opfFile.parentFile?.mkdirs()
        opfFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun StringBuilder.appendDc(tag: String, value: String) {
        if (value.isBlank()) return
        appendLine("        <dc:$tag>${escapeXml(value)}</dc:$tag>")
    }

    private fun writeToc(book: EpubBook) {
        val ncxItem = book.opf.manifest.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        if (ncxItem != null) {
            val ncxFile = book.resolve(ncxItem.href)
            ncxFile.parentFile?.mkdirs()
            ncxFile.writeText(buildNcx(book.toc, book.opf.metadata.identifier), Charsets.UTF_8)
        }

        val navItem = book.opf.manifest.firstOrNull { it.properties?.contains("nav") == true }
            ?: book.opf.manifest.firstOrNull { it.href.contains("nav.xhtml", true) }
        if (navItem != null) {
            val navFile = book.resolve(navItem.href)
            navFile.parentFile?.mkdirs()
            navFile.writeText(buildNavXhtml(book.toc), Charsets.UTF_8)
        }
    }

    private fun writeContainer(book: EpubBook) {
        val containerFile = File(book.workingDir, "META-INF/container.xml")
        containerFile.parentFile?.mkdirs()
        containerFile.writeText(buildContainer(book.opf.opfPath), Charsets.UTF_8)
    }

    private fun buildContainer(opfPath: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>
"""
    }

    private fun buildNcx(toc: TocModel, uid: String): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<ncx version=\"2005-1\" xml:lang=\"en\" xmlns=\"http://www.daisy.org/z3986/2005/ncx/\">")
        sb.appendLine("    <head>")
        sb.appendLine("        <meta name=\"dtb:uid\" content=\"${escapeXml(uid)}\"/>")
        sb.appendLine("        <meta name=\"dtb:depth\" content=\"2\"/>")
        sb.appendLine("        <meta name=\"dtb:totalPageCount\" content=\"0\"/>")
        sb.appendLine("        <meta name=\"dtb:maxPageNumber\" content=\"0\"/>")
        sb.appendLine("    </head>")
        sb.appendLine("    <docTitle>")
        sb.appendLine("        <text>${escapeXml(toc.title)}</text>")
        sb.appendLine("    </docTitle>")
        sb.appendLine("    <navMap>")

        var order = 1
        fun appendPoint(point: NavPoint, indent: String = "        ") {
            sb.appendLine("$indent<navPoint id=\"${point.id}\" playOrder=\"$order\">")
            order++
            sb.appendLine("$indent    <navLabel><text>${escapeXml(point.label)}</text></navLabel>")
            sb.appendLine("$indent    <content src=\"${escapeXml(point.src)}\"/>")
            point.children.forEach { appendPoint(it, "$indent    ") }
            sb.appendLine("$indent</navPoint>")
        }
        toc.rootPoints.forEach { appendPoint(it) }
        sb.appendLine("    </navMap>")
        sb.appendLine("</ncx>")
        return sb.toString()
    }

    private fun buildNavXhtml(toc: TocModel): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"en\" lang=\"en\">")
        sb.appendLine("    <head>")
        sb.appendLine("        <meta charset=\"UTF-8\"/>")
        sb.appendLine("        <title>${escapeXml(toc.title)}</title>")
        sb.appendLine("    </head>")
        sb.appendLine("    <body>")
        sb.appendLine("        <nav epub:type=\"toc\" id=\"toc\">")
        sb.appendLine("            <h1>${escapeXml(toc.title)}</h1>")
        sb.appendLine("            <ol>")

        fun appendPoint(point: NavPoint, indent: String = "                ") {
            sb.appendLine("$indent<li><a href=\"${escapeXml(point.src)}\">${escapeXml(point.label)}</a></li>")
            if (point.children.isNotEmpty()) {
                sb.appendLine("$indent    <ol>")
                point.children.forEach { appendPoint(it, "$indent        ") }
                sb.appendLine("$indent    </ol>")
            }
        }
        toc.rootPoints.forEach { appendPoint(it) }
        sb.appendLine("            </ol>")
        sb.appendLine("        </nav>")
        sb.appendLine("    </body>")
        sb.appendLine("</html>")
        return sb.toString()
    }

    private fun zipBook(book: EpubBook, outputStream: OutputStream) {
        ZipOutputStream(outputStream.buffered()).use { zos ->
            val mimetypeEntry = ZipEntry("mimetype")
            mimetypeEntry.method = ZipEntry.STORED
            val mimetypeBytes = book.mimetype.toByteArray(Charsets.UTF_8)
            mimetypeEntry.size = mimetypeBytes.size.toLong()
            mimetypeEntry.compressedSize = mimetypeEntry.size
            val crc = CRC32()
            crc.update(mimetypeBytes)
            mimetypeEntry.crc = crc.value
            zos.putNextEntry(mimetypeEntry)
            zos.write(mimetypeBytes)
            zos.closeEntry()

            book.workingDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relative = file.relativeTo(book.workingDir).path.replace("\\", "/")
                    if (relative == "mimetype") return@forEach
                    val entry = ZipEntry(relative)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
