package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmIsKycPrecompileTest {

  public static final Bytes IS_KYC =
      Bytes.fromHexString(
          "0xf2c31ff400000000000000000000000000000000000000000000000000000000000004b200000000000000000000000000000000000000000000000000000000000004b0");

  @Test
  void decodeIsKyc() {
    final var decodedInput = EvmIsKycPrecompile.decodeIsKyc(IS_KYC);

    assertTrue(decodedInput.token().length > 0);
    assertTrue(decodedInput.account().length > 0);
  }
}
