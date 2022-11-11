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
import java.util.Base64

@RestController
class TruIDSignupFlow(
    @Value("\${oauth2.clientId}")
    val clientId: String,

    @Value("\${oauth2.truid.domain}")
    val truidDomain: String,

    @Value("\${app.domain}")
    val publicDomain: String,
) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private val base64 = Base64.getUrlEncoder().withoutPadding()

    @GetMapping("/truid/v1/confirm-signup")
    suspend fun confirmSignup(
        @RequestHeader("X-Requested-With") xRequestedWith: String?,
        exchange: ServerWebExchange,
    ) {
        val state = getOauth2State(exchange.session.awaitSingle())
        val truidSignupUrl = URIBuilder("$truidDomain/oauth2/v1/authorization/confirm-signup")
            .addParameter("response_type", "code")
            .addParameter("client_id", clientId)
            .addParameter("scope", "veritru.me/claim/email/v1")
            .addParameter("redirect_uri", "$publicDomain/truid/v1/complete-signup")
            .addParameter("state", state)
            .build()

        exchange.response.headers.add(LOCATION, truidSignupUrl.toString())
        if (xRequestedWith == "XMLHttpRequest") {
            exchange.response.statusCode = ACCEPTED
        } else {
            exchange.response.statusCode = FOUND
        }
    }

    private fun getOauth2State(session: WebSession): String {
        session.start()

        // Use state parameter to prevent CSRF,
        // according to https://www.rfc-editor.org/rfc/rfc6749#section-10.12
        return base64.encodeToString(digest.digest(session.id.toByteArray()))
    }
}
