package com.jobregister.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jobregister.app.RoleConfig
import com.jobregister.app.model.Job
import com.jobregister.app.model.JobCategory
import com.jobregister.app.model.JobStatus
import java.util.Locale

fun money(v: Double?): String =
    if (v == null) "—" else String.format(Locale.US, "RM %.2f", v)

/**
 * Each trade gets its own pale card colour so a long list can be scanned
 * by trade without reading a word of it.
 */
fun categoryColor(c: JobCategory): Color = when (c) {
    JobCategory.AIRCON -> Color(0xFFE1F0FB)                          // light blue
    JobCategory.CLEANING, JobCategory.DEEP_CLEANING -> Color(0xFFFCE4E4)  // light red
    JobCategory.PEST_CONTROL -> Color(0xFFE4F3E6)                    // light green
    JobCategory.PLUMBING -> Color(0xFFFBF3D0)                        // light yellow
    else -> Color(0xFFF2F2F2)                                        // neutral
}

fun statusColor(s: JobStatus): Color = when (s) {
    JobStatus.AWAITING_APPROVAL -> Color(0xFF546E7A)
    JobStatus.COMPLETED -> Color(0xFF2E7D32)
    JobStatus.IN_PROGRESS -> Color(0xFF6A1B9A)
    JobStatus.PENDING -> Color(0xFFF9A825)
    JobStatus.WAITING -> Color(0xFF0277BD)
    JobStatus.NOT_COMPLETED -> Color(0xFFC62828)
}

@Composable
fun StatusChip(status: JobStatus) {
    AssistChip(
        onClick = {},
        label = { Text(status.label, color = statusColor(status)) }
    )
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobCard(job: Job, hasPhoto: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = categoryColor(job.category)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (job.unit.isBlank()) "Unit ?" else job.unit,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasPhoto) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Has a photo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (job.needsReview && RoleConfig.canReviewFlags) {
                        Icon(Icons.Filled.Flag, contentDescription = "Needs review",
                            tint = Color(0xFFC62828))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(job.status.label, color = statusColor(job.status),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            Text("${job.date}  ·  ${job.category.label}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
            if (job.description.isNotBlank()) {
                Text(job.description, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            }
            // Money lines are strictly role-scoped.
            if (RoleConfig.canSeeCustomerBilling && job.customerCharge != null) {
                Text("Customer: ${money(job.customerCharge)}   Payable: ${money(job.contractorPayable)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp))
            } else if (RoleConfig.canSeeContractorPayable && !RoleConfig.canSeeCustomerBilling
                && job.contractorPayable != null) {
                Text("Your pay: ${money(job.contractorPayable)}" + if (job.paid) "  (paid)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
