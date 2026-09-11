package com.jobregister.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.jobregister.app.AppViewModel
import com.jobregister.app.RoleConfig
import com.jobregister.app.model.Role

enum class Tab(val label: String, val icon: ImageVector) {
    NEW_JOB("New Job", Icons.Filled.AddComment),
    REGISTER("Jobs", Icons.AutoMirrored.Filled.Assignment),
    CUSTOMERS("Customers", Icons.Filled.Group),
    REVIEW("Review", Icons.Filled.Flag),
    BILLING("Billing", Icons.Filled.ReceiptLong),
    PAYABLE("Pay", Icons.Filled.Payments),
    FOLLOW_UP("Follow-up", Icons.Filled.Schedule),
    SETTINGS("Settings", Icons.Filled.Settings)
}

/** Which bottom tabs each APK ships with — the per-role information boundary. */
val roleTabs: List<Tab> = when (RoleConfig.role) {
    Role.ADMIN -> listOf(Tab.REGISTER, Tab.REVIEW, Tab.BILLING, Tab.PAYABLE, Tab.FOLLOW_UP)
    Role.CLEANER, Role.REPAIRER -> listOf(Tab.REGISTER, Tab.PAYABLE, Tab.SETTINGS)
    Role.INITIATOR -> listOf(Tab.NEW_JOB, Tab.REGISTER, Tab.SETTINGS)
}

private val roleColor: Color = when (RoleConfig.role) {
    Role.ADMIN -> Color(0xFF1565C0)      // blue
    Role.CLEANER -> Color(0xFF00796B)    // teal
    Role.REPAIRER -> Color(0xFFE65100)   // orange
    Role.INITIATOR -> Color(0xFF6A1B9A)  // purple
}

@Composable
fun App(vm: AppViewModel) {
    MaterialTheme(colorScheme = lightColorScheme(primary = roleColor)) {
        var currentTab by remember { mutableStateOf(roleTabs.first()) }
        var selectedJobId by remember { mutableStateOf<String?>(null) }
        var showSettings by remember { mutableStateOf(false) }
        var showCustomers by remember { mutableStateOf(false) }

        val snackbar = remember { SnackbarHostState() }
        val message by vm.message.collectAsState()
        LaunchedEffect(message) {
            message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (selectedJobId == null && !showSettings && !showCustomers && roleTabs.size > 1) {
                    NavigationBar {
                        roleTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == currentTab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val content = Modifier.padding(padding)
            when {
                showSettings -> SettingsScreen(vm, content) { showSettings = false }
                showCustomers -> CustomersScreen(vm, content) { showCustomers = false }
                selectedJobId != null -> JobDetailScreen(
                    vm, selectedJobId!!, content
                ) { selectedJobId = null }
                else -> when (currentTab) {
                    Tab.NEW_JOB -> NewJobScreen(vm, content)
                    Tab.REGISTER -> JobListScreen(vm, content,
                        onOpen = { selectedJobId = it },
                        onSettings = { showSettings = true },
                        onCustomers = if (RoleConfig.role == Role.ADMIN) {
                            { showCustomers = true }
                        } else null,
                        showFollowUpFilter = RoleConfig.role == Role.INITIATOR)
                    Tab.CUSTOMERS -> CustomersScreen(vm, content)
                    Tab.REVIEW -> ReviewScreen(vm, content)
                    Tab.BILLING -> BillingScreen(vm, content, onOpen = { selectedJobId = it })
                    Tab.PAYABLE -> PayableScreen(vm, content)
                    Tab.FOLLOW_UP -> FollowUpScreen(vm, content, onOpen = { selectedJobId = it })
                    Tab.SETTINGS -> SettingsScreen(vm, content, onClose = null)
                }
            }
        }
    }
}
