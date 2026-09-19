package com.shiv.rally.data.remote.stremio

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface StremioApi {

    @GET
    suspend fun getManifest(@Url manifestUrl: String): StremioManifest

    @GET
    suspend fun getCatalogByUrl(@Url catalogUrl: String): StremioCatalogResponse

    @GET
    suspend fun getStreamsByUrl(@Url streamUrl: String): StremioStreamResponse

    @GET("catalog/{type}/{id}.json")
    suspend fun getCatalog(
        @Path("type") type: String,
        @Path("id") id: String
    ): StremioCatalogResponse

    @GET("catalog/{type}/{id}/{extra}.json")
    suspend fun getCatalogWithExtra(
        @Path("type") type: String,
        @Path("id") id: String,
        @Path("extra") extra: String
    ): StremioCatalogResponse

    @GET("stream/{type}/{id}.json")
    suspend fun getStreams(
        @Path("type") type: String,
        @Path("id") id: String
    ): StremioStreamResponse
}

data class StremioManifest(
    val id: String?,
    val name: String?,
    val description: String?,
    val version: String?,
    val types: List<String>?,
    val catalogs: List<StremioCatalogDesc>?
)

data class StremioCatalogDesc(
    val type: String?,
    val id: String?,
    val name: String?
)

data class StremioCatalogResponse(
    val metas: List<StremioMetaItem>?
)

data class StremioMetaItem(
    val id: String,
    val type: String?,
    val name: String?,
    val poster: String?,
    val banner: String?,
    val description: String?,
    val genres: List<String>?
)

data class StremioStreamResponse(
    val streams: List<StremioStream>?
)

data class StremioStream(
    val name: String?,
    val title: String?,
    val description: String?,
    val url: String?,
    val externalUrl: String?,
    val ytId: String?,
    val behaviorHints: StremioBehaviorHints? = null
)

data class StremioBehaviorHints(
    val proxyHeaders: StremioProxyHeaders? = null,
    val notWebReady: Boolean? = null
)

data class StremioProxyHeaders(
    val request: Map<String, String>? = null
)
