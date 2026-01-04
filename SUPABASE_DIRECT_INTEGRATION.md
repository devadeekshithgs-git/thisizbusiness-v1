# KiranaFlow - Direct Supabase Integration

This document outlines the complete Direct Supabase Integration implementation for KiranaFlow, providing real-time data synchronization with offline-first capabilities.

## Overview

The Direct Supabase Integration replaces the previous sync-based architecture with direct database operations, real-time subscriptions, and automatic conflict resolution.

## Architecture

### Offline-First Design
- **Local Cache**: Room database serves as primary data source
- **Real-time Sync**: Automatic synchronization when online
- **Conflict Resolution**: Last-write-wins with timestamp tracking
- **Background Sync**: Automatic retry on connectivity restoration

### Data Flow
```
UI Operations → Local Room (Immediate) → Supabase (Background)
                    ↓
Real-time Updates ← Supabase ← Local Room (Cache)
```

## Setup Instructions

### 1. Configure Supabase Credentials

Update `gradle.properties` with your Supabase credentials:

```properties
# Supabase Configuration for Direct Integration
KIRANAFLOW_BACKEND_BASE_URL=https://your-project.supabase.co
KIRANAFLOW_BACKEND_API_KEY=your_supabase_anon_key_here
```

### 2. Database Schema

The following tables are already created in your Supabase project:

- `kf_items` - Inventory items
- `kf_parties` - Customers and vendors
- `kf_transactions` - Sales and expenses
- `kf_transaction_items` - Transaction line items
- `kf_reminders` - User reminders
- `kf_sync_ops` - Operation log for audit trail

### 3. Migration Process

Use the built-in migration utility to transfer existing data:

```kotlin
// In your ViewModel or Repository
val migrationManager = SupabaseMigrationManager(
    itemDao = database.itemDao(),
    partyDao = database.partyDao(),
    transactionDao = database.transactionDao(),
    transactionItemDao = database.transactionItemDao(), // Note: This is in TransactionDao
    reminderDao = database.reminderDao(),
    deviceId = deviceId
)

val result = migrationManager.migrateAllData(forceOverwrite = false)
```

## Key Components

### 1. SupabaseClient
- Singleton client configuration
- Auto-initialization from build properties
- Module accessors (auth, postgrest, realtime, storage)

### 2. ItemRepository (Example)
- Local-first operations
- Automatic remote sync
- Real-time subscription handling
- Offline queue management

### 3. RealtimeManager
- Real-time subscriptions to all tables
- Automatic connectivity-based subscription management
- Conflict resolution and cache updates

### 4. ConnectivityMonitor
- Network state monitoring
- Automatic sync triggers
- Offline operation support

## Usage Examples

### Basic Operations

```kotlin
// Get items (local first, then sync)
val items = itemRepository.getAllItems().collect { itemList ->
    // Update UI with cached items
}

// Upsert item (local + remote)
itemRepository.upsertItem(newItem)

// Search items
val searchResults = itemRepository.searchItems("rice")
```

### Real-time Updates

Real-time updates are handled automatically by the RealtimeManager. When data changes in Supabase, the local cache is updated automatically.

### Migration

```kotlin
// Show migration dialog
SupabaseMigrationDialog(
    isVisible = showMigrationDialog,
    onDismiss = { showMigrationDialog = false },
    onStartMigration = { forceOverwrite ->
        viewModelScope.launch {
            val result = migrationManager.migrateAllData(forceOverwrite)
            // Handle result
        }
    },
    migrationInProgress = viewModel.migrationInProgress,
    migrationResult = viewModel.migrationResult
)
```

## Features

### ✅ Implemented
- [x] Direct Supabase client integration
- [x] Offline-first data layer
- [x] Real-time subscriptions
- [x] Automatic conflict resolution
- [x] Data migration utility
- [x] Connectivity monitoring
- [x] Device-scoped data isolation
- [x] Background sync with retry

### 🔄 In Progress
- [ ] Authentication integration
- [ ] File storage for images
- [ ] Advanced conflict resolution
- [ ] Sync status indicators
- [ ] Batch operations optimization

### 📋 Planned
- [ ] Multi-device sync
- [ ] Data export/import
- [ ] Sync analytics
- [ ] Offline mode indicators

## Troubleshooting

### Common Issues

1. **Build Errors**: Ensure Supabase dependencies are properly added
2. **Connection Issues**: Check network connectivity and Supabase URL
3. **Migration Failures**: Verify Supabase table structure and permissions
4. **Real-time Not Working**: Check Row Level Security (RLS) policies

### Debug Logging

Enable debug logging by adding to your Application class:

```kotlin
// In KiranaApplication.onCreate()
if (BuildConfig.DEBUG) {
    SupabaseClient.getClient().println("Supabase debug mode enabled")
}
```

## Performance Considerations

### Optimizations
- Local caching reduces network calls
- Batch operations for bulk updates
- Lazy loading for large datasets
- Connection pooling via singleton client

### Best Practices
- Use Flow for reactive UI updates
- Implement proper error handling
- Monitor sync status
- Test offline scenarios

## Security

### Data Protection
- Device-scoped data isolation
- Row Level Security (RLS) policies
- API key management via build properties
- No sensitive data in local logs

### Recommendations
- Enable RLS on all tables
- Use service role key for admin operations
- Implement user authentication
- Regular security audits

## Migration from Sync Architecture

### Breaking Changes
- Repository interfaces updated
- Sync engine replaced with direct operations
- Outbox table deprecated (kept for audit)

### Migration Steps
1. Update dependencies
2. Configure Supabase client
3. Run data migration
4. Update repository usage
5. Test offline scenarios
6. Deploy to production

## Support

For issues or questions:
1. Check this documentation
2. Review Supabase logs
3. Enable debug logging
4. Test with sample data

## Version History

- **v2.0.0**: Direct Supabase Integration
  - Real-time subscriptions
  - Offline-first architecture
  - Migration utilities
  - Enhanced error handling
