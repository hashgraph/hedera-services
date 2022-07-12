package com.hedera.services.store.contracts.precompile;

/**
 * All key types used by {@link Precompile} implementations, in one place for easy review.
 */
public enum TokenKeyType {
  ADMIN_KEY(1),
  KYC_KEY(2),
  FREEZE_KEY(4),
  WIPE_KEY(8),
  SUPPLY_KEY(16),
  FEE_SCHEDULE_KEY(32),
  PAUSE_KEY(64);

  private final int value;

  TokenKeyType(final int value) {
    this.value = value;
  }

  public int value() {
    return value;
  }
}
