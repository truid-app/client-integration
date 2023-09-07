package app.truid.example.examplebackend

open class Forbidden(val error: String, message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
class Unauthorized(val error: String, message: String?) : RuntimeException(message)
