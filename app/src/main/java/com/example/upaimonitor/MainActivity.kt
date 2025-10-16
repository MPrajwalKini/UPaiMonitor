package com.example.upaimonitor

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import java.text.SimpleDateFormat
import com.example.upaimonitor.Transaction
import java.util.*

// Helper: checks if a transaction timestamp is from today
//fun isToday(timestamp: String): Boolean {
//    return try {
//        val formats = listOf(
//            SimpleDateFormat("MMM dd yyyy, hh:mm a", Locale.getDefault()),
//            SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()),
//            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
//        )
//
//        val transactionDate = formats.firstNotNullOfOrNull { fmt ->
//            runCatching { fmt.parse(timestamp) }.getOrNull()
//        } ?: return false
//
//        val today = Calendar.getInstance()
//        val txnCalendar = Calendar.getInstance().apply { time = transactionDate }
//
//        today.get(Calendar.YEAR) == txnCalendar.get(Calendar.YEAR) &&
//                today.get(Calendar.DAY_OF_YEAR) == txnCalendar.get(Calendar.DAY_OF_YEAR)
//    } catch (e: Exception) {
//        false
//    }
//}

// Helper: checks if a transaction timestamp is from today
object DateFilterHelper {
    private val formats = listOf(
        SimpleDateFormat("MMM dd yyyy, hh:mm a", Locale.getDefault()),
        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()),
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    )
    private val todayFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    private fun isToday(timestamp: String): Boolean {
        return try {
            val transactionDate = formats.firstNotNullOfOrNull { fmt ->
                runCatching { fmt.parse(timestamp) }.getOrNull()
            } ?: return false

            val today = Calendar.getInstance()
            val txnCalendar = Calendar.getInstance().apply { time = transactionDate }

            today.get(Calendar.YEAR) == txnCalendar.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == txnCalendar.get(Calendar.DAY_OF_YEAR)
        } catch (e: Exception) {
            false
        }
    }

    fun filterTodayTransactions(transactions: List<Transaction>): List<Transaction> {
        return transactions.filter { isToday(it.timestamp) }
    }

    fun getTodayFormatted(): String {
        return todayFormatter.format(Date())
    }
}


// Navigation sealed class (Combined features from Code 1 & 2)
sealed class Screen {
    object Dashboard : Screen()
    object Transactions : Screen()
    object SmsMonitors : Screen()
    object ManageDuplicates : Screen()
    object Settings : Screen() // Added from Code 2
}

class MainActivity : ComponentActivity() {
    private val viewModel: TransactionViewModel by viewModels()
    private val repository = MyApp.repository // Using Code 2 style repository access

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clean up existing duplicates on app start
        lifecycleScope.launch {
            val removedCount = repository.removeDuplicates()
            if (removedCount > 0) {
                Log.d("MainActivity", "Cleaned up $removedCount duplicate transactions")
                viewModel.loadTransactions()
            }
        }

