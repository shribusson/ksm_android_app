package com.example.bitrix_app.domain.usecase.user

import com.example.bitrix_app.data.local.preferences.EncryptedPreferences
import com.example.bitrix_app.domain.model.User
import com.example.bitrix_app.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

/**
 * Use Case для загрузки списка сотрудников из Bitrix24 портала.
 *
 * Использует admin webhook с правами на user.get для получения
 * списка всех активных пользователей портала.
 *
 * Пример использования:
 * 1. Администратор создает один admin webhook с правами user.get
 * 2. Приложение загружает список всех сотрудников
 * 3. Пользователь выбирает себя из списка
 * 4. Для выбранного пользователя администратор создает персональный webhook
 */
class LoadUsersFromPortalUseCase(
    private val userRepository: UserRepository,
    private val encryptedPrefs: EncryptedPreferences,
    private val httpClient: OkHttpClient
) {

    /**
     * Load users from Bitrix24 portal using admin webhook.
     *
     * @param adminWebhook Admin webhook URL с правами на user.get
     * @return Result с списком пользователей или ошибкой
     */
    suspend operator fun invoke(adminWebhook: String): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            // 1. Validate webhook format
            if (!isValidWebhookUrl(adminWebhook)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid webhook URL format"))
            }

            // 2. Call user.get API
            val users = fetchUsersFromBitrix(adminWebhook)

            if (users.isEmpty()) {
                return@withContext Result.failure(IOException("No users found in portal"))
            }

            // 3. Don't save to DB yet - only save when user is selected
            // userRepository.saveUsers(users)  // REMOVED

            // 4. Save admin webhook securely
            encryptedPrefs.saveAdminWebhook(adminWebhook)

            Timber.i("Loaded ${users.size} users from portal (not saved to DB yet)")
            Result.success(users)
        } catch (e: IOException) {
            Timber.e(e, "Network error loading users from portal")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Error loading users from portal")
            Result.failure(e)
        }
    }

    /**
     * Fetch users from Bitrix24 API using user.get method.
     */
    private fun fetchUsersFromBitrix(adminWebhook: String): List<User> {
        val safeWebhookUrl = if (adminWebhook.endsWith("/")) adminWebhook else "$adminWebhook/"
        val url = "${safeWebhookUrl}user.get.json"
        val allUsers = mutableListOf<User>()
        var start = 0

        while (true) {
            // Get all active users (ACTIVE = true)
            val requestBody = FormBody.Builder()
                .add("filter[ACTIVE]", "true")
                .add("select[]", "ID")
                .add("select[]", "NAME")
                .add("select[]", "LAST_NAME")
                .add("select[]", "PERSONAL_PHOTO")
                .add("select[]", "UF_DEPARTMENT")
                .add("start", start.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            val jsonResponse = JSONObject(responseBody)

            if (jsonResponse.has("error")) {
                val errorCode = jsonResponse.optString("error", "")
                val errorDesc = jsonResponse.optString("error_description", "Unknown error")
                throw IOException("API error ($errorCode): $errorDesc")
            }

            val result = jsonResponse.getJSONArray("result")

            for (i in 0 until result.length()) {
                val userJson = result.getJSONObject(i)

                val userId = userJson.getString("ID")
                val name = "${userJson.optString("NAME", "")} ${userJson.optString("LAST_NAME", "")}".trim()
                val photoUrl = userJson.optString("PERSONAL_PHOTO", "")

                // Generate avatar: use photo URL or initials
                val avatar = if (photoUrl.isNotBlank()) {
                    photoUrl
                } else {
                    // Generate initials from name
                    val nameParts = name.split(" ")
                    val initials = nameParts.mapNotNull { it.firstOrNull() }.take(2).joinToString("")
                    initials.ifBlank { "U" }  // Default to "U" if no initials
                }

                // Get supervisor ID (first department head)
                val departments = userJson.optJSONArray("UF_DEPARTMENT")
                val supervisorId = if (departments != null && departments.length() > 0) {
                    departments.optString(0)
                } else {
                    null
                }

                allUsers.add(User(
                    userId = userId,
                    name = name,
                    webhookUrl = "",  // Will be set later when user selects themselves
                    avatar = avatar,
                    supervisorId = supervisorId,
                    isActive = true,
                    lastSelectedAt = null
                ))
            }

            if (jsonResponse.has("next")) {
                start = jsonResponse.getInt("next")
            } else {
                break
            }
        }
        
        return allUsers
    }

    /**
     * Validate webhook URL format.
     */
    private fun isValidWebhookUrl(url: String): Boolean {
        return url.startsWith("https://") && url.contains("/rest/")
    }

    companion object {
        /**
         * Factory method для создания UseCase с зависимостями.
         */
        fun create(
            userRepository: UserRepository,
            encryptedPrefs: EncryptedPreferences,
            httpClient: OkHttpClient
        ): LoadUsersFromPortalUseCase {
            return LoadUsersFromPortalUseCase(userRepository, encryptedPrefs, httpClient)
        }
    }
}
