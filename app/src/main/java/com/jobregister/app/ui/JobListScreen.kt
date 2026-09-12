package com.jobregister.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onCustomers: (() -> Unit)? = null,
    showFollowUpFilter: Boolean = false,
    showApprovalFilter: Boolean = false
) {
    val jobs by vm.jobs.collectAsState()
    val photos by vm.photos.collectAsState()
    val syncing by vm.syncing.collectAsState()
    var filter by remember { mutableStateOf(JobFilter.ALL) }
    val all = RoleConfig.visibleJobs(jobs)
    val waiting = all.filter { !it.approved }
    val followUps = all.filter { it.needsFollowUp }
    val visible = when (filter) {
        JobFilter.TO_APPROVE -> waiting
        // Oldest first when chasing: the longest wait needs attention first.
        JobFilter.FOLLOW_UP -> followUps.sortedBy { it.date }
        JobFilter.ALL -> all
    }

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
        if (showFollowUpFilter || showApprovalFilter) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                FilterChip(
                    selected = filter == JobFilter.ALL,
                    onClick = { filter = JobFilter.ALL },
                    label = { Text("All jobs (${all.size})") }
                )
                if (showApprovalFilter) {
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = filter == JobFilter.TO_APPROVE,
                        onClick = { filter = JobFilter.TO_APPROVE },
                        label = { Text("To approve (${waiting.size})") }
                    )
                }
                if (showFollowUpFilter) {
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = filter == JobFilter.FOLLOW_UP,
                        onClick = { filter = JobFilter.FOLLOW_UP },
                        label = { Text("Follow-up (${followUps.size})") }
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            Text(
                when (filter) {
                    JobFilter.TO_APPROVE -> "Nothing waiting for approval."
                    JobFilter.FOLLOW_UP -> "Nothing to follow up — all jobs completed."
                    JobFilter.ALL -> "No jobs yet."
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                val withPhotos = photos.map { it.jobId }.toSet()
                items(visible, key = { it.id }) { job ->
                    JobCard(job, hasPhoto = job.id in withPhotos) { onOpen(job.id) }
                }
            }
        }
    }
}

/** Which slice of the register the list is showing. */
enum class JobFilter { ALL, TO_APPROVE, FOLLOW_UP }
