package com.freetime.lumastore.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Gitlab
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

private const val SUPABASE_URL = "https://ndlaevedujqxhygbyxfh.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_HlppI4ILiXV7DZkpyrDEhQ_ytb2vV6g"
private const val DESKTOP_OAUTH_PORT = 49152

private val desktopSupabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_PUBLISHABLE_KEY,
) {
    install(Postgrest)
    install(Auth) {
        // Compose Desktop runs on the JVM. supabase-kt starts a local HTTP
        // callback server for OAuth. A fixed port keeps the redirect URL stable
        // so it can be allow-listed in Supabase Auth URL Configuration.
        httpCallbackConfig.httpPort = DESKTOP_OAUTH_PORT
        httpCallbackConfig.htmlTitle = "Luma Store"
        flowType = FlowType.PKCE
    }
}

@Serializable
private data class DesktopSubmissionSummary(
    val id: String,
    val name: String,
    val status: String,
    val version: String? = null,
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("review_message") val reviewMessage: String? = null,
)

@Serializable
private data class NewDesktopSubmission(
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    val category: String? = null,
    val categories: List<String> = emptyList(),
    val platform: String,
    val version: String,
    @SerialName("version_code") val versionCode: Long? = null,
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("download_url") val downloadUrl: String,
    val changelog: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    @SerialName("repo_url") val repoUrl: String? = null,
    @SerialName("license_type") val licenseType: String? = null,
    @SerialName("ant_features") val antiFeatures: List<String> = emptyList(),
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_email") val authorEmail: String? = null,
    @SerialName("author_website") val authorWebsite: String? = null,
    @SerialName("website_url") val websiteUrl: String? = null,
    @SerialName("source_code_url") val sourceCodeUrl: String? = null,
    @SerialName("issue_tracker_url") val issueTrackerUrl: String? = null,
    @SerialName("translation_url") val translationUrl: String? = null,
    @SerialName("changelog_url") val changelogUrl: String? = null,
    @SerialName("donate_url") val donateUrl: String? = null,
    val liberapay: String? = null,
    val opencollective: String? = null,
    val bitcoin: String? = null,
    val litecoin: String? = null,
    @SerialName("closed_source") val closedSource: Boolean = false,
    @SerialName("localized_metadata") val localizedMetadata: JsonArray = JsonArray(emptyList()),
    @SerialName("linux_package_base") val linuxPackageBase: String? = null,
    val link: String? = null,
    val status: String = "Pending",
)

private class DesktopDeveloperRepository {
    suspend fun currentUserLabel(): String? {
        desktopSupabase.auth.awaitInitialization()
        val session = desktopSupabase.auth.currentSessionOrNull() ?: return null
        return session.user?.email ?: session.user?.id
    }

    suspend fun signInWithGitHub() {
        desktopSupabase.auth.signInWith(Github)
    }

    suspend fun signInWithGitLab() {
        desktopSupabase.auth.signInWith(Gitlab)
    }

    suspend fun signOut() {
        desktopSupabase.auth.signOut()
    }

    suspend fun loadSubmissions(): List<DesktopSubmissionSummary> {
        val user = desktopSupabase.auth.currentSessionOrNull()?.user
            ?: return emptyList()
        return desktopSupabase.from("luma_submissions")
            .select(
                columns = Columns.list(
                    "id",
                    "name",
                    "status",
                    "version",
                    "package_name",
                    "review_message",
                )
            ) {
                filter { eq("user_id", user.id) }
                order("submitted_at", Order.DESCENDING)
            }
            .decodeList()
    }

    suspend fun submit(form: DeveloperSubmissionForm) {
        val user = desktopSupabase.auth.currentSessionOrNull()?.user
            ?: error("Sign in before submitting an app.")
        val localized = parseLocalizedMetadata(form.localizedMetadataJson)
        val categories = splitValues(form.categories)
        val payload = NewDesktopSubmission(
            userId = user.id,
            name = form.name.trim(),
            description = form.description.clean(),
            shortDescription = form.shortDescription.clean(),
            category = categories.firstOrNull(),
            categories = categories,
            platform = form.platform,
            version = form.version.trim(),
            versionCode = form.versionCode.trim().toLongOrNull(),
            packageName = form.packageName.clean(),
            downloadUrl = form.downloadUrl.trim(),
            changelog = form.changelog.clean(),
            iconUrl = form.iconUrl.clean(),
            screenshots = splitLines(form.screenshots),
            repoUrl = form.repoUrl.clean(),
            licenseType = form.licenseType.clean(),
            antiFeatures = splitValues(form.antiFeatures),
            authorName = form.authorName.clean(),
            authorEmail = form.authorEmail.clean(),
            authorWebsite = form.authorWebsite.clean(),
            websiteUrl = form.websiteUrl.clean(),
            sourceCodeUrl = form.sourceCodeUrl.clean(),
            issueTrackerUrl = form.issueTrackerUrl.clean(),
            translationUrl = form.translationUrl.clean(),
            changelogUrl = form.changelogUrl.clean(),
            donateUrl = form.donateUrl.clean(),
            liberapay = form.liberapay.clean(),
            opencollective = form.opencollective.clean(),
            bitcoin = form.bitcoin.clean(),
            litecoin = form.litecoin.clean(),
            closedSource = form.closedSource,
            localizedMetadata = localized,
            linuxPackageBase = form.linuxPackageBase.takeIf { form.platform == "Linux" },
            link = form.websiteUrl.clean() ?: form.repoUrl.clean(),
        )
        desktopSupabase.from("luma_submissions").insert(payload)
    }

