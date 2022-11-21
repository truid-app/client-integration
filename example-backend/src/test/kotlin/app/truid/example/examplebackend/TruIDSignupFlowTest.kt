package app.truid.example.examplebackend

import org.apache.http.client.utils.URIBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.RequestEntity.get
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.net.HttpCookie


fun <T> ResponseEntity<T>.location() = this.headers[HttpHeaders.LOCATION]?.firstOrNull()
fun <T> ResponseEntity<T>.setCookie() = this.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()
fun URIBuilder.getParam(name: String) = this.queryParams.firstOrNull { it.name == name }?.value

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class TruIDSignupFlowTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `It should redirect to TruID authorization endpoint`() {
        val res = rest.exchange(
            get("/truid/v1/confirm-signup")
                .build(),
            Void::class.java,
        )
        assertEquals(302, res.statusCodeValue)

        val url = URIBuilder(res.location())
        assertEquals("https", url.scheme)
        assertEquals("api.truid.app", url.host)
        assertEquals("/oauth2/v1/authorization/confirm-signup", url.path)
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
        assertEquals("/oauth2/v1/authorization/confirm-signup", url.path)
    }

    @Test
    fun `It should return a secure cookie containing the session ID`() {
        val res = rest.exchange(
            get("/truid/v1/confirm-signup")
                .build(),
            Void::class.java,
        )
        assertEquals(302, res.statusCodeValue)
        val cookie = HttpCookie.parse(res.setCookie()).single()
        assertEquals(true, cookie.secure)
        assertEquals(true, cookie.isHttpOnly)
    }
}
