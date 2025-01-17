object Versions {

    // Spigot + Plugins
    const val LATEST_SPIGOT_API = "1.21.4-R0.1-SNAPSHOT"
    const val BSTATS = "3.0.1"
    const val PACKET_EVENTS = "2.7.0"
    const val PLACEHOLDER_API = "2.11.6"
    const val MYTHIC_MOBS = "5.7.2"
    const val GEYSER = "2.2.0-SNAPSHOT"
    const val VIVECRAFT = "3.0.0"

    // Adventure Chat API
    const val ADVENTURE_API = "4.18.0"
    const val ADVENTURE_BUKKIT = "4.3.4"
    const val ADVENTURE_TEXT_LEGACY = ADVENTURE_API
    const val ADVENTURE_TEXT_MINIMESSAGE = ADVENTURE_API

    // Misc
    const val JSON_SIMPLE = "1.1.1"
    const val ANNOTATIONS = "24.0.1"
    const val GSON = "2.10.1"
    const val KOTLIN = "1.9.21"
    const val X_SERIES = "13.0.0"
    const val FAST_UTIL = "8.5.13"
    const val FOLIA_SCHEDULER = "0.6.3"
    const val COMMAND_API = "9.7.0"
}

object Dependencies {

    // Spigot + Plugins
    const val LATEST_SPIGOT_API = "org.spigotmc:spigot-api:${Versions.LATEST_SPIGOT_API}"
    const val BSTATS = "org.bstats:bstats-bukkit:${Versions.BSTATS}"
    const val PACKET_EVENTS = "com.github.retrooper:packetevents-spigot:${Versions.PACKET_EVENTS}"
    const val PLACEHOLDER_API = "me.clip:placeholderapi:${Versions.PLACEHOLDER_API}"
    const val MYTHIC_MOBS = "io.lumine:Mythic-Dist:${Versions.MYTHIC_MOBS}"
    const val GEYSER = "org.geysermc.geyser:api:${Versions.GEYSER}"
    const val VIVECRAFT = "com.cjcrafter:vivecraft:${Versions.VIVECRAFT}"

    // Adventure Chat API
    const val ADVENTURE_API = "net.kyori:adventure-api:${Versions.ADVENTURE_API}"
    const val ADVENTURE_BUKKIT = "net.kyori:adventure-platform-bukkit:${Versions.ADVENTURE_BUKKIT}"
    const val ADVENTURE_TEXT_LEGACY = "net.kyori:adventure-text-serializer-legacy:${Versions.ADVENTURE_API}"
    const val ADVENTURE_TEXT_MINIMESSAGE = "net.kyori:adventure-text-minimessage:${Versions.ADVENTURE_API}"

    // Misc
    const val JSON_SIMPLE = "com.googlecode.json-simple:json-simple:${Versions.JSON_SIMPLE}"
    const val ANNOTATIONS = "org.jetbrains:annotations:${Versions.ANNOTATIONS}"
    const val GSON = "com.google.code.gson:gson:${Versions.GSON}"
    const val X_SERIES = "com.github.cryptomorin:XSeries:${Versions.X_SERIES}"
    const val FAST_UTIL = "it.unimi.dsi:fastutil:${Versions.FAST_UTIL}"
    const val FOLIA_SCHEDULER = "com.cjcrafter:foliascheduler:${Versions.FOLIA_SCHEDULER}"
    const val COMMAND_API = "dev.jorel:commandapi-bukkit-core:${Versions.COMMAND_API}"
    const val COMMAND_API_SHADE = "dev.jorel:commandapi-bukkit-shade:${Versions.COMMAND_API}"
    const val COMMAND_API_KOTLIN = "dev.jorel:commandapi-bukkit-kotlin:${Versions.COMMAND_API}"
}

fun org.gradle.api.artifacts.dsl.DependencyHandler.adventureChatAPI() {
    add("compileOnly", Dependencies.ADVENTURE_API)
    add("compileOnly", Dependencies.ADVENTURE_BUKKIT)
    add("compileOnly", Dependencies.ADVENTURE_TEXT_LEGACY)
    add("compileOnly", Dependencies.ADVENTURE_TEXT_MINIMESSAGE)
}