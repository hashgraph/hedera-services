package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmTokenURIPrecompileTest {

  private static final Bytes TOKEN_URI_INPUT =
      Bytes.fromHexString(
          "0xc87b56dd0000000000000000000000000000000000000000000000000000000000000001");

  @Test
  void decodeTokenURI() {
    final var decodedInput = EvmTokenURIPrecompile.decodeTokenUriNFT(TOKEN_URI_INPUT);

    assertEquals(1, decodedInput.serialNo());
  }
}
