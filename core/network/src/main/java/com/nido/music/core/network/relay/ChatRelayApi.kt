package com.nido.music.core.network.relay

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String? = null,
    val phone: String? = null,
    val registration_id: Int,
    val device_id: Int = 1,
    val fcm_token: String? = null,
    val invite_code: String? = null
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val device_id: Int = 1,
    val fcm_token: String? = null
)

@Serializable
data class AuthResponse(
    val user_id: String,
    val username: String,
    val device_id: Int,
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "bearer",
    val email: String? = null,
    val phone: String? = null
)

@Serializable
data class ProfileUpdateRequest(
    val phone: String,
    val email: String? = null,
    val username: String? = null
)

@Serializable
data class ProfileUpdateResponse(
    val user_id: String,
    val username: String,
    val phone: String? = null,
    val email: String? = null,
    val access_token: String? = null,
    val refresh_token: String? = null
)

@Serializable
data class UserProfileResponse(
    val user_id: String,
    val username: String,
    val phone: String? = null,
    val email: String? = null
)

@Serializable
data class RegisteredContact(
    val phone: String = "",
    val username: String,
    val user_id: String
)

@Serializable
data class ContactLookupRequest(
    val phone_numbers: List<String>
)

@Serializable
data class SignedPreKeyIn(
    val key_id: Int,
    val public_key: String,
    val signature: String
)

@Serializable
data class OneTimePreKeyIn(
    val key_id: Int,
    val public_key: String
)

@Serializable
data class UploadPreKeyBundle(
    val identity_key: String,
    val signed_prekey: SignedPreKeyIn,
    val one_time_prekeys: List<OneTimePreKeyIn>,
    val device_id: Int = 1
)

@Serializable
data class PeerBundleResponse(
    val user_id: String,
    val device_id: Int,
    val registration_id: Int,
    val identity_key: String,
    val signed_prekey: SignedPreKeyIn,
    val one_time_prekey: OneTimePreKeyIn? = null
)

@Serializable
data class SendMessageRequest(
    val recipient_user_id: String,
    val recipient_device_id: Int = 1,
    val ciphertext: String,
    val message_type: Int = 1,
    val sender_device_id: Int = 1,
    val reply_to_id: String? = null,
    val is_forwarded: Boolean = false,
    val client_msg_id: String? = null
)

@Serializable
data class ReceiptOut(
    val message_id: String,
    val status: String
)

@Serializable
data class MarkReadRequest(
    val message_ids: List<String>,
    val peer_user_id: String? = null
)

@Serializable
data class AckReceiptsRequest(
    val message_ids: List<String>
)

@Serializable
data class SendMessageResponse(
    val status: String,
    val message_id: String
)

@Serializable
data class PendingMessageOut(
    val id: String,
    val sender_user_id: String,
    val sender_username: String? = null,
    val sender_device_id: Int,
    val ciphertext: String,
    val message_type: Int,
    val group_id: String? = null,
    val reply_to_id: String? = null,
    val is_deleted: Boolean = false,
    val is_forwarded: Boolean = false,
    val created_at: String
)

@Serializable
data class ClipUploadResponse(
    val clip_id: String,
    val url: String
)

interface ChatRelayApi {
    @retrofit2.http.Multipart
    @POST("/api/v1/media/clip")
    suspend fun uploadClip(
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): ClipUploadResponse
    @POST("/api/v1/auth/register")
    suspend fun register(@Body req: RegisterRequest): AuthResponse

    @POST("/api/v1/auth/login")
    suspend fun login(@Body req: LoginRequest): AuthResponse

    @GET("/api/v1/auth/me")
    suspend fun getMyProfile(
        @Header("Authorization") auth: String
    ): UserProfileResponse

    @PUT("/api/v1/auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") auth: String,
        @Body req: ProfileUpdateRequest
    ): ProfileUpdateResponse

    @GET("/api/v1/auth/user/{username}")
    suspend fun getUserByUsername(@Path("username") username: String): RegisteredContact

    @POST("/api/v1/auth/contacts/lookup")
    suspend fun lookupContacts(@Body req: ContactLookupRequest): List<RegisteredContact>

    @PUT("/api/v1/keys/bundle")
    suspend fun uploadKeys(
        @Header("Authorization") auth: String,
        @Body bundle: UploadPreKeyBundle
    )

    @GET("/api/v1/keys/bundle/{peer_user_id}")
    suspend fun getPeerBundle(
        @Header("Authorization") auth: String,
        @Path("peer_user_id") peerUserId: String,
        @Query("device_id") deviceId: Int = 1
    ): PeerBundleResponse

    @POST("/api/v1/messages/send")
    suspend fun sendMessage(
        @Header("Authorization") auth: String,
        @Body req: SendMessageRequest
    ): SendMessageResponse

    @GET("/api/v1/messages/pending")
    suspend fun getPendingMessages(
        @Header("Authorization") auth: String,
        @Query("device_id") deviceId: Int = 1
    ): List<PendingMessageOut>

    @POST("/api/v1/messages/ack/{message_id}")
    suspend fun ackMessage(
        @Header("Authorization") auth: String,
        @Path("message_id") messageId: String
    )

    @POST("/api/v1/messages/receipts/read")
    suspend fun sendReadReceipts(
        @Header("Authorization") auth: String,
        @Body req: MarkReadRequest
    )

    @GET("/api/v1/messages/receipts/pending")
    suspend fun getPendingReceipts(
        @Header("Authorization") auth: String
    ): List<ReceiptOut>

    @POST("/api/v1/messages/receipts/ack")
    suspend fun ackReceipts(
        @Header("Authorization") auth: String,
        @Body req: AckReceiptsRequest
    )
}
