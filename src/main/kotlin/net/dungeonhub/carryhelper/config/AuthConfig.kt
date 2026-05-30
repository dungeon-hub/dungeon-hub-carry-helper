package net.dungeonhub.carryhelper.config

import com.teamresourceful.resourcefulconfig.api.types.options.TranslatableValue
import com.teamresourceful.resourcefulconfigkt.api.ConfigKt
import net.dungeonhub.carryhelper.DhCarryHelper

object AuthConfig : ConfigKt("${DhCarryHelper.MOD_ID}/auth") {
    override val name: TranslatableValue
        get() = Literal("DH Carry Helper ${DhCarryHelper.version}")

    var apiUrl by string("api_url", "https://api.dungeon-hub.net/") {
        name = Literal("API URL")
    }

    var authUrl by string("auth_url", "https://auth.dungeon-hub.net/realms/dungeon-hub/") {
        name = Literal("Auth URL")
    }

    private var _offlineToken by string("api_token", "") {
        name = Literal("API Token")
    }

    var offlineToken: String?
        get() = _offlineToken.takeIf { it.isNotBlank() }
        set(value) {
            _offlineToken = value ?: ""
        }
}