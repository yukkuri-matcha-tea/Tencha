package dev.vector.lineextension;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;
import androidx.annotation.NonNull;
import dev.vector.lineextension.core.ControlClient;
import dev.vector.lineextension.core.FeatureSupervisor;
import dev.vector.lineextension.hooks.*;
import dev.vector.lineextension.utils.ModuleStrings;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

public class Main extends XposedModule {

  public static final String TAG = "Tencha";
  public static final VectorConfig options = new VectorConfig();
  private static final String LINE_PKG = "jp.naver.line.android";
  private static volatile FeatureSupervisor featureSupervisor;

  @Override
  public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
    Vector.module = this;
    Vector.processName = param.getProcessName();
    log(Log.INFO, TAG, "Tencha loaded in " + param.getProcessName());
  }

  @Override
  public void onPackageReady(@NonNull PackageReadyParam param) {
    if (!LINE_PKG.equals(param.getPackageName())) return;
    Vector.module = this;
    bootstrap(param.getClassLoader(), param.getPackageName());
  }

  private void bootstrap(ClassLoader classLoader, String packageName) {
    LoadParam lpparam = new LoadParam(classLoader, packageName, Vector.processName);
    try {
      Method attachBaseContext =
          ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
      hook(attachBaseContext)
          .intercept(
              chain -> {
                Object result = chain.proceed();
                Context context = (Context) chain.getArg(0);
                if (context == null) return result;

                LineVersion.Config cfg = LineVersion.detectWithContext(context);
                if (cfg == null) {
                  cfg = LineVersion.detect(lpparam.classLoader);
                }
                if (cfg == null) {
                  handleUnsupportedVersion(lpparam, context);
                } else {
                  initializeModule(context, lpparam);
                }
                return result;
              });
    } catch (Throwable t) {
      Vector.log("Tencha: bootstrap failed: " + t);
    }
  }

  private void initializeModule(Context context, LoadParam lpparam) {
    synchronized (Main.class) {
      if (SettingsStore.isLoaded()) return;

      SettingsStore.setContext(context);
      featureSupervisor = new FeatureSupervisor(context);
      SettingsStore.load(options);
      ControlClient.syncSettings(context, options);
      boolean primaryProcess =
          lpparam.processName == null || lpparam.packageName.equals(lpparam.processName);
      if (!primaryProcess) {
        SettingsStore.setLoaded(true);
        Vector.log("Tencha: non-primary LINE process skipped: " + lpparam.processName);
        return;
      }
      ControlClient.reportSession(
          context, LineVersion.getDetectedVersionName(), lpparam.processName);
      if (ControlClient.consumeNextLaunchOff(context)) {
        SettingsStore.setLoaded(true);
        Vector.log("Tencha: all extensions disabled for this LINE launch");
        return;
      }
      SettingsStore.setLoaded(true);
      boolean developerMode = options.developerMode.enabled;

      Vector.log("Tencha: initializing isolated feature hooks...");

      applyHook(new SettingsUIInjector(), lpparam);
      applyHook(new SettingsButtonLongPress(), lpparam);
      applyHook(new SafeResourceFix(), lpparam);

      if (options.recordReadHistory.enabled
          || options.preventMarkAsRead.enabled
          || options.temporaryReadBlock.enabled
          || options.perChatReadBlock.enabled) {
        applyHook(new ReadReceiptHandler(), lpparam);
        applyHook(new PlusMenuHook(), lpparam);
        applyHook(new ChatListMoreMenuHook(), lpparam);
      }
      if (options.recordReadHistory.enabled || options.perChatReadBlock.enabled) {
        applyHook(new HeaderButtonInjector(), lpparam);
      }
      if (options.preventUnsendMessage.enabled) {
        applyHook(new UnsendProtector(), lpparam);
      }
      if (options.hideAiIconPermanently.enabled) {
        applyHook(new RemoveTalkRoomAgentIToggle(), lpparam);
        applyHook(new HideAiIconPermanently(), lpparam);
      }
      if (options.openUrlInDefaultBrowser.enabled) {
        applyHook(new OpenInExternalBrowserHook(), lpparam);
      }
      if (options.highQualityPhoto.enabled) {
        applyHook(new ImageQuality(), lpparam);
      }
      if (options.longVideo.enabled) {
        applyHook(new LongVideoHook(), lpparam);
      }
      if (options.searchByMember.enabled) {
        applyHook(new SearchByMemberHook(), lpparam);
      }
      if (options.searchMin1Char.enabled) {
        applyHook(new SearchMin1CharHook(), lpparam);
        applyHook(new SearchResultCountHook(), lpparam);
      }
      if (options.fixAnnouncementName.enabled) {
        applyHook(new AnnouncementNameFix(), lpparam);
      }
      if (options.showSecondsInChatTime.enabled) {
        applyHook(new ChatTimestampSeconds(), lpparam);
      }
      if (options.selectAllInEditMode.enabled) {
        applyHook(new ChatEditSelectAllHook(), lpparam);
      }
      if (options.showEditHistory.enabled) {
        applyHook(new EditHistoryHook(), lpparam);
      }
      if (options.useDefaultCamera.enabled) {
        applyHook(new UseDefaultCameraHook(), lpparam);
      }
      if (options.muteCameraShutter.enabled) {
        applyHook(new CameraShutterMuteHook(), lpparam);
      }
      if (developerMode && options.showProfileTimestamps.enabled) {
        applyHook(new ProfileTimestampsHook(), lpparam);
      }

      if (options.removeAds.enabled) {
        applyHook(new RemoveAds(), lpparam);
      }
      if (options.removeHomeRecommendations.enabled
          || options.removeHomeServices.enabled
          || options.removeHomeAccordion.enabled) {
        applyHook(new RemoveHomeContents(), lpparam);
      }
      if (options.removeTabVoom.enabled
          || options.removeTabNews.enabled
          || options.removeTabMini.enabled
          || options.removeTabCommerce.enabled
          || options.removeTabWallet.enabled
          || options.hideTabText.enabled
          || options.extendTabClickArea.enabled) {
        applyHook(new RemoveTabs(), lpparam);
      }
      if (options.removeAiFriendsButton.enabled
          || options.removeOpenChatButton.enabled
          || options.removeAlbumButton.enabled
          || options.removeCalendarButton.enabled
          || options.removeSearchBarAgentIButton.enabled) {
        applyHook(new RemoveHeaderButtons(), lpparam);
      }
      if (options.homeTabType.value != null && !options.homeTabType.value.isEmpty()) {
        applyHook(new HomeTabTypeHook(), lpparam);
      }
      if (options.useCustomFont.enabled) {
        applyHook(new FontUnlockHook(), lpparam);
      }
      if (options.useAmoledTheme.enabled) {
        applyHook(new AmoledThemeHook(), lpparam);
      }
      if (options.forceDarkModeUi.enabled) {
        applyHook(new ForceDarkModeUiHook(), lpparam);
      }
      if (developerMode && options.showThemeOnSubDevice.enabled) {
        applyHook(new ShowThemeOnSubDeviceHook(), lpparam);
      }

      if (options.reactionNotification.enabled) {
        applyHook(new ReactionNotification(), lpparam);
      }
      if (options.removeNotificationMuteButton.enabled) {
        applyHook(new NotificationHook(), lpparam);
      }
      if (options.stackMessageNotifications.enabled) {
        applyHook(new StackMessageNotificationsHook(), lpparam);
      }
      if (developerMode && options.lineForegroundKeepAlive.enabled) {
        applyHook(new LineForegroundKeepAliveHook(), lpparam);
      }
      if (developerMode && options.experimentalFcmFix.enabled) {
        applyHook(new FcmFixHook(), lpparam);
      }
      if (developerMode
          && (options.spoofVersion.enabled || options.spoofVersionUnsendOnly.enabled)) {
        applyHook(new VersionSpoof(), lpparam);
      }
      if (developerMode && options.fixSignatureMismatch.enabled) {
        applyHook(new SignatureSpoofHook(), lpparam);
      }
    }
  }

  private void applyHook(BaseHook hook, LoadParam lpparam) {
    FeatureSupervisor supervisor = featureSupervisor;
    String id = supervisor == null ? hook.getClass().getSimpleName() : supervisor.idFor(hook);
    if (supervisor != null && supervisor.isSafeMode(id)) {
      supervisor.safeMode(id);
      Vector.log("Tencha: skipped Safe Mode feature " + id);
      return;
    }
    try {
      hook.hook(options, lpparam);
      if (supervisor != null) supervisor.installed(id);
    } catch (Throwable t) {
      if (supervisor != null) supervisor.failed(id, t);
      else Vector.log("Tencha: Hook failed for " + hook.getClass().getSimpleName() + ": " + t);
    }
  }

  private void handleUnsupportedVersion(LoadParam lpparam, Context context) {
    final String supported = LineVersion.getSupportedVersions();
    final String msg = ModuleStrings.UNSUPPORTED_VERSION_MSG + " (Supported: " + supported + ")";

    try {
      Method onCreate =
          Reflect.findMethodExact(
              "jp.naver.line.android.activity.main.MainActivity",
              lpparam.classLoader,
              "onCreate",
              android.os.Bundle.class);
      hook(onCreate)
          .intercept(
              chain -> {
                Object result = chain.proceed();
                android.app.Activity activity = (android.app.Activity) chain.getThisObject();
                int themeId = dev.vector.lineextension.utils.LineTheme.dialogTheme(activity);
                dev.vector.lineextension.utils.LineTheme.applyDialogColors(
                    new android.app.AlertDialog.Builder(activity, themeId)
                        .setTitle(ModuleStrings.UNSUPPORTED_VERSION_TITLE)
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show(),
                    activity);
                return result;
              });
    } catch (Throwable t) {
      Vector.log("Tencha: unsupported-version dialog hook failed: " + t);
    }
  }
}
