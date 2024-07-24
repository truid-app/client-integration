package app.truid.example.examplebackend

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.findAll
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import org.apache.http.client.utils.URIBuilder
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.time.Duration
import java.time.Instant

@AutoConfigureWireMock(port = 0)
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = [FixedDateTimeProviderConfig::class])
@ActiveProfiles("test")
class TruidSignFlowTest {

    private val SIGNATURE = "eyJhbGciOiJFUzI1NiIsInR5cCI6Impvc2UiLCJjcml0IjpbInNpZ1QiLCJzaWdEIiwiYjY0Il0sInNpZ1QiOiIyMDI0LTA3LTI0VDE3OjI1OjE2WiIsInNpZ0QiOnsibUlkIjoiaHR0cDovL3VyaS5ldHNpLm9yZy8xOTE4Mi9PYmplY3RJZEJ5VVJJSGFzaCIsInBhcnMiOlsiaHR0cDovL2xvY2FsaG9zdDo4MDgwL2RvY3VtZW50cy9BZ3JlZW1lbnQucGRmIl0sImhhc2hNIjoiUzI1NiIsImhhc2hWIjpbImhSUnRxUEp0RWRHRU5Yb3d1U2w5alFoRElaUDdtcEo3YmotemJmdnlBeUUiXSwiY3R5cyI6WyJhcHBsaWNhdGlvbi9wZGYiXX0sImI2NCI6ZmFsc2UsIng1YyI6WyJNSUlDVnpDQ0FmMmdBd0lCQWdJSFNEbSs1cFNFbmpBS0JnZ3Foa2pPUFFRREFqQkxNUXN3Q1FZRFZRUUdFd0pUUlRFWU1CWUdBMVVFQ2d3UFZISjFhV1FnUVVJZ0xTQlVSVk5VTVNJd0lBWURWUVFEREJsVVJWTlVJQzBnU1c1MFpYSnRaV1JwWVhSbElFTkJJSFl4TUI0WERUSTBNRGN5TkRFM01qUXdNRm9YRFRJME1EZ3lNekUzTWpRd01Gb3dZekV0TUNzR0ExVUVRUXdrWkdFNE9ERXlaR1V0Wm1ZM05TMDBPV0V6TFdJeFpqUXRaalJsTldSaVpqWmhaREZqTVRJd01BWURWUVFERENsd2NHbGtQV1JoT0RneE1tUmxMV1ptTnpVdE5EbGhNeTFpTVdZMExXWTBaVFZrWW1ZMllXUXhZekJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEEwSUFCSTlEYUd2N2JqclNvVGZ1cGdXcVlEWlYrM1I1UlkyOVdCaHFHTjFPWDFIOUkxMEd3Q3kxbGlHeW94TzAvbUFac1RnQitBSWlrcWorVnBZUWR4bmpSYnFqZ2JNd2diQXdjUVlEVlIwakJHb3dhSUFVOE5YQS9lUmkwaDBBYmVkQXkwaEZYNGhQVGFpaFI2UkZNRU14Q3pBSkJnTlZCQVlUQWxORk1SZ3dGZ1lEVlFRS0RBOVVjblZwWkNCQlFpQXRJRlJGVTFReEdqQVlCZ05WQkFNTUVWUkZVMVFnTFNCU2IyOTBJRU5CSUhZeGdnZEhRVk9mK3JpQ01CMEdBMVVkRGdRV0JCUm9WZk5EeUh1ZkZTQnY3OUwyZmRwU3AvZjNpakFNQmdOVkhSTUJBZjhFQWpBQU1BNEdBMVVkRHdFQi93UUVBd0lHd0RBS0JnZ3Foa2pPUFFRREFnTklBREJGQWlFQXIrK2F1N0RvcDhNeTNRZ0ZQaXQ2VDdGYVZMbFlvUzJwNkVkWnRwaFR1dmtDSUZ3cVlhTjA2eHZkU0FSOTk1THdnL295TmllbW9RbjZmRU92cGJhaVN0NmsiLCJNSUlDZURDQ0FoMmdBd0lCQWdJSFIwRlRuL3E0Z2pBS0JnZ3Foa2pPUFFRREFqQkRNUXN3Q1FZRFZRUUdFd0pUUlRFWU1CWUdBMVVFQ2d3UFZISjFhV1FnUVVJZ0xTQlVSVk5VTVJvd0dBWURWUVFEREJGVVJWTlVJQzBnVW05dmRDQkRRU0IyTVRBZUZ3MHlNekE1TVRJd01EQXdNREJhRncweU5EQTVNVEl3TURBd01EQmFNRXN4Q3pBSkJnTlZCQVlUQWxORk1SZ3dGZ1lEVlFRS0RBOVVjblZwWkNCQlFpQXRJRlJGVTFReElqQWdCZ05WQkFNTUdWUkZVMVFnTFNCSmJuUmxjbTFsWkdsaGRHVWdRMEVnZGpFd1dUQVRCZ2NxaGtqT1BRSUJCZ2dxaGtqT1BRTUJCd05DQUFTelF0Q2dFcDdMRmgyMm52YUhiU0JNVUszMEJObkp4NEp2Tm1zWGJEWGwxUWJKVHF2bjBrRUhWajMyTHVVS2xWNkJ4cVNkb2d2UHBKMVlJYzg4WkNNSW80SHpNSUh3TUhFR0ExVWRJd1JxTUdpQUZKV0ZGSUJUS3JJd0FpTVNuR1R4bzlMQkRtR1FvVWVrUlRCRE1Rc3dDUVlEVlFRR0V3SlRSVEVZTUJZR0ExVUVDZ3dQVkhKMWFXUWdRVUlnTFNCVVJWTlVNUm93R0FZRFZRUUREQkZVUlZOVUlDMGdVbTl2ZENCRFFTQjJNWUlIUjBGVFdQcEg4ekFkQmdOVkhRNEVGZ1FVOE5YQS9lUmkwaDBBYmVkQXkwaEZYNGhQVGFnd0VnWURWUjBUQVFIL0JBZ3dCZ0VCL3dJQkFEQU9CZ05WSFE4QkFmOEVCQU1DQWNZd09BWURWUjBmQkRFd0x6QXRvQ3VnS1lZbmFIUjBjRG92TDNCcmFTNTBjblZwWkM1a1pYWXZZM0pzTDNKdmIzUXRZMkV0ZGpFdVkzSnNNQW9HQ0NxR1NNNDlCQU1DQTBrQU1FWUNJUURINnkrYndmcnhnR0dkZDBPOHZ5ZGNlTS96bGFCZXNSczhxVG9TRXhteHJBSWhBTWk2T2pjQTlReEpWcXpaeGRZTE5raE1uMG5WZ2NmS1JIbFBFUUc3VVdvOSIsIk1JSUIzakNDQVlTZ0F3SUJBZ0lIUjBGVFdQcEg4ekFLQmdncWhrak9QUVFEQWpCRE1Rc3dDUVlEVlFRR0V3SlRSVEVZTUJZR0ExVUVDZ3dQVkhKMWFXUWdRVUlnTFNCVVJWTlVNUm93R0FZRFZRUUREQkZVUlZOVUlDMGdVbTl2ZENCRFFTQjJNVEFlRncweU16QTVNVEl3TURBd01EQmFGdzB5TkRBNU1USXdNREF3TURCYU1FTXhDekFKQmdOVkJBWVRBbE5GTVJnd0ZnWURWUVFLREE5VWNuVnBaQ0JCUWlBdElGUkZVMVF4R2pBWUJnTlZCQU1NRVZSRlUxUWdMU0JTYjI5MElFTkJJSFl4TUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFRnozaVFoRVJVb1JwMHJFVmljZzRlQSsvekQ4TmZhcDhkZlBJV2wxbnlWN1RHb3N5d0wrWnZNYzJWMWM5VkZ6Q2JJcXA5QThSMzloSDA2aTUxejhLZEtOak1HRXdId1lEVlIwakJCZ3dGb0FVbFlVVWdGTXFzakFDSXhLY1pQR2owc0VPWVpBd0hRWURWUjBPQkJZRUZKV0ZGSUJUS3JJd0FpTVNuR1R4bzlMQkRtR1FNQThHQTFVZEV3RUIvd1FGTUFNQkFmOHdEZ1lEVlIwUEFRSC9CQVFEQWdIR01Bb0dDQ3FHU000OUJBTUNBMGdBTUVVQ0lFRmtUMFBIZURvM2EvaGxTSWMzQmJ0RE16enhxVk9xSzd2Y1BwZ1hoWU9OQWlFQWhaaGZRdHMvWm1rN2NIWndkK0ZTTE9PZU41TzZ4WW5hWUthU3hEaGNodFk9Il0sInRydWlkLmFwcC91c2VyX21lc3NhZ2UvdjEiOiJQbGVhc2Ugc2lnbiB0aGlzIGRvY3VtZW50IiwidHJ1aWQuYXBwL3ByZXNlbnRhdGlvbi92MSI6eyJzdWIiOiJkYTg4MTJkZS1mZjc1LTQ5YTMtYjFmNC1mNGU1ZGJmNmFkMWMiLCJjbGFpbXMiOltdfSwieDV0I1MyNTYiOiJYRlFsRHZSMWhFUlZKWmM5alZKcHFlZVhoMjkzWTQzY1hCQlBUX1RJM2NNIn0..dvqkmiEELTRoj7pHZ7F9AGnJfuwC6_GK17bTmI60NBHheausWIQdzj41eE4tJLD2jm8qEWlDSR3CDvkx6RmBSw"
    private val SIGNATURE_DATE = Instant.parse("2024-07-24T17:25:16Z")

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var clock: FixedDateTimeProvider

