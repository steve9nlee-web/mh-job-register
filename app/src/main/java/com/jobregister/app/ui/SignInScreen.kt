package com.jobregister.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jobregister.app.AppViewModel

/**
 * First screen of the Contractor app. The staff code the admin hands out
 * (from the Staff tab of the sheet) decides whether this phone shows
 * cleaning jobs or repair jobs, and whose name goes on each update.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(vm: AppViewModel, modifier: Modifier) {
    var code by rememberSaveable { mutableStateOf("") }
    val signingIn by vm.signingIn.collectAsState()

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("MH Job Register — Contractor") })
        Column(Modifier.padding(16.dp)) {
            SectionHeader("Sign in with your staff code")
            Text(
                "Your code comes from the admin. It sets whether you see cleaning or " +
                    "repair jobs, and puts your name on the jobs you update.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Staff code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (signingIn) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { vm.signIn(code) },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sign in") }
            }
        }
    }
}
