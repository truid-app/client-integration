package app.truid.example.examplebackend

import kotlinx.coroutines.reactor.awaitSingle
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.LOCATION
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.FOUND
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebSession
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private val sha256MessageDigest = MessageDigest.getInstance("SHA-256")
private val base64 = Base64.getUrlEncoder().withoutPadding()
private val secureRandom = SecureRandom()

private fun sha256(value: String): ByteArray =
    sha256MessageDigest.digest(value.toByteArray())

private fun base64url(value: ByteArray): String =
    base64.encodeToString(value)

private fun random(n: Int): ByteArray {
    val result = ByteArray(n)
    secureRandom.nextBytes(result)
    return result
}

@RestController
class TruIDSignupFlow(
    @Value("\${oauth2.clientId}")
    val clientId: String,

    @Value("\${oauth2.truid.domain}")
    val truidDomain: String,

    @Value("\${app.domain}")
    val publicDomain: String,
) {
    @GetMapping("/truid/v1/confirm-signup")
    suspend fun confirmSignup(
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
        exchange: ServerWebExchange,
    ) {
        val session = exchange.session.awaitSingle()

        val truidSignupUrl = URIBuilder("$truidDomain/oauth2/v1/authorization/confirm-signup")
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "veritru.me/claim/email/v1")
            .addParameter("redirect_uri", "$publicDomain/truid/v1/complete-signup")
            .addParameter("state", getOauth2State(session))
            .addParameter("code_challenge", getOauth2CodeChallenge(session))
            .addParameter("code_challenge_method", "S256")
            .build()

        exchange.response.headers.add(LOCATION, truidSignupUrl.toString())
        if (xRequestedWith == "XMLHttpRequest") {
            exchange.response.statusCode = ACCEPTED
        } else {
            exchange.response.statusCode = FOUND
        }
    }

    private fun getOauth2State(session: WebSession): String {
        return session.attributes.computeIfAbsent("oauth2-state") {
            // Use state parameter to prevent CSRF,
            // according to https://www.rfc-editor.org/rfc/rfc6749#section-10.12
            base64url(random(20))
        } as String
    }

    private fun getOauth2CodeVerifier(session: WebSession): String {
        return session.attributes.computeIfAbsent("oauth2-code-verifier") {
            // Create code verifier according to https://www.rfc-editor.org/rfc/rfc7636#section-4.1
            base64url(random(32))
        } as String
    }

    private fun getOauth2CodeChallenge(session: WebSession): String {
        // Create code challenge according to https://www.rfc-editor.org/rfc/rfc7636#section-4.2
        val codeVerifier = getOauth2CodeVerifier(session)
        return base64url(sha256(codeVerifier))
    }
}
