package com.fixlens.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fixlens.app.R
import com.fixlens.app.ui.theme.*

@Composable
fun HomeScreen(
    onScanPhoto: () -> Unit,
    onLiveCamera: () -> Unit,
    onMyRepairs: () -> Unit,
    onAssemblyMode: () -> Unit = {},
    onGetPro: () -> Unit = {},
    onDemoMode: () -> Unit = {},
    planBadge: String? = null,
) {
    Column(
        Modifier.fillMaxSize().paperSurface().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CenterFocusStrong, null, tint = FixLensColors.Terracotta, modifier = Modifier.size(27.dp))
            Spacer(Modifier.width(9.dp))
            Text("FixLens", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onGetPro, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, FixLensColors.Rule), shape = RoundedCornerShape(8.dp)) {
                Text(planBadge ?: "FixLens Pro", style = MaterialTheme.typography.labelMedium, color = FixLensColors.Ink)
            }
        }
        HorizontalDivider(color = FixLensColors.Rule)
        Spacer(Modifier.height(22.dp))
        NotebookEyebrow("YOUR EVERYDAY REPAIR COMPANION")
        Spacer(Modifier.height(8.dp))
        Text("A little guidance.\nA good-as-new feeling.", style = MaterialTheme.typography.displaySmall, color = FixLensColors.Ink)
        Spacer(Modifier.height(10.dp))
        Text("For the loose, the stuck, and the not-quite-right.\nLet’s take a closer look, together.",
            style = MaterialTheme.typography.bodyMedium, color = FixLensColors.MutedInk)
        Spacer(Modifier.height(12.dp))
        Image(painterResource(R.drawable.workshop_illustration), contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            NotebookEyebrow("01 / LOOK CLOSELY")
            NotebookEyebrow("ONE SMALL FIX AT A TIME")
        }
        Button(
            onClick = onScanPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(16.dp),
        ) {
            Icon(Icons.Outlined.PhotoCamera, null)
            Spacer(Modifier.width(12.dp))
            Text("Scan a photo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeCard("Live camera", "Frame a closer look", Icons.Outlined.Videocam, FixLensColors.Sage, onLiveCamera, Modifier.weight(1f))
            ModeCard("Assembly", "Find what fits where", Icons.Outlined.Handyman, FixLensColors.Lavender, onAssemblyMode, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = FixLensColors.Rule)
        TextButton(onClick = onMyRepairs, modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp), contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Outlined.MenuBook, null, tint = FixLensColors.BlueGray)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("My repairs", style = MaterialTheme.typography.titleMedium, color = FixLensColors.Ink)
                Text("Your workshop journal", style = MaterialTheme.typography.bodySmall, color = FixLensColors.MutedInk)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = FixLensColors.MutedInk, modifier = Modifier.size(18.dp))
        }
        HorizontalDivider(color = FixLensColors.Rule)
        TextButton(onClick = onDemoMode, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
            Text("Curious? Try a guided demo", style = MaterialTheme.typography.labelMedium, color = FixLensColors.MutedInk)
        }
    }
}

@Composable
private fun ModeCard(title: String, description: String, icon: ImageVector, wash: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier, color = wash, border = BorderStroke(1.dp, FixLensColors.Rule),
        shape = RoundedCornerShape(10.dp, 12.dp, 9.dp, 11.dp)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = FixLensColors.Ink, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = FixLensColors.Ink)
            Text(description, style = MaterialTheme.typography.bodySmall, color = FixLensColors.MutedInk)
        }
    }
}
