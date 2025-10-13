package com.example.upaimonitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsMonitorScreen(onBackClick: () -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    val monitoredIds by SmsMonitorManager.monitoredIds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Monitors") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Monitor")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                "Monitored Sender IDs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Add sender IDs to monitor specific SMS messages for transactions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (monitoredIds.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No monitored IDs yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(monitoredIds.toList()) { senderId ->
                        MonitoredIdItem(
                            senderId = senderId,
                            onDelete = { SmsMonitorManager.removeMonitoredId(senderId) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMonitorDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newId ->
                SmsMonitorManager.addMonitoredId(newId)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MonitoredIdItem(senderId: String, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = senderId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Monitoring messages from this ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMonitorDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var senderId by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add SMS Monitor") },
        text = {
            Column {
                Text("Enter the sender ID to monitor (e.g., HDFCBK, SBIINB, VM-PAYTM)")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = senderId,
                    onValueChange = {
                        senderId = it
                        errorMessage = ""
                    },
                    label = { Text("Sender ID") },
                    placeholder = { Text("e.g., HDFCBK") },
                    isError = errorMessage.isNotEmpty(),
                    supportingText = if (errorMessage.isNotEmpty()) {
                        { Text(errorMessage) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Common formats: HDFCBK, AX-FEDBNK, VM-PAYTM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (senderId.trim().isEmpty()) {
                        errorMessage = "Sender ID cannot be empty"
                    } else {
                        onAdd(senderId.trim())
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}