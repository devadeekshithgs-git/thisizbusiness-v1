package com.kiranaflow.app.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
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
public final class TransactionDao_Impl implements TransactionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TransactionEntity> __insertionAdapterOfTransactionEntity;

  private final EntityInsertionAdapter<TransactionItemEntity> __insertionAdapterOfTransactionItemEntity;

  public TransactionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTransactionEntity = new EntityInsertionAdapter<TransactionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `transactions` (`id`,`title`,`type`,`amount`,`date`,`time`,`customerId`,`vendorId`,`paymentMode`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TransactionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getType());
        statement.bindDouble(4, entity.getAmount());
        statement.bindLong(5, entity.getDate());
        statement.bindString(6, entity.getTime());
        if (entity.getCustomerId() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getCustomerId());
        }
        if (entity.getVendorId() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getVendorId());
        }
        statement.bindString(9, entity.getPaymentMode());
      }
    };
    this.__insertionAdapterOfTransactionItemEntity = new EntityInsertionAdapter<TransactionItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `transaction_items` (`id`,`transactionId`,`itemId`,`itemNameSnapshot`,`qty`,`price`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TransactionItemEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTransactionId());
        if (entity.getItemId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getItemId());
        }
        statement.bindString(4, entity.getItemNameSnapshot());
        statement.bindLong(5, entity.getQty());
        statement.bindDouble(6, entity.getPrice());
      }
    };
  }

  @Override
  public Object insertTransaction(final TransactionEntity transaction,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTransactionEntity.insertAndReturnId(transaction);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertTransactionItems(final List<TransactionItemEntity> items,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTransactionItemEntity.insert(items);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSale(final TransactionEntity transaction,
      final List<TransactionItemEntity> items, final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> TransactionDao.DefaultImpls.insertSale(TransactionDao_Impl.this, transaction, items, __cont), $completion);
  }

  @Override
  public Flow<List<TransactionEntity>> getAllTransactions() {
    final String _sql = "SELECT * FROM transactions ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<TransactionEntity>>() {
      @Override
      @NonNull
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
          final int _cursorIndexOfCustomerId = CursorUtil.getColumnIndexOrThrow(_cursor, "customerId");
          final int _cursorIndexOfVendorId = CursorUtil.getColumnIndexOrThrow(_cursor, "vendorId");
          final int _cursorIndexOfPaymentMode = CursorUtil.getColumnIndexOrThrow(_cursor, "paymentMode");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final String _tmpTime;
            _tmpTime = _cursor.getString(_cursorIndexOfTime);
            final Integer _tmpCustomerId;
            if (_cursor.isNull(_cursorIndexOfCustomerId)) {
              _tmpCustomerId = null;
            } else {
              _tmpCustomerId = _cursor.getInt(_cursorIndexOfCustomerId);
            }
            final Integer _tmpVendorId;
            if (_cursor.isNull(_cursorIndexOfVendorId)) {
              _tmpVendorId = null;
            } else {
              _tmpVendorId = _cursor.getInt(_cursorIndexOfVendorId);
            }
            final String _tmpPaymentMode;
            _tmpPaymentMode = _cursor.getString(_cursorIndexOfPaymentMode);
            _item = new TransactionEntity(_tmpId,_tmpTitle,_tmpType,_tmpAmount,_tmpDate,_tmpTime,_tmpCustomerId,_tmpVendorId,_tmpPaymentMode);
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
  public Flow<List<TransactionEntity>> getTransactionsForParty(final int customerId) {
    final String _sql = "SELECT * FROM transactions WHERE customerId = ? OR vendorId = ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, customerId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, customerId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<TransactionEntity>>() {
      @Override
      @NonNull
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
          final int _cursorIndexOfCustomerId = CursorUtil.getColumnIndexOrThrow(_cursor, "customerId");
          final int _cursorIndexOfVendorId = CursorUtil.getColumnIndexOrThrow(_cursor, "vendorId");
          final int _cursorIndexOfPaymentMode = CursorUtil.getColumnIndexOrThrow(_cursor, "paymentMode");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final String _tmpTime;
            _tmpTime = _cursor.getString(_cursorIndexOfTime);
            final Integer _tmpCustomerId;
            if (_cursor.isNull(_cursorIndexOfCustomerId)) {
              _tmpCustomerId = null;
            } else {
              _tmpCustomerId = _cursor.getInt(_cursorIndexOfCustomerId);
            }
            final Integer _tmpVendorId;
            if (_cursor.isNull(_cursorIndexOfVendorId)) {
              _tmpVendorId = null;
            } else {
              _tmpVendorId = _cursor.getInt(_cursorIndexOfVendorId);
            }
            final String _tmpPaymentMode;
            _tmpPaymentMode = _cursor.getString(_cursorIndexOfPaymentMode);
            _item = new TransactionEntity(_tmpId,_tmpTitle,_tmpType,_tmpAmount,_tmpDate,_tmpTime,_tmpCustomerId,_tmpVendorId,_tmpPaymentMode);
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
  public Flow<List<TransactionItemEntity>> getAllTransactionItems() {
    final String _sql = "SELECT * FROM transaction_items";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transaction_items"}, new Callable<List<TransactionItemEntity>>() {
      @Override
      @NonNull
      public List<TransactionItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTransactionId = CursorUtil.getColumnIndexOrThrow(_cursor, "transactionId");
          final int _cursorIndexOfItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "itemId");
          final int _cursorIndexOfItemNameSnapshot = CursorUtil.getColumnIndexOrThrow(_cursor, "itemNameSnapshot");
          final int _cursorIndexOfQty = CursorUtil.getColumnIndexOrThrow(_cursor, "qty");
          final int _cursorIndexOfPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "price");
          final List<TransactionItemEntity> _result = new ArrayList<TransactionItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionItemEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpTransactionId;
            _tmpTransactionId = _cursor.getInt(_cursorIndexOfTransactionId);
            final Integer _tmpItemId;
            if (_cursor.isNull(_cursorIndexOfItemId)) {
              _tmpItemId = null;
            } else {
              _tmpItemId = _cursor.getInt(_cursorIndexOfItemId);
            }
            final String _tmpItemNameSnapshot;
            _tmpItemNameSnapshot = _cursor.getString(_cursorIndexOfItemNameSnapshot);
            final int _tmpQty;
            _tmpQty = _cursor.getInt(_cursorIndexOfQty);
            final double _tmpPrice;
            _tmpPrice = _cursor.getDouble(_cursorIndexOfPrice);
            _item = new TransactionItemEntity(_tmpId,_tmpTransactionId,_tmpItemId,_tmpItemNameSnapshot,_tmpQty,_tmpPrice);
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
  public Flow<List<TransactionItemEntity>> getItemsForTransaction(final int transactionId) {
    final String _sql = "SELECT * FROM transaction_items WHERE transactionId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, transactionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transaction_items"}, new Callable<List<TransactionItemEntity>>() {
      @Override
      @NonNull
      public List<TransactionItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTransactionId = CursorUtil.getColumnIndexOrThrow(_cursor, "transactionId");
          final int _cursorIndexOfItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "itemId");
          final int _cursorIndexOfItemNameSnapshot = CursorUtil.getColumnIndexOrThrow(_cursor, "itemNameSnapshot");
          final int _cursorIndexOfQty = CursorUtil.getColumnIndexOrThrow(_cursor, "qty");
          final int _cursorIndexOfPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "price");
          final List<TransactionItemEntity> _result = new ArrayList<TransactionItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionItemEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpTransactionId;
            _tmpTransactionId = _cursor.getInt(_cursorIndexOfTransactionId);
            final Integer _tmpItemId;
            if (_cursor.isNull(_cursorIndexOfItemId)) {
              _tmpItemId = null;
            } else {
              _tmpItemId = _cursor.getInt(_cursorIndexOfItemId);
            }
            final String _tmpItemNameSnapshot;
            _tmpItemNameSnapshot = _cursor.getString(_cursorIndexOfItemNameSnapshot);
            final int _tmpQty;
            _tmpQty = _cursor.getInt(_cursorIndexOfQty);
            final double _tmpPrice;
            _tmpPrice = _cursor.getDouble(_cursorIndexOfPrice);
            _item = new TransactionItemEntity(_tmpId,_tmpTransactionId,_tmpItemId,_tmpItemNameSnapshot,_tmpQty,_tmpPrice);
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
  public Object deleteTransactionsByIds(final List<Integer> ids,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM transactions WHERE id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (int _item : ids) {
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
