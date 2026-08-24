package com.shoshiluna.executive.core.attachments

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import com.shoshiluna.executive.core.network.AiImageInput
import java.io.ByteArrayOutputStream
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
    val safeTextPreview: String? = null,
    val visualInputs: List<AiImageInput> = emptyList(),
    val projectType: String? = null
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
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val zipReport = if (category == AttachmentCategory.ARCHIVE || extension in ZIP_PACKAGES) {
            resolver.openInputStream(uri)?.use { input -> inspectZip(ZipInputStream(BufferedInputStream(input))) }
                ?: ZipReport("לא ניתן לקרוא את $name", emptyList())
        } else null
        if (zipReport?.error != null) return AttachmentInspection.Rejected(zipReport.error)

        val imageInputs = when {
            category == AttachmentCategory.IMAGE -> resolver.openInputStream(uri)?.use { imageToInput(it.readBytes(), mime, name) }?.let(::listOf).orEmpty()
            extension == "pdf" -> renderPdf(resolver, uri, name)
            else -> emptyList()
        }
        if (category == AttachmentCategory.IMAGE && imageInputs.isEmpty()) return AttachmentInspection.Rejected("לא ניתן לפענח את התמונה $name")

        val documentText = when (extension) {
            "docx", "xlsx", "pptx" -> resolver.openInputStream(uri)?.use { extractOpenXml(ZipInputStream(BufferedInputStream(it)), extension) }
            else -> null
        }
        val projectType = zipReport?.let { detectProjectType(it.entries) }
        val summary = when (category) {
            AttachmentCategory.IMAGE -> "תמונה מוכנה לראייה אמיתית"
            AttachmentCategory.TEXT -> "טקסט מוכן לניתוח"
            AttachmentCategory.DOCUMENT -> if (imageInputs.isNotEmpty() || !documentText.isNullOrBlank()) "מסמך חולץ ומוכן לניתוח" else "פורמט מסמך ישן: מטא־נתונים בלבד"
            AttachmentCategory.ARCHIVE -> "ZIP נסרק ומופה${projectType?.let { ": $it" }.orEmpty()}"
            AttachmentCategory.GAME_OR_PROJECT -> "${projectType ?: "חבילת משחק/אפליקציה"} מופתה לניתוח בלבד"
            AttachmentCategory.BINARY -> "קובץ בינארי התקבל לניתוח בלבד"
        }
        val preview = when (category) {
            AttachmentCategory.TEXT -> resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(MAX_TEXT_CHARS) }
            AttachmentCategory.DOCUMENT -> documentText
            AttachmentCategory.ARCHIVE, AttachmentCategory.GAME_OR_PROJECT -> buildString {
                zipReport?.entries?.joinTo(this, prefix = "מפת קבצים:\n", separator = "\n")
                zipReport?.textContent?.takeIf { it.isNotBlank() }?.let { append("\n\nתוכן קוד/טקסט שנשלף בבידוד:\n").append(it) }
            }.take(MAX_TEXT_CHARS)
            else -> null
        }
        return AttachmentInspection.Accepted(ChatAttachment(uri, name, mime, size, category, summary, preview, imageInputs, projectType))
    }

    internal fun validateZipPath(name: String): Boolean {
        if (name.isBlank() || name.indexOf('\u0000') >= 0) return false
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(normalized)) return false
        return normalized.split('/').none { it == ".." }
    }

    private data class ZipReport(val error: String?, val entries: List<String>, val textContent: String = "")

    private fun inspectZip(zip: ZipInputStream): ZipReport {
        var entries = 0
        var expanded = 0L
        val names = mutableListOf<String>()
        val extracted = StringBuilder()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val entry = zip.nextEntry ?: break
            entries++
            if (entries > MAX_ZIP_ENTRIES) return ZipReport("ZIP מכיל יותר מדי פריטים", names, extracted.toString())
            if (!validateZipPath(entry.name)) return ZipReport("ZIP כולל נתיב לא בטוח: ${entry.name.take(80)}", names, extracted.toString())
            if (names.size < MAX_ZIP_NAMES_IN_PROMPT) names += entry.name.take(160)
            if (!entry.isDirectory) {
                val capture = isReadableProjectFile(entry.name) && extracted.length < MAX_TEXT_CHARS
                val entryText = if (capture) ByteArrayOutputStream() else null
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    expanded += count
                    if (expanded > MAX_ZIP_EXPANDED_BYTES) return ZipReport("ZIP נפתח ליותר מ־100MB ולכן נחסם", names, extracted.toString())
                    if (entryText != null && entryText.size() < MAX_ENTRY_TEXT_BYTES) entryText.write(buffer, 0, minOf(count, MAX_ENTRY_TEXT_BYTES - entryText.size()))
                }
                entryText?.toString(Charsets.UTF_8.name())?.takeIf { it.isNotBlank() }?.let {
                    extracted.append("\n--- ").append(entry.name).append(" ---\n").append(it.take(MAX_TEXT_CHARS - extracted.length))
                }
            }
            zip.closeEntry()
        }
        return ZipReport(null, names, extracted.toString())
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

    private fun imageToInput(bytes: ByteArray, mime: String, name: String): AiImageInput? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return bitmapToInput(bitmap, mime.takeIf { it in setOf("image/png", "image/jpeg", "image/webp") } ?: "image/jpeg", name)
    }

    private fun bitmapToInput(bitmap: Bitmap, mime: String, name: String): AiImageInput {
        val output = ByteArrayOutputStream()
        val format = if (mime == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        bitmap.compress(format, 82, output)
        bitmap.recycle()
        return AiImageInput(mime, Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP), name)
    }

    private fun renderPdf(resolver: ContentResolver, uri: Uri, name: String): List<AiImageInput> {
        val descriptor: ParcelFileDescriptor = resolver.openFileDescriptor(uri, "r") ?: return emptyList()
        return descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                (0 until minOf(renderer.pageCount, MAX_PDF_PAGES)).mapNotNull { index ->
                    renderer.openPage(index).use { page ->
                        val scale = minOf(1f, 1400f / maxOf(page.width, page.height).toFloat())
                        val bitmap = Bitmap.createBitmap(maxOf(1, (page.width * scale).toInt()), maxOf(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmapToInput(bitmap, "image/jpeg", "$name page ${index + 1}")
                    }
                }
            }
        }
    }

    private fun extractOpenXml(zip: ZipInputStream, extension: String): String {
        val allowedPrefix = when (extension) { "docx" -> "word/"; "xlsx" -> "xl/"; else -> "ppt/" }
        val result = StringBuilder()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.startsWith(allowedPrefix) && entry.name.endsWith(".xml") && result.length < MAX_TEXT_CHARS) {
                val bytes = ByteArrayOutputStream()
                while (bytes.size() < MAX_ENTRY_TEXT_BYTES) {
                    val count = zip.read(buffer, 0, minOf(buffer.size, MAX_ENTRY_TEXT_BYTES - bytes.size()))
                    if (count < 0) break
                    bytes.write(buffer, 0, count)
                }
                val plain = bytes.toString(Charsets.UTF_8.name()).replace(Regex("<[^>]+>"), " ")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace(Regex("\\s+"), " ").trim()
                if (plain.isNotBlank()) result.append(plain).append('\n')
            }
            zip.closeEntry()
        }
        return result.take(MAX_TEXT_CHARS)
    }

    internal fun detectProjectType(paths: List<String>): String? = when {
        paths.any { it.endsWith("project.godot", true) } -> "Godot"
        paths.any { it.endsWith(".uproject", true) } || paths.any { it.startsWith("Content/") } -> "Unreal Engine"
        paths.any { it.startsWith("Assets/") } && paths.any { it.startsWith("ProjectSettings/") } -> "Unity"
        paths.any { it == "AndroidManifest.xml" } && paths.any { it.startsWith("classes") && it.endsWith(".dex") } -> "Android APK"
        paths.any { it.startsWith("base/manifest/") || it == "BundleConfig.pb" } -> "Android App Bundle"
        else -> null
    }

    private fun isReadableProjectFile(path: String): Boolean = path.substringAfterLast('.', "").lowercase(Locale.ROOT) in READABLE_EXTENSIONS

    private const val MAX_TEXT_CHARS = 60_000
    private const val MAX_ZIP_NAMES_IN_PROMPT = 250
    private const val MAX_ENTRY_TEXT_BYTES = 64 * 1024
    private const val MAX_PDF_PAGES = 6
    private val ZIP_PACKAGES = setOf("apk", "aab", "unitypackage", "jar")
    private val READABLE_EXTENSIONS = setOf("txt", "md", "json", "xml", "yaml", "yml", "gradle", "properties", "kt", "java", "cs", "gd", "py", "js", "ts", "html", "css", "uproject", "godot")
}
