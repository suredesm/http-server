package lk.fincore

import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path

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

        }catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun listenToClient(socket: Socket, repo: UserRepository): Boolean {
        val inputStream = socket.getInputStream()
        var data: Int
        var requestText = ""
        var endOfStream = false

        while(true) {
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

        try {
            val httpRequest = HttpRequest.parse(requestText)
            val body = buildString {
                @Suppress("UNUSED_PARAMETER")
                for (i in 0..<httpRequest.contentLength) {
                    data = inputStream.read()
                    if (data == -1) break
                    append(data.toChar())
                }
            }
            httpRequest.body = body

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
                    httpRequest.path.substring(UPTO_ROUTE_API_LENGTH) =="/users"
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

                if (fileToBeRetrieved != null) {
                    val content = Files.readString(fileToBeRetrieved)

                    response = HttpResponse(
                        HTTP_VERSION_1_1,
                        200,

                        "OK",
                        mapOf(
                            "Agent" to listOf("my-server!"),
                            "Content-Type" to listOf(Files.probeContentType(fileToBeRetrieved))
                        ),

                        content
                    )
                } else throw ServerException.NotFound("Index file not found")
            }  else throw ServerException.NotFound("Not Found")
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

        val outputStream = socket.getOutputStream()

        outputStream.write(response.toStringByteArray())
        outputStream.flush()


        return endOfStream && requestText == ""
    }
}