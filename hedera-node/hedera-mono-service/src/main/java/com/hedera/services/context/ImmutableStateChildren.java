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
 * A {@link StateChildren} implementation for providing immutable access to the children of a {@link
 * ServicesState}. (Experience shows that making repeated, indirect calls to {@link
 * com.swirlds.common.merkle.impl.PartialNaryMerkleInternal#getChild(int)} is much more expensive,
 * since the compiler does not seem to ever inline those calls.)
 */
public class ImmutableStateChildren implements StateChildren {
    private final NonAtomicReference<AccountStorageAdapter> accounts;
    private final WeakReference<MerkleMap<EntityNum, MerkleTopic>> topics;
    private final WeakReference<MerkleMap<EntityNum, MerkleToken>> tokens;
    // UniqueTokenMapAdapter is constructed on demand, so a strong reference needs to be held.
    private final NonAtomicReference<UniqueTokenMapAdapter> uniqueTokens;
    private final NonAtomicReference<RecordsStorageAdapter> payerRecords;
    private final WeakReference<MerkleScheduledTransactions> schedules;
    private final WeakReference<VirtualMap<VirtualBlobKey, VirtualBlobValue>> storage;
    private final WeakReference<VirtualMap<ContractKey, IterableContractValue>> contractStorage;
    private final WeakReference<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenAssociations;
    private final WeakReference<MerkleNetworkContext> networkCtx;
    private final WeakReference<AddressBook> addressBook;
    private final WeakReference<MerkleSpecialFiles> specialFiles;
    private final WeakReference<RecordsRunningHashLeaf> runningHashLeaf;
    private final WeakReference<Map<ByteString, EntityNum>> aliases;
    private final WeakReference<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
    private final Instant signedAt;

    public ImmutableStateChildren(final ServicesState state) {
        this.signedAt = state.getTimeOfLastHandledTxn();

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
        runningHashLeaf = new WeakReference<>(state.runningHashLeaf());
        aliases = new WeakReference<>(state.aliases());
        stakingInfo = new WeakReference<>(state.stakingInfo());
        payerRecords = new NonAtomicReference<>(state.payerRecords());
    }

    @Override
    public Instant signedAt() {
        return signedAt;
    }

    @Override
    public AccountStorageAdapter accounts() {
        return Objects.requireNonNull(accounts.get());
    }

    @Override
    public MerkleMap<EntityNum, MerkleTopic> topics() {
        return Objects.requireNonNull(topics.get());
    }

    @Override
    public MerkleMap<EntityNum, MerkleToken> tokens() {
        return Objects.requireNonNull(tokens.get());
    }

    @Override
    public VirtualMap<VirtualBlobKey, VirtualBlobValue> storage() {
        return Objects.requireNonNull(storage.get());
    }

    @Override
    public VirtualMap<ContractKey, IterableContractValue> contractStorage() {
        return Objects.requireNonNull(contractStorage.get());
    }

    @Override
    public MerkleScheduledTransactions schedules() {
        return Objects.requireNonNull(schedules.get());
    }

    @Override
    public MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations() {
        return Objects.requireNonNull(tokenAssociations.get());
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

    @Override
    public UniqueTokenMapAdapter uniqueTokens() {
        return Objects.requireNonNull(uniqueTokens.get());
    }

    @Override
    public RecordsStorageAdapter payerRecords() {
        return Objects.requireNonNull(payerRecords.get());
    }

    @Override
    public MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo() {
        return Objects.requireNonNull(stakingInfo.get());
    }

    @Override
    public RecordsRunningHashLeaf runningHashLeaf() {
        return Objects.requireNonNull(runningHashLeaf.get());
    }

    @Override
    public Map<ByteString, EntityNum> aliases() {
        return Objects.requireNonNull(aliases.get());
    }
}
