package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmGetTokenDefaultFreezeStatusTest {

  public static final Bytes GET_TOKEN_DEFAULT_FREEZE_STATUS_INPUT =
      Bytes.fromHexString(
          "0xa7daa18d00000000000000000000000000000000000000000000000000000000000003ff");

  @Test
  void decodeGetTokenDefaultFreezeStatus() {
    final var decodedInput = EvmGetTokenDefaultFreezeStatus.decodeTokenDefaultFreezeStatus(GET_TOKEN_DEFAULT_FREEZE_STATUS_INPUT);

    assertTrue(decodedInput.token().length > 0);
  }
}
