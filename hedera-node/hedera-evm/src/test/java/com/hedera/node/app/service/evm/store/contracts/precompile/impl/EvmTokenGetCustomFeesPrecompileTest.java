package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmTokenGetCustomFeesPrecompileTest {

  private static final Bytes GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT =
      Bytes.fromHexString(
          "0xae7611a000000000000000000000000000000000000000000000000000000000000003ee");

  @Test
  void decodeTokenGetCustomFees() {
    final var decodedInput = EvmTokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT);

    assertTrue(decodedInput.token().length > 0);
  }
}
