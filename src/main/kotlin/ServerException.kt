package lk.fincore

sealed class ServerException(
    message: String
): RuntimeException(message) {
    abstract val serverMessage: String
    abstract val response: HttpResponse

    class BadRequest(override val serverMessage: String = "") : ServerException("Bad HTTP request") {
        override val response = HttpResponse(
            version = HTTP_VERSION_1_1,
            status = 400,
            statusText = "BAD REQUEST",
            headers = mapOf(
                "Agent" to listOf("my-server!")
            ),
            body = serverMessage
        )
    }

    class Unauthorized(override val serverMessage: String = "") : ServerException("Unauthorized") {
        override val response = HttpResponse(
            version = HTTP_VERSION_1_1,
            status = 401,
            statusText = "UNAUTHORIZED",
            headers = mapOf(
                "Agent" to listOf("my-server!")
            ),
            body = serverMessage
        )
    }

    class NotFound(override val serverMessage: String = "") : ServerException("Not found!") {
        override val response = HttpResponse(
            version = HTTP_VERSION_1_1,
            status = 404,
            statusText = "Not Found",
            headers = mapOf(
                "Agent" to listOf("my-server!")
            ),
            body = serverMessage
        )
    }

    class Conflict(override val serverMessage: String = "") : ServerException("Conflict") {
        override val response = HttpResponse(
            version = HTTP_VERSION_1_1,
            status = 409,
            statusText = "CONFLICT",
            headers = mapOf(
                "Agent" to listOf("my-server!")
            ),
            body = serverMessage
        )
    }

    class NotImplemented(override val serverMessage: String = "") : ServerException("Not implemented!") {
        override val response = HttpResponse(
            version = HTTP_VERSION_1_1,
            status = 501,
            statusText = "NOT IMPLEMENTED",
            headers = mapOf(
                "Agent" to listOf("my-server!")
            ),
            body = serverMessage
        )
    }
}