package net.dungeonhub.carryhelper.client.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

object JwtDecoder {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseJwtClaims(token: String): Map<String, String> {
        val parts = token.split(".")

        require(parts.size == 3) {
            "Invalid JWT token format"
        }

        val payload = String(
            Base64.getUrlDecoder().decode(parts[1])
        )

        val jsonObject = json.parseToJsonElement(payload) as JsonObject

        return jsonObject.mapNotNull {
            if(it.value is JsonPrimitive) {
                it.key to it.value.jsonPrimitive.content
            } else {
                null
            }
        }.toMap()
    }
}