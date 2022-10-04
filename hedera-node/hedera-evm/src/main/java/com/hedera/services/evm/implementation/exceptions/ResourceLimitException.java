package com.hedera.services.evm.implementation.exceptions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public class ResourceLimitException extends InvalidTransactionException {
  public ResourceLimitException(ResponseCodeEnum responseCode) {
    super(responseCode);
  }
}