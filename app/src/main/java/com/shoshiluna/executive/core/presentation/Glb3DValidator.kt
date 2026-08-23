package com.shoshiluna.executive.core.presentation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GlbValidationResult(
    val isValid: Boolean,
    val meshCount: Int = 0,
    val materialCount: Int = 0,
    val textureCount: Int = 0,
    val imageCount: Int = 0,
    val skinCount: Int = 0,
    val uniqueJointCount: Int = 0,
    val skeletonStatus: String = "NO_SKIN",
    val boundsSanity: String = "UNKNOWN",
    val animationNames: List<String> = emptyList(),
    val missingAnimations: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

/**
 * Strict structural gate for self-contained production GLB avatar assets.
 * This is not a visual-identity or humanoid-semantics verifier; those remain runtime/manual gates.
 */
class Glb3DValidator {
    companion object {
        private const val GLB_MAGIC = 0x46546C67
        private const val CHUNK_TYPE_JSON = 0x4E4F534A
        private const val CHUNK_TYPE_BIN = 0x004E4942
        private const val MIN_FILE_SIZE = 100 * 1024
        private val SUPPORTED_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")
        val REQUIRED_ANIMATIONS = listOf("Idle", "Talk", "Walk", "Listen", "Point", "Present", "TurnLeft", "TurnRight")

        internal fun componentByteSize(componentType: Int): Int = when (componentType) {
            5120, 5121 -> 1
            5122, 5123 -> 2
            5125, 5126 -> 4
            else -> 0
        }

        internal fun typeComponentCount(type: String): Int = when (type) {
            "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4", "MAT2" -> 4; "MAT3" -> 9; "MAT4" -> 16
            else -> 0
        }
    }

    fun validate(file: File): GlbValidationResult {
        if (!file.exists() || file.length() < MIN_FILE_SIZE) {
            return GlbValidationResult(false, errors = listOf("GLB file missing or below $MIN_FILE_SIZE byte production threshold"))
        }
        return runCatching { validateBytes(file.readBytes()) }
            .getOrElse { GlbValidationResult(false, errors = listOf("GLB parser error: ${it.javaClass.simpleName}: ${it.message}")) }
    }

    internal fun validateBytes(bytes: ByteArray): GlbValidationResult {
        val errors = mutableListOf<String>()
        if (bytes.size < 20) return GlbValidationResult(false, errors = listOf("GLB header/chunk header truncated"))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        val version = buffer.int
        val declaredLength = buffer.int
        if (magic != GLB_MAGIC) return GlbValidationResult(false, errors = listOf("Invalid GLB magic"))
        if (version != 2) errors += "Unsupported GLB version $version"
        if (declaredLength != bytes.size) errors += "GLB declared length $declaredLength != actual ${bytes.size}"

        val jsonLength = buffer.int
        val jsonType = buffer.int
        if (jsonType != CHUNK_TYPE_JSON) return GlbValidationResult(false, errors = listOf("First GLB chunk is not JSON"))
        if (jsonLength <= 0 || jsonLength % 4 != 0) errors += "JSON chunk length must be positive and 4-byte aligned"
        if (jsonLength > buffer.remaining()) return GlbValidationResult(false, errors = listOf("JSON chunk truncated"))
        val jsonBytes = ByteArray(jsonLength).also(buffer::get)
        val jsonText = String(jsonBytes, Charsets.UTF_8).trimEnd(' ', '\u0000', '\t', '\r', '\n')
        val root = JsonParser.parseString(jsonText).asJsonObject

        var binLength = 0
        var actualBinAvailable = 0
        if (buffer.remaining() > 0) {
            if (buffer.remaining() < 8) errors += "Trailing bytes cannot contain a complete BIN chunk header"
            else {
                binLength = buffer.int
                val binType = buffer.int
                if (binType != CHUNK_TYPE_BIN) errors += "Second GLB chunk is not BIN"
                if (binLength < 0 || binLength % 4 != 0) errors += "BIN chunk length must be non-negative and 4-byte aligned"
                actualBinAvailable = buffer.remaining()
                if (binLength > actualBinAvailable) errors += "BIN chunk truncated: declared $binLength, available $actualBinAvailable"
                if (binLength <= actualBinAvailable) buffer.position(buffer.position() + binLength)
                if (buffer.hasRemaining()) errors += "Unexpected bytes/chunks after BIN chunk (${buffer.remaining()} bytes)"
            }
        }

        val buffers = root.array("buffers")
        val bufferViews = root.array("bufferViews")
        val accessors = root.array("accessors")
        val images = root.array("images")
        val textures = root.array("textures")
        val materials = root.array("materials")
        val meshes = root.array("meshes")
        val nodes = root.array("nodes")
        val skins = root.array("skins")
        val animations = root.array("animations")

        val bufferLengths = IntArray(buffers.size()) { -1 }
        for (i in 0 until buffers.size()) {
            val obj = buffers[i].asJsonObject
            val len = obj.int("byteLength", -1)
            bufferLengths[i] = len
            if (len <= 0) errors += "Buffer $i has invalid byteLength $len"
            if (obj.has("uri")) errors += "External buffer URI is not permitted for production self-contained GLB"
            if (i == 0 && len > binLength) errors += "Buffer 0 byteLength $len exceeds BIN chunk length $binLength"
        }
        if (buffers.size() == 0) errors += "GLB has no buffers"
        if (buffers.size() > 1) errors += "Production avatar GLB must use one self-contained binary buffer"
        if (buffers.size() > 0 && binLength == 0) errors += "Self-contained GLB requires a BIN chunk"

        data class ViewMeta(val bufferIndex: Int, val offset: Int, val length: Int, val stride: Int)
        val viewMeta = arrayOfNulls<ViewMeta>(bufferViews.size())
        for (i in 0 until bufferViews.size()) {
            val obj = bufferViews[i].asJsonObject
            val b = obj.int("buffer", -1); val off = obj.int("byteOffset", 0); val len = obj.int("byteLength", -1); val stride = obj.int("byteStride", 0)
            if (b !in 0 until buffers.size()) errors += "bufferView $i references invalid buffer $b"
            else if (off < 0 || len <= 0 || off.toLong() + len.toLong() > bufferLengths[b].toLong()) errors += "bufferView $i exceeds buffer $b bounds"
            if (b == 0 && off.toLong() + len.toLong() > binLength.toLong()) errors += "bufferView $i exceeds actual BIN chunk bounds"
            if (stride != 0 && stride !in 4..252) errors += "bufferView $i byteStride $stride outside glTF range 4..252"
            viewMeta[i] = ViewMeta(b, off, len, stride)
        }

        data class AccessorMeta(val view: Int, val offset: Int, val componentType: Int, val count: Int, val type: String)
        val accessorMeta = arrayOfNulls<AccessorMeta>(accessors.size())
        for (i in 0 until accessors.size()) {
            val obj = accessors[i].asJsonObject
            val view = obj.int("bufferView", -1); val off = obj.int("byteOffset", 0); val comp = obj.int("componentType", -1); val count = obj.int("count", -1); val type = obj.string("type")
            val compSize = componentByteSize(comp); val components = typeComponentCount(type)
            if (compSize == 0) errors += "Accessor $i invalid componentType $comp"
            if (components == 0) errors += "Accessor $i invalid type '$type'"
            if (count <= 0) errors += "Accessor $i count must be positive"
            if (off < 0) errors += "Accessor $i byteOffset is negative"
            if (view !in 0 until bufferViews.size()) errors += "Accessor $i missing/invalid bufferView $view"
            else if (compSize > 0 && components > 0 && count > 0) {
                val vm = viewMeta[view]!!
                val elementSize = compSize * components
                val stride = if (vm.stride > 0) vm.stride else elementSize
                if (stride < elementSize) errors += "Accessor $i byteStride $stride is smaller than element size $elementSize"
                val span = off.toLong() + (count - 1L) * stride + elementSize
                if (span > vm.length.toLong()) errors += "Accessor $i span $span exceeds bufferView $view length ${vm.length}"
                if (off % compSize != 0) errors += "Accessor $i byteOffset is not aligned to component size $compSize"
            }
            accessorMeta[i] = AccessorMeta(view, off, comp, count, type)
        }

        for (i in 0 until images.size()) {
            val obj = images[i].asJsonObject
            when {
                obj.has("bufferView") -> {
                    val view = obj.int("bufferView", -1)
                    if (view !in 0 until bufferViews.size()) errors += "Image $i invalid bufferView $view"
                    val mime = obj.string("mimeType")
                    if (mime !in SUPPORTED_MIME_TYPES) errors += "Image $i unsupported mimeType '$mime'"
                }
                obj.has("uri") -> {
                    val uri = obj.string("uri")
                    if (!uri.startsWith("data:")) errors += "External image URI not permitted: $uri"
                }
                else -> errors += "Image $i has neither bufferView nor URI"
            }
        }
        for (i in 0 until textures.size()) {
            val src = textures[i].asJsonObject.int("source", -1)
            if (src !in 0 until images.size()) errors += "Texture $i references invalid image $src"
        }
        if (materials.size() == 0) errors += "GLB contains zero materials"
        if (textures.size() == 0 || images.size() == 0) errors += "Production avatar requires embedded textures/images"
        for (i in 0 until materials.size()) validateMaterialTextureRefs(materials[i].asJsonObject, i, textures.size(), errors)

        if (meshes.size() == 0) errors += "GLB contains zero meshes"
        val positionAccessors = mutableSetOf<Int>()
        for (mi in 0 until meshes.size()) {
            val primitives = meshes[mi].asJsonObject.array("primitives")
            if (primitives.size() == 0) errors += "Mesh $mi has zero primitives"
            for (pi in 0 until primitives.size()) {
                val attrs = primitives[pi].asJsonObject.obj("attributes")
                val pos = attrs?.int("POSITION", -1) ?: -1
                if (pos !in 0 until accessors.size()) errors += "Mesh $mi primitive $pi has invalid/missing POSITION accessor"
                else {
                    positionAccessors += pos
                    val meta = accessorMeta[pos]!!
                    if (meta.type != "VEC3") errors += "POSITION accessor $pos must be VEC3"
                    if (meta.componentType != 5126) errors += "POSITION accessor $pos must use FLOAT componentType"
                }
            }
        }

        val uniqueJoints = mutableSetOf<Int>()
        if (skins.size() == 0) errors += "No skin/rig found"
        for (si in 0 until skins.size()) {
            val skin = skins[si].asJsonObject
            val joints = skin.array("joints")
            if (joints.size() == 0) errors += "Skin $si has no joints"
            for (ji in 0 until joints.size()) {
                val node = joints[ji].asInt
                if (node !in 0 until nodes.size()) errors += "Skin $si joint $node out of node bounds" else uniqueJoints += node
            }
            if (skin.has("skeleton") && skin.int("skeleton", -1) !in 0 until nodes.size()) errors += "Skin $si invalid skeleton root"
            if (skin.has("inverseBindMatrices")) {
                val idx = skin.int("inverseBindMatrices", -1)
                if (idx !in 0 until accessors.size()) errors += "Skin $si invalid inverseBindMatrices accessor"
                else {
                    val m = accessorMeta[idx]!!
                    if (m.componentType != 5126 || m.type != "MAT4") errors += "inverseBindMatrices accessor must be FLOAT MAT4"
                    if (m.count != joints.size()) errors += "inverseBindMatrices count must equal skin joint count"
                }
            }
        }
        val skeletonStatus = when {
            skins.size() == 0 -> "NO_SKIN"
            uniqueJoints.size >= 15 -> "RIGGED_SKELETON_PRESENT"
            else -> "SKELETON_INCOMPLETE"
        }
        if (skins.size() > 0 && uniqueJoints.size < 15) errors += "Rig has fewer than 15 unique joints"

        val animationNames = mutableListOf<String>()
        for (ai in 0 until animations.size()) {
            val anim = animations[ai].asJsonObject
            val name = anim.string("name").ifBlank { "anim_$ai" }
            animationNames += name
            val samplers = anim.array("samplers"); val channels = anim.array("channels")
            if (samplers.size() == 0) errors += "Animation '$name' has no samplers"
            if (channels.size() == 0) errors += "Animation '$name' has no channels"
            for (ci in 0 until channels.size()) {
                val ch = channels[ci].asJsonObject
                val samplerIndex = ch.int("sampler", -1)
                if (samplerIndex !in 0 until samplers.size()) { errors += "Animation '$name' channel $ci invalid sampler"; continue }
                val sampler = samplers[samplerIndex].asJsonObject
                val input = sampler.int("input", -1); val output = sampler.int("output", -1); val interp = sampler.string("interpolation").ifBlank { "LINEAR" }
                if (input !in 0 until accessors.size() || output !in 0 until accessors.size()) { errors += "Animation '$name' sampler has invalid accessor"; continue }
                if (interp !in setOf("LINEAR", "STEP", "CUBICSPLINE")) errors += "Animation '$name' unsupported interpolation '$interp'"
                val target = ch.obj("target")
                val node = target?.int("node", -1) ?: -1; val path = target?.string("path").orEmpty()
                if (node !in 0 until nodes.size()) errors += "Animation '$name' target node invalid"
                if (path !in setOf("translation", "rotation", "scale", "weights")) errors += "Animation '$name' target path '$path' invalid"
                val inputMeta = accessorMeta[input]!!; val outputMeta = accessorMeta[output]!!
                if (inputMeta.type != "SCALAR" || inputMeta.componentType != 5126) errors += "Animation '$name' input accessor must be FLOAT SCALAR"
                val expectedOutputType = when (path) { "rotation" -> "VEC4"; "translation", "scale" -> "VEC3"; "weights" -> "SCALAR"; else -> "" }
                if (expectedOutputType.isNotEmpty() && outputMeta.type != expectedOutputType) errors += "Animation '$name' '$path' output must be $expectedOutputType"
                if (outputMeta.componentType != 5126) errors += "Animation '$name' output accessor must use FLOAT"
                val expectedCount = if (interp == "CUBICSPLINE") inputMeta.count * 3 else inputMeta.count
                if (path != "weights" && outputMeta.count != expectedCount) errors += "Animation '$name' output count ${outputMeta.count} != expected $expectedCount"
            }
        }
        val missing = REQUIRED_ANIMATIONS.filter { req -> animationNames.none { it.equals(req, true) } }
        if (missing.isNotEmpty()) errors += "Missing required animations: $missing"

        var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY; var foundBounds = false
        for (idx in positionAccessors) {
            val obj = accessors[idx].asJsonObject
            val min = obj.array("min"); val max = obj.array("max")
            if (min.size() >= 3 && max.size() >= 3) {
                minY = kotlin.math.min(minY, min[1].asDouble); maxY = kotlin.math.max(maxY, max[1].asDouble); foundBounds = true
            }
        }
        var boundsSanity = "BOUNDS_REQUIRES_RUNTIME_VALIDATION"
        if (foundBounds && maxY > minY) {
            val height = maxY - minY
            boundsSanity = if (height in 0.8..2.5) "LOCAL_POSITION_BOUNDS_REASONABLE" else "OUT_OF_SCALE"
            if (height !in 0.8..2.5) errors += "Local POSITION height $height outside 0.8..2.5 range"
            // Node transforms can alter final world-space scale, therefore runtime validation remains mandatory.
        } else errors += "POSITION accessors do not expose min/max bounds"

        return GlbValidationResult(
            isValid = errors.isEmpty(), meshCount = meshes.size(), materialCount = materials.size(), textureCount = textures.size(),
            imageCount = images.size(), skinCount = skins.size(), uniqueJointCount = uniqueJoints.size,
            skeletonStatus = skeletonStatus, boundsSanity = boundsSanity, animationNames = animationNames,
            missingAnimations = missing, errors = errors
        )
    }

    private fun validateMaterialTextureRefs(material: JsonObject, index: Int, textureCount: Int, errors: MutableList<String>) {
        val refs = mutableListOf<Int>()
        val pbr = material.obj("pbrMetallicRoughness")
        pbr?.obj("baseColorTexture")?.int("index", -1)?.let(refs::add)
        pbr?.obj("metallicRoughnessTexture")?.int("index", -1)?.let(refs::add)
        material.obj("normalTexture")?.int("index", -1)?.let(refs::add)
        material.obj("occlusionTexture")?.int("index", -1)?.let(refs::add)
        material.obj("emissiveTexture")?.int("index", -1)?.let(refs::add)
        if (refs.none { it >= 0 }) errors += "Material $index has no texture references"
        refs.filter { it >= 0 && it !in 0 until textureCount }.forEach { errors += "Material $index invalid texture index $it" }
    }

    private fun JsonObject.array(name: String): JsonArray = if (has(name) && get(name).isJsonArray) getAsJsonArray(name) else JsonArray()
    private fun JsonObject.obj(name: String): JsonObject? = if (has(name) && get(name).isJsonObject) getAsJsonObject(name) else null
    private fun JsonObject.int(name: String, default: Int): Int = runCatching { get(name)?.asInt ?: default }.getOrDefault(default)
    private fun JsonObject.string(name: String): String = runCatching { get(name)?.asString ?: "" }.getOrDefault("")
}
