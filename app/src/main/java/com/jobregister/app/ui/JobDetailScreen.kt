package com.jobregister.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jobregister.app.AppViewModel
import com.jobregister.app.RoleConfig
import com.jobregister.app.data.SheetApi
import com.jobregister.app.model.JobStatus

/** Full job view, with every section gated by RoleConfig. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(vm: AppViewModel, jobId: String, modifier: Modifier, onBack: () -> Unit) {
    val jobs by vm.jobs.collectAsState()
    val allPhotos by vm.photos.collectAsState()
    val job = jobs.firstOrNull { it.id == jobId }
    if (job == null) { onBack(); return }

    var status by remember(job.id) { mutableStateOf(job.status) }
    var remarks by remember(job.id) { mutableStateOf(job.remarks) }
    var customer by remember(job.id, job.customerCharge) {
        mutableStateOf(job.customerCharge?.toString() ?: "")
    }
    var contractor by remember(job.id, job.contractorPayable) {
        mutableStateOf(job.contractorPayable?.toString() ?: "")
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(job.id) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = categoryColor(job.category)
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Job Details")
                    LabelValue("Date", job.date)
                    LabelValue("Unit", job.unit.ifBlank { "—" })
                    LabelValue("Category", job.category.label)
                    if (job.rooms.isNotBlank()) LabelValue("Room", job.rooms)
                    LabelValue("Status", job.status.label)
                    // Who moved the status on — the contractor who did the
                    // work, never the person who raised the job.
                    if (job.updatedBy.isNotBlank()) {
                        LabelValue("Status updated by", job.updatedBy)
                    }
                    LabelValue("Description", job.description)
                    if (job.remarks.isNotBlank()) LabelValue("Remarks", job.remarks)
                    if (RoleConfig.canReviewFlags && job.rawMessage.isNotBlank()) {
                        LabelValue("WhatsApp text", job.rawMessage)
                    }
                    if (RoleConfig.canReviewFlags && job.needsReview) {
                        Text("⚑ Flagged: ${job.reviewReason} (fix in Review tab)",
                            color = Color(0xFFC62828),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            val photos = allPhotos.filter {
                it.jobId == job.id && RoleConfig.canSeePhoto(it.kind)
            }
            val beforeShots = photos.count { it.kind == "before" }
            val afterShots = photos.count { it.kind == "after" }
            val readyToFinish = beforeShots > 0 && afterShots > 0
            if (photos.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader(
                            if (photos.size == 1) "Photo" else "Photos (${photos.size})"
                        )
                        photos.forEach { photo -> JobPhoto(vm, photo) }
                    }
                }
            }

            if (RoleConfig.canAddWorkPhotos) {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("Work record")
                        Text(
                            "Photograph the unit before you start, and again once the " +
                                "work is done. Both go to the job with the date and time on them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        WorkStep("1. Before you start", beforeShots) {
                            PhotoPickerButtons(takeLabel = "📷 Before photo") { picked ->
                                vm.uploadPhoto(job.id, picked, "before")
                            }
                        }
                        if (job.startedAt.isNotBlank()) {
                            LabelValue("Started", job.startedAt)
                        } else if (beforeShots > 0 && job.status != JobStatus.COMPLETED) {
                            Button(
                                onClick = {
                                    vm.updateStatus(job.id, JobStatus.IN_PROGRESS, remarks)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) { Text("Start work") }
                        }

                        Spacer(Modifier.height(14.dp))
                        WorkStep("2. When the work is done", afterShots) {
                            if (beforeShots == 0) {
                                Text(
                                    "Take the before photo first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                PhotoPickerButtons(takeLabel = "📷 After photo") { picked ->
                                    vm.uploadPhoto(job.id, picked, "after")
                                }
                            }
                        }
                        if (job.completedAt.isNotBlank()) LabelValue("Finished", job.completedAt)
                    }
                }
            }

            if (RoleConfig.canUpdateStatus) {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("Update Status")
                        if (!RoleConfig.completionOnly) {
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                JobStatus.entries.forEach { s ->
                                    FilterChip(
                                        selected = status == s,
                                        onClick = { status = s },
                                        label = { Text(s.label) },
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = remarks, onValueChange = { remarks = it },
                            label = { Text("Remarks") },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                        if (RoleConfig.completionOnly) {
                            Button(
                                onClick = {
                                    vm.updateStatus(job.id, JobStatus.COMPLETED, remarks)
                                },
                                enabled = readyToFinish || job.status == JobStatus.COMPLETED,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(
                                    if (job.status == JobStatus.COMPLETED) "Completed ✓"
                                    else "Mark done"
                                )
                            }
                            if (!readyToFinish && job.status != JobStatus.COMPLETED) {
                                Text(
                                    "Add the before and after photos above to finish this job.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        } else {
                            Button(
                                onClick = { vm.updateStatus(job.id, status, remarks) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) { Text("Save status") }
                        }
                    }
                }
            }

            if (RoleConfig.canSeeCustomerBilling) {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("Billing (Admin)")
                        Row {
                            OutlinedTextField(
                                value = customer, onValueChange = { customer = it },
                                label = { Text("Customer RM") },
                                modifier = Modifier.width(150.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            OutlinedTextField(
                                value = contractor, onValueChange = { contractor = it },
                                label = { Text("Payable RM") },
                                modifier = Modifier.width(150.dp)
                            )
                        }
                        Row(Modifier.padding(top = 8.dp)) {
                            Button(onClick = { vm.applyRateCard(job.id) }) {
                                Text("Match rate card")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                vm.setBilling(job.id,
                                    customer.toDoubleOrNull(), contractor.toDoubleOrNull())
                            }) { Text("Save amounts") }
                        }
                        LabelValue("Invoiced", if (job.invoiced) "Yes" else "No")
                        LabelValue("Paid", if (job.paid) "Yes" else "No")
                    }
                }
            } else if (RoleConfig.canSeeContractorPayable && job.contractorPayable != null) {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("Your Pay")
                        LabelValue("Amount", money(job.contractorPayable))
                        LabelValue("Payment status", if (job.paid) "Paid" else "Processing")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * One photo taken when the job was raised. The image is fetched through the
 * backend rather than straight from Drive, so the phone shows it without
 * needing its own access to the photo folder.
 */
@Composable
private fun JobPhoto(vm: AppViewModel, photo: SheetApi.JobPhoto) {
    var image by remember(photo.fileId) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(photo.fileId) { mutableStateOf(false) }

    LaunchedEffect(photo.fileId) {
        val bytes = vm.photoBytes(photo.fileId)
        val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bmp == null) failed = true else image = bmp.asImageBitmap()
    }

    Column(Modifier.padding(vertical = 6.dp)) {
        val shown = image
        when {
            shown != null -> Image(
                bitmap = shown,
                contentDescription = "Photo for job ${photo.jobId}",
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                contentScale = ContentScale.Fit
            )
            failed -> Text(
                "Photo could not be loaded — sync and try again.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFC62828)
            )
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Loading photo…", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            listOf(photoKindLabel(photo.kind), photo.uploadedAt.ifBlank { photo.filename })
                .filter { it.isNotBlank() }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun photoKindLabel(kind: String): String = when (kind) {
    "before" -> "Before work"
    "after" -> "After work"
    else -> "Job photo"
}

/** One step of the contractor's photo sequence: heading, tick, and controls. */
@Composable
private fun WorkStep(title: String, saved: Int, content: @Composable () -> Unit) {
    Text(
        if (saved > 0) "$title   ✓ $saved saved" else title,
        style = MaterialTheme.typography.titleSmall,
        color = if (saved > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(4.dp))
    content()
}
