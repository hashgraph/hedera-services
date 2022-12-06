package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmGetTokenDefaultKycStatusTest {

  public static final Bytes GET_TOKEN_DEFAULT_KYC_STATUS_INPUT =
      Bytes.fromHexString(
          "0x335e04c10000000000000000000000000000000000000000000000000000000000000404");

  @Test
  void decodeGetTokenDefaultKycStatus() {
    final var decodedInput = EvmGetTokenDefaultKycStatus.decodeTokenDefaultKycStatus(GET_TOKEN_DEFAULT_KYC_STATUS_INPUT);

    assertTrue(decodedInput.token().length > 0);
  }
}
