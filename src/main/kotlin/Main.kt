package lk.fincore

import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.round

private const val UPTO_ROUTE_API_LENGTH = 4

val rootPath = Path.of(
    System.getProperty("user.home"),
    "Desktop",
    "ServerRoot"
).let {
    Files.createDirectories(it)
}!!

fun main() {
    TCPServer(8080)
}

private const val CHUNKING_THRESHOLD = 1024 * 1024
private const val CHUNK_SIZE = 1024 * 1024

class TCPServer {
    constructor(port: Int) {
        try {
            val serverSocket = ServerSocket(port)
            val repo = UserRepository()
            println("Server started")
            println("Waiting for the client ...")
            var socket: Socket = serverSocket.accept()
            println("Client accepted!")

            while (true) {
                if (socket.isClosed) {
                    println("Waiting for the client ...")
                    socket = serverSocket.accept()
                    println("Client accepted!")
                }
                println("Listening for request ...")
                val shouldClose = listenToClient(socket, repo)
                println("Request completed")
                if (shouldClose) {
                    println("Connection closed")
                    socket.close()
                }
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun listenToClient(socket: Socket, repo: UserRepository): Boolean {
        val inputStream = socket.getInputStream()
        var data: Int
        var requestText = ""
        var endOfStream = false
        var fileInputStream: InputStream? = null

        while (true) {
            data = inputStream.read()
            if (data == -1) {
                endOfStream = true
                break
            }
            requestText += data.toChar()
            if (requestText.endsWith("\r\n\r\n")) {
                requestText = requestText.dropLast(4)
                break
            }
        }

        var response: HttpResponse
        var fileSize = 0L

        try {
            val httpRequest = HttpRequest.parse(requestText)
            val body = buildString {
                @Suppress("UNUSED_PARAMETER")
                for (i in 0 ..< httpRequest.contentLength) {
                    data = inputStream.read()
                    if (data == -1) break
                    append(data.toChar())
                }
            }
            httpRequest.body = body

            if (httpRequest.headers["Connection"]?.contains("close") == true) {
                endOfStream = true
            }

            if (httpRequest.path.startsWith("/api")) {
                if (httpRequest.method == "GET" && httpRequest.path.substring(UPTO_ROUTE_API_LENGTH) == "/users") {
                    val users = repo.getAllUsers().let {
                        Json.encodeToString(it)
                    }

                    response = HttpResponse(
                        HTTP_VERSION_1_1,
                        200,

                        "OK",
                        mapOf(
                            "Agent" to listOf("my-server!"),
                            "Content-Type" to listOf("application/json"),
                        ),
                        users
                    )
                } else if (
                    httpRequest.method == "GET" &&
                    httpRequest.path.substring(UPTO_ROUTE_API_LENGTH).startsWith("/users/") &&
                    httpRequest.path.substring(UPTO_ROUTE_API_LENGTH + 7).toIntOrNull() != null
                ) {
                    val userId = httpRequest.path.substring(UPTO_ROUTE_API_LENGTH + 7).toInt()
                    val userResult = repo.getUser(userId)
                    val user = userResult.getOrNull()?.let {
                        Json.encodeToString(it)
                    } ?: throw ServerException.NotFound("Retrieve failed. User with id=$userId not found")

                    response = HttpResponse(
                        HTTP_VERSION_1_1,
                        200,

                        "OK",
                        mapOf(
                            "Agent" to listOf("my-server!"),
                            "Content-Type" to listOf("application/json"),
                        ),
                        user
                    )
                } else if (
                    httpRequest.method == "POST" &&
                    httpRequest.path.substring(UPTO_ROUTE_API_LENGTH) == "/users"
                ) {
                    val content = httpRequest.body
                    val user = try {
                        Json.decodeFromString<User>(content)
                    } catch (_: IllegalArgumentException) {
                        throw ServerException.BadRequest("Malformed User format")
                    }

                    val result = repo.insertUser(user).getOrNull()

                    if (result != null) {
                        response = HttpResponse(
                            HTTP_VERSION_1_1,
                            201,

                            "OK CREATED",
                            mapOf(
                                "Agent" to listOf("my-server!")
                            ),
                            "User created with id=$result"
                        )
                    } else {
                        throw ServerException.Conflict("Insertion failed. User with id=${user.id} already exists")
                    }
                } else if (
                    httpRequest.method == "PUT" &&
                    httpRequest.path.substring(UPTO_ROUTE_API_LENGTH) == "/users"
                ) {
                    val content = httpRequest.body
                    val user = try {
                        Json.decodeFromString<User>(content)
                    } catch (_: IllegalArgumentException) {
                        throw ServerException.BadRequest("Malformed User format")
                    }

                    val result = repo.updateUser(user).getOrNull()

                    if (result != null) {
                        response = HttpResponse(
                            HTTP_VERSION_1_1,
                            200,

                            "OK",
                            mapOf(
                                "Agent" to listOf("my-server!")
                            ),
                            "User update with id=$result"
                        )
                    } else {
                        throw ServerException.NotFound("Update failed. User with id=${user.id} doesn't exists")
                    }
                } else if (
                    httpRequest.method == "DELETE" &&
                    httpRequest.path.substring(UPTO_ROUTE_API_LENGTH).startsWith("/users/") &&
                    httpRequest.path.substring(UPTO_ROUTE_API_LENGTH + 7).toIntOrNull() != null
                ) {
                    val userId = httpRequest.path.substring(UPTO_ROUTE_API_LENGTH + 7).toInt()
                    val userResult = repo.deleteUser(userId)
                    val result = userResult.getOrNull()

                    if (result != null) {
                        response = HttpResponse(
                            HTTP_VERSION_1_1,
                            200,

                            "OK",
                            mapOf(
                                "Agent" to listOf("my-server!"),
                            ),
                            "User with id=$userId is deleted"
                        )
                    } else throw ServerException.NotFound("Deletion failed. User with id=$userId doesn't exists")
                } else throw ServerException.NotFound("Not Found")
            } else if (httpRequest.method == "GET") {

                val pathRequested = rootPath.resolve(httpRequest.path.substring(1)).normalize()

                if (!pathRequested.startsWith(rootPath))
                    throw ServerException.Unauthorized("File trying to access is unauthorized")

                val fileToBeRetrieved = if (Files.isDirectory(pathRequested)) {
                    val indexFileHtml = rootPath.resolve("index.html")
                    val indexFileHtm = rootPath.resolve("index.htm")
                    when {
                        Files.exists(indexFileHtml) -> indexFileHtml
                        Files.exists(indexFileHtm) -> indexFileHtm
                        else -> null
                    }
                } else {
                    pathRequested
                }

                if (fileToBeRetrieved != null && Files.exists(fileToBeRetrieved)) {
                    val size = Files.size(fileToBeRetrieved)
                    fileSize = size

                    if (size < CHUNKING_THRESHOLD) {
                        fileInputStream = Files.newInputStream(fileToBeRetrieved)

                        response = HttpResponse(
                            HTTP_VERSION_1_1,
                            200,

                            "OK",
                            mapOf(
                                "Agent" to listOf("my-server!"),
                                "Content-Type" to listOf(Files.probeContentType(fileToBeRetrieved)),
                                "Content-Length" to listOf("" + size),
                                "Connection" to listOf("close")
                            ),
                            "",
                            autoAddContentLength = false
                        )
                    } else {
                        response = HttpResponse(
                            HTTP_VERSION_1_1,
                            200,
                            "OK",
                            mapOf(
                                "Agent" to listOf("my-server!"),
                                "Content-Type" to listOf(Files.probeContentType(fileToBeRetrieved)),
                                TRANSFER_ENCODING_HEADER to listOf(TRANSFER_ENCODING_VALUE_CHUNKED),
                            ),

                            ""
                        )

                        fileInputStream = Files.newInputStream(fileToBeRetrieved)
                    }
                } else throw ServerException.NotFound("File not found")
            } else throw ServerException.NotFound("Not Found")
        } catch (e: Exception) {
            response = if (e is ServerException) e.response
            else HttpResponse(
                HTTP_VERSION_1_1,
                500,

                "INTERNAL SERVER ERROR",
                mapOf(
                    "Agent" to listOf("my-server!")
                ),
                "Something went wrong in server"
            )
        }

        try {
            val outputStream = socket.getOutputStream()

            if (fileInputStream != null) {

                if (response.shouldTransferChunked()) {

                    outputStream.write(response.toStringByteArray())
                    outputStream.flush()
                    val buffer = ByteArray(CHUNK_SIZE)

                    var progressBytes = 0
                    print("\r[" + "#".repeat(0) + ".".repeat(10) + "]")

                    while (true) {
                        val bytesRead = fileInputStream.read(buffer)
                        progressBytes += bytesRead
                        if (bytesRead == -1) break
                        val chunkHeader = (bytesRead.toString(16) + "\r\n").toByteArray(Charsets.US_ASCII)
                        val endOfChunk = "\r\n".toByteArray(Charsets.US_ASCII)
                        outputStream.write(chunkHeader)
                        outputStream.write(buffer.copyOfRange(0, bytesRead))
                        outputStream.write(endOfChunk)
                        outputStream.flush()

                        val progress = round(progressBytes.toFloat() / fileSize * 10).toInt()
                        print("\r[" + "#".repeat(progress) + ".".repeat(10 - progress) + "]")
                    }
                    println("\nCompleted!")
                    val terminator = "0\r\n\r\n".toByteArray(Charsets.US_ASCII)
                    outputStream.write(terminator)
                    outputStream.flush()
                    fileInputStream.close()
                } else {
                    val content = fileInputStream.readAllBytes()
                    outputStream.write(response.toStringByteArray())
                    outputStream.write(content)
                    outputStream.flush()
                }
            } else {
                outputStream.write(response.toStringByteArray())
                outputStream.flush()
            }
        } catch(_: Exception) {
            endOfStream = true
        }

        return endOfStream || requestText == ""
    }
}