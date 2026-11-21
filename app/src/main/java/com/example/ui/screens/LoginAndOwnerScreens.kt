package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.model.AccountDurationOption
import com.example.model.LicenseAccount
import com.example.ui.theme.LvngAmber
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngCarbon
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngCrimsonDark
import com.example.ui.theme.LvngCyan
import com.example.ui.theme.LvngElevated
import com.example.ui.theme.LvngEmerald
import com.example.ui.theme.LvngObsidian
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import com.example.ui.theme.LvngVoid
import com.example.viewmodel.AccountsUiState
import com.example.viewmodel.LoginFormUiState
import com.google.firebase.auth.FirebaseUser

@Composable
fun LoginScreen(
    firebaseUser: FirebaseUser?,
    formState: LoginFormUiState,
    onGoogleAuthSuccess: () -> Unit,
    onLoginSubmit: (username: String, password: String) -> Unit,
    onVerifyOwnerCode: (code: String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var googleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    var showOwnerCodeDialog by remember { mutableStateOf(false) }
    var ownerCodeInput by remember { mutableStateOf("") }

    fun triggerGoogleSignIn(afterSignIn: (() -> Unit)? = null) {
        googleLoading = true
        googleError = null
        GoogleAuthHelper.onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                googleLoading = false
                onGoogleAuthSuccess()
                afterSignIn?.invoke()
            },
            onAuthError = { msg ->
                googleLoading = false
                googleError = msg
            },
            scope = scope,
            onAuthCancelled = {
                googleLoading = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A070B),
                        LvngVoid,
                        LvngVoid
                    )
                )
            )
            .statusBarsPadding()
            .imePadding()
    ) {
        // Top Header: Branding on Left, Owner Profile Button on Top-Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.lvng_space_logo),
                    contentDescription = "LVNG SPACE",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "LVNG SPACE",
                        style = MaterialTheme.typography.titleMedium,
                        color = LvngTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "LOGIN PANEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = LvngCrimson
                    )
                }
            }

            // Owner Profile Icon Button (Top-Right)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(LvngCarbon)
                    .border(1.dp, LvngCrimson.copy(alpha = 0.55f), CircleShape)
                    .clickable {
                        onClearError()
                        ownerCodeInput = ""
                        showOwnerCodeDialog = true
                    }
                    .testTag("owner_profile_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Panel Owner",
                    tint = LvngCrimson,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Main Centered Login Form Card
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(LvngCarbon)
                    .border(1.dp, LvngBorderSubtle, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Login Akun",
                    style = MaterialTheme.typography.headlineSmall,
                    color = LvngTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Masukin username & password dari owner",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LvngTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Cloud Google Auth Status Strip (required by Firestore security rules)
                if (firebaseUser == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(LvngObsidian)
                            .border(1.dp, LvngCrimson.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CloudQueue,
                                contentDescription = null,
                                tint = LvngAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Login Google dulu",
                                style = MaterialTheme.typography.labelLarge,
                                color = LvngTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Biar akunnya kesinkron & ke-lock ke HP ini",
                            style = MaterialTheme.typography.bodySmall,
                            color = LvngTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { triggerGoogleSignIn() },
                            enabled = !googleLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("google_signin_button")
                        ) {
                            if (googleLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Sign in with Google",
                                    color = Color(0xFF111315),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LvngEmerald.copy(alpha = 0.10f))
                            .border(1.dp, LvngEmerald.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = LvngEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cloud Terhubung • ${firebaseUser.email ?: "Aktif"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = LvngEmerald,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onClearError()
                    },
                    label = { Text("Username") },
                    leadingIcon = {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = LvngCrimson)
                    },
                    singleLine = true,
                    colors = lvngTextFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_username_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearError()
                    },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = LvngCrimson)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Sembunyikan Password" else "Lihat Password",
                                tint = LvngTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    colors = lvngTextFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                val displayError = formState.errorMessage ?: googleError
                if (!displayError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LvngCrimson.copy(alpha = 0.14f))
                            .border(1.dp, LvngCrimson.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = displayError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF8A95)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (firebaseUser == null) {
                            triggerGoogleSignIn {
                                onLoginSubmit(username, password)
                            }
                        } else {
                            onLoginSubmit(username, password)
                        }
                    },
                    enabled = !formState.isLoading && !googleLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_submit_button")
                ) {
                    if (formState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "MASUK",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "1 akun cuma buat 1 HP ya",
                    style = MaterialTheme.typography.labelSmall,
                    color = LvngTextSecondary
                )
            }
        }
    }

    if (showOwnerCodeDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!formState.ownerAuthLoading) {
                    showOwnerCodeDialog = false
                }
            },
            containerColor = LvngCarbon,
            titleContentColor = LvngTextPrimary,
            textContentColor = LvngTextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = null,
                    tint = LvngCrimson,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "Panel Owner",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Masukin kode owner buat bikin atau hapus akun",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LvngTextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (firebaseUser == null) {
                        Button(
                            onClick = { triggerGoogleSignIn() },
                            enabled = !googleLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (googleLoading) "Menghubungkan..." else "Sign in with Google Dulu",
                                color = Color(0xFF111315),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = ownerCodeInput,
                        onValueChange = {
                            ownerCodeInput = it
                            onClearError()
                        },
                        label = { Text("Kode Owner") },
                        leadingIcon = {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = LvngCrimson)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = lvngTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_code_input")
                    )

                    if (!formState.ownerAuthError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formState.ownerAuthError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF8A95)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (firebaseUser == null) {
                            triggerGoogleSignIn {
                                onVerifyOwnerCode(ownerCodeInput)
                            }
                        } else {
                            onVerifyOwnerCode(ownerCodeInput)
                        }
                    },
                    enabled = !formState.ownerAuthLoading && !googleLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                    modifier = Modifier.testTag("owner_code_submit")
                ) {
                    if (formState.ownerAuthLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Masuk Owner", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOwnerCodeDialog = false },
                    enabled = !formState.ownerAuthLoading
                ) {
                    Text("Batal", color = LvngTextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnerPanelScreen(
    formState: LoginFormUiState,
    accountsState: AccountsUiState,
    onCreateAccount: (username: String, password: String, duration: AccountDurationOption, customDays: String, onSuccess: () -> Unit) -> Unit,
    onDeleteAccount: (accountId: String, username: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableStateOf(AccountDurationOption.LIFETIME) }
    var customDaysInput by remember { mutableStateOf("14") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LvngVoid)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("owner_panel_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali ke Login",
                    tint = LvngTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Panel Owner",
                    style = MaterialTheme.typography.titleLarge,
                    color = LvngTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bikin & hapus akun",
                    style = MaterialTheme.typography.bodySmall,
                    color = LvngTextSecondary
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Form Buat Akun Baru
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(LvngCarbon)
                        .border(1.dp, LvngBorderSubtle, RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Tambah Akun Baru",
                        style = MaterialTheme.typography.titleMedium,
                        color = LvngTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username Baru") },
                        placeholder = { Text("contoh: player_01") },
                        singleLine = true,
                        colors = lvngTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_create_username")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Password Baru") },
                        placeholder = { Text("Minimal 3 karakter") },
                        singleLine = true,
                        colors = lvngTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_create_password")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Durasi Aktif Akun",
                        style = MaterialTheme.typography.labelLarge,
                        color = LvngTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AccountDurationOption.entries.forEach { option ->
                            val selected = selectedDuration == option
                            FilterChip(
                                selected = selected,
                                onClick = { selectedDuration = option },
                                label = {
                                    Text(
                                        text = option.labelId,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LvngCrimson,
                                    selectedLabelColor = Color.White,
                                    containerColor = LvngObsidian,
                                    labelColor = LvngTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = LvngBorderSubtle,
                                    selectedBorderColor = LvngCrimson
                                ),
                                modifier = Modifier.testTag("duration_chip_${option.id}")
                            )
                        }
                    }

                    if (selectedDuration == AccountDurationOption.CUSTOM) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customDaysInput,
                            onValueChange = { raw ->
                                if (raw.all { it.isDigit() } && raw.length <= 4) {
                                    customDaysInput = raw
                                }
                            },
                            label = { Text("Jumlah Hari (Custom)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = lvngTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("owner_custom_days_input")
                        )
                    }

                    if (!formState.ownerActionFeedback.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(LvngObsidian)
                                .border(1.dp, LvngCrimson.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = formState.ownerActionFeedback,
                                style = MaterialTheme.typography.bodySmall,
                                color = LvngTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            onCreateAccount(newUsername, newPassword, selectedDuration, customDaysInput) {
                                newUsername = ""
                                newPassword = ""
                            }
                        },
                        enabled = !formState.ownerActionLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("owner_create_submit")
                    ) {
                        if (formState.ownerActionLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Buat Akun Sekarang",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Daftar Akun Terdaftar
            item {
                Text(
                    text = "DAFTAR AKUN TERDAFTAR",
                    style = MaterialTheme.typography.labelLarge,
                    color = LvngTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }

            when (accountsState) {
                is AccountsUiState.Loading -> {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp)
                        ) {
                            CircularProgressIndicator(color = LvngCrimson)
                        }
                    }
                }

                is AccountsUiState.Error -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(LvngCarbon)
                                .border(1.dp, LvngCrimson.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = accountsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFF8A95)
                            )
                        }
                    }
                }

                is AccountsUiState.Success -> {
                    if (accountsState.accounts.isEmpty()) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(LvngCarbon)
                                    .border(1.dp, LvngBorderSubtle, RoundedCornerShape(16.dp))
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = "Belum ada akun, bikin dulu di atas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LvngTextSecondary
                                )
                            }
                        }
                    } else {
                        items(accountsState.accounts, key = { it.id }) { account ->
                            OwnerAccountCard(
                                account = account,
                                onDelete = { onDeleteAccount(account.id, account.username) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerAccountCard(
    account: LicenseAccount,
    onDelete: () -> Unit
) {
    val isExpired = account.isExpiredNow()
    val durationLabel = when {
        account.isLifetimeAccount -> "LIFETIME"
        isExpired -> "KEDALUWARSA"
        else -> {
            val remainingMs = (account.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val remainingHours = remainingMs / 3_600_000L
            val days = remainingHours / 24L
            val hours = remainingHours % 24L
            if (days > 0) "${account.durationDays} Hari • Sisa ${days}h ${hours}j"
            else "${account.durationDays} Hari • Sisa ${hours}j"
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LvngCarbon)
            .border(1.dp, LvngBorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("owner_account_card_${account.id}")
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.username,
                    style = MaterialTheme.typography.titleMedium,
                    color = LvngTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isExpired -> LvngCrimsonDark.copy(alpha = 0.6f)
                                account.isLifetimeAccount -> LvngEmerald.copy(alpha = 0.16f)
                                else -> LvngElevated
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = durationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isExpired -> Color(0xFFFF8A95)
                            account.isLifetimeAccount -> LvngEmerald
                            else -> LvngAmber
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (account.hasDeviceLock) Icons.Filled.PhoneAndroid else Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = if (account.hasDeviceLock) LvngCrimson else LvngCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (account.hasDeviceLock) {
                        "Terkunci di ${account.boundDeviceName.ifBlank { "1 HP" }}"
                    } else {
                        "Belum dipakai (tersedia 1 HP)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (account.hasDeviceLock) LvngTextPrimary else LvngCyan
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(48.dp)
                .testTag("owner_delete_${account.id}")
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = "Hapus Akun ${account.username}",
                tint = LvngCrimson
            )
        }
    }
}

@Composable
private fun lvngTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LvngCrimson,
    unfocusedBorderColor = LvngBorderSubtle,
    focusedContainerColor = LvngObsidian,
    unfocusedContainerColor = LvngObsidian,
    focusedTextColor = LvngTextPrimary,
    unfocusedTextColor = LvngTextPrimary,
    focusedLabelColor = LvngCrimson,
    unfocusedLabelColor = LvngTextSecondary
)
