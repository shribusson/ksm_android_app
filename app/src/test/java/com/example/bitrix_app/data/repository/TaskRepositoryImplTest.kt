package com.example.bitrix_app.data.repository

import com.example.bitrix_app.data.local.dao.SyncQueueDao
import com.example.bitrix_app.data.local.dao.TaskDao
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TaskRepositoryImplTest {

    private lateinit var taskRepository: TaskRepositoryImpl
    private val taskDao: TaskDao = mock()
    private val syncQueueDao: SyncQueueDao = mock()
    private val httpClient: OkHttpClient = mock()

    @Before
    fun setUp() {
        taskRepository = TaskRepositoryImpl(taskDao, syncQueueDao, httpClient)
    }

    @Test
    fun `refreshTasks should make correct API call and update database`() = runBlocking {
        // Given
        val userId = "123"
        val webhookUrl = "https://example.com/rest/1/webhook/"
        val mockCall: Call = mock()
        val responseBody = """
            {
                "result": {
                    "tasks": [
                        {
                            "id": "1",
                            "title": "Test Task 1",
                            "description": "",
                            "timeSpentInLogs": 0,
                            "timeEstimate": 0,
                            "deadline": null,
                            "status": "2",
                            "priority": "0"
                        }
                    ]
                }
            }
        """.trimIndent().toResponseBody("application/json".toMediaType())

        val mockResponse = Response.Builder()
            .request(Request.Builder().url("${webhookUrl}tasks.task.list.json").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        whenever(httpClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(mockResponse)

        // When
        val result = taskRepository.refreshTasks(userId, webhookUrl)

        // Then
        assert(result.isSuccess)

        val requestCaptor = argumentCaptor<Request>()
        verify(httpClient).newCall(requestCaptor.capture())
        val capturedRequest = requestCaptor.firstValue

        assert(capturedRequest.url.toString() == "${webhookUrl}tasks.task.list.json")
        val formBody = capturedRequest.body as FormBody
        assert(formBody.size == 1)
        assert(formBody.name(0) == "filter[RESPONSIBLE_ID]")
        assert(formBody.value(0) == userId)

        verify(taskDao).deleteAllForUser(userId)
        verify(taskDao).insertAll(any())
    }
}
