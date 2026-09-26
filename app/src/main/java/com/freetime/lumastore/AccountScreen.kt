package com.freetime.lumastore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.freetime.lumastore.data.DeveloperRepository
import com.freetime.lumastore.data.DeveloperSession
import com.freetime.lumastore.data.AccountRating
import com.freetime.lumastore.data.LumaStoreApi
import com.freetime.lumastore.data.FavoriteApp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(repository: DeveloperRepository, onOpenApp: (String) -> Unit = {}) {
    var session by remember { mutableStateOf<DeveloperSession?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reviews by remember { mutableStateOf<List<AccountRating>>(emptyList()) }
    var editingReview by remember { mutableStateOf<AccountRating?>(null) }
    var editText by remember { mutableStateOf("") }
    var editRating by remember { mutableStateOf(0) }
    var reviewPendingDelete by remember { mutableStateOf<AccountRating?>(null) }
    var favoritePendingDelete by remember { mutableStateOf<FavoriteApp?>(null) }
    var favorites by remember { mutableStateOf<List<FavoriteApp>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { repository.savedSession() }
            .onSuccess {
                session = it
                if (it != null) {
                    coroutineScope {
                        val ratingsRequest = async { runCatching { LumaStoreApi.myRatings() } }
                        val favoritesRequest = async { runCatching { LumaStoreApi.myFavorites() } }
                        ratingsRequest.await().onSuccess { ratings -> reviews = ratings }.onFailure { failure -> error = failure.message }
                        favoritesRequest.await().onSuccess { saved -> favorites = saved }.onFailure { failure -> error = failure.message }
                    }
                }
            }
            .onFailure { error = it.message }
        loading = false
        repository.sessionFlow().collect {
            session = it
            if (it != null) {
                coroutineScope {
                    val ratingsRequest = async { runCatching { LumaStoreApi.myRatings() } }
                    val favoritesRequest = async { runCatching { LumaStoreApi.myFavorites() } }
                    ratingsRequest.await().onSuccess { ratings -> reviews = ratings }.onFailure { failure -> error = failure.message }
                    favoritesRequest.await().onSuccess { saved -> favorites = saved }.onFailure { failure -> error = failure.message }
                }
            } else {
                reviews = emptyList()
                favorites = emptyList()
            }
            loading = false
            working = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.account), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (session == null) {
            Text(stringResource(R.string.account_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (session == null) {
            Button(
                onClick = {
                    scope.launch {
                        working = true
                        error = null
                        runCatching { repository.signInWithGoogle() }
                            .onFailure {
                                error = it.message ?: "Google sign in failed."
                                working = false
                            }
                    }
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sign_in_google)) }
        } else {
            Text(stringResource(R.string.signed_in_as), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(session?.email ?: session?.userId.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = {
                    scope.launch {
                        working = true
                        error = null
                        runCatching { repository.signOut() }
                            .onFailure {
                                error = it.message ?: "Sign out failed."
                                working = false
                            }
                    }
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sign_out)) }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.favorites_count, favorites.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (favorites.isEmpty()) {
                Text(stringResource(R.string.no_favorites), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            favorites.forEach { favorite ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = favorite.iconUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(favorite.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            favorite.packageName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        TextButton(onClick = { onOpenApp(favorite.packageName ?: favorite.appId) }) { Text(stringResource(R.string.open_details)) }
                        TextButton(onClick = { favoritePendingDelete = favorite }) { Text(stringResource(R.string.remove_favorite)) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.my_reviews_count, reviews.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (reviews.isEmpty()) {
                Text(stringResource(R.string.no_reviews), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            reviews.forEach { review ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(review.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("★".repeat(review.rating) + "☆".repeat(5 - review.rating), color = MaterialTheme.colorScheme.primary)
                        review.updatedAt?.take(10)?.let { Text(stringResource(R.string.review_updated, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        review.reviewText?.let { Text(it) }
                        if (editingReview?.appId == review.appId) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                (1..5).forEach { star ->
                                    TextButton(onClick = { editRating = star }) {
                                        Text(if (star <= editRating) "★" else "☆", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { if (it.length <= 2000) editText = it },
                                label = { Text(stringResource(R.string.review_optional)) },
                                supportingText = { Text("${editText.length}/2000") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        runCatching { LumaStoreApi.setMyRating(review.packageName ?: review.appId, editRating.coerceIn(1, 5), editText) }
                                            .onSuccess {
                                                reviews = LumaStoreApi.myRatings()
                                                editingReview = null
                                            }
                                            .onFailure { error = it.message }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.save_review)) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editingReview = review; editText = review.reviewText.orEmpty(); editRating = review.rating }) {
                                Text(stringResource(R.string.edit_review))
                            }
                            TextButton(onClick = { reviewPendingDelete = review }) { Text(stringResource(R.string.delete_review)) }
                        }
                    }
                }
            }
        }
    }
    reviewPendingDelete?.let { review ->
        AlertDialog(
            onDismissRequest = { reviewPendingDelete = null },
            title = { Text(stringResource(R.string.delete_review)) },
            text = { Text(stringResource(R.string.delete_review_confirmation, review.appName)) },
            confirmButton = {
                TextButton(onClick = {
                    reviewPendingDelete = null
                    scope.launch {
                        runCatching { LumaStoreApi.deleteMyRating(review.packageName ?: review.appId) }
                            .onSuccess {
                                reviews = LumaStoreApi.myRatings()
                                if (editingReview?.appId == review.appId) editingReview = null
                            }
                            .onFailure { error = it.message }
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { reviewPendingDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    favoritePendingDelete?.let { favorite ->
        AlertDialog(
            onDismissRequest = { favoritePendingDelete = null },
            title = { Text(stringResource(R.string.remove_favorite)) },
            text = { Text(stringResource(R.string.remove_favorite_confirmation, favorite.appName)) },
            confirmButton = {
                TextButton(onClick = {
                    favoritePendingDelete = null
                    scope.launch {
                        runCatching { LumaStoreApi.removeFavorite(favorite.packageName ?: favorite.appId) }
                            .onSuccess { favorites = LumaStoreApi.myFavorites() }
                            .onFailure { error = it.message }
                    }
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { favoritePendingDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

}
