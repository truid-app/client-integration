package app.truid.example.examplebackend

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.session.CookieWebSessionIdResolver
import org.springframework.web.server.session.WebSessionIdResolver
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.TcpClient
import java.net.URL

@SpringBootApplication
class ExampleBackendApplication {
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
        return WebClient
            .builder()
            // create a new connection for each request to avoid connection reset by peer when docker compose container
            // is restarted
            .clientConnector(ReactorClientHttpConnector(HttpClient.from(TcpClient.newConnection())))
            .build()
    }
}

fun main(args: Array<String>) {
    runApplication<ExampleBackendApplication>(*args)
}
