package app.truid.example.examplebackend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.session.CookieWebSessionIdResolver
import org.springframework.web.server.session.WebSessionIdResolver

@SpringBootApplication
class ExampleBackendApplication {
    @Bean
    fun webSessionIdResolver(): WebSessionIdResolver {
        return CookieWebSessionIdResolver().apply {
            addCookieInitializer {
                it.secure(true)
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
    runApplication<ExampleBackendApplication>(*args)
}
