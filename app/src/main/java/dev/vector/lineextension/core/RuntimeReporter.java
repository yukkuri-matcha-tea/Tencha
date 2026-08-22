package dev.vector.lineextension.core;

import android.app.Application;
import dev.vector.lineextension.Vector;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Reports the first real execution of a feature without writing on every UI/frame callback. */
public final class RuntimeReporter {
  private static final Set<String> reportedWorking = ConcurrentHashMap.newKeySet();

  private RuntimeReporter() {}

  public static void working(String featureId, String detail) {
    if (!reportedWorking.add(featureId)) return;
    Application application = Vector.currentApplication();
    if (application != null) {
      ControlClient.report(application, featureId, FeatureStatus.WORKING, detail);
    }
  }

  public static void partial(String featureId, String detail) {
    Application application = Vector.currentApplication();
    if (application != null) {
      ControlClient.report(application, featureId, FeatureStatus.PARTIAL, detail);
    }
  }
}
