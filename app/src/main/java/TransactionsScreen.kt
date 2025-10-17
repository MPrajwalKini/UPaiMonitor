package com.example.upaimonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    transactionsFlow: StateFlow<List<Transaction>>, // Reactive transactions
    onBackClick: () -> Unit
) {
    // Collect transactions from StateFlow
    val transactions by transactionsFlow.collectAsState()

    // Filter transactions for current month
    val currentMonthTransactions = remember(transactions) {
        DateFilterHelper.filterCurrentMonthTransactions(transactions)
    }

    val currentMonthName = remember { DateFilterHelper.getCurrentMonthName() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Transactions")
                        Text(
                            currentMonthName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Summary Cards with Net Total for current month
            val netTotal = currentMonthTransactions.sumOf { transaction ->
                if (transaction.isCredit()) transaction.amount else -transaction.amount
            }

            val totalColor = if (netTotal >= 0) Color(0xFF4CAF50) else Color(0xFFE53935)
            val totalPrefix = if (netTotal >= 0) "+" else ""

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                TransactionSummaryCard(
                    title = "This Month",
                    value = currentMonthTransactions.size.toString(),
                    useColoredBackground = true
                )
                TransactionSummaryCard(
                    title = "Net Amount",
                    value = "$totalPrefix₹${"%.2f".format(netTotal)}",
                    valueColor = totalColor,
                    useColoredBackground = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Transaction History - $currentMonthName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (currentMonthTransactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No transactions this month",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Transactions will appear here once detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        currentMonthTransactions.sortedByDescending {
                            DateFilterHelper.parseTimestamp(it.timestamp)?.time ?: 0L
                        },
                        key = { it.transactionId }
                    ) { transaction ->
                        DetailedTransactionItem(transaction)
                    }
                }
            }
        }
    }
}
@Composable
fun TransactionSummaryCard(
    title: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    useColoredBackground: Boolean = true
) {
    Card(
        modifier = Modifier.size(width = 160.dp, height = 90.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (useColoredBackground) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (valueColor != Color.Unspecified) valueColor else {
                    if (useColoredBackground) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = title,
                fontSize = 14.sp,
                color = if (useColoredBackground) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DetailedTransactionItem(transaction: Transaction) {
    val isCredit = transaction.isCredit()
    val amountColor = if (isCredit) Color(0xFF4CAF50) else Color(0xFFE53935)
    val amountPrefix = if (isCredit) "+" else "-"
    val backgroundColor = if (isCredit) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFFE53935).copy(alpha = 0.1f)
    val arrowIcon = if (isCredit) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown

    val parsedDate = DateFilterHelper.parseTimestamp(transaction.timestamp)
    val parsedTime = parsedDate?.time ?: 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.small,
                        color = amountColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(arrowIcon, contentDescription = if (isCredit) "Credit" else "Debit",
                                tint = amountColor, modifier = Modifier.size(32.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "$amountPrefix₹${"%.2f".format(transaction.amount)}",
                            color = amountColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isCredit) "Money Received" else "Money Sent",
                            fontSize = 12.sp,
                            color = amountColor.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(Icons.Default.CheckCircle,
                    contentDescription = "Synced",
                    tint = if (transaction.isSynced) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = amountColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = if (isCredit) "From:" else "To:", fontSize = 12.sp, color = Color.Gray)
                    Text(text = transaction.sender, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Date:", fontSize = 12.sp, color = Color.Gray)
                    Text(text = transaction.timestamp, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (!transaction.transactionId.startsWith("T")) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "UPI Ref:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = transaction.transactionId,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium)
                }
            }

            if (transaction.message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = amountColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Message:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = transaction.message, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
        }
    }
}
