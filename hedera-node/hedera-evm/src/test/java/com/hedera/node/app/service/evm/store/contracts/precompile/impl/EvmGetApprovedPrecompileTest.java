package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmGetApprovedPrecompileTest {

  private static final Bytes GET_APPROVED_INPUT_ERC =
      Bytes.fromHexString(
          "0x618dc65e00000000000000000000000000000000000003ec081812fc0000000000000000000000000000000000000000000000000000000000000001");

  @Test
  void decodeGetApproved() {
    final var decodedInput = EvmGetApprovedPrecompile.decodeGetApproved(GET_APPROVED_INPUT_ERC);

    assertTrue(decodedInput.token().length > 0);
    assertEquals(1, decodedInput.serialNo());
  }
}
