package com.example.repository

import com.example.data.TransactionDao
import com.example.data.TransactionEntity
import com.example.network.TelegramSender
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val telegramSender: TelegramSender
) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val matchedTransactions: Flow<List<TransactionEntity>> = transactionDao.getTransactionsByStatus("MATCHED")
    val pendingTransactions: Flow<List<TransactionEntity>> = transactionDao.getTransactionsByStatus("PENDING")

    suspend fun processReceipt(rawReceipt: TransactionEntity): TransactionEntity {
        // Look for matching pending SMS in DB
        val matchingSms = transactionDao.findMatchingSms(
            amountRial = rawReceipt.amountRial,
            refNumber = rawReceipt.trackingNumber
        )

        val finalStatus = if (matchingSms != null) "MATCHED" else "PENDING"
        val matchReason = if (matchingSms != null) {
            "تطابق خودکار با پیامک ${matchingSms.bankName} (کد: ${matchingSms.trackingNumber.ifBlank { "مبلغ همسان" }})"
        } else {
            "رسید ثبت شد؛ در انتظار دریافت پیامک بانکی..."
        }

        val processedReceipt = rawReceipt.copy(
            status = finalStatus,
            matchReason = matchReason,
            smsRawText = matchingSms?.smsRawText ?: rawReceipt.smsRawText,
            timestamp = System.currentTimeMillis()
        )

        val insertedId = transactionDao.insertTransaction(processedReceipt)
        val savedReceipt = processedReceipt.copy(id = insertedId)

        if (matchingSms != null) {
            // Update SMS entry to MATCHED as well
            val updatedSms = matchingSms.copy(
                status = "MATCHED",
                matchReason = "تطابق با رسید #${savedReceipt.id}",
                receiptRawText = savedReceipt.receiptRawText
            )
            transactionDao.updateTransaction(updatedSms)
        }

        // Send Telegram notification
        val sentSuccess = telegramSender.sendTransactionResult(savedReceipt)
        if (sentSuccess) {
            transactionDao.updateTransaction(savedReceipt.copy(telegramNotificationStatus = "SENT_SUCCESS"))
        } else {
            transactionDao.updateTransaction(savedReceipt.copy(telegramNotificationStatus = "FAILED"))
        }

        return savedReceipt
    }

    suspend fun processIncomingSms(rawSms: TransactionEntity): TransactionEntity {
        val pendingReceipt = transactionDao.findPendingReceipt(
            amountRial = rawSms.amountRial,
            refNumber = rawSms.trackingNumber
        )

        val finalStatus = if (pendingReceipt != null) "MATCHED" else "PENDING"
        val matchReason = if (pendingReceipt != null) {
            "تطابق خودکار پیامک بانکی با رسید ثبت شده #${pendingReceipt.id}"
        } else {
            "پیامک واریز/برداشت ثبت شد؛ منتظر رسید مربوطه"
        }

        val processedSms = rawSms.copy(
            status = finalStatus,
            matchReason = matchReason,
            receiptRawText = pendingReceipt?.receiptRawText ?: rawSms.receiptRawText
        )

        val insertedId = transactionDao.insertTransaction(processedSms)
        val savedSms = processedSms.copy(id = insertedId)

        if (pendingReceipt != null) {
            val updatedReceipt = pendingReceipt.copy(
                status = "MATCHED",
                matchReason = "تطابق با پیامک بانکی ${savedSms.bankName}",
                smsRawText = savedSms.smsRawText
            )
            transactionDao.updateTransaction(updatedReceipt)

            // Notify Telegram of matched receipt
            telegramSender.sendTransactionResult(updatedReceipt)
        } else {
            // Send telegram notification for standalone SMS
            telegramSender.sendTransactionResult(savedSms)
        }

        return savedSms
    }

    suspend fun markAsAborted(transaction: TransactionEntity, reason: String) {
        val updated = transaction.copy(
            status = "ABORTED",
            matchReason = reason
        )
        transactionDao.updateTransaction(updated)
        telegramSender.sendTransactionResult(updated)
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun clearAll() {
        transactionDao.clearAllTransactions()
    }
}
