package com.example.data

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import com.example.model.ActivationMethod
import com.example.model.AppLanguage
import com.example.model.CrosshairStyle
import com.example.model.KeymapNode
import com.example.model.MacroConfig
import com.example.model.MacroTapMode
import com.example.model.MacroTargetNode
import com.example.model.MacroTriggerNode
import com.example.model.PerformanceMode
import com.example.model.ResolutionScale
import com.example.model.TouchConfig
import com.example.model.TouchCurve
import com.example.model.defaultKeymapNodes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.lvngDataStore: DataStore<Preferences> by preferencesDataStore(name = "lvng_space_prefs")

data class StoredGameTuning(
    val performanceMode: PerformanceMode = PerformanceMode.OVERDRIVE,
    val touchSensitivity: Float = 1.75f,
    val autoDndOnLaunch: Boolean = true,
    val crosshairStyle: CrosshairStyle = CrosshairStyle.OFF,
    val keymapNodes: List<KeymapNode> = defaultKeymapNodes(),
    val launchCount: Int = 0
)

data class StoredPreferencesState(
    val language: AppLanguage = AppLanguage.ID,
    val activeMode: PerformanceMode = PerformanceMode.OVERDRIVE,
    val touchConfig: TouchConfig = TouchConfig(),
    val addedPackages: Set<String> = emptySet(),
    val gameTunings: Map<String, StoredGameTuning> = emptyMap(),
    val selectedGamePackage: String? = null,
    val activationMethod: ActivationMethod = ActivationMethod.NONE,
    val showTouchCoordinates: Boolean = false,
    val showTouchDots: Boolean = false,
    val isMonitoringInfoVisible: Boolean = false,
    val resolutionScale: ResolutionScale = ResolutionScale.NATIVE,
    val activeCrosshair: CrosshairStyle = CrosshairStyle.OFF,
    val macroConfig: MacroConfig = MacroConfig(),
    val localDeviceId: String = "",
    val loggedInAccountId: String? = null,
    val loggedInUsername: String? = null,
    val loggedInExpiresAtMillis: Long = 0L
)

class LvngPreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val ACTIVE_MODE = stringPreferencesKey("active_mode")
        val TOUCH_ENGINE_ENABLED = booleanPreferencesKey("touch_engine_enabled")
        val TOUCH_SAMPLING_HZ = intPreferencesKey("touch_sampling_hz")
        val TOUCH_SENSITIVITY = floatPreferencesKey("touch_sensitivity")
        val SWIPE_ACCELERATION = floatPreferencesKey("swipe_acceleration")
        val MICRO_AIM_SMOOTHING = floatPreferencesKey("micro_aim_smoothing")
        val EDGE_DEADZONE_DP = floatPreferencesKey("edge_deadzone_dp")
        val TOUCH_CURVE = stringPreferencesKey("touch_curve")
        val ADDED_PACKAGES = stringSetPreferencesKey("added_packages")
        val GAME_TUNINGS_JSON = stringPreferencesKey("game_tunings_json")
        val SELECTED_GAME_PKG = stringPreferencesKey("selected_game_pkg")
        val ACTIVATION_METHOD = stringPreferencesKey("activation_method")
        val SHOW_TOUCH_COORDS = booleanPreferencesKey("show_touch_coords")
        val SHOW_TOUCH_DOTS = booleanPreferencesKey("show_touch_dots")
        val MONITORING_INFO_VISIBLE = booleanPreferencesKey("monitoring_info_visible")
        val RESOLUTION_SCALE = stringPreferencesKey("resolution_scale")
        val ACTIVE_CROSSHAIR = stringPreferencesKey("active_crosshair")
        val MACRO_CONFIG_JSON = stringPreferencesKey("macro_config_json")
        val DEVICE_UNIQUE_ID = stringPreferencesKey("device_unique_id")
        val LOGGED_IN_ACCOUNT_ID = stringPreferencesKey("logged_in_account_id")
        val LOGGED_IN_USERNAME = stringPreferencesKey("logged_in_username")
        val LOGGED_IN_EXPIRES_AT = longPreferencesKey("logged_in_expires_at")
    }

    val preferencesFlow: Flow<StoredPreferencesState> = context.lvngDataStore.data.map { prefs ->
        val tuningsJson = prefs[Keys.GAME_TUNINGS_JSON] ?: "{}"
        val macroJson = prefs[Keys.MACRO_CONFIG_JSON]
        StoredPreferencesState(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            activeMode = PerformanceMode.fromId(prefs[Keys.ACTIVE_MODE]),
            touchConfig = TouchConfig(
                engineEnabled = prefs[Keys.TOUCH_ENGINE_ENABLED] ?: true,
                samplingRateHz = prefs[Keys.TOUCH_SAMPLING_HZ] ?: 480,
                sensitivityMultiplier = prefs[Keys.TOUCH_SENSITIVITY] ?: 1.65f,
                swipeAcceleration = prefs[Keys.SWIPE_ACCELERATION] ?: 0.58f,
                microAimSmoothing = prefs[Keys.MICRO_AIM_SMOOTHING] ?: 0.25f,
                edgeDeadzoneDp = prefs[Keys.EDGE_DEADZONE_DP] ?: 8f,
                touchCurve = TouchCurve.fromId(prefs[Keys.TOUCH_CURVE])
            ),
            addedPackages = prefs[Keys.ADDED_PACKAGES] ?: emptySet(),
            gameTunings = parseGameTunings(tuningsJson),
            selectedGamePackage = prefs[Keys.SELECTED_GAME_PKG],
            activationMethod = ActivationMethod.fromId(prefs[Keys.ACTIVATION_METHOD]),
            showTouchCoordinates = prefs[Keys.SHOW_TOUCH_COORDS] ?: false,
            showTouchDots = prefs[Keys.SHOW_TOUCH_DOTS] ?: false,
            isMonitoringInfoVisible = prefs[Keys.MONITORING_INFO_VISIBLE] ?: false,
            resolutionScale = ResolutionScale.fromId(prefs[Keys.RESOLUTION_SCALE]),
            activeCrosshair = CrosshairStyle.fromId(prefs[Keys.ACTIVE_CROSSHAIR]),
            macroConfig = parseMacroConfig(macroJson),
            localDeviceId = prefs[Keys.DEVICE_UNIQUE_ID] ?: "",
            loggedInAccountId = prefs[Keys.LOGGED_IN_ACCOUNT_ID]?.takeIf { it.isNotBlank() },
            loggedInUsername = prefs[Keys.LOGGED_IN_USERNAME]?.takeIf { it.isNotBlank() },
            loggedInExpiresAtMillis = prefs[Keys.LOGGED_IN_EXPIRES_AT] ?: 0L
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = context.lvngDataStore.data.first()[Keys.DEVICE_UNIQUE_ID]
        if (!current.isNullOrBlank() && current.length >= 6) {
            return current
        }
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().replace(Regex("[^a-zA-Z0-9_-]"), "").take(12)
        val generated = if (androidId.isNotBlank()) {
            "$androidId-${UUID.randomUUID().toString().replace("-", "").take(16)}"
        } else {
            UUID.randomUUID().toString().replace("-", "")
        }
        context.lvngDataStore.edit { it[Keys.DEVICE_UNIQUE_ID] = generated }
        return generated
    }

    suspend fun saveLoginSession(accountId: String, username: String, expiresAtMillis: Long) {
        context.lvngDataStore.edit { prefs ->
            prefs[Keys.LOGGED_IN_ACCOUNT_ID] = accountId
            prefs[Keys.LOGGED_IN_USERNAME] = username
            prefs[Keys.LOGGED_IN_EXPIRES_AT] = expiresAtMillis
        }
    }

    suspend fun clearLoginSession() {
        context.lvngDataStore.edit { prefs ->
            prefs.remove(Keys.LOGGED_IN_ACCOUNT_ID)
            prefs.remove(Keys.LOGGED_IN_USERNAME)
            prefs.remove(Keys.LOGGED_IN_EXPIRES_AT)
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.lvngDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setActiveMode(mode: PerformanceMode) {
        context.lvngDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_MODE] = mode.id
            prefs[Keys.TOUCH_SAMPLING_HZ] = mode.touchPollHz
        }
    }

    suspend fun updateTouchConfig(config: TouchConfig) {
        context.lvngDataStore.edit { prefs ->
            prefs[Keys.TOUCH_ENGINE_ENABLED] = config.engineEnabled
            prefs[Keys.TOUCH_SAMPLING_HZ] = config.samplingRateHz
            prefs[Keys.TOUCH_SENSITIVITY] = config.sensitivityMultiplier
            prefs[Keys.SWIPE_ACCELERATION] = config.swipeAcceleration
            prefs[Keys.MICRO_AIM_SMOOTHING] = config.microAimSmoothing
            prefs[Keys.EDGE_DEADZONE_DP] = config.edgeDeadzoneDp
            prefs[Keys.TOUCH_CURVE] = config.touchCurve.id
        }
    }

    suspend fun addGamePackage(packageName: String) {
        context.lvngDataStore.edit { prefs ->
            val current = prefs[Keys.ADDED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            current.add(packageName)
            prefs[Keys.ADDED_PACKAGES] = current
            prefs[Keys.SELECTED_GAME_PKG] = packageName
        }
    }

    suspend fun removeGamePackage(packageName: String) {
        context.lvngDataStore.edit { prefs ->
            val current = prefs[Keys.ADDED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            current.remove(packageName)
            prefs[Keys.ADDED_PACKAGES] = current
            if (prefs[Keys.SELECTED_GAME_PKG] == packageName) {
                prefs[Keys.SELECTED_GAME_PKG] = current.firstOrNull() ?: ""
            }
        }
    }

    suspend fun setSelectedGame(packageName: String) {
        context.lvngDataStore.edit { it[Keys.SELECTED_GAME_PKG] = packageName }
    }

    suspend fun saveGameTuning(packageName: String, tuning: StoredGameTuning) {
        context.lvngDataStore.edit { prefs ->
            val root = runCatching { JSONObject(prefs[Keys.GAME_TUNINGS_JSON] ?: "{}") }
                .getOrElse { JSONObject() }
            root.put(packageName, serializeTuning(tuning))
            prefs[Keys.GAME_TUNINGS_JSON] = root.toString()
        }
    }

    suspend fun incrementLaunchCount(packageName: String) {
        context.lvngDataStore.edit { prefs ->
            val root = runCatching { JSONObject(prefs[Keys.GAME_TUNINGS_JSON] ?: "{}") }
                .getOrElse { JSONObject() }
            val existing = root.optJSONObject(packageName) ?: JSONObject()
            val launches = existing.optInt("launches", 0) + 1
            existing.put("launches", launches)
            root.put(packageName, existing)
            prefs[Keys.GAME_TUNINGS_JSON] = root.toString()
        }
    }

    suspend fun setActivationMethod(method: ActivationMethod) {
        context.lvngDataStore.edit { it[Keys.ACTIVATION_METHOD] = method.id }
    }

    suspend fun setShowTouchCoordinates(enabled: Boolean) {
        context.lvngDataStore.edit { it[Keys.SHOW_TOUCH_COORDS] = enabled }
    }

    suspend fun setShowTouchDots(enabled: Boolean) {
        context.lvngDataStore.edit { it[Keys.SHOW_TOUCH_DOTS] = enabled }
    }

    suspend fun setMonitoringInfoVisible(visible: Boolean) {
        context.lvngDataStore.edit { it[Keys.MONITORING_INFO_VISIBLE] = visible }
    }

    suspend fun setResolutionScale(scale: ResolutionScale) {
        context.lvngDataStore.edit { it[Keys.RESOLUTION_SCALE] = scale.id }
    }

    suspend fun setCrosshairStyle(style: CrosshairStyle) {
        context.lvngDataStore.edit { it[Keys.ACTIVE_CROSSHAIR] = style.id }
    }

    suspend fun saveMacroConfig(config: MacroConfig) {
        context.lvngDataStore.edit { prefs ->
            prefs[Keys.MACRO_CONFIG_JSON] = serializeMacroConfig(config).toString()
        }
    }

    suspend fun exportMappingsJson(): String {
        val prefs = context.lvngDataStore.data.first()
        return prefs[Keys.GAME_TUNINGS_JSON] ?: "{}"
    }

    suspend fun importMappingsJson(rawJson: String): Boolean {
        val parsed = runCatching { JSONObject(rawJson) }.getOrNull() ?: return false
        context.lvngDataStore.edit { prefs ->
            prefs[Keys.GAME_TUNINGS_JSON] = parsed.toString()
        }
        return true
    }

    private fun serializeMacroConfig(config: MacroConfig): JSONObject {
        val triggersArr = JSONArray()
        config.triggers.forEach { t ->
            triggersArr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("x", t.xFraction.toDouble())
                    put("y", t.yFraction.toDouble())
                }
            )
        }
        val targetsArr = JSONArray()
        config.targets.forEach { tgt ->
            targetsArr.put(
                JSONObject().apply {
                    put("id", tgt.id)
                    put("name", tgt.name)
                    put("x", tgt.xFraction.toDouble())
                    put("y", tgt.yFraction.toDouble())
                    put("mode", tgt.tapMode.id)
                    put("intervalMs", tgt.intervalMs)
                    put("durationMs", tgt.durationMs)
                }
            )
        }
        return JSONObject().apply {
            put("active", config.isMacroActive)
            put("triggers", triggersArr)
            put("targets", targetsArr)
        }
    }

    private fun parseMacroConfig(jsonStr: String?): MacroConfig {
        if (jsonStr.isNullOrBlank()) return MacroConfig()
        return runCatching {
            val root = JSONObject(jsonStr)
            val triggersArr = root.optJSONArray("triggers")
            val triggers = if (triggersArr != null) {
                (0 until triggersArr.length()).mapNotNull { i ->
                    val o = triggersArr.optJSONObject(i) ?: return@mapNotNull null
                    MacroTriggerNode(
                        id = o.optString("id", "trigger_${i + 1}"),
                        name = o.optString("name", "Trigger ${i + 1}"),
                        xFraction = o.optDouble("x", 0.78).toFloat().coerceIn(0.05f, 0.95f),
                        yFraction = o.optDouble("y", 0.62).toFloat().coerceIn(0.08f, 0.92f)
                    )
                }
            } else emptyList()

            val targetsArr = root.optJSONArray("targets")
            val targets = if (targetsArr != null) {
                (0 until targetsArr.length()).mapNotNull { i ->
                    val o = targetsArr.optJSONObject(i) ?: return@mapNotNull null
                    MacroTargetNode(
                        id = o.optString("id", "target_${i + 1}"),
                        name = o.optString("name", "Target ${i + 1}"),
                        xFraction = o.optDouble("x", 0.70).toFloat().coerceIn(0.05f, 0.95f),
                        yFraction = o.optDouble("y", 0.42).toFloat().coerceIn(0.08f, 0.92f),
                        tapMode = MacroTapMode.fromId(o.optString("mode", MacroTapMode.REPEAT_TAP.id)),
                        intervalMs = o.optInt("intervalMs", 70).coerceIn(10, 1000),
                        durationMs = o.optInt("durationMs", 70).coerceIn(10, 1000)
                    )
                }
            } else emptyList()

            MacroConfig(
                triggers = triggers,
                targets = targets,
                isMacroActive = root.optBoolean("active", false)
            )
        }.getOrElse { MacroConfig() }
    }

    private fun serializeTuning(tuning: StoredGameTuning): JSONObject {
        val nodesArray = JSONArray()
        tuning.keymapNodes.forEach { node ->
            nodesArray.put(
                JSONObject().apply {
                    put("id", node.id)
                    put("label", node.label)
                    put("x", node.xFraction.toDouble())
                    put("y", node.yFraction.toDouble())
                }
            )
        }
        return JSONObject().apply {
            put("mode", tuning.performanceMode.id)
            put("sens", tuning.touchSensitivity.toDouble())
            put("dnd", tuning.autoDndOnLaunch)
            put("crosshair", tuning.crosshairStyle.id)
            put("launches", tuning.launchCount)
            put("keymap", nodesArray)
        }
    }

    private fun parseGameTunings(jsonStr: String): Map<String, StoredGameTuning> {
        val result = mutableMapOf<String, StoredGameTuning>()
        runCatching {
            val root = JSONObject(jsonStr)
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = root.optJSONObject(pkg) ?: continue
                val nodesArr = obj.optJSONArray("keymap")
                val nodes = if (nodesArr != null && nodesArr.length() > 0) {
                    (0 until nodesArr.length()).mapNotNull { idx ->
                        val n = nodesArr.optJSONObject(idx) ?: return@mapNotNull null
                        KeymapNode(
                            id = n.optString("id", "btn_$idx"),
                            label = n.optString("label", "BTN"),
                            xFraction = n.optDouble("x", 0.5).toFloat().coerceIn(0.05f, 0.95f),
                            yFraction = n.optDouble("y", 0.5).toFloat().coerceIn(0.05f, 0.95f)
                        )
                    }
                } else defaultKeymapNodes()

                result[pkg] = StoredGameTuning(
                    performanceMode = PerformanceMode.fromId(obj.optString("mode", PerformanceMode.OVERDRIVE.id)),
                    touchSensitivity = obj.optDouble("sens", 1.75).toFloat(),
                    autoDndOnLaunch = obj.optBoolean("dnd", true),
                    crosshairStyle = CrosshairStyle.fromId(obj.optString("crosshair", CrosshairStyle.OFF.id)),
                    keymapNodes = nodes,
                    launchCount = obj.optInt("launches", 0)
                )
            }
        }
        return result
    }
}
