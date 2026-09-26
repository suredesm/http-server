package lk.fincore

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

const val CONTENT_LENGTH_HEADER = "Content-Length"
const val TRANSFER_ENCODING_HEADER = "Transfer-Encoding"
const val TRANSFER_ENCODING_VALUE_CHUNKED = "chunked"

const val HTTP_VERSION_1_1 = "HTTP/1.1"

class HttpRequest(
    val method: String,
    val path: String,
    val version: String,
    val headers: Map<String, List<String>>,
    var body: String = ""
) {
    companion object {
        fun parse(requestText: String) = try {
            val lines = requestText.split("\r\n")

            // parse request line
            val requestLine = lines.first().split(" ")

            if (requestLine.size != 3) throw ServerException.BadRequest("Miss formatted http request")

            val method = requestLine[0]
            val path = URLDecoder.decode(requestLine[1], StandardCharsets.UTF_8)
            val version = requestLine[2]

            val headers = mutableMapOf<String, List<String>>()

            for(line in lines.drop(1)) {
                val header = line.takeWhile { it != ':' }
                val value = line.substringAfter(": ")
                if (header == TRANSFER_ENCODING_HEADER && value == TRANSFER_ENCODING_VALUE_CHUNKED) {
                    throw ServerException.NotImplemented("Feature not yet implemented")
                }
                headers[header] = (headers[header] ?: emptyList()) + value
            }

            HttpRequest(
                method = method,
                path = path,
                version = version,
                headers = headers,
            )
        } catch (e: Exception) {
            throw e as? ServerException ?: ServerException.BadRequest()
        }
    }

    val contentLength: Int get() {
        val values = headers[CONTENT_LENGTH_HEADER]
        return values?.firstOrNull()
            ?.toIntOrNull() ?: 0
    }

    override fun toString(): String {
        return "Method -> $method\n" +
                "Path -> $path\n" +
                "Protocol -> $version\n" +
                buildString {
                    headers.forEach { (header, values) ->
                        values.forEach {
                            append("$header -> $it\n")
                        }
                    }
                } + "\nbody -> " +
                body
    }
}