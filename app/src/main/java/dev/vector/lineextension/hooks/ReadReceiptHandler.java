package dev.vector.lineextension.hooks;

import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.SettingsStore;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import dev.vector.lineextension.core.ReadBlockStore;
import dev.vector.lineextension.core.RuntimeReporter;
import dev.vector.lineextension.utils.LineDBUtils;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public class ReadReceiptHandler implements BaseHook {

  private static final String TIME_FMT = "yyyy-MM-dd HH:mm:ss";
  private static final String NOP_CHAT_ID = "VECTOR_NOP";
  private static final String UNKNOWN_READER_NAME = "Unknown";
  private static final int MAX_HISTORY_CHATS = 200;
  private static final int MAX_MESSAGES_PER_CHAT = 2000;

  private static volatile boolean isBulkReading = false;
  private static final Set<String> pendingManualReads = ConcurrentHashMap.newKeySet();

  @Override
  public void hook(final VectorConfig config, LoadParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    ClassLoader cl = lpparam.classLoader;

    hookOperationForHistory(cfg, cl);
    hookThriftReadReceipt(cfg, cl, config);
    hookReadReceiptManager(cfg, cl, config);
  }

  private void hookOperationForHistory(LineVersion.Config cfg, ClassLoader cl) {
    try {
      Vector.hookAll(
          Reflect.findClass(cfg.unsend.notifiedReadMessageHandlerClass, cl),
          cfg.unsend.methodReadBuffer,
          chain -> {
            Object result = chain.proceed();
            if (!recordingEnabled()) return result;
            Object op = chain.getArg(1);
            if (op == null || op instanceof String) return result;
            try {
              Object type = Reflect.getObjectField(op, cfg.unsend.operationTypeField);
              if (type == null
                  || !cfg.readReceipt.operationNotifiedReadName.equals(type.toString())) {
                return result;
              }
              recordReadEvent(
                  (String) Reflect.getObjectField(op, cfg.unsend.operationParam1Field),
                  (String) Reflect.getObjectField(op, cfg.unsend.operationParam2Field),
                  (String) Reflect.getObjectField(op, cfg.unsend.operationParam3Field),
                  Reflect.getLongField(op, cfg.unsend.operationCreatedTimeField));
            } catch (Throwable ignored) {
            }
            return result;
          });
    } catch (Throwable ignored) {
    }
  }

  private boolean recordingEnabled() {
    return SettingsStore.get("record_read_history", false);
  }

  private void hookThriftReadReceipt(LineVersion.Config cfg, ClassLoader cl, VectorConfig config) {
    try {
      Vector.hookAll(
          cl.loadClass(cfg.thrift.talkServiceClientImplClass),
          cfg.thrift.v1,
          chain -> {
            List<Object> args = chain.getArgs();
            if (args.isEmpty() || args.get(0) == null) return chain.proceed();
            String chatId = firstString(args);
            if (containsPendingManualRead(args) || !shouldBlockReadReceipt(config, chatId)) {
              return chain.proceed();
            }
            if (args.get(0) instanceof String) {
              Object[] newArgs = args.toArray();
              newArgs[0] = NOP_CHAT_ID;
              return chain.proceed(newArgs);
            }
            return null;
          });
    } catch (Throwable ignored) {
    }
  }

  private boolean containsPendingManualRead(List<Object> args) {
    for (Object a : args) {
      if (a instanceof String && pendingManualReads.contains(a)) return true;
    }
    return false;
  }

  private String firstString(List<Object> args) {
    for (Object arg : args) {
      if (arg instanceof String) return (String) arg;
    }
    return null;
  }

  private void hookReadReceiptManager(LineVersion.Config cfg, ClassLoader cl, VectorConfig config) {
    Class<?> managerCls = getManagerClass(cfg, cl);
    if (managerCls == null) return;

    hookSendReadReceipt(managerCls, cfg, config);
    hookExecuteReadReceiptAsync(managerCls, cfg, config);
    hookResolveReadTarget(managerCls, cfg);
    hookReadAll(managerCls, cfg, config);
  }

  private void hookResolveReadTarget(Class<?> managerCls, LineVersion.Config cfg) {
    String method = cfg.readReceipt.methodResolveReadTarget;
    if (method == null || method.isEmpty()) return;
    try {
      Vector.hookAll(
          managerCls,
          method,
          chain -> {
            Object result = chain.proceed();
            if (result instanceof Long
                && (Long) result == 0L
                && !chain.getArgs().isEmpty()
                && chain.getArg(0) instanceof String) {
              pendingManualReads.remove(chain.getArg(0));
            }
            return result;
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookSendReadReceipt(
      Class<?> managerCls, LineVersion.Config cfg, VectorConfig config) {
    try {
      Vector.hookAll(
          managerCls,
          cfg.readReceipt.methodSendReadReceipt,
          chain -> {
            Class<?>[] params = ((Method) chain.getExecutable()).getParameterTypes();
            String chatId = null;
            if (params.length == 3 && params[0] == long.class && chain.getArgs().size() > 1) {
              Object arg = chain.getArg(1);
              if (arg instanceof String) chatId = (String) arg;
            }

            boolean isManualRead = chatId != null && pendingManualReads.contains(chatId);
            boolean skip =
                chatId != null && !isManualRead && shouldBlockReadReceipt(config, chatId);

            Object result = skip ? null : chain.proceed();
            if (skip) RuntimeReporter.working("read_block", "既読送信の抑制をRuntime確認");
            if (chatId != null) pendingManualReads.remove(chatId);
            return result;
          });
    } catch (Throwable ignored) {
    }
  }

  private void hookExecuteReadReceiptAsync(
      Class<?> managerCls, LineVersion.Config cfg, VectorConfig config) {
    try {
      Vector.hookAll(
          managerCls,
          cfg.readReceipt.methodExecuteReadReceiptAsync,
          chain -> {
            if (isPreventActive(config)) {
              Class<?>[] params = ((Method) chain.getExecutable()).getParameterTypes();
              if (params.length == 1
                  && params[0] == String.class
                  && shouldRememberManualRead(cfg)) {
                pendingManualReads.add((String) chain.getArg(0));
              }
            }
            return chain.proceed();
          });
    } catch (Throwable ignored) {
    }
  }

  private boolean shouldRememberManualRead(LineVersion.Config cfg) {
    String manualClass = cfg.readReceipt.longPressReadClass;
    boolean manual = manualClass != null && !manualClass.isEmpty() && isFromClass(manualClass);
    return manual || SettingsStore.get("send_mark_state", false);
  }

  private void hookReadAll(Class<?> managerCls, LineVersion.Config cfg, VectorConfig config) {
    try {
      Vector.hookAll(
          managerCls,
          cfg.readReceipt.methodReadAll,
          chain -> {
            boolean isNoArg = ((Method) chain.getExecutable()).getParameterCount() == 0;
            if (isPreventActive(config) && isNoArg) isBulkReading = true;
            try {
              return chain.proceed();
            } finally {
              if (isNoArg) isBulkReading = false;
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private boolean shouldBlockReadReceipt(VectorConfig config, String chatId) {
    if (isBulkReading) return false;
    if (isPreventActive(config)) return true;
    if (SettingsStore.get("per_chat_read_block", false)
        && ReadBlockStore.isPersistentlyBlocked(chatId)) return true;
    return SettingsStore.get("temporary_read_block", false)
        && ReadBlockStore.shouldBlockOneShot(chatId);
  }

  private boolean isPreventActive(VectorConfig config) {
    return SettingsStore.get("prevent_mark_as_read", false)
        && SettingsStore.get("prevent_read_state", true);
  }

  private boolean isFromClass(String prefix) {
    for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
      String n = el.getClassName();
      if (n.equals(prefix) || n.startsWith(prefix + "$") || n.startsWith(prefix + ".")) return true;
    }
    return false;
  }

  private boolean isLocalReadContext() {
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    for (StackTraceElement element : stack) {
      String name = element.getClassName();
      if (name.contains("ChatHistoryActivity")
          || name.contains("MessageList")
          || name.contains("ChatList")) return true;
    }
    return false;
  }

  private Class<?> getManagerClass(LineVersion.Config cfg, ClassLoader cl) {
    if (cfg.readReceipt.readReceiptManagerClass.isEmpty()) return null;
    try {
      return Reflect.findClass(cfg.readReceipt.readReceiptManagerClass, cl);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private void recordReadEvent(
      String chatId, String readerMid, String lastMsgIdStr, long readTime) {
    if (chatId == null || readerMid == null || lastMsgIdStr == null) return;

    long lastMsgId = parseLong(lastMsgIdStr, -1L);
    if (lastMsgId < 0) return;

    String myMid = LineDBUtils.getMyMid();
    if (myMid == null || readerMid.equals(myMid)) return;

    try {
      JSONObject history = SettingsStore.loadReadHistory();
      JSONObject chat = ensureChat(history, chatId);

      long prevHwm = getReaderHwm(chat, readerMid);
      if (lastMsgId <= prevHwm) return;

      String readerName = LineDBUtils.resolveMemberName(readerMid);
      String timeStr =
          new SimpleDateFormat(TIME_FMT, Locale.getDefault()).format(new Date(readTime));

      long lowerBound = prevHwm > 0 ? prevHwm : lastMsgId - 1;
      List<LineDBUtils.MessageRecord> records =
          LineDBUtils.fetchMyMessagesUpTo(chatId, lowerBound, lastMsgId, myMid);

      ensureMessageEntries(chat, records);
      markReaderOnMessagesUpTo(chat, readerMid, readerName, timeStr, lastMsgId);
      setReaderHwm(chat, readerMid, lastMsgId);
      pruneReadHistory(history);
      SettingsStore.saveReadHistory(history);
      RuntimeReporter.working("read_block", "既読ユーザーID・時刻の記録をRuntime確認");
    } catch (Throwable ignored) {
    }
  }

  private void pruneReadHistory(JSONObject history) {
    JSONObject chats = history.optJSONObject("c");
    if (chats == null) return;
    ArrayList<String> chatIds = jsonKeys(chats);
    for (String chatId : chatIds) {
      JSONObject chat = chats.optJSONObject(chatId);
      JSONObject messages = chat == null ? null : chat.optJSONObject("m");
      if (messages == null || messages.length() <= MAX_MESSAGES_PER_CHAT) continue;
      ArrayList<String> ids = jsonKeys(messages);
      ids.sort(Comparator.comparingLong(id -> parseLong(id, Long.MIN_VALUE)));
      for (int i = 0; i < ids.size() - MAX_MESSAGES_PER_CHAT; i++) messages.remove(ids.get(i));
    }
    if (chatIds.size() <= MAX_HISTORY_CHATS) return;
    chatIds.sort(
        Comparator.comparingLong(
            id -> {
              JSONObject chat = chats.optJSONObject(id);
              JSONObject messages = chat == null ? null : chat.optJSONObject("m");
              long newest = Long.MIN_VALUE;
              if (messages != null) {
                Iterator<String> keys = messages.keys();
                while (keys.hasNext()) newest = Math.max(newest, parseLong(keys.next(), newest));
              }
              return newest;
            }));
    for (int i = 0; i < chatIds.size() - MAX_HISTORY_CHATS; i++) chats.remove(chatIds.get(i));
  }

  private static ArrayList<String> jsonKeys(JSONObject object) {
    ArrayList<String> keys = new ArrayList<>();
    Iterator<String> iterator = object.keys();
    while (iterator.hasNext()) keys.add(iterator.next());
    return keys;
  }

  private JSONObject ensureChat(JSONObject history, String chatId) throws Exception {
    JSONObject chats = history.optJSONObject("c");
    if (chats == null) history.put("c", chats = new JSONObject());
    JSONObject chat = chats.optJSONObject(chatId);
    if (chat == null) chats.put(chatId, chat = new JSONObject());
    return chat;
  }

  private long getReaderHwm(JSONObject chat, String readerMid) {
    JSONObject hwm = chat.optJSONObject("rh");
    return hwm == null ? 0L : parseLong(hwm.optString(readerMid, ""), 0L);
  }

  private void setReaderHwm(JSONObject chat, String readerMid, long msgId) throws Exception {
    JSONObject hwm = chat.optJSONObject("rh");
    if (hwm == null) chat.put("rh", hwm = new JSONObject());
    hwm.put(readerMid, String.valueOf(msgId));
  }

  private void ensureMessageEntries(JSONObject chat, List<LineDBUtils.MessageRecord> records)
      throws Exception {
    if (records.isEmpty()) return;
    JSONObject messages = chat.optJSONObject("m");
    if (messages == null) chat.put("m", messages = new JSONObject());

    if (!chat.has("n")) {
      String chatName = records.get(0).chatName;
      if (chatName != null) chat.put("n", chatName);
    }

    for (LineDBUtils.MessageRecord record : records) {
      if (messages.has(record.id)) continue;
      JSONObject msg = new JSONObject();
      msg.put("c", record.text);
      msg.put("sn", record.senderName);
      msg.put("ct", record.timestamp);
      msg.put("r", new JSONObject());
      messages.put(record.id, msg);
    }
  }

  private void markReaderOnMessagesUpTo(
      JSONObject chat, String readerMid, String readerName, String timeStr, long lastMsgId)
      throws Exception {
    JSONObject messages = chat.optJSONObject("m");
    if (messages == null) return;

    Iterator<String> it = messages.keys();
    while (it.hasNext()) {
      String key = it.next();
      if (parseLong(key, Long.MAX_VALUE) > lastMsgId) continue;
      JSONObject msg = messages.optJSONObject(key);
      if (msg != null) markReader(msg, readerMid, readerName, timeStr);
    }
  }

  private void markReader(JSONObject msg, String readerMid, String readerName, String timeStr)
      throws Exception {
    JSONObject readers = msg.optJSONObject("r");
    if (readers == null) {
      readers = new JSONObject();
      msg.put("r", readers);
    }
    if (readers.has(readerMid)) return;
    JSONObject info = new JSONObject();
    info.put("n", readerName != null ? readerName : UNKNOWN_READER_NAME);
    info.put("t", timeStr);
    readers.put(readerMid, info);
  }

  private static long parseLong(String s, long fallback) {
    if (s == null || s.isEmpty()) return fallback;
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
