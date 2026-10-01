package dev.vector.lineextension.hooks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import dev.vector.lineextension.Main;
import dev.vector.lineextension.Reflect;
import dev.vector.lineextension.Vector;
import java.lang.reflect.Proxy;

/** Shows soundboard controls inside LINE's native party container. */
final class CallPartySoundboardHook {
  private static final String HOLDER =
      "com.linecorp.voip2.feature.watchtogether.view.menu.WtMenuContainerViewHolder";
  private static final String TAG = "tencha_party_soundboard";

  void install(ClassLoader loader) {
    if (!Main.options.soundboard.enabled) return;
    try {
      Class<?> holder = Reflect.findClass(HOLDER, loader);
      Vector.module
          .hook(
              Reflect.findConstructorExact(
                  holder, "so7.a", FrameLayout.class, "androidx.constraintlayout.widget.Guideline"))
          .intercept(
              chain -> {
                Object result = chain.proceed();
                try {
                  Object binding = Reflect.getObjectField(chain.getThisObject(), "f");
                  View root = (View) Reflect.callMethod(binding, "getRoot");
                  addLauncher(root);
                } catch (Throwable error) {
                  Vector.log("Tencha: party soundboard UI unavailable", error);
                }
                return result;
              });
    } catch (Throwable error) {
      Vector.log("Tencha: party soundboard hook unavailable", error);
    }
  }

  private static void addLauncher(View root) {
    if (root.findViewWithTag(TAG) != null) return;
    // Verified against LINE 26.15.0's constructor and native tab binder (eu7.f).
    // Resolve the names verified in resources.arsc; never use Tencha's own resource IDs in LINE.
    int tabsId = resource(root, "tab", "id");
    int itemLayout = resource(root, "wt_menu_tab", "layout");
    int titleId = resource(root, "menu_title", "id");
    int badgeId = resource(root, "menu_badge_dot", "id");
    int pagerId = resource(root, "menu_pager", "id");
    if (tabsId == 0 || itemLayout == 0 || titleId == 0 || pagerId == 0) return;
    View tabs = root.findViewById(tabsId);
    View pager = root.findViewById(pagerId);
    if (pager == null || !(pager.getParent() instanceof ViewGroup)) return;
    if (tabs == null || !(tabs.getParent() instanceof ViewGroup)) return;
    ViewGroup parent = (ViewGroup) tabs.getParent();
    // The verified native host is a FrameLayout; avoid replacing unknown future hierarchies.
    if (!(parent instanceof FrameLayout)) return;
    View item = LayoutInflater.from(root.getContext()).inflate(itemLayout, parent, false);
    TextView label = item.findViewById(titleId);
    View badge = badgeId == 0 ? null : item.findViewById(badgeId);
    if (label == null) return;
    label.setText("サウンドボード");
    if (badge != null) badge.setVisibility(View.GONE);
    item.setTag(TAG);
    item.setContentDescription("サウンドボード");
    item.setClickable(true);
    item.setFocusable(true);
    FrameLayout pages = new FrameLayout(root.getContext());
    ScrollView soundboardPage = new ScrollView(root.getContext());
    soundboardPage.setFillViewport(true);
    soundboardPage.setClipToPadding(false);
    int padding = Math.round(16 * root.getResources().getDisplayMetrics().density);
    soundboardPage.setPadding(padding, padding, padding, padding);
    LinearLayout content = new LinearLayout(root.getContext());
    content.setOrientation(LinearLayout.VERTICAL);
    soundboardPage.addView(content, new ScrollView.LayoutParams(-1, -2));
    soundboardPage.setVisibility(View.GONE);
    PartyPageTransition transition = new PartyPageTransition(pages, pager, soundboardPage);

    Runnable showNativePage =
        () -> {
          transition.show(false);
          item.setSelected(false);
          tabs.setAlpha(1f);
        };
    // Selected and reselected tabs both return to LINE's actual page. Register before changing
    // the hierarchy so an unsupported native listener API leaves the original screen untouched.
    ClassLoader loader = tabs.getClass().getClassLoader();
    Class<?> listenerType =
        Reflect.findClass("com.google.android.material.tabs.TabLayout$c", loader);
    Object listener =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {listenerType},
            (proxy, method, args) -> {
              String name = method.getName();
              if ("a".equals(name) || "c".equals(name)) showNativePage.run();
              if ("hashCode".equals(name)) return System.identityHashCode(proxy);
              if ("equals".equals(name)) return args != null && proxy == args[0];
              if ("toString".equals(name)) return "TenchaPartyTabListener";
              return null;
            });
    Reflect.callMethod(tabs, "a", listener);
    item.setOnClickListener(
        view -> {
          if (item.isSelected()) return;
          if (!CallMicLevelHook.attachPartySoundboard(content, label.getCurrentTextColor())) {
            Toast.makeText(root.getContext(), "通話画面を取得できません", Toast.LENGTH_SHORT).show();
            return;
          }
          transition.show(true);
          item.setSelected(true);
          tabs.setAlpha(0.6f);
        });

    ViewGroup pagerParent = (ViewGroup) pager.getParent();
    ViewGroup.LayoutParams pagerParams = pager.getLayoutParams();
    int pagerIndex = pagerParent.indexOfChild(pager);
    pagerParent.removeView(pager);
    pages.addView(pager, new FrameLayout.LayoutParams(-1, -1));
    pages.addView(soundboardPage, new FrameLayout.LayoutParams(-1, -1));
    pagerParent.addView(pages, pagerIndex, pagerParams);

    // Keep the actual TabLayout and its two-item adapter untouched: adding a third native Tab
    // would make LINE select a non-existent ViewPager page. A sibling uses the same tab layout.
    LinearLayout row = new LinearLayout(root.getContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    ViewGroup.LayoutParams original = tabs.getLayoutParams();
    int index = parent.indexOfChild(tabs);
    parent.removeView(tabs);
    row.addView(tabs, new LinearLayout.LayoutParams(0, -1, 2f));
    row.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
    parent.addView(row, index, original);
    Vector.log("Tencha: inline soundboard page added to party menu");
  }

  private static int resource(View root, String name, String type) {
    return root.getResources().getIdentifier(name, type, root.getContext().getPackageName());
  }
}
