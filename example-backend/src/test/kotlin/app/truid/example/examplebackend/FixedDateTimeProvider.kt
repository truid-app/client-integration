package app.truid.example.examplebackend

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.auditing.DateTimeProvider
import java.time.Instant
import java.time.temporal.TemporalAccessor
import java.util.Optional

@TestConfiguration
class FixedDateTimeProviderConfig {
    @Primary
    @Bean
    fun clock() = FixedDateTimeProvider()
}

class FixedDateTimeProvider : DateTimeProvider {
    private var instant = Instant.now()

    override fun getNow(): Optional<TemporalAccessor> {
        return Optional.of(instant)
    }

    fun setTime(value: Instant) {
        instant = value
    }
}
