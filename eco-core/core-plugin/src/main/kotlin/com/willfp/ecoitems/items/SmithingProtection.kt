package com.willfp.ecoitems.items

import com.willfp.ecoitems.plugin
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.PrepareSmithingEvent
import org.bukkit.event.inventory.SmithItemEvent
import org.bukkit.inventory.SmithingInventory
import org.bukkit.persistence.PersistentDataType
import java.io.File

/** Rules contain definitions only; no player, inventory, item or session cache. */
object SmithingProtection : Listener {
    @Volatile private var rules: List<SmithingRule> = emptyList()
    private val itemKey = NamespacedKey("ecoitems", "item")
    private val legacyItemKey = NamespacedKey("ecoweapons", "weapon")

    // Called at startup and by the existing /ecoitems reload lifecycle callback.
    // Reload I/O runs on AsyncScheduler; publication is one immutable list swap.
    @Synchronized fun reloadRules(): Boolean {
        return try {
            val file = File(plugin.dataFolder, "smithing.yml")
            val defaults = plugin.getResource("smithing.yml")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val text = if (file.exists()) file.readText(Charsets.UTF_8) else ""
            val merged = mergeSmithingDefaults(text, defaults)
            val config = YamlConfiguration().apply { loadFromString(merged) }
            val next = parseSmithingRules(config)
            if (merged != text) {
                file.parentFile.mkdirs()
                // Append defaults and their comments without rewriting unrelated settings.
                file.writeText(merged, Charsets.UTF_8)
            }
            rules = next
            plugin.logger.info("Loaded ${next.size} smithing protection rule(s).")
            true
        } catch (ex: Exception) {
            plugin.logger.severe("smithing.yml was not applied; keeping previous smithing protection rules: ${ex.message}")
            false
        }
    }

    private fun denial(inventory: SmithingInventory): String? {
        val recipe = inventory.recipe as? Keyed ?: return null
        val pdc = inventory.inputEquipment?.persistentDataContainer ?: return null
        // Protection is based on the physical identity, even while definitions
        // are being reloaded. Do not migrate or otherwise modify a denied input.
        val id = when {
            pdc.has(legacyItemKey, PersistentDataType.STRING) -> pdc.get(legacyItemKey, PersistentDataType.STRING)
            pdc.has(itemKey, PersistentDataType.STRING) -> pdc.get(itemKey, PersistentDataType.STRING)
            else -> null
        }
        return blockedSmithing(rules, id, recipe.key.toString())
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun prepare(event: PrepareSmithingEvent) {
        if (denial(event.inventory) != null) event.result = null
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun take(event: SmithItemEvent) {
        val message = denial(event.inventory) ?: return
        event.isCancelled = true
        event.inventory.result = null
        event.whoClicked.sendMessage(
            LegacyComponentSerializer.legacyAmpersand().deserialize(message).decoration(TextDecoration.ITALIC, false)
        )
    }
}
