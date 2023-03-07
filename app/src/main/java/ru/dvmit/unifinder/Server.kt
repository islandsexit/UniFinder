package ru.dvmit.unifinder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.*

class Server(private val port: Int, imageView: ImageView) {

companion object{
    const val TAG = "Server"
}

    private lateinit var serverSocket: ServerSocket
    private var serverThread: Thread

    init {
        serverThread = Thread{
            start(imageView)
        }
        serverThread.start()
        Log.i(TAG, "Server started")
    }


    fun stop(){
        serverThread.interrupt()
        serverSocket.close()

    }

    fun start(imageView: ImageView) {
        serverSocket = ServerSocket(port)
        while (true) {
            val clientSocket = serverSocket.accept()
            val input = clientSocket.getInputStream()
            val request = readRequest(input)
            Log.i(TAG, "start: Geted request $request")
            if (request.method == "POST") {
                val body = readBody(input, request.headers["Content-Length"]?.toInt() ?: 0)
                val keys = readKeys(body)
                if(keys.containsKey("img64")){

                    Log.i(TAG, "start: body - $body")
                    try{
                        val bitmap = decodeBase64ToBitmap(keys.get("img64")!!)
                        val response = JSONObject(mapOf("msg" to "success"))
                        val output = OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8")
                        val dateTimeStart = DateFormat.format("yyyy-MM-dd%hh:mm:ss", Date())
                        Handler(Looper.getMainLooper()).post{
                            imageView.setImageBitmap(bitmap)
                        }
                        Thread.sleep(1000)
                        val dateTimeEnd = DateFormat.format("yyyy-MM-dd%hh:mm:ss", Date())

                        val params = mapOf(
                            "pass" to "123451",
                            "personId" to "-1",
                            "startTime" to "0",
                            "endTime" to "0"


                            )
                        makeRequestInUni("http://192.168.48.87:8090/newFindRecords",params)
//                        makeRequestInUni("http://192.168.48.38:6969/find", params)
                        sendResponse(output, response.toString())

                    }catch (e:Exception){
                        e.printStackTrace()
                        Log.e(TAG, "start: ${e}", )
                        writeError("Error in decoding Image", clientSocket)
                    }
                }else{
                    writeError("No img64 was passed in body", clientSocket)
                }
            }else{
                writeError("Only Post Supported", clientSocket)
            }
            clientSocket.close()
        }
    }

    data class Request(val method: String, val path: String, val headers: Map<String, String>)

    fun readRequest(input: InputStream): Request {
        val requestLine = readLine(input)
        val parts = requestLine.split(" ")
        val method = parts[0]
        val path = parts[1]
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input)
            if (line == "") {
                break
            }
            val parts = line.split(": ")
            headers[parts[0]] = parts[1]
        }
        return Request(method, path, headers)
    }

    fun readKeys(body:String):Map<String, String>{
        val parts = body.split("&")
        val params = body.split("&").associate {
            val (key, value) = it.split("=")
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }
        return params
    }

    fun readBody(input: InputStream, length: Int): String {
        val buffer = mutableListOf<Byte>()
        var totalRead = 0
        while (totalRead < length) {
            val byte = input.read()
            if (byte == -1) {
                break
            }
            buffer.add(byte.toByte())
            totalRead++
        }
        return String(buffer.toByteArray())
    }

    fun readLine(input: InputStream): String {
        val buffer = mutableListOf<Byte>()
        while (true) {
            val byte = input.read()
            if (byte == -1 || byte == 10) {
                break
            }
            buffer.add(byte.toByte())
        }
        return String(buffer.toByteArray(), Charsets.UTF_8).trimEnd()
    }

    fun decodeBase64ToBitmap(data: String): Bitmap {
        val decodedBytes = Base64.decode(data, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }


    fun sendResponse(output: OutputStreamWriter, message: String) {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Content-Length" to message.length.toString(),
            "Connection" to "close",
            "Date" to Date().toString(),
        )
        val statusLine = "HTTP/1.1 200 OK\r\n"
        val headersText = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
        val responseText = "$statusLine$headersText\r\n\r\n$message"
        output.write(responseText)
        output.flush()
    }

    fun makeRequestInUni(urlString:String, params: Map<String, String>){
        val url = URL(urlString)
        var socket = Socket(url.host, url.port)
        var outputStream = socket.getOutputStream()
        val query = params.map { "${it.key}=${it.value}" }.joinToString("&")
        val httpRequest = "GET ${url.path}?$query HTTP/1.1\r\n" +
                "Host: ${url.host}\r\n"+"\r\n"

        outputStream.write(httpRequest.toByteArray(Charsets.UTF_8))
        outputStream.flush()

        val inputStream = socket.getInputStream()
        val buffer = BufferedReader(InputStreamReader(inputStream))
        val response = buffer.readText()
        parseOutputUniUbi(response)
        buffer.close()

        socket = Socket(url.host, url.port)
        outputStream = socket.getOutputStream()
        val httpRequestToDelete = "POST /newDeleteRecords HTTP/1.1\r\n" +
                "Host: ${url.host}\r\n"+"Content-Type: application/x-www-form-urlencoded" +"\r\n"+
                "Content-Length: ${query.length}\r\n\r\n" +
                query
        outputStream.write(httpRequestToDelete.toByteArray(Charsets.UTF_8))
        outputStream.flush()

        val inputStream2 = socket.getInputStream()
        val buffer2 = BufferedReader(InputStreamReader(inputStream2))
        val response2 = buffer2.readText()
        parseOutputUniUbi(response2)
        buffer.close()

        socket.close()
    }

    private fun parseOutputUniUbi(response:String) {
        val parts = response.split("\r\n\r\n")
        val jsonObject = JSONObject(parts[1])
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            map[key] = value
        }
        Log.i(TAG, "parseOutputUniUbi: ${map.toString()}")
    }


    private fun writeError(msg:String, clientSocket:Socket){
        val errorResponse = JSONObject(mapOf("msg" to msg))
        val output = OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8")
        sendResponse(output, errorResponse.toString())
    }
}
