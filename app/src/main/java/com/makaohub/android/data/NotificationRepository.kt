package com.makaohub.android.data

class NotificationRepository(private val auth: AuthRepository) {
    suspend fun notifications() = auth.authorized { api, header -> api.notifications(header) }
    suspend fun markRead(id: String) = auth.authorized { api, header ->
        val response = api.readNotification(header, id)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }
}
