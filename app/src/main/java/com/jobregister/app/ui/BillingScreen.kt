package com.jobregister.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jobregister.app.AppViewModel
import com.jobregister.app.model.Job

/**
 * Admin-only billing pipeline:
 * Rate Card Matching -> Customer Billing -> Invoice & Payment.
 * Only completed, reviewed jobs enter this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(vm: AppViewModel, modifier: Modifier, onOpen: (String) -> Unit) {
    val jobs by vm.jobs.collectAsState()
    val billable = jobs.filter { it.billable }.sortedBy { it.date }
    val unrated = billable.filter { it.customerCharge == null }
    val rated = billable.filter { it.customerCharge != null }

    val totalBilling = rated.sumOf { it.customerCharge ?: 0.0 }
    val totalUninvoiced = rated.filter { !it.invoiced }.sumOf { it.customerCharge ?: 0.0 }
    val totalUnpaid = rated.filter { it.invoiced && !it.paid }.sumOf { it.customerCharge ?: 0.0 }

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Billing") })
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("Customer Billing Summary")
                        LabelValue("Total billable", money(totalBilling))
                        LabelValue("Not yet invoiced", money(totalUninvoiced))
                        LabelValue("Invoiced, unpaid", money(totalUnpaid))
                    }
                }
            }

            if (unrated.isNotEmpty()) {
                item { SectionHeader("Rate Card Matching — ${unrated.size} job(s)") }
                items(unrated, key = { it.id }) { job ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${job.unit} · ${job.category.label} · ${job.date}",
                                fontWeight = FontWeight.Bold)
                            Text(job.description, style = MaterialTheme.typography.bodySmall,
                                maxLines = 2)
                            Row(Modifier.padding(top = 6.dp)) {
                                Button(onClick = { vm.applyRateCard(job.id) }) {
                                    Text("Match rate card")
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { onOpen(job.id) }) { Text("Open") }
                            }
                        }
                    }
                }
            }

            if (rated.isNotEmpty()) {
                item { SectionHeader("Invoice & Payment") }
                items(rated, key = { it.id }) { job ->
                    InvoiceRow(job,
                        onInvoiced = { vm.markInvoiced(job.id, it) },
                        onPaid = { vm.markPaid(job.id, it) },
                        onOpen = { onOpen(job.id) })
                }
            }

            if (billable.isEmpty()) {
                item {
                    Text("No completed jobs ready for billing yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InvoiceRow(
    job: Job,
    onInvoiced: (Boolean) -> Unit,
    onPaid: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${job.unit} · ${job.category.label} · ${job.date}", fontWeight = FontWeight.Bold)
            LabelValue("Customer charge", money(job.customerCharge))
            LabelValue("Contractor payable", money(job.contractorPayable))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = job.invoiced, onCheckedChange = onInvoiced)
                Text("Invoiced")
                Spacer(Modifier.width(16.dp))
                Checkbox(checked = job.paid, onCheckedChange = onPaid, enabled = job.invoiced)
                Text("Paid")
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = onOpen) { Text("Open") }
            }
        }
    }
}
