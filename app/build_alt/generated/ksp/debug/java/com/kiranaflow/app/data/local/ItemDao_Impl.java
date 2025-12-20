package com.kiranaflow.app.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ItemDao_Impl implements ItemDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ItemEntity> __insertionAdapterOfItemEntity;

  private final SharedSQLiteStatement __preparedStmtOfSoftDelete;

  private final SharedSQLiteStatement __preparedStmtOfDecreaseStock;

  private final SharedSQLiteStatement __preparedStmtOfIncreaseStock;

  public ItemDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfItemEntity = new EntityInsertionAdapter<ItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `items` (`id`,`name`,`price`,`stock`,`category`,`rackLocation`,`marginPercentage`,`barcode`,`costPrice`,`gstPercentage`,`reorderPoint`,`vendorId`,`imageUri`,`expiryDateMillis`,`isDeleted`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ItemEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindDouble(3, entity.getPrice());
        statement.bindLong(4, entity.getStock());
        statement.bindString(5, entity.getCategory());
        if (entity.getRackLocation() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getRackLocation());
        }
        statement.bindDouble(7, entity.getMarginPercentage());
        if (entity.getBarcode() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getBarcode());
        }
        statement.bindDouble(9, entity.getCostPrice());
        if (entity.getGstPercentage() == null) {
          statement.bindNull(10);
        } else {
          statement.bindDouble(10, entity.getGstPercentage());
        }
        statement.bindLong(11, entity.getReorderPoint());
        if (entity.getVendorId() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getVendorId());
        }
        if (entity.getImageUri() == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, entity.getImageUri());
        }
        if (entity.getExpiryDateMillis() == null) {
          statement.bindNull(14);
        } else {
          statement.bindLong(14, entity.getExpiryDateMillis());
        }
        final int _tmp = entity.isDeleted() ? 1 : 0;
        statement.bindLong(15, _tmp);
      }
    };
    this.__preparedStmtOfSoftDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE items SET isDeleted = 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDecreaseStock = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE items SET stock = stock - ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfIncreaseStock = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE items SET stock = stock + ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertItem(final ItemEntity item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfItemEntity.insert(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object softDelete(final int itemId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSoftDelete.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, itemId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSoftDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object decreaseStock(final int itemId, final int qty,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDecreaseStock.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, qty);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, itemId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDecreaseStock.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object increaseStock(final int itemId, final int qty,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncreaseStock.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, qty);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, itemId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncreaseStock.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ItemEntity>> getAllItems() {
    final String _sql = "SELECT * FROM items WHERE isDeleted = 0 ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"items"}, new Callable<List<ItemEntity>>() {
      @Override
      @NonNull
      public List<ItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "price");
          final int _cursorIndexOfStock = CursorUtil.getColumnIndexOrThrow(_cursor, "stock");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfRackLocation = CursorUtil.getColumnIndexOrThrow(_cursor, "rackLocation");
          final int _cursorIndexOfMarginPercentage = CursorUtil.getColumnIndexOrThrow(_cursor, "marginPercentage");
          final int _cursorIndexOfBarcode = CursorUtil.getColumnIndexOrThrow(_cursor, "barcode");
          final int _cursorIndexOfCostPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "costPrice");
          final int _cursorIndexOfGstPercentage = CursorUtil.getColumnIndexOrThrow(_cursor, "gstPercentage");
          final int _cursorIndexOfReorderPoint = CursorUtil.getColumnIndexOrThrow(_cursor, "reorderPoint");
          final int _cursorIndexOfVendorId = CursorUtil.getColumnIndexOrThrow(_cursor, "vendorId");
          final int _cursorIndexOfImageUri = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUri");
          final int _cursorIndexOfExpiryDateMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "expiryDateMillis");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final List<ItemEntity> _result = new ArrayList<ItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ItemEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final double _tmpPrice;
            _tmpPrice = _cursor.getDouble(_cursorIndexOfPrice);
            final int _tmpStock;
            _tmpStock = _cursor.getInt(_cursorIndexOfStock);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpRackLocation;
            if (_cursor.isNull(_cursorIndexOfRackLocation)) {
              _tmpRackLocation = null;
            } else {
              _tmpRackLocation = _cursor.getString(_cursorIndexOfRackLocation);
            }
            final double _tmpMarginPercentage;
            _tmpMarginPercentage = _cursor.getDouble(_cursorIndexOfMarginPercentage);
            final String _tmpBarcode;
            if (_cursor.isNull(_cursorIndexOfBarcode)) {
              _tmpBarcode = null;
            } else {
              _tmpBarcode = _cursor.getString(_cursorIndexOfBarcode);
            }
            final double _tmpCostPrice;
            _tmpCostPrice = _cursor.getDouble(_cursorIndexOfCostPrice);
            final Double _tmpGstPercentage;
            if (_cursor.isNull(_cursorIndexOfGstPercentage)) {
              _tmpGstPercentage = null;
            } else {
              _tmpGstPercentage = _cursor.getDouble(_cursorIndexOfGstPercentage);
            }
            final int _tmpReorderPoint;
            _tmpReorderPoint = _cursor.getInt(_cursorIndexOfReorderPoint);
            final Integer _tmpVendorId;
            if (_cursor.isNull(_cursorIndexOfVendorId)) {
              _tmpVendorId = null;
            } else {
              _tmpVendorId = _cursor.getInt(_cursorIndexOfVendorId);
            }
            final String _tmpImageUri;
            if (_cursor.isNull(_cursorIndexOfImageUri)) {
              _tmpImageUri = null;
            } else {
              _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
            }
            final Long _tmpExpiryDateMillis;
            if (_cursor.isNull(_cursorIndexOfExpiryDateMillis)) {
              _tmpExpiryDateMillis = null;
            } else {
              _tmpExpiryDateMillis = _cursor.getLong(_cursorIndexOfExpiryDateMillis);
            }
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            _item = new ItemEntity(_tmpId,_tmpName,_tmpPrice,_tmpStock,_tmpCategory,_tmpRackLocation,_tmpMarginPercentage,_tmpBarcode,_tmpCostPrice,_tmpGstPercentage,_tmpReorderPoint,_tmpVendorId,_tmpImageUri,_tmpExpiryDateMillis,_tmpIsDeleted);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getItemByBarcode(final String barcode,
      final Continuation<? super ItemEntity> $completion) {
    final String _sql = "SELECT * FROM items WHERE barcode = ? AND isDeleted = 0 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, barcode);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ItemEntity>() {
      @Override
      @Nullable
      public ItemEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "price");
          final int _cursorIndexOfStock = CursorUtil.getColumnIndexOrThrow(_cursor, "stock");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfRackLocation = CursorUtil.getColumnIndexOrThrow(_cursor, "rackLocation");
          final int _cursorIndexOfMarginPercentage = CursorUtil.getColumnIndexOrThrow(_cursor, "marginPercentage");
          final int _cursorIndexOfBarcode = CursorUtil.getColumnIndexOrThrow(_cursor, "barcode");
          final int _cursorIndexOfCostPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "costPrice");
          final int _cursorIndexOfGstPercentage = CursorUtil.getColumnIndexOrThrow(_cursor, "gstPercentage");
          final int _cursorIndexOfReorderPoint = CursorUtil.getColumnIndexOrThrow(_cursor, "reorderPoint");
          final int _cursorIndexOfVendorId = CursorUtil.getColumnIndexOrThrow(_cursor, "vendorId");
          final int _cursorIndexOfImageUri = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUri");
          final int _cursorIndexOfExpiryDateMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "expiryDateMillis");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final ItemEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final double _tmpPrice;
            _tmpPrice = _cursor.getDouble(_cursorIndexOfPrice);
            final int _tmpStock;
            _tmpStock = _cursor.getInt(_cursorIndexOfStock);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpRackLocation;
            if (_cursor.isNull(_cursorIndexOfRackLocation)) {
              _tmpRackLocation = null;
            } else {
              _tmpRackLocation = _cursor.getString(_cursorIndexOfRackLocation);
            }
            final double _tmpMarginPercentage;
            _tmpMarginPercentage = _cursor.getDouble(_cursorIndexOfMarginPercentage);
            final String _tmpBarcode;
            if (_cursor.isNull(_cursorIndexOfBarcode)) {
              _tmpBarcode = null;
            } else {
              _tmpBarcode = _cursor.getString(_cursorIndexOfBarcode);
            }
            final double _tmpCostPrice;
            _tmpCostPrice = _cursor.getDouble(_cursorIndexOfCostPrice);
            final Double _tmpGstPercentage;
            if (_cursor.isNull(_cursorIndexOfGstPercentage)) {
              _tmpGstPercentage = null;
            } else {
              _tmpGstPercentage = _cursor.getDouble(_cursorIndexOfGstPercentage);
            }
            final int _tmpReorderPoint;
            _tmpReorderPoint = _cursor.getInt(_cursorIndexOfReorderPoint);
            final Integer _tmpVendorId;
            if (_cursor.isNull(_cursorIndexOfVendorId)) {
              _tmpVendorId = null;
            } else {
              _tmpVendorId = _cursor.getInt(_cursorIndexOfVendorId);
            }
            final String _tmpImageUri;
            if (_cursor.isNull(_cursorIndexOfImageUri)) {
              _tmpImageUri = null;
            } else {
              _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
            }
            final Long _tmpExpiryDateMillis;
            if (_cursor.isNull(_cursorIndexOfExpiryDateMillis)) {
              _tmpExpiryDateMillis = null;
            } else {
              _tmpExpiryDateMillis = _cursor.getLong(_cursorIndexOfExpiryDateMillis);
            }
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            _result = new ItemEntity(_tmpId,_tmpName,_tmpPrice,_tmpStock,_tmpCategory,_tmpRackLocation,_tmpMarginPercentage,_tmpBarcode,_tmpCostPrice,_tmpGstPercentage,_tmpReorderPoint,_tmpVendorId,_tmpImageUri,_tmpExpiryDateMillis,_tmpIsDeleted);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object softDeleteMany(final List<Integer> itemIds,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE items SET isDeleted = 1 WHERE id IN (");
        final int _inputSize = itemIds.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (int _item : itemIds) {
          _stmt.bindLong(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
