package app.truid.example.examplebackend

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.apache.http.client.utils.URIBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

fun <T> ResponseEntity<T>.location() = this.headers[HttpHeaders.LOCATION]?.firstOrNull()
fun <T> ResponseEntity<T>.setCookie() = this.headers[HttpHeaders.SET_COOKIE]?.firstOrNull()
fun URIBuilder.getParam(name: String) = this.queryParams.firstOrNull { it.name == name }?.value

fun ResponseDefinitionBuilder.withJsonBody(body: Any): ResponseDefinitionBuilder = this
    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
    .withBody(ObjectMapper().writeValueAsString(body))
