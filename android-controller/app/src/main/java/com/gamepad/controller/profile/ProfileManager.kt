/**
 * @aegis-contract
 * @claim Profile Persistence, CRUD, and Schema Validation Manager
 * @true Implements atomic profile storage in profiles.json with Section 3 PUBG Mobile default layout
 * @true Validates exported/imported JSON against GamepadLayoutProfile schema specifications
 * @false Permits invalid coordinates, corrupted JSON writes, or empty profile sets
 */
package com.gamepad.controller.profile

import android.content.Context
import android.util.Log
import com.gamepad.controller.data.ControlConfig
import com.gamepad.controller.data.GamepadLayoutProfile
import com.gamepad.controller.data.LayoutSettings
import com.gamepad.controller.data.ProfilesStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class ProfileManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val tag = "ProfileManager"
    private val profilesFileName = "profiles.json"
    private val profilesFile = File(context.filesDir, profilesFileName)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
        isLenient = false
    }

    private val _profiles = MutableStateFlow<List<GamepadLayoutProfile>>(emptyList())
    val profiles: StateFlow<List<GamepadLayoutProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow(createDefaultBattleRoyaleProfile())
    val activeProfile: StateFlow<GamepadLayoutProfile> = _activeProfile.asStateFlow()

    init {
        loadProfiles()
    }

    /**
     * Loads profiles from profiles.json in app-private storage.
     * Initializes with the Section 3 PUBG Mobile default layout if the file is absent or invalid.
     */
    fun loadProfiles() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (profilesFile.exists()) {
                        val content = profilesFile.readText()
                        val storage = json.decodeFromString<ProfilesStorage>(content)
                        if (storage.profiles.isNotEmpty()) {
                            _profiles.value = storage.profiles
                            val active = storage.profiles.find { it.profileName == storage.activeProfileName }
                                ?: storage.profiles.first()
                            _activeProfile.value = active
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to read profiles.json, restoring defaults", e)
                }

                // Initialize default profile
                val defaultProfile = createDefaultBattleRoyaleProfile()
                _profiles.value = listOf(defaultProfile)
                _activeProfile.value = defaultProfile
                persistToDisk()
            }
        }
    }

    /**
     * Updates an existing profile or appends a new one, persisting to disk.
     */
    fun saveProfile(profile: GamepadLayoutProfile) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val currentList = _profiles.value.toMutableList()
                val index = currentList.indexOfFirst { it.profileName == profile.profileName }
                if (index >= 0) {
                    currentList[index] = profile
                } else {
                    currentList.add(profile)
                }
                _profiles.value = currentList
                if (_activeProfile.value.profileName == profile.profileName) {
                    _activeProfile.value = profile
                }
                persistToDisk()
            }
        }
    }

    /**
     * Sets the active profile by human-readable title.
     */
    fun setActiveProfile(profileName: String) {
        val found = _profiles.value.find { it.profileName == profileName } ?: return
        _activeProfile.value = found
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                persistToDisk()
            }
        }
    }

    /**
     * Renames a profile, preserving active selection.
     */
    fun renameProfile(oldName: String, newName: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isEmpty() || _profiles.value.any { it.profileName == cleanName }) {
            return false
        }
        val currentList = _profiles.value.toMutableList()
        val index = currentList.indexOfFirst { it.profileName == oldName }
        if (index < 0) return false

        val updated = currentList[index].copy(profileName = cleanName)
        currentList[index] = updated
        _profiles.value = currentList

        if (_activeProfile.value.profileName == oldName) {
            _activeProfile.value = updated
        }
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                persistToDisk()
            }
        }
        return true
    }

    /**
     * Creates a duplicate of an existing profile with an incremented copy suffix.
     */
    fun duplicateProfile(profileName: String): GamepadLayoutProfile? {
        val original = _profiles.value.find { it.profileName == profileName } ?: return null
        var candidateName = "${original.profileName} Copy"
        var counter = 2
        while (_profiles.value.any { it.profileName == candidateName }) {
            candidateName = "${original.profileName} Copy $counter"
            counter++
        }
        val duplicate = original.copy(profileName = candidateName)
        val currentList = _profiles.value.toMutableList()
        currentList.add(duplicate)
        _profiles.value = currentList
        _activeProfile.value = duplicate

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                persistToDisk()
            }
        }
        return duplicate
    }

    /**
     * Deletes a profile. If active, switches to the next available profile or defaults.
     */
    fun deleteProfile(profileName: String): Boolean {
        val currentList = _profiles.value.toMutableList()
        val index = currentList.indexOfFirst { it.profileName == profileName }
        if (index < 0) return false

        currentList.removeAt(index)
        if (currentList.isEmpty()) {
            val defaultProfile = createDefaultBattleRoyaleProfile()
            currentList.add(defaultProfile)
            _activeProfile.value = defaultProfile
        } else if (_activeProfile.value.profileName == profileName) {
            _activeProfile.value = currentList.first()
        }

        _profiles.value = currentList
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                persistToDisk()
            }
        }
        return true
    }

    /**
     * Resets a profile's control arrangement and settings to the Section 3 PUBG Mobile default.
     */
    fun resetProfileToDefault(profileName: String) {
        val defaultProfile = createDefaultBattleRoyaleProfile().copy(profileName = profileName)
        saveProfile(defaultProfile)
    }

    /**
     * Serializes a profile to JSON string for clipboard export or network sharing.
     */
    fun exportProfileToJson(profile: GamepadLayoutProfile): String {
        return json.encodeToString(profile)
    }

    /**
     * Validates and imports a profile from a JSON string.
     * Enforces schema rules defined in docs/layout-format.md.
     */
    fun importProfileFromJson(jsonString: String): Result<GamepadLayoutProfile> {
        return try {
            val profile = json.decodeFromString<GamepadLayoutProfile>(jsonString)
            validateProfileSchema(profile)

            // Resolve name collision if profile name already exists
            var resolvedName = profile.profileName
            var counter = 2
            while (_profiles.value.any { it.profileName == resolvedName }) {
                resolvedName = "${profile.profileName} ($counter)"
                counter++
            }
            val finalProfile = profile.copy(profileName = resolvedName)
            saveProfile(finalProfile)
            _activeProfile.value = finalProfile
            Result.success(finalProfile)
        } catch (e: Exception) {
            Log.e(tag, "Schema validation failed for imported JSON", e)
            Result.failure(IllegalArgumentException("Invalid Layout Profile JSON: ${e.localizedMessage ?: "Schema violation"}"))
        }
    }

    /**
     * Rigorous schema validation per docs/layout-format.md Section 2.
     */
    private fun validateProfileSchema(profile: GamepadLayoutProfile) {
        require(profile.profileName.isNotBlank()) { "profileName must not be blank" }
        require(profile.version >= 1) { "version must be >= 1" }

        val s = profile.settings
        require(s.touchSensitivity in 0.1f..5.0f) { "touchSensitivity must be between 0.1 and 5.0" }
        require(s.adsSensitivityMultiplier in 0.1f..2.0f) { "adsSensitivityMultiplier must be between 0.1 and 2.0" }
        require(s.stickDeadzone in 0.05f..0.8f) { "stickDeadzone must be between 0.05 and 0.8" }

        require(profile.controls.isNotEmpty()) { "Profile must contain at least one control" }
        val ids = mutableSetOf<String>()
        val validTypes = setOf("stick", "button", "chip", "look_zone")
        val validBehaviors = setOf("hold", "toggle", "tap", "stick")
        val validMouseButtons = setOf("none", "LMB", "RMB", "MMB", "X1", "X2")

        for (c in profile.controls) {
            require(c.id.isNotBlank()) { "Control id must not be blank" }
            require(ids.add(c.id)) { "Duplicate control id detected: ${c.id}" }
            require(c.type in validTypes) { "Invalid control type '${c.type}' for id '${c.id}'" }
            require(c.behavior in validBehaviors) { "Invalid behavior '${c.behavior}' for id '${c.id}'" }
            require(c.boundMouseButton in validMouseButtons) { "Invalid boundMouseButton '${c.boundMouseButton}' for id '${c.id}'" }
            if (c.boundKeyId != null) {
                require(c.boundKeyId in 0..127) { "boundKeyId must be between 0 and 127 for id '${c.id}'" }
            }
            require(c.x in 0.0f..100.0f) { "x coordinate must be between 0.0 and 100.0 for id '${c.id}'" }
            require(c.y in 0.0f..100.0f) { "y coordinate must be between 0.0 and 100.0 for id '${c.id}'" }
            require(c.width in 1.0f..100.0f) { "width must be between 1.0 and 100.0 for id '${c.id}'" }
            require(c.height in 1.0f..100.0f) { "height must be between 1.0 and 100.0 for id '${c.id}'" }
            require(c.opacity in 0.1f..1.0f) { "opacity must be between 0.1 and 1.0 for id '${c.id}'" }
        }
    }

    /**
     * Atomically writes storage state to disk using a temporary file.
     */
    private fun persistToDisk() {
        try {
            val storage = ProfilesStorage(
                activeProfileName = _activeProfile.value.profileName,
                profiles = _profiles.value
            )
            val jsonText = json.encodeToString(storage)
            val tempFile = File(context.filesDir, "$profilesFileName.tmp")
            tempFile.writeText(jsonText)
            if (!tempFile.renameTo(profilesFile)) {
                tempFile.copyTo(profilesFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: IOException) {
            Log.e(tag, "Failed to persist profiles to disk", e)
        }
    }

    companion object {
        /**
         * Authoritative Section 3 PUBG Mobile HUD layout configuration.
         */
        fun createDefaultBattleRoyaleProfile(): GamepadLayoutProfile {
            return GamepadLayoutProfile(
                profileName = "Default Battle Royale",
                version = 1,
                settings = LayoutSettings(
                    touchSensitivity = 1.0f,
                    adsSensitivityMultiplier = 0.6f,
                    gyroEnabled = false,
                    gyroSensitivity = 1.0f,
                    stickDeadzone = 0.35f,
                    stickFloatingOrigin = true
                ),
                controls = listOf(
                    ControlConfig(id = "ms", type = "stick", label = "Move", behavior = "stick", x = 16.0f, y = 66.0f, width = 17.0f, height = 17.0f, opacity = 0.75f),
                    ControlConfig(id = "run", type = "button", label = "Sprint", boundKeyId = 38, boundMouseButton = "none", behavior = "toggle", x = 16.0f, y = 34.0f, width = 5.6f, height = 5.6f, opacity = 0.75f),
                    ControlConfig(id = "lfire", type = "button", label = "Fire", boundMouseButton = "LMB", behavior = "hold", x = 31.0f, y = 58.0f, width = 9.0f, height = 9.0f, opacity = 0.85f),
                    ControlConfig(id = "rfire", type = "button", label = "Fire & Look", boundMouseButton = "LMB", behavior = "hold", x = 86.0f, y = 64.0f, width = 14.0f, height = 14.0f, opacity = 0.85f),
                    ControlConfig(id = "scope", type = "button", label = "ADS", boundMouseButton = "RMB", behavior = "toggle", x = 73.0f, y = 47.0f, width = 8.0f, height = 8.0f, opacity = 0.85f),
                    ControlConfig(id = "jump", type = "button", label = "Jump", boundKeyId = 36, boundMouseButton = "none", behavior = "hold", x = 94.0f, y = 38.0f, width = 8.0f, height = 8.0f, opacity = 0.85f),
                    ControlConfig(id = "crouch", type = "button", label = "Crouch", boundKeyId = 2, boundMouseButton = "none", behavior = "hold", x = 76.0f, y = 84.0f, width = 7.0f, height = 7.0f, opacity = 0.85f),
                    ControlConfig(id = "prone", type = "button", label = "Prone", boundKeyId = 25, boundMouseButton = "none", behavior = "hold", x = 95.0f, y = 88.0f, width = 7.0f, height = 7.0f, opacity = 0.85f),
                    ControlConfig(id = "leanl", type = "button", label = "Lean L", boundKeyId = 16, boundMouseButton = "none", behavior = "hold", x = 62.0f, y = 32.0f, width = 6.0f, height = 6.0f, opacity = 0.8f),
                    ControlConfig(id = "leanr", type = "button", label = "Lean R", boundKeyId = 4, boundMouseButton = "none", behavior = "hold", x = 70.0f, y = 32.0f, width = 6.0f, height = 6.0f, opacity = 0.8f),
                    ControlConfig(id = "reload", type = "button", label = "Reload", boundKeyId = 17, boundMouseButton = "none", behavior = "hold", x = 64.0f, y = 68.0f, width = 6.4f, height = 6.4f, opacity = 0.85f),
                    ControlConfig(id = "use", type = "button", label = "Use", boundKeyId = 5, boundMouseButton = "none", behavior = "hold", x = 58.0f, y = 54.0f, width = 6.4f, height = 6.4f, opacity = 0.8f),
                    ControlConfig(id = "s1", type = "button", label = "Slot 1", boundKeyId = 27, boundMouseButton = "none", behavior = "hold", x = 38.0f, y = 15.0f, width = 6.0f, height = 6.0f, opacity = 0.75f),
                    ControlConfig(id = "s2", type = "button", label = "Slot 2", boundKeyId = 28, boundMouseButton = "none", behavior = "hold", x = 44.5f, y = 15.0f, width = 6.0f, height = 6.0f, opacity = 0.75f),
                    ControlConfig(id = "s3", type = "button", label = "Melee", boundKeyId = 29, boundMouseButton = "none", behavior = "hold", x = 51.0f, y = 15.0f, width = 6.0f, height = 6.0f, opacity = 0.75f),
                    ControlConfig(id = "gren", type = "button", label = "Grenade", boundKeyId = 6, boundMouseButton = "none", behavior = "hold", x = 58.0f, y = 15.0f, width = 6.0f, height = 6.0f, opacity = 0.75f),
                    ControlConfig(id = "heal", type = "button", label = "Heal", boundKeyId = 31, boundMouseButton = "none", behavior = "hold", x = 65.0f, y = 15.0f, width = 6.0f, height = 6.0f, opacity = 0.75f),
                    ControlConfig(id = "map", type = "button", label = "Map", boundKeyId = 12, boundMouseButton = "none", behavior = "hold", x = 84.0f, y = 12.0f, width = 5.4f, height = 5.4f, opacity = 0.75f),
                    ControlConfig(id = "bag", type = "button", label = "Bag", boundKeyId = 37, boundMouseButton = "none", behavior = "hold", x = 91.0f, y = 12.0f, width = 5.4f, height = 5.4f, opacity = 0.75f),
                    ControlConfig(id = "gyro", type = "chip", label = "Gyro", behavior = "toggle", x = 50.0f, y = 90.0f, width = 15.0f, height = 4.8f, opacity = 0.7f)
                )
            )
        }
    }
}
