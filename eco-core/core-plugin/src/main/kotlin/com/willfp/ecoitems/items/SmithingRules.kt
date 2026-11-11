package com.willfp.ecoitems.items

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

internal data class SmithingRule(val items: Set<String>, val recipes: Set<String>, val message: String)

internal fun parseSmithingRules(config: ConfigurationSection): List<SmithingRule> {
    val section = config.getConfigurationSection("smithing-rules")
        ?: throw IllegalArgumentException("smithing-rules must be a mapping (use {} to disable)")
    return section.getKeys(false).map { name ->
        val rule = section.getConfigurationSection(name)
            ?: throw IllegalArgumentException("smithing-rules.$name must be a mapping")
        fun strings(path: String): List<String> {
            val values = rule.getList(path)
            require(!values.isNullOrEmpty() && values.all { it is String }) { "$name.$path must be a non-empty string list" }
            return values.map { it as String }
        }
        val items = strings("items").map {
            require(it.startsWith("ecoitems:") && it.removePrefix("ecoitems:").matches(Regex("[a-z0-9/._-]+"))) {
                "$name.items: only ecoitems:<id> is supported: $it"
            }
            it.removePrefix("ecoitems:")
        }.toSet()
        val recipes = strings("blocked-recipes").onEach {
            require(it.matches(Regex("[a-z0-9._-]+:[a-z0-9/._-]+"))) { "$name.blocked-recipes: invalid namespaced recipe ID: $it" }
        }.toSet()
        require(!rule.contains("message") || rule.isString("message")) { "$name.message must be a string" }
        SmithingRule(items, recipes, rule.getString("message") ?: "&c这件活动装备不能用于此锻造配方。")
    }.toList()
}

internal fun blockedSmithing(rules: List<SmithingRule>, item: String?, recipe: String?): String? =
    rules.firstOrNull { item in it.items && recipe in it.recipes }?.message

internal fun mergeSmithingDefaults(text: String, defaults: String): String {
    val config = YamlConfiguration().apply { loadFromString(text) }
    return if (config.contains("smithing-rules")) text else text.trimEnd() + "\n\n" + defaults
}
