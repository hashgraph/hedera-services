package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmGetTokenExpiryInfoPrecompileTest {

  public static final Bytes GET_EXPIRY_INFO_FOR_TOKEN_INPUT =
      Bytes.fromHexString(
          "0xd614cdb800000000000000000000000000000000000000000000000000000000000008c1");

  @Test
  void decodeGetTokenExpiryInfo() {
    final var decodedInput = EvmGetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo(GET_EXPIRY_INFO_FOR_TOKEN_INPUT);

    assertTrue(decodedInput.token().length > 0);
  }
}
