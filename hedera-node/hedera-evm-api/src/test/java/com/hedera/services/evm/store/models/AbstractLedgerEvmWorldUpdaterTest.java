package com.hedera.services.evm.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.services.evm.accounts.AccountAccessor;
import com.hedera.services.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import java.util.Collections;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AbstractLedgerEvmWorldUpdaterTest {
  private final Address address =
      Address.fromHexString("0x000000000000000000000000000000000000077e");
  AccountAccessor accountAccessor;
  AbstractLedgerEvmWorldUpdater abstractLedgerEvmWorldUpdater = new AbstractLedgerEvmWorldUpdater(accountAccessor);

  @Test
  void accountTests() {
    assertNull(abstractLedgerEvmWorldUpdater.createAccount(address, 1, Wei.ONE));
    assertNull(abstractLedgerEvmWorldUpdater.getAccount(address));
    assertEquals(Collections.emptyList(), abstractLedgerEvmWorldUpdater.getTouchedAccounts());
    assertEquals(Collections.emptyList(), abstractLedgerEvmWorldUpdater.getDeletedAccountAddresses());
  }

  @Test
  void updaterTest() {
    assertEquals(Optional.empty(), abstractLedgerEvmWorldUpdater.parentUpdater());
    assertNull(abstractLedgerEvmWorldUpdater.updater());
  }
}
