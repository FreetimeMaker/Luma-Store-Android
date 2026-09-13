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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import com.freetime.lumastore.data.AppRepository
import com.freetime.lumastore.data.DeveloperRepository
import com.freetime.lumastore.data.StoreApp
import com.freetime.lumastore.data.supabase
import com.freetime.lumastore.install.ApkInstaller
import com.freetime.lumastore.notifications.NotificationSyncJobService
import com.freetime.lumastore.notifications.SystemNotificationManager
import com.freetime.lumastore.ui.theme.LumaStoreTheme
import io.github.jan.supabase.auth.handleDeeplinks

private enum class MainScreen { MY_APPS, DEVELOPER, SEARCH }

class MainActivity : ComponentActivity() {
    private val repository by lazy { AppRepository(applicationContext) }
    private val developerRepository by lazy { DeveloperRepository() }
    private val installedAppsRevision = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSupabaseDeepLinkSafely(intent)
        scheduleNotificationSyncSafely()
        enableEdgeToEdge()

        setContent {
            val revision = installedAppsRevision.intValue
            var screen by rememberSaveable {
                mutableStateOf(
                    if (intent.getBooleanExtra(SystemNotificationManager.EXTRA_OPEN_DEVELOPER, false)) {
                        MainScreen.DEVELOPER
                    } else {
                        MainScreen.SEARCH
                    }
                )
            }
            var myAppsMounted by rememberSaveable { mutableStateOf(screen == MainScreen.MY_APPS) }
            var developerMounted by rememberSaveable { mutableStateOf(screen == MainScreen.DEVELOPER) }

            LaunchedEffect(screen) {
                when (screen) {
                    MainScreen.MY_APPS -> myAppsMounted = true
                    MainScreen.DEVELOPER -> developerMounted = true
                    MainScreen.SEARCH -> Unit
                }
            }

            LumaStoreTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = screen == MainScreen.MY_APPS,
                                onClick = { screen = MainScreen.MY_APPS },
                                icon = { Text("↓") },
                                label = { Text(stringResource(R.string.my_apps)) }
                            )
                            NavigationBarItem(
                                selected = screen == MainScreen.DEVELOPER,
                                onClick = { screen = MainScreen.DEVELOPER },
                                icon = { Text("</>") },
                                label = { Text(stringResource(R.string.developer)) }
                            )
                            NavigationBarItem(
                                selected = screen == MainScreen.SEARCH,
                                onClick = { screen = MainScreen.SEARCH },
                                icon = { Text("⌕") },
                                label = { Text(stringResource(R.string.search)) }
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        PersistentScreen(visible = screen == MainScreen.SEARCH) {
                            FdroidStoreScreen(
                                repository = repository,
                                installedAppsRevision = revision,
                                installedVersionCode = { installedVersionCode(it) },
                                installedVersionName = { installedVersionName(it) },
                                openInstalledApp = { openInstalledApp(it) },
                                canInstallPackages = { canInstallUnknownApps() },
                                requestInstallPermission = { openInstallPermission() },
                                install = installerCallback()
                            )
                        }

                        if (myAppsMounted) {
                            PersistentScreen(visible = screen == MainScreen.MY_APPS) {
                                MyAppsScreen(
                                    repository = repository,
                                    installedAppsRevision = revision,
                                    installedVersionCode = { installedVersionCode(it) },
                                    installedVersionName = { installedVersionName(it) },
                                    openInstalledApp = { openInstalledApp(it) },
                                    canInstallPackages = { canInstallUnknownApps() },
                                    requestInstallPermission = { openInstallPermission() },
                                    install = installerCallback()
                                )
                            }
                        }

                        if (developerMounted) {
                            PersistentScreen(visible = screen == MainScreen.DEVELOPER) {
                                DeveloperScreen(
                                    repository = developerRepository,
                                    onBack = { screen = MainScreen.SEARCH },
                                    active = screen == MainScreen.DEVELOPER
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PersistentScreen(
        visible: Boolean,
        content: @Composable () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (visible) 1f else 0f)
                .graphicsLayer { alpha = if (visible) 1f else 0f },
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }

    private fun installerCallback(): (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> Unit =
        { app, onProgress, onReady, onError ->
            ApkInstaller.downloadAndInstall(
                this@MainActivity,
                app.id,
                app.apkUrl,
                { runOnUiThread { onProgress(it) } },
                { runOnUiThread(onReady) },
                { error -> runOnUiThread { onError(error) } }
            )
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSupabaseDeepLinkSafely(intent)
    }

    override fun onResume() {
        super.onResume()
        installedAppsRevision.intValue++
    }

    private fun handleSupabaseDeepLinkSafely(intent: Intent) {
        if (intent.data == null) return
        runCatching { supabase.handleDeeplinks(intent) }
    }

    private fun scheduleNotificationSyncSafely() {
        runCatching {
            val scheduler = getSystemService(JobScheduler::class.java)
            val component = ComponentName(this, NotificationSyncJobService::class.java)
            scheduler.schedule(
                JobInfo.Builder(NOTIFICATION_SYNC_JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * 60 * 1000L)
                    .build()
            )
        }
    }

    private fun installedVersionCode(packageName: String): Long? = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrNull()

    private fun installedVersionName(packageName: String): String? = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    private fun openInstalledApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return true
    }

    private fun canInstallUnknownApps(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun openInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    companion object {
        private const val NOTIFICATION_SYNC_JOB_ID = 4201
    }
}
