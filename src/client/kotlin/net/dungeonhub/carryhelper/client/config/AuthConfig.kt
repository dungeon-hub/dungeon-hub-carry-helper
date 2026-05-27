package net.dungeonhub.carryhelper.client.config

import com.teamresourceful.resourcefulconfig.api.annotations.Config
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry

// TODO use the kt config variant - then we can remove the jvm field annotation
@Config(value = "dh-carry-helper/auth")
object AuthConfig {
    @JvmField
    @ConfigEntry(id = "api_url", translation = "API URL")
    var apiUrl: String = "https://api.dungeon-hub.net/"

    @JvmField
    @ConfigEntry(id = "auth_url", translation = "Auth URL")
    var authUrl: String = "https://auth.dungeon-hub.net/realms/dungeon-hub/"

    @JvmField
    @ConfigEntry(id = "api_token", translation = "API Token")
    var offlineToken: String = ""

    fun getOfflineToken(): String? = offlineToken.takeIf { it.isNotBlank() }
}