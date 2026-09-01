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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("New Job — WhatsApp Intake") })
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Text(
                    "Paste the WhatsApp group message below. Each line is converted into one Job Register row.",
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
