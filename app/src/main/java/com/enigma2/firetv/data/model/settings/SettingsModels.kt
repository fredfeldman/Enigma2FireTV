package com.enigma2.firetv.data.model.settings

// Ported verbatim from Enigma2Android v1.5.1
// (data/model/settings/SettingsModels.kt). Kept in lockstep so the
// SettingsXml parser ports byte-for-byte and the receiver-side JSON/XML
// shapes are interpreted identically on both apps.

/** Live receiver status from `api/statusinfo`. All fields optional / best-effort. */
data class StatusInfo(
    val volume: Int = 0,
    val isMuted: Boolean = false,
    val inStandby: Boolean = false,
    val isRecording: Boolean = false,
    val currentService: String? = null,
    val currentServiceRef: String? = null
)

data class PowerState(val inStandby: Boolean)

data class SleepTimer(
    val enabled: Boolean,
    val minutes: Int,
    /** "standby" or "shutdown" */
    val action: String
)

data class VolumeInfo(val current: Int, val muted: Boolean)

data class TunerSignal(
    val tunerNumber: Int? = null,
    val tunerType: String? = null,
    val snr: String? = null,
    val ber: String? = null,
    val signal: String? = null
)

data class RecordingLocations(
    val current: String?,
    val locations: List<String>
)

data class WolSetup(
    val enabled: Boolean,
    val location: String?,
    val wolStandby: Boolean
)

data class ParentalSettings(
    val configured: Boolean,
    val type: String?,
    val setupPinActive: Boolean,
    val protectedServices: List<ProtectedService> = emptyList()
)

data class ProtectedService(val ref: String, val name: String)

data class WebUiConfig(val keyValues: Map<String, String>)

data class TranscodingProfile(val keyValues: Map<String, String>)

/** Represents one saveable item from `api/config/{section}`. */
data class ConfigItem(
    val path: String,
    val description: String,
    val type: ConfigItemType,
    val value: String,
    /** For Choice: list of (value,label). */
    val choices: List<Pair<String, String>> = emptyList(),
    val min: Int? = null,
    val max: Int? = null
)

enum class ConfigItemType { Bool, Int, Float, Choice, Text, Password, Directory, Slider, Unknown }

data class ConfigSection(val name: String, val items: List<ConfigItem>)

/** Top-level capability flags returned once per session. */
data class ReceiverCapabilities(
    val hasParental: Boolean,
    val hasTranscoding: Boolean,
    val hasConfigTree: Boolean,
    val hasWol: Boolean
)
