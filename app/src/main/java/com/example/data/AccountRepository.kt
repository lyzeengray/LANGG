package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.R
import com.example.model.AccountDurationOption
import com.example.model.LicenseAccount
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = Firebase.auth.currentUser
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

class AccountRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Login Google dulu ya.")

    suspend fun verifyAndRegisterOwner(enteredCode: String): Result<Unit> {
        val cleanedCode = enteredCode.trim()
        if (cleanedCode != OWNER_CODE) {
            return Result.failure(IllegalArgumentException("Kode owner salah."))
        }
        var docPath = "owners/unauthenticated"
        return try {
            val uid = requireUserId()
            val ownerRef = db.collection("owners").document(uid)
            docPath = ownerRef.path
            val snap = ownerRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "ownerUid" to uid,
                    "ownerCode" to OWNER_CODE,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                ownerRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeOwnerAccounts(ownerUid: String): Flow<List<LicenseAccount>> = flow {
        val path = "accounts"
        emitAll(
            db.collection(path)
                .whereEqualTo("createdByUid", ownerUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LicenseAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == ownerUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createAccount(
        usernameRaw: String,
        passwordRaw: String,
        durationOption: AccountDurationOption,
        customDays: Int
    ): Result<LicenseAccount> {
        val trimmedUser = usernameRaw.trim()
        val accountId = trimmedUser.lowercase()
        val docPath = "accounts/$accountId"
        return try {
            val uid = requireUserId()
            require(accountId.matches(ACCOUNT_ID_REGEX)) {
                "Username minimal 3 huruf/angka."
            }
            require(passwordRaw.length in 3..64) {
                "Password minimal 3 karakter."
            }

            val resolvedDays: Long
            val expiresAtMillis: Long
            if (durationOption == AccountDurationOption.LIFETIME) {
                resolvedDays = -1L
                expiresAtMillis = -1L
            } else {
                val d = if (durationOption == AccountDurationOption.CUSTOM) {
                    customDays.toLong()
                } else {
                    durationOption.days
                }
                require(d in 1L..3650L) {
                    "Isi hari antara 1 - 3650."
                }
                resolvedDays = d
                expiresAtMillis = System.currentTimeMillis() + d * MILLIS_PER_DAY
            }

            val docRef = db.collection("accounts").document(accountId)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                return Result.failure(IllegalStateException("Username '$trimmedUser' udah kepake."))
            }

            val hash = sha256Hex(passwordRaw)
            val payload = mapOf(
                "username" to trimmedUser,
                "passwordHash" to hash,
                "createdByUid" to uid,
                "durationType" to durationOption.id,
                "durationDays" to resolvedDays,
                "expiresAtMillis" to expiresAtMillis,
                "boundDeviceId" to "",
                "boundDeviceName" to "",
                "boundByUid" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            Result.success(
                LicenseAccount(
                    id = accountId,
                    username = trimmedUser,
                    passwordHash = hash,
                    createdByUid = uid,
                    durationType = durationOption.id,
                    durationDays = resolvedDays,
                    expiresAtMillis = expiresAtMillis,
                    boundDeviceId = "",
                    boundDeviceName = "",
                    boundByUid = ""
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(accountId: String): Result<Unit> {
        val docPath = "accounts/$accountId"
        return try {
            requireUserId()
            require(accountId.isNotBlank()) { "ID akun tidak valid." }
            db.collection("accounts").document(accountId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun loginAndBindDevice(
        usernameRaw: String,
        passwordRaw: String,
        localDeviceId: String
    ): Result<LicenseAccount> {
        val accountId = usernameRaw.trim().lowercase()
        val docPath = "accounts/$accountId"
        return try {
            val uid = requireUserId()
            require(accountId.matches(ACCOUNT_ID_REGEX)) {
                "Masukkan username yang valid (minimal 3 karakter)."
            }
            require(passwordRaw.isNotEmpty()) {
                "Password tidak boleh kosong."
            }
            require(localDeviceId.length in 6..128) {
                "ID perangkat tidak valid."
            }

            val passwordHash = sha256Hex(passwordRaw)
            val deviceModel = readableDeviceName()
            val docRef = db.collection("accounts").document(accountId)

            val accountResult = db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Akun '$usernameRaw' ga ketemu.")
                }
                val account = snapshot.toObject(
                    LicenseAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Data akun error.")

                if (account.passwordHash != passwordHash) {
                    throw IllegalStateException("Password salah.")
                }
                if (account.isExpiredNow()) {
                    throw IllegalStateException("Akun udah habis masa aktifnya.")
                }

                when {
                    account.boundDeviceId.isBlank() -> {
                        transaction.update(
                            docRef,
                            mapOf(
                                "boundDeviceId" to localDeviceId,
                                "boundDeviceName" to deviceModel,
                                "boundByUid" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        account.copy(
                            boundDeviceId = localDeviceId,
                            boundDeviceName = deviceModel,
                            boundByUid = uid
                        )
                    }

                    account.boundDeviceId == localDeviceId -> {
                        account
                    }

                    else -> {
                        val lockedLabel = account.boundDeviceName.ifBlank { "HP lain" }
                        throw IllegalStateException(
                            "Akun udah kepake di $lockedLabel. Minta owner bikin akun baru."
                        )
                    }
                }
            }.await()

            Result.success(accountResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun verifyActiveSession(accountId: String, localDeviceId: String): Result<LicenseAccount> {
        val docPath = "accounts/$accountId"
        return try {
            requireUserId()
            val snap = db.collection("accounts").document(accountId).get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Akun udah dihapus owner."))
            }
            val account = snap.toObject(
                LicenseAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Sesi akun error."))

            if (account.isExpiredNow()) {
                return Result.failure(IllegalStateException("Masa aktif akun udah berakhir."))
            }
            if (account.boundDeviceId.isNotBlank() && account.boundDeviceId != localDeviceId) {
                return Result.failure(IllegalStateException("Akun terkunci di HP lain."))
            }
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    companion object {
        const val OWNER_CODE = "100214"
        private const val MILLIS_PER_DAY = 86_400_000L
        private val ACCOUNT_ID_REGEX = Regex("^[a-z0-9_\\-]{3,32}$")

        fun sha256Hex(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun readableDeviceName(): String {
            val mfg = Build.MANUFACTURER.orEmpty().trim()
            val model = Build.MODEL.orEmpty().trim()
            val raw = if (model.startsWith(mfg, ignoreCase = true)) model else "$mfg $model".trim()
            return raw.ifBlank { "Android Device" }.take(80)
        }
    }
}
