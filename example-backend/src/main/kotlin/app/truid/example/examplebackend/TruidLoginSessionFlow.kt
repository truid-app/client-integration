package app.truid.example.examplebackend

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
class TruidLoginSessionFlow(
    @Value("\${oauth2.clientId}")
    val clientId: String,

    @Value("\${oauth2.clientSecret}")
    val clientSecret: String,

    @Value("\${oauth2.redirectUri.login}")
    val redirectUri: String,

    @Value("\${oauth2.truid.login-endpoint}")
    val truidLoginEndpoint: String,

    @Value("\${oauth2.truid.token-endpoint}")
    val truidTokenEndpoint: String,

    @Value("\${oauth2.truid.presentation-endpoint}")
    val truidPresentationEndpoint: String,

    @Value("\${web.login.success}")
    val webSuccess: URI,

    @Value("\${web.login.failure}")
    val webFailure: URI,

    val webClient: WebClient
) {
    private val refreshMutex = Mutex()

    @GetMapping("/truid/v1/login-session")
    suspend fun loginSession(
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
        exchange: ServerWebExchange
    ) {
        val session = exchange.session.awaitSingle()
        // Clear session from previous runs
        clearUserSession(session)

        val truidLoginUrl = URIBuilder(truidLoginEndpoint)
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "truid.app/data-point/email")
            .addParameter("redirect_uri", redirectUri)
            .addParameter("state", createOauth2State(session))
            .addParameter("code_challenge", createOauth2CodeChallenge(session))
            .addParameter("code_challenge_method", "S256")
            .build()

        exchange.response.headers.add(HttpHeaders.LOCATION, truidLoginUrl.toString())
        if (xRequestedWith == "XMLHttpRequest") {
            exchange.response.statusCode = HttpStatus.ACCEPTED
        } else {
            exchange.response.statusCode = HttpStatus.FOUND
        }
    }

    @GetMapping("/truid/v1/complete-login")
    suspend fun completeLogin(
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

                // Exchange code for access token and refresh token
                val tokenResponse = webClient.post()
                    .uri(URIBuilder(truidTokenEndpoint).build())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(fromFormData(body))
                    .retrieve()
                    .awaitBody<TokenResponse>()

                // Get subject (sub) and print user email from Truid
                val getPresentationUri = URIBuilder(truidPresentationEndpoint)
                    .addParameter("claims", "truid.app/claim/email/v1")
                    .build()

                val presentation = webClient
                    .get()
                    .uri(getPresentationUri)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenResponse.accessToken}")
                    .retrieve()
                    .awaitBody<PresentationResponse>()

                println(presentation)

                // Store tokens and user-info in session
                // See getActiveUserAccessToken() for example how to validate and refreshing access token
                updateUserSession(tokenResponse, session, presentation)
            } catch (e: WebClientResponseException.Forbidden) {
                throw Forbidden("access_denied", e.message)
            }
        }

        if (exchange.request.headers.accept.contains(MediaType.TEXT_HTML)) {
            // Redirect to success page in the webapp flow
            exchange.response.headers.location = webSuccess
            exchange.response.statusCode = HttpStatus.FOUND
            return null
        } else {
            // Return a 200 response in case of an AJAX request
            exchange.response.statusCode = HttpStatus.OK
            return null
        }
    }

    @GetMapping(
        path = ["/api/user-info"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun getUserInfo(
        exchange: ServerWebExchange
    ): PresentationResponse? {
        val session = exchange.session.awaitSingle()
        // Make sure user is authenticated
        validateUserSession(session)
        // Return user-info from web-session
        return session.attributes["user-info"] as PresentationResponse?
    }

    @PostMapping("/api/perform-action")
    suspend fun performAction(
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
        exchange: ServerWebExchange
    ) {
        // Example (dummy) action endpoint that requires the user to be authenticated.
        val session = exchange.session.awaitSingle()

        // Make sure user is authenticated
        validateUserSession(session)

        // Make some action on behalf of the user
    }

    private suspend fun validateUserSession(session: WebSession) {
        val token = session.attributes["oauth2-user-access-token"] as String?
        val tokenExpires = session.attributes["oauth2-user-access-token-expires"] as Long?

        if (token == null || tokenExpires == null) {
            throw Unauthorized("authentication_required", "No active Login session")
        }

        if (System.currentTimeMillis() > tokenExpires) {
            refreshToken(session)
        }
    }

    private suspend fun refreshToken(session: WebSession): TokenResponse {
        // Synchronized, two refreshes with same refresh token
        // invalidates all access tokens and refresh tokens in accordance to
        // https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-15#section-4.12.2
        refreshMutex.withLock {
            val refreshToken = getRefreshToken(session) ?: throw Forbidden("access_denied", "No refresh_token found")

            val body = LinkedMultiValueMap<String, String>()
            body.add("grant_type", "refresh_token")
            body.add("refresh_token", refreshToken)
            body.add("client_id", clientId)
            body.add("client_secret", clientSecret)

            val refreshedTokenResponse = webClient.post()
                .uri(URIBuilder(truidTokenEndpoint).build())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(fromFormData(body))
                .retrieve()
                .awaitBody<TokenResponse>()

            updateUserSession(refreshedTokenResponse, session)
            return refreshedTokenResponse
        }
    }

    @ExceptionHandler(Forbidden::class)
    fun handleForbidden(
        e: Forbidden,
        exchange: ServerWebExchange
    ): Map<String, String>? {
        println("Handle forbidden: ${e.error}: ${e.message}")
        if (exchange.request.headers.accept.contains(MediaType.TEXT_HTML)) {
            // Redirect to error page in the webapp flow
            exchange.response.headers.location =
                URIBuilder(webFailure).addParameter("error", e.error).build()
            exchange.response.statusCode = HttpStatus.FOUND

            return null
        } else {
            // Return a 403 response in case of an AJAX request
            exchange.response.statusCode = HttpStatus.FORBIDDEN

            return mapOf(
                "error" to e.error
            )
        }
    }

    @ExceptionHandler(Unauthorized::class)
    fun handleUnauthorized(
        e: Unauthorized,
        exchange: ServerWebExchange
    ): Map<String, String>? {
        if (exchange.request.headers.accept.contains(MediaType.TEXT_HTML)) {
            // Redirect to error page in the webapp flow
            exchange.response.headers.location =
                URIBuilder(webFailure).addParameter("error", e.error).build()
            exchange.response.statusCode = HttpStatus.FOUND

            return null
        } else {
            // Return a 401 response in case of an AJAX request
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED

            return mapOf(
                "error" to e.error
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

    private fun clearUserSession(session: WebSession) {
        session.attributes.remove("oauth2-user-access-token")
        session.attributes.remove("oauth2-user-access-token-expires")
        session.attributes.remove("oauth2-user-refresh-token")
        session.attributes.remove("user-info")
    }

    private fun getRefreshToken(session: WebSession): String? {
        return session.attributes["oauth2-user-refresh-token"] as String?
    }

    private fun updateUserSession(
        tokenResponse: TokenResponse,
        session: WebSession,
        userInfo: PresentationResponse? = null
    ) {
        // We store the user access token in the web-session.
        // As long as there is an active access token in the web-session, the user is considered logged in.
        // When the access token has expired, a new access token must be exchanged with the refresh token, to verify
        // that the user still has a valid Truid login-session.
        // The refresh token TTL will control how long idle time is allowed
        session.attributes["oauth2-user-access-token"] = tokenResponse.accessToken
        session.attributes["oauth2-user-access-token-expires"] =
            System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
        session.attributes["oauth2-user-refresh-token"] = tokenResponse.refreshToken
        if (userInfo != null) {
            session.attributes["user-info"] = userInfo
        }
        println(
            "User session ${if (userInfo != null) "created" else "refreshed"}, access-token expires: ${
            toTimestampString(
                session.attributes["oauth2-user-access-token-expires"] as Long
            )
            }"
        )
    }

    private fun toTimestampString(timeMillis: Long): String? {
        val date = Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
}
