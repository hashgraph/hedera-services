package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmIsFrozenPrecompileTest {

  public static final Bytes IS_FROZEN_INPUT =
      Bytes.fromHexString(
          "0x46de0fb1000000000000000000000000000000000000000000000000000000000000050e000000000000000000000000000000000000000000000000000000000000050c");

  @Test
  void decodeIsFrozen() {
    final var decodedInput = EvmIsFrozenPrecompile.decodeIsFrozen(IS_FROZEN_INPUT);

    assertTrue(decodedInput.token().length > 0);
    assertTrue(decodedInput.account().length > 0);
  }
}
