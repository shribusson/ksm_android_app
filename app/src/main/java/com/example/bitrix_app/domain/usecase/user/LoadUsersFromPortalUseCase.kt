package com.example.bitrix_app.domain.usecase.user

import com.example.bitrix_app.data.local.preferences.EncryptedPreferences
import com.example.bitrix_app.domain.model.User
import com.example.bitrix_app.data.remote.BitrixApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class LoadUsersFromPortalUseCase @Inject constructor(
    private val encryptedPrefs: EncryptedPreferences,
    private val bitrixApi: BitrixApi
) {

    suspend operator fun invoke(adminWebhook: String): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            if (!isValidWebhookUrl(adminWebhook)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid webhook URL format"))
            }

            val safeWebhookUrl = if (adminWebhook.endsWith("/")) adminWebhook else "$adminWebhook/"
            val allUsers = mutableListOf<User>()
            var start = 0
            var moreAvailable = true

            while (moreAvailable) {
                // Call API
                val response = bitrixApi.getUsers(
                    url = "${safeWebhookUrl}user.get.json",
                    start = start
                )

                if (response.error != null) {
                     throw Exception("API Error: ${response.error}")
                }
                val userDtos = response.result ?: emptyList()
                if (userDtos.isEmpty()) {
                    moreAvailable = false
                } else {
                    userDtos.forEach { dto ->
                         // Generate avatar (always initials to avoid link text issues)
                         val displayName = "${dto.name ?: ""} ${dto.lastName ?: ""}".trim()
                         
                         // Force initials to avoid "link text" in UI since UserAvatar only renders text currently.
                         // And we don't have Coil/Glide added yet.
                         val nameParts = displayName.split(" ")
                         val initials = nameParts.mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
                         val finalInitials = initials.ifBlank { "U" }
                         
                         allUsers.add(
                             User(
                                 userId = dto.id,
                                 name = displayName,
                                 webhookUrl = "", 
                                 avatar = finalInitials,
                                 supervisorId = null
                             )
                         )
                    }

                    if (response.next != null && response.total != null && start + userDtos.size < response.total) {
                        start = response.next
                    } else {
                         moreAvailable = false
                    }
                }
            }

            if (allUsers.isEmpty()) {
                return@withContext Result.failure(Exception("No users found in portal"))
            }

            // Save admin webhook
            encryptedPrefs.saveAdminWebhook(adminWebhook)

            Timber.i("Loaded ${allUsers.size} users from portal")
            Result.success(allUsers)
        } catch (e: Exception) {
            Timber.e(e, "Error loading users from portal")
            Result.failure(e)
        }
    }

    private fun isValidWebhookUrl(url: String): Boolean {
        // Simple validation
        return url.contains("https://") || url.contains("http://")
    }
}
