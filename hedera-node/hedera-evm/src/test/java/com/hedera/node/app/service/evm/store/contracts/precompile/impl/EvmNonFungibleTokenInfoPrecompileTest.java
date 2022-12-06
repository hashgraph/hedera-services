package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmNonFungibleTokenInfoPrecompileTest {

  public static final Bytes GET_NON_FUNGIBLE_TOKEN_INFO_INPUT =
      Bytes.fromHexString(
          "0x287e1da8000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000001");

  @Test
  void decodeNonFungibleTokenInfo() {
    final var decodedInput = EvmNonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo(GET_NON_FUNGIBLE_TOKEN_INFO_INPUT);

    assertTrue(decodedInput.token().length > 0);
    assertEquals(1, decodedInput.serialNumber());
  }
}