        setContent {
            UPaiMonitorTheme {
                val scope = rememberCoroutineScope()

                // Observe LiveData emitted when new SMS transactions are detected
                val newTransaction by repository.newTransactionLiveData.observeAsState()

                // When a new SMS is detected, check for duplicates before storing
                LaunchedEffect(newTransaction) {
                    newTransaction?.let { txn ->
                        // Check for duplicates before inserting
                        val isDuplicate = repository.isDuplicateTransaction(txn)

                        if (!isDuplicate) {
                            viewModel.addNewTransaction(txn)
                            scope.launch {
                                repository.insert(txn)
                            }
                        } else {
                            Log.d("MainActivity", "Duplicate transaction detected and skipped")
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

    // Added from Code 2 to refresh data when app comes to foreground
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
            Screen.Dashboard -> UPaiDashboard( // Kept original function name
                transactions = transactions,
                onTransactionsClick = { currentScreen = Screen.Transactions },
                onSmsMonitorsClick = { currentScreen = Screen.SmsMonitors },
                onManageDuplicatesClick = { currentScreen = Screen.ManageDuplicates },
                onSettingsClick = { currentScreen = Screen.Settings } // Added
            )
            Screen.Transactions -> TransactionsScreen(
                transactions = transactions,
                onBackClick = { currentScreen = Screen.Dashboard }
            )
            Screen.SmsMonitors -> SmsMonitorScreen(
                onBackClick = { currentScreen = Screen.Dashboard }
            )
            Screen.ManageDuplicates -> ManageDuplicatesScreen(
                transactions = transactions,
                onBackClick = { currentScreen = Screen.Dashboard },
                onTransactionsDeleted = {
                    viewModel.loadTransactions()
                }
            )
            Screen.Settings -> SettingsScreen( // Added
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
fun UPaiDashboard( // Kept original function name
    transactions: List<Transaction>,
    onTransactionsClick: () -> Unit,
    onSmsMonitorsClick: () -> Unit,
    onManageDuplicatesClick: () -> Unit,
    onSettingsClick: () -> Unit // Added from Code 2
) {
    val context = LocalContext.current
    // Use SharedPreferences for monitoring persistence (Code 2 logic)
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var monitoringActive by remember {
        mutableStateOf(prefs.getBoolean("monitoring_active", true))
    }

    val todayTransactions = remember(transactions) {
        DateFilterHelper.filterTodayTransactions(transactions)
    }
    val todayDate = remember { DateFilterHelper.getTodayFormatted() }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UPai Monitor") }, // Changed title to be less redundant
                actions = {
                    IconButton(onClick = onSettingsClick) { // Navigate to Settings (Code 2)
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Monitoring Card (from Code 1/2, updated with persistence logic)
            MonitoringCard(
                monitoringActive = monitoringActive,
                onCheckedChange = { isActive ->
                    monitoringActive = isActive
                    // Persist state in SharedPreferences (Code 2 logic)
                    prefs.edit().putBoolean("monitoring_active", isActive).apply()
                    Toast.makeText(
                        context,
                        if (isActive) "Monitoring enabled" else "Monitoring disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Dashboard Summary (Replaced Code 1's simple SummaryCard with Code 2's Today's Activity Card)
            DashboardSummary(todayTransactions, todayDate)

            Spacer(modifier = Modifier.height(20.dp))

            // Recent Transactions (Updated to display Today's transactions and use CompactCard)
            RecentTransactions(todayTransactions, onTransactionsClick)

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions (Updated button layout from Code 2)
            QuickActions(
                onTransactionsClick = onTransactionsClick,
                onSmsMonitorsClick = onSmsMonitorsClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Manage Duplicates Button (from Code 1)
            OutlinedButton(
                onClick = onManageDuplicatesClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Duplicate Transactions")
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
                Color(0xFF4CAF50).copy(alpha = 0.1f) // Light green when active
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
                // Status indicator icon
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

// Replaced Code 1's DashboardSummary with Code 2's Today's Activity Card logic
@Composable
fun DashboardSummary(todayTransactions: List<Transaction>, todayDate: String) {
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
}


// Removed Code 1's SummaryCard as it's consolidated into DashboardSummary

@Composable
fun QuickActions(onTransactionsClick: () -> Unit, onSmsMonitorsClick: () -> Unit) {
    // Used the button structure from Code 2 for a cleaner look
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onSmsMonitorsClick,
            modifier = Modifier.weight(1f)
        ) {
            Text("SMS Monitors")
        }
        Button(
            onClick = onTransactionsClick,
            modifier = Modifier.weight(1f)
        ) {
            Text("All Transactions")
        }
    }
}

// Updated from Code 1: filters for today's transactions and uses CompactTransactionCard
@Composable
fun RecentTransactions(transactions: List<Transaction>, onNavigateToTransactions: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Today's Transactions",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = onNavigateToTransactions) {
                Text("View All")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No transactions today", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("New transactions will appear here automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 300.dp) // Max height to keep dashboard compact
            ) {
                items(transactions.take(10), key = { it.transactionId }) { transaction ->
                    CompactTransactionCard(transaction)
                }
                if (transactions.size > 10) {
                    item {
                        TextButton(
                            onClick = onNavigateToTransactions,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View ${transactions.size - 10} more transactions")
                        }
                    }
                }
            }
        }
    }
}

//@Composable
//fun RecentTransactions(transactions: List<Transaction>) {
//    // Filter only today's transactions
//    val todayTransactions = transactions.filter { transaction ->
//        isToday(transaction.timestamp)
//    }
//
//    Column(modifier = Modifier.fillMaxWidth()) {
//        Text(
//            "Today's Transactions",
//            fontWeight = FontWeight.Bold,
//            style = MaterialTheme.typography.titleLarge
//        )
//        Spacer(modifier = Modifier.height(8.dp))
//
//        if (todayTransactions.isEmpty()) {
//            Text(
//                "No transactions today.",
//                color = Color.Gray,
//                style = MaterialTheme.typography.bodyMedium
//            )
//        } else {
//            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
//                items(todayTransactions.take(5), key = { it.transactionId }) { transaction ->
//                    TransactionItem(transaction)
//                }
//            }
//        }
//    }
//}

// New Transaction Item (from Code 2) - Kept original function name for simplicity but implemented Code 2's logic
@Composable
fun TransactionItem(transaction: Transaction) {
    // Rerouted to the new, preferred compact card design
    CompactTransactionCard(transaction)
}

// New CompactTransactionCard (from Code 2) - This implements the actual visual logic
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


// Placeholder Screens (For navigation from Code 1 & 2)

@Composable
fun TransactionsScreen(transactions: List<Transaction>, onBackClick: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("All Transactions") }, navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back") } }) }) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions, key = { it.transactionId }) { transaction ->
                CompactTransactionCard(transaction)
            }
        }
    }
}

@Composable
fun SmsMonitorScreen(onBackClick: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("SMS Monitors") }, navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back") } }) }) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text("SMS Monitor management screen content.")
        }
    }
}

@Composable
fun ManageDuplicatesScreen(transactions: List<Transaction>, onBackClick: () -> Unit, onTransactionsDeleted: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Manage Duplicates") }, navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back") } }) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text("Screen to view and delete duplicate transactions.")
        }
    }
}

@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back") } }) }) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text("General application settings will go here.")
        }
    }
}

// Minimal Transaction class definition for preview/compilation
data class Transaction(
    val transactionId: String,
    val amount: Double,
    val sender: String,
    val timestamp: String,
    val type: String = if (amount > 0) "CREDIT" else "DEBIT"
) {
    fun isCredit() = type == "CREDIT" || amount > 0
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    UPaiMonitorTheme {
        val today = DateFilterHelper.getTodayFormatted()
        val dummyTransactions = listOf(
            Transaction(
                transactionId = "1",
                amount = 90.0,
                sender = "AX-FEDBNK-S",
                timestamp = "$today, 07:42 PM"
            ),
            Transaction(
                transactionId = "2",
                amount = 55.0,
                sender = "VM-HDFCBK",
                timestamp = "$today, 01:01 PM"
            ),
            Transaction(
                transactionId = "3",
                amount = -150.0,
                sender = "GPAY-MERCHANT",
                timestamp = "$today, 10:30 AM"
            )
        )
        UPaiDashboard(
            transactions = dummyTransactions,
            onTransactionsClick = {},
            onSmsMonitorsClick = {},
            onManageDuplicatesClick = {},
            onSettingsClick = {}
        )
    }
}