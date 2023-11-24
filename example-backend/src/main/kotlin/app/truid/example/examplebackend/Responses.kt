package app.truid.example.examplebackend

import com.fasterxml.jackson.annotation.JsonProperty

data class TokenResponse(
    @JsonProperty("refresh_token")
    val refreshToken: String,
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Long,
    @JsonProperty("token_type")
    val tokenType: String,
    val scope: String
)
data class ParResponse(
    @JsonProperty("request_uri")
    val requestUri: String,
    @JsonProperty("expires_in")
    val expiresIn: Long
)
data class PresentationResponse(
    val sub: String,
    val claims: List<PresentationResponseClaims>
)
data class PresentationResponseClaims(
    val type: String,
    val value: String
)
