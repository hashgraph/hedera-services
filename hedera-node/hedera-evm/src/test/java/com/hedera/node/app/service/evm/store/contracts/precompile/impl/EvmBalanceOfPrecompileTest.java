package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EvmBalanceOfPrecompileTest {

  private static final Bytes BALANCE_INPUT =
      Bytes.fromHexString(
          "0x70a08231000000000000000000000000000000000000000000000000000000000000059f");

  @Test
  void decodeBalanceInput() {
    final var decodedInput = EvmBalanceOfPrecompile.decodeBalanceOf(BALANCE_INPUT);

    assertTrue(decodedInput.account().length > 0);
  }
}
