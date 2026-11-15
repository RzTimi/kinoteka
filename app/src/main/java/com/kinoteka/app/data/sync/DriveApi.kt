package com.kinoteka.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Ошибка Диска с HTTP-кодом (401 = токен устарел). */
class DriveException(val code: Int, message: String) : IOException(message)

@Serializable
data class DriveUser(
    val displayName: String? = null,
    val emailAddress: String? = null,
    val photoLink: String? = null,
)

@Serializable
private data class AboutResponse(val user: DriveUser = DriveUser())

@Serializable
data class DriveFile(val id: String, val name: String = "", val modifiedTime: String? = null)

@Serializable
private data class FileList(
    val files: List<DriveFile> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
)

/**
 * Минимальный клиент Google Drive API v3: только то, что нужно для синхронизации.
 * Всё через обычные HTTP-запросы, без тяжёлых библиотек Google.
 */
class DriveApi(http: OkHttpClient) {
    private val client = http.newBuilder().build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=UTF-8".toMediaType()

    suspend fun about(token: String): DriveUser {
        val url = "$API/about".toHttpUrl().newBuilder().addQueryParameter("fields", "user(displayName,emailAddress,photoLink)").build()
        return json.decodeFromString<AboutResponse>(get(token, url).decodeToString()).user
    }

    /** Найти файл или папку по имени внутри [parentId] (или в корне). */
    suspend fun find(token: String, name: String, parentId: String?, folder: Boolean): DriveFile? {
        val q = buildString {
            append("name = '").append(name.replace("\\", "\\\\").replace("'", "\\'")).append("'")
            append(" and trashed = false")
            append(if (folder) " and mimeType = '$FOLDER_MIME'" else " and mimeType != '$FOLDER_MIME'")
            if (parentId != null) append(" and '").append(parentId).append("' in parents")
        }
        return list(token, q).firstOrNull()
    }

    /** Все файлы внутри папки. */
    suspend fun children(token: String, parentId: String): List<DriveFile> =
        list(token, "'$parentId' in parents and trashed = false")

    private suspend fun list(token: String, q: String): List<DriveFile> {
        val result = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = "$API/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime)")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = json.decodeFromString<FileList>(get(token, url).decodeToString())
            result += page.files
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return result
    }

    suspend fun createFolder(token: String, name: String, parentId: String?): String {
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            if (parentId != null) put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parentId)) })
        }
        val request = Request.Builder()
            .url("$API/files?fields=id")
            .header("Authorization", "Bearer $token")
            .post(meta.toString().toRequestBody(jsonType))
            .build()
        return json.decodeFromString<DriveFile>(execute(request).decodeToString()).id
    }

    /** Создать файл с содержимым (multipart-загрузка). */
    suspend fun createFile(token: String, name: String, parentId: String, mime: String, bytes: ByteArray): String {
        val meta = buildJsonObject {
            put("name", name)
            put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parentId)) })
        }
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(meta.toString().toRequestBody(jsonType))
            .addPart(bytes.toRequestBody(mime.toMediaType()))
            .build()
        val request = Request.Builder()
            .url("$UPLOAD/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return json.decodeFromString<DriveFile>(execute(request).decodeToString()).id
    }

    /** Заменить содержимое существующего файла. */
    suspend fun updateFile(token: String, fileId: String, mime: String, bytes: ByteArray) {
        val request = Request.Builder()
            .url("$UPLOAD/files/$fileId?uploadType=media&fields=id")
            .header("Authorization", "Bearer $token")
            .patch(bytes.toRequestBody(mime.toMediaType()))
            .build()
        execute(request)
    }

    suspend fun download(token: String, fileId: String): ByteArray =
        get(token, "$API/files/$fileId".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build())

    suspend fun delete(token: String, fileId: String) {
        val request = Request.Builder()
            .url("$API/files/$fileId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        execute(request)
    }

    private suspend fun get(token: String, url: HttpUrl): ByteArray =
        execute(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())

    private suspend fun execute(request: Request): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val bytes = response.body.bytes()
            if (!response.isSuccessful) throw DriveException(response.code, "Google Drive: HTTP ${response.code}")
            bytes
        }
    }

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        fun folderUrl(id: String) = "https://drive.google.com/drive/folders/$id"
    }
}
