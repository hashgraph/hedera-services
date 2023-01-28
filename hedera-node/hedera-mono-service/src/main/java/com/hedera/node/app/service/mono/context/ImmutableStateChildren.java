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

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
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
    private final NonAtomicReference<MerkleMapLike<EntityNum, MerkleTopic>> topics;
    private final NonAtomicReference<MerkleMapLike<EntityNum, MerkleToken>> tokens;
    // UniqueTokenMapAdapter is constructed on demand, so a strong reference needs to be held.
    private final NonAtomicReference<UniqueTokenMapAdapter> uniqueTokens;
    private final NonAtomicReference<RecordsStorageAdapter> payerRecords;
    private final NonAtomicReference<MerkleScheduledTransactions> schedules;
    private final NonAtomicReference<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> storage;
    private final NonAtomicReference<VirtualMapLike<ContractKey, IterableContractValue>>
            contractStorage;
    private final NonAtomicReference<TokenRelStorageAdapter> tokenAssociations;
    private final NonAtomicReference<MerkleNetworkContext> networkCtx;
    private final NonAtomicReference<AddressBook> addressBook;
    private final NonAtomicReference<MerkleSpecialFiles> specialFiles;
    private final NonAtomicReference<RecordsRunningHashLeaf> runningHashLeaf;
    private final NonAtomicReference<Map<ByteString, EntityNum>> aliases;
    private final NonAtomicReference<MerkleMapLike<EntityNum, MerkleStakingInfo>> stakingInfo;
    private final Instant signedAt;

    public ImmutableStateChildren(final StateChildrenProvider provider) {
        this.signedAt = provider.getTimeOfLastHandledTxn();

        accounts = new NonAtomicReference<>(provider.accounts());
        topics = new NonAtomicReference<>(provider.topics());
        storage = new NonAtomicReference<>(provider.storage());
        contractStorage = new NonAtomicReference<>(provider.contractStorage());
        tokens = new NonAtomicReference<>(provider.tokens());
        tokenAssociations = new NonAtomicReference<>(provider.tokenAssociations());
        schedules = new NonAtomicReference<>(provider.scheduleTxs());
        networkCtx = new NonAtomicReference<>(provider.networkCtx());
        addressBook = new NonAtomicReference<>(provider.addressBook());
        specialFiles = new NonAtomicReference<>(provider.specialFiles());
        uniqueTokens = new NonAtomicReference<>(provider.uniqueTokens());
        runningHashLeaf = new NonAtomicReference<>(provider.runningHashLeaf());
        aliases = new NonAtomicReference<>(provider.aliases());
        stakingInfo = new NonAtomicReference<>(provider.stakingInfo());
        payerRecords = new NonAtomicReference<>(provider.payerRecords());
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
    public MerkleMapLike<EntityNum, MerkleTopic> topics() {
        return Objects.requireNonNull(topics.get());
    }

    @Override
    public MerkleMapLike<EntityNum, MerkleToken> tokens() {
        return Objects.requireNonNull(tokens.get());
    }

    @Override
    public VirtualMapLike<VirtualBlobKey, VirtualBlobValue> storage() {
        return Objects.requireNonNull(storage.get());
    }

    @Override
    public VirtualMapLike<ContractKey, IterableContractValue> contractStorage() {
        return Objects.requireNonNull(contractStorage.get());
    }

    @Override
    public MerkleScheduledTransactions schedules() {
        return Objects.requireNonNull(schedules.get());
    }

    @Override
    public TokenRelStorageAdapter tokenAssociations() {
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
    public MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfo() {
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
