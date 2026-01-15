package com.example.bitrix_app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedPreferences(context: Context) {

    private val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveAdminWebhook(url: String) {
        sharedPreferences.edit().putString("admin_webhook", url).apply()
    }

    fun getAdminWebhook(): String? {
        return sharedPreferences.getString("admin_webhook", null)
    }

    fun saveUserWebhook(userId: String, url: String) {
        sharedPreferences.edit().putString("user_webhook_$userId", url).apply()
    }

    fun getUserWebhook(userId: String): String? {
        return sharedPreferences.getString("user_webhook_$userId", null)
    }

    fun saveDefaultGroupId(groupId: String) {
        sharedPreferences.edit().putString("default_group_id", groupId).apply()
    }

    fun getDefaultGroupId(): String {
        return sharedPreferences.getString("default_group_id", "69") ?: "69"
    }

    fun saveUsersJson(json: String) {
        sharedPreferences.edit().putString("users_list_json", json).apply()
    }

    fun getUsersJson(): String? {
        return sharedPreferences.getString("users_list_json", null)
    }
}