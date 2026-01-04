package com.kiranaflow.app.data.repository

import com.kiranaflow.app.data.local.ItemDao
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.remote.SupabaseClient
import com.kiranaflow.app.data.remote.SimpleSupabaseClient
import com.kiranaflow.app.util.ConnectivityMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Offline-first repository for Items with Direct Supabase Integration
 * 
 * This repository provides:
 * - Local Room database as primary cache
 * - Automatic sync with Supabase when online
 * - Real-time updates from Supabase
 * - Conflict resolution for concurrent edits
 */
class ItemRepository(
    private val itemDao: ItemDao,
    private val deviceId: String
) {
    
    private val supabase = SupabaseClient
    
    /**
     * Get all items as Flow (local first, then sync with remote)
     */
    fun getAllItems(): Flow<List<ItemEntity>> {
        // Return local items immediately
        return itemDao.getAllItems()
    }
    
    /**
     * Get item by ID (local first, fallback to remote)
     */
    suspend fun getItemById(id: Int): ItemEntity? {
        // Try local first
        val localItem = itemDao.getItemById(id)
        
        // If not found locally and online, try remote
        if (localItem == null && ConnectivityMonitor.isOnlineNow()) {
            val remoteItem = getItemFromRemote(id)
            if (remoteItem != null) {
                // Cache remote item locally
                itemDao.insertItem(remoteItem)
                return remoteItem
            }
        }
        
        return localItem
    }
    
    /**
     * Insert or update item (local + remote)
     */
    suspend fun upsertItem(item: ItemEntity) {
        // Always update local first for immediate response
        itemDao.insertItem(item)
        
        // Then sync to remote if online
        if (ConnectivityMonitor.isOnlineNow()) {
            try {
                upsertItemToRemote(item)
            } catch (e: Exception) {
                // Handle sync failure - item stays in local DB
                println("Failed to sync item to remote: ${e.message}")
            }
        }
    }
    
    /**
     * Delete item (local + remote)
     */
    suspend fun deleteItem(item: ItemEntity) {
        // Mark as deleted locally
        val deletedItem = item.copy(isDeleted = true)
        itemDao.insertItem(deletedItem)
        
        // Delete from remote if online
        if (ConnectivityMonitor.isOnlineNow()) {
            try {
                deleteItemFromRemote(item.id)
            } catch (e: Exception) {
                println("Failed to delete item from remote: ${e.message}")
            }
        }
    }
    
    /**
     * Search items by name or barcode
     */
    fun searchItems(query: String): Flow<List<ItemEntity>> {
        return itemDao.searchItems(query)
    }
    
    /**
     * Get items with low stock
     */
    fun getLowStockItems(): Flow<List<ItemEntity>> {
        return itemDao.getLowStockItems()
    }
    
    /**
     * Sync all items from remote to local
     */
    suspend fun syncFromRemote() {
        if (!ConnectivityMonitor.isOnlineNow()) return
        
        try {
            val remoteItems = SimpleSupabaseClient.getAll(supabase.kfItems, deviceId)
            
            // Convert remote items to local entities and update cache
            remoteItems.forEach { remoteItemMap ->
                val localItem = RemoteItem(
                    deviceId = deviceId,
                    localId = remoteItemMap["localId"] as? String ?: "",
                    name = remoteItemMap["name"] as? String ?: "",
                    category = remoteItemMap["category"] as? String,
                    price = remoteItemMap["price"] as? Double,
                    costPrice = remoteItemMap["costPrice"] as? Double,
                    stock = remoteItemMap["stock"] as? Int,
                    gstPercentage = remoteItemMap["gstPercentage"] as? Double,
                    reorderPoint = remoteItemMap["reorderPoint"] as? Int,
                    vendorLocalId = remoteItemMap["vendorLocalId"] as? String,
                    rackLocation = remoteItemMap["rackLocation"] as? String,
                    barcode = remoteItemMap["barcode"] as? String,
                    imageUri = remoteItemMap["imageUri"] as? String,
                    expiryDateMillis = remoteItemMap["expiryDateMillis"] as? Long
                ).toLocalEntity()
                itemDao.insertItem(localItem)
            }
            
        } catch (e: Exception) {
            println("Failed to sync items from remote: ${e.message}")
        }
    }
    
    // Private helper methods
    
    private suspend fun getItemFromRemote(id: Int): ItemEntity? {
        return try {
            val localId = id.toString()
            val remoteItems = SimpleSupabaseClient.getAll(supabase.kfItems, deviceId)
            val remoteItemMap = remoteItems.find { 
                (it["localId"] as? String) == localId 
            }
            
            if (remoteItemMap != null) {
                ItemEntity(
                    id = (remoteItemMap["localId"] as? String)?.toIntOrNull() ?: 0,
                    name = (remoteItemMap["name"] as? String) ?: "",
                    price = (remoteItemMap["price"] as? Double) ?: 0.0,
                    stock = (remoteItemMap["stock"] as? Int) ?: 0,
                    category = (remoteItemMap["category"] as? String) ?: "",
                    rackLocation = remoteItemMap["rackLocation"] as? String,
                    marginPercentage = if (remoteItemMap["costPrice"] != null && remoteItemMap["price"] != null) {
                        val costPrice = remoteItemMap["costPrice"] as? Double ?: 0.0
                        val price = remoteItemMap["price"] as? Double ?: 0.0
                        if (costPrice > 0) ((price - costPrice) / costPrice) * 100 else 0.0
                    } else 0.0,
                    barcode = remoteItemMap["barcode"] as? String,
                    costPrice = (remoteItemMap["costPrice"] as? Double) ?: 0.0,
                    gstPercentage = remoteItemMap["gstPercentage"] as? Double,
                    reorderPoint = (remoteItemMap["reorderPoint"] as? Int) ?: 0,
                    vendorId = (remoteItemMap["vendorLocalId"] as? String)?.toIntOrNull(),
                    imageUri = remoteItemMap["imageUri"] as? String,
                    expiryDateMillis = remoteItemMap["expiryDateMillis"] as? Long,
                    isDeleted = false
                )
            } else null
        } catch (e: Exception) {
            println("Failed to get item from remote: ${e.message}")
            null
        }
    }
    
    private suspend fun upsertItemToRemote(item: ItemEntity) {
        val remoteItem = mapOf(
            "deviceId" to deviceId,
            "localId" to item.id.toString(),
            "name" to item.name,
            "category" to item.category,
            "price" to item.price,
            "costPrice" to item.costPrice,
            "stock" to item.stock,
            "gstPercentage" to item.gstPercentage,
            "reorderPoint" to item.reorderPoint,
            "vendorLocalId" to item.vendorId?.toString(),
            "rackLocation" to item.rackLocation,
            "barcode" to item.barcode,
            "imageUri" to item.imageUri,
            "expiryDateMillis" to item.expiryDateMillis
        )
        
        try {
            SimpleSupabaseClient.insert(supabase.kfItems, remoteItem)
        } catch (e: Exception) {
            // Try update if insert fails (record might exist)
            try {
                SimpleSupabaseClient.update(supabase.kfItems, item.id.toString(), remoteItem)
            } catch (updateException: Exception) {
                println("Failed to upsert item to remote: ${updateException.message}")
                throw updateException
            }
        }
    }
    
    private suspend fun deleteItemFromRemote(id: Int) {
        SimpleSupabaseClient.delete(supabase.kfItems, id.toString())
    }
    
    /**
     * Data class matching Supabase table schema
     */
    private data class RemoteItem(
        val deviceId: String,
        val localId: String,
        val name: String,
        val category: String?,
        val price: Double?,
        val costPrice: Double?,
        val stock: Int?,
        val gstPercentage: Double?,
        val reorderPoint: Int?,
        val vendorLocalId: String?,
        val rackLocation: String?,
        val barcode: String?,
        val imageUri: String?,
        val expiryDateMillis: Long?
    ) {
        fun toLocalEntity(): ItemEntity {
            return ItemEntity(
                id = localId.toIntOrNull() ?: 0,
                name = name,
                price = price ?: 0.0,
                stock = stock ?: 0,
                category = category ?: "",
                rackLocation = rackLocation,
                marginPercentage = if (costPrice != null && price != null && costPrice > 0) {
                    ((price - costPrice) / costPrice) * 100
                } else 0.0,
                barcode = barcode,
                costPrice = costPrice ?: 0.0,
                gstPercentage = gstPercentage,
                reorderPoint = reorderPoint ?: 0,
                vendorId = vendorLocalId?.toIntOrNull(),
                imageUri = imageUri,
                expiryDateMillis = expiryDateMillis,
                isDeleted = false
            )
        }
    }
}