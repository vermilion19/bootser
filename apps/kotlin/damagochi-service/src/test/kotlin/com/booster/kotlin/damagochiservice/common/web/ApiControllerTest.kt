package com.booster.kotlin.damagochiservice.common.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, properties = ["server.port=18081"])
class ApiControllerTest {
    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `missing X-User-Id header returns bad request`() {
        val response = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:18081/api/creatures"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(response.body()).contains("MISSING_HEADER")
    }

    @Test
    fun `battle queue request validates creature id`() {
        val response = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:18081/api/battles/random/queue"))
                .header("X-User-Id", "1")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"creatureId":0}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(response.body()).contains("VALIDATION_ERROR")
    }
}

