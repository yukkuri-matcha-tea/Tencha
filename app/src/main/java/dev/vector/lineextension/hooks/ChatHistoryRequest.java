package dev.vector.lineextension.hooks;

import android.app.Activity;
import android.content.Intent;
import android.os.Parcelable;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.Reflect;

final class ChatHistoryRequest {
  static final String EXTRA = "chatHistoryRequest";
  private static final String[] LEGACY_CHAT_ID_KEYS = {"chatId", "chat_id", "extra-chat-id"};

  private ChatHistoryRequest() {}

  static String resolveChatId(Activity activity, LineVersion.Config config) {
    Intent intent = activity.getIntent();
    if (intent != null) {
      try {
        Parcelable request = intent.getParcelableExtra(EXTRA);
        if (request != null) {
          Object id = Reflect.callMethod(request, "getChatId");
          if (id instanceof String && !((String) id).isEmpty()) return (String) id;
        }
        for (String key : LEGACY_CHAT_ID_KEYS) {
          String id = intent.getStringExtra(key);
          if (id != null && !id.isEmpty()) return id;
        }
      } catch (RuntimeException | LinkageError ignored) {
      }
    }
    if (config != null) {
      try {
        Object model = Reflect.getObjectField(activity, config.chat.chatIdField);
        if (model != null) {
          return (String) Reflect.callMethod(model, config.chat.methodGetChatId);
        }
      } catch (RuntimeException | LinkageError ignored) {
      }
    }
    return null;
  }
}
