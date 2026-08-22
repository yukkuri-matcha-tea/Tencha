package dev.vector.lineextension.core;

import dev.vector.lineextension.SettingsStore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONObject;

/** Persistent and one-shot per-chat read-receipt state. */
public final class ReadBlockStore {
  public static final String BLOCKED_CHATS_KEY = "read_blocked_chats_json";
  private static final String ONE_SHOT_PENDING_KEY = "temporary_read_block_pending";
  private static final String ONE_SHOT_CHAT_KEY = "temporary_read_block_chat_id";
  private static final String ONE_SHOT_EXPIRES_KEY = "temporary_read_block_expires_at";
  private static final long ONE_SHOT_TTL_MS = 5 * 60 * 1000L;
  private static final Object LOCK = new Object();

  private ReadBlockStore() {}

  public static final class Entry {
    public final String chatId;
    public final String name;
    public final long addedAt;

    Entry(String chatId, String name, long addedAt) {
      this.chatId = chatId;
      this.name = name;
      this.addedAt = addedAt;
    }
  }

  public static boolean isPersistentlyBlocked(String chatId) {
    if (chatId == null || chatId.isEmpty()) return false;
    synchronized (LOCK) {
      return readBlocked().has(chatId);
    }
  }

  public static boolean togglePersistent(String chatId, String displayName) {
    if (chatId == null || chatId.isEmpty()) return false;
    synchronized (LOCK) {
      JSONObject root = readBlocked();
      boolean enabled = !root.has(chatId);
      if (enabled) {
        JSONObject entry = new JSONObject();
        try {
          entry.put("name", displayName == null || displayName.isEmpty() ? chatId : displayName);
          entry.put("addedAt", System.currentTimeMillis());
          root.put(chatId, entry);
        } catch (Throwable ignored) {
          return false;
        }
      } else {
        root.remove(chatId);
      }
      SettingsStore.save(BLOCKED_CHATS_KEY, root.toString());
      return enabled;
    }
  }

  public static void remove(String chatId) {
    if (chatId == null || chatId.isEmpty()) return;
    synchronized (LOCK) {
      JSONObject root = readBlocked();
      root.remove(chatId);
      SettingsStore.save(BLOCKED_CHATS_KEY, root.toString());
    }
  }

  public static List<Entry> list() {
    synchronized (LOCK) {
      JSONObject root = readBlocked();
      List<Entry> out = new ArrayList<>();
      Iterator<String> keys = root.keys();
      while (keys.hasNext()) {
        String chatId = keys.next();
        JSONObject value = root.optJSONObject(chatId);
        out.add(
            new Entry(
                chatId,
                value == null ? chatId : value.optString("name", chatId),
                value == null ? 0L : value.optLong("addedAt", 0L)));
      }
      return out;
    }
  }

  /** Arms a five-minute session that binds to the first chat whose read receipt is attempted. */
  public static void armOneShot() {
    synchronized (LOCK) {
      SettingsStore.save(ONE_SHOT_PENDING_KEY, true);
      SettingsStore.save(ONE_SHOT_CHAT_KEY, "");
      SettingsStore.save(
          ONE_SHOT_EXPIRES_KEY, String.valueOf(System.currentTimeMillis() + ONE_SHOT_TTL_MS));
    }
  }

  public static boolean isOneShotArmed() {
    synchronized (LOCK) {
      return oneShotAppliesLocked(null, false);
    }
  }

  public static boolean shouldBlockOneShot(String chatId) {
    if (chatId == null || chatId.isEmpty()) return false;
    synchronized (LOCK) {
      return oneShotAppliesLocked(chatId, true);
    }
  }

  private static boolean oneShotAppliesLocked(String chatId, boolean bind) {
    if (!SettingsStore.get(ONE_SHOT_PENDING_KEY, false)) return false;
    long expires = parseLong(SettingsStore.getString(ONE_SHOT_EXPIRES_KEY, "0"));
    if (expires <= System.currentTimeMillis()) {
      clearOneShotLocked();
      return false;
    }
    String bound = SettingsStore.getString(ONE_SHOT_CHAT_KEY, "");
    if (chatId == null) return true;
    if (bound.isEmpty() && bind) {
      SettingsStore.save(ONE_SHOT_CHAT_KEY, chatId);
      bound = chatId;
    }
    return chatId.equals(bound);
  }

  private static void clearOneShotLocked() {
    SettingsStore.save(ONE_SHOT_PENDING_KEY, false);
    SettingsStore.save(ONE_SHOT_CHAT_KEY, "");
    SettingsStore.save(ONE_SHOT_EXPIRES_KEY, "0");
  }

  private static JSONObject readBlocked() {
    try {
      return new JSONObject(SettingsStore.getString(BLOCKED_CHATS_KEY, "{}"));
    } catch (Throwable ignored) {
      return new JSONObject();
    }
  }

  private static long parseLong(String value) {
    try {
      return Long.parseLong(value);
    } catch (Throwable ignored) {
      return 0L;
    }
  }
}
