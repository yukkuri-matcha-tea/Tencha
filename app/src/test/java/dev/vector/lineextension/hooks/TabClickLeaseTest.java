package dev.vector.lineextension.hooks;

import static org.junit.Assert.*;

import org.junit.Test;

public class TabClickLeaseTest {
  @Test
  public void programmaticNavigationWithoutTouchIsNotAnimated() {
    assertFalse(new TabClickLease().consume(100));
  }

  @Test
  public void clickIsConsumedOnlyOnce() {
    TabClickLease lease = new TabClickLease();
    lease.begin(100);
    assertTrue(lease.consume(150));
    assertFalse(lease.consume(151));
  }

  @Test
  public void lagCannotLeakClickIntoRestoration() {
    TabClickLease lease = new TabClickLease();
    lease.begin(100);
    assertFalse(lease.consume(351));
  }

  @Test
  public void oldCleanupDoesNotCancelNewClick() {
    TabClickLease lease = new TabClickLease();
    long old = lease.begin(100);
    lease.begin(200);
    lease.cancel(old);
    assertTrue(lease.consume(220));
  }

  @Test
  public void leavingActivityInvalidatesPendingClick() {
    TabClickLease lease = new TabClickLease();
    lease.begin(100);
    lease.cancel();
    assertFalse(lease.consume(110));
  }
}
