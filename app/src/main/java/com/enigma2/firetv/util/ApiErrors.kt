package com.enigma2.firetv.util

import android.content.Context
import com.enigma2.firetv.R
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Translates raw network/HTTP exceptions into short, user-friendly strings.
 *
 * Falls back to the throwable's message (or a generic catch-all) when no
 * specific case matches — never returns the bare class name.
 */
object ApiErrors {

    fun userMessage(context: Context, t: Throwable?): String {
        if (t == null) return context.getString(R.string.error_unknown)
        return when (t) {
            is UnknownHostException -> context.getString(R.string.error_host_not_found)
            is SocketTimeoutException -> context.getString(R.string.error_timeout)
            is SSLException -> context.getString(R.string.error_ssl)
            is HttpException -> when (t.code()) {
                401, 403 -> context.getString(R.string.error_auth)
                404 -> context.getString(R.string.error_not_found)
                in 500..599 -> context.getString(R.string.error_server, t.code())
                else -> context.getString(R.string.error_http, t.code())
            }
            is IOException -> context.getString(R.string.error_network)
            else -> t.message?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.error_unknown)
        }
    }
}
