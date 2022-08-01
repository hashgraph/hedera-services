package com.hedera.services.store.contracts;

public class KvUsageInfo {
  private int current;
  private int pending;

  public KvUsageInfo(int current) {
    this.current = current;
    this.pending = current;
  }

  public void updatePendingBy(final int delta) {
    pending += delta;
  }

  public int pendingUsageDelta() {
    return pending - current;
  }

  public int pendingUsage() {
    return pending;
  }
}
