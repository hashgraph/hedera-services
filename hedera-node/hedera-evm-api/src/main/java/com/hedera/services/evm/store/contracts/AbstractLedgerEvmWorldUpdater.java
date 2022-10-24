package com.hedera.services.evm.store.contracts;

import com.hedera.services.evm.accounts.AccountAccessor;
import java.util.Collection;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class AbstractLedgerEvmWorldUpdater implements WorldUpdater {

  protected final AccountAccessor accountAccessor;

  protected AbstractLedgerEvmWorldUpdater(AccountAccessor accountAccessor) {
    this.accountAccessor = accountAccessor;
  }

  @Override
  public EvmAccount createAccount(Address address, long nonce, Wei balance) {
    return null;
  }

  @Override
  public EvmAccount getAccount(Address address) {
    return null;
  }

  @Override
  public void deleteAccount(Address address) {

  }

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return null;
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return null;
  }

  @Override
  public void revert() {

  }

  @Override
  public void commit() {

  }

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    return Optional.empty();
  }

  @Override
  public WorldUpdater updater() {
    return null;
  }

  @Override
  public Account get(Address address) {
    return new com.hedera.services.evm.store.models.EvmAccount(getCanonicalAddress(address));
  }

  public Address getCanonicalAddress(Address address) {
    return accountAccessor.exists(address);
  }

}