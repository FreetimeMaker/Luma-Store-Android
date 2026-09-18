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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.freetime.lumastore.data.AppRepository
import com.freetime.lumastore.data.DeveloperRepository
import com.freetime.lumastore.data.StoreApp
import com.freetime.lumastore.data.supabase
import com.freetime.lumastore.install.ApkInstaller
import com.freetime.lumastore.notifications.NotificationSyncJobService
import com.freetime.lumastore.notifications.SystemNotificationManager
import com.freetime.lumastore.ui.glass.lumaBackdropSource
import com.freetime.lumastore.ui.glass.lumaLiquidGlass
import com.freetime.lumastore.ui.glass.rememberLumaBackdrop
import com.freetime.lumastore.ui.theme.LumaStoreTheme
import io.github.jan.supabase.auth.handleDeeplinks

private enum class MainScreen { DISCOVER, SEARCH, MY_APPS, SOURCES, DEVELOPER }

class MainActivity : ComponentActivity() {
    private val repository by lazy { AppRepository(applicationContext) }
    private val developerRepository by lazy { DeveloperRepository(applicationContext) }
    private val installedAppsRevision = mutableIntStateOf(0)
    private val sourcesRevision = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSupabaseDeepLinkSafely(intent)
        scheduleNotificationSyncSafely()
        enableEdgeToEdge()

        setContent {
            val revision = installedAppsRevision.intValue
            val currentSourcesRevision = sourcesRevision.intValue
            var screen by rememberSaveable {
                mutableStateOf(
                    if (intent.getBooleanExtra(SystemNotificationManager.EXTRA_OPEN_DEVELOPER, false)) {
                        MainScreen.DEVELOPER
                    } else {
                        MainScreen.DISCOVER
                    }
                )
            }
            var searchMounted by rememberSaveable { mutableStateOf(screen == MainScreen.SEARCH) }
            var myAppsMounted by rememberSaveable { mutableStateOf(screen == MainScreen.MY_APPS) }
            var sourcesMounted by rememberSaveable { mutableStateOf(screen == MainScreen.SOURCES) }
            var developerMounted by rememberSaveable { mutableStateOf(screen == MainScreen.DEVELOPER) }

            LaunchedEffect(screen) {
                when (screen) {
                    MainScreen.SEARCH -> searchMounted = true
                    MainScreen.MY_APPS -> myAppsMounted = true
                    MainScreen.SOURCES -> sourcesMounted = true
                    MainScreen.DEVELOPER -> developerMounted = true
                    MainScreen.DISCOVER -> Unit
                }
            }

            LumaStoreTheme {
                val backdrop = rememberLumaBackdrop()
                val navigationShape = RoundedCornerShape(32.dp)

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .lumaLiquidGlass(
                                    backdrop = backdrop,
                                    shape = navigationShape,
                                    interactive = true
                                ),
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            FdroidNavigationItem(
                                selected = screen == MainScreen.DISCOVER,
                                onClick = { screen = MainScreen.DISCOVER },
                                label = stringResource(R.string.discover),
                                icon = {
                                    Icon(
                                        Icons.Filled.Explore,
                                        contentDescription = stringResource(R.string.discover)
                                    )
                                }
                            )
                            FdroidNavigationItem(
                                selected = screen == MainScreen.SEARCH,
                                onClick = { screen = MainScreen.SEARCH },
                                label = stringResource(R.string.search),
                                icon = {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.search)
                                    )
                                }
                            )
                            FdroidNavigationItem(
                                selected = screen == MainScreen.MY_APPS,
                                onClick = { screen = MainScreen.MY_APPS },
                                label = stringResource(R.string.my_apps),
                                icon = {
                                    Icon(
                                        Icons.Filled.Apps,
                                        contentDescription = stringResource(R.string.my_apps)
                                    )
                                }
                            )
                            FdroidNavigationItem(
                                selected = screen == MainScreen.SOURCES,
                                onClick = { screen = MainScreen.SOURCES },
                                label = stringResource(R.string.sources),
                                icon = {
                                    Icon(
                                        Icons.Filled.Storage,
                                        contentDescription = stringResource(R.string.sources)
                                    )
                                }
                            )
                            FdroidNavigationItem(
                                selected = screen == MainScreen.DEVELOPER,
                                onClick = { screen = MainScreen.DEVELOPER },
                                label = stringResource(R.string.developer),
                                icon = {
                                    Icon(
                                        Icons.Filled.Code,
                                        contentDescription = stringResource(R.string.developer)
                                    )
                                }
                            )
                        }
                    }
                ) { _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .lumaBackdropSource(backdrop)
                    ) {
                        PersistentScreen(visible = screen == MainScreen.DISCOVER) {
                            key(currentSourcesRevision) {
                                FdroidDiscoverScreen(
                                    repository = repository,
                                    installedVersionCode = { installedVersionCode(it) },
                                    installedVersionName = { installedVersionName(it) },
                                    openInstalledApp = { openInstalledApp(it) },
                                    canInstallPackages = { canInstallUnknownApps() },
                                    requestInstallPermission = { openInstallPermission() },
                                    install = installerCallback()
                                )
                            }
                        }

                        if (searchMounted) {
                            PersistentScreen(visible = screen == MainScreen.SEARCH) {
                                key(currentSourcesRevision) {
                                    FdroidSearchScreen(
                                        repository = repository,
                                        installedVersionCode = { installedVersionCode(it) },
                                        installedVersionName = { installedVersionName(it) },
                                        openInstalledApp = { openInstalledApp(it) },
                                        canInstallPackages = { canInstallUnknownApps() },
                                        requestInstallPermission = { openInstallPermission() },
                                        install = installerCallback()
                                    )
                                }
                            }
                        }

                        if (myAppsMounted) {
                            PersistentScreen(visible = screen == MainScreen.MY_APPS) {
                                key(currentSourcesRevision) {
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
                        }

                        if (sourcesMounted) {
                            PersistentScreen(visible = screen == MainScreen.SOURCES) {
                                SettingsScreen(
                                    repository = repository,
                                    onBack = { screen = MainScreen.DISCOVER },
                                    onSourcesChanged = { sourcesRevision.intValue++ }
                                )
                            }
                        }

                        if (developerMounted) {
                            PersistentScreen(visible = screen == MainScreen.DEVELOPER) {
                                DeveloperScreen(
                                    repository = developerRepository,
                                    onBack = { screen = MainScreen.DISCOVER },
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
    private fun RowScope.FdroidNavigationItem(
        selected: Boolean,
        onClick: () -> Unit,
        label: String,
        icon: @Composable () -> Unit
    ) {
        NavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = icon,
            label = { Text(label) },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                selectedTextColor = MaterialTheme.colorScheme.primary,
                selectedIconColor = MaterialTheme.colorScheme.primary
            )
        )
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
