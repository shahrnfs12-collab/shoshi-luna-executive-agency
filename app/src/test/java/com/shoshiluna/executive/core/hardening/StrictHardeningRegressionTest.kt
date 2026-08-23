package com.shoshiluna.executive.core.hardening

import com.shoshiluna.executive.core.presentation.Glb3DValidator
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StrictHardeningRegressionTest {
    private fun baselineJson(): String = """
    {
      "asset":{"version":"2.0"},
      "buffers":[{"byteLength":107520}],
      "bufferViews":[
        {"buffer":0,"byteOffset":0,"byteLength":36},
        {"buffer":0,"byteOffset":36,"byteLength":8},
        {"buffer":0,"byteOffset":44,"byteLength":32},
        {"buffer":0,"byteOffset":76,"byteLength":100}
      ],
      "accessors":[
        {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[-0.5,0.0,-0.5],"max":[0.5,1.75,0.5]},
        {"bufferView":1,"componentType":5126,"count":2,"type":"SCALAR"},
        {"bufferView":2,"componentType":5126,"count":2,"type":"VEC4"}
      ],
      "images":[{"bufferView":3,"mimeType":"image/png"}],
      "textures":[{"source":0}],
      "materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
      "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
      "nodes":[
        {"name":"root"},{"name":"j1"},{"name":"j2"},{"name":"j3"},{"name":"j4"},{"name":"j5"},
        {"name":"j6"},{"name":"j7"},{"name":"j8"},{"name":"j9"},{"name":"j10"},{"name":"j11"},
        {"name":"j12"},{"name":"j13"},{"name":"j14"},{"name":"j15"}
      ],
      "skins":[{"joints":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15],"skeleton":0}],
      "animations":[
        {"name":"Idle","samplers":[{"input":1,"output":2,"interpolation":"LINEAR"}],"channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}]},
        {"name":"Talk","samplers":[{"input":1,"output":2,"interpolation":"LINEAR"}],"channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}]},
        {"name":"Walk","samplers":[{"input":1,"output":2,"interpolation":"LINEAR"}],"channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}]},
        {"name":"Listen","samplers":[{"input":1,"output":2,"interpolation":"LINEAR"}],"channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}]},
        {"name":"Point","samplers":[{"input":1,"output":2,"interpolation":"LINEAR"}],"channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}]},
        {"name":"Present","samplers":[{"input":1,"output":2,"interpolation":"LINEAR"}],"channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}]},
        {"name":"TurnLeft","samplers":[{"input":1,"output":2,"interpolation":"LINEAR"}],"channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}]},
        {"name":"TurnRight","samplers":[{"input":1,"output":2,"interpolation":"LINEAR"}],"channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}]}
      ]
    }
    """.trimIndent()

    private fun glb(json: String): File {
        val raw = json.toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - raw.size % 4) % 4
        val jsonLen = raw.size + jsonPad
        val binLen = 107520 // multiple of 4 and > production-size gate
        val total = 12 + 8 + jsonLen + 8 + binLen
        val b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x46546C67); b.putInt(2); b.putInt(total)
        b.putInt(jsonLen); b.putInt(0x4E4F534A); b.put(raw); repeat(jsonPad) { b.put(0x20) }
        b.putInt(binLen); b.putInt(0x004E4942); b.put(ByteArray(binLen))
        return File.createTempFile("avatar", ".glb").apply { writeBytes(b.array()); deleteOnExit() }
    }

    @Test fun validBaselinePassesStructuralGate() {
        val result = Glb3DValidator().validate(glb(baselineJson()))
        assertTrue("Unexpected GLB errors: ${result.errors}", result.isValid)
        assertEquals("RIGGED_SKELETON_PRESENT", result.skeletonStatus)
        assertTrue(result.missingAnimations.isEmpty())
    }

    @Test fun missingPointFailsOnlyAnimationGate() {
        val result = Glb3DValidator().validate(glb(baselineJson().replace("\"name\":\"Point\"", "\"name\":\"OtherPoint\"")))
        assertFalse(result.isValid)
        assertTrue(result.missingAnimations.contains("Point"))
    }

    @Test fun invalidMaterialTextureReferenceFails() {
        val result = Glb3DValidator().validate(glb(baselineJson().replace("\"index\":0", "\"index\":99")))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("invalid texture index") })
    }

    @Test fun accessorSpanOverflowFails() {
        val result = Glb3DValidator().validate(glb(baselineJson().replace("\"count\":3,\"type\":\"VEC3\"", "\"count\":300,\"type\":\"VEC3\"")))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("exceeds bufferView") })
    }

    @Test fun rotationAnimationRequiresVec4Output() {
        val result = Glb3DValidator().validate(glb(baselineJson().replace("\"count\":2,\"type\":\"VEC4\"", "\"count\":2,\"type\":\"SCALAR\"")))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("rotation") && it.contains("VEC4") })
    }
}
