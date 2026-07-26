package com.example.network

import com.example.data.TransactionEntity
import com.example.data.UserPreferencesManager
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelegramSender(private val prefsManager: UserPreferencesManager) {

    suspend fun sendTransactionResult(transaction: TransactionEntity): Boolean {
        val botToken = prefsManager.botToken.value
        val chatId = prefsManager.chatId.value
        val isEnabled = prefsManager.telegramEnabled.value

        if (!isEnabled || botToken.isBlank() || chatId.isBlank()) {
            return false
        }

        val formattedMessage = formatTransactionMessage(transaction)

        return try {
            val response = TelegramClient.service.sendMessage(
                token = botToken,
                chatId = chatId,
                text = formattedMessage,
                parseMode = "HTML"
            )
            response.isSuccessful && (response.body()?.ok == true)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun sendTestNotification(): Pair<Boolean, String> {
        val botToken = prefsManager.botToken.value
        val chatId = prefsManager.chatId.value

        if (botToken.isBlank() || chatId.isBlank()) {
            return Pair(false, "لطفاً توکن ربات و چت آی‌دی تلگرام را وارد کنید.")
        }

        val testText = """
            <b>🔔 پیام تست ربات تایید تراکنش</b>
            
            ارتباط با API تلگرام با موفقیت برقرار شد! ✅
            
            📱 <b>نام دستگاه:</b> Android Local Verification Node
            ⏱ <b>زمان:</b> ${formatTimestamp(System.currentTimeMillis())}
            
            <i>سیستم آماده ارسال خودکار وضعیت رسیدها و پیامک‌ها می‌باشد.</i>
        """.trimIndent()

        return try {
            val response = TelegramClient.service.sendMessage(
                token = botToken,
                chatId = chatId,
                text = testText,
                parseMode = "HTML"
            )
            if (response.isSuccessful && response.body()?.ok == true) {
                Pair(true, "پیام تست با موفقیت به تلگرام ارسال شد! ✅")
            } else {
                Pair(false, "خطا در تلگرام: ${response.body()?.description ?: response.message()}")
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "عدم اتصال"
            val friendlyMsg = if (errorMsg.contains("UnknownHostException") || errorMsg.contains("Failed to connect") || errorMsg.contains("timeout")) {
                "خطا: مشکل در اتصال به اینترنت یا فیلترینگ تلگرام (نیاز به VPN/پروکسی)."
            } else if (errorMsg.contains("Malformed URL") || errorMsg.contains("invalid token") || errorMsg.contains("HTTP 401") || errorMsg.contains("Unauthorized")) {
                "خطا: توکن ربات تلگرام نامعتبر است. لطفاً توکن را بررسی کنید."
            } else {
                "خطای ارتباطی: $errorMsg"
            }
            Pair(false, friendlyMsg)
        }
    }

    private fun formatTransactionMessage(tx: TransactionEntity): String {
        val formatter = NumberFormat.getInstance(Locale.Builder().setLanguage("fa").setRegion("IR").build())
        val amountFormattedToman = formatter.format(tx.amountToman)
        val amountFormattedRial = formatter.format(tx.amountRial)
        val dateStr = formatTimestamp(tx.timestamp)

        val (statusHeader, statusEmoji) = when (tx.status) {
            "MATCHED" -> Pair("🟢 <b>تراکنش تایید شد (MATCHED)</b>", "✅")
            "WAITING", "PENDING" -> Pair("⏳ <b>در انتظار پیامک بانکی (WAITING)</b>", "⏳")
            else -> Pair("🔴 <b>تراکنش ناموفق / عدم تطابق (ABORTED)</b>", "❌")
        }

        return """
            $statusHeader
            
            💵 <b>مبلغ:</b> $amountFormattedToman تومان ($amountFormattedRial ریال)
            🏦 <b>بانک:</b> ${tx.bankName}
            🔍 <b>کد رهگیری:</b> <code>${if (tx.trackingNumber.isNotBlank()) tx.trackingNumber else "ثبت نشده"}</code>
            💳 <b>کارت مبدا/فرستنده:</b> <code>${if (tx.senderCardOrName.isNotBlank()) tx.senderCardOrName else "ناشناخته"}</code>
            💳 <b>کارت مقصد/گیرنده:</b> <code>${if (tx.recipientCardOrName.isNotBlank()) tx.recipientCardOrName else "ثبت نشده"}</code>
            ⏱ <b>زمان ثبت:</b> $dateStr
            
            ℹ️ <b>توضیح/علت:</b> ${if (tx.matchReason.isNotBlank()) tx.matchReason else "پردازش توسط موتور محلی"}
            
            #تایید_تراکنش #${tx.status.lowercase()}
        """.trimIndent()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd - HH:mm:ss", Locale.US)
        return sdf.format(Date(timestamp))
    }
}
