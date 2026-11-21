package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.PermissionStep
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngCarbon
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary
import com.example.ui.theme.LvngVoid

@Composable
fun PermissionOnboardingScreen(
    step: PermissionStep,
    language: AppLanguage,
    onRefreshPermissions: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    val (title, description) = when (step) {
        PermissionStep.OVERLAY -> {
            if (language == AppLanguage.ID) {
                "Butuh izin overlay" to "Biar logo panel bisa muncul di atas game"
            } else {
                "Need overlay permission" to "Lets the floating panel show over games"
            }
        }
        PermissionStep.WRITE_SETTINGS -> {
            if (language == AppLanguage.ID) {
                "Butuh izin ubah setelan" to "Buat atur kunci layar otomatis"
            } else {
                "Need write settings" to "Lets the app lock rotation automatically"
            }
        }
        PermissionStep.NOTIFICATIONS -> {
            if (language == AppLanguage.ID) {
                "Butuh izin notifikasi" to "Biar panel tetep jalan di latar belakang"
            } else {
                "Need notifications" to "Keeps the overlay running during games"
            }
        }
        PermissionStep.COMPLETE -> "" to ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LvngVoid)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        // Subtle brand emblem at top-center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 180.dp)
                .alpha(0.85f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.lvng_space_logo),
                contentDescription = "LVNG SPACE",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "LVNG SPACE",
                style = MaterialTheme.typography.headlineMedium,
                color = LvngTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        // Bottom-anchored clean permission card matching Screenshots 1-3
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(LvngCarbon)
                .border(1.dp, LvngBorderSubtle, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            // Subtle step indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                val activeIdx = when (step) {
                    PermissionStep.OVERLAY -> 0
                    PermissionStep.WRITE_SETTINGS -> 1
                    PermissionStep.NOTIFICATIONS -> 2
                    PermissionStep.COMPLETE -> 2
                }
                repeat(3) { idx ->
                    Box(
                        modifier = Modifier
                            .size(width = if (idx == activeIdx) 20.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (idx <= activeIdx) LvngCrimson else Color(0xFF2C3136))
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = LvngTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = LvngTextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    when (step) {
                        PermissionStep.OVERLAY -> {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                        PermissionStep.WRITE_SETTINGS -> {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                        PermissionStep.NOTIFICATIONS -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        }
                        PermissionStep.COMPLETE -> Unit
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LvngCrimson),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("permission_grant_button")
            ) {
                Text(
                    text = if (language == AppLanguage.ID) "Kasih Izin" else "Allow",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("permission_skip_button")
            ) {
                Text(
                    text = if (language == AppLanguage.ID) "Nanti aja" else "Skip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LvngTextSecondary
                )
            }
        }
    }
}
