package com.enigma2.firetv.data.api

import com.enigma2.firetv.data.model.EpgResponse
import com.enigma2.firetv.data.model.GetServicesResponse
import com.enigma2.firetv.data.model.MovieListResponse
import com.enigma2.firetv.data.model.NowNextResponse
import com.enigma2.firetv.data.model.ServicesResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the OpenWebif REST API exposed by Enigma2 receivers.
 *
 * Base URL: http://<receiver_ip>/
 * Optional HTTP Basic Auth is handled inside [ApiClient].
 */
interface OpenWebifService {

    /**
     * Returns all bouquets with their child services.
     * Equivalent to: GET /api/getallservices
     */
    @GET("api/getallservices")
    suspend fun getAllServices(): ServicesResponse

    /**
     * Returns channels inside a specific bouquet.
     * sRef = service reference of the bouquet, e.g. "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET ..."
     */
    @GET("api/getservices")
    suspend fun getServices(@Query("sRef") bouquetRef: String): GetServicesResponse

    /**
     * Returns EPG events for a single service reference (full schedule).
     */
    @GET("api/epgservice")
    suspend fun getEpgForService(@Query("sRef") serviceRef: String): EpgResponse

    /**
     * Returns multi-service EPG (now + schedule) for all services in a bouquet.
     */
    @GET("api/epgmulti")
    suspend fun getMultiEpg(@Query("bRef") bouquetRef: String): EpgResponse

    /**
     * Returns currently-airing and next event for every service in a bouquet.
     */
    @GET("api/epgmulti")
    suspend fun getNowNext(@Query("bRef") bouquetRef: String): NowNextResponse

    /**
     * Zap the receiver to a given service (optional – changes the live output on the box).
     */
    @GET("api/zap")
    suspend fun zapToService(@Query("sRef") serviceRef: String): Map<String, Any>

    /**
     * Returns the list of recordings.
     * Optional [dirname] limits results to a specific folder; omit for all recordings.
     */
    @GET("api/movielist")
    suspend fun getMovieList(@Query("dirname") dirname: String? = null): MovieListResponse
}
