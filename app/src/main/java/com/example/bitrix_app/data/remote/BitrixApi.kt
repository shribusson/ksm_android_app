package com.example.bitrix_app.data.remote

import com.example.bitrix_app.data.remote.dto.BitrixResponse
import com.example.bitrix_app.data.remote.dto.TaskDto
import com.example.bitrix_app.data.remote.dto.UserDto
import com.example.bitrix_app.data.remote.dto.ChecklistItemDto
import com.example.bitrix_app.data.remote.dto.GroupDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface BitrixApi {

    @FormUrlEncoded
    @POST
    suspend fun getTasks(
        @Url url: String,
        @FieldMap params: Map<String, String>,
        @Field("start") start: Int = 0
    ): BitrixResponse<TasksResult>

    @FormUrlEncoded
    @POST
    suspend fun getUsers(
        @Url url: String,
        @Field("filter[ACTIVE]") active: String = "true",
        @Field("start") start: Int = 0
    ): BitrixResponse<List<UserDto>>

    @FormUrlEncoded
    @POST
    suspend fun createTask(
        @Url url: String,
        @Field("fields[TITLE]") title: String,
        @Field("fields[RESPONSIBLE_ID]") responsibleId: String,
        @Field("fields[TIME_ESTIMATE]") timeEstimate: Int,
        @Field("fields[GROUP_ID]") groupId: String,
        @Field("fields[DEADLINE]") deadline: String? = null
    ): BitrixResponse<TaskResult>
    
    @FormUrlEncoded
    @POST
    suspend fun deleteTask(
        @Url url: String,
        @Field("taskId") taskId: String
    ): BitrixResponse<JsonElement?>

    @FormUrlEncoded
    @POST
    suspend fun getTaskChecklist(
        @Url url: String,
        @Query("taskId") taskId: String
    ): BitrixResponse<List<ChecklistItemDto>>

    @FormUrlEncoded
    @POST
    suspend fun completeChecklistItem(
        @Url url: String,
        @Field("taskId") taskId: String,
        @Field("itemId") itemId: String
    ): BitrixResponse<JsonElement?>

    @FormUrlEncoded
    @POST
    suspend fun renewChecklistItem(
        @Url url: String,
        @Field("taskId") taskId: String,
        @Field("itemId") itemId: String
    ): BitrixResponse<JsonElement?>
    
    @POST
    suspend fun getGroups(
         @Url url: String,
         @Query("ORDER[NAME]") order: String = "ASC"
    ): BitrixResponse<List<GroupDto>>

    @FormUrlEncoded
    @POST
    suspend fun completeTask(
        @Url url: String,
        @Field("taskId") taskId: String
    ): BitrixResponse<JsonElement?>

    @FormUrlEncoded
    @POST
    suspend fun addElapsedTime(
        @Url url: String,
        @Field("taskId") taskId: String,
        @Field("arFields[SECONDS]") seconds: Int,
        @Field("arFields[COMMENT_TEXT]") comment: String,
        @Field("arFields[USER_ID]") userId: String
    ): BitrixResponse<JsonElement?>

    @FormUrlEncoded
    @POST
    suspend fun addComment(
        @Url url: String,
        @Field("taskId") taskId: String,
        @Field("fields[POST_MESSAGE]") comment: String,
        @Field("fields[AUTHOR_ID]") userId: String
    ): BitrixResponse<JsonElement?>
}

@kotlinx.serialization.Serializable
data class TasksResult(
    val tasks: List<TaskDto> = emptyList()
)

@kotlinx.serialization.Serializable
data class TaskResult(
    val task: TaskDto? = null
)

