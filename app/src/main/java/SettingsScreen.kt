package com.example.upaimonitor

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: TransactionViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var monitoringActive by remember { mutableStateOf(prefs.getBoolean("monitoring_active", false)) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Monitoring toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monitoring", fontWeight = FontWeight.Bold)
                        Text(
                            if (monitoringActive) "Currently Active" else "Inactive",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = monitoringActive,
                        onCheckedChange = { isChecked ->
                            monitoringActive = isChecked
                            prefs.edit().putBoolean("monitoring_active", isChecked).apply()
                            if (isChecked) {
                                Toast.makeText(context, "Monitoring Enabled", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Monitoring Disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Clear All Transactions
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Database", fontWeight = FontWeight.Bold)
                    Text(
                        "Remove all stored transactions permanently.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                MyApp.repository.clearAllTransactions()
                            }
                            Toast.makeText(context, "All transactions cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All Transactions")
                    }
                }
            }

            // App info / about section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("About", fontWeight = FontWeight.Bold)
                    Text("UPai Monitor v1.0", style = MaterialTheme.typography.bodySmall)
                    Text("Developed by Prajwal Kini", style = MaterialTheme.typography.bodySmall)
                    Text("Credits: Adithya Pai", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}