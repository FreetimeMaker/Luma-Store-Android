package com.freetime.lumastore

import com.freetime.design.liquidGlassCapsule
import com.freetime.design.LiquidGlassRoot
import com.freetime.warn.FreetimeWarn
import com.freetime.warn.FreetimeWarnFrequency
import com.freetime.warn.rememberFreetimeWarnState

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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
        handleFdroidRepositoryLink(intent)
        scheduleNotificationSyncSafely()
        enableEdgeToEdge()

        setContent {
            val revision = installedAppsRevision.intValue
            val currentSourcesRevision = sourcesRevision.intValue
            val deepLinkedAppId = deepLinkedAppId(intent.data)
            var screen by rememberSaveable {
                mutableStateOf(
                    if (intent.getBooleanExtra(SystemNotificationManager.EXTRA_OPEN_DEVELOPER, false)) {
                        MainScreen.DEVELOPER
                    } else if (deepLinkedAppId(intent.data) != null) {
                        MainScreen.SEARCH
                    } else if (isFdroidRepositoryLink(intent.data)) {
                        MainScreen.SOURCES
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

            // Automatic day/night theme without weather or location data.
            // Light from 07:00 to 19:00, dark for the rest of the day.
            var currentHour by remember { mutableIntStateOf(java.time.LocalTime.now().hour) }
            LaunchedEffect(Unit) {
                while (true) {
                    currentHour = java.time.LocalTime.now().hour
                    kotlinx.coroutines.delay(60_000)
                }
            }
            val darkTheme = currentHour < 7 || currentHour >= 19
            val oledMode = remember(currentSourcesRevision) { repository.oledModeEnabled() }
            val availableUpdateCount = remember(revision, currentSourcesRevision) {
                repository.currentApps().groupBy { it.id }.count { (packageName, variants) ->
                    val installed = installedVersionCode(packageName)
                    installed != null && (repository.preferredVariant(packageName, variants, installed)?.versionCode ?: installed) > installed
                }
            }

            LumaStoreTheme(darkTheme = darkTheme, oledMode = oledMode) {
                val warnState = rememberFreetimeWarnState(
                    context = this@MainActivity,
                    appName = getString(R.string.app_name),
                    versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode,
                    frequency = FreetimeWarnFrequency.ONCE_PER_VERSION
                )
                LiquidGlassRoot {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                val wideWindow = maxWidth >= 840.dp
                val navigationHorizontalPadding = 18.dp
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().then(
                            if (wideWindow) Modifier.padding(start = 104.dp) else Modifier
                        )
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
                                        initialAppId = deepLinkedAppId,
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
                                        installedPackageNames = { installedPackageNames() },
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

                    NavigationBar(
                        modifier = Modifier
                            .align(if (wideWindow) androidx.compose.ui.Alignment.CenterStart else androidx.compose.ui.Alignment.BottomCenter)
                            .then(if (wideWindow) Modifier.width(88.dp).padding(start = 12.dp) else Modifier.fillMaxWidth())

                            .padding(horizontal = navigationHorizontalPadding)
                            .navigationBarsPadding()
                            .padding(bottom = 14.dp)
                            .heightIn(min = 68.dp)
                            .liquidGlassCapsule(interactive = false),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        FdroidNavigationItem(screen == MainScreen.DISCOVER, { screen = MainScreen.DISCOVER }, stringResource(R.string.discover)) { Icon(Icons.Filled.Explore, contentDescription = stringResource(R.string.discover)) }
                        FdroidNavigationItem(screen == MainScreen.SEARCH, { screen = MainScreen.SEARCH }, stringResource(R.string.search)) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search)) }
                        FdroidNavigationItem(screen == MainScreen.MY_APPS, { screen = MainScreen.MY_APPS }, stringResource(R.string.my_apps), availableUpdateCount) { Icon(Icons.Filled.Apps, contentDescription = stringResource(R.string.my_apps)) }
                        FdroidNavigationItem(screen == MainScreen.SOURCES, { screen = MainScreen.SOURCES }, stringResource(R.string.sources)) { Icon(Icons.Filled.Storage, contentDescription = stringResource(R.string.sources)) }
                        FdroidNavigationItem(screen == MainScreen.DEVELOPER, { screen = MainScreen.DEVELOPER }, stringResource(R.string.developer)) { Icon(Icons.Filled.Code, contentDescription = stringResource(R.string.developer)) }
                    }
                }
                }
                FreetimeWarn(state = warnState)
                }
            }
        }
    }

    @Composable
    private fun RowScope.FdroidNavigationItem(
        selected: Boolean,
        onClick: () -> Unit,
        label: String,
        badgeCount: Int = 0,
        icon: @Composable () -> Unit
    ) {
        NavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = {
                if (badgeCount > 0) {
                    BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) { icon() }
                } else icon()
            },
            label = {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            alwaysShowLabel = false,
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
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
            color = Color.Transparent
        ) {
            content()
        }
    }

    private fun installerCallback(): (StoreApp, (Int) -> Unit, () -> Unit, (Throwable) -> Unit) -> ApkInstaller.DownloadHandle =
        { app, onProgress, onReady, onError ->
            ApkInstaller.downloadAndInstall(
                this@MainActivity,
                app.id,
                app.apkUrl,
                app.expectedSha256,
                { runOnUiThread { onProgress(it) } },
                { runOnUiThread(onReady) },
                { error -> runOnUiThread { onError(error) } }
            )
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSupabaseDeepLinkSafely(intent)
        if (handleFdroidRepositoryLink(intent)) {
            recreate()
        }
    }

    private fun deepLinkedAppId(uri: Uri?): String? {
        uri ?: return null
        if (uri.scheme.equals("lumastore", true) && uri.host.equals("app", true)) {
            return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: uri.getQueryParameter("id")?.takeIf { it.isNotBlank() }
        }
        if ((uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            uri.host.equals("luma.free-time.me", true)) {
            return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun isFdroidRepositoryLink(uri: Uri?): Boolean =
        uri?.scheme.equals("fdroidrepo", ignoreCase = true) ||
            uri?.scheme.equals("fdroidrepos", ignoreCase = true)

    private fun handleFdroidRepositoryLink(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (!isFdroidRepositoryLink(uri)) return false
        repository.importRepository(uri.toString())
            .onSuccess {
                repository.setSourceEnabled(it, true)
                sourcesRevision.intValue++
            }
        return true
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

    private fun installedPackageNames(): Set<String> =
        packageManager.getInstalledPackages(0).mapTo(mutableSetOf()) { it.packageName }

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
