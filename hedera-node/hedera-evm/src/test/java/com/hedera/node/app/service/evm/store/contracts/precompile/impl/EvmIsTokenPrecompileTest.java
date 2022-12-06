package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmIsTokenPrecompileTest {

  private static final Bytes IS_TOKEN_INPUT =
      Bytes.fromHexString(
          "0x19f373610000000000000000000000000000000000000000000000000000000000000b03");

  @Test
  void decodeIsToken() {
    final var decodedInput = EvmIsTokenPrecompile.decodeIsToken(IS_TOKEN_INPUT);

    assertTrue(decodedInput.token().length > 0);
    assertEquals(-1, decodedInput.serialNumber());
  }
}
