package com.example.upaimonitor

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.upaimonitor.ui.theme.UPaiMonitorTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

// Navigation sealed class
sealed class Screen {
    object Dashboard : Screen()
    object Transactions : Screen()
    object SmsMonitors : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: TransactionViewModel by viewModels()
    private val repository = MyApp.repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove duplicates on app start
        lifecycleScope.launch {
            repository.removeDuplicates()
            viewModel.loadTransactions()
        }

        setContent {
            UPaiMonitorTheme {
                val scope = rememberCoroutineScope()

                // Observe new transactions from repository
                val newTransaction by repository.newTransactionLiveData.observeAsState()

                LaunchedEffect(newTransaction) {
                    newTransaction?.let { txn ->
                        val isDuplicate = repository.isDuplicateTransaction(txn)
                        if (!isDuplicate) {
                            viewModel.addNewTransaction(txn)
                        }
                        repository.clearNewTransaction()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadTransactions()
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppContent(viewModel: TransactionViewModel) {
    val smsPermissionState = rememberPermissionState(Manifest.permission.RECEIVE_SMS)
    val transactions by viewModel.transactions.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    BackHandler(enabled = currentScreen != Screen.Dashboard) {
        currentScreen = Screen.Dashboard
    }

    if (smsPermissionState.status.isGranted) {
        when (currentScreen) {
            Screen.Dashboard -> HomeScreenWithTodayTransactions(
                allTransactions = transactions,
                onNavigateToTransactions = { currentScreen = Screen.Transactions },
                onNavigateToMonitors = { currentScreen = Screen.SmsMonitors },
                onNavigateToSettings = { currentScreen = Screen.Settings }
            )
            Screen.Transactions -> TransactionsScreen(
                transactions = transactions,
                onBackClick = { currentScreen = Screen.Dashboard }
            )
            Screen.SmsMonitors -> SmsMonitorScreen(
                onBackClick = { currentScreen = Screen.Dashboard }
            )
            Screen.Settings -> SettingsScreen(
                onBackClick = { currentScreen = Screen.Dashboard }
            )
        }
    } else {
        PermissionScreen(onGrant = { smsPermissionState.launchPermissionRequest() })
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SMS Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("This app needs to read your SMS messages to track banking transactions.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGrant) {
            Text("Grant Permission")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenWithTodayTransactions(
    allTransactions: List<Transaction>,
    onNavigateToTransactions: () -> Unit,
    onNavigateToMonitors: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var monitoringActive by remember {
        mutableStateOf(prefs.getBoolean("monitoring_active", false))
    }

    val todayTransactions = remember(allTransactions) {
        DateFilterHelper.filterTodayTransactions(allTransactions)
    }
    val todayDate = remember { DateFilterHelper.getTodayFormatted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UPai Monitor") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            MonitoringCard(
                monitoringActive = monitoringActive,
                onCheckedChange = { isActive ->
                    monitoringActive = isActive
                    prefs.edit().putBoolean("monitoring_active", isActive).apply()
                    Toast.makeText(
                        context,
                        if (isActive) "Monitoring enabled" else "Monitoring disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Today's Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                todayDate,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${todayTransactions.size}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        val todayNet = todayTransactions.sumOf {
                            if (it.isCredit()) it.amount else -it.amount
                        }
                        val netColor = if (todayNet >= 0) Color(0xFF4CAF50) else Color(0xFFE53935)
                        val netPrefix = if (todayNet >= 0) "+" else ""
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Net Amount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "$netPrefix₹${"%.2f".format(todayNet)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = netColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Today's Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToTransactions) {
                    Text("View All")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (todayTransactions.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No transactions today",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "New transactions will appear here automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(todayTransactions.take(10), key = { it.transactionId }) { transaction ->
                        CompactTransactionCard(transaction)
                    }
                    if (todayTransactions.size > 10) {
                        item {
                            TextButton(
                                onClick = onNavigateToTransactions,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View ${todayTransactions.size - 10} more transactions")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToMonitors,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("SMS Monitors")
                }
                Button(
                    onClick = onNavigateToTransactions,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("All Transactions")
                }
            }
        }
    }
}

@Composable
fun MonitoringCard(monitoringActive: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (monitoringActive) {
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (monitoringActive) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Monitoring ${if (monitoringActive) "Active" else "Inactive"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (monitoringActive) {
                            "UPI transactions are being tracked"
                        } else {
                            "Enable to track UPI transactions"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = monitoringActive,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun CompactTransactionCard(transaction: Transaction) {
    val isCredit = transaction.isCredit()
    val amountColor = if (isCredit) Color(0xFF4CAF50) else Color(0xFFE53935)
    val amountPrefix = if (isCredit) "+" else "-"
    val backgroundColor = if (isCredit) {
        Color(0xFF4CAF50).copy(alpha = 0.08f)
    } else {
        Color(0xFFE53935).copy(alpha = 0.08f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.sender,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isCredit) "Received" else "Sent",
                        style = MaterialTheme.typography.bodySmall,
                        color = amountColor.copy(alpha = 0.8f)
                    )
                    Text(
                        " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        transaction.timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                "$amountPrefix₹${transaction.amount}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    UPaiMonitorTheme {
        val dummyTransactions = listOf(
            Transaction(
                transactionId = "1",
                amount = 90.0,
                sender = "AX-FEDBNK",
                timestamp = "Oct 15, 07:42 PM"
            ),
            Transaction(
                transactionId = "2",
                amount = 55.0,
                sender = "VM-HDFCBK",
                timestamp = "Oct 15, 01:01 PM"
            )
        )
        HomeScreenWithTodayTransactions(
            allTransactions = dummyTransactions,
            onNavigateToTransactions = {},
            onNavigateToMonitors = {},
            onNavigateToSettings = {}
        )
    }
}
