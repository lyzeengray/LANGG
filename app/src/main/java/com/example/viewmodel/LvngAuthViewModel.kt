package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountRepository
import com.example.data.LvngPreferencesRepository
import com.example.model.AccountDurationOption
import com.example.model.LicenseAccount
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AccountsUiState {
    data object Loading : AccountsUiState
    data class Success(val accounts: List<LicenseAccount>) : AccountsUiState
    data class Error(val message: String) : AccountsUiState
}

data class LoginFormUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val ownerVerifiedUid: String? = null,
    val ownerAuthLoading: Boolean = false,
    val ownerAuthError: String? = null,
    val ownerActionLoading: Boolean = false,
    val ownerActionFeedback: String? = null
)

class LvngAuthViewModel(
    private val accountRepository: AccountRepository,
    private val prefsRepository: LvngPreferencesRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(LoginFormUiState())
    val formState: StateFlow<LoginFormUiState> = _formState.asStateFlow()

    private val _activeOwnerUid = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ownerAccountsState: StateFlow<AccountsUiState> = _activeOwnerUid
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf<AccountsUiState>(AccountsUiState.Loading)
            } else {
                accountRepository.observeOwnerAccounts(uid)
                    .map<List<LicenseAccount>, AccountsUiState> { AccountsUiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing owner accounts", rootError)
                        emit(AccountsUiState.Error(rootError.localizedMessage ?: "Gagal memuat daftar akun"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AccountsUiState.Loading
        )

    fun clearLoginError() {
        _formState.update { it.copy(errorMessage = null) }
    }

    fun clearOwnerErrors() {
        _formState.update { it.copy(ownerAuthError = null, ownerActionFeedback = null) }
    }

    fun closeOwnerPanel() {
        _activeOwnerUid.value = null
        _formState.update {
            it.copy(
                ownerVerifiedUid = null,
                ownerAuthError = null,
                ownerActionFeedback = null
            )
        }
    }

    fun loginWithUsernamePassword(
        username: String,
        password: String,
        onLoginSuccess: () -> Unit
    ) {
        if (_formState.value.isLoading) return
        _formState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val deviceId = prefsRepository.getOrCreateDeviceId()
            val result = accountRepository.loginAndBindDevice(
                usernameRaw = username,
                passwordRaw = password,
                localDeviceId = deviceId
            )
            result.fold(
                onSuccess = { account ->
                    prefsRepository.saveLoginSession(
                        accountId = account.id,
                        username = account.username,
                        expiresAtMillis = account.expiresAtMillis
                    )
                    _formState.update { it.copy(isLoading = false, errorMessage = null) }
                    onLoginSuccess()
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Login gagal"
                        )
                    }
                }
            )
        }
    }

    fun verifyOwnerCode(
        code: String,
        currentFirebaseUid: String?,
        onVerified: () -> Unit
    ) {
        if (_formState.value.ownerAuthLoading) return
        if (currentFirebaseUid.isNullOrBlank()) {
            _formState.update {
                it.copy(ownerAuthError = "Login Google dulu ya.")
            }
            return
        }
        _formState.update { it.copy(ownerAuthLoading = true, ownerAuthError = null) }

        viewModelScope.launch {
            val result = accountRepository.verifyAndRegisterOwner(code)
            result.fold(
                onSuccess = {
                    _activeOwnerUid.value = currentFirebaseUid
                    _formState.update {
                        it.copy(
                            ownerAuthLoading = false,
                            ownerVerifiedUid = currentFirebaseUid,
                            ownerAuthError = null
                        )
                    }
                    onVerified()
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            ownerAuthLoading = false,
                            ownerAuthError = error.localizedMessage ?: "Kode Owner tidak valid"
                        )
                    }
                }
            )
        }
    }

    fun createAccount(
        username: String,
        password: String,
        durationOption: AccountDurationOption,
        customDaysRaw: String,
        onCreated: () -> Unit
    ) {
        if (_formState.value.ownerActionLoading) return
        val customDays = if (durationOption == AccountDurationOption.CUSTOM) {
            customDaysRaw.trim().toIntOrNull() ?: 0
        } else {
            0
        }

        _formState.update { it.copy(ownerActionLoading = true, ownerActionFeedback = null) }
        viewModelScope.launch {
            val result = accountRepository.createAccount(
                usernameRaw = username,
                passwordRaw = password,
                durationOption = durationOption,
                customDays = customDays
            )
            result.fold(
                onSuccess = { created ->
                    _formState.update {
                        it.copy(
                            ownerActionLoading = false,
                            ownerActionFeedback = "Akun '${created.username}' berhasil dibuat"
                        )
                    }
                    onCreated()
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            ownerActionLoading = false,
                            ownerActionFeedback = error.localizedMessage ?: "Gagal membuat akun"
                        )
                    }
                }
            )
        }
    }

    fun deleteAccount(accountId: String, username: String) {
        if (_formState.value.ownerActionLoading) return
        _formState.update { it.copy(ownerActionLoading = true, ownerActionFeedback = null) }
        viewModelScope.launch {
            val result = accountRepository.deleteAccount(accountId)
            result.fold(
                onSuccess = {
                    _formState.update {
                        it.copy(
                            ownerActionLoading = false,
                            ownerActionFeedback = "Akun '$username' berhasil dihapus"
                        )
                    }
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            ownerActionLoading = false,
                            ownerActionFeedback = error.localizedMessage ?: "Gagal menghapus akun"
                        )
                    }
                }
            )
        }
    }

    fun verifySavedSession(
        accountId: String,
        onInvalidSession: (reason: String) -> Unit
    ) {
        viewModelScope.launch {
            val deviceId = prefsRepository.getOrCreateDeviceId()
            val result = accountRepository.verifyActiveSession(accountId, deviceId)
            result.onFailure { err ->
                val msg = err.localizedMessage ?: "Sesi akun tidak valid"
                // Only force logout on definitive account denial (deleted, expired, wrong device), not transient offline errors
                if (msg.contains("dihapus", ignoreCase = true) ||
                    msg.contains("berakhir", ignoreCase = true) ||
                    msg.contains("terkunci", ignoreCase = true)
                ) {
                    prefsRepository.clearLoginSession()
                    _formState.update { it.copy(errorMessage = msg) }
                    onInvalidSession(msg)
                }
            }
        }
    }

    fun logoutLocalSession() {
        viewModelScope.launch {
            prefsRepository.clearLoginSession()
        }
    }

    private companion object {
        const val TAG = "LvngAuthVM"
    }
}
