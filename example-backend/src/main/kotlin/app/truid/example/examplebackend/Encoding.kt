package app.truid.example.examplebackend

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private val sha256MessageDigest = MessageDigest.getInstance("SHA-256")
private val base64 = Base64.getUrlEncoder().withoutPadding()
private val secureRandom = SecureRandom()

fun sha256(value: String): ByteArray =
    sha256MessageDigest.digest(value.toByteArray())

fun sha256(value: ByteArray): ByteArray =
    sha256MessageDigest.digest(value)

fun base64url(value: ByteArray): String =
    base64.encodeToString(value)

fun random(n: Int): ByteArray {
    val result = ByteArray(n)
    secureRandom.nextBytes(result)
    return result
}
