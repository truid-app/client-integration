package app.truid.example.examplebackend

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.util.Base64
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.auditing.DateTimeProvider
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
import java.security.GeneralSecurityException
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.time.Duration
import java.time.Instant
import java.util.Date

class InvalidSignature(msg: String, cause: Throwable? = null) : Forbidden("invalid_signature", msg, cause)

@RestController
class TruidSignFlow(
    @Value("\${oauth2.clientId}")
    val clientId: String,

    @Value("\${oauth2.clientSecret}")
    val clientSecret: String,

    @Value("\${oauth2.truid.sign-endpoint}")
    val truidSignEndpoint: String,

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

    @Value("\${trustanchor.truid}")
    val trustAnchors: List<String>,

    val clock: DateTimeProvider,

    val webClient: WebClient
) {
    private val FACTORY = CertificateFactory.getInstance("X509")

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

        val truidSignupUrl = URIBuilder(truidSignEndpoint)
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
            // Return a 202 response in case of an AJAX request
            exchange.response.statusCode = ACCEPTED
        } else {
            // Return a 302 response in case of browser redirect
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

                println("Signature: $signature")

                verifySignature(signature, getDocument())
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
        println("Caught an exception: $e")
        e.printStackTrace()

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

    private fun truidTrustAnchors(): Set<TrustAnchor> {
        return trustAnchors.map {
            TrustAnchor(
                FACTORY.generateCertificate(
                    Base64.from(
                        it.replace("(\n)?-----(BEGIN|END) CERTIFICATE-----(\n)?".toRegex(), "")
                    ).decode().inputStream()
                ) as X509Certificate,
                null
            )
        }.toSet()
    }

    private fun decodeCertificateChain(x509CertChain: List<Base64>): List<X509Certificate> {
        return x509CertChain.map {
            FACTORY.generateCertificate(it.decode().inputStream()) as X509Certificate
        }
    }

    fun validateCertificateChain(chain: List<X509Certificate>, trustAnchors: Set<TrustAnchor>, verifyAt: Date) {
        val validator = CertPathValidator.getInstance("PKIX")

        try {
            val params = PKIXParameters(trustAnchors)
            params.isRevocationEnabled = false // TBD: CRL not published yet
            params.date = verifyAt
            validator.validate(FACTORY.generateCertPath(chain), params)
        } catch (e: GeneralSecurityException) {
            throw InvalidSignature("Invalid certificate chain", e)
        }
    }

    private fun verifySignature(jws: String, payload: ByteArray) {
        val now = Instant.from(clock.now.get())
        val jwt = JWTParser.parse(jws)
        println("jwt.header: ${jwt.header}")

        if (jwt is SignedJWT) {
            val x509CertChain = decodeCertificateChain(jwt.header.x509CertChain)
            println("certificates: $x509CertChain")

            validateCertificateChain(x509CertChain, truidTrustAnchors(), Date.from(now))

            val publicKey = x509CertChain[0].publicKey
            if (publicKey !is ECPublicKey) {
                throw InvalidSignature("Expected EC key")
            }

            if (!jwt.verify(ECDSAVerifier(publicKey, setOf("sigD", "sigT")))) {
                throw InvalidSignature("Invalid signature")
            }

            val sigD = jwt.header.getCustomParam("sigD") as Map<*, *>

            val mId = sigD["mId"] as String
            if (mId != "http://uri.etsi.org/19182/ObjectIdByURIHash") {
                throw InvalidSignature("mId does not match")
            }
            val pars = sigD["pars"] as List<*>
            if (pars[0] != "$publicDomain/documents/Agreement.pdf") {
                throw InvalidSignature("pars does not match")
            }
            val hashM = sigD["hashM"]
            if (hashM != "S256") {
                throw InvalidSignature("hashM does not match")
            }
            val hashV = sigD["hashV"] as List<*>
            if (hashV[0] != base64url(sha256(payload))) {
                throw InvalidSignature("Payload digest does not match")
            }
            val userMessage = jwt.header.getCustomParam("truid.app/user_message/v1") as String
            if (userMessage != "Please sign this document") {
                throw InvalidSignature("User message does not match")
            }

            val sigT = parseSigT(jwt.header.getCustomParam("sigT"))
            if (sigT > now) {
                throw InvalidSignature("sigT is in the future")
            }
            if (sigT < now - Duration.ofHours(1)) {
                throw InvalidSignature("sigT is in the past")
            }
        } else {
            throw InvalidSignature("JWS signature is not signed")
        }
    }

    private fun parseSigT(value: Any): Instant {
        return Instant.parse(value as String)
    }
}
