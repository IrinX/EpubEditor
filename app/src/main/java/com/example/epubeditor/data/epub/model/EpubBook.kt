package com.example.epubeditor.data.epub.model

import android.net.Uri
import java.io.File

data class EpubBook(
    val id: String,
    val sourceFile: File?,
    val workingDir: File,
    val opf: OpfModel,
    val toc: TocModel,
    val containerXml: String = DEFAULT_CONTAINER,
    val mimetype: String = MIMETYPE,
    val originalUri: Uri? = null
) {
    val baseDir: File
        get() = if (opf.packageDir.isEmpty()) workingDir else File(workingDir, opf.packageDir)

    fun resolve(href: String): File = File(baseDir, href)

    fun relativize(file: File): String = file.relativeTo(baseDir).path.replace("\\", "/")

    companion object {
        const val MIMETYPE = "application/epub+zip"
        const val DEFAULT_CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>
"""
    }
}