    private fun parseLocalizedMetadata(raw: String): JsonArray {
        if (raw.isBlank()) return JsonArray(emptyList())
        val parsed: JsonElement = Json.parseToJsonElement(raw)
        return parsed as? JsonArray
            ?: error("Localized metadata must be a JSON array.")
    }

    private fun splitValues(raw: String): List<String> = raw
        .split(',', '\n')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    private fun splitLines(raw: String): List<String> = raw
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()

    private fun String.clean(): String? = trim().takeIf(String::isNotBlank)
}

private data class DeveloperSubmissionForm(
    val name: String = "",
    val shortDescription: String = "",
    val description: String = "",
    val platform: String = "Windows",
    val version: String = "",
    val versionCode: String = "",
    val packageName: String = "",
    val downloadUrl: String = "",
    val changelog: String = "",
    val categories: String = "",
    val licenseType: String = "",
    val iconUrl: String = "",
    val screenshots: String = "",
    val repoUrl: String = "",
    val antiFeatures: String = "",
    val authorName: String = "",
    val authorEmail: String = "",
    val authorWebsite: String = "",
    val websiteUrl: String = "",
    val sourceCodeUrl: String = "",
    val issueTrackerUrl: String = "",
    val translationUrl: String = "",
    val changelogUrl: String = "",
    val donateUrl: String = "",
    val liberapay: String = "",
    val opencollective: String = "",
    val bitcoin: String = "",
    val litecoin: String = "",
    val closedSource: Boolean = false,
    val localizedMetadataJson: String = "",
    val linuxPackageBase: String? = null,
)

