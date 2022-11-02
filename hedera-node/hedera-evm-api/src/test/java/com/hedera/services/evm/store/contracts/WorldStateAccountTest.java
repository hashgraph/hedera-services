package com.hedera.services.evm.store.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WorldStateAccountTest {

  @Mock
  HederaEvmEntityAccess entityAccess;

  MockAbstractCodeCache codeCache =  new MockAbstractCodeCache(100, entityAccess);;

  private final Address address =
      Address.fromHexString("0x000000000000000000000000000000000000077e");

  WorldStateAccount subject = new WorldStateAccount(address, Wei.ONE, codeCache,
      entityAccess);

  @Test
  void getAddress() {
    assertEquals(address, subject.getAddress());
  }

  @Test
  void getAddressHash() {
    assertEquals(Hash.EMPTY, subject.getAddressHash());
  }

  @Test
  void getNonce() {
    assertEquals(0, subject.getNonce());
  }

  @Test
  void getBalance() {
    assertEquals(Wei.ONE, subject.getBalance());
  }

}
