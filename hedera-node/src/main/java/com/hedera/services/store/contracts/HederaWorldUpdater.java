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

import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.db.ServicesRepositoryRoot;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.AbstractWorldUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;

public class HederaWorldUpdater implements WorldUpdater {

  private final AccountStore accountStore;
  private final ServicesRepositoryRoot repositoryRoot;

  private final Map<Address, Bytes> updatedAccountCode = new HashMap<>();
  private final Map<Address, Bytes> updatedStorageTries = new HashMap<>();

  private final Map<Address, Bytes> provisionalCodeUpdates = new HashMap<>();
  private final Map<Address, EvmAccountImpl> provisionalAccountUpdates = new HashMap<>();
  private final Map<Id, Map.Entry<Id, HederaAccountCustomizer>> provisionalAccountCreations = new HashMap<>();

  @Inject
  public HederaWorldUpdater(
          final AccountStore accountStore,
          final ServicesRepositoryRoot repositoryRoot
          ) {
    this.accountStore = accountStore;
    this.repositoryRoot = repositoryRoot;
  }

  public void put(Address address, Wei balance) {
    provisionalAccountUpdates.put(address, new EvmAccountImpl(address, balance));
  }

  public void putCode(Address address, Bytes code) {
    provisionalCodeUpdates.put(address, code);
  }

  public Bytes getCode(Address address) {
    if (provisionalCodeUpdates.containsKey(address)) {
      return provisionalCodeUpdates.get(address);
    } else {
      var codeBytes = repositoryRoot.getCode(address.toArray());
      return codeBytes == null ? Bytes.EMPTY : Bytes.of(codeBytes);
    }
  }

  private void clearStorage(Address address) {
    //todo
  }

  @Override
  public WorldUpdater updater() {
    return new Updater(this);
  }

  @Override
  public Account get(Address address) {
    Id id = EntityIdUtils.idParsedFromEvmAddress(address.toArray());

    if (provisionalAccountCreations.containsKey(id)) {
      return new HederaUpdateTrackingAccount(new EvmAccountImpl(address, Wei.of(0)));
    }

    if (accountStore.exists(id)) {
      com.hedera.services.store.models.Account hederaAccount = accountStore.loadAccount(id);

      var balance = hederaAccount.getBalance();
      if (hederaAccount.isSmartContract()) {
        var code = provisionalCodeUpdates.containsKey(address) ?
                provisionalCodeUpdates.get(address) : Bytes.of(repositoryRoot.getCode(address.toArray()));
        return new HederaUpdateTrackingAccount(new EvmAccountImpl(address, Wei.of(balance), code));
      }
      // TODO we must address nonces and mitigate all EVM related operations since Hedera does not have the concept of nonces
      return new HederaUpdateTrackingAccount(new EvmAccountImpl(address, Wei.of(balance)));
    }

    // TODO: test out when you want to send to a non-existing address in Hedera
    return null;
  }

  @Override
  public EvmAccount createAccount(Address address, long nonce, Wei balance) {
    Id id = EntityIdUtils.idParsedFromEvmAddress(address.toArray());
    com.hedera.services.store.models.Account account = new com.hedera.services.store.models.Account(id);
    account.setBalance(balance.toLong());
    accountStore.persistNew(account);
    return new HederaUpdateTrackingAccount(new EvmAccountImpl(address, balance));
  }

  @Override
  public EvmAccount getAccount(final Address address) {
    return new HederaUpdateTrackingAccount(get(address));
  }

  @Override
  public void deleteAccount(Address address) {
    //todo
  }

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return provisionalAccountUpdates.values();
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return null;
  }

  @Override
  public void revert() {
    //todo
  }

  @Override
  public void commit() {
    //todo sponsor, customizer? we ignore them with the accountstore
    provisionalAccountCreations.forEach((accountId, entry) -> {
      validateTrue(!accountStore.exists(accountId), FAIL_INVALID);
      var pair = entry;
      if (pair != null) {
        validateTrue((Boolean) pair.getValue().getChanges().get(AccountProperty.IS_SMART_CONTRACT), INVALID_SOLIDITY_ADDRESS);
        com.hedera.services.store.models.Account account = new com.hedera.services.store.models.Account(accountId);
        account.setBalance(0L);
        accountStore.persistNew(account);
      }
    });

    provisionalAccountUpdates.forEach((address, evmAccount) -> {
      final var accountId = EntityIdUtils.idParsedFromEvmAddress(address.toArray());
      validateTrue(accountStore.exists(accountId), FAIL_INVALID);
      final var account = accountStore.loadAccount(accountId);
      //todo is this enough to update the account balance?
      account.setBalance(evmAccount.getBalance().toLong() - account.getBalance());
      accountStore.persistAccount(account);
    });

    /* Commit code updates for each updated address */
    provisionalCodeUpdates.forEach((address, code) -> {
      repositoryRoot.saveCode(address.toArray(), code.toArray());
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

    private volatile Bytes storageTrie;

    public WorldStateAccount(final Address address, final long nonce, final Wei balance) {
      this.address = address;
      this.nonce = nonce;
      this.balance = balance;
    }
//todo
    private Bytes storageTrie() {
      final Bytes updatedTrie = updatedStorageTries.get(address);
      if (updatedTrie != null) {
        storageTrie = updatedTrie;
      }
      if (storageTrie == null) {
//        storageTrie = newAccountStorageMap(address);
      }
      return storageTrie;
    }

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
      return Bytes.wrap(repositoryRoot.getCode(address.toArray()));
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
      // todo
//      repositoryRoot.getStorageValue()
      //      return storageTrie().get(key).orElse(UInt256.ZERO);
      return null;
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
          extends AbstractWorldUpdater<HederaWorldUpdater, WorldStateAccount> {

    protected Updater(final HederaWorldUpdater world) {
      super(world);
    }

    @Override
    protected WorldStateAccount getForMutation(final Address address) {
      final HederaWorldUpdater wrapped = wrappedWorldView();
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

    // we need to override this or MessageCallProcessor.transferValue fails
    @Override
    public EvmAccount getAccount(final Address address) {
      // We may have updated it already, so check that first.
      final MutableAccount existing = updatedAccounts.get(address);
      if (existing != null) {
        return new WrappedEvmAccount(existing);
      }
      if (deletedAccounts.contains(address)) {
        return null;
      }

      // Otherwise, get it from our wrapped view and create a new update tracker.
      final Account origin = getForMutation(address);
      if (origin == null) {
        return null;
      } else {
        return new WrappedEvmAccount(track(new HederaUpdateTrackingAccount(origin)));
      }
    }

    @Override
    public void commit() {

      final HederaWorldUpdater wrapped = wrappedWorldView();

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
//          final TreeSet<Map.Entry<UInt256, UInt256>> entries =
//                  new TreeSet<>(Comparator.comparing(Map.Entry::getKey));
//          entries.addAll(updatedStorage.entrySet());
//
//          for (final Map.Entry<UInt256, UInt256> entry : entries) {
//            final UInt256 value = entry.getValue();
//            if (value.isZero()) {
//              storageTrie.remove(entry.getKey());
//            } else {
//              storageTrie.put(entry.getKey(), value);
//            }
          }
        }

        // Lastly, save the new account.
        // TODO we must not allow for arbitrary contract creation. If we do `get` for account and it
        // returns `null` we must halt the execution and revert
//        wrapped.put(
//                updated.getAddress(), updated.getBalance());
      }

      // Commit account state changes
//      wrapped.commit();

      // Clear structures
//      wrapped.updatedStorageTries.clear();
    }
  }

