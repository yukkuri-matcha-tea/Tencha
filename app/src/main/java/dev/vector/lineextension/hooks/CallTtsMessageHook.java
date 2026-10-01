package dev.vector.lineextension.hooks;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import dev.vector.lineextension.utils.LineDBUtils;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Reads version-mapped plaintext thrift messages from LINE's send/receive processors. */
public final class CallTtsMessageHook implements BaseHook {
  private static final ScheduledExecutorService SELF_ROWS =
      Executors.newSingleThreadScheduledExecutor(
          task -> {
            Thread thread = new Thread(task, "TenchaTtsRows");
            thread.setDaemon(true);
            return thread;
          });
  private static final Set<String> pendingRows = ConcurrentHashMap.newKeySet();

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) {
    if (!config.callTts.enabled) return;
    LineVersion.CallTts mapping = LineVersion.get().callTts;
    hookSelfMessageInsert();
    hookMessageMethod(
        lpparam.classLoader,
        mapping.receiveProcessorClass,
        mapping.receivePlaintextMethod,
        mapping.messageClass,
        false);
  }

  /**
   * Captures successful local inserts, then reads through an independent connection. Only a
   * committed, fixed plaintext row is spoken: insert can precede commit and send completion.
   */
  private static void hookSelfMessageInsert() {
    try {
      Method insert =
          Reflect.findMethodExact(
              SQLiteDatabase.class,
              "insertWithOnConflict",
              String.class,
              String.class,
              ContentValues.class,
              int.class);
      Vector.module
          .hook(insert)
          .intercept(
              chain -> {
                Object result = chain.proceed();
                try {
                  if (!Main.options.callTts.enabled || !CallTtsManager.isSessionEnabled()) {
                    return result;
                  }
                  String table = (String) chain.getArg(0);
                  ContentValues values = (ContentValues) chain.getArg(2);
                  if (!"chat_history".equals(table) || values == null) return result;
                  if (!(result instanceof Long) || (Long) result <= 0) return result;
                  SQLiteDatabase source = (SQLiteDatabase) chain.getThisObject();
                  File expected = Vector.currentApplication().getDatabasePath("naver_line");
                  if (!expected
                      .getCanonicalPath()
                      .equals(new File(source.getPath()).getCanonicalPath())) return result;
                  String content = values.getAsString("content");
                  String fromMid = values.getAsString("from_mid");
                  String myMid = LineDBUtils.getMyMid();
                  if (!CallTtsPolicy.isPlainSelfRow(
                      values.getAsInteger("type"),
                      values.getAsInteger("attachement_type"),
                      fromMid,
                      myMid,
                      content)) return result;
                  int token = CallTtsManager.enabledSessionToken();
                  if (token < 0) return result;
                  long rowId = (Long) result;
                  String key = token + ":" + rowId;
                  if (pendingRows.add(key))
                    SELF_ROWS.schedule(
                        () ->
                            readCommittedSelfRow(
                                expected.getAbsolutePath(),
                                rowId,
                                token,
                                key,
                                android.os.SystemClock.elapsedRealtime() + 60_000L),
                        100L,
                        TimeUnit.MILLISECONDS);
                } catch (Throwable error) {
                  Vector.log(
                      "Tencha: TTS self insert inspection failed: "
                          + error.getClass().getSimpleName());
                }
                return result;
              });
      Vector.log("Tencha: TTS self-message chat_history hook installed");
    } catch (Throwable error) {
      Vector.log("Tencha: TTS self-message chat_history hook failed", error);
    }
  }

  private static void readCommittedSelfRow(
      String path, long rowId, int token, String key, long deadline) {
    boolean retry = false;
    try {
      if (!Main.options.callTts.enabled || token != CallTtsManager.enabledSessionToken()) return;
      try (SQLiteDatabase db =
              SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
          Cursor row =
              db.rawQuery(
                  "SELECT server_id, from_mid, content, type, attachement_type, status FROM chat_history WHERE id = ?",
                  new String[] {String.valueOf(rowId)})) {
        if (!row.moveToFirst()) {
          retry = true; // Outer transaction may not have committed yet.
        } else {
          String mid = LineDBUtils.getMyMid();
          if (!CallTtsPolicy.isPlainSelfRow(
              row.isNull(3) ? null : row.getInt(3),
              row.isNull(4) ? null : row.getInt(4),
              row.getString(1),
              mid,
              row.getString(2))) return;
          if (row.isNull(5) || CallTtsPolicy.isFailedRowStatus(row.getInt(5))) return;
          if (CallTtsPolicy.isFixedRowStatus(row.getInt(5))
              && !row.isNull(0)
              && !row.getString(0).isEmpty()) {
            CallTtsManager.onCommittedSelfText(
                token, row.getString(0), mid, LineDBUtils.getMyName(), row.getString(2));
          } else {
            retry = true;
          }
        }
      }
    } catch (Throwable error) {
      Vector.log("Tencha: TTS committed row read failed: " + error.getClass().getSimpleName());
    } finally {
      if (retry
          && android.os.SystemClock.elapsedRealtime() < deadline
          && token == CallTtsManager.enabledSessionToken()) {
        SELF_ROWS.schedule(
            () -> readCommittedSelfRow(path, rowId, token, key, deadline),
            250L,
            TimeUnit.MILLISECONDS);
      } else {
        pendingRows.remove(key);
      }
    }
  }

  private static void hookMessageMethod(
      ClassLoader loader,
      String ownerName,
      String methodName,
      String messageClassName,
      boolean outgoing) {
    if (ownerName.isEmpty() || methodName.isEmpty() || messageClassName.isEmpty()) return;
    try {
      Class<?> owner = Reflect.findClass(ownerName, loader);
      boolean installed = false;
      for (Method method : owner.getDeclaredMethods()) {
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length == 0 || !messageClassName.equals(parameters[0].getName())) continue;
        if (!methodName.equals(method.getName())) continue;
        method.setAccessible(true);
        Vector.module
            .hook(method)
            .intercept(
                chain -> {
                  if (Main.options.callTts.enabled) process(chain.getArg(0), outgoing);
                  return chain.proceed();
                });
        installed = true;
      }
      if (!installed)
        Vector.log("Tencha: TTS message stage not found: " + ownerName + "#" + methodName);
      else Vector.log("Tencha: TTS message stage hooked: " + ownerName + "#" + methodName);
    } catch (Throwable error) {
      Vector.log("Tencha: TTS message hook failed for " + ownerName, error);
    }
  }

  private static void process(Object message, boolean outgoing) {
    if (message == null) return;
    try {
      LineVersion.CallTts mapping = LineVersion.get().callTts;
      Object contentType = Reflect.getObjectField(message, mapping.messageContentTypeField);
      if (contentType == null || !mapping.textContentTypeName.equals(contentType.toString()))
        return;
      String text = (String) Reflect.getObjectField(message, mapping.messageTextField);
      if (text == null || text.trim().isEmpty()) return;
      String id = (String) Reflect.getObjectField(message, mapping.messageIdField);
      String senderMid = (String) Reflect.getObjectField(message, mapping.messageFromField);
      String myMid = LineDBUtils.getMyMid();
      boolean fromSelf = outgoing || (myMid != null && myMid.equals(senderMid));
      if (fromSelf && (senderMid == null || senderMid.isEmpty())) senderMid = myMid;
      String senderName = LineDBUtils.resolveMemberName(senderMid);
      Log.i("TenchaCall", "TTS text observed: direction=" + (outgoing ? "outgoing" : "incoming"));
      CallTtsManager.onTextMessage(id, senderMid, senderName, text, fromSelf);
    } catch (Throwable error) {
      Vector.log("Tencha: TTS message parse failed: " + error.getClass().getSimpleName());
    }
  }
}
