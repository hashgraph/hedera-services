/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.context;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.swirlds.common.system.address.AddressBook;
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
    private WeakReference<MerkleMapLike<EntityNum, MerkleTopic>> topics;
    private WeakReference<MerkleMapLike<EntityNum, MerkleToken>> tokens;
    // UniqueTokenMapAdapter is constructed on demand, so a strong reference needs to be held.
    private NonAtomicReference<UniqueTokenMapAdapter> uniqueTokens;
    private NonAtomicReference<RecordsStorageAdapter> payerRecords;
    private WeakReference<MerkleScheduledTransactions> schedules;
    private WeakReference<VirtualMap<VirtualBlobKey, VirtualBlobValue>> storage;
    private WeakReference<VirtualMap<ContractKey, IterableContractValue>> contractStorage;
    private NonAtomicReference<TokenRelStorageAdapter> tokenAssociations;
    private WeakReference<MerkleNetworkContext> networkCtx;
    private WeakReference<AddressBook> addressBook;
    private WeakReference<MerkleSpecialFiles> specialFiles;
    private WeakReference<RecordsRunningHashLeaf> runningHashLeaf;
    private WeakReference<Map<ByteString, EntityNum>> aliases;
    private WeakReference<MerkleMapLike<EntityNum, MerkleStakingInfo>> stakingInfo;
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
    public MerkleMapLike<EntityNum, MerkleTopic> topics() {
        return Objects.requireNonNull(topics.get());
    }

    public long numTopics() {
        return topics().size();
    }

    public void setTopics(final MerkleMapLike<EntityNum, MerkleTopic> topics) {
        this.topics = new WeakReference<>(topics);
    }

    @Override
    public MerkleMapLike<EntityNum, MerkleToken> tokens() {
        return Objects.requireNonNull(tokens.get());
    }

    public long numTokens() {
        return tokens().size();
    }

    public void setTokens(final MerkleMapLike<EntityNum, MerkleToken> tokens) {
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
    public TokenRelStorageAdapter tokenAssociations() {
        return Objects.requireNonNull(tokenAssociations.get());
    }

    public long numTokenRels() {
        return tokenAssociations().size();
    }

    public void setTokenAssociations(final TokenRelStorageAdapter tokenAssociations) {
        this.tokenAssociations = new NonAtomicReference<>(tokenAssociations);
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
    public MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfo() {
        return Objects.requireNonNull(stakingInfo.get());
    }

    public void setStakingInfo(final MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfo) {
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
            final StateChildrenProvider provider, final Instant lowerBoundOnSigningTime) {
        updateFrom(provider);
        signedAt = lowerBoundOnSigningTime;
    }

    public void updateFrom(final StateChildrenProvider provider) {
        if (!provider.isInitialized()) {
            throw new IllegalArgumentException(
                    "State children require an initialized state to update");
        }
        updatePrimitiveChildrenFrom(provider);
    }

    public void updatePrimitiveChildrenFrom(final StateChildrenProvider provider) {
        accounts = new NonAtomicReference<>(provider.accounts());
        topics = new WeakReference<>(provider.topics());
        storage = new WeakReference<>(provider.storage());
        contractStorage = new WeakReference<>(provider.contractStorage());
        tokens = new WeakReference<>(provider.tokens());
        tokenAssociations = new NonAtomicReference<>(provider.tokenAssociations());
        schedules = new WeakReference<>(provider.scheduleTxs());
        networkCtx = new WeakReference<>(provider.networkCtx());
        addressBook = new WeakReference<>(provider.addressBook());
        specialFiles = new WeakReference<>(provider.specialFiles());
        uniqueTokens = new NonAtomicReference<>(provider.uniqueTokens());
        payerRecords = new NonAtomicReference<>(provider.payerRecords());
        runningHashLeaf = new WeakReference<>(provider.runningHashLeaf());
        aliases = new WeakReference<>(provider.aliases());
        stakingInfo = new WeakReference<>(provider.stakingInfo());
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
