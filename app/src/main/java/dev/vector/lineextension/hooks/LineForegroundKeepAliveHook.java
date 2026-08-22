package dev.vector.lineextension.hooks;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;

public class LineForegroundKeepAliveHook implements BaseHook {

  private static final String KEEP_ALIVE_ACTION = "dev.vector.lineextension.action.LINE_KEEP_ALIVE";
  private static final String KEEP_ALIVE_CHANNEL_ID = "vector_line_keep_alive";
  private static final int KEEP_ALIVE_NOTIFICATION_ID = 0x4b4e5446;
  private static final String NOTIFICATION_TEXT = "この常設通知は通知設定から非表示にできます。";

  private static final int MAX_RESTART_ATTEMPTS = 5;
  private static final long RESTART_BASE_DELAY_MS = 5000L;
  private static final long RESTART_MAX_DELAY_MS = 300_000L; // 5min

  private static volatile Context appContext;
  private static volatile boolean keepAliveRequested;
  private static volatile boolean keepAliveActive;
  private static volatile int restartAttempts;

  private static boolean isEnabled(VectorConfig config) {
    return config.lineForegroundKeepAlive.enabled;
  }

  private static void log(String message) {
    Vector.log("Tencha: " + message);
  }

  private static Notification buildNotification(Context context) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
      NotificationChannel channel =
          new NotificationChannel(
              KEEP_ALIVE_CHANNEL_ID, "LINE background", NotificationManager.IMPORTANCE_LOW);
      channel.setDescription(NOTIFICATION_TEXT);
      channel.setShowBadge(false);
      notificationManager.createNotificationChannel(channel);
    }

    int smallIcon = context.getApplicationInfo().icon;
    if (smallIcon == 0) {
      smallIcon = android.R.drawable.stat_notify_sync;
    }

    Notification.Builder builder =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(context, KEEP_ALIVE_CHANNEL_ID)
            : new Notification.Builder(context);
    builder
        .setSmallIcon(smallIcon)
        .setContentTitle("LINE")
        .setContentText(NOTIFICATION_TEXT)
        .setStyle(new Notification.BigTextStyle().bigText(NOTIFICATION_TEXT))
        .setOngoing(true)
        .setShowWhen(false)
        .setLocalOnly(true)
        .setCategory(Notification.CATEGORY_SERVICE)
        .addAction(android.R.drawable.ic_menu_manage, "通知設定", buildSettingsPendingIntent(context));

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      builder.setPriority(Notification.PRIORITY_LOW);
    }

    return builder.build();
  }

  private static PendingIntent buildSettingsPendingIntent(Context context) {
    Intent intent;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
      intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
      intent.putExtra(Settings.EXTRA_CHANNEL_ID, KEEP_ALIVE_CHANNEL_ID);
    } else {
      intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      intent.setData(Uri.parse("package:" + context.getPackageName()));
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    return PendingIntent.getActivity(context, 0, intent, flags);
  }

  private static int getForegroundServiceType() {
    return android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
  }

  private static void startKeepAlive(Service service) {
    Notification notification = buildNotification(service);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification, getForegroundServiceType());
      return;
    }
    service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification);
  }

  private static void requestKeepAlive(
      Context context, LineVersion.Config.ForegroundKeepAlive keepAliveCfg) {
    if (context == null || keepAliveCfg == null || keepAliveCfg.serviceClass.isEmpty()) {
      return;
    }

    if (keepAliveActive) return;

    Context targetContext = context.getApplicationContext();
    if (targetContext == null) targetContext = context;

    try {
      Intent intent = new Intent(KEEP_ALIVE_ACTION);
      intent.setClassName(targetContext.getPackageName(), keepAliveCfg.serviceClass);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        targetContext.startForegroundService(intent);
      } else {
        targetContext.startService(intent);
      }
      Vector.log("requested LINE foreground keep-alive service");
    } catch (Throwable t) {
      Vector.log("Tencha: failed to start LINE foreground keep-alive service: " + t);
    }
  }

  private static void scheduleKeepAlive(
      Context context, LineVersion.Config.ForegroundKeepAlive keepAliveCfg) {
    if (keepAliveRequested
        || context == null
        || keepAliveCfg == null
        || keepAliveCfg.serviceClass.isEmpty()) {
      return;
    }

    keepAliveRequested = true;
    new Handler(Looper.getMainLooper())
        .postDelayed(() -> requestKeepAlive(context, keepAliveCfg), 3000L);
  }

  private static void scheduleRestart(
      Context context, LineVersion.Config.ForegroundKeepAlive keepAliveCfg) {
    int attempts = restartAttempts;
    if (attempts >= MAX_RESTART_ATTEMPTS) {
      Vector.log(
          "Tencha: LINE foreground keep-alive: max restart attempts ("
              + MAX_RESTART_ATTEMPTS
              + ") reached; relying on START_STICKY only");
      return;
    }

    restartAttempts = attempts + 1;
    long delay = Math.min(RESTART_BASE_DELAY_MS << attempts, RESTART_MAX_DELAY_MS);
    Vector.log(
        "Tencha: LINE foreground keep-alive service destroyed; scheduling restart in "
            + delay
            + "ms (attempt "
            + (attempts + 1)
            + "/"
            + MAX_RESTART_ATTEMPTS
            + ")");
    new Handler(Looper.getMainLooper())
        .postDelayed(() -> requestKeepAlive(context, keepAliveCfg), delay);
  }

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    if (!isEnabled(config)) {
      return;
    }

    LineVersion.Config versionConfig = LineVersion.get();
    if (versionConfig == null || versionConfig.foregroundKeepAlive == null) {
      return;
    }

    final LineVersion.Config.ForegroundKeepAlive keepAliveCfg = versionConfig.foregroundKeepAlive;
    if (keepAliveCfg.serviceClass.isEmpty()) {
      return;
    }

    Vector.module
        .hook(Reflect.findMethodExact(Application.class, "onCreate"))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              if (!isEnabled(config)) return result;
              if (!(chain.getThisObject() instanceof Application)) return result;
              Application application = (Application) chain.getThisObject();
              appContext = application.getApplicationContext();

              if (lpparam.processName != null && !lpparam.packageName.equals(lpparam.processName)) {
                return result;
              }

              scheduleKeepAlive(application, keepAliveCfg);
              return result;
            });

    Vector.module
        .hook(
            Reflect.findMethodExact(
                keepAliveCfg.serviceClass,
                lpparam.classLoader,
                "onStartCommand",
                Intent.class,
                int.class,
                int.class))
        .intercept(
            chain -> {
              if (!isEnabled(config)) return chain.proceed();
              Intent intent = (Intent) chain.getArg(0);
              if (intent == null || !KEEP_ALIVE_ACTION.equals(intent.getAction())) {
                return chain.proceed();
              }

              try {
                startKeepAlive((Service) chain.getThisObject());
                keepAliveActive = true;
                restartAttempts = 0;
                Vector.log("Tencha: LINE foreground keep-alive service is active");
                return Service.START_STICKY;
              } catch (Throwable t) {
                Vector.log("Tencha: failed to activate LINE foreground keep-alive service: " + t);
                return Service.START_NOT_STICKY;
              }
            });

    Vector.module
        .hook(Reflect.findMethodExact(keepAliveCfg.serviceClass, lpparam.classLoader, "onDestroy"))
        .intercept(
            chain -> {
              Object result = chain.proceed();
              if (!isEnabled(config)) return result;
              if (appContext == null) return result;
              keepAliveActive = false;
              scheduleRestart(appContext, keepAliveCfg);
              return result;
            });

    Vector.log("LineForegroundKeepAliveHook installed");
  }
}
