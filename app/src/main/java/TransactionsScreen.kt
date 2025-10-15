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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Transactions") },
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
            // Summary Cards with Net Total
            val netTotal = transactions.sumOf { transaction ->
                if (transaction.isCredit()) {
                    transaction.amount  // Add credits
                } else {
                    -transaction.amount  // Subtract debits
                }
            }

            val totalColor = if (netTotal >= 0) Color(0xFF4CAF50) else Color(0xFFE53935)
            val totalPrefix = if (netTotal >= 0) "+" else ""

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                TransactionSummaryCard(
                    title = "Total",
                    value = transactions.size.toString(),
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
                "Transaction History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No transactions yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transactions, key = { it.transactionId }) { transaction ->
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
            containerColor = if (useColoredBackground) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
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
                    if (useColoredBackground) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                }
            )
            Text(
                text = title,
                fontSize = 14.sp,
                color = if (useColoredBackground) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header row with amount and icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Transaction type icon - Down arrow for credit, Up arrow for debit
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = amountColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = arrowIcon,
                                contentDescription = if (isCredit) "Credit" else "Debit",
                                tint = amountColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "$amountPrefix₹${transaction.amount}",
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

                // Sync status
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Synced",
                    tint = if (transaction.isSynced) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = amountColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            // Details section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isCredit) "From:" else "To:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = transaction.sender,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Date:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = transaction.timestamp,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Show transaction ID if not auto-generated
            if (!transaction.transactionId.startsWith("T")) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UPI Ref:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = transaction.transactionId,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (transaction.message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = amountColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Message:",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transaction.message,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}