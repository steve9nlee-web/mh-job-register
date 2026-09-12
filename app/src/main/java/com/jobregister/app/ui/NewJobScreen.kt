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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jobregister.app.AppViewModel
import com.jobregister.app.model.Job

/**
 * WhatsApp Message -> AI Conversion -> Job Register rows.
 * Paste the daily group message; each non-empty line becomes one register row.
 * Rows missing a unit or category are flagged for admin review automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewJobScreen(vm: AppViewModel, modifier: Modifier) {
    var rawText by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<List<Job>>(emptyList()) }

    val customers by vm.customers.collectAsState()
    val apartments by vm.apartments.collectAsState()
    val services by vm.services.collectAsState()
    val serviceDetails by vm.serviceDetails.collectAsState()
    var showServiceInfo by remember { mutableStateOf(false) }
    var selApt by remember { mutableStateOf("") }
    var selUnit by remember { mutableStateOf("") }
    var selService by remember { mutableStateOf("") }
    var selRoom by remember { mutableStateOf("") }
    var jobDesc by remember { mutableStateOf("") }

    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("New Job") },
            actions = {
                IconButton(onClick = { vm.sync() }) {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync customer list")
                }
            }
        )
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("Create job — pick a unit")
                        if (customers.isEmpty() && apartments.isEmpty()) {
                            Text(
                                "No customer list loaded yet — tap the sync icon (top right) to fetch it from the database.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Only apartments with registered customer units;
                            // new units are added by the admin in Customer Database.
                            val aptCodes = customers.map { it.apartment }
                                .filter { it.isNotBlank() }.distinct().sorted()
                            val aptLabels = aptCodes.map { c ->
                                apartments[c]?.takeIf { it.isNotBlank() }?.let { "$c — $it" } ?: c
                            }
                            val selAptLabel = aptCodes.indexOf(selApt)
                                .takeIf { it >= 0 }?.let { aptLabels[it] } ?: ""
                            DropdownField("Apartment", aptLabels, selAptLabel) { i ->
                                selApt = aptCodes[i]; selUnit = ""
                            }
                            Spacer(Modifier.height(6.dp))

                            val units = customers.filter { it.apartment == selApt }
                                .map { it.unit }.distinct().sorted()
                            DropdownField("Unit", units, selUnit) { i ->
                                selUnit = units[i]
                                customers.firstOrNull { it.unit == selUnit }
                                    ?.service?.takeIf { it.isNotBlank() }
                                    ?.let { selService = it }
                            }
                            Spacer(Modifier.height(6.dp))

                            val serviceOptions = services.ifEmpty {
                                listOf("Cleaning", "AirCond Service", "Pest Control", "General")
                            }
                            DropdownField("Service", serviceOptions, selService) { i ->
                                selService = serviceOptions[i]
                            }
                            val detail = serviceDetails[selService].orEmpty()
                            if (detail.isNotBlank()) {
                                TextButton(onClick = { showServiceInfo = true }) {
                                    Text("ⓘ  $selService — tap for price & details")
                                }
                            }
                            if (showServiceInfo && detail.isNotBlank()) {
                                AlertDialog(
                                    onDismissRequest = { showServiceInfo = false },
                                    title = { Text(selService) },
                                    text = {
                                        Text(
                                            detail,
                                            modifier = Modifier
                                                .heightIn(max = 400.dp)
                                                .verticalScroll(rememberScrollState())
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showServiceInfo = false }) {
                                            Text("Close")
                                        }
                                    }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            // How much of the unit the job covers — this is what
                            // the cleaning sets are priced by.
                            val roomOptions = listOf(
                                "Not applicable", "Room 1", "Room 2", "Room 3", "Room All"
                            )
                            DropdownField("Room", roomOptions, selRoom) { i ->
                                selRoom = if (i == 0) "" else roomOptions[i]
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = jobDesc, onValueChange = { jobDesc = it },
                                label = { Text("Notes (optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            SectionHeader("Photo")
                            PhotoPickerButtons { picked -> photoBytes = picked }
                            photoBytes?.let { bytes ->
                                Row {
                                    Text(
                                        "✓ Photo taken (${bytes.size / 1024} KB) — goes to Drive with the job number and time stamp when you create the job",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(onClick = { photoBytes = null }) { Text("Remove") }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    vm.createJob(selUnit, selService, jobDesc, photoBytes, selRoom)
                                    jobDesc = ""; photoBytes = null; selRoom = ""
                                },
                                enabled = selUnit.isNotBlank() && selService.isNotBlank()
                            ) { Text("Create job${if (selUnit.isNotBlank()) " for $selUnit" else ""}") }
                        }
                    }
                }

                SectionHeader("Or paste the WhatsApp message")
                Text(
                    "Each line is converted into one Job Register row.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    placeholder = { Text("e.g.\n12/8 PV-12-03 cleaning done\nR-5-11 sink leaking, plumber tmr") }
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(
                        onClick = {
                            parsed = rawText.lines()
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .map { vm.parseMessage(it) }
                        },
                        enabled = rawText.isNotBlank()
                    ) { Text("Convert with AI") }
                    Spacer(Modifier.width(12.dp))
                    if (parsed.isNotEmpty()) {
                        Button(onClick = {
                            parsed.forEach { vm.saveJob(it) }
                            parsed = emptyList()
                            rawText = ""
                        }) { Text("Save ${parsed.size} row(s)") }
                    }
                }
                if (parsed.isNotEmpty()) {
                    SectionHeader("Preview — Job Register rows")
                }
            }
            items(parsed, key = { it.id }) { job ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${if (job.unit.isBlank()) "Unit ?" else job.unit} · ${job.category.label}",
                            fontWeight = FontWeight.Bold
                        )
                        Text("${job.date} · ${job.status.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor(job.status))
                        Text(job.description, style = MaterialTheme.typography.bodyMedium)
                        if (job.needsReview) {
                            Text("⚑ Needs review: ${job.reviewReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC62828))
                        }
                        OutlinedButton(
                            onClick = { parsed = parsed.filterNot { it.id == job.id } },
                            modifier = Modifier.padding(top = 4.dp)
                        ) { Text("Remove") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(i); expanded = false })
            }
        }
    }
}
