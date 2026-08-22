package dev.vector.lineextension.hooks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import dev.vector.lineextension.LineVersion;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import dev.vector.lineextension.core.RuntimeReporter;

public class OpenInExternalBrowserHook implements BaseHook {

  @Override
  public void hook(VectorConfig config, LoadParam lpparam) throws Throwable {
    if (config == null || !config.openUrlInDefaultBrowser.enabled) return;

    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null || cfg.iab.inAppBrowserActivityClass.isEmpty()) return;

    try {
      Vector.module
          .hook(
              Reflect.findMethodExact(
                  cfg.iab.inAppBrowserActivityClass, lpparam.classLoader, "onCreate", Bundle.class))
          .intercept(
              chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                Intent intent = activity.getIntent();
                if (intent == null) return result;

                Uri uri = intent.getData();
                if (uri == null) return result;

                String url = uri.toString();

                // URLs handled by IAB for functionality
                if (url.startsWith("https://account-center.lylink.yahoo.co.jp")
                    || url.startsWith("https://access.line.me")
                    || url.startsWith(
                        "https://id.lylink.yahoo.co.jp/federation/ly/normal/callback/first")) {
                  return result;
                }

                Intent externalIntent = new Intent(Intent.ACTION_VIEW, uri);
                externalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(externalIntent);
                RuntimeReporter.working("external_browser", "既定ブラウザへの引き渡しをRuntime確認");

                activity.finish();
                return result;
              });
    } catch (Throwable t) {
      Vector.log("Tencha: Failed to hook InAppBrowserActivity: " + t.getMessage());
    }
  }
}
