package com.lightest.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lightest.launcher.data.LauncherRepository
import com.lightest.launcher.model.AppItem
import com.lightest.launcher.model.IconEntry
import com.lightest.launcher.model.PackageDetails
import com.lightest.launcher.ui.theme.LauncherColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppDetailDialog(
    appItem: AppItem,
    iconEntry: IconEntry?,
    onDismiss: () -> Unit,
    onHideApp: () -> Unit,
    onEditLayout: () -> Unit
) {
    val context = LocalContext.current
    // Version/size loaded only when dialog opens — never on home-grid cold start.
    var details by remember(appItem.packageName) { mutableStateOf<PackageDetails?>(null) }

    LaunchedEffect(appItem.packageName) {
        details = withContext(Dispatchers.IO) {
            LauncherRepository.getPackageDetails(context, appItem.packageName)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(LauncherColors.CardBackground, shape = CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconEntry != null) {
                        Image(
                            bitmap = iconEntry.bitmap,
                            contentDescription = appItem.label,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (iconEntry.isAdaptive) Modifier.scale(1.5f) else Modifier
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = appItem.label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (appItem.isWorkProfile) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Work Profile",
                        color = LauncherColors.Green,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = appItem.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.LightGray,
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val versionText = details?.versionName ?: "…"
                    val sizeText = details?.formattedSize ?: "…"

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Version: $versionText",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                    ) {
                        Text(
                            text = "Size: $sizeText",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", appItem.packageName, null)
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("App Info", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onEditLayout()
                    },
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LauncherColors.Green),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = LauncherColors.Green
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Edit Layout", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hide App — removes from launcher; restorable via Settings > Hidden Apps
                OutlinedButton(
                    onClick = onHideApp,
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color(0xFFFF6B35)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF6B35)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Hide App", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
