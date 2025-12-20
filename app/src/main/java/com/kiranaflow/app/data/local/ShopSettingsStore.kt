package com.kiranaflow.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ShopSettings(
    val shopName: String,
    val ownerName: String,
    val upiId: String,
    val whatsappReminderMessage: String
)

private val Context.shopSettingsDataStore by preferencesDataStore(name = "shop_settings")

class ShopSettingsStore(private val context: Context) {
    private object Keys {
        val shopName = stringPreferencesKey("shop_name")
        val ownerName = stringPreferencesKey("owner_name")
        val upiId = stringPreferencesKey("upi_id")
        val whatsappReminderMessage = stringPreferencesKey("whatsapp_reminder_message")
    }

    val settings: Flow<ShopSettings> = context.shopSettingsDataStore.data.map { prefs ->
        ShopSettings(
            shopName = prefs[Keys.shopName].orEmpty(),
            ownerName = prefs[Keys.ownerName].orEmpty(),
            upiId = prefs[Keys.upiId].orEmpty(),
            whatsappReminderMessage = prefs[Keys.whatsappReminderMessage].orEmpty()
        )
    }

    suspend fun save(shopName: String, ownerName: String, upiId: String) {
        context.shopSettingsDataStore.edit { prefs ->
            prefs[Keys.shopName] = shopName.trim()
            prefs[Keys.ownerName] = ownerName.trim()
            prefs[Keys.upiId] = upiId.trim()
        }
    }

    suspend fun saveWhatsAppReminderMessage(message: String) {
        context.shopSettingsDataStore.edit { prefs ->
            prefs[Keys.whatsappReminderMessage] = message.trim()
        }
    }
}


