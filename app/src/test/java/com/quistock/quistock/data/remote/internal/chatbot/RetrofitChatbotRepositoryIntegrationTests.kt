package com.quistock.quistock.data.remote.internal.chatbot

import com.quistock.quistock.app.di.createJson
import com.quistock.quistock.domain.model.ChatbotHttpException
import com.quistock.quistock.domain.model.ChatbotProductCard
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.SessionSnapshot
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RetrofitChatbotRepositoryIntegrationTests {
    private lateinit var server: MockWebServer
    private lateinit var json: Json
    private lateinit var repository: RetrofitChatbotRepository

    @Before
    fun setup() {
        server = MockWebServer().also { it.start() }
        json = createJson()
        val api = Retrofit.Builder().baseUrl(server.url("/api/")).addConverterFactory(
            json.asConverterFactory("application/json".toMediaType()),
        ).build().create(ChatbotApi::class.java)
        repository = RetrofitChatbotRepository(api, json)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `sends an opaque conversation id and maps the confirmed core envelope`() = runTest {
        server.enqueue(
            response(
                """
                {"conversation_id":"opaque-id","request_id":"req-1","response":"Há dois produtos.","status":"completed"}
                """.trimIndent(),
            ),
        )
        val result = repository.sendMessage(
            ChatbotRequest("opaque-id", "Quais produtos?"),
            SessionSnapshot(1, true),
        )
        result.conversationId shouldBe "opaque-id"
        result.response shouldBe "Há dois produtos."
        val request = server.takeRequest()
        request.method shouldBe "POST"
        request.path shouldBe "/api/chat"
        json.parseToJsonElement(request.body.readUtf8()) shouldBe json.parseToJsonElement(
            """{"message":"Quais produtos?","conversation_id":"opaque-id","is_resuming_conversation":false}""",
        )
    }

    @Test
    fun `FEFO card contains only supported bounded display fields`() = runTest {
        server.enqueue(
            response(
                """{"conversation_id":"id","request_id":"req-1","response":"Consulte o ERP.","status":"completed"}""",
            ),
        )
        repository.sendMessage(
            ChatbotRequest(
                "id",
                "Ajude com este alerta",
                productCard = ChatbotProductCard("a".repeat(205), "b".repeat(125)),
            ),
            SessionSnapshot(1, true),
        )
        val card = json.parseToJsonElement(server.takeRequest().body.readUtf8())
            .jsonObject["product_card"]!!.jsonObject
        card.keys shouldBe setOf("product_name", "category_name")
        card.getValue("product_name").jsonPrimitive.content.length shouldBe 200
        card.getValue("category_name").jsonPrimitive.content.length shouldBe 120
    }

    @Test
    fun `422 body is decoded as a rejected chat response`() = runTest {
        server.enqueue(
            response(
                """{"conversation_id":"id","request_id":"req-2","response":"Pergunta inválida.","status":"rejected"}""",
                422,
            ),
        )
        val result = repository.sendMessage(ChatbotRequest("id", "pergunta"), SessionSnapshot(1, true))
        result.status shouldBe "rejected"
        result.response shouldBe "Pergunta inválida."
    }

    @Test
    fun `definitive client error is classified with its HTTP status`() = runTest {
        server.enqueue(response("""{"error":"invalid request"}""", 400))
        val error = runCatching {
            repository.sendMessage(ChatbotRequest("id", "pergunta"), SessionSnapshot(1, true))
        }.exceptionOrNull() as ChatbotHttpException
        error.status shouldBe 400
    }

    @Test
    fun `lists ended conversations reads history and ends an exact conversation`() = runTest {
        server.enqueue(
            response(
                """
                {"conversations":[{"conversation_id":"ended-id","title":"Estoque","updated_at":"2026-10-31T12:00:00Z"}]}
                """.trimIndent(),
            ),
        )
        server.enqueue(
            response(
                """
                {"conversation_id":"ended-id","title":"Estoque","status":"ended","messages":[{"message_id":"m1","role":"user","content":"Ajuda","created_at":"2026-10-31T11:00:00Z"}]}
                """.trimIndent(),
            ),
        )
        server.enqueue(
            response(
                """
                {"conversation_id":"ended-id","request_id":"end-1","job_id":"job-1","status":"accepted","summary_status":"pending"}
                """.trimIndent(),
                202,
            ),
        )

        repository.listEnded(SessionSnapshot(2, true)).single().id shouldBe "ended-id"
        repository.history("ended-id", SessionSnapshot(2, true)).messages.single().content shouldBe "Ajuda"
        repository.end("ended-id", SessionSnapshot(2, true))
        server.takeRequest().path shouldBe "/api/chat/conversations"
        server.takeRequest().path shouldBe "/api/chat/conversations/ended-id/messages"
        val end = server.takeRequest()
        end.method shouldBe "POST"
        end.path shouldBe "/api/chat/conversations/ended-id/end"
    }

    private fun response(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
}
