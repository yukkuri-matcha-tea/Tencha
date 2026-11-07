package dev.vector.lineextension.hooks;

import android.view.View;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.Reflect;

final class MessageItemView {
  private MessageItemView() {}

  static View get(Object holder, LineVersion.Config config) {
    return (View)
        (config.unsend.itemViewField.isEmpty()
            ? Reflect.callMethod(holder, config.unsend.methodGetItemView)
            : Reflect.getObjectField(holder, config.unsend.itemViewField));
  }
}
