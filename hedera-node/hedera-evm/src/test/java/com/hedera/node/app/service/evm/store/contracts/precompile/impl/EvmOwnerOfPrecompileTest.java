package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmOwnerOfPrecompileTest {

  private static final Bytes OWNER_OF_INPUT =
      Bytes.fromHexString(
          "0x6352211e0000000000000000000000000000000000000000000000000000000000000001");

  @Test
  void decodeOwnerOf() {
    final var decodedInput = EvmOwnerOfPrecompile.decodeOwnerOf(OWNER_OF_INPUT);

    assertEquals(1, decodedInput.serialNo());
  }
}
