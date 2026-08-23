package com.shoshiluna.executive.core.business

import android.content.Context
import com.shoshiluna.executive.core.data.HiveMemoryManager
import java.util.UUID

enum class GarmentType {
    JACKET, BLAZER, SHIRT, BLOUSE, DRESS, SKIRT, TROUSERS, SHOES, ACCESSORY
}

data class WardrobeItem(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val type: GarmentType,
    val style: String,
    val material: String,
    val isCanonicalSafe: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

data class Outfit(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val name: String,
    val items: List<WardrobeItem>,
    val contextTags: Set<String> = emptySet(),
    val isFavorite: Boolean = false,
    val lastUsed: Long? = null
)

/**
 * ShoshiLuna Virtual Tailor.
 * Configures outfits without altering canonical identities.
 */
object VirtualTailor {
    
    private val wardrobe = mutableListOf<WardrobeItem>()
    private val savedOutfits = mutableMapOf<String, Outfit>()
    private val activeOutfits = mutableMapOf<String, String>() // agentId to outfitId

    fun registerItem(item: WardrobeItem) {
        wardrobe.add(item)
    }

    fun buildOutfit(ownerId: String, name: String, itemIds: List<String>): Outfit? {
        val selectedItems = wardrobe.filter { it.id in itemIds && it.ownerId == ownerId }
        if (selectedItems.isEmpty()) return null
        
        val outfit = Outfit(ownerId = ownerId, name = name, items = selectedItems)
        savedOutfits[outfit.id] = outfit
        return outfit
    }

    fun applyOutfit(context: Context, agentId: String, outfitId: String) {
        activeOutfits[agentId] = outfitId
        HiveMemoryManager.saveString(context, "ACTIVE_OUTFIT_$agentId", outfitId)
    }

    fun getActiveOutfit(context: Context, agentId: String): Outfit? {
        val outfitId = HiveMemoryManager.getString(context, "ACTIVE_OUTFIT_$agentId", "")
        return savedOutfits[outfitId]
    }

    fun getWardrobeFor(ownerId: String): List<WardrobeItem> = wardrobe.filter { it.ownerId == ownerId }
}