    @BeforeEach
    fun `setup par`() {
        stubFor(
            post(urlEqualTo("/oauth2/v1/par/sign")).willReturn(
                aResponse()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withStatus(200)
                    .withJsonBody(
                        mapOf(
                            "request_uri" to "request-uri",
                            "expires_in" to 60
                        )
                    )
            )
        )
    }

    @Nested
    inner class SignEndpoint {

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
            assertEquals("request-uri", url.getParam("request_uri"))
            assertEquals("test-client-id", url.getParam("client_id"))
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
        fun `It should add a state parameter to PAR request`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .build(),
                Void::class.java
            )
            assertEquals(302, res.statusCodeValue)

            verify(
                postRequestedFor(urlEqualTo("/oauth2/v1/par/sign"))
                    .withRequestBody(matching(".*state=[a-zA-Z0-9_-]*&.*"))
            )
        }

        @Test
        fun `It should use PKCE with S256`() {
            val res = rest.exchange(
                RequestEntity.get("/truid/v1/sign")
                    .build(),
                Void::class.java
            )
            assertEquals(302, res.statusCodeValue)

            verify(
                postRequestedFor(urlEqualTo("/oauth2/v1/par/sign"))
                    .withRequestBody(matching(".*code_challenge=[a-zA-Z0-9_-]{43}&code_challenge_method=S256&.*"))
            )
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

            // Extract the random state parameter from the request
            val request = findAll(
                postRequestedFor(urlEqualTo("/oauth2/v1/par/sign"))
            ).first().bodyAsString
            val (stateValue) = ".*state=([a-zA-Z0-9_-]*)&.*".toRegex().find(request)!!.destructured
            state = stateValue
        }

        @Test
        fun `Complete sign should return 200`() {
            clock.setTime(SIGNATURE_DATE)
            stubFor(
                get("/exchange/v1/signature").willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/jose")
                        .withStatus(200)
                        .withBody(SIGNATURE)
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
        fun `Signature with invalid certificate date should return 403`() {
            clock.setTime(SIGNATURE_DATE + Duration.ofDays(40))
            stubFor(
                get("/exchange/v1/signature").willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/jose")
                        .withStatus(200)
                        .withBody(SIGNATURE)
                )
            )

            val res = rest.exchange(
                RequestEntity.get("/truid/v1/complete-sign?code=1234&state=$state")
                    .header(HttpHeaders.COOKIE, cookie.toString())
                    .build(),
                Map::class.java
            )
            assertEquals(403, res.statusCodeValue)
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
            clock.setTime(SIGNATURE_DATE)
            stubFor(
                get("/exchange/v1/signature").willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/jose")
                        .withStatus(200)
                        .withBody(SIGNATURE)
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

            // Extract the random state parameter from the request
            val request = findAll(
                postRequestedFor(urlEqualTo("/oauth2/v1/par/sign"))
            ).first().bodyAsString
            val (stateValue) = ".*state=([a-zA-Z0-9_-]*)&.*".toRegex().find(request)!!.destructured
            state = stateValue
        }

        @Test
        fun `Complete sign should return 302`() {
            clock.setTime(SIGNATURE_DATE)
            stubFor(
                get("/exchange/v1/signature").willReturn(
                    aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/jose")
                        .withStatus(200)
                        .withBody(SIGNATURE)
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
                clock.setTime(SIGNATURE_DATE)
                stubFor(
                    get("/exchange/v1/signature").willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/jose")
                            .withStatus(200)
                            .withBody(SIGNATURE)
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
