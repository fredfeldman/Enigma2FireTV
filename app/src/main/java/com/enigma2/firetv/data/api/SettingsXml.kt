package com.enigma2.firetv.data.api

import android.util.Xml
import com.enigma2.firetv.data.model.settings.ConfigItem
import com.enigma2.firetv.data.model.settings.ConfigItemType
import com.enigma2.firetv.data.model.settings.ConfigSection
import com.enigma2.firetv.data.model.settings.PowerState
import com.enigma2.firetv.data.model.settings.ProtectedService
import com.enigma2.firetv.data.model.settings.RecordingLocations
import com.enigma2.firetv.data.model.settings.SleepTimer
import com.enigma2.firetv.data.model.settings.StatusInfo
import com.enigma2.firetv.data.model.settings.TunerSignal
import com.enigma2.firetv.data.model.settings.VolumeInfo
import com.enigma2.firetv.data.model.settings.WolSetup
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Tolerant parsers for the various OpenWebif "settings" responses (XML + JSON mix).
 * Ported verbatim from Enigma2Android v1.5.1 — keep in lockstep.
 */
object SettingsXml {

    // ---- Generic helpers ----

    /** Walks a flat `<root><tag>value</tag>…</root>` into a map. */
    private fun parseFlatXml(stream: InputStream): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)
            val text = StringBuilder()
            var key: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> { key = parser.name; text.setLength(0) }
                    XmlPullParser.TEXT -> text.append(parser.text)
                    XmlPullParser.END_TAG -> if (key == parser.name) {
                        out[parser.name] = text.toString().trim()
                    }
                }
                event = parser.next()
            }
            out
        } catch (_: Exception) {
            out
        }
    }

    private fun bodyToString(stream: InputStream): String =
        try { stream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }

    private fun String.toBoolLoose(): Boolean =
        equals("true", true) || equals("1", true) || equals("on", true) || equals("yes", true)

    // ---- Status ----

    fun parseStatusInfo(stream: InputStream): StatusInfo {
        val body = bodyToString(stream).ifBlank { return StatusInfo() }
        return try {
            val o = JSONObject(body)
            StatusInfo(
                volume = o.optInt("volume", 0),
                isMuted = o.optBoolean("muted", false),
                inStandby = o.optBoolean("inStandby", false) || o.optString("inStandby") == "true",
                isRecording = o.optBoolean("isRecording", false),
                currentService = o.optString("currservice_name").takeIf { it.isNotBlank() },
                currentServiceRef = o.optString("currservice_serviceref").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) { StatusInfo() }
    }

    // ---- Power ----

    fun parsePowerState(stream: InputStream): PowerState {
        val body = bodyToString(stream)
        try {
            val o = JSONObject(body)
            return PowerState(
                inStandby = o.optBoolean("instandby", false) || o.optString("instandby").toBoolLoose()
            )
        } catch (_: Exception) {}
        val v = Regex("<e2instandby>([^<]+)</e2instandby>", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1)?.trim().orEmpty()
        return PowerState(inStandby = v.toBoolLoose())
    }

    fun parseSleepTimer(stream: InputStream): SleepTimer {
        val map = parseFlatXml(stream)
        return SleepTimer(
            enabled = (map["e2enabled"] ?: "false").toBoolLoose(),
            minutes = (map["e2minutes"] ?: "0").toIntOrNull() ?: 0,
            action = map["e2action"] ?: "standby"
        )
    }

    // ---- Volume ----

    fun parseVolume(stream: InputStream): VolumeInfo {
        val map = parseFlatXml(stream)
        return VolumeInfo(
            current = (map["e2current"] ?: "0").toIntOrNull() ?: 0,
            muted = (map["e2ismuted"] ?: "false").toBoolLoose()
        )
    }

    // ---- Generic settings dump ----

    /** `<e2settings>` is a flat list of `<e2setting><e2settingname>…</e2settingname><e2settingvalue>…</e2settingvalue></e2setting>`. */
    fun parseAllSettings(stream: InputStream): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)
            val text = StringBuilder()
            var name: String? = null
            var value: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        text.setLength(0)
                        if (parser.name == "e2setting") { name = null; value = null }
                    }
                    XmlPullParser.TEXT -> text.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "e2settingname" -> name = text.toString().trim()
                        "e2settingvalue" -> value = text.toString().trim()
                        "e2setting" -> if (name != null) out[name!!] = value.orEmpty()
                    }
                }
                event = parser.next()
            }
            out
        } catch (_: Exception) { out }
    }

    // ---- Config tree (JSON) ----

    fun parseConfigSections(stream: InputStream): List<String> {
        val body = bodyToString(stream)
        val out = mutableListOf<String>()
        try {
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) out.add(arr.getString(i))
            return out
        } catch (_: Exception) {}
        try {
            val o = JSONObject(body)
            val arr = o.optJSONArray("sections")
            if (arr != null) for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (_: Exception) {}
        return out
    }

    fun parseConfigSection(name: String, stream: InputStream): ConfigSection {
        val body = bodyToString(stream)
        val items = mutableListOf<ConfigItem>()
        try {
            val arr: JSONArray = try {
                JSONObject(body).let { it.optJSONArray(name) ?: it.optJSONArray("settings") }
                    ?: JSONArray(body)
            } catch (_: Exception) { JSONArray(body) }
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val type = mapType(it.optString("type"))
                val choices = mutableListOf<Pair<String, String>>()
                val cArr = it.optJSONArray("choices")
                if (cArr != null) {
                    for (j in 0 until cArr.length()) {
                        val c = cArr.opt(j)
                        when (c) {
                            is JSONArray -> if (c.length() >= 2) choices.add(c.optString(0) to c.optString(1))
                            is JSONObject -> {
                                val keys = c.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next(); choices.add(k to c.optString(k))
                                }
                            }
                            is String -> choices.add(c to c)
                        }
                    }
                }
                items.add(
                    ConfigItem(
                        path = it.optString("path").ifBlank { it.optString("name") },
                        description = it.optString("description").ifBlank { it.optString("text") }
                            .ifBlank { it.optString("path") },
                        type = type,
                        value = it.optString("value"),
                        choices = choices,
                        min = it.optInt("min", Int.MIN_VALUE).takeIf { v -> v != Int.MIN_VALUE },
                        max = it.optInt("max", Int.MIN_VALUE).takeIf { v -> v != Int.MIN_VALUE }
                    )
                )
            }
        } catch (_: Exception) {}
        return ConfigSection(name, items)
    }

    private fun mapType(raw: String): ConfigItemType = when (raw.lowercase()) {
        "bool", "boolean", "yesno", "onoff" -> ConfigItemType.Bool
        "integer", "int" -> ConfigItemType.Int
        "float" -> ConfigItemType.Float
        "selection", "choice", "list" -> ConfigItemType.Choice
        "password", "pin" -> ConfigItemType.Password
        "directory", "dir" -> ConfigItemType.Directory
        "slider" -> ConfigItemType.Slider
        "text", "string" -> ConfigItemType.Text
        else -> ConfigItemType.Unknown
    }

    // ---- Parental ----

    fun parseProtectionSettings(stream: InputStream): Pair<Boolean, Boolean> {
        val body = bodyToString(stream)
        return try {
            val o = JSONObject(body)
            val configured = o.optString("Configured").toBoolLoose() || o.optBoolean("Configured")
            val pinActive = o.optString("SetupPinActive").toBoolLoose() || o.optBoolean("SetupPinActive")
            configured to pinActive
        } catch (_: Exception) { false to false }
    }

    fun parseProtectedServices(stream: InputStream): List<ProtectedService> {
        val body = bodyToString(stream)
        val out = mutableListOf<ProtectedService>()
        try {
            val o = JSONObject(body)
            val arr = o.optJSONArray("services") ?: o.optJSONArray("servicelist") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                when (item) {
                    is JSONObject -> out.add(
                        ProtectedService(
                            ref = item.optString("servicereference").ifBlank { item.optString("ref") },
                            name = item.optString("servicename").ifBlank { item.optString("name") }
                        )
                    )
                    is String -> out.add(ProtectedService(ref = item, name = item))
                }
            }
        } catch (_: Exception) {}
        return out
    }

    // ---- Recording locations ----

    fun parseLocations(stream: InputStream): List<String> {
        val body = bodyToString(stream)
        val out = mutableListOf<String>()
        try {
            val arr = JSONObject(body).optJSONArray("locations") ?: return emptyList()
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (_: Exception) {}
        return out
    }

    fun parseCurrentLocation(stream: InputStream): String? {
        val body = bodyToString(stream)
        return try {
            val o = JSONObject(body)
            o.optString("location").takeIf { it.isNotBlank() }
                ?: o.optString("currlocation").takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    fun parseRecordingLocations(currentStream: InputStream, listStream: InputStream): RecordingLocations {
        val current = parseCurrentLocation(currentStream)
        val list = parseLocations(listStream)
        return RecordingLocations(current, list)
    }

    // ---- Tuner ----

    fun parseTunerSignal(stream: InputStream): TunerSignal {
        val map = parseFlatXml(stream)
        return TunerSignal(
            tunerNumber = (map["e2tunernumber"] ?: map["e2frontend"])?.toIntOrNull(),
            tunerType = map["e2tunertype"] ?: map["e2frontendtype"],
            snr = map["e2snrdb"] ?: map["e2snr"],
            ber = map["e2ber"],
            signal = map["e2acg"] ?: map["e2signal"]
        )
    }

    // ---- WOL ----

    fun parseWolSetup(stream: InputStream): WolSetup {
        val map = parseFlatXml(stream)
        return WolSetup(
            enabled = (map["e2wol"] ?: map["wol"] ?: "false").toBoolLoose(),
            location = map["e2location"] ?: map["location"],
            wolStandby = (map["e2wolstandby"] ?: map["wolstandby"] ?: "false").toBoolLoose()
        )
    }

    // ---- Save-config ack ----

    /** Treats anything non-empty containing "true"/"ok"/"saved" as success. Falls back to true on empty 200. */
    fun parseSaveAck(stream: InputStream): Pair<Boolean, String?> {
        val body = bodyToString(stream).trim()
        if (body.isEmpty()) return true to null
        try {
            val o = JSONObject(body)
            if (o.has("result")) {
                val ok = o.optBoolean("result") || o.optString("result").toBoolLoose()
                return ok to (o.optString("message").takeIf { it.isNotBlank() })
            }
        } catch (_: Exception) {}
        val m = Regex("<e2state>([^<]+)</e2state>", RegexOption.IGNORE_CASE).find(body)
        if (m != null) {
            val txt = Regex("<e2statetext>([^<]+)</e2statetext>", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.get(1)
            return m.groupValues[1].toBoolLoose() to txt
        }
        return true to null
    }
}
