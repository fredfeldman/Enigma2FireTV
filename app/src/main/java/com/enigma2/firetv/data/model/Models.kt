package com.enigma2.firetv.data.model

import com.google.gson.annotations.SerializedName

// ---------- Bouquet / Service list ----------

data class ServicesResponse(
    @SerializedName("services") val bouquets: List<Bouquet>?
)

data class GetServicesResponse(
    @SerializedName("result") val result: Boolean,
    @SerializedName("services") val services: List<Service>?
)

data class Bouquet(
    /** Service reference of the bouquet itself */
    @SerializedName("servicereference") val ref: String,
    @SerializedName("servicename") val name: String,
    @SerializedName("subservices") val channels: List<Service>?
)

data class Service(
    @SerializedName("servicereference") val ref: String,
    @SerializedName("servicename") val name: String,
    /** Optional picon URL suffix provided by OpenWebif */
    @SerializedName("picon") val piconPath: String? = null
)

// ---------- EPG ----------

data class EpgResponse(
    @SerializedName("events") val events: List<EpgEvent>?,
    @SerializedName("result") val result: Boolean
)

data class EpgEvent(
    @SerializedName("id") val id: Long,
    @SerializedName("sref") val serviceRef: String,
    @SerializedName("sname") val serviceName: String,
    @SerializedName("title") val title: String,
    @SerializedName("shortdesc") val shortDesc: String?,
    @SerializedName("longdesc") val longDesc: String?,
    @SerializedName("begin_timestamp") val beginTimestamp: Long,  // Unix timestamp (seconds)
    @SerializedName("duration_sec") val durationSeconds: Int      // Duration in seconds
) {
    val beginMs: Long get() = beginTimestamp * 1000L
    val endTimestamp: Long get() = beginTimestamp + durationSeconds
    val endMs: Long get() = endTimestamp * 1000L
}

// ---------- Now/Next (channel overview) ----------

data class NowNextResponse(
    @SerializedName("events") val events: List<NowNextEvent>?,
    @SerializedName("result") val result: Boolean
)

data class NowNextEvent(
    @SerializedName("now_event") val nowEvent: EpgEvent?,
    @SerializedName("next_event") val nextEvent: EpgEvent?,
    @SerializedName("sref") val serviceRef: String,
    @SerializedName("sname") val serviceName: String
)

// ---------- Current stream info ----------

data class CurrentService(
    val service: Service,
    val nowEvent: EpgEvent?,
    val nextEvent: EpgEvent?
)

// ---------- Recordings ----------

data class MovieListResponse(
    @SerializedName("movies") val movies: List<Recording>?
)

data class Recording(
    /** Full path on the receiver, e.g. /hdd/movie/20250306 1900 - BBC One - News.ts */
    @SerializedName("filename") val filename: String?,
    /** Recording event/show name. */
    @SerializedName("eventname") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("descriptionExtended") val descriptionExtended: String?,
    /** Full service reference including file path — used for streaming. */
    @SerializedName("serviceref") val serviceRef: String?,
    @SerializedName("servicename") val channelName: String?,
    /** Recording start time as Unix timestamp (seconds). */
    @SerializedName("recordingtime") val startTimestamp: Long,
    /** Duration as pre-formatted string, e.g. "1:30" (hours:minutes). */
    @SerializedName("length") val length: String?,
    @SerializedName("filesize") val fileSizeBytes: Long?
) {
    val startMs: Long get() = startTimestamp * 1000L

    /** Returns the duration string as-is (OpenWebif already formats it as "H:MM"). */
    fun formatDuration(): String = length?.takeIf { it.isNotBlank() && it != "?:??" } ?: ""

    /** Duration converted to seconds for PlayerActivity (parses "H:MM" format). */
    val durationInSeconds: Int get() {
        val parts = length?.split(":") ?: return 0
        return try {
            val h = parts[0].trim().toInt()
            val m = parts.getOrNull(1)?.trim()?.toInt() ?: 0
            (h * 60 + m) * 60
        } catch (_: NumberFormatException) { 0 }
    }

    /** Display title — falls back to filename stem if eventname is blank. */
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: filename?.substringAfterLast('/')?.substringBeforeLast('.')
            ?: ""

    /** Best available description (extended preferred). */
    val synopsis: String
        get() = descriptionExtended?.takeIf { it.isNotBlank() }
            ?: description?.takeIf { it.isNotBlank() }
            ?: ""
}

// ---------- Timer ----------

data class TimerResponse(
    @SerializedName("result") val result: Boolean,
    @SerializedName("message") val message: String?
)
