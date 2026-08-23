package dev.vector.lineextension.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Parcelable;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.Vector;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;

public class ChatJumpUtil {

  public static boolean jumpToMessage(Activity activity, String chatId, String serverMsgId) {
    if (activity == null || chatId == null || serverMsgId == null) return false;

    LineVersion.Config config = LineVersion.get();
    if (config == null
        || config.chatJump.requestClass.isEmpty()
        || config.chatJump.launchActivityClass.isEmpty()
        || config.chatJump.requestExtraKey.isEmpty()) {
      return false;
    }

    long localId = LineDBUtils.resolveLocalMessageId(serverMsgId);
    if (localId < 0) return false;

    try {
      ClassLoader cl = activity.getClassLoader();
      Class<?> requestCls = cl.loadClass(config.chatJump.requestClass);
      Class<?> launchCls = cl.loadClass(config.chatJump.launchActivityClass);

      Constructor<?> requestCtor = null;
      for (Constructor<?> c : requestCls.getDeclaredConstructors()) {
        Class<?>[] p = c.getParameterTypes();
        if (p.length == 3
            && p[0] == String.class
            && (p[1] == boolean.class || p[1] == Boolean.class)) {
          requestCtor = c;
          break;
        }
      }
      if (requestCtor == null) return false;

      Class<?> highlightCls = requestCtor.getParameterTypes()[2];
      Constructor<?> highlightCtor =
          highlightCls.getDeclaredConstructor(List.class, Long.class, List.class);
      highlightCtor.setAccessible(true);
      Object highlight =
          highlightCtor.newInstance(
              Collections.singletonList(""),
              Long.valueOf(localId),
              Collections.singletonList(Long.valueOf(localId)));

      requestCtor.setAccessible(true);
      boolean isGroup = !chatId.startsWith("u");
      Object request = requestCtor.newInstance(chatId, isGroup, highlight);

      Intent intent = new Intent(activity, launchCls);
      intent.putExtra(config.chatJump.requestExtraKey, (Parcelable) request);
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
      activity.startActivity(intent);
      activity.overridePendingTransition(0, 0);
      return true;
    } catch (Throwable t) {
      Vector.log("Tencha: ChatJumpUtil error: " + t);
      return false;
    }
  }
}
