package com.example.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class IranianBankSmsParserTest {

    @Test
    fun `parse income sms from bank`() {
        val rawSms = "واریز ۵۰,۰۰۰,۰۰۰ ریال به حساب ۶۱۰۴...۱۱۰۲ از کارت ۶۰۳۷...۸۸۲۱ کد: 982341"
        val parsed = IranianBankSmsParser.parse(rawSms, "10002000")

        assertTrue(parsed.isBankSms)
        assertEquals("INCOME", parsed.transactionType)
        assertEquals(50000000L, parsed.amountRial)
        assertEquals(5000000L, parsed.amountToman)
        assertEquals("6104...1102", parsed.accountOrCard)
        assertEquals("982341", parsed.refNumber)
    }

    @Test
    fun `parse expense sms from bank`() {
        val rawSms = "برداشت ۲۴,۰۰۰,۰۰۰ ریال از حساب ۶۱۰۴...۱۱۰۲ خرید فروشگاهی کد: 441920"
        val parsed = IranianBankSmsParser.parse(rawSms, "BankMELLAT")

        assertTrue(parsed.isBankSms)
        assertEquals("EXPENSE", parsed.transactionType)
        assertEquals(24000000L, parsed.amountRial)
        assertEquals("بانک ملت", parsed.bankName)
        assertEquals("441920", parsed.refNumber)
    }

    @Test
    fun `parse non bank sms`() {
        val rawSms = "سلام خوبی کجایی؟"
        val parsed = IranianBankSmsParser.parse(rawSms, "+989123456789")

        assertFalse(parsed.isBankSms)
    }
}
