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
import java.time.Duration
import java.time.Instant

@AutoConfigureWireMock(port = 0)
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = [FixedDateTimeProviderConfig::class])
@ActiveProfiles("test")
class TruidSignFlowTest {

    private val SIGNATURE = "eyJhbGciOiJFUzI1NiIsInR5cCI6Impvc2UiLCJjcml0IjpbInNpZ1QiLCJzaWdEIiwiYjY0Il0sInNpZ1QiOiIyMDIzLTA5LTEwVDEyOjU2OjUyWiIsInNpZ0QiOnsibUlkIjoiaHR0cDovL3VyaS5ldHNpLm9yZy8xOTE4Mi9PYmplY3RJZEJ5VVJJSGFzaCIsInBhcnMiOlsiaHR0cDovL2xvY2FsaG9zdDo4MDgwL2RvY3VtZW50cy9BZ3JlZW1lbnQucGRmIl0sImhhc2hNIjoiUzI1NiIsImhhc2hWIjpbImhSUnRxUEp0RWRHRU5Yb3d1U2w5alFoRElaUDdtcEo3YmotemJmdnlBeUUiXX0sImI2NCI6ZmFsc2UsIng1YyI6WyJNSUlDRXpDQ0FicWdBd0lCQWdJSFJ6KzJ1QWRSU3pBS0JnZ3Foa2pPUFFRREFqQkJNUXN3Q1FZRFZRUUdFd0pUUlRFUk1BOEdBMVVFQ2d3SVZISjFhV1FnUVVJeEh6QWRCZ05WQkFNTUZrUmxkaUJKYm5SbGNtMWxaR2xoZEdVZ1EwRWdkakV3SGhjTk1qTXdPVEV3TURBd01EQXdXaGNOTWpNeE1ERXdNREF3TURBd1dqQTBNVEl3TUFZRFZRUUREQ2x3Y0dsa1BUQTVNamN6WVRGa0xUQmhaakl0TkROaVpTMWhZelpoTFdNelltUmtOMkZtT0RVeU9UQlpNQk1HQnlxR1NNNDlBZ0VHQ0NxR1NNNDlBd0VIQTBJQUJFVmt3M2wwVFZQeFNVbzZzdE92Y1Q5ZGs4Z3BDNUJMOFhENnFXWGMvK0tkWVZ4aldWRDVidm5yNDJWM0pOUlN0UkxodHd2cnMwMTJTa2dRM2IwTnZET2pnYWt3Z2FZd1p3WURWUjBqQkdBd1hvQVVqbmMzaFNHR3dUMFNlZ28rK0dWNzJYc0dsbitoUGFRN01Ea3hDekFKQmdOVkJBWVRBbE5GTVJFd0R3WURWUVFLREFoVWNuVnBaQ0JCUWpFWE1CVUdBMVVFQXd3T1JHVjJJRkp2YjNRZ1EwRWdkakdDQjBjL3RwaVVleWd3SFFZRFZSME9CQllFRk95NVNHemZaUHdSQWlBVWhMa2F1RXFyZUNyT01Bd0dBMVVkRXdFQi93UUNNQUF3RGdZRFZSMFBBUUgvQkFRREFnYkFNQW9HQ0NxR1NNNDlCQU1DQTBjQU1FUUNJQU1NL20veVU1U2hWZEZJRitWQnhtWVU2NjVseW1GZnd2c3N3UHpnc2ZUa0FpQTI1Tk00bGVsZ1hESGZIdkZIenR2V1NaRHo3OUtKSlM2RCt1aGVYS3lPZ3c9PSIsIk1JSUNJRENDQWNXZ0F3SUJBZ0lIUnorMm1KUjdLREFLQmdncWhrak9QUVFEQWpBNU1Rc3dDUVlEVlFRR0V3SlRSVEVSTUE4R0ExVUVDZ3dJVkhKMWFXUWdRVUl4RnpBVkJnTlZCQU1NRGtSbGRpQlNiMjkwSUVOQklIWXhNQjRYRFRJek1Ea3hNREF3TURBd01Gb1hEVEkwTURreE1EQXdNREF3TUZvd1FURUxNQWtHQTFVRUJoTUNVMFV4RVRBUEJnTlZCQW9NQ0ZSeWRXbGtJRUZDTVI4d0hRWURWUVFEREJaRVpYWWdTVzUwWlhKdFpXUnBZWFJsSUVOQklIWXhNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUV0b2RGQSthVWt2K242R0VEZmxlelBSUGFqaU91c3RKRkVaU2xyTytNZHgyUCtXQTRiQlBNTUNFNGRCeDlzUVlmTm01bVRWblM2ZnducWEwMG9XTmY3Nk9CcnpDQnJEQm5CZ05WSFNNRVlEQmVnQlR6WkJtbm9razJ0ekZSL3BnV001YzF5NjRSZTZFOXBEc3dPVEVMTUFrR0ExVUVCaE1DVTBVeEVUQVBCZ05WQkFvTUNGUnlkV2xrSUVGQ01SY3dGUVlEVlFRRERBNUVaWFlnVW05dmRDQkRRU0IyTVlJSFJ5d1R4Q1ZpcXpBZEJnTlZIUTRFRmdRVWpuYzNoU0dHd1QwU2VnbysrR1Y3MlhzR2xuOHdFZ1lEVlIwVEFRSC9CQWd3QmdFQi93SUJBREFPQmdOVkhROEJBZjhFQkFNQ0FjWXdDZ1lJS29aSXpqMEVBd0lEU1FBd1JnSWhBTDI1REdHelR5ZTRPNS9aY3gyMFptRWduZU5nWmp5UGU4U2M2aktwTDE4VEFpRUFvSEVXeFlRZlpuM1pnVHlCclBhektXSENaZ1lIa01FWDJpcnBFUm5qaWxVPSIsIk1JSUJ5akNDQVhDZ0F3SUJBZ0lIUnl3VHhDVmlxekFLQmdncWhrak9QUVFEQWpBNU1Rc3dDUVlEVlFRR0V3SlRSVEVSTUE4R0ExVUVDZ3dJVkhKMWFXUWdRVUl4RnpBVkJnTlZCQU1NRGtSbGRpQlNiMjkwSUVOQklIWXhNQjRYRFRJek1EZ3hOakF3TURBd01Gb1hEVEkwTURneE5qQXdNREF3TUZvd09URUxNQWtHQTFVRUJoTUNVMFV4RVRBUEJnTlZCQW9NQ0ZSeWRXbGtJRUZDTVJjd0ZRWURWUVFEREE1RVpYWWdVbTl2ZENCRFFTQjJNVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEEwSUFCT3dCNk5ON1RUNkZQaTVnS25VQ2ZXQ2dPVDF0d3Q5VnhtRjV0QVRwcktTbWpEY0M1TXJkalhLaGh5WUcrNzVwY2tlaVU1a2NEWkFKN1F2RldJK1F6K0tqWXpCaE1COEdBMVVkSXdRWU1CYUFGUE5rR2FlaVNUYTNNVkgrbUJZemx6WExyaEY3TUIwR0ExVWREZ1FXQkJUelpCbW5va2sydHpGUi9wZ1dNNWMxeTY0UmV6QVBCZ05WSFJNQkFmOEVCVEFEQVFIL01BNEdBMVVkRHdFQi93UUVBd0lCeGpBS0JnZ3Foa2pPUFFRREFnTklBREJGQWlCQWl3Q1ptcUhvUXdoTjE2NERHR3BnSjlPUTZReS9NSUowRXdpU1pIeEg4QUloQUtPdGFtSGhsZ3BES2l5a1pWeVl3QUNRMkhKYlY3SDh1aGtsZ1cvSGZTeisiXSwidHJ1aWQuYXBwL3VzZXJfbWVzc2FnZS92MSI6IlBsZWFzZSBzaWduIHRoaXMgZG9jdW1lbnQifQ..PYDvx-oEGZmWws-HKYkEsIDgi13ww4D5LgocNpEeGkT7EEpFJfo8Ge31WHWONLuzc85b-2sHaqZDsafKffMflQ"
    private val SIGNATURE_DATE = Instant.parse("2023-09-10T12:57:00Z")

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var clock: FixedDateTimeProvider

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
            val url = URIBuilder(res.location())
            state = url.getParam("state")!!
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
