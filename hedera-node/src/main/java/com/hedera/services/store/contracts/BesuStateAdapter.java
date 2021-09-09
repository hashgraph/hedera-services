package com.hedera.services.store.contracts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.contracts.sources.BlobStorageSource;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.AbstractWorldUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeSet;

public class BesuStateAdapter implements WorldUpdater {

//  private final Map<Address, AccountStorageMap> updatedStorageTries = new HashMap<>();
  private final Map<Address, Bytes> updatedAccountCode = new HashMap<>();

  private final AccountStore accountStore;
  private final BlobStorageSource blobStorageSource;
//  private final VirtualMap<ContractKey, ContractValue> virtualMap;
  private final Map<Address, Bytes> provisionalCodeUpdates = new HashMap<>();
  private final Map<Address, EvmAccountImpl> provisionalAccountUpdates = new HashMap<>();
  private final Map<Id, Map.Entry<Id, HederaAccountCustomizer>> provisionalAccountCreations = new HashMap<>();

  public BesuStateAdapter(
          final AccountStore accountStore,
          final BlobStorageSource blobStorageSource
  ) {
    this.accountStore = accountStore;
    this.blobStorageSource = blobStorageSource;
  }

  @Override
  public WorldUpdater updater() {
    return new Updater(this);
  }

  @Override
  public Account get(final Address address) {

    Id id = EntityIdUtils.idParsedFromEvmAddress(address.toArray());

    if (provisionalAccountCreations.containsKey(id)) {
      return new EvmAccountImpl(address, Wei.of(0));
    }

    if (accountStore.exists(id)) {
      com.hedera.services.store.models.Account hederaAccount = accountStore.loadAccount(id);

      var balance = hederaAccount.getBalance();
      if (hederaAccount.isSmartContract()) {
        var code = provisionalCodeUpdates.containsKey(address) ?
                provisionalCodeUpdates.get(address) : Bytes.of(blobStorageSource.get(address.toArray()));
        return new EvmAccountImpl(address, Wei.of(balance), code);
      }
      // TODO we must address nonces and mitigate all EVM related operations since Hedera does not have the concept of nonces
      return new EvmAccountImpl(address, Wei.of(balance));
    }

    // TODO: test out when you want to send to a non-existing address in Hedera
    return null;
  }

  public void put(Address address, long nonce, Wei balance) {
    provisionalAccountUpdates.put(address, new EvmAccountImpl(address, balance));
  }

  public void putCode(Address address, Bytes code) {
    provisionalCodeUpdates.put(address, code);
  }

  public Bytes getCode(Address address) {
    if (provisionalCodeUpdates.containsKey(address)) {
      return provisionalCodeUpdates.get(address);
    } else {
      var codeBytes = blobStorageSource.get(address.toArray());
      return codeBytes == null ? Bytes.EMPTY : Bytes.of(codeBytes);
    }
  }

