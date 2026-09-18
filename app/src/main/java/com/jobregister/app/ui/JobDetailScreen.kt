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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    // Which status is waiting on a reason being typed, if any.
    var holdReason by remember(job.id) { mutableStateOf<JobStatus?>(null) }

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
                    if (job.approvedBy.isNotBlank()) {
                        LabelValue(
                            "Approved by",
                            listOf(job.approvedBy, job.approvedAt)
                                .filter { it.isNotBlank() }.joinToString("  ·  ")
                        )
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

            if (!job.approved) {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("Approval")
                        if (RoleConfig.canApproveJobs) {
                            Text(
                                "No contractor can see this job yet. Approving it " +
                                    "releases it to the trade it belongs to.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { vm.approveJob(job.id) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) { Text("Approve — send to ${job.category.label}") }
                        } else {
                            Text(
                                "Waiting for the admin to approve this job.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Everyone who can open the job sees its whole photo record:
            // the picture it was raised with and the before/after pair.
            val photos = allPhotos.filter { it.jobId == job.id }
            val beforeShots = photos.count { it.kind == "before" }
            val afterShots = photos.count { it.kind == "after" }
            val readyToFinish = beforeShots > 0 && afterShots > 0
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    SectionHeader("Photos (${photos.size})")
                    PhotoGroup(
                        "Photo from Initiator",
                        photos.filter { it.kind != "before" && it.kind != "after" },
                        vm
                    )
                    PhotoGroup("Photo before work", photos.filter { it.kind == "before" }, vm)
                    PhotoGroup("Photo after work", photos.filter { it.kind == "after" }, vm)
                }
            }

            if (RoleConfig.canAddWorkPhotos) {
                val done = job.status == JobStatus.COMPLETED
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

                        // Top — the before photo, which unlocks everything else.
                        WorkStep("1. Before you start", beforeShots) {
                            PhotoPickerButtons(
                                takeLabel = if (beforeShots == 0) "📷 Before photo"
                                    else "📷 Another before photo"
                            ) { picked -> vm.uploadPhoto(job.id, picked, "before") }
                        }
                        if (job.startedAt.isNotBlank()) LabelValue("Started", job.startedAt)

                        // Middle — how the job is going.
                        Spacer(Modifier.height(16.dp))
                        Text("2. Work status", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        if (beforeShots == 0) {
                            Text(
                                "Take the before photo first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        holdReason = null
                                        vm.updateStatus(job.id, JobStatus.IN_PROGRESS, remarks)
                                    },
                                    enabled = !done && job.status != JobStatus.IN_PROGRESS,
                                    modifier = Modifier.weight(1f)
                                ) { Text("▶ Start") }
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = { holdReason = JobStatus.WAITING },
                                    enabled = !done,
                                    modifier = Modifier.weight(1f)
                                ) { Text("⏸ Held up") }
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = { holdReason = JobStatus.NOT_COMPLETED },
                                    enabled = !done,
                                    modifier = Modifier.weight(1f)
                                ) { Text("✖ Not done") }
                            }
                        }
                        holdReason?.let { pending ->
                            OutlinedTextField(
                                value = remarks, onValueChange = { remarks = it },
                                label = {
                                    Text(
                                        if (pending == JobStatus.WAITING)
                                            "Why is it held up?"
                                        else "Why could it not be completed?"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            Row(Modifier.padding(top = 6.dp)) {
                                Button(
                                    onClick = {
                                        vm.updateStatus(job.id, pending, remarks)
                                        holdReason = null
                                    },
                                    enabled = remarks.isNotBlank()
                                ) { Text("Save reason") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { holdReason = null }) { Text("Cancel") }
                            }
                        }

                        // Bottom — the after photo, then finishing the job.
                        Spacer(Modifier.height(16.dp))
                        WorkStep("3. When the work is done", afterShots) {
                            if (beforeShots == 0) {
                                Text(
                                    "Take the before photo first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                PhotoPickerButtons(
                                    takeLabel = if (afterShots == 0) "📷 After photo"
                                        else "📷 Another after photo"
                                ) { picked -> vm.uploadPhoto(job.id, picked, "after") }
                            }
                        }
                        if (holdReason == null) {
                            OutlinedTextField(
                                value = remarks, onValueChange = { remarks = it },
                                label = { Text("Remarks (optional)") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                        // Green the moment the after photo is in, so a cleaner
                        // can see at a glance that the job is ready to close.
                        Button(
                            onClick = { vm.updateStatus(job.id, JobStatus.COMPLETED, remarks) },
                            enabled = readyToFinish || done,
                            colors = if (afterShots > 0 || done) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            } else ButtonDefaults.buttonColors(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text(if (done) "Completed ✓" else "✓ Complete job") }
                        if (!readyToFinish && !done) {
                            Text(
                                "Add the before and after photos to finish this job.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        if (job.completedAt.isNotBlank()) LabelValue("Finished", job.completedAt)
                    }
                }
            }

            // Contractors drive the job from the work record above; this is
            // the admin's free-hand override.
            if (RoleConfig.canUpdateStatus && !RoleConfig.completionOnly) {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("Update Status")
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
                        OutlinedTextField(
                            value = remarks, onValueChange = { remarks = it },
                            label = { Text("Remarks") },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                        Button(
                            onClick = { vm.updateStatus(job.id, status, remarks) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text("Save status") }
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

/**
 * One of the three photo groups. Empty groups still show their heading, so a
 * missing after photo is as visible as a present one.
 */
@Composable
private fun PhotoGroup(title: String, photos: List<SheetApi.JobPhoto>, vm: AppViewModel) {
    Text(
        if (photos.isEmpty()) title else "$title  (${photos.size})",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 10.dp)
    )
    if (photos.isEmpty()) {
        Text(
            "None yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        photos.forEach { photo -> JobPhoto(vm, photo) }
    }
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
