package com.nido.music.feature.chat.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.nido.music.core.crypto.SignalProtocolManager
import com.nido.music.core.network.relay.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val relayApi: ChatRelayApi,
    private val signalProtocolManager: SignalProtocolManager
) {
    companion object {
        private const val TAG = "ChatAuthManager"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nido_chat_auth_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow(loadAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    data class AuthState(
        val userId: String? = null,
        val username: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val accessToken: String? = null,
        val isLoggedIn: Boolean = false
    )

    private fun loadAuthState(): AuthState {
        val uid = prefs.getString("user_id", null)
        val uname = prefs.getString("username", null)
        val mail = prefs.getString("email", null)
        val ph = prefs.getString("phone", null)
        val token = prefs.getString("access_token", null)
        return AuthState(
            userId = uid,
            username = uname,
            email = mail,
            phone = ph,
            accessToken = token,
            isLoggedIn = !uid.isNullOrEmpty() && !token.isNullOrEmpty()
        )
    }

    suspend fun registerUser(
        desiredUsername: String,
        password: String = "nido_pass_123456",
        email: String? = null,
        phone: String? = null,
        inviteCode: String? = null,
        isGuest: Boolean = false
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        Log.d(TAG, "registerUser: desiredUsername=$desiredUsername, email=$email, phone=$phone, isGuest=$isGuest")
        try {
            val cleanUsername = desiredUsername.lstrip("@").trim().lowercase().replace(" ", "_")
            val regId = (1000..9999).random()

            val regReq = RegisterRequest(
                username = cleanUsername,
                password = password,
                email = email?.trim()?.takeIf { it.isNotBlank() },
                phone = phone,
                registration_id = regId,
                device_id = 1,
                invite_code = inviteCode
            )

            val authRes = if (isGuest) {
                try {
                    relayApi.register(regReq)
                } catch (e: Exception) {
                    // If already registered guest, attempt login
                    val loginReq = LoginRequest(
                        username = cleanUsername,
                        password = password,
                        device_id = 1
                    )
                    relayApi.login(loginReq)
                }
            } else {
                try {
                    relayApi.register(regReq)
                } catch (e: retrofit2.HttpException) {
                    throw Exception(parseHttpErrorDetail(e))
                }
            }

            saveAuth(
                authRes.user_id,
                authRes.username,
                authRes.access_token,
                authRes.refresh_token,
                email = email ?: authRes.email,
                phone = phone ?: authRes.phone,
                completedOnboarding = !isGuest
            )

            // Upload Signal prekey bundle
            uploadPreKeyBundle(authRes.access_token)

            Log.i(TAG, "registerUser: success user_id=${authRes.user_id}, username=${authRes.username}, phone=${authRes.phone}")
            Result.success(authRes)
        } catch (e: Exception) {
            Log.e(TAG, "registerUser: failed - ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun loginUser(
        desiredUsername: String,
        password: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        Log.d(TAG, "loginUser: desiredUsername=$desiredUsername")
        try {
            val cleanUsername = desiredUsername.lstrip("@").trim().lowercase().replace(" ", "_")
            val loginReq = LoginRequest(
                username = cleanUsername,
                password = password,
                device_id = 1
            )
            val authRes = try {
                relayApi.login(loginReq)
            } catch (e: retrofit2.HttpException) {
                throw Exception(parseHttpErrorDetail(e))
            }
            saveAuth(
                authRes.user_id,
                authRes.username,
                authRes.access_token,
                authRes.refresh_token,
                email = authRes.email,
                phone = authRes.phone,
                completedOnboarding = true
            )
            uploadPreKeyBundle(authRes.access_token)
            Log.i(TAG, "loginUser: success user_id=${authRes.user_id}, username=${authRes.username}, phone=${authRes.phone}")
            Result.success(authRes)
        } catch (e: Exception) {
            Log.e(TAG, "loginUser: failed - ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseHttpErrorDetail(e: retrofit2.HttpException): String {
        return try {
            val body = e.response()?.errorBody()?.string() ?: ""
            if (body.contains("\"detail\"")) {
                val json = org.json.JSONObject(body)
                json.optString("detail", e.message())
            } else {
                e.message()
            }
        } catch (_: Exception) {
            e.message()
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _authState.value = AuthState()
    }

    suspend fun autoRegisterIfGuest(): AuthState = withContext(Dispatchers.IO) {
        val current = _authState.value
        if (current.isLoggedIn && !current.userId.isNullOrEmpty()) {
            return@withContext current
        }

        // Generate unique username based on device UUID suffix
        val randSuffix = UUID.randomUUID().toString().take(6)
        val guestUsername = "user_$randSuffix"
        registerUser(desiredUsername = guestUsername, isGuest = true)

        _authState.value
    }

    private suspend fun uploadPreKeyBundle(token: String) {
        try {
            val bundle = signalProtocolManager.generateInitialPreKeyBundle()
            val uploadDto = UploadPreKeyBundle(
                identity_key = bundle.identityKey,
                signed_prekey = SignedPreKeyIn(
                    key_id = bundle.signedPreKeyId,
                    public_key = bundle.signedPreKey,
                    signature = bundle.signedPreKeySignature
                ),
                one_time_prekeys = bundle.oneTimePreKeys.map {
                    OneTimePreKeyIn(key_id = it.keyId, public_key = it.publicKey)
                },
                device_id = 1
            )
            relayApi.uploadKeys("Bearer $token", uploadDto)
        } catch (e: Exception) {
            // Ignore if keys already uploaded or network transient
        }
    }

    private fun saveAuth(
        userId: String,
        username: String,
        accessToken: String,
        refreshToken: String,
        email: String? = null,
        phone: String? = null,
        completedOnboarding: Boolean = true
    ) {
        val editor = prefs.edit()
            .putString("user_id", userId)
            .putString("username", username)
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
        if (completedOnboarding) {
            editor.putBoolean("has_completed_onboarding", true)
        }
        if (!email.isNullOrBlank()) {
            editor.putString("email", email)
        }
        if (!phone.isNullOrBlank()) {
            editor.putString("phone", phone)
        }
        editor.apply()

        _authState.value = AuthState(
            userId = userId,
            username = username,
            email = email ?: prefs.getString("email", null),
            phone = phone ?: prefs.getString("phone", null),
            accessToken = accessToken,
            isLoggedIn = true
        )
    }

    suspend fun updateProfile(
        phone: String,
        email: String? = null,
        username: String? = null
    ): Result<ProfileUpdateResponse> = withContext(Dispatchers.IO) {
        Log.d(TAG, "updateProfile: phone=$phone, email=$email, username=$username")
        try {
            val token = _authState.value.accessToken
                ?: return@withContext Result.failure(Exception("Not logged in"))
            val cleanPhone = phone.trim()
            val cleanEmail = email?.trim()?.takeIf { it.isNotBlank() }
            val cleanUsername = username?.lstrip("@")?.trim()?.lowercase()?.replace(" ", "_")?.takeIf { it.isNotBlank() }

            val req = ProfileUpdateRequest(
                phone = cleanPhone,
                email = cleanEmail,
                username = cleanUsername
            )

            val res = try {
                relayApi.updateProfile("Bearer $token", req)
            } catch (e: retrofit2.HttpException) {
                throw Exception(parseHttpErrorDetail(e))
            }

            val editor = prefs.edit()
                .putString("phone", res.phone ?: cleanPhone)
            if (!res.username.isNullOrBlank()) {
                editor.putString("username", res.username)
            }
            if (!res.email.isNullOrBlank()) {
                editor.putString("email", res.email)
            }
            if (!res.access_token.isNullOrBlank()) {
                editor.putString("access_token", res.access_token)
            }
            if (!res.refresh_token.isNullOrBlank()) {
                editor.putString("refresh_token", res.refresh_token)
            }
            editor.apply()

            _authState.value = _authState.value.copy(
                username = res.username ?: _authState.value.username,
                phone = res.phone ?: cleanPhone,
                email = res.email ?: cleanEmail ?: _authState.value.email,
                accessToken = res.access_token ?: _authState.value.accessToken
            )

            Log.i(TAG, "updateProfile: success user_id=${res.user_id}, username=${res.username}, phone=${res.phone}, email=${res.email}")
            Result.success(res)
        } catch (e: Exception) {
            Log.e(TAG, "updateProfile: failed - ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchProfile(): Result<UserProfileResponse> = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchProfile: start")
        try {
            val token = _authState.value.accessToken
                ?: return@withContext Result.failure(Exception("Not logged in"))
            val profile = relayApi.getMyProfile("Bearer $token")
            val editor = prefs.edit()
            if (!profile.phone.isNullOrBlank()) {
                editor.putString("phone", profile.phone)
            }
            if (!profile.email.isNullOrBlank()) {
                editor.putString("email", profile.email)
            }
            if (!profile.username.isNullOrBlank()) {
                editor.putString("username", profile.username)
            }
            editor.apply()

            _authState.value = _authState.value.copy(
                username = profile.username,
                phone = profile.phone,
                email = profile.email
            )
            Log.i(TAG, "fetchProfile: success username=${profile.username}, phone=${profile.phone}")
            Result.success(profile)
        } catch (e: Exception) {
            Log.w(TAG, "fetchProfile: failed - ${e.message}")
            Result.failure(e)
        }
    }

    fun needsPhoneUpdate(): Boolean {
        val s = _authState.value
        return s.isLoggedIn && s.phone.isNullOrBlank()
    }

    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean("has_completed_onboarding", false)
    }

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
    }

    private fun String.lstrip(prefix: String): String =
        if (startsWith(prefix)) substring(prefix.length) else this
}
