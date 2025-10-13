package com.example.upaimonitor

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
}

class MainActivity : ComponentActivity() {
    private val viewModel: TransactionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clean up existing duplicates on app start
        lifecycleScope.launch {
            val removedCount = MyApp.repository.removeDuplicates()
            if (removedCount > 0) {
                Log.d("MainActivity", "Cleaned up $removedCount duplicate transactions")
                viewModel.loadTransactions()
            }
        }

        setContent {
            UPaiMonitorTheme {
                val scope = rememberCoroutineScope()

                // Observe LiveData emitted when new SMS transactions are detected
                val newTransaction by MyApp.repository.newTransactionLiveData.observeAsState()

                // When a new SMS is detected, check for duplicates before storing
                LaunchedEffect(newTransaction) {
                    newTransaction?.let { txn ->
                        // Check for duplicates before inserting
                        val isDuplicate = MyApp.repository.isDuplicateTransaction(txn)

                        if (!isDuplicate) {
                            viewModel.addNewTransaction(txn)
                            scope.launch {
                                MyApp.repository.insert(txn)
                            }
                        } else {
                            Log.d("MainActivity", "Duplicate transaction detected and skipped")
                        }
                        MyApp.repository.clearNewTransaction()
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
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppContent(viewModel: TransactionViewModel) {
    val smsPermissionState = rememberPermissionState(Manifest.permission.RECEIVE_SMS)
    val transactions by viewModel.transactions.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    if (smsPermissionState.status.isGranted) {
        when (currentScreen) {
            Screen.Dashboard -> UPaiDashboard(
                transactions = transactions,
                onTransactionsClick = { currentScreen = Screen.Transactions },
                onSmsMonitorsClick = { currentScreen = Screen.SmsMonitors }
            )
            Screen.Transactions -> TransactionsScreen(
                transactions = transactions,
                onBackClick = { currentScreen = Screen.Dashboard }
            )
            Screen.SmsMonitors -> SmsMonitorScreen(
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
fun UPaiDashboard(
    transactions: List<Transaction>,
    onTransactionsClick: () -> Unit,
    onSmsMonitorsClick: () -> Unit
) {
    var monitoringActive by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UPai Dashboard") },
                actions = {
                    IconButton(onClick = { /* Settings action */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MonitoringCard(
                monitoringActive = monitoringActive,
                onCheckedChange = { monitoringActive = it }
            )
            Spacer(modifier = Modifier.height(16.dp))
            DashboardSummary(transactions)
            Spacer(modifier = Modifier.height(16.dp))
            QuickActions(
                onTransactionsClick = onTransactionsClick,
                onSmsMonitorsClick = onSmsMonitorsClick
            )
            Spacer(modifier = Modifier.height(16.dp))
            RecentTransactions(transactions)
        }
    }
}

@Composable
fun MonitoringCard(monitoringActive: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Monitoring Active", fontWeight = FontWeight.Bold)
                Text(
                    "UPI transactions are being tracked",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = monitoringActive,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun DashboardSummary(transactions: List<Transaction>) {
    // Calculate net total: credits (+) minus debits (-)
    val totalAmount = transactions.sumOf { transaction ->
        if (transaction.isCredit()) {
            transaction.amount  // Add credits
        } else {
            -transaction.amount  // Subtract debits
        }
    }

    val totalColor = if (totalAmount >= 0) Color.Green else Color.Red
    val totalPrefix = if (totalAmount >= 0) "+" else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        SummaryCard("Transactions", transactions.size.toString())
        SummaryCard(
            "Net Total",
            "$totalPrefix₹${"%.2f".format(totalAmount)}",
            valueColor = totalColor
        )
    }
}

@Composable
fun SummaryCard(title: String, value: String, valueColor: Color = Color.Unspecified) {
    Card(
        modifier = Modifier.size(width = 150.dp, height = 80.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(text = title, fontSize = 14.sp)
        }
    }
}

@Composable
fun QuickActions(onTransactionsClick: () -> Unit, onSmsMonitorsClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Quick Actions", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTransactionsClick) {
                    Text("Transactions")
                }
                Button(onClick = onSmsMonitorsClick) {
                    Text("SMS Monitors")
                }
            }
        }
    }
}

@Composable
fun RecentTransactions(transactions: List<Transaction>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Recent Transactions",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (transactions.isEmpty()) {
            Text("No transactions found yet. Add SMS monitors to start tracking.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(transactions.take(5), key = { it.transactionId }) { transaction ->
                    TransactionItem(transaction)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    val isCredit = transaction.isCredit()
    val amountColor = if (isCredit) Color.Green else Color.Red
    val amountPrefix = if (isCredit) "+" else "-"
    val arrowIcon = if (isCredit) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
    val arrowTint = if (isCredit) Color.Green else Color.Red

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "$amountPrefix₹${transaction.amount}",
                    color = amountColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "From: ${transaction.sender}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = transaction.timestamp,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = arrowIcon,
                contentDescription = if (isCredit) "Credit" else "Debit",
                tint = arrowTint,
                modifier = Modifier.size(32.dp)
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
                sender = "AX-FEDBNK-S",
                timestamp = "Oct 10, 07:42 PM"
            ),
            Transaction(
                transactionId = "2",
                amount = 55.0,
                sender = "VM-HDFCBK",
                timestamp = "Oct 10, 01:01 PM"
            )
        )
        UPaiDashboard(
            transactions = dummyTransactions,
            onTransactionsClick = {},
            onSmsMonitorsClick = {}
        )
    }
}