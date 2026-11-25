package com.vantra.music.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.vantra.music.R
import com.vantra.music.island.IslandRuntime
import com.vantra.music.island.core.IslandBus
import com.vantra.music.island.data.SettingsRepo
import com.vantra.music.island.service.NotifListener
import com.vantra.music.island.ui.DynamicIslandActivity
import com.vantra.music.island.ui.Perms
import com.vantra.music.island.ui.Screen
import com.vantra.music.ui.component.SubSettingsScaffold
import com.vantra.music.ui.screens.audiolab.LabCard
import com.vantra.music.ui.screens.audiolab.LabColors
import com.vantra.music.ui.screens.audiolab.LabToggleRow

private val Granted = Color(0xFF8FD98B)

private fun hasRuntime(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun DynamicIslandSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settings by SettingsRepo.state.collectAsState()
    val running by IslandBus.serviceRunning.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val accessibility = remember(tick) { Perms.accessibility(context) }
    val listener = remember(tick) { Perms.notifications(context) }
    val battery = remember(tick) { Perms.batteryUnrestricted(context) }
    val calls = remember(tick) { Perms.calls(context) }
    val bluetooth = remember(tick) { Perms.bluetooth(context) }
    val listenerConnected = remember(tick) { NotifListener.isConnected }
    val post = remember(tick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasRuntime(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    val askPost = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val askCalls = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_dynamic_island_category),
        onBack = { navController.navigateUp() },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!IslandRuntime.supported) {
                Text(
                    stringResource(R.string.dynamic_island_unsupported),
                    color = LabColors.Secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                return@Column
            }

            Text(
                stringResource(R.string.dynamic_island_intro),
                color = LabColors.Secondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            LabCard {
                LabToggleRow(
                    title = stringResource(R.string.dynamic_island_enable),
                    subtitle = stringResource(R.string.dynamic_island_enable_sub),
                    checked = settings.enabled,
                    onCheckedChange = { on -> SettingsRepo.update { copy(enabled = on) } },
                )
                Text(
                    text = stringResource(
                        when {
                            !settings.enabled -> R.string.dynamic_island_status_off
                            running -> R.string.dynamic_island_status_running
                            else -> R.string.dynamic_island_status_needs_permission
                        },
                    ),
                    color = if (settings.enabled && running) Granted else LabColors.Secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }

            SectionLabel(stringResource(R.string.dynamic_island_permissions))
            LabCard {
                PermissionRow(
                    stringResource(R.string.dynamic_island_perm_accessibility),
                    stringResource(R.string.dynamic_island_perm_accessibility_sub),
                    accessibility,
                ) { Perms.openAccessibility(context) }
                PermissionRow(
                    stringResource(R.string.dynamic_island_perm_listener),
                    stringResource(R.string.dynamic_island_perm_listener_sub),
                    listener,
                ) { Perms.openNotifications(context) }
                PermissionRow(
                    stringResource(R.string.dynamic_island_perm_post),
                    stringResource(R.string.dynamic_island_perm_post_sub),
                    post,
                ) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askPost.launch(Manifest.permission.POST_NOTIFICATIONS) }
                PermissionRow(
                    stringResource(R.string.dynamic_island_perm_battery),
                    stringResource(R.string.dynamic_island_perm_battery_sub),
                    battery,
                ) { Perms.openBattery(context) }
                PermissionRow(
                    stringResource(R.string.dynamic_island_perm_calls),
                    stringResource(R.string.dynamic_island_perm_calls_sub),
                    calls,
                ) { askCalls.launch(Perms.CALL_PERMISSIONS) }
                PermissionRow(
                    stringResource(R.string.dynamic_island_perm_bluetooth),
                    stringResource(R.string.dynamic_island_perm_bluetooth_sub),
                    bluetooth,
                ) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            }

            SectionLabel(stringResource(R.string.dynamic_island_notifications))
            LabCard {
                LabToggleRow(
                    title = stringResource(R.string.dynamic_island_notif_apps),
                    subtitle = stringResource(R.string.dynamic_island_notif_apps_sub),
                    checked = settings.cardMessages,
                    onCheckedChange = { on -> SettingsRepo.update { copy(cardMessages = on) } },
                )
                LabToggleRow(
                    title = stringResource(R.string.dynamic_island_notif_quiet),
                    subtitle = stringResource(R.string.dynamic_island_notif_quiet_sub),
                    checked = settings.msgQuiet,
                    onCheckedChange = { on -> SettingsRepo.update { copy(msgQuiet = on) } },
                )
                Text(
                    text = stringResource(
                        if (listenerConnected) R.string.dynamic_island_notif_connected else R.string.dynamic_island_notif_disconnected,
                    ),
                    color = if (listenerConnected) Granted else LabColors.Secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
                if (listener && !listenerConnected) {
                    LinkRow(
                        stringResource(R.string.dynamic_island_notif_reconnect),
                        stringResource(R.string.dynamic_island_notif_reconnect_sub),
                    ) {
                        NotifListener.requestReconnect(context)
                        tick++
                    }
                }
                if (!listener) {
                    LinkRow(
                        stringResource(R.string.dynamic_island_restricted),
                        stringResource(R.string.dynamic_island_restricted_sub),
                    ) { Perms.openAppDetails(context) }
                }
                LinkRow(
                    stringResource(R.string.dynamic_island_notif_choose),
                    stringResource(R.string.dynamic_island_notif_choose_sub),
                ) { context.startActivity(DynamicIslandActivity.intent(context, Screen.NOTIF_APPS)) }
                LinkRow(
                    stringResource(R.string.dynamic_island_notif_diag),
                    stringResource(R.string.dynamic_island_notif_diag_sub),
                ) { context.startActivity(DynamicIslandActivity.intent(context, Screen.NOTIF_DIAG)) }
            }

            SectionLabel(stringResource(R.string.dynamic_island_more))
            LabCard {
                LinkRow(
                    stringResource(R.string.dynamic_island_all_permissions),
                    stringResource(R.string.dynamic_island_all_permissions_sub),
                ) { context.startActivity(DynamicIslandActivity.intent(context, Screen.PERMISSIONS)) }
                LinkRow(
                    stringResource(R.string.dynamic_island_full_settings),
                    stringResource(R.string.dynamic_island_full_settings_sub),
                ) { context.startActivity(DynamicIslandActivity.intent(context)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LabColors.Secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
    )
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = LabColors.Primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = LabColors.Secondary, fontSize = 13.sp)
        }
        if (granted) {
            Text(
                stringResource(R.string.dynamic_island_granted),
                color = Granted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp),
            )
        } else {
            Text(
                stringResource(R.string.dynamic_island_grant),
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Text(title, color = LabColors.Primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = LabColors.Secondary, fontSize = 13.sp)
    }
}
