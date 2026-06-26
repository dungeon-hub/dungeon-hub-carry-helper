package net.dungeonhub.carryhelper.auth

import com.mojang.brigadier.context.CommandContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.dungeonhub.auth.AuthenticationProvider
import net.dungeonhub.carryhelper.DhCarryHelper
import net.dungeonhub.carryhelper.util.MessageUtil.sendDevError
import net.dungeonhub.carryhelper.config.categories.DevCategory
import net.dungeonhub.client.DungeonHubClient
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

object AuthenticationHandler : AuthenticationProvider {
    private val logger = LoggerFactory.getLogger(AuthenticationHandler::class.java)

    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private var loadingDeviceAuthorizationCode = false
    var deviceAuthorizationCode: DeviceCodeResponse? = null
        private set

    private var offlineTokenLoadTask: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }
    private var accessTokenLoadTask: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    private var accessToken: String? = null
    private var accessTokenFailure = false

    private val supervisor = SupervisorJob()
    private val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val scheduler = CoroutineScope(supervisor + dispatcher)

    var username: String? = null
        private set

    fun shutdown() {
        scheduler.cancel()
        dispatcher.close()
    }

    fun logout() {
        offlineTokenLoadTask = null
        accessTokenLoadTask = null

        accessToken = null
        accessTokenFailure = false
        DevCategory.offlineToken = null

        DhCarryHelper.config.save()
    }

    fun isValid(): Boolean {
        return !accessTokenFailure && accessToken != null
    }

    override suspend fun getApiToken(): String {
        return accessToken ?: ""
    }

    suspend fun setup() {
        DungeonHubClient.apiUrl = DevCategory.apiUrl
        DungeonHubClient.cdnUrl = DevCategory.apiUrl + "cdn/"
        DungeonHubClient.staticUrl = DevCategory.apiUrl + "cdn/static/"

        if(DevCategory.offlineToken != null) {
            startAccessTokenRefresh()
        } else {
            loadDeviceAuthorizationCodes()
        }
    }

    suspend fun loadDeviceAuthorizationCodes(): DeviceCodeResponse? {
        if(loadingDeviceAuthorizationCode) return null

        loadingDeviceAuthorizationCode = true

        val url = DevCategory.authUrl + "protocol/openid-connect/auth/device"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(
                "client_id=dungeon-hub-carry-helper&scope=openid%20profile%20offline_access"
            ))
            .build()

        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

        val result: DeviceCodeResponse? = response.body()?.let {
            json.decodeFromString(it)
        }

        deviceAuthorizationCode = result
        loadingDeviceAuthorizationCode = false

        if(result == null) return null

        offlineTokenLoadTask = scheduler.launch {
            when (val result = pollForLogin(result)) {
                is LoginResult.Success -> {
                    val offlineToken = result.data.refreshToken

                    DevCategory.offlineToken = offlineToken
                    DhCarryHelper.config.save()

                    val username = getUsername(result.data.accessToken)
                    AuthenticationHandler.username = username

                    val message = "[CH] Successfully logged in${if (username != null) " as $username" else ""}!"

                    startAccessTokenRefresh()

                    logger.info(message)

                    Minecraft.getInstance().execute {
                        Minecraft.getInstance().gui.chat.addClientSystemMessage(
                            Component.literal(message).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))
                        )
                    }
                }
                is LoginResult.Failure -> {
                    if (result.error.error == "invalid_grant" || result.error.error == "expired") {
                        scheduler.launch {
                            loadDeviceAuthorizationCodes()
                        }
                        return@launch
                    }

                    logger.sendDevError("[CH] Failed to login: ${result.error.error}: ${result.error.errorDescription}")
                }
            }
        }

        logger.info("[CH] Successfully loaded device authorization code!")

        return result
    }

    fun startAccessTokenRefresh() {
        accessTokenLoadTask = scheduler.launch {
            val tokenUrl = DevCategory.authUrl + "protocol/openid-connect/token"

            while (true) {
                try {
                    val body = listOf(
                        "grant_type" to "refresh_token",
                        "refresh_token" to DevCategory.offlineToken,
                        "client_id" to "dungeon-hub-carry-helper"
                    ).joinToString("&") { (k, v) ->
                        "${k}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
                    }

                    val request = HttpRequest.newBuilder()
                        .uri(URI.create(tokenUrl))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build()

                    val response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                    )

                    if (response.statusCode() == 200) {
                        val result: LoginSuccess = json.decodeFromString(response.body())

                        accessToken = result.accessToken
                        accessTokenFailure = false

                        val username = getUsername(result.accessToken)
                        AuthenticationHandler.username = username

                        val delayTime = (result.expiresIn - 5).seconds
                        delay(delayTime)
                    } else {
                        val error: LoginError = json.decodeFromString(response.body())

                        val errorMessage = "[CH] Failed to login: ${error.error}: ${error.errorDescription}"

                        logger.sendDevError(errorMessage)

                        accessTokenFailure = true

                        delay(30.seconds)
                    }
                } catch (exception: Exception) {
                    if(exception is CancellationException) return@launch
                    logger.error("[CH] Failed to refresh access token!", exception)
                }
            }
        }
    }

    private fun getUsername(accessToken: String): String? {
        val claims = JwtDecoder.parseJwtClaims(accessToken)

        return claims["given_name"] ?: claims["preferred_username"]
    }

    suspend fun checkUserLogin(deviceAuthorizationCode: DeviceCodeResponse): LoginResult {
        val url = DevCategory.authUrl + "protocol/openid-connect/token"

        val body = listOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            "device_code" to deviceAuthorizationCode.deviceCode,
            "client_id" to "dungeon-hub-carry-helper"
        ).joinToString("&") { (key, value) ->
            "${key}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(
                body
            ))
            .build()

        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

        if(response.statusCode() == 200) {
            val result: LoginSuccess = json.decodeFromString(response.body())
            return LoginResult.Success(result)
        } else {
            val error: LoginError = json.decodeFromString(response.body())
            return LoginResult.Failure(error)
        }
    }

    suspend fun pollForLogin(deviceAuthorizationCode: DeviceCodeResponse): LoginResult {
        val startTime = Clock.System.now()

        while (true) {
            val elapsedSeconds = (Clock.System.now() - startTime).inWholeSeconds
            if (elapsedSeconds >= deviceAuthorizationCode.expiresIn) {
                return LoginResult.Failure(
                    LoginError(
                        error = "expired",
                        errorDescription = "Device authorization code expired"
                    )
                )
            }

            when (val result = checkUserLogin(deviceAuthorizationCode)) {
                is LoginResult.Success -> {
                    return result
                }

                is LoginResult.Failure -> {
                    if (result.error.error == "authorization_pending") {
                        // keep polling
                    } else {
                        return result
                    }
                }
            }

            delay(deviceAuthorizationCode.interval.seconds)
        }
    }

    fun CommandContext<FabricClientCommandSource>.requireLogin(): Boolean {
        if(isValid()) return true

        val deviceAuthorizationCode = deviceAuthorizationCode

        if(deviceAuthorizationCode == null) {
            source.sendError(Component.literal("[CH] Please login first! Loading authentication URL..."))

            scheduler.launch {
                val deviceAuthorizationCode = loadDeviceAuthorizationCodes()

                if(deviceAuthorizationCode == null) {
                    source.sendError(Component.literal("[CH] Failed to load the authentication URL!"))
                } else {
                    Minecraft.getInstance().execute {
                        source.sendFeedback(Component.literal("Please login: ").append(
                            Component.literal(deviceAuthorizationCode.completeVerificationUrl).setStyle(
                                Style.EMPTY
                                    .withClickEvent(ClickEvent.OpenUrl(URI.create(deviceAuthorizationCode.completeVerificationUrl)))
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                            )
                        ))
                    }
                }
            }
        } else {
            source.sendError(Component.literal("[CH] Please login first: ").append(
                Component.literal(deviceAuthorizationCode.completeVerificationUrl).setStyle(
                    Style.EMPTY
                        .withClickEvent(ClickEvent.OpenUrl(URI.create(deviceAuthorizationCode.completeVerificationUrl)))
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                )
            ))
        }

        return false
    }

    fun promptLogin(): Flow<Component>? {
        if(isValid()) return null

        val deviceAuthorizationCode = deviceAuthorizationCode

        return flow {
            if(deviceAuthorizationCode == null) {
                emit(Component.literal("[CH] Please login! Loading authentication URL...").withStyle(ChatFormatting.RED))

                val deviceAuthorizationCode = loadDeviceAuthorizationCodes()

                if(deviceAuthorizationCode == null) {
                    emit(Component.literal("[CH] Failed to load the authentication URL!").withStyle(ChatFormatting.RED))
                } else {
                    emit(Component.literal("Please login: ").append(
                        Component.literal(deviceAuthorizationCode.completeVerificationUrl).setStyle(
                            Style.EMPTY
                                .withClickEvent(ClickEvent.OpenUrl(URI.create(deviceAuthorizationCode.completeVerificationUrl)))
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                        )
                    ))
                }
            } else {
                emit(Component.literal("[CH] Please login: ").withStyle(ChatFormatting.RED).append(
                    Component.literal(deviceAuthorizationCode.completeVerificationUrl).setStyle(
                        Style.EMPTY
                            .withClickEvent(ClickEvent.OpenUrl(URI.create(deviceAuthorizationCode.completeVerificationUrl)))
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                    )
                ))
            }
        }
    }

    @Serializable
    data class DeviceCodeResponse(
        @SerialName("device_code")
        val deviceCode: String,
        @SerialName("verification_uri_complete")
        val completeVerificationUrl: String,
        @SerialName("expires_in")
        val expiresIn: Int,
        @SerialName("interval")
        val interval: Int
    )

    @Serializable
    data class LoginSuccess(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("expires_in")
        val expiresIn: Long,

        @SerialName("refresh_token")
        val refreshToken: String
    )

    @Serializable
    data class LoginError(
        val error: String,
        @SerialName("error_description")
        val errorDescription: String? = null
    )

    sealed class LoginResult {
        data class Success(val data: LoginSuccess) : LoginResult()
        data class Failure(val error: LoginError) : LoginResult()
    }
}