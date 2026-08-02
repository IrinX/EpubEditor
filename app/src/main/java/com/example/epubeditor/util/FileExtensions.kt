package com.example.epubeditor.util

fun String.sanitizeFileName(): String {
    // Keep letters/digits/spaces/punctuation across all languages; only strip reserved path chars.
    return this.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .trimEnd('.', ' ')
        .take(120)
}
