package com.kiranaflow.app.ui.screens.vendors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.repository.KiranaRepository

/**
 * Minimal ViewModel kept so older imports/usages (if any) remain valid.
 * Current `VendorDetailScreen` reads from Room flows directly.
 */
class VendorDetailViewModel(app: Application) : AndroidViewModel(app) {
    val repo: KiranaRepository = KiranaRepository(KiranaDatabase.getDatabase(app))
}