@Composable
fun DesktopDeveloperScreen() {
    val repository = remember { DesktopDeveloperRepository() }
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<String?>(null) }
    var loadingAccount by remember { mutableStateOf(true) }
    var authenticating by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(DeveloperSubmissionForm()) }
    var submissions by remember { mutableStateOf(emptyList<DesktopSubmissionSummary>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    suspend fun refreshAccount() {
        account = repository.currentUserLabel()
        submissions = if (account != null) repository.loadSubmissions() else emptyList()
        loadingAccount = false
    }

    LaunchedEffect(Unit) {
        runCatching { refreshAccount() }
            .onFailure {
                error = it.message ?: "Could not load developer session."
                loadingAccount = false
            }
    }

    if (loadingAccount) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (account == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Developer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Sign in with the same account you use on the Luma Store developer website. The browser is only used for OAuth; the submission form stays in the desktop app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    scope.launch {
                        authenticating = true
                        error = null
                        runCatching {
                            repository.signInWithGitHub()
                            refreshAccount()
                        }.onFailure { error = it.message ?: "GitHub sign-in failed." }
                        authenticating = false
                    }
                },
                enabled = !authenticating,
            ) {
                Text(if (authenticating) "Signing in…" else "Sign in with GitHub")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        authenticating = true
                        error = null
                        runCatching {
                            repository.signInWithGitLab()
                            refreshAccount()
                        }.onFailure { error = it.message ?: "GitLab sign-in failed." }
                        authenticating = false
                    }
                },
                enabled = !authenticating,
            ) {
                Text("Sign in with GitLab")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Developer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(account.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    scope.launch {
                        repository.signOut()
                        account = null
                        submissions = emptyList()
                    }
                }) {
                    Text("Sign out")
                }
            }
        }

        item {
            Text("Submit an app", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "This creates the same luma_submissions review item as the developer website.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionTitle("App") }
        item {
            FormField("App name *", form.name) { form = form.copy(name = it) }
        }
        item {
            FormField("Short description", form.shortDescription) { form = form.copy(shortDescription = it) }
        }
        item {
            FormField("Description", form.description, singleLine = false) { form = form.copy(description = it) }
        }
        item {
            Text("Platform *", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Android", "Windows", "Linux").forEach { platform ->
                    FilterChip(
                        selected = form.platform == platform,
                        onClick = {
                            form = form.copy(
                                platform = platform,
                                linuxPackageBase = if (platform == "Linux") form.linuxPackageBase else null,
                            )
                        },
                        label = { Text(platform) },
                    )
                }
            }
        }
        if (form.platform == "Linux") {
            item {
                Text("Linux package base *", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Debian-based", "RPM-based").forEach { base ->
                        FilterChip(
                            selected = form.linuxPackageBase == base,
                            onClick = { form = form.copy(linuxPackageBase = base) },
                            label = { Text(base) },
                        )
                    }
                }
            }
        }
        item { FormField("Version *", form.version) { form = form.copy(version = it) } }
        if (form.platform == "Android") {
            item { FormField("Package name *", form.packageName) { form = form.copy(packageName = it) } }
            item { FormField("Version code *", form.versionCode) { form = form.copy(versionCode = it) } }
        }
        item { FormField("Download URL *", form.downloadUrl) { form = form.copy(downloadUrl = it) } }
        item { FormField("Changelog", form.changelog, singleLine = false) { form = form.copy(changelog = it) } }
        item { FormField("Categories (comma separated)", form.categories) { form = form.copy(categories = it) } }
        item { FormField("License", form.licenseType) { form = form.copy(licenseType = it) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = form.closedSource,
                    onCheckedChange = { form = form.copy(closedSource = it) },
                )
                Text("Closed source")
            }
        }

        item { SectionTitle("Media") }
        item { FormField("Icon URL", form.iconUrl) { form = form.copy(iconUrl = it) } }
        item {
            FormField("Screenshot URLs (one per line)", form.screenshots, singleLine = false) {
                form = form.copy(screenshots = it)
            }
        }

        item { SectionTitle("Project links") }
        item { FormField("Repository URL", form.repoUrl) { form = form.copy(repoUrl = it) } }
        item { FormField("Website URL", form.websiteUrl) { form = form.copy(websiteUrl = it) } }
        item { FormField("Source code URL", form.sourceCodeUrl) { form = form.copy(sourceCodeUrl = it) } }
        item { FormField("Issue tracker URL", form.issueTrackerUrl) { form = form.copy(issueTrackerUrl = it) } }
        item { FormField("Translation URL", form.translationUrl) { form = form.copy(translationUrl = it) } }
        item { FormField("Changelog URL", form.changelogUrl) { form = form.copy(changelogUrl = it) } }

        item { SectionTitle("Author") }
        item { FormField("Author name", form.authorName) { form = form.copy(authorName = it) } }
        item { FormField("Author email", form.authorEmail) { form = form.copy(authorEmail = it) } }
        item { FormField("Author website", form.authorWebsite) { form = form.copy(authorWebsite = it) } }

        item { SectionTitle("Funding & metadata") }
        item { FormField("Donate URL", form.donateUrl) { form = form.copy(donateUrl = it) } }
        item { FormField("Liberapay", form.liberapay) { form = form.copy(liberapay = it) } }
        item { FormField("OpenCollective", form.opencollective) { form = form.copy(opencollective = it) } }
        item { FormField("Bitcoin", form.bitcoin) { form = form.copy(bitcoin = it) } }
        item { FormField("Litecoin", form.litecoin) { form = form.copy(litecoin = it) } }
        item { FormField("Anti-features (comma separated)", form.antiFeatures) { form = form.copy(antiFeatures = it) } }
        item {
            FormField(
                "Localized metadata (optional JSON array)",
                form.localizedMetadataJson,
                singleLine = false,
            ) { form = form.copy(localizedMetadataJson = it) }
        }

        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        success?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }

        item {
            Button(
                onClick = {
                    scope.launch {
                        submitting = true
                        error = null
                        success = null
                        val validation = validateSubmission(form)
                        if (validation != null) {
                            error = validation
                            submitting = false
                            return@launch
                        }
                        runCatching {
                            repository.submit(form)
                            submissions = repository.loadSubmissions()
                        }.onSuccess {
                            success = "App submitted for review."
                            form = DeveloperSubmissionForm()
                        }.onFailure {
                            error = it.message ?: "Could not submit app."
                        }
                        submitting = false
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (submitting) "Submitting…" else "Submit app for review")
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("My submissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        if (submissions.isEmpty()) {
            item { Text("No submissions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(submissions, key = { it.id }) { submission ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(submission.name, fontWeight = FontWeight.SemiBold)
                        Text("Status: ${submission.status}")
                        submission.version?.let { Text("Version: $it", style = MaterialTheme.typography.bodySmall) }
                        submission.packageName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        submission.reviewMessage?.let {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("Review feedback", fontWeight = FontWeight.Medium)
                            Text(it)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

private fun validateSubmission(form: DeveloperSubmissionForm): String? {
    if (form.name.isBlank()) return "App name is required."
    if (form.version.isBlank()) return "Version is required."
    if (form.downloadUrl.isBlank()) return "Download URL is required."
    if (!form.downloadUrl.startsWith("https://") && !form.downloadUrl.startsWith("http://")) {
        return "Download URL must start with https:// or http://."
    }
    if (form.platform == "Android") {
        if (form.packageName.isBlank()) return "Android package name is required."
        val versionCode = form.versionCode.toLongOrNull()
        if (versionCode == null || versionCode <= 0) return "Android version code must be a positive number."
    }
    if (form.platform == "Linux" && form.linuxPackageBase == null) {
        return "Choose Debian-based or RPM-based for Linux."
    }
    return null
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FormField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
    )
}
