package com.scantoftp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.scantoftp.domain.model.FlashMode
import com.scantoftp.domain.model.FtpSettings
import com.scantoftp.domain.model.ServerProfile
import com.scantoftp.domain.model.UploadProtocol
import com.scantoftp.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private val securePrefs: SharedPreferences by lazy { createSecurePrefs() }

    private fun createSecurePrefs(): SharedPreferences {
        return try {
            buildEncryptedPrefs()
        } catch (throwable: Throwable) {
            Log.w(TAG, "Secure settings keyset unreadable; resetting it", throwable)
            clearCorruptedSecurePrefs()
            buildEncryptedPrefs()
        }
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun clearCorruptedSecurePrefs() {
        runCatching {
            context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        runCatching {
            val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$SECURE_PREFS_NAME.xml")
            if (prefsFile.exists()) prefsFile.delete()
        }
    }

    override fun settingsFlow(): Flow<FtpSettings> = context.settingsDataStore.data.map { prefs ->
        val profiles = readProfiles(prefs)
        val activeId = resolveActiveId(prefs, profiles)
        val active = profiles.firstOrNull { it.id == activeId }
        FtpSettings(
            host = active?.host ?: "",
            port = active?.port ?: 21,
            username = active?.username ?: "",
            password = active?.password ?: "",
            uploadProtocol = active?.protocol ?: UploadProtocol.Ftp,
            remoteDirectory = active?.remoteDirectory ?: "/receipts",
            useFtps = active?.protocol == UploadProtocol.Ftps,
            smbShareName = "",
            smbDomain = active?.smbDomain ?: "",
            tripSubfolder = prefs[TRIP_SUBFOLDER] ?: "",
            imageQuality = prefs[IMAGE_QUALITY] ?: 90,
            autoCaptureEnabled = prefs[AUTO_CAPTURE] ?: true,
            flashMode = prefs[FLASH_MODE]
                ?.let { stored -> runCatching { FlashMode.valueOf(stored) }.getOrNull() }
                ?: FlashMode.Auto,
            ocrRenamingEnabled = prefs[OCR_RENAMING] ?: true,
            uploadOnWifiOnly = prefs[WIFI_ONLY] ?: false,
        )
    }

    override fun profilesFlow(): Flow<List<ServerProfile>> =
        context.settingsDataStore.data.map { readProfiles(it) }

    override fun activeProfileIdFlow(): Flow<String?> = context.settingsDataStore.data.map { prefs ->
        resolveActiveId(prefs, readProfiles(prefs))
    }

    override suspend fun upsertProfile(profile: ServerProfile) {
        val current = loadProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) current[index] = profile else current.add(profile)
        val makeActive = readActiveIdRaw() == null
        persistProfiles(current, newActiveId = if (makeActive) profile.id else null)
    }

    override suspend fun deleteProfile(id: String) {
        val current = loadProfiles().filterNot { it.id == id }
        persistProfiles(current, newActiveId = null)
        securePrefs.edit { remove(passwordKey(id)) }
    }

    override suspend fun setActiveProfile(id: String) {
        context.settingsDataStore.edit { prefs -> prefs[ACTIVE_PROFILE_ID] = id }
    }

    override suspend fun setImageQuality(quality: Int) =
        editPrefs { it[IMAGE_QUALITY] = quality.coerceIn(50, 100) }

    override suspend fun setAutoCapture(enabled: Boolean) = editPrefs { it[AUTO_CAPTURE] = enabled }

    override suspend fun setFlashMode(mode: FlashMode) = editPrefs { it[FLASH_MODE] = mode.name }

    override suspend fun setOcrRenaming(enabled: Boolean) = editPrefs { it[OCR_RENAMING] = enabled }

    override suspend fun setTripSubfolder(path: String) = editPrefs { it[TRIP_SUBFOLDER] = path }

    override suspend fun setUploadOnWifiOnly(enabled: Boolean) = editPrefs { it[WIFI_ONLY] = enabled }

    private suspend fun editPrefs(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private suspend fun loadProfiles(): List<ServerProfile> =
        readProfiles(context.settingsDataStore.data.first())

    private suspend fun readActiveIdRaw(): String? =
        context.settingsDataStore.data.first()[ACTIVE_PROFILE_ID]

    private suspend fun persistProfiles(profiles: List<ServerProfile>, newActiveId: String?) {
        // Commit passwords to secure storage BEFORE the DataStore write. The DataStore
        // edit emits immediately and re-derives the effective settings (which read the
        // password back from secure prefs); if we wrote the password afterwards/async,
        // that first emission would see a blank password and report setup as incomplete.
        securePrefs.edit(commit = true) {
            profiles.forEach { putString(passwordKey(it.id), it.password) }
        }
        context.settingsDataStore.edit { prefs ->
            prefs[PROFILES_JSON] = serializeProfiles(profiles)
            if (newActiveId != null) prefs[ACTIVE_PROFILE_ID] = newActiveId
            val active = prefs[ACTIVE_PROFILE_ID]
            if (active == null || profiles.none { it.id == active }) {
                val fallback = profiles.firstOrNull()?.id
                if (fallback != null) prefs[ACTIVE_PROFILE_ID] = fallback else prefs.remove(ACTIVE_PROFILE_ID)
            }
        }
    }

    private fun resolveActiveId(prefs: Preferences, profiles: List<ServerProfile>): String? {
        val stored = prefs[ACTIVE_PROFILE_ID]
        return stored?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
    }

    private fun readProfiles(prefs: Preferences): List<ServerProfile> {
        val json = prefs[PROFILES_JSON]
        if (json != null) {
            return runCatching { parseProfiles(json) }.getOrElse { emptyList() }
        }
        // No profiles stored yet: fall back to any legacy single-destination config so
        // an existing setup keeps uploading until the user touches profile management.
        return legacyProfile(prefs)?.let { listOf(it) } ?: emptyList()
    }

    private fun legacyProfile(prefs: Preferences): ServerProfile? {
        val host = prefs[LEGACY_HOST]?.takeIf { it.isNotBlank() } ?: return null
        val protocol = prefs[LEGACY_UPLOAD_PROTOCOL]
            ?.let { runCatching { UploadProtocol.valueOf(it) }.getOrNull() }
            ?: if (prefs[LEGACY_USE_FTPS] == true) UploadProtocol.Ftps else UploadProtocol.Ftp
        val password = securePrefs.getString(passwordKey(LEGACY_ID), null)
            ?: securePrefs.getString(LEGACY_PASSWORD_KEY, "")
            ?: ""
        return ServerProfile(
            id = LEGACY_ID,
            name = host,
            protocol = protocol,
            host = host,
            port = prefs[LEGACY_PORT] ?: if (protocol == UploadProtocol.Smb) 445 else 21,
            username = prefs[LEGACY_USERNAME] ?: "",
            password = password,
            remoteDirectory = prefs[LEGACY_REMOTE_DIRECTORY] ?: "/receipts",
            smbDomain = prefs[LEGACY_SMB_DOMAIN] ?: "",
        )
    }

    private fun serializeProfiles(profiles: List<ServerProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("protocol", profile.protocol.name)
                    put("host", profile.host)
                    put("port", profile.port)
                    put("username", profile.username)
                    put("remoteDirectory", profile.remoteDirectory)
                    put("smbDomain", profile.smbDomain)
                },
            )
        }
        return array.toString()
    }

    private fun parseProfiles(json: String): List<ServerProfile> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                add(
                    ServerProfile(
                        id = id,
                        name = obj.optString("name"),
                        protocol = runCatching { UploadProtocol.valueOf(obj.optString("protocol")) }
                            .getOrDefault(UploadProtocol.Ftp),
                        host = obj.optString("host"),
                        port = obj.optInt("port", 21),
                        username = obj.optString("username"),
                        password = securePrefs.getString(passwordKey(id), "") ?: "",
                        remoteDirectory = obj.optString("remoteDirectory", "/receipts"),
                        smbDomain = obj.optString("smbDomain", ""),
                    ),
                )
            }
        }
    }

    private fun passwordKey(id: String) = "pw_$id"

    private companion object {
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val TRIP_SUBFOLDER = stringPreferencesKey("trip_subfolder")
        val IMAGE_QUALITY = intPreferencesKey("image_quality")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val FLASH_MODE = stringPreferencesKey("flash_mode")
        val OCR_RENAMING = booleanPreferencesKey("ocr_renaming")
        val WIFI_ONLY = booleanPreferencesKey("upload_wifi_only")

        // Legacy single-destination keys, read once to migrate an existing setup.
        val LEGACY_HOST = stringPreferencesKey("host")
        val LEGACY_PORT = intPreferencesKey("port")
        val LEGACY_USERNAME = stringPreferencesKey("username")
        val LEGACY_UPLOAD_PROTOCOL = stringPreferencesKey("upload_protocol")
        val LEGACY_REMOTE_DIRECTORY = stringPreferencesKey("remote_directory")
        val LEGACY_USE_FTPS = booleanPreferencesKey("use_ftps")
        val LEGACY_SMB_DOMAIN = stringPreferencesKey("smb_domain")
        const val LEGACY_PASSWORD_KEY = "password"
        const val LEGACY_ID = "legacy"

        const val SECURE_PREFS_NAME = "secure_settings"
        const val TAG = "VaultshotSettings"
    }
}
