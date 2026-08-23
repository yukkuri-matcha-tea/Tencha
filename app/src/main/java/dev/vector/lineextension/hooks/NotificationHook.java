package dev.vector.lineextension.hooks;

import android.app.Notification;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;

public class NotificationHook implements BaseHook {

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    Vector.module
        .hook(
            Reflect.findMethodExact(
                Notification.Builder.class, "addAction", Notification.Action.class))
        .intercept(
            chain -> {
              if (!config.removeNotificationMuteButton.enabled) return chain.proceed();

              Notification.Action action = (Notification.Action) chain.getArg(0);
              if (action == null || action.title == null) return chain.proceed();

              android.app.Application app = Vector.currentApplication();
              if (app == null) return chain.proceed();

              int resId =
                  app.getResources()
                      .getIdentifier("notification_button_mute", "string", app.getPackageName());
              if (resId == 0) return chain.proceed();

              String muteLabel = app.getString(resId);
              if (muteLabel.equals(action.title.toString())) {
                return chain.getThisObject();
              }
              return chain.proceed();
            });

    Vector.module
        .hook(
            Reflect.findMethodExact(
                Notification.Builder.class,
                "addAction",
                int.class,
                CharSequence.class,
                android.app.PendingIntent.class))
        .intercept(
            chain -> {
              if (!config.removeNotificationMuteButton.enabled) return chain.proceed();

              CharSequence titleCs = (CharSequence) chain.getArg(1);
              if (titleCs == null) return chain.proceed();

              android.app.Application app = Vector.currentApplication();
              if (app == null) return chain.proceed();

              int resId =
                  app.getResources()
                      .getIdentifier("notification_button_mute", "string", app.getPackageName());
              if (resId == 0) return chain.proceed();

              String muteLabel = app.getString(resId);
              if (muteLabel.equals(titleCs.toString())) {
                return chain.getThisObject();
              }
              return chain.proceed();
            });
  }
}
