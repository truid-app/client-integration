package app.truid.example.examplebackend

import kotlinx.coroutines.reactor.awaitSingle
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.LOCATION
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.BodyInserters.fromFormData
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebSession
import java.net.URI
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

class Forbidden(val error: String, message: String?): RuntimeException(message)

@RestController
class TruidSignupFlow(
    @Value("\${oauth2.clientId}")
    val clientId: String,

    @Value("\${oauth2.clientSecret}")
    val clientSecret: String,

    @Value("\${oauth2.truid.signup-endpoint}")
    val truidSignupEndpoint: String,

    @Value("\${oauth2.truid.token-endpoint}")
    val truidTokenEndpoint: String,

    @Value("\${app.domain}")
    val publicDomain: String,

    @Value("\${web.success}")
    val webSuccess: URI,

    @Value("\${web.failure}")
    val webFailure: URI,

    val webClient: WebClient,
) {
    @GetMapping("/truid/v1/confirm-signup")
    suspend fun confirmSignup(
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
        exchange: ServerWebExchange,
    ) {
        val session = exchange.session.awaitSingle()

        val truidSignupUrl = URIBuilder(truidSignupEndpoint)
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "veritru.me/claim/email/v1")
            .addParameter("redirect_uri", "$publicDomain/truid/v1/complete-signup")
            .addParameter("state", createOauth2State(session))
            .addParameter("code_challenge", createOauth2CodeChallenge(session))
            .addParameter("code_challenge_method", "S256")
            .build()

        exchange.response.headers.add(LOCATION, truidSignupUrl.toString())
        if (xRequestedWith == "XMLHttpRequest") {
            exchange.response.statusCode = ACCEPTED
        } else {
            exchange.response.statusCode = FOUND
        }
    }

    @GetMapping("/truid/v1/complete-signup")
    suspend fun completeSignup(
        @RequestParam("code") code: String?,
        @RequestParam("state") state: String?,
        @RequestParam("error") error: String?,
        exchange: ServerWebExchange,
    ): Void? {
        val session = exchange.session.awaitSingle()

        if (error != null) {
            throw Forbidden(error, "There was an authorization error")
        } else if (!verifyOauth2State(session, state)) {
            throw Forbidden("access_denied", "State does not match the expected value")
        } else {
            try {
                val body = LinkedMultiValueMap<String, String>()
                body.add("grant_type", "authorization_code")
                body.add("code", code)
                body.add("redirect_uri", "$publicDomain/truid/v1/complete-signup")
                body.add("client_id", clientId)
                body.add("client_secret", clientSecret)
                body.add("code_verifier", getOauth2CodeVerifier(session))

                val tokenResponse = webClient.post()
                    .uri(URIBuilder(truidTokenEndpoint).build())
                    .contentType(APPLICATION_FORM_URLENCODED)
                    .accept(APPLICATION_JSON)
                    .body(fromFormData(body))
                    .retrieve()
                    .awaitBody<Map<String, String>>()

                // TODO: Store tokenResponse["refresh_token"]
            } catch (e: WebClientResponseException.Forbidden) {
                throw Forbidden("access_denied", e.message)
            }
        }

        if (exchange.request.headers.accept.contains(MediaType.TEXT_HTML)) {
            // Redirect to success page in the webapp flow
            exchange.response.headers.location = webSuccess
            exchange.response.statusCode = FOUND
            return null
        } else {
            // Return a 200 response in case of an AJAX request
            exchange.response.statusCode = OK
            return null
        }
    }

    @ExceptionHandler(Forbidden::class)
    fun handleForbidden(
        e: Forbidden,
        exchange: ServerWebExchange,
    ): Map<String, String>? {
        if (exchange.request.headers.accept.contains(MediaType.TEXT_HTML)) {
            // Redirect to error page in the webapp flow
            exchange.response.headers.location =
                URIBuilder(webFailure).addParameter("error", e.error).build()
            exchange.response.statusCode = FOUND

            return null
        } else {
            // Return a 403 response in case of an AJAX request
            exchange.response.statusCode = FORBIDDEN

            return mapOf(
                "error" to e.error,
            )
        }
    }

    private fun createOauth2State(session: WebSession): String {
        return session.attributes.compute("oauth2-state") { _, _ ->
            // Use state parameter to prevent CSRF,
            // according to https://www.rfc-editor.org/rfc/rfc6749#section-10.12
            base64url(random(20))
        } as String
    }

    private fun verifyOauth2State(session: WebSession, state: String?): Boolean {
        val savedState = session.attributes.remove("oauth2-state") as String?
        return savedState != null && state != null && state == savedState
    }

    private fun createOauth2CodeChallenge(session: WebSession): String {
        val codeVerifier = session.attributes.compute("oauth2-code-verifier") { _, _ ->
            // Create code verifier,
            // according to https://www.rfc-editor.org/rfc/rfc7636#section-4.1
            base64url(random(32))
        } as String

        // Create code challenge,
        // according to https://www.rfc-editor.org/rfc/rfc7636#section-4.2
        return base64url(sha256(codeVerifier))
    }

    private fun getOauth2CodeVerifier(session: WebSession): String? {
        return session.attributes["oauth2-code-verifier"] as String?
    }
}
