package com.freetime.lumastore

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freetime.lumastore.data.AppRepository
import com.freetime.lumastore.data.DeveloperRepository
import com.freetime.lumastore.data.supabase
import com.freetime.lumastore.install.ApkInstaller
import com.freetime.lumastore.notifications.NotificationSyncJobService
import com.freetime.lumastore.notifications.SystemNotificationManager
import com.freetime.lumastore.ui.theme.LumaStoreTheme
import io.github.jan.supabase.auth.handleDeeplinks

private enum class MainScreen { STORE, DEVELOPER, SETTINGS }

class MainActivity : ComponentActivity() {
    private val repository by lazy { AppRepository(applicationContext) }
    private val developerRepository by lazy { DeveloperRepository() }
    private val installedAppsRevision = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supabase.handleDeeplinks(intent)
        scheduleNotificationSync()
        enableEdgeToEdge()
        setContent {
            val revision = installedAppsRevision.intValue
            var screen by rememberSaveable {
                mutableStateOf(if (intent.getBooleanExtra(SystemNotificationManager.EXTRA_OPEN_DEVELOPER, false)) MainScreen.DEVELOPER else MainScreen.STORE)
            }
            LumaStoreTheme {
                when (screen) {
                    MainScreen.SETTINGS -> SettingsScreen(repository, { screen = MainScreen.STORE }, { })
                    MainScreen.DEVELOPER -> DeveloperScreen(developerRepository) { screen = MainScreen.STORE }
                    MainScreen.STORE -> Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { screen = MainScreen.DEVELOPER }) { Text(stringResource(R.string.developer)) }
                            TextButton(onClick = { screen = MainScreen.SETTINGS }) { Text(stringResource(R.string.settings)) }
                        }
                        Box(Modifier.weight(1f)) {
                            StoreScreen(
                                repository = repository,
                                installedAppsRevision = revision,
                                installedVersionCode = { installedVersionCode(it) },
                                installedVersionName = { installedVersionName(it) },
                                openInstalledApp = { openInstalledApp(it) },
                                canInstallPackages = { canInstallUnknownApps() },
                                requestInstallPermission = { openInstallPermission() },
                                install = { app, onProgress, onReady, onError ->
                                    ApkInstaller.downloadAndInstall(this@MainActivity, app.id, app.apkUrl,
                                        { runOnUiThread { onProgress(it) } },
                                        { runOnUiThread(onReady) },
                                        { error -> runOnUiThread { onError(error) } })
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); supabase.handleDeeplinks(intent) }
    override fun onResume() { super.onResume(); installedAppsRevision.intValue++ }

    private fun scheduleNotificationSync() {
        val scheduler = getSystemService(JobScheduler::class.java)
        val component = ComponentName(this, NotificationSyncJobService::class.java)
        scheduler.schedule(JobInfo.Builder(NOTIFICATION_SYNC_JOB_ID, component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15 * 60 * 1000L).build())
    }

    private fun installedVersionCode(packageName: String): Long? = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else { @Suppress("DEPRECATION") info.versionCode.toLong() }
    }.getOrNull()
    private fun installedVersionName(packageName: String): String? = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
    private fun openInstalledApp(packageName: String): Boolean { val i = packageManager.getLaunchIntentForPackage(packageName) ?: return false; i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true }
    private fun canInstallUnknownApps() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
    private fun openInstallPermission() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }

    companion object { private const val NOTIFICATION_SYNC_JOB_ID = 4201 }
}
