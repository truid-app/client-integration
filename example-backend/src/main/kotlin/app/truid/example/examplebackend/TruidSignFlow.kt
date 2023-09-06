package app.truid.example.examplebackend

import kotlinx.coroutines.reactor.awaitSingle
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.http.MediaType.TEXT_HTML
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
import java.net.URI

@RestController
class TruidSignFlow(
    @Value("\${oauth2.clientId}")
    val clientId: String,

    @Value("\${oauth2.clientSecret}")
    val clientSecret: String,

    @Value("\${oauth2.truid.sign-endpoint}")
    val truidSignupEndpoint: String,

    @Value("\${oauth2.truid.token-endpoint}")
    val truidTokenEndpoint: String,

    @Value("\${oauth2.truid.signature-endpoint}")
    val truidSignatureEndpoint: String,

    @Value("\${app.domain}")
    val publicDomain: String,

    @Value("\${web.sign.success}")
    val webSuccess: URI,

    @Value("\${web.sign.failure}")
    val webFailure: URI,

    val webClient: WebClient
) {
    @GetMapping("/documents/Agreement.pdf", produces = [APPLICATION_PDF_VALUE])
    suspend fun document(): ByteArray {
        return getDocument()
    }

    @GetMapping("/truid/v1/sign")
    suspend fun sign(
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
        exchange: ServerWebExchange
    ) {
        val session = exchange.session.awaitSingle()
        val document = getDocument()

        val truidSignupUrl = URIBuilder(truidSignupEndpoint)
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "truid.app/data-point/email")
            .addParameter("redirect_uri", "$publicDomain/truid/v1/complete-sign")
            .addParameter("state", createOauth2State(session))
            .addParameter("code_challenge", createOauth2CodeChallenge(session))
            .addParameter("code_challenge_method", "S256")
            .addParameter("user_message", "Please sign this document")
            .addParameter("data_object_id", "$publicDomain/documents/Agreement.pdf")
            .addParameter("data_object_digest", base64url(sha256(document)))
            .addParameter("data_object_digest_algorithm", "S256")
            .addParameter("data_object_b64", "false")
            .addParameter("data_object_content_type", "application/pdf")
            .addParameter("signature_profile", "aes_jades_baseline_b-b")
            .addParameter("jws_packaging", "detached")
            .addParameter("jws_serialization", "compact")
            .build()

        exchange.response.headers.location = truidSignupUrl
        if (xRequestedWith == "XMLHttpRequest") {
            exchange.response.statusCode = ACCEPTED
        } else {
            exchange.response.statusCode = FOUND
        }
    }

    @GetMapping("/truid/v1/complete-sign")
    suspend fun completeSign(
        @RequestParam("code") code: String?,
        @RequestParam("state") state: String?,
        @RequestParam("error") error: String?,
        exchange: ServerWebExchange
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
                body.add("redirect_uri", "$publicDomain/truid/v1/complete-sign")
                body.add("client_id", clientId)
                body.add("client_secret", clientSecret)
                body.add("code_verifier", getOauth2CodeVerifier(session))

                println("Posting code to: $truidTokenEndpoint")
                // Exchange code for access token and refresh token
                val tokenResponse = webClient.post()
                    .uri(URIBuilder(truidTokenEndpoint).build())
                    .contentType(APPLICATION_FORM_URLENCODED)
                    .accept(APPLICATION_JSON)
                    .body(fromFormData(body))
                    .retrieve()
                    .awaitBody<TokenResponse>()

                println("Fetching signature: $truidSignatureEndpoint")
                // Get signature
                val signature = webClient
                    .get()
                    .uri(truidSignatureEndpoint)
                    .accept(MediaType("application", "jose"))
                    .headers { it.setBearerAuth(tokenResponse.accessToken) }
                    .retrieve()
                    .awaitBody<String>()

                println(signature)

                // TODO: verify signature
            } catch (e: WebClientResponseException.Forbidden) {
                throw Forbidden("access_denied", e.message)
            }
        }

        if (exchange.request.headers.accept.contains(TEXT_HTML)) {
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
        exchange: ServerWebExchange
    ): Map<String, String>? {
        if (exchange.request.headers.accept.contains(TEXT_HTML)) {
            // Redirect to error page in the webapp flow
            exchange.response.headers.location =
                URIBuilder(webFailure).addParameter("error", e.error).build()
            exchange.response.statusCode = FOUND

            return null
        } else {
            // Return a 403 response in case of an AJAX request
            exchange.response.statusCode = FORBIDDEN

            return mapOf(
                "error" to e.error
            )
        }
    }

    private fun getDocument(): ByteArray {
        val document = javaClass.getResourceAsStream("/documents/Agreement.pdf")
        if (document == null) {
            throw RuntimeException("document not found")
        } else {
            return document.readAllBytes()
        }
    }
}
