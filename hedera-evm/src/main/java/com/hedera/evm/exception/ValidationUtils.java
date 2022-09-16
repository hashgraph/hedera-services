package com.hedera.evm.exception;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public class ValidationUtils {
  private ValidationUtils() {
    throw new UnsupportedOperationException("Utility Class");
  }

  public static void validateTrue(final boolean flag, final ResponseCodeEnum code) {
    if (!flag) {
      throw new InvalidTransactionException(code);
    }
  }

}
