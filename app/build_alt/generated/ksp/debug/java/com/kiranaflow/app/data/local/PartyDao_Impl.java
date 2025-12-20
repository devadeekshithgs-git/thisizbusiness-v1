package com.kiranaflow.app.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class PartyDao_Impl implements PartyDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PartyEntity> __insertionAdapterOfPartyEntity;

  private final EntityDeletionOrUpdateAdapter<PartyEntity> __deletionAdapterOfPartyEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateBalance;

  public PartyDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPartyEntity = new EntityInsertionAdapter<PartyEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `parties` (`id`,`name`,`phone`,`type`,`gstNumber`,`balance`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PartyEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getPhone());
        statement.bindString(4, entity.getType());
        if (entity.getGstNumber() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getGstNumber());
        }
        statement.bindDouble(6, entity.getBalance());
      }
    };
    this.__deletionAdapterOfPartyEntity = new EntityDeletionOrUpdateAdapter<PartyEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `parties` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PartyEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateBalance = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE parties SET balance = balance + ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertParty(final PartyEntity party, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPartyEntity.insert(party);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteParty(final PartyEntity party, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfPartyEntity.handle(party);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateBalance(final int partyId, final double amount,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateBalance.acquire();
        int _argIndex = 1;
        _stmt.bindDouble(_argIndex, amount);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, partyId);
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
          __preparedStmtOfUpdateBalance.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PartyEntity>> getVendors() {
    final String _sql = "SELECT * FROM parties WHERE type = 'VENDOR' ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"parties"}, new Callable<List<PartyEntity>>() {
      @Override
      @NonNull
      public List<PartyEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPhone = CursorUtil.getColumnIndexOrThrow(_cursor, "phone");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfGstNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "gstNumber");
          final int _cursorIndexOfBalance = CursorUtil.getColumnIndexOrThrow(_cursor, "balance");
          final List<PartyEntity> _result = new ArrayList<PartyEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PartyEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpPhone;
            _tmpPhone = _cursor.getString(_cursorIndexOfPhone);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpGstNumber;
            if (_cursor.isNull(_cursorIndexOfGstNumber)) {
              _tmpGstNumber = null;
            } else {
              _tmpGstNumber = _cursor.getString(_cursorIndexOfGstNumber);
            }
            final double _tmpBalance;
            _tmpBalance = _cursor.getDouble(_cursorIndexOfBalance);
            _item = new PartyEntity(_tmpId,_tmpName,_tmpPhone,_tmpType,_tmpGstNumber,_tmpBalance);
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
  public Flow<List<PartyEntity>> getCustomers() {
    final String _sql = "SELECT * FROM parties WHERE type = 'CUSTOMER' ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"parties"}, new Callable<List<PartyEntity>>() {
      @Override
      @NonNull
      public List<PartyEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPhone = CursorUtil.getColumnIndexOrThrow(_cursor, "phone");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfGstNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "gstNumber");
          final int _cursorIndexOfBalance = CursorUtil.getColumnIndexOrThrow(_cursor, "balance");
          final List<PartyEntity> _result = new ArrayList<PartyEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PartyEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpPhone;
            _tmpPhone = _cursor.getString(_cursorIndexOfPhone);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpGstNumber;
            if (_cursor.isNull(_cursorIndexOfGstNumber)) {
              _tmpGstNumber = null;
            } else {
              _tmpGstNumber = _cursor.getString(_cursorIndexOfGstNumber);
            }
            final double _tmpBalance;
            _tmpBalance = _cursor.getDouble(_cursorIndexOfBalance);
            _item = new PartyEntity(_tmpId,_tmpName,_tmpPhone,_tmpType,_tmpGstNumber,_tmpBalance);
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
  public Flow<List<PartyEntity>> getAllParties() {
    final String _sql = "SELECT * FROM parties ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"parties"}, new Callable<List<PartyEntity>>() {
      @Override
      @NonNull
      public List<PartyEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPhone = CursorUtil.getColumnIndexOrThrow(_cursor, "phone");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfGstNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "gstNumber");
          final int _cursorIndexOfBalance = CursorUtil.getColumnIndexOrThrow(_cursor, "balance");
          final List<PartyEntity> _result = new ArrayList<PartyEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PartyEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpPhone;
            _tmpPhone = _cursor.getString(_cursorIndexOfPhone);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpGstNumber;
            if (_cursor.isNull(_cursorIndexOfGstNumber)) {
              _tmpGstNumber = null;
            } else {
              _tmpGstNumber = _cursor.getString(_cursorIndexOfGstNumber);
            }
            final double _tmpBalance;
            _tmpBalance = _cursor.getDouble(_cursorIndexOfBalance);
            _item = new PartyEntity(_tmpId,_tmpName,_tmpPhone,_tmpType,_tmpGstNumber,_tmpBalance);
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
  public Object findCustomerByPhone(final String phone,
      final Continuation<? super PartyEntity> $completion) {
    final String _sql = "SELECT * FROM parties WHERE type = 'CUSTOMER' AND phone = ? ORDER BY id DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, phone);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PartyEntity>() {
      @Override
      @Nullable
      public PartyEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPhone = CursorUtil.getColumnIndexOrThrow(_cursor, "phone");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfGstNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "gstNumber");
          final int _cursorIndexOfBalance = CursorUtil.getColumnIndexOrThrow(_cursor, "balance");
          final PartyEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpPhone;
            _tmpPhone = _cursor.getString(_cursorIndexOfPhone);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpGstNumber;
            if (_cursor.isNull(_cursorIndexOfGstNumber)) {
              _tmpGstNumber = null;
            } else {
              _tmpGstNumber = _cursor.getString(_cursorIndexOfGstNumber);
            }
            final double _tmpBalance;
            _tmpBalance = _cursor.getDouble(_cursorIndexOfBalance);
            _result = new PartyEntity(_tmpId,_tmpName,_tmpPhone,_tmpType,_tmpGstNumber,_tmpBalance);
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
  public Object findPartyByPhoneAndType(final String phone, final String type,
      final Continuation<? super PartyEntity> $completion) {
    final String _sql = "SELECT * FROM parties WHERE type = ? AND phone = ? ORDER BY id DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, type);
    _argIndex = 2;
    _statement.bindString(_argIndex, phone);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PartyEntity>() {
      @Override
      @Nullable
      public PartyEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfPhone = CursorUtil.getColumnIndexOrThrow(_cursor, "phone");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfGstNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "gstNumber");
          final int _cursorIndexOfBalance = CursorUtil.getColumnIndexOrThrow(_cursor, "balance");
          final PartyEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpPhone;
            _tmpPhone = _cursor.getString(_cursorIndexOfPhone);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpGstNumber;
            if (_cursor.isNull(_cursorIndexOfGstNumber)) {
              _tmpGstNumber = null;
            } else {
              _tmpGstNumber = _cursor.getString(_cursorIndexOfGstNumber);
            }
            final double _tmpBalance;
            _tmpBalance = _cursor.getDouble(_cursorIndexOfBalance);
            _result = new PartyEntity(_tmpId,_tmpName,_tmpPhone,_tmpType,_tmpGstNumber,_tmpBalance);
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
  public Object deletePartiesByIds(final List<Integer> partyIds,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM parties WHERE id IN (");
        final int _inputSize = partyIds.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (int _item : partyIds) {
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
