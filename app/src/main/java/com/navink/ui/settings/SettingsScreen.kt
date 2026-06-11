package com.navink.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

@Composable
fun SettingsScreen(
    onConnected: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onBack != null) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start),
            ) { Text("← Back") }
        }
        Text(text = "Navink", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))

        Text(
            text = "Server",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.height(8.dp))

        TextFieldMMD(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("https://music.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))

        TextFieldMMD(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))

        TextFieldMMD(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(16.dp))

        if (state.error != null) {
            Text(text = state.error!!, color = Color.Black)
            Spacer(Modifier.height(8.dp))
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            ButtonMMD(
                onClick = { viewModel.connect(onConnected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            ) {
                Text("Connect")
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val external = state.storageLocation == "external"
            if (external) {
                ButtonMMD(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("SD Card") }
                OutlinedButtonMMD(
                    onClick = { viewModel.setStorageLocation("internal") },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Internal") }
            } else {
                OutlinedButtonMMD(
                    onClick = { viewModel.setStorageLocation("external") },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("SD Card") }
                ButtonMMD(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Internal") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "New downloads go to the selected storage. Existing downloads stay where they are.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedButtonMMD(
            onClick = { viewModel.toggleOfflineMode() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (state.offlineMode) "Offline mode: On" else "Offline mode: Off")
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Storage used: ${state.storageUsedMb} MB",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedButtonMMD(
            onClick = { viewModel.verifyDownloads() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Verify downloads") }
        state.verifyMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}
