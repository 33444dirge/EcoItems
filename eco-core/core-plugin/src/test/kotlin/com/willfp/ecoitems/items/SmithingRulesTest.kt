package com.willfp.ecoitems.items

import org.bukkit.configuration.file.YamlConfiguration

fun main() {
    val config = YamlConfiguration().apply { loadFromString("""
        other: keep
        smithing-rules:
          crown:
            items: [ecoitems:pumpkin_king_crown]
            blocked-recipes: [enderite:dz_enderite_helmet]
            message: denied
    """.trimIndent()) }
    val rules = parseSmithingRules(config)
    check(blockedSmithing(rules, "pumpkin_king_crown", "enderite:dz_enderite_helmet") == "denied") { "matching identity+recipe must block" }
    check(blockedSmithing(rules, "ordinary", "enderite:dz_enderite_helmet") == null)
    check(blockedSmithing(rules, "pumpkin_king_crown", "minecraft:sentry_armor_trim") == null)
    check(blockedSmithing(rules, null, "enderite:dz_enderite_helmet") == null)
    for ((path, value) in listOf("items" to listOf("unknown:hat"), "blocked-recipes" to listOf("not a key"))) {
        val copy = YamlConfiguration().apply { loadFromString(config.saveToString()) }
        copy.set("smithing-rules.crown.$path", value)
        check(runCatching { parseSmithingRules(copy) }.isFailure) { "invalid $path must be rejected" }
    }
    val defaults = "# default rules\nsmithing-rules: {}\n"
    val old = "# owner comment\nother: keep\n"
    val merged = mergeSmithingDefaults(old, defaults)
    check(merged.contains("smithing-rules:")) { "missing configuration must be added automatically" }
    check(merged.contains("# owner comment") && merged.contains("other: keep") && merged.contains("# default rules"))
    check(mergeSmithingDefaults(merged, defaults) == merged) { "migration must be idempotent" }
    val disabled = "# disabled on purpose\nsmithing-rules: {}\n"
    check(mergeSmithingDefaults(disabled, defaults) == disabled) { "empty rules must remain disabled" }
    config.set("smithing-rules.crown.message", mapOf("bad" to "mapping"))
    check(runCatching { parseSmithingRules(config) }.isFailure) { "a message mapping must not be silently accepted as text" }
    println("SMITHING_RULES_PASS 11 assertions")
}
