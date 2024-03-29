package app.truid.example.examplebackend

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.session.CookieWebSessionIdResolver
import org.springframework.web.server.session.WebSessionIdResolver
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.util.Optional

@SpringBootApplication
class Application {
    @Bean
    fun clock(): DateTimeProvider {
        val clock = Clock.systemUTC()
        return DateTimeProvider { Optional.of(Instant.now(clock)) }
    }

    @Bean
    fun webSessionIdResolver(
        @Value("\${app.domain}")
        publicDomain: String
    ): WebSessionIdResolver {
        return CookieWebSessionIdResolver().apply {
            addCookieInitializer {
                // Disable secure cookies if on http or the session cookie will not be saved
                // Note: A production service in Truid cannot use a http redirect, for test only
                if (URL(publicDomain).protocol != "http") {
                    it.secure(true)
                }
                it.sameSite("Lax")
            }
        }
    }

    @Bean
    fun webClient(): WebClient {
        return WebClient.create()
    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
