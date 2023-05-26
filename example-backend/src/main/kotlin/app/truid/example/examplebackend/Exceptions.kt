package app.truid.example.examplebackend

class Forbidden(val error: String, message: String?) : RuntimeException(message)
class Unauthorized(val error: String, message: String?) : RuntimeException(message)
