package com.example.upaimonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDuplicatesScreen(
    transactions: List<Transaction>,
    onBackClick: () -> Unit,
    onTransactionsDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTransactions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // Group potential duplicates
    val duplicateGroups = remember(transactions) {
        groupPotentialDuplicates(transactions)
    }

    val hasSelection = selectedTransactions.isNotEmpty()
    val selectionMode = selectedTransactions.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "${selectedTransactions.size} selected"
                        else "Manage Duplicates"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasSelection) {
                        TextButton(onClick = { selectedTransactions = emptySet() }) {
                            Text("Clear")
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !isDeleting
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
            if (duplicateGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No duplicate transactions found!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "All your transactions are unique",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                // Info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Found ${duplicateGroups.size} duplicate group(s)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Select transactions you want to delete",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Duplicate groups list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    duplicateGroups.forEachIndexed { groupIndex, group ->
                        item {
                            DuplicateGroupCard(
                                groupNumber = groupIndex + 1,
                                transactions = group,
                                selectedTransactions = selectedTransactions,
                                onTransactionToggle = { transactionId ->
                                    selectedTransactions = if (selectedTransactions.contains(transactionId)) {
                                        selectedTransactions - transactionId
                                    } else {
                                        selectedTransactions + transactionId
                                    }
                                },
                                onSelectAll = {
                                    // Select all but keep first (don't select the original)
                                    selectedTransactions = selectedTransactions + group.drop(1).map { it.transactionId }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${selectedTransactions.size} Transaction(s)?") },
            text = { Text("This action cannot be undone. The selected transactions will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isDeleting = true
                            withContext(Dispatchers.IO) {
                                selectedTransactions.forEach { txId ->
                                    val tx = transactions.find { it.transactionId == txId }
                                    tx?.let { MyApp.repository.deleteTransaction(it) }
                                }
                            }
                            isDeleting = false
                            selectedTransactions = emptySet()
                            showDeleteDialog = false
                            onTransactionsDeleted()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DuplicateGroupCard(
    groupNumber: Int,
    transactions: List<Transaction>,
    selectedTransactions: Set<String>,
    onTransactionToggle: (String) -> Unit,
    onSelectAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Group $groupNumber",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "${transactions.size} transactions • ₹${transactions[0].amount}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                TextButton(onClick = onSelectAll) {
                    Text("Select Duplicates")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Transaction items in group
            transactions.forEachIndexed { index, transaction ->
                SelectableTransactionItem(
                    transaction = transaction,
                    isSelected = selectedTransactions.contains(transaction.transactionId),
                    onToggle = { onTransactionToggle(transaction.transactionId) },
                    isOriginal = index == 0 // First one is considered original
                )

                if (index < transactions.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SelectableTransactionItem(
    transaction: Transaction,
    isSelected: Boolean,
    onToggle: () -> Unit,
    isOriginal: Boolean
) {
    val isCredit = transaction.isCredit()
    val amountColor = if (isCredit) Color(0xFF4CAF50) else Color(0xFFE53935)
    val amountPrefix = if (isCredit) "+" else "-"
    val arrowIcon = if (isCredit) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when {
            isOriginal -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            isSelected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
        onClick = if (!isOriginal) onToggle else ({})
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox (disabled for original)
            if (isOriginal) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "✓",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction icon
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = amountColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$amountPrefix₹${transaction.amount}",
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        fontSize = 16.sp
                    )

                    if (isOriginal) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "KEEP",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = transaction.sender,
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Text(
                    text = transaction.timestamp,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * Groups transactions that are potential duplicates
 */
private fun groupPotentialDuplicates(transactions: List<Transaction>): List<List<Transaction>> {
    val groups = mutableListOf<List<Transaction>>()
    val processed = mutableSetOf<String>()

    for (transaction in transactions) {
        if (processed.contains(transaction.transactionId)) continue

        // Find all similar transactions
        val similarTransactions = transactions.filter { other ->
            !processed.contains(other.transactionId) &&
                    transaction.amount == other.amount &&
                    areTimestampsClose(transaction.timestamp, other.timestamp, 60000)
        }

        // If we found duplicates, add as a group
        if (similarTransactions.size > 1) {
            groups.add(similarTransactions.sortedBy { parseTimestamp(it.timestamp) })
            processed.addAll(similarTransactions.map { it.transactionId })
        } else {
            processed.add(transaction.transactionId)
        }
    }

    return groups
}

private fun areTimestampsClose(timestamp1: String, timestamp2: String, thresholdMillis: Long): Boolean {
    return try {
        val time1 = parseTimestamp(timestamp1)
        val time2 = parseTimestamp(timestamp2)
        Math.abs(time1 - time2) <= thresholdMillis
    } catch (e: Exception) {
        false
    }
}

private fun parseTimestamp(timestamp: String): Long {
    return try {
        val format = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
        format.parse(timestamp)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}
