/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.context;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.RecordsStorageAdapter;
import com.hedera.services.state.migration.UniqueTokenMapAdapter;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NonAtomicReference;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link StateChildren} implementation for providing cheap repeated access to the children of a
 * {@link ServicesState}. (Experience shows that making repeated, indirect calls to {@link
 * com.swirlds.common.merkle.impl.PartialNaryMerkleInternal#getChild(int)} is much more expensive,
 * since the compiler does not seem to ever inline those calls.)
 */
public class MutableStateChildren implements StateChildren {
    private NonAtomicReference<AccountStorageAdapter> accounts;
    private WeakReference<MerkleMap<EntityNum, MerkleTopic>> topics;
    private WeakReference<MerkleMap<EntityNum, MerkleToken>> tokens;
    // UniqueTokenMapAdapter is constructed on demand, so a strong reference needs to be held.
    private NonAtomicReference<UniqueTokenMapAdapter> uniqueTokens;
    private NonAtomicReference<RecordsStorageAdapter> payerRecords;
    private WeakReference<MerkleScheduledTransactions> schedules;
    private WeakReference<VirtualMap<VirtualBlobKey, VirtualBlobValue>> storage;
    private WeakReference<VirtualMap<ContractKey, IterableContractValue>> contractStorage;
    private WeakReference<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenAssociations;
    private WeakReference<MerkleNetworkContext> networkCtx;
    private WeakReference<AddressBook> addressBook;
    private WeakReference<MerkleSpecialFiles> specialFiles;
    private WeakReference<RecordsRunningHashLeaf> runningHashLeaf;
    private WeakReference<Map<ByteString, EntityNum>> aliases;
    private WeakReference<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
    private Instant signedAt = Instant.EPOCH;

    public MutableStateChildren() {
        /* No-op */
    }

    @Override
    public Instant signedAt() {
        return signedAt;
    }

    @Override
    public AccountStorageAdapter accounts() {
        return Objects.requireNonNull(accounts.get());
    }

    public long numAccountAndContracts() {
        return accounts().size();
    }

    public void setAccounts(final AccountStorageAdapter accounts) {
        this.accounts = new NonAtomicReference<>(accounts);
    }

    @Override
    public MerkleMap<EntityNum, MerkleTopic> topics() {
        return Objects.requireNonNull(topics.get());
    }

    public long numTopics() {
        return topics().size();
    }

    public void setTopics(final MerkleMap<EntityNum, MerkleTopic> topics) {
        this.topics = new WeakReference<>(topics);
    }

    @Override
    public MerkleMap<EntityNum, MerkleToken> tokens() {
        return Objects.requireNonNull(tokens.get());
    }

    public long numTokens() {
        return tokens().size();
    }

    public void setTokens(final MerkleMap<EntityNum, MerkleToken> tokens) {
        this.tokens = new WeakReference<>(tokens);
    }

    @Override
    public VirtualMap<VirtualBlobKey, VirtualBlobValue> storage() {
        return Objects.requireNonNull(storage.get());
    }

    public long numBlobs() {
        return storage().size();
    }

    public void setStorage(final VirtualMap<VirtualBlobKey, VirtualBlobValue> storage) {
        this.storage = new WeakReference<>(storage);
    }

    @Override
    public VirtualMap<ContractKey, IterableContractValue> contractStorage() {
        return Objects.requireNonNull(contractStorage.get());
    }

    public long numStorageSlots() {
        return contractStorage().size();
    }

    public void setContractStorage(
            final VirtualMap<ContractKey, IterableContractValue> contractStorage) {
        this.contractStorage = new WeakReference<>(contractStorage);
    }

    @Override
    public MerkleScheduledTransactions schedules() {
        return Objects.requireNonNull(schedules.get());
    }

    public long numSchedules() {
        return schedules().getNumSchedules();
    }

    @Override
    public MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations() {
        return Objects.requireNonNull(tokenAssociations.get());
    }

    public long numTokenRels() {
        return tokenAssociations().size();
    }

    public void setTokenAssociations(
            final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations) {
        this.tokenAssociations = new WeakReference<>(tokenAssociations);
    }

    @Override
    public MerkleNetworkContext networkCtx() {
        return Objects.requireNonNull(networkCtx.get());
    }

    @Override
    public AddressBook addressBook() {
        return Objects.requireNonNull(addressBook.get());
    }

    @Override
    public MerkleSpecialFiles specialFiles() {
        return Objects.requireNonNull(specialFiles.get());
    }

    public void setSpecialFiles(final MerkleSpecialFiles specialFiles) {
        this.specialFiles = new WeakReference<>(specialFiles);
    }

    @Override
    public UniqueTokenMapAdapter uniqueTokens() {
        return Objects.requireNonNull(uniqueTokens.get());
    }

    public long numNfts() {
        return uniqueTokens().size();
    }

    public void setUniqueTokens(final UniqueTokenMapAdapter uniqueTokens) {
        this.uniqueTokens = new NonAtomicReference<>(uniqueTokens);
    }

    @Override
    public RecordsStorageAdapter payerRecords() {
        return Objects.requireNonNull(payerRecords.get());
    }

    public void setPayerRecords(final RecordsStorageAdapter payerRecords) {
        this.payerRecords = new NonAtomicReference<>(payerRecords);
    }

    @Override
    public MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo() {
        return Objects.requireNonNull(stakingInfo.get());
    }

    public void setStakingInfo(final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo) {
        this.stakingInfo = new WeakReference<>(stakingInfo);
    }

    @Override
    public RecordsRunningHashLeaf runningHashLeaf() {
        return Objects.requireNonNull(runningHashLeaf.get());
    }

    @Override
    public Map<ByteString, EntityNum> aliases() {
        return Objects.requireNonNull(aliases.get());
    }

    public void updateFromImmutable(
            final ServicesState signedState, final Instant lowerBoundOnSigningTime) {
        updateFrom(signedState);
        signedAt = lowerBoundOnSigningTime;
    }

    public void updateFrom(final ServicesState state) {
        if (!state.isInitialized()) {
            throw new IllegalArgumentException(
                    "State children require an initialized state to update");
        }
        updatePrimitiveChildrenFrom(state);
    }

    public void updatePrimitiveChildrenFrom(final ServicesState state) {
        accounts = new NonAtomicReference<>(state.accounts());
        topics = new WeakReference<>(state.topics());
        storage = new WeakReference<>(state.storage());
        contractStorage = new WeakReference<>(state.contractStorage());
        tokens = new WeakReference<>(state.tokens());
        tokenAssociations = new WeakReference<>(state.tokenAssociations());
        schedules = new WeakReference<>(state.scheduleTxs());
        networkCtx = new WeakReference<>(state.networkCtx());
        addressBook = new WeakReference<>(state.addressBook());
        specialFiles = new WeakReference<>(state.specialFiles());
        uniqueTokens = new NonAtomicReference<>(state.uniqueTokens());
        payerRecords = new NonAtomicReference<>(state.payerRecords());
        runningHashLeaf = new WeakReference<>(state.runningHashLeaf());
        aliases = new WeakReference<>(state.aliases());
        stakingInfo = new WeakReference<>(state.stakingInfo());
    }

    /* --- used only in unit tests */
    @VisibleForTesting
    public void setNetworkCtx(final MerkleNetworkContext networkCtx) {
        this.networkCtx = new WeakReference<>(networkCtx);
    }

    @VisibleForTesting
    public void setAliases(final Map<ByteString, EntityNum> aliases) {
        this.aliases = new WeakReference<>(aliases);
    }
}
