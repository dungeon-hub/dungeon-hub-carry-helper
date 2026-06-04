package net.dungeonhub.carryhelper.config.categories

import com.teamresourceful.resourcefulconfig.api.types.options.TranslatableValue
import com.teamresourceful.resourcefulconfigkt.api.CategoryKt
import net.dungeonhub.carryhelper.DhCarryHelper.MOD_ID
import net.dungeonhub.carryhelper.config.Config

object AuthConfig : CategoryKt("$MOD_ID/auth") {
    override val name: TranslatableValue
        get() = Literal("Developer")

    override val hidden: Boolean
        get() = !Config.developer

    var extendedDebug by boolean("extended_debug", false) {
        name = Literal("Extended Debug output")
        description = Literal("This increases the amount of information sent to you ingame.")
    }

    var apiUrl by string("api_url", "https://api.dungeon-hub.net/") {
        name = Literal("API URL")
        description = Literal("The API URL of the dungeon-hub-server.")
    }

    var authUrl by string("auth_url", "https://auth.dungeon-hub.net/realms/dungeon-hub/") {
        name = Literal("Auth URL")
        description = Literal("The URL of the auth server (Keycloak URL including the realm).")
    }

    private var _offlineToken by string("api_token", "") {
        name = Literal("API Token")
        description = Literal("This is the offline token, used to retrieve the access token through Keycloak.")
    }

    var offlineToken: String?
        get() = _offlineToken.takeIf { it.isNotBlank() }
        set(value) {
            _offlineToken = value ?: ""
        }
}