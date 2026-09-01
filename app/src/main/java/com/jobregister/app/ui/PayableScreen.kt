package com.jobregister.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jobregister.app.AppViewModel
import com.jobregister.app.RoleConfig
import com.jobregister.app.model.Job
import com.jobregister.app.model.Role

/**
 * ADMIN: cleaner + repairer payment summaries side by side.
 * CLEANER / REPAIRER: only their own payable — customer charges never shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayableScreen(vm: AppViewModel, modifier: Modifier) {
    val jobs by vm.jobs.collectAsState()
    val completed = jobs.filter { it.billable && it.contractorPayable != null }

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = {
            Text(if (RoleConfig.role == Role.ADMIN) "Contractor Payables" else "My Pay")
        })
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            if (RoleConfig.role == Role.ADMIN) {
                item {
                    PayableSummary("Cleaner Payment Summary",
                        completed.filter { it.category.isCleaning })
                }
                item {
                    PayableSummary("Repairer Payment Summary",
                        completed.filter { it.category.isRepair })
                }
            } else {
                val mine = RoleConfig.visibleJobs(completed)
                item { PayableSummary("Completed jobs — your pay", mine) }
                items(mine, key = { it.id }) { job ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${job.unit} · ${job.date}", fontWeight = FontWeight.Bold)
                            Text(job.category.label, style = MaterialTheme.typography.bodySmall)
                            LabelValue("Your pay", money(job.contractorPayable))
                            LabelValue("Payment status", if (job.paid) "Paid" else "Processing")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PayableSummary(title: String, jobs: List<Job>) {
    val total = jobs.sumOf { it.contractorPayable ?: 0.0 }
    val paid = jobs.filter { it.paid }.sumOf { it.contractorPayable ?: 0.0 }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            SectionHeader(title)
            LabelValue("Jobs", jobs.size.toString())
            LabelValue("Total payable", money(total))
            LabelValue("Paid out", money(paid))
            LabelValue("Outstanding", money(total - paid))
        }
    }
}
