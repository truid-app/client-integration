package app.truid.example.examplebackend

import org.springframework.web.server.WebSession

fun createOauth2State(session: WebSession): String {
    return session.attributes.compute("oauth2-state") { _, _ ->
        // Use state parameter to prevent CSRF,
        // according to https://www.rfc-editor.org/rfc/rfc6749#section-10.12
        base64url(random(20))
    } as String
}

fun verifyOauth2State(session: WebSession, state: String?): Boolean {
    val savedState = session.attributes.remove("oauth2-state") as String?
    return savedState != null && state != null && state == savedState
}

fun createOauth2CodeChallenge(session: WebSession): String {
    val codeVerifier = session.attributes.compute("oauth2-code-verifier") { _, _ ->
        // Create code verifier,
        // according to https://www.rfc-editor.org/rfc/rfc7636#section-4.1
        base64url(random(32))
    } as String

    // Create code challenge,
    // according to https://www.rfc-editor.org/rfc/rfc7636#section-4.2
    return base64url(sha256(codeVerifier))
}

fun getOauth2CodeVerifier(session: WebSession): String? {
    return session.attributes["oauth2-code-verifier"] as String?
}
