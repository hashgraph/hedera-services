package com.hedera.node.app.service.evm.exceptions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;

public class InvalidTransactionException extends RuntimeException {

  private final ResponseCodeEnum responseCode;
  private final boolean reverting;

  public InvalidTransactionException(final ResponseCodeEnum responseCode) {
    this(responseCode.name(), responseCode, false);
  }

  public InvalidTransactionException(final ResponseCodeEnum responseCode, boolean reverting) {
    this(responseCode.name(), responseCode, reverting);
  }

  public InvalidTransactionException(
      final String detailMessage, final ResponseCodeEnum responseCode) {
    this(detailMessage, responseCode, false);
  }

  public InvalidTransactionException(
      final String detailMessage, final ResponseCodeEnum responseCode, boolean reverting) {
    super(detailMessage);
    this.responseCode = responseCode;
    this.reverting = reverting;
  }

  public ResponseCodeEnum getResponseCode() {
    return responseCode;
  }

  public boolean isReverting() {
    return reverting;
  }

  public Bytes getRevertReason() {
    if (!isReverting()) {
      throw new IllegalStateException();
    }
    return messageBytes();
  }

  public Bytes messageBytes() {
    final var detail = getMessage();
    return Bytes.of(detail.getBytes());
  }
}
