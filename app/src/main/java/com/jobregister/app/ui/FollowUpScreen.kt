package com.jobregister.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
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

/** Pending / waiting / not-completed jobs that need chasing, oldest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpScreen(vm: AppViewModel, modifier: Modifier, onOpen: (String) -> Unit) {
    val jobs by vm.jobs.collectAsState()
    val followUps = RoleConfig.visibleJobs(jobs)
        .filter { it.needsFollowUp }
        .sortedBy { it.date }

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Pending Job Follow-up") })
        if (followUps.isEmpty()) {
            Text("Nothing to follow up — all jobs completed.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp))
        } else {
            Text("${followUps.size} job(s) not ready for billing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                items(followUps, key = { it.id }) { job ->
                    JobCard(job) { onOpen(job.id) }
                }
            }
        }
    }
}
