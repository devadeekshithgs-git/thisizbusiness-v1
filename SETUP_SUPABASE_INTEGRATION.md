# Setup Guide: Direct Supabase Integration

## 🚀 Quick Setup (5 minutes)

### Step 1: Update Supabase Credentials

Edit `gradle.properties` and replace the placeholder:

```properties
# Replace this line with your actual Supabase anon key
KIRANAFLOW_BACKEND_API_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Where to find your anon key:**
1. Go to [Supabase Dashboard](https://supabase.com/dashboard/project/skcglmrtmehtqlpjajrw)
2. Navigate to **Settings** → **API**
3. Copy the **anon public** key
4. Replace the placeholder in `gradle.properties`

### Step 2: Build and Run

```bash
# Clean and rebuild the project
./gradlew clean build

# Install and run on device/emulator
./gradlew installDebug
```

### Step 3: Migrate Existing Data

The app will automatically detect if you have existing local data and show a migration dialog. Alternatively, you can trigger migration manually:

1. Open the app
2. Go to Settings → Advanced → "Migrate to Supabase"
3. Choose whether to overwrite existing data
4. Wait for migration to complete

## 🔧 Detailed Setup

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 26+ (already configured)
- Active internet connection for initial setup
- Supabase project already created

### 1. Supabase Project Setup

Your Supabase project should have these tables (already created):

```sql
-- Core tables (already exist)
kf_items
kf_parties  
kf_transactions
kf_transaction_items
kf_reminders
kf_sync_ops
```

### 2. Android Project Configuration

The following files have been modified/created:

#### New Files:
- `SupabaseClient.kt` - Supabase client configuration
- `RealtimeManager.kt` - Real-time subscriptions
- `ItemRepository.kt` - Offline-first repository
- `SupabaseMigrationManager.kt` - Data migration utility
- `ConnectivityMonitor.kt` - Network monitoring
- `DeviceIdProvider.kt` - Device identification
- `SupabaseIntegrationTest.kt` - Integration tests

#### Modified Files:
- `app/build.gradle.kts` - Added Supabase dependencies
- `gradle.properties` - Added Supabase configuration
- `AndroidManifest.xml` - Added Application class
- `KiranaApplication.kt` - Initialize services
- `Daos.kt` - Added sync methods

### 3. Dependencies Added

```kotlin
// Supabase Android SDK for Direct Integration
val supabaseVersion = "2.2.3"
implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabaseVersion")
implementation("io.github.jan-tennert.supabase:realtime-kt:$supabaseVersion")
implementation("io.github.jan-tennert.supabase:auth-kt:$supabaseVersion")
implementation("io.github.jan-tennert.supabase:storage-kt:$supabaseVersion")
implementation("io.github.jan-tennert.supabase:gotrue-kt:$supabaseVersion")
implementation("io.github.jan-tennert.supabase:functions-kt:$supabaseVersion")
```

## 🧪 Testing the Integration

### Manual Testing

1. **Basic Connection Test:**
   ```kotlin
   // Run this in your app's debug section
   SupabaseManualTest.runAllTests(applicationContext)
   ```

2. **Real-time Test:**
   - Open app on two devices
   - Add an item on one device
   - Verify it appears on the other device

3. **Offline Test:**
   - Turn off internet
   - Add/modify items
   - Turn on internet
   - Verify sync happens automatically

### Automated Testing

Run the integration tests:
```bash
./gradlew connectedAndroidTest
```

## 🔍 Troubleshooting

### Common Issues

#### 1. Build Errors
**Problem:** Gradle sync fails with Supabase dependencies
**Solution:** 
- Check internet connection
- Update Gradle wrapper: `./gradlew wrapper --gradle-version=8.0`
- Clean and rebuild: `./gradlew clean build`

#### 2. Connection Issues
**Problem:** "Supabase client not configured" error
**Solution:**
- Verify `KIRANAFLOW_BACKEND_API_KEY` in `gradle.properties`
- Check that the key starts with `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
- Ensure project URL is correct: `https://skcglmrtmehtqlpjajrw.supabase.co`

#### 3. Migration Fails
**Problem:** Data migration shows errors
**Solution:**
- Check internet connection
- Verify Supabase tables exist
- Check Row Level Security (RLS) policies
- Try with "Force Overwrite" option

#### 4. Real-time Not Working
**Problem:** Changes don't sync between devices
**Solution:**
- Check RLS policies allow real-time subscriptions
- Verify devices are online
- Check device IDs are different
- Restart the app

### Debug Mode

Enable debug logging by adding this to `KiranaApplication.onCreate()`:

```kotlin
if (BuildConfig.DEBUG) {
    println("🔧 Debug mode enabled")
    println("Device ID: $deviceId")
    println("Supabase URL: ${BackendConfig.backendBaseUrl}")
    println("Supabase configured: ${SupabaseClient.isConfigured()}")
}
```

## 📊 Monitoring

### Sync Status

Monitor sync status in the app:
- Settings → Advanced → "Sync Status"
- Shows pending changes, last sync time, errors

### Network Status

The app automatically monitors connectivity and shows:
- 🟢 Online - Real-time sync active
- 🟡 Poor Connection - Sync delayed
- 🔴 Offline - Local mode only

## 🚀 Production Deployment

### Before Deploying

1. **Test thoroughly** with real data
2. **Backup existing data** before migration
3. **Verify RLS policies** are secure
4. **Test offline scenarios**
5. **Monitor performance**

### Security Checklist

- [ ] RLS policies enabled on all tables
- [ ] API keys properly configured
- [ ] No sensitive data in logs
- [ ] HTTPS enforced
- [ ] Device isolation working

## 📚 Additional Resources

- [Supabase Android SDK Docs](https://supabase.com/docs/reference/android)
- [Real-time Subscriptions Guide](https://supabase.com/docs/guides/realtime)
- [Row Level Security](https://supabase.com/docs/guides/auth/row-level-security)

## 🆘 Support

If you encounter issues:

1. Check the troubleshooting section above
2. Look at Android Studio Logcat for errors
3. Verify Supabase dashboard for any issues
4. Test with the integration test suite

---

**🎉 Your KiranaFlow app is now ready for Direct Supabase Integration!**

The app will provide:
- ✅ Real-time data synchronization
- ✅ Offline-first operation
- ✅ Automatic conflict resolution
- ✅ Cloud backup and sync
- ✅ Multi-device support
