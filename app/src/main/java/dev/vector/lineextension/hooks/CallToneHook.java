package dev.vector.lineextension.hooks;

import android.content.Context;
import android.net.Uri;
import dev.vector.lineextension.LoadParam;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import dev.vector.lineextension.VectorConfig;
import java.io.File;

/** Replaces only verified LINE tone sources; LINE retains focus, looping and stop handling. */
public final class CallToneHook implements BaseHook {
  @Override
  public void hook(VectorConfig config, LoadParam param) {
    if (!NativeCallBridge.uses2615CallApi()) return;
    ClassLoader loader = param.classLoader;
    for (String source : new String[] {"ps7.a$a$a", "ps7.a$a$b"}) {
      try {
        Vector.module
            .hook(Reflect.findMethodExact(source, loader, "d", Context.class))
            .intercept(
                chain -> {
                  if (Main.options.customCallRingtone.enabled) {
                    try {
                      File file =
                          CallToneStore.selectedFile(
                              (Context) chain.getArg(0), CallToneStore.Kind.RINGTONE);
                      if (file != null)
                        return Reflect.newInstance(
                            Reflect.findClass("a40.b", loader), Uri.fromFile(file));
                    } catch (Throwable error) {
                      Vector.log("Tencha: custom ringtone unavailable; using LINE tone", error);
                    }
                  }
                  return chain.proceed();
                });
      } catch (Throwable error) {
        Vector.log("Tencha: ringtone source hook unavailable", error);
      }
    }
    for (String source : new String[] {"ps7.a$b$a", "ps7.a$b$b"}) {
      try {
        // ps7.a$b$b is OBSRemoteData (peer's tone). ps7.a$b$a is LINE's default ringback.
        Object standard = Reflect.getStaticObjectField(Reflect.findClass("ps7.a$b$a", loader), "a");
        Vector.module
            .hook(Reflect.findMethodExact(source, loader, "b", Context.class))
            .intercept(
                chain -> {
                  try {
                    File file =
                        Main.options.customCallRingback.enabled
                            ? CallToneStore.selectedFile(
                                (Context) chain.getArg(0), CallToneStore.Kind.RINGBACK)
                            : null;
                    CallTonePolicy.Source selected =
                        CallTonePolicy.ringback(
                            Main.options.customCallRingback.enabled,
                            file != null,
                            Main.options.disablePeerRingback.enabled,
                            source.equals("ps7.a$b$b"));
                    if (selected == CallTonePolicy.Source.CUSTOM)
                      return Reflect.newInstance(
                          Reflect.findClass("a40.b", loader), Uri.fromFile(file));
                    if (selected == CallTonePolicy.Source.DEFAULT)
                      return Reflect.callMethod(standard, "b", chain.getArg(0));
                  } catch (Throwable error) {
                    Vector.log("Tencha: default ringback unavailable; using LINE tone", error);
                  }
                  return chain.proceed();
                });
      } catch (Throwable error) {
        Vector.log("Tencha: peer ringback hook unavailable", error);
      }
    }
  }
}
