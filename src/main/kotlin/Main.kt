package lk.fincore

import java.io.IOException
import java.net.ServerSocket

fun main() {
    val server = TCPServer(8080)
}

class TCPServer {
    constructor(port: Int) {
        try {
            val serverSocket = ServerSocket(port)
            println("Server started")
            println("Waiting for the client ...")

            val socket = serverSocket.accept()
            println("Client accepted!")

            val inputStream = socket.getInputStream()
            var data: Int
            var request = ""

            while(true) {
                data = inputStream.read()
                if (data == -1) break
                request += data.toChar()
                if (request.endsWith("\r\n\r\n")) break
            }


            val response = "HTTP/1.1 200 OK\r\n\r\nHello from the server!"
            val outputStream = socket.getOutputStream()

            outputStream.write(response.toByteArray())
            outputStream.flush()

            socket.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}