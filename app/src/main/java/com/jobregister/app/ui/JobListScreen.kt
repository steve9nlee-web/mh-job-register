package com.jobregister.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jobregister.app.AppViewModel
import com.jobregister.app.RoleConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onCustomers: (() -> Unit)? = null
) {
    val jobs by vm.jobs.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val visible = RoleConfig.visibleJobs(jobs)

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(RoleConfig.appTitle) },
            actions = {
                if (syncing) CircularProgressIndicator(Modifier.padding(12.dp))
                else IconButton(onClick = { vm.sync() }) {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync")
                }
                if (onCustomers != null) {
                    IconButton(onClick = onCustomers) {
                        Icon(Icons.Filled.Group, contentDescription = "Customer database")
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        )
        if (visible.isEmpty()) {
            Text(
                "No jobs yet.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                items(visible, key = { it.id }) { job ->
                    JobCard(job) { onOpen(job.id) }
                }
            }
        }
    }
}
