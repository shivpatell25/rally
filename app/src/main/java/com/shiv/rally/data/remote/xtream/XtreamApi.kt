package com.shiv.rally.data.remote.xtream

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/** Xtream Codes APIs are configured per account, so every request supplies its full URL. */
interface XtreamApi {
    @GET
    suspend fun request(@Url url: String): Response<JsonElement>
}
