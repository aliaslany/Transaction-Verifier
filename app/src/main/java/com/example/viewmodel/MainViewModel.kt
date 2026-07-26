package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TransactionEntity
import com.example.data.UserPreferencesManager
import com.example.network.TelegramSender
import com.example.parser.IranianBankSmsParser
import com.example.parser.IranianReceiptParser
import com.example.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardSummary(
    val totalIncomeToman: Long = 0L,
    val totalExpenseToman: Long = 0L,
    val netBalanceToman: Long = 0L,
    val totalTransactionsCount: Int = 0,
    val matchedCount: Int = 0,
    val pendingCount: Int = 0,
    val abortedCount: Int = 0,
    val matchRatePercent: Int = 100
)

data class UiNotification(
    val message: String,
    val isSuccess: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val preferencesManager = UserPreferencesManager(application)
    val telegramSender = TelegramSender(preferencesManager)
    val repository = TransactionRepository(db.transactionDao(), telegramSender)

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dashboardSummary: StateFlow<DashboardSummary> = allTransactions.map { list ->
        var income = 0L
        var expense = 0L
        var matched = 0
        var pending = 0
        var aborted = 0

        list.forEach { tx ->
            if (tx.transactionType == "INCOME") {
                income += tx.amountToman
            } else {
                expense += tx.amountToman
            }

            when (tx.status) {
                "MATCHED" -> matched++
                "PENDING", "WAITING" -> pending++
                "ABORTED" -> aborted++
            }
        }

        val total = list.size
        val rate = if (total > 0) ((matched.toDouble() / total.toDouble()) * 100).toInt() else 100

        DashboardSummary(
            totalIncomeToman = income,
            totalExpenseToman = expense,
            netBalanceToman = income - expense,
            totalTransactionsCount = total,
            matchedCount = matched,
            pendingCount = pending,
            abortedCount = aborted,
            matchRatePercent = rate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardSummary()
    )

    private val _uiNotification = MutableStateFlow<UiNotification?>(null)
    val uiNotification: StateFlow<UiNotification?> = _uiNotification.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun dismissNotification() {
        _uiNotification.value = null
    }

    fun verifyReceiptText(rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _isProcessing.value = true
            val parsed = IranianReceiptParser.parse(rawText)

            val tx = TransactionEntity(
                type = "RECEIPT",
                transactionType = "INCOME",
                amountRial = parsed.amountRial,
                amountToman = parsed.amountToman,
                senderCardOrName = parsed.senderCard,
                recipientCardOrName = parsed.recipientCard,
                trackingNumber = parsed.trackingNumber,
                bankName = parsed.bankName,
                status = "PENDING",
                receiptRawText = parsed.rawText,
                category = "کارت به کارت"
            )

            val processed = repository.processReceipt(tx)
            _isProcessing.value = false

            val statusMessage = if (processed.status == "MATCHED") {
                "رسید تایید شد و با پیامک بانکی تطابق یافت! ✅"
            } else {
                "رسید ثبت شد. در انتظار پیامک بانکی جهت تایید... ⏳"
            }

            _uiNotification.value = UiNotification(statusMessage, true)
        }
    }

    fun simulateBankSms(smsText: String, sender: String = "6000123") {
        if (smsText.isBlank()) return
        viewModelScope.launch {
            _isProcessing.value = true
            val parsed = IranianBankSmsParser.parse(smsText, sender)

            val smsTx = TransactionEntity(
                type = "SMS",
                transactionType = parsed.transactionType,
                amountRial = parsed.amountRial,
                amountToman = parsed.amountToman,
                senderCardOrName = parsed.accountOrCard,
                trackingNumber = parsed.refNumber,
                bankName = parsed.bankName,
                status = "PENDING",
                smsRawText = parsed.rawSms,
                category = if (parsed.transactionType == "INCOME") "واریز بانکی" else "برداشت بانکی"
            )

            val processed = repository.processIncomingSms(smsTx)
            _isProcessing.value = false

            _uiNotification.value = UiNotification(
                "پیامک دریافت شد: ${processed.bankName} (${if (processed.status == "MATCHED") "تطابق یافته ✅" else "ثبت گردید ⏳"})",
                true
            )
        }
    }

    fun markTransactionAborted(tx: TransactionEntity, reason: String = "لغو توسط کاربر یا عدم تطابق شماره کارت/مبلغ") {
        viewModelScope.launch {
            repository.markAsAborted(tx, reason)
            _uiNotification.value = UiNotification("تراکنش به حالت لغو شده (ABORTED) تغییر یافت. 🔴", false)
        }
    }

    fun sendTestTelegramMessage() {
        viewModelScope.launch {
            _isProcessing.value = true
            val result = telegramSender.sendTestNotification()
            _isProcessing.value = false
            _uiNotification.value = UiNotification(result.second, result.first)
        }
    }

    fun updateTelegramSettings(token: String, chatId: String, enabled: Boolean) {
        preferencesManager.updateTelegramConfig(token, chatId, enabled)
        _uiNotification.value = UiNotification("تنظیمات تلگرام با موفقیت ذخیره شد.", true)
    }

    fun setCurrencyUnit(unit: String) {
        preferencesManager.setCurrencyUnit(unit)
    }

    fun setAutoSmsListener(enabled: Boolean) {
        preferencesManager.setAutoSmsListenerEnabled(enabled)
    }

    fun addManualTransaction(
        title: String,
        amountToman: Long,
        type: String, // INCOME or EXPENSE
        category: String
    ) {
        viewModelScope.launch {
            val tx = TransactionEntity(
                type = "MANUAL",
                transactionType = type,
                amountRial = amountToman * 10,
                amountToman = amountToman,
                senderCardOrName = title,
                bankName = "دستی",
                status = "MATCHED",
                matchReason = "تراکنش دستی ثبت شده در سیستم",
                category = category,
                receiptRawText = "ثبت دستی: $title"
            )
            repository.insertTransaction(tx)
            _uiNotification.value = UiNotification("تراکنش با موفقیت ثبت شد.", true)
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
            _uiNotification.value = UiNotification("تراکنش حذف شد.", true)
        }
    }

    fun populateSampleData() {
        viewModelScope.launch {
            _isProcessing.value = true
            val now = System.currentTimeMillis()
            val dayInMs = 86400000L

            val samples = listOf(
                TransactionEntity(
                    type = "RECEIPT",
                    transactionType = "INCOME",
                    amountRial = 50000000L,
                    amountToman = 5000000L,
                    senderCardOrName = "6037****8821 (رضا محمدی)",
                    recipientCardOrName = "6104****1102",
                    trackingNumber = "982341",
                    bankName = "بانک ملی ایران",
                    status = "MATCHED",
                    receiptRawText = "رسید انتقال کارت به کارت\nمبلغ: ۵,۰۰۰,۰۰۰ تومان\nکد رهگیری: 982341\nمبدا: رضا محمدی",
                    smsRawText = "واریز ۵۰,۰۰۰,۰۰۰ ریال به حساب ۶۱۰۴...۱۱۰۲ از کارت ۶۰۳۷...۸۸۲۱ کد: 982341",
                    timestamp = now - (dayInMs * 1),
                    matchReason = "تطابق خودکار رسید با پیامک بانکی",
                    category = "فروش کالا"
                ),
                TransactionEntity(
                    type = "RECEIPT",
                    transactionType = "INCOME",
                    amountRial = 15000000L,
                    amountToman = 1500000L,
                    senderCardOrName = "6219****3341 (علی حسینی)",
                    recipientCardOrName = "6104****1102",
                    trackingNumber = "771029",
                    bankName = "بانک سامان",
                    status = "PENDING",
                    receiptRawText = "رسید کارت به کارت ۷۲۴\nمبلغ: ۱۵,۰۰۰,۰۰۰ ریال\nکد پیگیری: 771029\nفرستنده: علی حسینی",
                    timestamp = now - (dayInMs * 0.2).toLong(),
                    matchReason = "رسید ثبت شد؛ در انتظار دریافت پیامک بانکی...",
                    category = "کارت به کارت"
                ),
                TransactionEntity(
                    type = "SMS",
                    transactionType = "EXPENSE",
                    amountRial = 24000000L,
                    amountToman = 2400000L,
                    senderCardOrName = "کارت 6104...1102",
                    trackingNumber = "441920",
                    bankName = "بانک ملت",
                    status = "MATCHED",
                    smsRawText = "برداشت ۲۴,۰۰۰,۰۰۰ ریال از حساب ۶۱۰۴...۱۱۰۲ خرید فروشگاهی کد: 441920",
                    timestamp = now - (dayInMs * 2),
                    matchReason = "تراکنش خرید فروشگاهی",
                    category = "خرید و هزینه‌ها"
                ),
                TransactionEntity(
                    type = "RECEIPT",
                    transactionType = "INCOME",
                    amountRial = 30000000L,
                    amountToman = 3000000L,
                    senderCardOrName = "5892****9988",
                    trackingNumber = "102938",
                    bankName = "بانک سپه",
                    status = "ABORTED",
                    receiptRawText = "رسید کارت به کارت مشکوک\nمبلغ: ۳,۰۰۰,۰۰۰ تومان\nکد: 102938",
                    timestamp = now - (dayInMs * 3),
                    matchReason = "عدم دریافت پیامک بانکی پس از مهلت زمانی مشخص (تراکنش لغو گردید)",
                    category = "کارت به کارت"
                )
            )

            samples.forEach { repository.insertTransaction(it) }
            _isProcessing.value = false
            _uiNotification.value = UiNotification("داده‌های نمونه آزمایش سیستم اضافه شدند.", true)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            _uiNotification.value = UiNotification("تمام داده‌ها پاکسازی شدند.", true)
        }
    }
}
