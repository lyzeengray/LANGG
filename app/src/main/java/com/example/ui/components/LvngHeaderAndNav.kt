package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppLanguage
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngCarbon
import com.example.ui.theme.LvngCrimson
import com.example.ui.theme.LvngCrimsonDark
import com.example.ui.theme.LvngTextPrimary
import com.example.ui.theme.LvngTextSecondary

@Composable
fun LvngHomeHeader(
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.lvng_space_logo),
                contentDescription = "LVNG SPACE",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, LvngCrimson.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "LVNG SPACE",
                    style = MaterialTheme.typography.titleLarge,
                    color = LvngTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Game Space Engine",
                    style = MaterialTheme.typography.labelSmall,
                    color = LvngTextSecondary
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(LvngCrimsonDark.copy(alpha = 0.7f))
                .border(1.dp, LvngCrimson.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .clickable(onClick = onToggleLanguage)
                .padding(horizontal = 18.dp, vertical = 7.dp)
                .testTag("language_pill_button")
        ) {
            Text(
                text = language.code,
                style = MaterialTheme.typography.labelLarge,
                color = LvngTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

enum class LvngMainTab(
    val route: String,
    val labelId: String,
    val labelEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    GAMES("games", "Mulai Game", "Start Game", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    TOUCH("touch", "Touch", "Touch", Icons.Filled.TouchApp, Icons.Outlined.TouchApp),
    SETTINGS("settings", "Setelan", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun LvngBottomPillBar(
    currentTab: LvngMainTab,
    language: AppLanguage,
    onSelectTab: (LvngMainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(34.dp))
                .background(LvngCarbon)
                .border(1.dp, LvngBorderSubtle, RoundedCornerShape(34.dp))
                .padding(6.dp)
        ) {
            LvngMainTab.entries.forEach { tab ->
                val selected = tab == currentTab
                val label = if (language == AppLanguage.ID) tab.labelId else tab.labelEn

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            if (selected) LvngCrimson.copy(alpha = 0.22f)
                            else Color.Transparent
                        )
                        .then(
                            if (selected) Modifier.border(
                                1.dp,
                                LvngCrimson.copy(alpha = 0.55f),
                                RoundedCornerShape(28.dp)
                            ) else Modifier
                        )
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = if (selected) 20.dp else 18.dp)
                        .testTag("bottom_tab_${tab.route}")
                ) {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = label,
                        tint = if (selected) LvngCrimson else LvngTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    if (selected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = LvngTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
