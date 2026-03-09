package com.enigma2.firetv.data.repository

import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.model.NowNextEvent
import com.enigma2.firetv.data.model.Recording
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.model.TimerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for all Enigma2 data.
 * All network calls are dispatched on [Dispatchers.IO].
 */
class Enigma2Repository {

    // -------------------------------------------------------------------------
    // Bouquets & Channels
    // -------------------------------------------------------------------------

    /**
     * Fetches all top-level bouquets (TV channel lists).
     * Returns an empty list on error.
     */
    suspend fun getBouquets(): List<Bouquet> = withContext(Dispatchers.IO) {
        ApiClient.service.getAllServices().bouquets ?: emptyList()
    }

    /**
     * Fetches child services (channels) for a given bouquet reference.
     */
    suspend fun getChannels(bouquetRef: String): List<Service> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getServices(bouquetRef).services ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "getChannels failed", e)
            emptyList()
        }
    }
    // -------------------------------------------------------------------------
    // Recordings
    // -------------------------------------------------------------------------

    /**
     * Fetches all recordings from the receiver's default recording location.
     * Returns an empty list on error.
     */
    suspend fun getRecordings(): List<Recording> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getMovieList().movies ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "getRecordings failed", e)
            emptyList()
        }
    }

    /**
     * Fetches video files from a specific directory on the receiver (e.g. /media/hdd/video).
     * Uses the same movielist API with a dirname filter.
     * Returns an empty list on error.
     */
    suspend fun getVideoFiles(dirname: String): List<Recording> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getMovieList(dirname).movies ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "getVideoFiles failed", e)
            emptyList()
        }
    }
    // -------------------------------------------------------------------------
    // EPG
    // -------------------------------------------------------------------------

    /**
     * Returns the full EPG schedule for a single service.
     */
    suspend fun getEpgForService(serviceRef: String): List<EpgEvent> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getEpgForService(serviceRef).events ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns EPG events for all services in a bouquet (multi-service EPG).
     */
    suspend fun getMultiEpg(bouquetRef: String): List<EpgEvent> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getMultiEpg(bouquetRef).events ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns now/next event pairs for each service in a bouquet.
     */
    suspend fun getNowNext(bouquetRef: String): List<NowNextEvent> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getNowNext(bouquetRef).events ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Timers
    // -------------------------------------------------------------------------

    /**
     * Adds a recording timer on the receiver for the given EPG event.
     * Returns a [TimerResponse] with [TimerResponse.result] == true on success.
     */
    suspend fun addTimer(event: EpgEvent): TimerResponse = withContext(Dispatchers.IO) {
        ApiClient.service.addTimer(
            sRef = event.serviceRef,
            begin = event.beginTimestamp,
            end = event.endTimestamp,
            name = event.title,
            eit = event.id
        )
    }

    // -------------------------------------------------------------------------
    // Zap
    // -------------------------------------------------------------------------

    suspend fun zapToService(serviceRef: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = ApiClient.service.zapToService(serviceRef)
            result["result"] as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }
}
