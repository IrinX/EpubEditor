package com.example.epubeditor.util

fun String.sanitizeFileName(): String {
    return this.replace(Regex("[^a-zA-Z0-9\\-_.]"), "_").take(100)
}
