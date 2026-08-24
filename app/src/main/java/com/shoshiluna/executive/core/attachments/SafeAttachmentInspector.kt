package com.shoshiluna.executive.core.attachments

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class ChatAttachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: AttachmentCategory,
    val summary: String,
    val safeTextPreview: String? = null
)

enum class AttachmentCategory { IMAGE, TEXT, DOCUMENT, ARCHIVE, GAME_OR_PROJECT, BINARY }

sealed interface AttachmentInspection {
    data class Accepted(val attachment: ChatAttachment) : AttachmentInspection
    data class Rejected(val reason: String) : AttachmentInspection
}

object SafeAttachmentInspector {
    const val MAX_FILES = 5
    const val MAX_FILE_BYTES = 25L * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 2_000
    const val MAX_ZIP_EXPANDED_BYTES = 100L * 1024 * 1024

    fun inspect(resolver: ContentResolver, uri: Uri): AttachmentInspection {
        val name = queryName(resolver, uri) ?: "attachment"
        val size = querySize(resolver, uri)
        if (size > MAX_FILE_BYTES) return AttachmentInspection.Rejected("$name גדול מ־25MB")
        if (size < 0L) return AttachmentInspection.Rejected("לא ניתן לאמת את גודל הקובץ $name")

        val mime = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        val category = categoryFor(name, mime)
        val zipReport = if (category == AttachmentCategory.ARCHIVE) {
            resolver.openInputStream(uri)?.use { input -> inspectZip(ZipInputStream(BufferedInputStream(input))) }
                ?: ZipReport("לא ניתן לקרוא את $name", emptyList())
        } else null
        if (zipReport?.error != null) return AttachmentInspection.Rejected(zipReport.error)

        val summary = when (category) {
            AttachmentCategory.IMAGE -> "תמונה מוכנה לצירוף"
            AttachmentCategory.TEXT -> "טקסט מוכן לניתוח"
            AttachmentCategory.DOCUMENT -> "מסמך מוכן לצירוף"
            AttachmentCategory.ARCHIVE -> "ZIP נסרק ומוכן לניתוח"
            AttachmentCategory.GAME_OR_PROJECT -> "קובץ משחק/פרויקט התקבל לניתוח בלבד"
            AttachmentCategory.BINARY -> "קובץ בינארי התקבל לניתוח בלבד"
        }
        val preview = when (category) {
            AttachmentCategory.TEXT -> resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(MAX_TEXT_CHARS) }
            AttachmentCategory.ARCHIVE -> zipReport?.entries?.joinToString(prefix = "תוכן ZIP:\n", separator = "\n")
            else -> null
        }
        return AttachmentInspection.Accepted(ChatAttachment(uri, name, mime, size, category, summary, preview))
    }

    internal fun validateZipPath(name: String): Boolean {
        if (name.isBlank() || name.indexOf('\u0000') >= 0) return false
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(normalized)) return false
        return normalized.split('/').none { it == ".." }
    }

    private data class ZipReport(val error: String?, val entries: List<String>)

    private fun inspectZip(zip: ZipInputStream): ZipReport {
        var entries = 0
        var expanded = 0L
        val names = mutableListOf<String>()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val entry = zip.nextEntry ?: break
            entries++
            if (entries > MAX_ZIP_ENTRIES) return ZipReport("ZIP מכיל יותר מדי פריטים", names)
            if (!validateZipPath(entry.name)) return ZipReport("ZIP כולל נתיב לא בטוח: ${entry.name.take(80)}", names)
            if (names.size < MAX_ZIP_NAMES_IN_PROMPT) names += entry.name.take(160)
            if (!entry.isDirectory) {
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    expanded += count
                    if (expanded > MAX_ZIP_EXPANDED_BYTES) return ZipReport("ZIP נפתח ליותר מ־100MB ולכן נחסם", names)
                }
            }
            zip.closeEntry()
        }
        return ZipReport(null, names)
    }

    private fun categoryFor(name: String, mime: String): AttachmentCategory {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            mime.startsWith("image/") -> AttachmentCategory.IMAGE
            mime.startsWith("text/") || extension in setOf("md", "json", "xml", "csv", "yaml", "yml", "kt", "java", "js", "ts", "py") -> AttachmentCategory.TEXT
            extension == "zip" || mime == "application/zip" -> AttachmentCategory.ARCHIVE
            extension in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx") -> AttachmentCategory.DOCUMENT
            extension in setOf("apk", "aab", "unitypackage", "uproject", "godot", "pak", "obb", "jar") -> AttachmentCategory.GAME_OR_PROJECT
            else -> AttachmentCategory.BINARY
        }
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? =
        queryColumn(resolver, uri, OpenableColumns.DISPLAY_NAME) { it.getString(0) }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long =
        queryColumn(resolver, uri, OpenableColumns.SIZE) { if (it.isNull(0)) -1L else it.getLong(0) } ?: -1L

    private fun <T> queryColumn(resolver: ContentResolver, uri: Uri, column: String, read: (Cursor) -> T): T? =
        resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) read(cursor) else null
        }

    private const val MAX_TEXT_CHARS = 60_000
    private const val MAX_ZIP_NAMES_IN_PROMPT = 250
}
