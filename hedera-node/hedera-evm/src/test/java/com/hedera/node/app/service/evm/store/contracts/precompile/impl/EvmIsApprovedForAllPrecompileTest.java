package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmIsApprovedForAllPrecompileTest {

  public static final Bytes IS_APPROVED_FOR_ALL_INPUT_ERC =
      Bytes.fromHexString(
          "0x618dc65e00000000000000000000000000000000000003ece985e9c50000000000000000000000000000000000000000000000000000000027bc86aa00000000000000000000000000000000000000000000000000000000000003eb");

  @Test
  void decodeIsApprovedForAll() {
    final var decodedInput = EvmIsApprovedForAllPrecompile.decodeIsApprovedForAll(IS_APPROVED_FOR_ALL_INPUT_ERC);

    assertTrue(decodedInput.token().length > 0);
    assertTrue(decodedInput.owner().length > 0);
    assertTrue(decodedInput.operator().length > 0);
  }
}
