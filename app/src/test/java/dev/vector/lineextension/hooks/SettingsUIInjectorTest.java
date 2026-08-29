package dev.vector.lineextension.hooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsUIInjectorTest {
  private static class Owner {}

  private static class BaseAdapter {
    private final Owner owner;

    BaseAdapter(Owner owner) {
      this.owner = owner;
    }
  }

  private static final class MainSettingsAdapter extends BaseAdapter {
    private static final Owner UNRELATED_STATIC_OWNER = new Owner();

    MainSettingsAdapter(Owner owner) {
      super(owner);
    }
  }

  private static class ResourceBase {
    private final int resourceId;

    ResourceBase(int resourceId) {
      this.resourceId = resourceId;
    }
  }

  private static final class HeaderModel extends ResourceBase {
    private static final int UNRELATED_STATIC_ID = 42;

    HeaderModel(int resourceId) {
      super(resourceId);
    }
  }

  @Test
  public void findsFragmentOwnerInAdapterSuperclass() {
    Owner owner = new Owner();
    assertTrue(SettingsUIInjector.hasInstanceFieldValue(new MainSettingsAdapter(owner), owner));
  }

  @Test
  public void ignoresDifferentAndStaticOwners() {
    Owner owner = new Owner();
    MainSettingsAdapter adapter = new MainSettingsAdapter(new Owner());
    assertFalse(SettingsUIInjector.hasInstanceFieldValue(adapter, owner));
    assertFalse(
        SettingsUIInjector.hasInstanceFieldValue(
            adapter, MainSettingsAdapter.UNRELATED_STATIC_OWNER));
  }

  @Test
  public void findsPrivateIntFieldInModelSuperclass() {
    assertTrue(SettingsUIInjector.hasIntFieldValue(new HeaderModel(1234), 1234));
    assertFalse(SettingsUIInjector.hasIntFieldValue(new HeaderModel(1234), 42));
    assertFalse(SettingsUIInjector.hasIntFieldValue(new HeaderModel(1234), 5678));
  }
}
