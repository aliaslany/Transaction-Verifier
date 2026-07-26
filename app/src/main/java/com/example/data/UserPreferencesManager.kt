package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

    private val _botToken = MutableStateFlow(prefs.getString(KEY_BOT_TOKEN, "") ?: "")
    val botToken: StateFlow<String> = _botToken.asStateFlow()

    private val _chatId = MutableStateFlow(prefs.getString(KEY_CHAT_ID, "") ?: "")
    val chatId: StateFlow<String> = _chatId.asStateFlow()

    private val _telegramEnabled = MutableStateFlow(prefs.getBoolean(KEY_TELEGRAM_ENABLED, true))
    val telegramEnabled: StateFlow<Boolean> = _telegramEnabled.asStateFlow()

    private val _currencyUnit = MutableStateFlow(prefs.getString(KEY_CURRENCY_UNIT, "TOMAN") ?: "TOMAN") // TOMAN or RIAL
    val currencyUnit: StateFlow<String> = _currencyUnit.asStateFlow()

    private val _autoSmsListenerEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SMS, true))
    val autoSmsListenerEnabled: StateFlow<Boolean> = _autoSmsListenerEnabled.asStateFlow()

    private val _securityPin = MutableStateFlow(prefs.getString(KEY_SECURITY_PIN, "") ?: "")
    val securityPin: StateFlow<String> = _securityPin.asStateFlow()

    fun updateTelegramConfig(token: String, chat: String, enabled: Boolean) {
        prefs.edit()
            .putString(KEY_BOT_TOKEN, token.trim())
            .putString(KEY_CHAT_ID, chat.trim())
            .putBoolean(KEY_TELEGRAM_ENABLED, enabled)
            .apply()
        _botToken.value = token.trim()
        _chatId.value = chat.trim()
        _telegramEnabled.value = enabled
    }

    fun setCurrencyUnit(unit: String) {
        prefs.edit().putString(KEY_CURRENCY_UNIT, unit).apply()
        _currencyUnit.value = unit
    }

    fun setAutoSmsListenerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SMS, enabled).apply()
        _autoSmsListenerEnabled.value = enabled
    }

    fun setSecurityPin(pin: String) {
        prefs.edit().putString(KEY_SECURITY_PIN, pin).apply()
        _securityPin.value = pin
    }

    companion object {
        private const val KEY_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_CHAT_ID = "telegram_chat_id"
        private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        private const val KEY_CURRENCY_UNIT = "currency_unit"
        private const val KEY_AUTO_SMS = "auto_sms_enabled"
        private const val KEY_SECURITY_PIN = "security_pin"
    }
}
