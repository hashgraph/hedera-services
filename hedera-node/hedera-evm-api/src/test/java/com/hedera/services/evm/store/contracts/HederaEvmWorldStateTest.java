package com.hedera.services.evm.store.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.services.evm.contracts.execution.EvmProperties;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HederaEvmWorldStateTest {

  @Mock
  private HederaEvmEntityAccess hederaEvmEntityAccess;

  @Mock
  private EvmProperties evmProperties;

  @Mock
  private AbstractCodeCache abstractCodeCache;

  private HederaEvmWorldState subject;

  @BeforeEach
  void setUp() {
    subject =
        new HederaEvmWorldState(hederaEvmEntityAccess, evmProperties, abstractCodeCache) {
          @Override
          public HederaEvmWorldUpdater updater() {
            return null;
          }
        };
  }

  @Test
  void rootHash() {
    assertEquals(Hash.EMPTY, subject.rootHash());
  }

  @Test
  void frontierRootHash() {
    assertEquals(Hash.EMPTY, subject.frontierRootHash());
  }

  @Test
  void streamAccounts() {
    assertNull(subject.streamAccounts(Bytes32.ZERO, 1));
  }

}