  @Override
  public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
    //todo
    return null;
  }

  @Override
  public EvmAccount getAccount(final Address address) {
    //todo is this ok?
    return new UpdateTrackingAccount<>(get(address));
  }

  @Override
  public void deleteAccount(final Address address) {
    // TODO: set somewhere provisionally
  }

  public void clearStorage(Address address) {
    // TODO: set somewhere provisionally
  }

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    //todo
    return null;
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    //todo
    return null;
  }

  @Override
  public void revert() {
    //todo
//    accounts = new HashMap<>();
  }

  @Override
  public void commit() {
    //todo can we spawn account without using hederaledger?
//    provisionalAccountCreations.forEach((accountId, entry) -> {
//      validateTrue(!accountStore.exists(accountId), FAIL_INVALID);
//      var pair = entry;
//      if (pair != null) {
//        validateTrue((Boolean) pair.getValue().getChanges().get(AccountProperty.IS_SMART_CONTRACT), INVALID_SOLIDITY_ADDRESS);
//        com.hedera.services.store.models.Account account = new com.hedera.services.store.models.Account(accountId);
//        account.initBalance(0L);
//        accountStore.persistAccount(pair.getKey(), accountId, 0L, pair.getValue());
//      }
//    });
//
//    provisionalAccountUpdates.forEach((address, evmAccount) -> {
//      final var accountId = EntityIdUtils.accountParsedFromSolidityAddress(address.toArray());
//      validateTrue(ledger.exists(accountId), FAIL_INVALID);
//      final var account = ledger.get(accountId);
//      ledger.adjustBalance(accountId, evmAccount.getBalance().toLong() - account.getBalance());
//    });

    /* Commit code updates for each updated address */
    provisionalCodeUpdates.forEach((address, code) -> {
      blobStorageSource.put(address.toArray(), code.toArray());
    });

    /* Clear any provisional changes */
    provisionalCodeUpdates.clear();
    provisionalAccountUpdates.clear();
    provisionalAccountCreations.clear();
  }

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    return Optional.empty();
  }

  // An immutable class that represents an individual account as stored in
  // in the world state's underlying merkle patricia trie.
  public class WorldStateAccount implements Account {

    private final Address address;
    private final long nonce;
    private final Wei balance;

    //todo Lazily initialized since we don't always access storage.
//    private volatile AccountStorageMap storageTrie;

    public WorldStateAccount(final Address address, final long nonce, final Wei balance) {
      this.address = address;
      this.nonce = nonce;
      this.balance = balance;
    }
//todo
//    private AccountStorageMap storageTrie() {
//      final AccountStorageMap updatedTrie = updatedStorageTries.get(address);
//      if (updatedTrie != null) {
//        storageTrie = updatedTrie;
//      }
//      if (storageTrie == null) {
//        storageTrie = newAccountStorageMap(address);
//      }
//      return storageTrie;
//    }

    @Override
    public Address getAddress() {
      return address;
    }

    @Override
    public Hash getAddressHash() {
      return Hash.EMPTY; // Not supported!
    }

    @Override
    public long getNonce() {
      return nonce;
    }

    @Override
    public Wei getBalance() {
      return balance;
    }

    Hash getStorageRoot() {
      return Hash.EMPTY; // Not supported!
    }

    @Override
    public Bytes getCode() {
      final Bytes updatedCode = updatedAccountCode.get(address);
      if (updatedCode != null) {
        return updatedCode;
      }

//      return accountStateStore.getCode(address); todo
      return null;
    }

    @Override
    public boolean hasCode() {
      return !getCode().isEmpty();
    }

    @Override
    public Hash getCodeHash() {
      return Hash.EMPTY; // Not supported!
    }

    @Override
    public UInt256 getStorageValue(final UInt256 key) {
      return null;// todo
//      return storageTrie().get(key).orElse(UInt256.ZERO);
    }

    @Override
    public UInt256 getOriginalStorageValue(final UInt256 key) {
      return getStorageValue(key);
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
            final Bytes32 startKeyHash, final int limit) {
      throw new UnsupportedOperationException("Stream storage entries not supported");
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("AccountState").append("{");
      builder.append("address=").append(getAddress()).append(", ");
      builder.append("nonce=").append(getNonce()).append(", ");
      builder.append("balance=").append(getBalance()).append(", ");
      builder.append("storageRoot=").append(getStorageRoot()).append(", ");
      builder.append("codeHash=").append(getCodeHash()).append(", ");
      return builder.append("}").toString();
    }
  }

  protected static class Updater
          extends AbstractWorldUpdater<BesuStateAdapter, WorldStateAccount> {

    protected Updater(final BesuStateAdapter world) {
      super(world);
    }

    @Override
    protected WorldStateAccount getForMutation(final Address address) {
      final BesuStateAdapter wrapped = wrappedWorldView();
      Account acc = wrapped.get(address);
      return acc == null
              ? null
              : wrapped.new WorldStateAccount(acc.getAddress(), acc.getNonce(), acc.getBalance());
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
      return new ArrayList<>(getUpdatedAccounts());
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      return new ArrayList<>(getDeletedAccounts());
    }

    @Override
    public void revert() {
      getDeletedAccounts().clear();
      getUpdatedAccounts().clear();
    }

    @Override
    public void commit() {
      final BesuStateAdapter wrapped = wrappedWorldView();

      for (final Address address : getDeletedAccounts()) {
        wrapped.deleteAccount(address);
      }

      for (final UpdateTrackingAccount<WorldStateAccount> updated : getUpdatedAccounts()) {
        final WorldStateAccount origin = updated.getWrappedAccount();

        // Save the code in storage ...
        if (updated.codeWasUpdated()) {
          wrapped.putCode(updated.getAddress(), updated.getCode());
        }
        // ...and storage in the account trie first.
        final boolean freshState = origin == null || updated.getStorageWasCleared();
        if (freshState) {
          wrapped.clearStorage(updated.getAddress());
        }

        final Map<UInt256, UInt256> updatedStorage = updated.getUpdatedStorage();
        if (!updatedStorage.isEmpty()) {
          // Apply any storage updates todo
//          final AccountStorageMap storageTrie =
//                  freshState ? wrapped.newAccountStorageMap(origin.getAddress()) : origin.storageTrie();
          final TreeSet<Map.Entry<UInt256, UInt256>> entries =
                  new TreeSet<>(Comparator.comparing(Map.Entry::getKey));
          entries.addAll(updatedStorage.entrySet());

          for (final Map.Entry<UInt256, UInt256> entry : entries) {
            final UInt256 value = entry.getValue();
//        todo    if (value.isZero()) {
//              storageTrie.remove(entry.getKey());
//            } else {
//              storageTrie.put(entry.getKey(), value);
//            }
          }
        }

        // Lastly, save the new account.
        // TODO we must not allow for arbitrary contract creation. If we do `get` for account and it
        // returns `null` we must halt the execution and revert
        wrapped.put(
                updated.getAddress(), updated.getNonce(), updated.getBalance());
      }

      // Commit account state changes
      wrapped.commit();

      // Clear structures
//      wrapped.updatedStorageTries.clear();
    }
  }
}
