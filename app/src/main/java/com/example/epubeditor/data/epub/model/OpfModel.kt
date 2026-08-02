package com.example.epubeditor.data.epub.model

data class OpfModel(
    val packageDir: String = "",
    val opfFileName: String = "content.opf",
    val uniqueIdentifier: String = "",
    val metadata: EpubMetadata = EpubMetadata(),
    val manifest: MutableList<ManifestItem> = mutableListOf(),
    val spine: MutableList<String> = mutableListOf(),
    val spineToc: String? = null,
    val guide: MutableList<GuideReference> = mutableListOf()
) {
    val opfPath: String
        get() = if (packageDir.isEmpty()) opfFileName else "$packageDir/$opfFileName"
}

data class EpubMetadata(
    var title: String = "Untitled",
    var authors: MutableList<String> = mutableListOf(),
    var publisher: String = "",
    var language: String = "en",
    var identifier: String = "",
    var date: String = "",
    var description: String = "",
    var rights: String = "",
    var coverManifestId: String? = null,
    var otherMeta: MutableMap<String, String> = mutableMapOf()
)

data class GuideReference(
    val type: String,
    val title: String,
    val href: String
)
