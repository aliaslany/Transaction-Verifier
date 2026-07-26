package com.example.parser

import java.util.regex.Pattern

data class ParsedBankSms(
    val transactionType: String, // INCOME (واریز), EXPENSE (برداشت)
    val amountRial: Long,
    val amountToman: Long,
    val accountOrCard: String,
    val refNumber: String,
    val bankName: String,
    val rawSms: String,
    val isBankSms: Boolean
)

object IranianBankSmsParser {

    fun parse(rawSms: String, senderAddress: String = ""): ParsedBankSms {
        if (rawSms.isBlank()) {
            return ParsedBankSms("INCOME", 0L, 0L, "", "", "ناشناخته", rawSms, false)
        }

        val cleaned = normalizeDigits(rawSms)

        // Detect if Bank SMS
        val isIncome = cleaned.contains("واریز") || cleaned.contains("افزایش") || cleaned.contains("ورودی") || cleaned.contains("کارت به کارت")
        val isExpense = cleaned.contains("برداشت") || cleaned.contains("خرید") || cleaned.contains("انتقال") || cleaned.contains("کاهش")

        val isBankKeywords = isIncome || isExpense || cleaned.contains("بانک") || cleaned.contains("موجودی") || cleaned.contains("مانده") || senderAddress.startsWith("1000") || senderAddress.startsWith("2000") || senderAddress.startsWith("3000") || senderAddress.startsWith("6000")

        if (!isBankKeywords) {
            return ParsedBankSms("INCOME", 0L, 0L, "", "", "غیربانکی", rawSms, false)
        }

        val txType = if (isExpense && !isIncome) "EXPENSE" else "INCOME"

        // Extract Amount
        val amountPair = extractSmsAmount(cleaned)

        // Extract Ref / Tracking code
        val ref = extractSmsRef(cleaned)

        // Extract Card / Account
        val acc = extractAccountOrCard(cleaned)

        // Detect Bank
        val bank = detectBankFromSms(cleaned, senderAddress)

        return ParsedBankSms(
            transactionType = txType,
            amountRial = amountPair.first,
            amountToman = amountPair.second,
            accountOrCard = acc,
            refNumber = ref,
            bankName = bank,
            rawSms = rawSms,
            isBankSms = true
        )
    }

    private fun normalizeDigits(text: String): String {
        val pDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val aDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        var result = text

        for (i in 0..9) {
            result = result.replace(pDigits[i], (i + '0'.code).toChar())
            result = result.replace(aDigits[i], (i + '0'.code).toChar())
        }
        return result
    }

    private fun extractSmsAmount(text: String): Pair<Long, Long> {
        val patterns = listOf(
            Pattern.compile("(?:مبلغ|واریز|برداشت|خرید|ارزش)\\s*[:=]?\\s*([\\d,٫٬\\s]+)\\s*(تومان|ریال|Rial|Toman)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\d,٫٬]+)\\s*(تومان|ریال|Rial|Toman)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val numStr = matcher.group(1)?.replace("[^0-9]".toRegex(), "") ?: ""
                val unit = matcher.group(2) ?: "ریال" // Most Iranian bank SMS use Rial by default
                val valLong = numStr.toLongOrNull() ?: 0L

                if (valLong > 0) {
                    return if (unit.contains("تومان", ignoreCase = true) || unit.contains("toman", ignoreCase = true)) {
                        Pair(valLong * 10, valLong)
                    } else {
                        // Rial default in SMS
                        Pair(valLong, valLong / 10)
                    }
                }
            }
        }

        // Standalone number match (e.g. "واریز 1,000,000 به حساب")
        val standalonePattern = Pattern.compile("(?:واریز|برداشت)\\s+([\\d,٫٬]+)")
        val matcher = standalonePattern.matcher(text)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace("[^0-9]".toRegex(), "") ?: ""
            val valLong = numStr.toLongOrNull() ?: 0L
            if (valLong > 0) {
                return Pair(valLong, valLong / 10)
            }
        }

        return Pair(0L, 0L)
    }

    private fun extractSmsRef(text: String): String {
        val patterns = listOf(
            Pattern.compile("(?:پیگیری|پیگیر|رهگیری|مرجع|ارجاع|شناسه|کد|Ref|RRN)\\s*[:=]?\\s*([a-zA-Z0-9]{4,16})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{6,12})\\b")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val code = matcher.group(1)?.trim() ?: ""
                if (code.length in 5..14 && !code.startsWith("6037") && !code.startsWith("6104")) {
                    return code
                }
            }
        }
        return ""
    }

    private fun extractAccountOrCard(text: String): String {
        // e.g. "به حساب ...1234" or "از کارت ...5678"
        val pattern = Pattern.compile("(?:حساب|کارت)\\s*[:=]?\\s*([\\d*\\-.]{4,19})")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }
        return ""
    }

    private fun detectBankFromSms(text: String, sender: String): String {
        return when {
            text.contains("ملی") || sender.contains("MELLI") || text.contains("بام") -> "بانک ملی ایران"
            text.contains("ملت") || sender.contains("MELLAT") -> "بانک ملت"
            text.contains("سامان") || text.contains("بلو") || sender.contains("SAMAN") || sender.contains("BLU") -> "بانک سامان / بلو"
            text.contains("تجارت") || sender.contains("TEJARAT") -> "بانک تجارت"
            text.contains("سپه") || text.contains("انصار") -> "بانک سپه"
            text.contains("پاسارگاد") || sender.contains("PASARGAD") -> "بانک پاسارگاد"
            text.contains("پارسیان") -> "بانک پارسیان"
            text.contains("صادرات") -> "بانک صادرات"
            text.contains("کشاورزی") -> "بانک کشاورزی"
            text.contains("رسالت") -> "بانک رسالت"
            else -> "بانک ایران"
        }
    }
}
