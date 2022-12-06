package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmGetTokenTypePrecompileTest {

  private static final Bytes GET_TOKEN_TYPE_INPUT =
      Bytes.fromHexString(
          "0x93272baf0000000000000000000000000000000000000000000000000000000000000b0d");

  @Test
  void decodeGetTokenTypeInput() {
    final var decodedInput = EvmGetTokenTypePrecompile.decodeGetTokenType(GET_TOKEN_TYPE_INPUT);

    assertTrue(decodedInput.token().length > 0);
    assertEquals(-1, decodedInput.serialNumber());
  }
}
