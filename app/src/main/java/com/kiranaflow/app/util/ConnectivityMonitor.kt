package com.kiranaflow.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object ConnectivityMonitor {
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline
    
    private val connectivityListeners = mutableListOf<(Boolean) -> Unit>()
    
    /**
     * Initialize the connectivity monitor
     */
    fun initialize(context: Context) {
        // Start monitoring in background
        GlobalScope.launch {
            createConnectivityFlow(context).collect { isOnlineValue ->
                _isOnline.value = isOnlineValue
                notifyListeners(isOnlineValue)
            }
        }
    }
    
    /**
     * Check if currently online (synchronous)
     */
    fun isOnlineNow(): Boolean {
        return _isOnline.value
    }
    
    /**
     * Add a listener for connectivity changes
     */
    fun addOnConnectivityChangedListener(listener: (Boolean) -> Unit) {
        connectivityListeners.add(listener)
    }
    
    /**
     * Remove a connectivity change listener
     */
    fun removeOnConnectivityChangedListener(listener: (Boolean) -> Unit) {
        connectivityListeners.remove(listener)
    }
    
    private fun notifyListeners(isOnline: Boolean) {
        connectivityListeners.forEach { listener ->
            try {
                listener(isOnline)
            } catch (e: Exception) {
                println("Error in connectivity listener: ${e.message}")
            }
        }
    }
    
    private fun createConnectivityFlow(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        fun current(): Boolean {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        trySend(current())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(current())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(current())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}






