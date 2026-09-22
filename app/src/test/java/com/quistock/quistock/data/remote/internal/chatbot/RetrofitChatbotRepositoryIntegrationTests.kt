package com.quistock.quistock.data.remote.internal.chatbot

import com.quistock.quistock.app.di.createChatbotApi
import com.quistock.quistock.app.di.createJson
import com.quistock.quistock.app.di.createRetrofit
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.ChatbotResponse
import com.quistock.quistock.domain.model.FlowType
import com.quistock.quistock.domain.model.SuggestedAction
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection

class RetrofitChatbotRepositoryIntegrationTests {
    private lateinit var server: MockWebServer
    private lateinit var json: Json
    private lateinit var sendMessageToChatbot: SendMessageToChatbotUseCase

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        json = createJson()

        val retrofit = createRetrofit(
            baseUrl = server.url("/api/").toString(),
            json = json,
        )

        val repository = RetrofitChatbotRepository(createChatbotApi(retrofit))
        sendMessageToChatbot = SendMessageToChatbotUseCase(repository)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `should send request and map successful response to domain`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "answer": "Create a promotion for milk",
                  "responsible_agent": "operational_agent",
                  "referenced_data": [
                    {
                      "product_id": "product-1",
                      "name": "Whole Milk",
                      "flow_type": "LOW",
                      "suggested_action": "PROMOTION"
                    }
                  ],
                  "new_backend_field": "ignored"
                }
                """.trimIndent(),
            ),
        )

        val result = sendMessageToChatbot(
            ChatbotRequest(
                userId = "user-123",
                message = "Which products need a promotion?",
            ),
        )

        result shouldBe ChatbotResponse(
            answer = "Create a promotion for milk",
            responsibleAgent = "operational_agent",
            referencedData = listOf(
                ChatbotResponse.ReferencedData(
                    productId = "product-1",
                    name = "Whole Milk",
                    flowType = FlowType.LOW,
                    suggestedAction = SuggestedAction.PROMOTION,
                ),
            ),
        )

        val recordedRequest = server.takeRequest()
        recordedRequest.method shouldBe "POST"
        recordedRequest.path shouldBe "/api/chat"
        recordedRequest.getHeader("Content-Type").orEmpty() shouldContain "application/json"
        json.parseToJsonElement(recordedRequest.body.readUtf8()) shouldBe
            json.parseToJsonElement(
                """
                {
                  "user_id": "user-123",
                  "message": "Which products need a promotion?"
                }
                """.trimIndent(),
            )
    }

    @Test
    fun `should reject response containing unknown enum value`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "answer": "Review stock",
                  "responsible_agent": "operational_agent",
                  "referenced_data": [
                    {
                      "product_id": "product-1",
                      "name": "Whole Milk",
                      "flow_type": "CRITICAL",
                      "suggested_action": "MONITOR"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val exception = runCatching {
            sendMessageToChatbot(
                ChatbotRequest(
                    userId = "user-123",
                    message = "Review the stock",
                ),
            )
        }.exceptionOrNull()

        exception.shouldBeInstanceOf<SerializationException>()
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_OK)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
