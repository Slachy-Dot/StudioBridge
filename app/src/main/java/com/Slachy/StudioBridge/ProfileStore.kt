package com.Slachy.StudioBridge

import android.content.Context
import com.google.gson.Gson

class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("obs_profiles_v2", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getProfiles(): List<OBSProfile> {
        val json = prefs.getString("profiles", null) ?: return emptyList()
        return gson.fromJson(json, Array<OBSProfile>::class.java).toList()
    }

    fun saveProfile(profile: OBSProfile) {
        val list = getProfiles().toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        prefs.edit().putString("profiles", gson.toJson(list)).apply()
    }

    fun deleteProfile(id: String) {
        val list = getProfiles().filter { it.id != id }
        prefs.edit().putString("profiles", gson.toJson(list)).apply()
        if (getLastUsedId() == id) clearLastUsedId()
    }

    fun getLastUsedId(): String? = prefs.getString("last_id", null)
    fun setLastUsedId(id: String) = prefs.edit().putString("last_id", id).apply()
    private fun clearLastUsedId() = prefs.edit().remove("last_id").apply()

    fun getAutoConnect(): Boolean = prefs.getBoolean("auto_connect", false)
    fun setAutoConnect(enabled: Boolean) = prefs.edit().putBoolean("auto_connect", enabled).apply()

    fun getTwitchChannel(): String = prefs.getString("twitch_channel", "") ?: ""
    fun setTwitchChannel(channel: String) = prefs.edit().putString("twitch_channel", channel).apply()

    fun getChatFontSize(): Float = prefs.getFloat("chat_font_size", DEFAULT_FONT_SIZE)
    fun setChatFontSize(sp: Float) = prefs.edit().putFloat("chat_font_size", sp).apply()

    fun getChatLineSpacing(): Float = prefs.getFloat("chat_line_spacing", DEFAULT_LINE_SPACING)
    fun setChatLineSpacing(dp: Float) = prefs.edit().putFloat("chat_line_spacing", dp).apply()

    fun getAnimatedEmotes(): Boolean = prefs.getBoolean("animated_emotes", true)
    fun setAnimatedEmotes(enabled: Boolean) = prefs.edit().putBoolean("animated_emotes", enabled).apply()

    fun getChatEmoteSize(): Float = prefs.getFloat("chat_emote_size", DEFAULT_EMOTE_SIZE)
    fun setChatEmoteSize(sp: Float) = prefs.edit().putFloat("chat_emote_size", sp).apply()

    fun getChatUsernameSize(): Float = prefs.getFloat("chat_username_size", DEFAULT_USERNAME_SIZE)
    fun setChatUsernameSize(sp: Float) = prefs.edit().putFloat("chat_username_size", sp).apply()

    fun getShowDebugBar(): Boolean = prefs.getBoolean("show_debug_bar", false)
    fun setShowDebugBar(enabled: Boolean) = prefs.edit().putBoolean("show_debug_bar", enabled).apply()

    fun getEnable7tv(): Boolean = prefs.getBoolean("enable_7tv", true)
    fun setEnable7tv(enabled: Boolean) = prefs.edit().putBoolean("enable_7tv", enabled).apply()

    fun getEnableBttv(): Boolean = prefs.getBoolean("enable_bttv", true)
    fun setEnableBttv(enabled: Boolean) = prefs.edit().putBoolean("enable_bttv", enabled).apply()

    fun getEnableFfz(): Boolean = prefs.getBoolean("enable_ffz", true)
    fun setEnableFfz(enabled: Boolean) = prefs.edit().putBoolean("enable_ffz", enabled).apply()

    /** One-time migration from the old single-profile storage. */
    fun migrateFromLegacy(context: Context) {
        if (getProfiles().isNotEmpty()) return
        val old = context.getSharedPreferences("obs_profile", Context.MODE_PRIVATE)
        val host = old.getString("host", "") ?: ""
        if (host.isNotEmpty()) {
            saveProfile(
                OBSProfile(
                    name = "Default",
                    host = host,
                    port = old.getInt("port", DEFAULT_OBS_PORT),
                    password = old.getString("password", "") ?: ""
                )
            )
        }
    }
}
