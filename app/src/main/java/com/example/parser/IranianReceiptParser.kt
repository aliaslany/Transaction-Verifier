package com.example.parser

import java.util.regex.Pattern

data class ParsedReceiptResult(
    val amountRial: Long,
    val amountToman: Long,
    val trackingNumber: String,
    val senderCard: String,
    val recipientCard: String,
    val bankName: String,
    val rawText: String,
    val isSuccess: Boolean
)

object IranianReceiptParser {

    fun parse(rawText: String): ParsedReceiptResult {
        if (rawText.isBlank()) {
            return ParsedReceiptResult(0L, 0L, "", "", "", "ناشناخته", rawText, false)
        }

        val cleanedText = normalizePersianDigits(rawText)

        // 1. Extract Amount
        val amountPair = extractAmount(cleanedText)
        val amountRial = amountPair.first
        val amountToman = amountPair.second

        // 2. Extract Tracking/Ref Number
        val trackingNo = extractTrackingNumber(cleanedText)

        // 3. Extract Card Numbers
        val cards = extractCardNumbers(cleanedText)
        val sender = if (cards.isNotEmpty()) cards[0] else ""
        val recipient = if (cards.size > 1) cards[1] else ""

        // 4. Detect Bank Name
        val bank = detectBank(cleanedText, sender, recipient)

        val isSuccess = amountRial > 0L || trackingNo.isNotBlank() || cards.isNotEmpty()

        return ParsedReceiptResult(
            amountRial = amountRial,
            amountToman = amountToman,
            trackingNumber = trackingNo,
            senderCard = sender,
            recipientCard = recipient,
            bankName = bank,
            rawText = rawText,
            isSuccess = isSuccess
        )
    }

    private fun normalizePersianDigits(text: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        var result = text

        for (i in 0..9) {
            result = result.replace(persianDigits[i], (i + '0'.code).toChar())
            result = result.replace(arabicDigits[i], (i + '0'.code).toChar())
        }
        return result
    }

    private fun extractAmount(text: String): Pair<Long, Long> {
        // Look for patterns like "مبلغ: ۵۰۰,۰۰۰ تومان" or "5000000 ریال"
        val patterns = listOf(
            Pattern.compile("(?:مبلغ|مبلغ تراکنش|ارزش)\\s*[:=]?\\s*([\\d,٫٬\\s]+)\\s*(تومان|ریال|Rial|Toman)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\d,٫٬]+)\\s*(تومان|ریال|Rial|Toman)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val numStr = matcher.group(1)?.replace("[^0-9]".toRegex(), "") ?: ""
                val unit = matcher.group(2) ?: "تومان"
                val valLong = numStr.toLongOrNull() ?: 0L

                if (valLong > 0) {
                    return if (unit.contains("ریال", ignoreCase = true) || unit.contains("rial", ignoreCase = true)) {
                        Pair(valLong, valLong / 10)
                    } else {
                        // Toman default
                        Pair(valLong * 10, valLong)
                    }
                }
            }
        }

        // Fallback: search for numbers over 10,000 without unit
        val standaloneNumberPattern = Pattern.compile("([\\d]{1,3}(?:[,٫٬][\\d]{3})+)")
        val matcher = standaloneNumberPattern.matcher(text)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace("[^0-9]".toRegex(), "") ?: ""
            val valLong = numStr.toLongOrNull() ?: 0L
            if (valLong >= 1000) {
                // assume Toman if >= 1000 and < 100_000_000
                return Pair(valLong * 10, valLong)
            }
        }

        return Pair(0L, 0L)
    }

    private fun extractTrackingNumber(text: String): String {
        val keywords = listOf(
            "کد رهگیری", "شماره پیگیری", "کد پیگیری", "شماره ارجاع", "شماره مرجع", "کد مرجع",
            "کد تایید", "شناسه پرداخت", "Ref", "Ref No", "Tracking No", "RRN"
        )

        for (keyword in keywords) {
            val pattern = Pattern.compile("$keyword\\s*[:=]?\\s*([a-zA-Z0-9]{4,16})", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val found = matcher.group(1)?.trim() ?: ""
                if (found.isNotBlank() && found.length >= 4) {
                    return found
                }
            }
        }

        // Fallback: Find isolated 6-12 digit sequence
        val pattern = Pattern.compile("\\b(\\d{6,12})\\b")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val digits = matcher.group(1) ?: ""
            // Avoid capturing card numbers or timestamps
            if (!digits.startsWith("6037") && !digits.startsWith("6104") && digits.length in 6..12) {
                return digits
            }
        }

        return ""
    }

    private fun extractCardNumbers(text: String): List<String> {
        val cards = mutableListOf<String>()

        // 16-digit card pattern (formatted or contiguous, with optional asterisks or dashes)
        val cardPattern = Pattern.compile("([56]\\d{3}[\\s*-]*\\d{4}[\\s*-]*\\d{4}[\\s*-]*\\d{4})")
        val matcher = cardPattern.matcher(text)
        while (matcher.find()) {
            val card = matcher.group(1)?.replace("[^0-9*]".toRegex(), "") ?: ""
            if (card.length == 16 && !cards.contains(card)) {
                cards.add(formatCardNumber(card))
            }
        }

        return cards
    }

    private fun formatCardNumber(card: String): String {
        if (card.length != 16) return card
        return "${card.substring(0, 4)}-${card.substring(4, 8)}-${card.substring(8, 12)}-${card.substring(12, 16)}"
    }

    fun detectBank(text: String, senderCard: String, recipientCard: String): String {
        val cardToCheck = if (recipientCard.isNotBlank()) recipientCard else senderCard
        val prefix = cardToCheck.replace("-", "").take(6)

        return when {
            prefix.startsWith("603799") || text.contains("ملی") || text.contains("Melli") -> "بانک ملی ایران"
            prefix.startsWith("610433") || text.contains("ملت") || text.contains("Mellat") -> "بانک ملت"
            prefix.startsWith("621986") || text.contains("سامان") || text.contains("Saman") -> "بانک سامان"
            prefix.startsWith("562700") || text.contains("بلو") || text.contains("Blu") -> "بلو بانک (Saman)"
            prefix.startsWith("622106") || prefix.startsWith("639347") || text.contains("پارسیان") -> "بانک پارسیان"
            prefix.startsWith("589210") || prefix.startsWith("627381") || text.contains("سپه") -> "بانک سپه"
            prefix.startsWith("502229") || prefix.startsWith("639607") || text.contains("پاسارگاد") -> "بانک پاسارگاد"
            prefix.startsWith("627353") || text.contains("تجارت") || text.contains("Tejarat") -> "بانک تجارت"
            prefix.startsWith("603769") || text.contains("صادرات") || text.contains("Saderat") -> "بانک صادرات"
            prefix.startsWith("603770") || text.contains("کشاورزی") -> "بانک کشاورزی"
            prefix.startsWith("628023") || text.contains("مسکن") -> "بانک مسکن"
            prefix.startsWith("504172") || text.contains("رسالت") -> "بانک قرض‌الحسنه رسالت"
            prefix.startsWith("639346") || text.contains("سینا") -> "بانک سینا"
            prefix.startsWith("636214") || text.contains("آینده") -> "بانک آینده"
            else -> "بانک عضو شتاب"
        }
    }
}
