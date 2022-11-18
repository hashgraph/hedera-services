package com.hedera.test.utils;

import com.swirlds.common.crypto.config.CryptoConfig;

public class CryptoConfigUtils {
  public static CryptoConfig MINIMAL_CRYPTO_CONFIG = new CryptoConfig(1, 1, 5, 5, false, "keystorePass");

  private CryptoConfigUtils() {}
}
