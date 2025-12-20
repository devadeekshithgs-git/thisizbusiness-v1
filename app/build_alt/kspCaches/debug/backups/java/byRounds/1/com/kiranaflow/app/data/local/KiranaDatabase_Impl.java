package com.kiranaflow.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class KiranaDatabase_Impl extends KiranaDatabase {
  private volatile ItemDao _itemDao;

  private volatile PartyDao _partyDao;

  private volatile TransactionDao _transactionDao;

  private volatile ReminderDao _reminderDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(6) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `price` REAL NOT NULL, `stock` INTEGER NOT NULL, `category` TEXT NOT NULL, `rackLocation` TEXT, `marginPercentage` REAL NOT NULL, `barcode` TEXT, `costPrice` REAL NOT NULL, `gstPercentage` REAL, `reorderPoint` INTEGER NOT NULL, `vendorId` INTEGER, `imageUri` TEXT, `expiryDateMillis` INTEGER, `isDeleted` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `parties` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `type` TEXT NOT NULL, `gstNumber` TEXT, `balance` REAL NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `date` INTEGER NOT NULL, `time` TEXT NOT NULL, `customerId` INTEGER, `vendorId` INTEGER, `paymentMode` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `itemId` INTEGER, `itemNameSnapshot` TEXT NOT NULL, `qty` INTEGER NOT NULL, `price` REAL NOT NULL, FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_transactionId` ON `transaction_items` (`transactionId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_itemId` ON `transaction_items` (`itemId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `refId` INTEGER, `dueAt` INTEGER NOT NULL, `note` TEXT, `isDone` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '4f1fbccc78791ccaee28c9dc0b6cd300')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `items`");
        db.execSQL("DROP TABLE IF EXISTS `parties`");
        db.execSQL("DROP TABLE IF EXISTS `transactions`");
        db.execSQL("DROP TABLE IF EXISTS `transaction_items`");
        db.execSQL("DROP TABLE IF EXISTS `reminders`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsItems = new HashMap<String, TableInfo.Column>(15);
        _columnsItems.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("price", new TableInfo.Column("price", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("stock", new TableInfo.Column("stock", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("rackLocation", new TableInfo.Column("rackLocation", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("marginPercentage", new TableInfo.Column("marginPercentage", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("barcode", new TableInfo.Column("barcode", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("costPrice", new TableInfo.Column("costPrice", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("gstPercentage", new TableInfo.Column("gstPercentage", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("reorderPoint", new TableInfo.Column("reorderPoint", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("vendorId", new TableInfo.Column("vendorId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("imageUri", new TableInfo.Column("imageUri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("expiryDateMillis", new TableInfo.Column("expiryDateMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsItems.put("isDeleted", new TableInfo.Column("isDeleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysItems = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesItems = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoItems = new TableInfo("items", _columnsItems, _foreignKeysItems, _indicesItems);
        final TableInfo _existingItems = TableInfo.read(db, "items");
        if (!_infoItems.equals(_existingItems)) {
          return new RoomOpenHelper.ValidationResult(false, "items(com.kiranaflow.app.data.local.ItemEntity).\n"
                  + " Expected:\n" + _infoItems + "\n"
                  + " Found:\n" + _existingItems);
        }
        final HashMap<String, TableInfo.Column> _columnsParties = new HashMap<String, TableInfo.Column>(6);
        _columnsParties.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsParties.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsParties.put("phone", new TableInfo.Column("phone", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsParties.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsParties.put("gstNumber", new TableInfo.Column("gstNumber", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsParties.put("balance", new TableInfo.Column("balance", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysParties = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesParties = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoParties = new TableInfo("parties", _columnsParties, _foreignKeysParties, _indicesParties);
        final TableInfo _existingParties = TableInfo.read(db, "parties");
        if (!_infoParties.equals(_existingParties)) {
          return new RoomOpenHelper.ValidationResult(false, "parties(com.kiranaflow.app.data.local.PartyEntity).\n"
                  + " Expected:\n" + _infoParties + "\n"
                  + " Found:\n" + _existingParties);
        }
        final HashMap<String, TableInfo.Column> _columnsTransactions = new HashMap<String, TableInfo.Column>(9);
        _columnsTransactions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("date", new TableInfo.Column("date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("time", new TableInfo.Column("time", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("customerId", new TableInfo.Column("customerId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("vendorId", new TableInfo.Column("vendorId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("paymentMode", new TableInfo.Column("paymentMode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTransactions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTransactions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTransactions = new TableInfo("transactions", _columnsTransactions, _foreignKeysTransactions, _indicesTransactions);
        final TableInfo _existingTransactions = TableInfo.read(db, "transactions");
        if (!_infoTransactions.equals(_existingTransactions)) {
          return new RoomOpenHelper.ValidationResult(false, "transactions(com.kiranaflow.app.data.local.TransactionEntity).\n"
                  + " Expected:\n" + _infoTransactions + "\n"
                  + " Found:\n" + _existingTransactions);
        }
        final HashMap<String, TableInfo.Column> _columnsTransactionItems = new HashMap<String, TableInfo.Column>(6);
        _columnsTransactionItems.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactionItems.put("transactionId", new TableInfo.Column("transactionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactionItems.put("itemId", new TableInfo.Column("itemId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactionItems.put("itemNameSnapshot", new TableInfo.Column("itemNameSnapshot", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactionItems.put("qty", new TableInfo.Column("qty", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactionItems.put("price", new TableInfo.Column("price", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTransactionItems = new HashSet<TableInfo.ForeignKey>(2);
        _foreignKeysTransactionItems.add(new TableInfo.ForeignKey("transactions", "CASCADE", "NO ACTION", Arrays.asList("transactionId"), Arrays.asList("id")));
        _foreignKeysTransactionItems.add(new TableInfo.ForeignKey("items", "SET NULL", "NO ACTION", Arrays.asList("itemId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesTransactionItems = new HashSet<TableInfo.Index>(2);
        _indicesTransactionItems.add(new TableInfo.Index("index_transaction_items_transactionId", false, Arrays.asList("transactionId"), Arrays.asList("ASC")));
        _indicesTransactionItems.add(new TableInfo.Index("index_transaction_items_itemId", false, Arrays.asList("itemId"), Arrays.asList("ASC")));
        final TableInfo _infoTransactionItems = new TableInfo("transaction_items", _columnsTransactionItems, _foreignKeysTransactionItems, _indicesTransactionItems);
        final TableInfo _existingTransactionItems = TableInfo.read(db, "transaction_items");
        if (!_infoTransactionItems.equals(_existingTransactionItems)) {
          return new RoomOpenHelper.ValidationResult(false, "transaction_items(com.kiranaflow.app.data.local.TransactionItemEntity).\n"
                  + " Expected:\n" + _infoTransactionItems + "\n"
                  + " Found:\n" + _existingTransactionItems);
        }
        final HashMap<String, TableInfo.Column> _columnsReminders = new HashMap<String, TableInfo.Column>(7);
        _columnsReminders.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("refId", new TableInfo.Column("refId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("dueAt", new TableInfo.Column("dueAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("note", new TableInfo.Column("note", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("isDone", new TableInfo.Column("isDone", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysReminders = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesReminders = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoReminders = new TableInfo("reminders", _columnsReminders, _foreignKeysReminders, _indicesReminders);
        final TableInfo _existingReminders = TableInfo.read(db, "reminders");
        if (!_infoReminders.equals(_existingReminders)) {
          return new RoomOpenHelper.ValidationResult(false, "reminders(com.kiranaflow.app.data.local.ReminderEntity).\n"
                  + " Expected:\n" + _infoReminders + "\n"
                  + " Found:\n" + _existingReminders);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "4f1fbccc78791ccaee28c9dc0b6cd300", "6c693e8d13d2a8d71159f829b70b64d6");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "items","parties","transactions","transaction_items","reminders");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `items`");
      _db.execSQL("DELETE FROM `parties`");
      _db.execSQL("DELETE FROM `transactions`");
      _db.execSQL("DELETE FROM `transaction_items`");
      _db.execSQL("DELETE FROM `reminders`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ItemDao.class, ItemDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PartyDao.class, PartyDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TransactionDao.class, TransactionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ReminderDao.class, ReminderDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ItemDao itemDao() {
    if (_itemDao != null) {
      return _itemDao;
    } else {
      synchronized(this) {
        if(_itemDao == null) {
          _itemDao = new ItemDao_Impl(this);
        }
        return _itemDao;
      }
    }
  }

  @Override
  public PartyDao partyDao() {
    if (_partyDao != null) {
      return _partyDao;
    } else {
      synchronized(this) {
        if(_partyDao == null) {
          _partyDao = new PartyDao_Impl(this);
        }
        return _partyDao;
      }
    }
  }

  @Override
  public TransactionDao transactionDao() {
    if (_transactionDao != null) {
      return _transactionDao;
    } else {
      synchronized(this) {
        if(_transactionDao == null) {
          _transactionDao = new TransactionDao_Impl(this);
        }
        return _transactionDao;
      }
    }
  }

  @Override
  public ReminderDao reminderDao() {
    if (_reminderDao != null) {
      return _reminderDao;
    } else {
      synchronized(this) {
        if(_reminderDao == null) {
          _reminderDao = new ReminderDao_Impl(this);
        }
        return _reminderDao;
      }
    }
  }
}
