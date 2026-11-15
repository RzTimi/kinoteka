package com.kinoteka.app.data.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Вход через Google и доступ к Диску.
 * Просим только drive.file: приложение видит лишь файлы, которые создало само (папку «Кінотека»).
 */
class GoogleAuth(context: Context) {
    private val client = Identity.getAuthorizationClient(context.applicationContext)
    private val driveScope = Scope(DRIVE_FILE_SCOPE)

    private fun request(email: String?): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(driveScope))
        .apply { if (email != null) setAccount(Account(email, ACCOUNT_TYPE)) }
        .build()

    /** Начать вход: если нужен выбор аккаунта/согласие, в результате будет pendingIntent. */
    suspend fun authorize(email: String? = null): AuthorizationResult = client.authorize(request(email)).await()

    /** Токен без показа окон; null, если пользователю нужно войти заново. */
    suspend fun tokenSilently(email: String): String? {
        val result = client.authorize(request(email)).await()
        return if (result.hasResolution()) null else result.accessToken
    }

    fun resultFromIntent(intent: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(intent)

    /** Сбросить устаревший токен (после ответа 401 от Диска). */
    suspend fun clearToken(token: String) {
        runCatching { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    /** Выход: отзываем доступ приложения к Диску этого аккаунта. */
    suspend fun revoke(email: String) {
        runCatching {
            client.revokeAccess(
                RevokeAccessRequest.builder()
                    .setScopes(listOf(driveScope))
                    .setAccount(Account(email, ACCOUNT_TYPE))
                    .build()
            ).await()
        }
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val ACCOUNT_TYPE = "com.google"

        /** Понятное сообщение об ошибке входа. */
        fun describe(e: Throwable): String = when {
            e is ApiException && e.statusCode == CommonStatusCodes.DEVELOPER_ERROR ->
                "Google ще не налаштований для цього застосунку (OAuth-клієнт Android і SHA-1 у Google Cloud)"
            e is ApiException && e.statusCode == CommonStatusCodes.NETWORK_ERROR -> "Немає з'єднання з інтернетом"
            e is ApiException && e.statusCode == CommonStatusCodes.CANCELED -> "Вхід скасовано"
            e is ApiException -> "Помилка входу Google (код ${e.statusCode})"
            else -> e.message ?: "Помилка входу"
        }
    }
}
