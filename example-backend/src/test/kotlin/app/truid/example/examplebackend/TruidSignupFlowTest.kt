package app.truid.example.examplebackend

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
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
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpHeaders.COOKIE
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.RequestEntity.get
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.net.HttpCookie

fun <T> ResponseEntity<T>.location() = this.headers[HttpHeaders.LOCATION]?.firstOrNull()
fun <T> ResponseEntity<T>.setCookie() = this.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()
fun URIBuilder.getParam(name: String) = this.queryParams.firstOrNull { it.name == name }?.value

fun ResponseDefinitionBuilder.withJsonBody(body: Any): ResponseDefinitionBuilder = this
    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
    .withBody(ObjectMapper().writeValueAsString(body))

@AutoConfigureWireMock(port = 0)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class TruidSignupFlowTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Nested
    inner class ConfirmSignupEndpoint {
        @Test
        fun `It should redirect to Truid authorization endpoint`() {
            val res = rest.exchange(
                get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java,
            )
            assertEquals(302, res.statusCodeValue)

            val url = URIBuilder(res.location())
            assertEquals("https", url.scheme)
            assertEquals("api.truid.app", url.host)
            assertEquals("/oauth2/v1/authorize/confirm-signup", url.path)
            assertEquals("code", url.getParam("response_type"))
            assertEquals("test-client-id", url.getParam("client_id"))
            assertNotNull(url.getParam("scope"))
        }

        @Test
        fun `It should not add client_secret to the returned URL`() {
            val res = rest.exchange(
                get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java,
            )
            assertEquals(302, res.statusCodeValue)

            val url = URIBuilder(res.location())
            assertNull(url.getParam("client_secret"))
        }

        @Test
        fun `It should add a state parameter to the returned URL`() {
            val res = rest.exchange(
                get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java,
            )
            assertEquals(302, res.statusCodeValue)

            val url = URIBuilder(res.location())
            assertNotNull(url.getParam("state"))
        }

        @Test
        fun `It should use PKCE with S256`() {
            val res = rest.exchange(
                get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java,
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
                get("/truid/v1/confirm-signup")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build(),
                Void::class.java,
            )
            assertEquals(202, res.statusCodeValue)

            val url = URIBuilder(res.location())
            assertEquals("https", url.scheme)
            assertEquals("api.truid.app", url.host)
            assertEquals("/oauth2/v1/authorize/confirm-signup", url.path)
        }

        @Test
        fun `It should return a cookie containing the session ID`() {
            val res = rest.exchange(
                get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java,
            )
            assertEquals(302, res.statusCodeValue)
            val cookie = HttpCookie.parse(res.setCookie()).single()
            assertEquals(true, cookie.isHttpOnly)
        }
        @Test
        fun `Presentation should return a 403 if no tokens have been exchanged`() {
            val res = rest.exchange(
                get("/truid/v1/presentation")
                    .build(),
                Void::class.java,
            )
            assertEquals(403, res.statusCodeValue)
        }
    }

    @Nested
    inner class CompleteSignup {
        private lateinit var state: String
        private lateinit var cookie: HttpCookie

        @BeforeEach
        fun `setup authorization`() {
            WireMock.stubFor(
                post(urlEqualTo("/oauth2/v1/token")).willReturn(
                    aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withJsonBody(
                            mapOf(
                                "access_token" to "access-123",
                                "token_type" to "bearer",
                                "expires_in" to 300,
                                "refresh_token" to "refresh-123",
                                "scope" to "truid.app/data-point/email",
                            ),
                        ),
                )
            )
        }

        @BeforeEach
        fun `Start signup`() {
            val res = rest.exchange(
                get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java,
            )
            assertEquals(302, res.statusCodeValue)

            cookie = HttpCookie.parse(res.setCookie()).single()
            val url = URIBuilder(res.location())
            state = url.getParam("state")!!
        }

        @Test
        fun `Complete signup should return 200`() {

            WireMock.stubFor(
                WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1").willReturn(
                    aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withJsonBody(
                            PresentationResponse(
                                sub = "1234567abcdefg",
                                claims = listOf(PresentationResponseClaims(type="truid.app/claim/email/v1", value="email@example.com"))),
                        ),
                )
            )

            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=${state}")
                    .header(COOKIE, cookie.toString())
                    .build(),
                Map::class.java,
            )
            assertEquals(200, res.statusCodeValue)
        }

        @Test
        fun `Should return 403 on error authorization response`() {
            val res = rest.exchange(
                get("/truid/v1/complete-signup?error=access_denied")
                    .header(COOKIE, cookie.toString())
                    .build(),
                Map::class.java,
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }

        @Test
        fun `Should return 403 on mismatching state`() {
            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=wrong-state")
                    .header(COOKIE, cookie.toString())
                    .build(),
                Map::class.java,
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }

        @Test
        fun `Should return 403 on null state`() {
            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234")
                    .header(COOKIE, cookie.toString())
                    .build(),
                Map::class.java,
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }

        @Test
        fun `Should not allow the same state twice`() {

            WireMock.stubFor(
                WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1").willReturn(
                    aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withJsonBody(
                            PresentationResponse(
                                sub = "1234567abcdefg",
                                claims = listOf(PresentationResponseClaims(type="truid.app/claim/email/v1", value="email@example.com"))),
                        ),
                )
            )

            val res1 = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=${state}")
                    .header(COOKIE, cookie.toString())
                    .build(),
                Map::class.java,
            )
            assertEquals(200, res1.statusCodeValue)

            val res2 = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=${state}")
                    .header(COOKIE, cookie.toString())
                    .build(),
                Map::class.java,
            )
            assertEquals(403, res2.statusCodeValue)
            assertEquals("access_denied", res2.body?.get("error"))
        }

        @Test
        fun `Should return 403 on unauthorized requests`() {
            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=${state}")
                    .build(),
                Map::class.java,
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }

        @Test
        fun `Should return 403 if the token request fails`() {
            WireMock.stubFor(
                post(urlEqualTo("/oauth2/v1/token")).willReturn(
                    aResponse()
                        .withStatus(403)
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withJsonBody(
                            mapOf(
                                "error" to "access_denied",
                            ),
                        ),
                )
            )

            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=${state}")
                    .header(COOKIE, cookie.toString())
                    .build(),
                Map::class.java,
            )
            assertEquals(403, res.statusCodeValue)
            assertEquals("access_denied", res.body?.get("error"))
        }
    }

    @Nested
    inner class CompleteSignupWebFlow {
        private lateinit var state: String
        private lateinit var cookie: HttpCookie

        @BeforeEach
        fun `setup authorization`() {
            WireMock.stubFor(
                post(urlEqualTo("/oauth2/v1/token")).willReturn(
                    aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withJsonBody(
                            mapOf(
                                "access_token" to "access-123",
                                "token_type" to "bearer",
                                "expires_in" to 300,
                                "refresh_token" to "refresh-123",
                                "scope" to "truid.app/data-point/email",
                            ),
                        ),
                )
            )
        }

        @BeforeEach
        fun `Start signup`() {
            val res = rest.exchange(
                get("/truid/v1/confirm-signup")
                    .build(),
                Void::class.java,
            )
            assertEquals(302, res.statusCodeValue)

            cookie = HttpCookie.parse(res.setCookie()).single()
            val url = URIBuilder(res.location())
            state = url.getParam("state")!!
        }

        @Test
        fun `Complete signup should return 302`() {

            WireMock.stubFor(
                WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1").willReturn(
                    aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withJsonBody(
                            PresentationResponse(
                                sub = "1234567abcdefg",
                                claims = listOf(PresentationResponseClaims(type="truid.app/claim/email/v1", value="email@example.com"))),
                        ),
                )
            )

            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=${state}")
                    .header(COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java,
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/success.html", url.path)
        }

        @Test
        fun `Should return 302 with error on error authorization response`() {
            val res = rest.exchange(
                get("/truid/v1/complete-signup?error=access_denied")
                    .header(COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java,
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Test
        fun `Should return 302 with error on mismatching state`() {
            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=wrong-state")
                    .header(COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java,
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Test
        fun `Should return 302 with error on null state`() {
            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234")
                    .header(COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java,
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Test
        fun `Should return 302 with error on unauthorized requests`() {
            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=${state}")
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java,
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Test
        fun `Should return 302 with error if the token request fails`() {
            WireMock.stubFor(
                post(urlEqualTo("/oauth2/v1/token")).willReturn(
                    aResponse()
                        .withStatus(403)
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withJsonBody(
                            mapOf(
                                "error" to "access_denied",
                            ),
                        ),
                )
            )

            val res = rest.exchange(
                get("/truid/v1/complete-signup?code=1234&state=${state}")
                    .header(COOKIE, cookie.toString())
                    .accept(MediaType.TEXT_HTML)
                    .build(),
                Void::class.java,
            )
            val url = URIBuilder(res.location())
            assertEquals(302, res.statusCodeValue)
            assertEquals("/failure.html", url.path)
            assertEquals("access_denied", url.getParam("error"))
        }

        @Nested
        inner class WithPersistedTokens {

            @BeforeEach
            fun `Complete signup`() {

                WireMock.stubFor(
                    WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1").willReturn(
                        aResponse()
                            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                            .withStatus(200)
                            .withJsonBody(
                                PresentationResponse(
                                    sub = "1234567abcdefg",
                                    claims = listOf(PresentationResponseClaims(type="truid.app/claim/email/v1", value="email@example.com"))),
                            ),
                    )
                )

                val res = rest.exchange(
                    get("/truid/v1/complete-signup?code=1234&state=${state}")
                        .header(COOKIE, cookie.toString())
                        .accept(MediaType.TEXT_HTML)
                        .build(),
                    Void::class.java,
                )
                val url = URIBuilder(res.location())
                assertEquals(302, res.statusCodeValue)
                assertEquals("/success.html", url.path)
            }


            @Test
            fun `Should return presentation`() {
                val res = rest.exchange(
                    get("/truid/v1/presentation")
                        .header(COOKIE, cookie.toString())
                        .accept(MediaType.APPLICATION_JSON)
                        .build(),
                    PresentationResponse::class.java,
                )
                assertEquals(200, res.statusCodeValue)
                assertEquals("1234567abcdefg", res.body!!.sub)
            }

        }
    }

    @Test
    fun `Should use refresh token if access token is expired`(){
        WireMock.stubFor(
            post(urlEqualTo("/oauth2/v1/token"))
                .withRequestBody(containing("grant_type=authorization_code"))
                .willReturn(
                aResponse()
                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .withStatus(200)
                    .withJsonBody(
                        mapOf(
                            "access_token" to "access-123",
                            "token_type" to "bearer",
                            "expires_in" to 1,
                            "refresh_token" to "refresh-123",
                            "scope" to "truid.app/data-point/email",
                        ),
                    ),
            )
        )

        WireMock.stubFor(
            WireMock.get("/exchange/v1/presentation?claims=truid.app%2Fclaim%2Femail%2Fv1").willReturn(
                aResponse()
                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .withStatus(200)
                    .withJsonBody(
                        PresentationResponse(
                            sub = "1234567abcdefg",
                            claims = listOf(PresentationResponseClaims(type="truid.app/claim/email/v1", value="email@example.com"))),
                    ),
            )
        )


        WireMock.stubFor(
            post(urlEqualTo("/oauth2/v1/token"))
                .withRequestBody(containing("grant_type=refresh_token"))
                .willReturn(
                    aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withJsonBody(
                            mapOf(
                                "access_token" to "access-456",
                                "token_type" to "bearer",
                                "expires_in" to 1,
                                "refresh_token" to "refresh-456",
                                "scope" to "truid.app/data-point/email",
                            ),
                        ),
                )
        )

        val res1 = rest.exchange(
            get("/truid/v1/confirm-signup")
                .build(),
            Void::class.java,
        )
        assertEquals(302, res1.statusCodeValue)

        val cookie = HttpCookie.parse(res1.setCookie()).single()
        val url = URIBuilder(res1.location())
        val state = url.getParam("state")!!

        rest.exchange(
            get("/truid/v1/complete-signup?code=1234&state=${state}")
                .header(COOKIE, cookie.toString())
                .build(),
            Map::class.java,
        ).also { assertEquals(200, it.statusCodeValue) }

        val res = rest.exchange(
            get("/truid/v1/presentation")
                .header(COOKIE, cookie.toString())
                .accept(MediaType.APPLICATION_JSON)
                .build(),
            PresentationResponse::class.java,
        ).also {
            assertEquals(200, it.statusCodeValue)
            assertEquals("1234567abcdefg", it.body!!.sub)
        }
    }
}
