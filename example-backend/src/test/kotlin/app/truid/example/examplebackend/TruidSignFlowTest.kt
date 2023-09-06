package app.truid.example.examplebackend

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.apache.http.client.utils.URIBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.test.context.ActiveProfiles
import java.net.HttpCookie

@AutoConfigureWireMock(port = 0)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class TruidSignFlowTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Nested
    inner class ConfirmSignEndpoint {
        @Test
        fun `It should redirect to Truid authorization endpoint`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .build(),
                Void::class.java
            )
            assertEquals(302, res.statusCodeValue)

            val url = URIBuilder(res.location())
            assertEquals("https", url.scheme)
            assertEquals("api.truid.app", url.host)
            assertEquals("/oauth2/v1/authorize/sign", url.path)
            assertEquals("code", url.getParam("response_type"))
            assertEquals("test-client-id", url.getParam("client_id"))
            assertNotNull(url.getParam("scope"))
        }

        @Test
        fun `It should not add client_secret to the returned URL`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .build(),
                Void::class.java
            )
            assertEquals(302, res.statusCodeValue)

            val url = URIBuilder(res.location())
            assertNull(url.getParam("client_secret"))
        }

        @Test
        fun `It should add a state parameter to the returned URL`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .build(),
                Void::class.java
            )
            assertEquals(302, res.statusCodeValue)

            val url = URIBuilder(res.location())
            assertNotNull(url.getParam("state"))
        }

        @Test
        fun `It should use PKCE with S256`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .build(),
                Void::class.java
            )
            assertEquals(302, res.statusCodeValue)

            val url = URIBuilder(res.location())
            assertNotNull(url.getParam("code_challenge"))
            assertEquals(43, url.getParam("code_challenge")?.length)
            assertEquals("S256", url.getParam("code_challenge_method"))
        }

        @Test
        fun `It should use a 202 response for XMLHttpRequest clients`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build(),
                Void::class.java
            )
            assertEquals(202, res.statusCodeValue)

            val url = URIBuilder(res.location())
            assertEquals("https", url.scheme)
            assertEquals("api.truid.app", url.host)
            assertEquals("/oauth2/v1/authorize/sign", url.path)
        }

        @Test
        fun `It should return a cookie containing the session ID`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .build(),
                Void::class.java
            )
            assertEquals(302, res.statusCodeValue)
            val cookie = HttpCookie.parse(res.setCookie()).single()
            assertEquals(true, cookie.isHttpOnly)
        }
    }

    @Nested
    inner class CompleteSign {
        private lateinit var state: String
        private lateinit var cookie: HttpCookie

        @BeforeEach
        fun `setup authorization`() {
            stubFor(
                post(urlEqualTo("/oauth2/v1/token")).willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withJsonBody(
                            mapOf(
                                "access_token" to "access-123",
                                "token_type" to "bearer",
                                "expires_in" to 300,
                                "refresh_token" to "refresh-123",
                                "scope" to "truid.app/data-point/email"
                            )
                        )
                )
            )
        }

        @BeforeEach
        fun `Start sign`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .build(),
                Void::class.java
            )
            assertEquals(302, res.statusCodeValue)

            cookie = HttpCookie.parse(res.setCookie()).single()
            val url = URIBuilder(res.location())
            state = url.getParam("state")!!
        }

        @Test
        fun `Complete sign should return 200`() {
            stubFor(
                get("/exchange/v1/signature").willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/jose")
                        .withStatus(200)
                        .withBody("signature")
                )
            )

            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )
            assertEquals(200, res.statusCodeValue)
        }

        @Test
        fun `Should return 403 on error authorization response`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?error=access_denied")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }

        @Test
        fun `Should return 403 on mismatching state`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=wrong-state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }

        @Test
        fun `Should return 403 on null state`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }

        @Test
        fun `Should not allow the same state twice`() {
            stubFor(
                get("/exchange/v1/signature").willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/jose")
                        .withStatus(200)
                        .withBody("signature")
                )
            )

            val res1 = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )
            assertEquals(200, res1.statusCodeValue)

            val res2 = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )
            assertEquals(403, res2.statusCodeValue)
            assertEquals("access_denied", res2.body?.get("error"))
        }

        @Test
        fun `Should return 403 on unauthorized requests`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                    .build(),
                Map::class.java
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }

        @Test
        fun `Should return 403 if the token request fails`() {
            stubFor(
                post(urlEqualTo("/oauth2/v1/token")).willReturn(
                    aResponse()
                        .withStatus(403)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withJsonBody(
                            mapOf(
                                "error" to "access_denied"
                            )
                        )
                )
            )

            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }
    }

    @Nested
    inner class CompleteSignWebFlow {
        private lateinit var state: String
        private lateinit var cookie: HttpCookie

        @BeforeEach
        fun `setup authorization`() {
            stubFor(
                post(urlEqualTo("/oauth2/v1/token")).willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withJsonBody(
                            mapOf(
                                "access_token" to "access-123",
                                "token_type" to "bearer",
                                "expires_in" to 300,
                                "refresh_token" to "refresh-123",
                                "scope" to "truid.app/data-point/email"
                            )
                        )
                )
            )
        }

        @BeforeEach
        fun `Start sign`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .build(),
                Void::class.java
            )
            assertEquals(302, res.statusCodeValue)

            cookie = HttpCookie.parse(res.setCookie()).single()
            val url = URIBuilder(res.location())
            state = url.getParam("state")!!
        }

        @Test
        fun `Complete sign should return 302`() {
            stubFor(
                get("/exchange/v1/signature").willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/jose")
                        .withStatus(200)
                        .withBody("signature")
                )
            )

            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/sign/success.html", url.path)
        }

        @Test
        fun `Should return 302 with error on error authorization response`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?error=access_denied")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/sign/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Test
        fun `Should return 302 with error on mismatching state`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=wrong-state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/sign/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Test
        fun `Should return 302 with error on null state`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/sign/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Test
        fun `Should return 302 with error on unauthorized requests`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/sign/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Test
        fun `Should return 302 with error if the token request fails`() {
            stubFor(
                post(urlEqualTo("/oauth2/v1/token")).willReturn(
                    aResponse()
                        .withStatus(403)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withJsonBody(
                            mapOf(
                                "error" to "access_denied"
                            )
                        )
                )
            )

            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/sign/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Nested
        inner class WithPersistedTokens {

            @BeforeEach
            fun `Complete sign`() {
                stubFor(
                    get("/exchange/v1/signature").willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/jose")
                            .withStatus(200)
                            .withBody("signature")
                    )
                )

                val res = rest.exchange(
                    RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                        .header(HttpHeaders.COOKIE, cookie.toString())
                        .accept(MediaType.TEXT_HTML)
                        .build(),
                    Void::class.java
                )
                val url = URIBuilder(res.location())
                assertEquals(302, res.statusCodeValue)
                assertEquals("/sign/success.html", url.path)
            }
        }
    }
}
