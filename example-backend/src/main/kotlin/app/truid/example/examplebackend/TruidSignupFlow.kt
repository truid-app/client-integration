package app.truid.example.examplebackend

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
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
class TruidSignupFlow(
    @Value("\${oauth2.clientId}")
    val clientId: String,

    @Value("\${oauth2.clientSecret}")
    val clientSecret: String,

    @Value("\${oauth2.redirectUri.signup}")
    val redirectUri: String,

    @Value("\${oauth2.truid.signup-endpoint}")
    val truidSignupEndpoint: String,

    @Value("\${oauth2.truid.token-endpoint}")
    val truidTokenEndpoint: String,

    @Value("\${oauth2.truid.presentation-endpoint}")
    val truidPresentationEndpoint: String,

    @Value("\${web.signup.success}")
    val webSuccess: URI,

    @Value("\${web.signup.failure}")
    val webFailure: URI,

    val webClient: WebClient
) {
    // This variable acts as our persistence in this example
    private var _persistedRefreshToken: String? = null
    private val refreshMutex = Mutex()

    @GetMapping("/truid/v1/confirm-signup")
    suspend fun confirmSignup(
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
        exchange: ServerWebExchange
    ) {
        val session = exchange.session.awaitSingle()
        // Clear data from previous runs
        clearPersistence()

        val truidSignupUrl = URIBuilder(truidSignupEndpoint)
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "truid.app/data-point/email")
            .addParameter("redirect_uri", redirectUri)
            .addParameter("state", createOauth2State(session))
            .addParameter("code_challenge", createOauth2CodeChallenge(session))
            .addParameter("code_challenge_method", "S256")
            .build()

        exchange.response.headers.location = truidSignupUrl
        if (xRequestedWith == "XMLHttpRequest") {
            // Return a 202 response in case of an AJAX request
            exchange.response.statusCode = ACCEPTED
        } else {
            // Return a 302 response in case of browser redirect
            exchange.response.statusCode = FOUND
        }
    }

    @GetMapping("/truid/v1/complete-signup")
    suspend fun completeSignup(
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
                body.add("redirect_uri", redirectUri)
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

                println("Fetching presentation: $truidPresentationEndpoint")
                // Get and print user email from Truid
                val getPresentationUri = URIBuilder(truidPresentationEndpoint)
                    .addParameter("claims", "truid.app/claim/email/v1")
                    .build()

                val presentation = webClient
                    .get()
                    .uri(getPresentationUri)
                    .accept(APPLICATION_JSON)
                    .headers { it.setBearerAuth(tokenResponse.accessToken) }
                    .retrieve()
                    .awaitBody<PresentationResponse>()

                println(presentation)

                // Persist token, so it can be accessed via GET "/truid/v1/presentation"
                // See getAccessToken for an example of refreshing access token
                persist(tokenResponse)
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

    @GetMapping(
        path = ["/truid/v1/presentation"],
        produces = [APPLICATION_JSON_VALUE]
    )
    suspend fun getPresentation(): PresentationResponse {
        val accessToken = refreshToken()

        val getPresentationUri = URIBuilder(truidPresentationEndpoint)
            .addParameter("claims", "truid.app/claim/email/v1")
            .build()

        return webClient
            .get()
            .uri(getPresentationUri)
            .accept(APPLICATION_JSON)
            .header(AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .awaitBody()
    }

    private suspend fun refreshToken(): String {
        // Synchronized, two refreshes with same refresh token
        // invalidates all access tokens and refresh tokens in accordance to
        // https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-15#section-4.12.2
        refreshMutex.withLock {
            val refreshToken = getPersistedToken() ?: throw Forbidden("access_denied", "No refresh_token found")

            val body = LinkedMultiValueMap<String, String>()
            body.add("grant_type", "refresh_token")
            body.add("refresh_token", refreshToken)
            body.add("client_id", clientId)
            body.add("client_secret", clientSecret)

            val refreshedTokenResponse = webClient.post()
                .uri(URIBuilder(truidTokenEndpoint).build())
                .contentType(APPLICATION_FORM_URLENCODED)
                .accept(APPLICATION_JSON)
                .body(fromFormData(body))
                .retrieve()
                .awaitBody<TokenResponse>()

            persist(refreshedTokenResponse)
            return refreshedTokenResponse.accessToken
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

    private fun clearPersistence() {
        _persistedRefreshToken = null
    }

    private fun persist(tokenResponse: TokenResponse) {
        _persistedRefreshToken = tokenResponse.refreshToken
    }
    private fun getPersistedToken(): String? {
        return _persistedRefreshToken
    }
}
