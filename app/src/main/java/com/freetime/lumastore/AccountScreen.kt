package com.freetime.lumastore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freetime.lumastore.data.DeveloperRepository
import com.freetime.lumastore.data.DeveloperSession
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(repository: DeveloperRepository) {
    var session by remember { mutableStateOf<DeveloperSession?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { repository.savedSession() }
            .onSuccess { session = it }
            .onFailure { error = it.message }
        loading = false
        repository.sessionFlow().collect {
            session = it
            loading = false
            working = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.account), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.account_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        }
    }
}
