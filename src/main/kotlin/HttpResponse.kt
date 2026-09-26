package lk.fincore

class HttpResponse(
    val version: String,
    val status: Int,
    val statusText: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val autoAddContentLength: Boolean = true,
) {
    override fun toString(): String {
        return "$version $status $statusText\r\n" +
                buildString {
                    headers.forEach { (header, values) ->
                        if (header == CONTENT_LENGTH_HEADER && autoAddContentLength) return@forEach
                        values.forEach {
                            append("$header: $it\r\n")
                        }
                    }

                    if (body.isNotEmpty() && !shouldTransferChunked() && autoAddContentLength) {
                        append(CONTENT_LENGTH_HEADER + ": " + body.length + "\r\n")
                    }
                } + "\r\n" + body
    }

    fun toStringByteArray() = toString().toByteArray(Charsets.US_ASCII)

    fun shouldTransferChunked() = headers[TRANSFER_ENCODING_HEADER]?.contains(TRANSFER_ENCODING_VALUE_CHUNKED) == true
}