package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

public class EvmKeyTest {

  @Test
  void test() {
    EvmKey evmKey = new EvmKey(null, new byte[]{
        -120, -12, 112, 11, 85, 25, -66, 76, -83, -44, 11, -40, 28, -44, -43,
        -30, 46, 60, -5, 88, 6, 49, 52, -114, 115, -26, 85, -87, -54, 53, -118,
        -116
    },
        new byte[0], null);

    assertEquals(Address.ZERO, evmKey.getContractId());
    assertEquals(Address.ZERO, evmKey.getDelegatableContractId());
  }

}
