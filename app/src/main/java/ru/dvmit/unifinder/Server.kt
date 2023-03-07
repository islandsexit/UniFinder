package ru.dvmit.unifinder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.Visibility
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.widget.ImageView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.*
import java.util.*
import kotlin.collections.LinkedHashMap

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
                    try {
                        val bitmap = decodeBase64ToBitmap(keys.get("img64")!!)
                        val output = OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8")
                        val dateTimeStart = DateFormat.format("yyyy-MM-dd%hh:mm:ss", Date())
                        Handler(Looper.getMainLooper()).post {
                            imageView.visibility = View.VISIBLE
                            imageView.setImageBitmap(bitmap)
                        }
                        Thread.sleep(1000)
                        val dateTimeEnd = DateFormat.format("yyyy-MM-dd%hh:mm:ss", Date())

                        val params = mapOf(
                            "pass" to "123451",


                            )
                        val name = makeRequestInUni("http://192.168.48.87", params)
                        val response = JSONObject(mapOf("msg" to "SUCCESS", "personId" to name))

                        sendResponse(output, response.toString())
                        Handler(Looper.getMainLooper()).post {
                            imageView.visibility = View.INVISIBLE
                        }
                    }catch(e:JSONException){
                        writeError("There isn`t any faces", clientSocket)
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

    fun makeRequestInUni(urlString:String, params: Map<String, String>):String{
        val param = params + mapOf("personId" to "-1","startTime" to "0","endTime" to "0")
        val response = ("$urlString:8090/newFindRecords").getRequest(param)
         val findMap = parseJson(response)
        val headers = mapOf("Content-Type" to "application/x-www-form-urlencoded")
        val data = mapOf("personId" to "-1", "pass" to "123451", "startTime" to "0", "endTime" to "0")
        val responsePost = "$urlString:8090/newDeleteRecords".postRequest(headers, data)


            val dataJson = findMap["data"] as LinkedHashMap<*, *>
            val records = dataJson["records"] as JSONArray
            val lastPerson = records[0] as JSONObject
            val lastName = lastPerson["personId"] as String
        return lastName



    }

    private fun String.getRequest(
        query: Map<String, String>
    ): String {
        val query = query.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
        val url = URL("$this?$query")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        val input = BufferedReader(InputStreamReader(connection.inputStream))
        val response = input.readText()
        input.close()
        connection.disconnect()
        return response
    }

    private fun String.postRequest(
        headers: Map<String, String>,
        data: Map<String, String>
    ):String{
        val url = URL(this)
        val body = data.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
        val connection = url.openConnection() as HttpURLConnection
        headers.forEach{
            connection.setRequestProperty(it.key, it.value)
        }
        connection.setRequestProperty("Content-Length",body.toByteArray(Charsets.UTF_8).size.toString())
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val outputStream = connection.outputStream
        outputStream.write(body.toByteArray(Charsets.UTF_8))
        outputStream.flush()
        outputStream.close()
        val inputStream = connection.inputStream
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val response = bufferedReader.readText()
        bufferedReader.close()
        connection.disconnect()
        return response

    }

    private fun parseJson(response:String):Map<String,Any> {
        val json = JSONObject(response)
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            val value = json.get(key)
            if (value is JSONObject) {
                map[key] = parseJson(value.toString())
            } else {
                map[key] = value
            }
        }
        Log.i(TAG, "parseOutputUniUbi: ${map.toString()}")
        return map
    }


    private fun writeError(msg:String, clientSocket:Socket){
        val errorResponse = JSONObject(mapOf("Msg" to msg))
        val output = OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8")
        sendResponse(output, errorResponse.toString())
    }
}
