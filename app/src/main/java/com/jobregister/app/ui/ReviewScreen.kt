package com.jobregister.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import com.jobregister.app.model.JobCategory

/** Admin-only: Human Review of AI-flagged rows — correct missing info, clear the flag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: AppViewModel, modifier: Modifier) {
    val jobs by vm.jobs.collectAsState()
    val flagged = jobs.filter { it.needsReview }.sortedBy { it.date }

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Human Review — AI Flags") })
        if (flagged.isEmpty()) {
            Text("No flagged jobs. The register is clean.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                items(flagged, key = { it.id }) { job ->
                    ReviewCard(job) { unit, cat, desc ->
                        vm.resolveReview(job.id, unit, cat, desc)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewCard(job: Job, onResolve: (String, JobCategory, String) -> Unit) {
    var unit by remember(job.id) { mutableStateOf(job.unit) }
    var category by remember(job.id) { mutableStateOf(job.category) }
    var description by remember(job.id) { mutableStateOf(job.description) }
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${job.id} · ${job.date}", fontWeight = FontWeight.Bold)
            Text("⚑ ${job.reviewReason}",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
            if (job.rawMessage.isNotBlank()) {
                Text("Original: “${job.rawMessage}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            OutlinedTextField(value = unit, onValueChange = { unit = it },
                label = { Text("Unit number") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = category.label, onValueChange = {}, readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    JobCategory.entries.filter { it != JobCategory.UNKNOWN }.forEach { c ->
                        DropdownMenuItem(text = { Text(c.label) },
                            onClick = { category = c; expanded = false })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = description, onValueChange = { description = it },
                label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onResolve(unit, category, description) },
                enabled = unit.isNotBlank() && category != JobCategory.UNKNOWN
            ) { Text("Resolve & clear flag") }
        }
    }
}
