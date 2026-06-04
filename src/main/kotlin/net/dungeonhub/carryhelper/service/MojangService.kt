package net.dungeonhub.carryhelper.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object MojangService {
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val profileCache = ConcurrentHashMap<UUID, String>()
    private val fetchingByUuid = ConcurrentHashMap<UUID, Boolean>()
    private val fetchingByName = ConcurrentHashMap<String, Boolean>()

    private val supervisor = SupervisorJob()
    private val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val scheduler = CoroutineScope(supervisor + dispatcher)

    fun getPlayerName(uuid: UUID): String? {
        // Check cache first
        profileCache[uuid]?.let { return it }

        // Trigger async fetch if not already fetching
        if (fetchingByUuid.putIfAbsent(uuid, true) == null) {
            fetchByUuid(uuid)
        }

        return null
    }

    suspend fun awaitPlayerName(uuid: UUID, maxRetries: Int = 10, delayDuration: Duration = 100.milliseconds): String? {
        repeat(maxRetries) {
            getPlayerName(uuid)?.let { return it }
            delay(delayDuration)
        }
        return null
    }

    fun getPlayerUuid(name: String): UUID? {
        val normalizedName = name.lowercase()

        // Check cache by scanning values
        profileCache.entries.find { it.value.equals(normalizedName, ignoreCase = true) }?.let {
            return it.key
        }

        // Trigger async fetch if not already fetching
        if (fetchingByName.putIfAbsent(normalizedName, true) == null) {
            fetchByName(normalizedName)
        }

        // Return null while loading
        return null
    }

    private fun fetchByUuid(uuid: UUID) {
        scheduler.launch {
            try {
                val uuidString = uuid.toString().replace("-", "")
                val url = "https://api.minecraftservices.com/minecraft/profile/lookup/$uuidString"

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build()

                val response = withContext(Dispatchers.IO) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }

                if (response.statusCode() == 200) {
                    val mojangProfile = json.decodeFromString<MojangProfile>(response.body())
                    profileCache[uuid] = mojangProfile.name
                } else {
                    profileCache[uuid] = uuid.toString().substring(0, 8)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                profileCache[uuid] = uuid.toString().substring(0, 8)
            } finally {
                fetchingByUuid.remove(uuid)
            }
        }
    }

    private fun fetchByName(name: String) {
        scheduler.launch {
            try {
                val url = "https://api.minecraftservices.com/minecraft/profile/lookup/name/$name"

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build()

                val response = withContext(Dispatchers.IO) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }

                if (response.statusCode() == 200) {
                    val mojangProfile = json.decodeFromString<MojangProfile>(response.body())
                    val uuid = UUID.fromString(
                        mojangProfile.id.replace(
                            Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"),
                            "$1-$2-$3-$4-$5"
                        )
                    )
                    profileCache[uuid] = mojangProfile.name
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                fetchingByName.remove(name)
            }
        }
    }

    @Serializable
    private data class MojangProfile(
        val id: String,
        val name: String
    )
}