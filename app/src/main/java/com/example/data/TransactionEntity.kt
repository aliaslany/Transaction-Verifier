package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "RECEIPT", // RECEIPT, SMS, MATCHED_PAIR, MANUAL
    val transactionType: String = "INCOME", // INCOME (واریز), EXPENSE (برداشت)
    val amountRial: Long = 0L,
    val amountToman: Long = 0L,
    val senderCardOrName: String = "",
    val recipientCardOrName: String = "",
    val trackingNumber: String = "", // کد رهگیری / شماره پیگیری
    val bankName: String = "ناشناخته",
    val status: String = "PENDING", // PENDING (در انتظار پیامک), MATCHED (تایید شده), ABORTED (ناموفق)
    val receiptRawText: String = "",
    val smsRawText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val telegramNotificationStatus: String = "NOT_SENT", // NOT_SENT, SENT_SUCCESS, FAILED
    val matchReason: String = "",
    val category: String = "کارت به کارت"
)
