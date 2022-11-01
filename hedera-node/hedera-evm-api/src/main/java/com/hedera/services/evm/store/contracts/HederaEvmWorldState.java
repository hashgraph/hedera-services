package com.hedera.services.evm.store.contracts;

import com.hedera.services.evm.contracts.execution.EvmProperties;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;

public class HederaEvmWorldState {

  private final HederaEvmEntityAccess hederaEvmEntityAccess;
  private final EvmProperties evmProperties;
  private final AbstractCodeCache abstractCodeCache;

  public HederaEvmWorldState(HederaEvmEntityAccess hederaEvmEntityAccess,
      EvmProperties evmProperties, AbstractCodeCache abstractCodeCache) {
    this.hederaEvmEntityAccess = hederaEvmEntityAccess;
    this.evmProperties = evmProperties;
    this.abstractCodeCache = abstractCodeCache;
  }

  public Account get(final Address address) {
    if (address == null) {
      return null;
    }
    if (hederaEvmEntityAccess.isTokenAccount(address)
        && evmProperties.isRedirectTokenCallsEnabled()) {
      return new HederaEvmWorldStateTokenAccount(address);
    }
    if (!hederaEvmEntityAccess.isUsable(address)) {
      return null;
    }
    final long balance = hederaEvmEntityAccess.getBalance(address);
    return new WorldStateAccount(address, Wei.of(balance), abstractCodeCache, hederaEvmEntityAccess);
  }

}
