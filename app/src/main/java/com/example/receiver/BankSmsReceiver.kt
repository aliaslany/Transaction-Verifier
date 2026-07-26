package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.AppDatabase
import com.example.data.TransactionEntity
import com.example.data.UserPreferencesManager
import com.example.network.TelegramSender
import com.example.parser.IranianBankSmsParser
import com.example.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BankSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val prefs = UserPreferencesManager(context)

            if (!prefs.autoSmsListenerEnabled.value) return

            for (sms in messages) {
                val body = sms.messageBody ?: continue
                val sender = sms.originatingAddress ?: ""

                val parsed = IranianBankSmsParser.parse(body, sender)
                if (parsed.isBankSms && parsed.amountRial > 0L) {
                    val db = AppDatabase.getDatabase(context)
                    val telegramSender = TelegramSender(prefs)
                    val repository = TransactionRepository(db.transactionDao(), telegramSender)

                    CoroutineScope(Dispatchers.IO).launch {
                        val smsEntity = TransactionEntity(
                            type = "SMS",
                            transactionType = parsed.transactionType,
                            amountRial = parsed.amountRial,
                            amountToman = parsed.amountToman,
                            senderCardOrName = parsed.accountOrCard,
                            trackingNumber = parsed.refNumber,
                            bankName = parsed.bankName,
                            status = "PENDING",
                            smsRawText = parsed.rawSms,
                            category = if (parsed.transactionType == "INCOME") "واریزی بانکی" else "برداشت بانکی"
                        )
                        repository.processIncomingSms(smsEntity)
                    }
                }
            }
        }
    }
}
