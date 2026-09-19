package com.shiv.rally.data.remote.stalker

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface StalkerApi {
    
    @GET("server/load.php")
    suspend fun handshake(
        @Query("type") type: String,
        @Query("action") action: String
    ): StalkerResponse<JsonElement>

    @GET("server/load.php")
    suspend fun getProfile(
        @Query("type") type: String,
        @Query("action") action: String,
        @Header("Authorization") token: String
    ): StalkerResponse<JsonElement>

    @GET("server/load.php")
    suspend fun getOrderedList(
        @Query("type") type: String,
        @Query("action") action: String,
        @Query("p") page: Int,
        @Header("Authorization") token: String
    ): StalkerResponse<JsonElement>

    @GET("server/load.php")
    suspend fun getAllChannels(
        @Query("type") type: String,
        @Query("action") action: String,
        @Header("Authorization") token: String
    ): StalkerResponse<JsonElement>
    
    @GET("server/load.php")
    suspend fun getGenres(
        @Query("type") type: String,
        @Query("action") action: String,
        @Header("Authorization") token: String
    ): StalkerResponse<JsonElement>

    @GET("server/load.php")
    suspend fun getShortEpg(
        @Query("type") type: String,
        @Query("action") action: String,
        @Query("ch_id") channelId: String,
        @Query("size") size: Int,
        @Header("Authorization") token: String
    ): StalkerResponse<JsonElement>

    @GET("server/load.php")
    suspend fun createLink(
        @Query("type") type: String,
        @Query("action") action: String,
        @Query("cmd") cmd: String,
        @Header("Authorization") token: String
    ): StalkerResponse<JsonElement>
}

data class StalkerResponse<T>(
    val js: T? = null,
    val error: String? = null
)

data class HandshakeData(val token: String)
data class ProfileData(val id: Int? = null, val mac: String? = null)
data class StalkerChannel(
    val id: String,
    val name: String,
    val number: String,
    val logo: String? = null,
    val tv_genre_id: String? = null,
    val cmd: String,
    val supportsCatchUp: Boolean = false,
    val archiveDurationHours: Int? = null
)
data class LinkData(val cmd: String)
