import com.example.parser.IranianBankSmsParser

fun main() {
    val rawSms = "برداشت ۲۴,۰۰۰,۰۰۰ ریال از حساب ۶۱۰۴...۱۱۰۲ خرید فروشگاهی کد: 441920"
    val parsed = IranianBankSmsParser.parse(rawSms, "BankMellat")
    println(parsed)
}
