package com.example.epubeditor.data.epub.model

data class TocModel(
    val title: String = "Table of Contents",
    val rootPoints: MutableList<NavPoint> = mutableListOf()
)

data class NavPoint(
    val id: String,
    var playOrder: Int = 0,
    var label: String = "",
    var src: String = "",
    var children: MutableList<NavPoint> = mutableListOf()
) {
    fun flatten(): List<NavPoint> {
        val result = mutableListOf<NavPoint>()
        result.add(this)
        children.forEach { result.addAll(it.flatten()) }
        return result
    }
}
