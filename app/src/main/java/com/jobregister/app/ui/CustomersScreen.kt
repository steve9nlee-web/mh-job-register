package com.jobregister.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jobregister.app.AppViewModel

/**
 * Manage the customer database from the phone: add/delete customers,
 * apartments and services. Every change is pushed to the spreadsheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(vm: AppViewModel, modifier: Modifier, onBack: (() -> Unit)? = null) {
    val customers by vm.customers.collectAsState()
    val apartments by vm.apartments.collectAsState()
    val services by vm.services.collectAsState()

    var newApt by remember { mutableStateOf("") }
    var newUnit by remember { mutableStateOf("") }
    var newService by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var aptCode by remember { mutableStateOf("") }
    var aptName by remember { mutableStateOf("") }
    var svcName by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Customer Database") },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                IconButton(onClick = { vm.sync() }) {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync")
                }
            }
        )
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SectionHeader("Add customer unit")
                        val codes = apartments.keys.sorted()
                        val labels = codes.map { c ->
                            apartments[c]?.takeIf { it.isNotBlank() }?.let { "$c — $it" } ?: c
                        }
                        val sel = codes.indexOf(newApt).takeIf { it >= 0 }?.let { labels[it] } ?: ""
                        DropdownField("Apartment", labels, sel) { i -> newApt = codes[i] }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newUnit, onValueChange = { newUnit = it },
                            label = { Text("Unit no (e.g. 19-11)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        val svcOptions = services.ifEmpty {
                            listOf("Cleaning", "AirCond Service", "Pest Control", "General")
                        }
                        DropdownField("Service", svcOptions, newService) { i -> newService = svcOptions[i] }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newName, onValueChange = { newName = it },
                            label = { Text("Customer name (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        val typed = newUnit.trim().uppercase()
                        val unitFull = when {
                            typed.isBlank() -> ""
                            Regex("^[A-Z]{1,3}-").containsMatchIn(typed) -> typed
                            newApt.isNotBlank() -> "$newApt-$typed"
                            else -> typed
                        }
                        Button(
                            onClick = {
                                vm.addCustomer(newApt, unitFull, newService, newName)
                                newUnit = ""; newName = ""
                            },
                            enabled = newApt.isNotBlank() && unitFull.isNotBlank() && newService.isNotBlank()
                        ) { Text(if (unitFull.isBlank()) "Add customer" else "Add $unitFull") }
                    }
                }
                SectionHeader("Customers (${customers.size})")
            }
            items(customers.sortedBy { it.unit }, key = { "cust-${it.unit}" }) { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(c.unit, fontWeight = FontWeight.Bold)
                            Text(
                                listOf(c.service, c.customerName)
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { vm.deleteCustomer(c.unit) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${c.unit}")
                        }
                    }
                }
            }
            item { SectionHeader("Apartments") }
            items(apartments.keys.sorted(), key = { "apt-$it" }) { code ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$code — ${apartments[code] ?: ""}")
                    IconButton(onClick = { vm.deleteApartment(code) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete $code")
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = aptCode, onValueChange = { aptCode = it },
                        label = { Text("Code") }, modifier = Modifier.width(100.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = aptName, onValueChange = { aptName = it },
                        label = { Text("Name") }, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            vm.addApartment(aptCode.trim().uppercase(), aptName.trim())
                            aptCode = ""; aptName = ""
                        },
                        enabled = aptCode.isNotBlank()
                    ) { Text("Add") }
                }
                SectionHeader("Services")
            }
            items(services, key = { "svc-$it" }) { s ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s)
                    IconButton(onClick = { vm.deleteService(s) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete $s")
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = svcName, onValueChange = { svcName = it },
                        label = { Text("New service") }, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { vm.addService(svcName.trim()); svcName = "" },
                        enabled = svcName.isNotBlank()
                    ) { Text("Add") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
