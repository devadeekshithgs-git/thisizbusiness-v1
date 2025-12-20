package com.kiranaflow.app.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
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
public final class ReminderDao_Impl implements ReminderDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ReminderEntity> __insertionAdapterOfReminderEntity;

  private final SharedSQLiteStatement __preparedStmtOfMarkDone;

  private final SharedSQLiteStatement __preparedStmtOfDeleteReminder;

  public ReminderDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfReminderEntity = new EntityInsertionAdapter<ReminderEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `reminders` (`id`,`title`,`type`,`refId`,`dueAt`,`note`,`isDone`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ReminderEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getType());
        if (entity.getRefId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getRefId());
        }
        statement.bindLong(5, entity.getDueAt());
        if (entity.getNote() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getNote());
        }
        final int _tmp = entity.isDone() ? 1 : 0;
        statement.bindLong(7, _tmp);
      }
    };
    this.__preparedStmtOfMarkDone = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE reminders SET isDone = 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteReminder = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM reminders WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertReminder(final ReminderEntity reminder,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfReminderEntity.insert(reminder);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markDone(final int id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkDone.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfMarkDone.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteReminder(final int id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteReminder.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfDeleteReminder.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ReminderEntity>> getActiveReminders() {
    final String _sql = "SELECT * FROM reminders WHERE isDone = 0 ORDER BY dueAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"reminders"}, new Callable<List<ReminderEntity>>() {
      @Override
      @NonNull
      public List<ReminderEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfRefId = CursorUtil.getColumnIndexOrThrow(_cursor, "refId");
          final int _cursorIndexOfDueAt = CursorUtil.getColumnIndexOrThrow(_cursor, "dueAt");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final int _cursorIndexOfIsDone = CursorUtil.getColumnIndexOrThrow(_cursor, "isDone");
          final List<ReminderEntity> _result = new ArrayList<ReminderEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ReminderEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final Integer _tmpRefId;
            if (_cursor.isNull(_cursorIndexOfRefId)) {
              _tmpRefId = null;
            } else {
              _tmpRefId = _cursor.getInt(_cursorIndexOfRefId);
            }
            final long _tmpDueAt;
            _tmpDueAt = _cursor.getLong(_cursorIndexOfDueAt);
            final String _tmpNote;
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _tmpNote = null;
            } else {
              _tmpNote = _cursor.getString(_cursorIndexOfNote);
            }
            final boolean _tmpIsDone;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDone);
            _tmpIsDone = _tmp != 0;
            _item = new ReminderEntity(_tmpId,_tmpTitle,_tmpType,_tmpRefId,_tmpDueAt,_tmpNote,_tmpIsDone);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
