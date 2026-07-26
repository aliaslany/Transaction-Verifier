package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.TransactionEntity
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RoseDanger
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsListScreen(viewModel: MainViewModel) {
    val transactions by viewModel.allTransactions.collectAsState()
    val currencyUnit by viewModel.preferencesManager.currencyUnit.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Matched, 2: Pending, 3: Aborted

    var selectedTransactionForDetail by remember { mutableStateOf<TransactionEntity?>(null) }

    val filteredList = remember(transactions, searchQuery, selectedTab) {
        transactions.filter { tx ->
            val matchesTab = when (selectedTab) {
                1 -> tx.status == "MATCHED"
                2 -> tx.status == "PENDING" || tx.status == "WAITING"
                3 -> tx.status == "ABORTED"
                else -> true
            }

            val matchesQuery = searchQuery.isBlank() ||
                    tx.senderCardOrName.contains(searchQuery, ignoreCase = true) ||
                    tx.recipientCardOrName.contains(searchQuery, ignoreCase = true) ||
                    tx.trackingNumber.contains(searchQuery, ignoreCase = true) ||
                    tx.bankName.contains(searchQuery, ignoreCase = true) ||
                    tx.receiptRawText.contains(searchQuery, ignoreCase = true)

            matchesTab && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "تراکنش‌ها و پیامک‌های بانکی",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("جستجو براساس کد رهگیری، شماره کارت یا بانک...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs
        val tabTitles = listOf("همه (${transactions.size})", "تایید شده 🟢", "در انتظار ⏳", "ناموفق 🔴")
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = Color.Transparent
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // List
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "هیچ تراکنشی در این فیلتر یافت نشد",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList, key = { it.id }) { tx ->
                    Box(modifier = Modifier.clickable { selectedTransactionForDetail = tx }) {
                        TransactionListItem(
                            tx = tx,
                            currencyUnit = currencyUnit,
                            onAbortedClick = { viewModel.markTransactionAborted(tx) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // Detail Modal Bottom Sheet
    selectedTransactionForDetail?.let { tx ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { selectedTransactionForDetail = null },
            sheetState = sheetState
        ) {
            TransactionDetailSheet(
                tx = tx,
                currencyUnit = currencyUnit,
                onAbort = {
                    viewModel.markTransactionAborted(tx)
                    selectedTransactionForDetail = null
                },
                onDelete = {
                    viewModel.deleteTransaction(tx)
                    selectedTransactionForDetail = null
                },
                onDismiss = { selectedTransactionForDetail = null }
            )
        }
    }
}

@Composable
fun TransactionDetailSheet(
    tx: TransactionEntity,
    currencyUnit: String,
    onAbort: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "جزئیات کامل تراکنش #${tx.id}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            val (statusText, statusColor) = when (tx.status) {
                "MATCHED" -> Pair("تایید شده ✅", EmeraldPrimary)
                "PENDING", "WAITING" -> Pair("در انتظار پیامک ⏳", MaterialTheme.colorScheme.secondary)
                else -> Pair("ناموفق / لغو شده 🔴", RoseDanger)
            }

            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        val amountVal = if (currencyUnit == "TOMAN") tx.amountToman else tx.amountRial
        Text(
            text = "${formatNumber(amountVal)} ${if (currencyUnit == "TOMAN") "تومان" else "ریال"}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        ParsedDetailRow("بانک صادرکننده:", tx.bankName)
        ParsedDetailRow("کد رهگیری / مرجع:", tx.trackingNumber.ifBlank { "ثبت نشده" })
        ParsedDetailRow("کارت مبدا / فرستنده:", tx.senderCardOrName.ifBlank { "ناشناخته" })
        ParsedDetailRow("زمان ثبت:", formatDate(tx.timestamp))
        ParsedDetailRow("ارسال به تلگرام:", if (tx.telegramNotificationStatus == "SENT_SUCCESS") "ارسال شد ✅" else "ارسال نشده ❌")

        if (tx.matchReason.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("علت / شرح وضعیت:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(tx.matchReason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (tx.receiptRawText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("متن خام رسید:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(tx.receiptRawText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (tx.smsRawText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("متن خام پیامک بانکی:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(tx.smsRawText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tx.status != "ABORTED") {
                OutlinedButton(
                    onClick = onAbort,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseDanger),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("لغو / Abort")
                }
            }

            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseDanger),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("حذف")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
