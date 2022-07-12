package com.hedera.services.store.contracts.precompile;

/**
 * All key type constants used by {@link Precompile} implementations, in one place for easy review.
 */
public final class KeyTypeConstants {
  private KeyTypeConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static final int ADMIN_KEY = 1;
  public static final int KYC_KEY = 2;
  public static final int FREEZE_KEY = 4;
  public static final int WIPE_KEY = 8;
  public static final int SUPPLY_KEY = 16;
  public static final int FEE_SCHEDULE_KEY = 32;
  public static final int PAUSE_KEY = 64;
}
