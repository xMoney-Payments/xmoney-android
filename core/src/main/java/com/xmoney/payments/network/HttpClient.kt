package com.xmoney.payments.network

import com.xmoney.payments.model.PaymentError
import com.xmoney.payments.util.DeviceMetadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
class HttpClient(
    private val client: OkHttpClient = sharedOkHttpClient,
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun getJson(url: String, bearer: String? = null): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            executeSync(baseRequest(url, bearer).get().build())
        }

    suspend fun postJson(url: String, body: Map<String, Any?>, bearer: String? = null): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            val payload = (mapToJson(body) as JSONObject).toString().toRequestBody(jsonMedia)
            executeSync(baseRequest(url, bearer).post(payload).build())
        }

    suspend fun delete(url: String, bearer: String?) {
        withContext(Dispatchers.IO) {
            client.newCall(baseRequest(url, bearer).delete().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PaymentError.Network()
                }
            }
        }
    }

    suspend fun postMultipart(url: String, fields: Map<String, String>): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            val boundary = "xmoney-${UUID.randomUUID()}"
            val body = buildMultipartBody(boundary, fields)
            val mediaType = "multipart/form-data; boundary=$boundary".toMediaType()
            executeSync(
                Request.Builder()
                    .url(url)
                    .apply { withDefaultHeaders() }
                    .post(body.toRequestBody(mediaType))
                    .build(),
            )
        }

    internal fun buildMultipartBody(boundary: String, fields: Map<String, String>): ByteArray {
        val body = StringBuilder()
        for ((key, value) in fields) {
            val safeKey = sanitizedMultipartToken(key, boundary, "name")
            val safeValue = sanitizedMultipartToken(value, boundary, "value")
            body.append("--").append(boundary).append("\r\n")
            body.append("Content-Disposition: form-data; name=\"").append(safeKey).append("\"\r\n\r\n")
            body.append(safeValue).append("\r\n")
        }
        body.append("--").append(boundary).append("--\r\n")
        return body.toString().toByteArray(Charsets.UTF_8)
    }

    private fun baseRequest(url: String, bearer: String?): Request.Builder {
        val builder = Request.Builder().url(url)
            .apply { withDefaultHeaders() }
            .header("Content-Type", "application/json")
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return builder
    }

    private fun Request.Builder.withDefaultHeaders() {
        for ((name, value) in DeviceMetadata.httpHeaders()) {
            header(name, value)
        }
    }

    private fun executeSync(request: Request): Map<String, Any?> {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { jsonToMap(JSONObject(text)) }.getOrDefault(emptyMap())
            if (!response.isSuccessful) {
                throw PaymentError.Network()
            }
            return json
        }
    }

    companion object {
        private val sharedOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        fun shared(): HttpClient = HttpClient(sharedOkHttpClient)

        internal fun sanitizedMultipartToken(value: String, boundary: String, label: String): String {
            if (value.contains("\r") || value.contains("\n") || value.contains(boundary)) {
                throw PaymentError.Network("Invalid multipart field $label")
            }
            return value
        }

        fun jsonToMap(obj: JSONObject): Map<String, Any?> {
            val map = HashMap<String, Any?>()
            for (key in obj.keys()) {
                map[key] = unwrap(obj.get(key))
            }
            return map
        }

        fun mapToJson(value: Any?): Any = when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val obj = JSONObject()
                for ((k, v) in value) {
                    if (k is String) obj.put(k, mapToJson(v))
                }
                obj
            }
            is List<*> -> {
                val arr = org.json.JSONArray()
                for (item in value) arr.put(mapToJson(item))
                arr
            }
            is Boolean, is Number, is String -> value
            else -> value.toString()
        }

        private fun unwrap(value: Any?): Any? = when (value) {
            is JSONObject -> jsonToMap(value)
            is org.json.JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }
            JSONObject.NULL -> null
            else -> value
        }
    }
}
