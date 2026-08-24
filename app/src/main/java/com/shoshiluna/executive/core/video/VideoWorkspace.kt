package com.shoshiluna.executive.core.video

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/**
 * Device-local video workspace. It never uploads media to a third party by itself.
 * Imported media remains referenced through Android's content URI permission model.
 */
object VideoWorkspace {
    data class VideoAsset(
        val id: String = UUID.randomUUID().toString(),
        val uri: String,
        val displayName: String,
        val sizeBytes: Long,
        val mimeType: String,
        val ownerAgentId: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    data class BuildResult(
        val outputPath: String,
        val bytes: Long,
        val startedAtUs: Long,
        val endedAtUs: Long
    )

    @Volatile private var appContext: Context? = null
    private val assets = linkedMapOf<String, VideoAsset>()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun importVideo(uriString: String, ownerAgentId: String): VideoAsset {
        val context = requireNotNull(appContext) { "VideoWorkspace is not initialized" }
        val uri = Uri.parse(uriString)
        val type = context.contentResolver.getType(uri).orEmpty()
        require(type.startsWith("video/")) { "Selected file is not a video" }

        var name = "video-${System.currentTimeMillis()}"
        var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val asset = VideoAsset(uri = uri.toString(), displayName = name, sizeBytes = size, mimeType = type, ownerAgentId = ownerAgentId)
        synchronized(this) { assets[asset.id] = asset }
        return asset
    }

    @Synchronized fun get(assetId: String): VideoAsset? = assets[assetId]
    @Synchronized fun all(): List<VideoAsset> = assets.values.toList()

    /**
     * Creates a real MP4 clip by remuxing selected time range without transcoding.
     * startMs/endMs are optional; endMs=0 means until source end.
     */
    fun buildTrimmedMp4(assetId: String, startMs: Long = 0L, endMs: Long = 0L): BuildResult {
        val context = requireNotNull(appContext) { "VideoWorkspace is not initialized" }
        val asset = get(assetId) ?: throw IllegalArgumentException("Unknown video asset: $assetId")
        require(startMs >= 0) { "startMs must be >= 0" }
        require(endMs == 0L || endMs > startMs) { "endMs must be greater than startMs" }

        val outputDir = File(context.filesDir, "video-builds").apply { mkdirs() }
        val output = File(outputDir, "shoshiluna-${System.currentTimeMillis()}-${asset.id.take(8)}.mp4")
        val extractor = MediaExtractor()
        context.contentResolver.openAssetFileDescriptor(Uri.parse(asset.uri), "r")?.use { afd ->
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        } ?: error("Unable to open selected video")

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val trackMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 1024 * 1024
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                    if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBufferSize = maxOf(maxBufferSize, format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }
            require(trackMap.isNotEmpty()) { "No audio/video tracks found" }
            muxer.start()

            val startUs = startMs * 1000L
            val endUs = if (endMs > 0) endMs * 1000L else Long.MAX_VALUE
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = java.nio.ByteBuffer.allocate(maxBufferSize)
            val info = android.media.MediaCodec.BufferInfo()
            var firstWrittenUs = -1L
            var lastWrittenUs = -1L

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0 || sampleTime >= endUs) break
                val outTrack = trackMap[trackIndex]
                if (outTrack != null && sampleTime >= startUs) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    if (firstWrittenUs < 0) firstWrittenUs = sampleTime
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = sampleTime - firstWrittenUs
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(outTrack, buffer, info)
                    lastWrittenUs = sampleTime
                }
                if (!extractor.advance()) break
            }
            require(firstWrittenUs >= 0) { "Requested range produced no samples" }
            return BuildResult(output.absolutePath, output.length(), firstWrittenUs, lastWrittenUs)
        } finally {
            runCatching { muxer.stop() }
            muxer.release()
            extractor.release()
        }
    }
}
