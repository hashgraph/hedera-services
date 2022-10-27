package com.hedera.services.evm.store.models;

import static org.junit.Assert.assertEquals;


import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.Test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdatedHederaEvmAccountTest {
  private static final long newBalance = 200_000L;
  private static final int constNonce = 2;
  private final Address address =
      Address.fromHexString("0x000000000000000000000000000000000000077e");
  private UpdatedHederaEvmAccount subject;

  @BeforeEach
  void setUp() {
    subject = new UpdatedHederaEvmAccount(address, 1, Wei.ONE);
  }

  @Test
  void balanceChanges() {
    subject.setBalance(Wei.of(newBalance));
    assertEquals(newBalance, subject.getBalance().toLong());
  }

}
