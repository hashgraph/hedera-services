package com.hedera.evm.exception;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public class ResourceLimitException extends InvalidTransactionException {
  public ResourceLimitException(ResponseCodeEnum responseCode) {
    super(responseCode);
  }
}

