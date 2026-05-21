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
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Streaming
import retrofit2.http.Url

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

    // -------------------------------------------------------------------------
    // BouquetEditor plugin (XML responses; parsed by BouquetEditorXml)
    // -------------------------------------------------------------------------

    /**
     * Lists bouquets via the BouquetEditor plugin (`/bouqueteditor/api/getservices`).
     * With no `sRef`, the plugin returns the TV bouquet root; pass the radio root to
     * list radio bouquets. Same JSON shape as the core OpenWebif `getservices`.
     * Also serves as the capability probe — a 404 means the plugin is not installed.
     */
    @GET("bouqueteditor/api/getservices")
    suspend fun getBouquetEditorBouquets(
        @Query("sRef") sRef: String = ""
    ): ServicesResponse

    /**
     * Creates a new empty user bouquet.
     * @param mode 0 = TV, 1 = Radio (Enigma2 `MODE_TV` / `MODE_RADIO`).
     *
     * Mutation endpoints use `/bouqueteditor/web/...` which returns the
     * `<e2simplexmlresult>` shape we already parse for AutoTimer. The `/api/`
     * sibling returns `{"Result":[bool,"msg"]}` JSON instead.
     */
    @GET("bouqueteditor/web/addbouquet")
    suspend fun addBouquet(
        @Query("name") name: String,
        @Query("mode") mode: Int = 0
    ): ResponseBody

    /**
     * Renames a bouquet. The BouquetEditor plugin doesn't expose a dedicated
     * `renamebouquet` endpoint — instead `renameservice` accepts a bouquet ref
     * and uses `setListName` when the ref points at a bouquet (mustDescent flag).
     */
    @GET("bouqueteditor/web/renameservice")
    suspend fun renameBouquet(
        @Query("sRef") sRef: String,
        @Query("newName") newName: String,
        @Query("mode") mode: Int = 0
    ): ResponseBody

    /** Deletes a user bouquet by reference. */
    @GET("bouqueteditor/web/removebouquet")
    suspend fun removeBouquet(
        @Query("sBouquetRef") sBouquetRef: String,
        @Query("mode") mode: Int = 0
    ): ResponseBody

    /** Adds a service to a user bouquet (appended at the end). */
    @GET("bouqueteditor/web/addservicetobouquet")
    suspend fun addServiceToBouquet(
        @Query("sBouquetRef") sBouquetRef: String,
        @Query("sRef") sRef: String,
        @Query("Name") name: String
    ): ResponseBody

    /** Removes a service from a user bouquet. */
    @GET("bouqueteditor/web/removeservice")
    suspend fun removeServiceFromBouquet(
        @Query("sBouquetRef") sBouquetRef: String,
        @Query("sRef") sRef: String
    ): ResponseBody

    /** Moves a service to a new zero-based [position] inside a user bouquet. */
    @GET("bouqueteditor/web/moveservice")
    suspend fun moveServiceInBouquet(
        @Query("sBouquetRef") sBouquetRef: String,
        @Query("sRef") sRef: String,
        @Query("position") position: Int
    ): ResponseBody

    // ---- Generic file access (OpenWebif FileController) -----------------
    // Used by the EPGImport viewer to enumerate `/etc/epgimport/*.sources.xml`
    // and download their contents. Responses are JSON for `?dir=` and raw
    // bytes for `?file=&action=download`.

    /**
     * Lists files in [dir] matching [pattern]. Returns
     * `{ "result": true|false, "dirs": [...], "files": [...] }`.
     */
    @GET("file")
    suspend fun listFiles(
        @Query("dir") dir: String,
        @Query("pattern") pattern: String = "*"
    ): com.enigma2.firetv.data.model.FileListResponse

    /** Downloads the raw contents of [file]. */
    @Streaming
    @GET("file")
    suspend fun downloadFile(
        @Query("file") file: String,
        @Query("action") action: String = "download"
    ): ResponseBody

    // =========================================================================
    // 1.1.0 PORT — Phase 0 foundations: new endpoints from Enigma2Android v1.0.6
    //                                   through v1.1.1 (ports of the sibling
    //                                   project's OpenWebifService.kt verbatim
    //                                   where paths are concerned).
    // =========================================================================

    // ---- v1.0.6: Receiver Settings ----

    /** Live status — header on most receiver-settings screens. */
    @GET("api/statusinfo")
    suspend fun getStatusInfo(): Response<ResponseBody>

    // Power
    @GET("api/powerstate")
    suspend fun getPowerState(): Response<ResponseBody>

    @GET("api/powerstate")
    suspend fun setPowerState(@Query("newstate") newState: Int): Response<ResponseBody>

    @GET("web/sleeptimer")
    suspend fun getSleepTimer(@Query("cmd") cmd: String = "get"): Response<ResponseBody>

    @GET("web/sleeptimer")
    suspend fun setSleepTimer(
        @Query("cmd") cmd: String = "set",
        @Query("time") time: Int,
        @Query("action") action: String,
        @Query("enabled") enabled: String
    ): Response<ResponseBody>

    // Volume
    @GET("web/vol")
    suspend fun getVolume(): Response<ResponseBody>

    /** [set] = "setNN" for an absolute volume, or "mute" to toggle mute. */
    @GET("web/vol")
    suspend fun setVolume(@Query("set") set: String): Response<ResponseBody>

    // Generic config tree
    @GET("web/settings")
    suspend fun getAllSettings(): Response<ResponseBody>

    @GET("api/config")
    suspend fun getConfigSections(): Response<ResponseBody>

    @GET
    suspend fun getConfigSection(@Url url: String): Response<ResponseBody>

    @GET("web/saveconfig")
    suspend fun saveConfig(
        @Query("key") key: String,
        @Query("value") value: String
    ): Response<ResponseBody>

    // OpenWebif Web UI config (six toggles)
    @GET("web/setwebconfig")
    suspend fun setWebConfig(@QueryMap params: Map<String, String>): Response<ResponseBody>

    // Parental control (read)
    @GET("web/parentcontrollist")
    suspend fun getParentControlList(): Response<ResponseBody>

    @GET("BQE/getprotectionsettings")
    suspend fun getProtectionSettings(): Response<ResponseBody>

    // Recording locations
    @GET("api/getlocations")
    suspend fun getLocations(): Response<ResponseBody>

    @GET("api/getcurrlocation")
    suspend fun getCurrentLocation(): Response<ResponseBody>

    @GET("api/setcurrlocation")
    suspend fun setCurrentLocation(@Query("location") location: String): Response<ResponseBody>

    @GET("api/addlocation")
    suspend fun addLocation(
        @Query("dirname") dirname: String,
        @Query("createFolder") createFolder: Int = 1
    ): Response<ResponseBody>

    @GET("api/removelocation")
    suspend fun removeLocation(@Query("dirname") dirname: String): Response<ResponseBody>

    // Tuner / signal
    @GET("web/tunersignal")
    suspend fun getTunerSignal(): Response<ResponseBody>

    // Wake-on-LAN setup (receiver-side)
    @GET("wol/setup")
    suspend fun getWolSetup(): Response<ResponseBody>

    @GET("wol/setup")
    suspend fun setWolSetup(@QueryMap params: Map<String, String>): Response<ResponseBody>

    // Transcoding plugin
    @GET("transcoding")
    suspend fun getTranscodingConfig(): Response<ResponseBody>

    @GET("transcoding")
    suspend fun setTranscodingConfig(@QueryMap params: Map<String, String>): Response<ResponseBody>

    // ---- v1.0.7: Remote control & messaging ----

    /** Linux input keycode (e.g. 352 = OK, 412 = back, 116 = power). */
    @GET("api/remotecontrol")
    suspend fun sendRemoteCommand(@Query("command") commandCode: Int): Response<ResponseBody>

    @GET("api/message")
    suspend fun sendMessage(
        @Query("text") text: String,
        @Query("type") type: Int = 1,
        @Query("timeout") timeout: Int = 10
    ): Response<ResponseBody>

    // ---- v1.0.8: Recording management ----

    @GET("api/movierename")
    suspend fun renameMovie(
        @Query("sRef") sRef: String,
        @Query("newname") newName: String
    ): Response<ResponseBody>

    @GET("api/moviemove")
    suspend fun moveMovie(
        @Query("sRef") sRef: String,
        @Query("dirname") newDir: String
    ): Response<ResponseBody>

    @GET("api/movietags")
    suspend fun movieTags(
        @Query("sRef") sRef: String,
        @Query("add") add: String? = null,
        @Query("del") del: String? = null
    ): Response<ResponseBody>

    /** Returns the list of all tags configured on the receiver. */
    @GET("api/gettags")
    suspend fun getTags(): Response<ResponseBody>

    // ---- v1.1.0: Parental write ----

    /** action: "add" or "remove". */
    @GET("api/parentcontrol")
    suspend fun parentalProtect(
        @Query("sRef") sRef: String,
        @Query("action") action: String,
        @Query("type") type: String? = null
    ): Response<ResponseBody>

    @GET("api/changesetuppin")
    suspend fun changeSetupPin(
        @Query("newpin") newPin: String,
        @Query("oldpin") oldPin: String
    ): Response<ResponseBody>

    // ---- v1.1.0: Storage / system / plugins / network ----

    @GET("api/mountinfo")
    suspend fun getMountInfo(): Response<ResponseBody>

    @GET("api/smartinfo")
    suspend fun getSmartInfo(): Response<ResponseBody>

    @GET("api/getlog")
    suspend fun getReceiverLog(): Response<ResponseBody>

    @GET("api/plugins")
    suspend fun listPlugins(): Response<ResponseBody>

    @GET("api/installplugin")
    suspend fun installPlugin(@Query("package") pkg: String): Response<ResponseBody>

    @GET("api/removeplugin")
    suspend fun removePlugin(@Query("package") pkg: String): Response<ResponseBody>

    @GET("api/networkinfo")
    suspend fun getNetworkInfo(): Response<ResponseBody>

    // ---- v1.1.1: EPG refresh ----

    @GET("api/serviceupdateepg")
    suspend fun refreshEpgForService(@Query("sRef") sRef: String): Response<ResponseBody>

    @GET("web/epgrefresh")
    suspend fun triggerEpgRefresh(): Response<ResponseBody>

    // ---- v1.2.0 Phase 7: EPG Assign companion plugin ----

    @GET("epgassign/ping")
    suspend fun epgAssignPing(): Response<ResponseBody>

    @GET("epgassign/sources")
    suspend fun epgAssignSources(): Response<ResponseBody>

    @GET("epgassign/source")
    suspend fun epgAssignSource(@Query("name") name: String): Response<ResponseBody>

    @GET("epgassign/mappings")
    suspend fun epgAssignMappings(): Response<ResponseBody>

    @GET("epgassign/assign")
    suspend fun epgAssign(
        @Query("sref") sRef: String,
        @Query("channelId") channelId: String,
        @Query("source") source: String,
        @Query("name") name: String
    ): Response<ResponseBody>

    @GET("epgassign/unassign")
    suspend fun epgAssignUnassign(@Query("sref") sRef: String): Response<ResponseBody>

    @GET("epgassign/import")
    suspend fun epgAssignImport(): Response<ResponseBody>
}
