package com.enigma2.firetv.data.api

import com.enigma2.firetv.data.model.EpgResponse
import com.enigma2.firetv.data.model.GetServicesResponse
import com.enigma2.firetv.data.model.MovieListResponse
import com.enigma2.firetv.data.model.NowNextResponse
import com.enigma2.firetv.data.model.ServicesResponse
import com.enigma2.firetv.data.model.TimerDeleteResponse
import com.enigma2.firetv.data.model.TimerListResponse
import com.enigma2.firetv.data.model.TimerResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

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
     * Returns the currently-airing event for every service in a bouquet.
     */
    @GET("api/epgnow")
    suspend fun getEpgNow(@Query("bRef") bouquetRef: String): EpgResponse

    /**
     * Returns the next event for every service in a bouquet.
     */
    @GET("api/epgnext")
    suspend fun getEpgNext(@Query("bRef") bouquetRef: String): EpgResponse

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

    /**
     * Adds a timer (recording) on the receiver.
     * @param sRef     Service reference of the channel to record.
     * @param begin    Recording start time as Unix timestamp (seconds).
     * @param end      Recording end time as Unix timestamp (seconds).
     * @param name     Event/show name (used as the recording filename prefix).
     * @param eit      EPG event ID (optional, helps the receiver link the timer to EPG data).
     * @param justPlay 0 = record (default), 1 = zap only.
     */
    @GET("api/timeradd")
    suspend fun addTimer(
        @Query("sRef") sRef: String,
        @Query("begin") begin: Long,
        @Query("end") end: Long,
        @Query("name") name: String,
        @Query("eit") eit: Long = 0,
        @Query("justplay") justPlay: Int = 0
    ): TimerResponse

    /**
     * Returns the list of all timers currently scheduled on the receiver.
     */
    @GET("api/timerlist")
    suspend fun getTimerList(): TimerListResponse

    /**
     * Deletes a timer identified by service reference + begin + end times.
     */
    @GET("api/timerdelete")
    suspend fun deleteTimer(
        @Query("sRef") sRef: String,
        @Query("begin") begin: Long,
        @Query("end") end: Long
    ): TimerDeleteResponse

    /**
     * Toggles a timer's disabled state (enable <-> disable).
     */
    @GET("api/timertogglestatus")
    suspend fun toggleTimerStatus(
        @Query("sRef") sRef: String,
        @Query("begin") begin: Long,
        @Query("end") end: Long
    ): TimerDeleteResponse

    /**
     * Deletes a recording on the receiver. [sRef] must be the recording's full
     * service reference (which encodes the file path).
     */
    @GET("api/movieDelete")
    suspend fun deleteMovie(@Query("sRef") sRef: String): TimerDeleteResponse

    /**
     * Searches EPG across all services for events matching [query].
     */
    @GET("api/epgsearch")
    suspend fun searchEpg(@Query("search") query: String): EpgResponse

    /**
     * Returns a screenshot of the currently displayed image on the receiver.
     * The response body is a JPEG image.
     */
    @Streaming
    @GET("api/screenshot")
    suspend fun getScreenshot(): ResponseBody

    // -------------------------------------------------------------------------
    // AutoTimer plugin (XML responses; parsed by AutoTimerXml)
    // -------------------------------------------------------------------------

    /**
     * Returns the full list of AutoTimer rules as the plugin's native XML.
     */
    @GET("autotimer/get")
    suspend fun getAutoTimers(): ResponseBody

    /**
     * Adds or edits an AutoTimer rule.
     *
     * Pass [id] = -1 (or omit) to add a new rule; passing an existing id updates that rule.
     * [services] is a repeated `services` query parameter — one entry per service reference.
     * The receiver returns a `<e2simplexmlresult>` body which the caller parses via
     * [com.enigma2.firetv.data.api.AutoTimerXml.parseSimpleResult].
     */
    @GET("autotimer/edit")
    suspend fun editAutoTimer(
        @Query("id") id: Int,
        @Query("name") name: String,
        @Query("match") match: String,
        @Query("enabled") enabled: String,
        @Query("searchType") searchType: String = "partial",
        @Query("searchCase") searchCase: String = "insensitive",
        @Query("encoding") encoding: String = "UTF-8",
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("services") services: List<String>? = null
    ): ResponseBody

    /**
     * Deletes the AutoTimer rule with the given numeric id.
     */
    @GET("autotimer/remove")
    suspend fun removeAutoTimer(@Query("id") id: Int): ResponseBody

    /**
     * Asks the receiver to scan the EPG now and create timers for any matching events.
     */
    @GET("autotimer/parse")
    suspend fun parseAutoTimers(): ResponseBody

    /**
     * Returns receiver hardware/software info: brand, model, image version, kernel,
     * uptime, tuners, hard disks, network interfaces, etc. Wrapped in `{ "info": {...} }`.
     */
    @GET("api/about")
    suspend fun getAbout(): Map<String, Any>
}
