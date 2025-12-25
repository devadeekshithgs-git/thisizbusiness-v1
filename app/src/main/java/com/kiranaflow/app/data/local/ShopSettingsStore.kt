package com.kiranaflow.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ShopSettings(
    val shopName: String = "",
    // Optional public contact number shown on receipts.
    val shopPhone: String = "",
    val ownerName: String = "",
    val upiId: String = "",
    // Payee name shown inside UPI apps (pn=). If blank, we fall back to shopName.
    val upiPayeeName: String = "",
    val whatsappReminderMessage: String = "",
    // WhatsApp receipt template for billing (editable in Settings).
    // If blank, the app falls back to a default receipt message.
    val receiptTemplate: String = "",
    // GST business profile (used by GST Reports export)
    val gstin: String = "",
    val legalName: String = "",
    val address: String = "",
    val stateCode: Int = 0
)

private val Context.shopSettingsDataStore by preferencesDataStore(name = "shop_settings")

class ShopSettingsStore(private val context: Context) {
    private object Keys {
        val shopName = stringPreferencesKey("shop_name")
        val shopPhone = stringPreferencesKey("shop_phone")
        val ownerName = stringPreferencesKey("owner_name")
        val upiId = stringPreferencesKey("upi_id")
        val upiPayeeName = stringPreferencesKey("upi_payee_name")
        val whatsappReminderMessage = stringPreferencesKey("whatsapp_reminder_message")
        val receiptTemplate = stringPreferencesKey("receipt_template")
        val gstin = stringPreferencesKey("gstin")
        val legalName = stringPreferencesKey("legal_name")
        val address = stringPreferencesKey("address")
        val stateCode = intPreferencesKey("state_code")
    }

    val settings: Flow<ShopSettings> = context.shopSettingsDataStore.data.map { prefs ->
        ShopSettings(
            shopName = prefs[Keys.shopName].orEmpty(),
            shopPhone = prefs[Keys.shopPhone].orEmpty(),
            ownerName = prefs[Keys.ownerName].orEmpty(),
            upiId = prefs[Keys.upiId].orEmpty(),
            upiPayeeName = prefs[Keys.upiPayeeName].orEmpty(),
            whatsappReminderMessage = prefs[Keys.whatsappReminderMessage].orEmpty(),
            receiptTemplate = prefs[Keys.receiptTemplate].orEmpty(),
            gstin = prefs[Keys.gstin].orEmpty(),
            legalName = prefs[Keys.legalName].orEmpty(),
            address = prefs[Keys.address].orEmpty(),
            stateCode = prefs[Keys.stateCode] ?: 0
        )
    }

    suspend fun save(
        shopName: String,
        shopPhone: String,
        ownerName: String,
        upiId: String,
        upiPayeeName: String = ""
    ) {
        context.shopSettingsDataStore.edit { prefs ->
            prefs[Keys.shopName] = shopName.trim()
            prefs[Keys.shopPhone] = shopPhone.trim()
            prefs[Keys.ownerName] = ownerName.trim()
            prefs[Keys.upiId] = upiId.trim()
            prefs[Keys.upiPayeeName] = upiPayeeName.trim()
        }
    }

    suspend fun saveWhatsAppReminderMessage(message: String) {
        context.shopSettingsDataStore.edit { prefs ->
            prefs[Keys.whatsappReminderMessage] = message.trim()
        }
    }

    suspend fun saveReceiptTemplate(template: String) {
        context.shopSettingsDataStore.edit { prefs ->
            prefs[Keys.receiptTemplate] = template.trim()
        }
    }

    suspend fun saveGstBusinessInfo(gstin: String, legalName: String, address: String, stateCode: Int) {
        context.shopSettingsDataStore.edit { prefs ->
            prefs[Keys.gstin] = gstin.trim().uppercase()
            prefs[Keys.legalName] = legalName.trim()
            prefs[Keys.address] = address.trim()
            prefs[Keys.stateCode] = stateCode
        }
    }
}


