/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.setup;

import static com.hedera.node.app.service.mono.setup.InfrastructureInitializer.accountIdWith;

import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccountState;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.ContractKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.ContractKeySupplier;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValueSupplier;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.tree.MerkleBinaryTree;
import com.swirlds.merkle.tree.MerkleTreeInternalNode;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import com.swirlds.virtualmap.internal.merkle.VirtualMapState;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.time.Instant;

public class Constructables {

    public static final int ADDEND = 17;
    public static final int MULTIPLIER = 31;
    public static final long SEED = 1_234_567L;
    public static final long PRETEND_FEE = 2L;
    public static final long PRETEND_AMOUNT = 1L;
    public static final long SECS_PER_DAY = 24 * 60 * 60;
    // Midnight of Jan 1, 1971
    public static final Instant SOME_TIME = Instant.ofEpochSecond(31536000L);
    public static final AccountID FUNDING_ID = accountIdWith(98L);
    public static final AccountID STAKING_REWARD_ID = accountIdWith(800L);
    public static final int NUM_REWARDABLE_PERIODS = 365;
    public static final int FIRST_USER_I = 1001;
    public static final int FIRST_NODE_I = 3;

    private Constructables() {
        throw new UnsupportedOperationException();
    }

    public static void registerForAccounts() {
        try {
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleAccountState.class, MerkleAccountState::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(new ClassConstructorPair(FCQueue.class, FCQueue::new));
        } catch (final ConstructableRegistryException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void registerForStakingInfo() {
        try {
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleStakingInfo.class, MerkleStakingInfo::new));
        } catch (final ConstructableRegistryException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void registerForContractStorage() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(ContractKey.class, ContractKey::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(
                                ContractKeySerializer.class, ContractKeySerializer::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(
                                ContractKeySupplier.class, ContractKeySupplier::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(
                                IterableContractValue.class, IterableContractValue::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(
                                IterableContractValueSupplier.class,
                                IterableContractValueSupplier::new));
    }

    public static void registerForMerkleMap() {
        try {
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(MerkleMap.class, MerkleMap::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleBinaryTree.class, MerkleBinaryTree::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleTreeInternalNode.class, MerkleTreeInternalNode::new));
        } catch (final ConstructableRegistryException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void registerForVirtualMap() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(Hash.class, Hash::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(VirtualMap.class, VirtualMap::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(VirtualMapState.class, VirtualMapState::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(VirtualRootNode.class, VirtualRootNode::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(VirtualNodeCache.class, VirtualNodeCache::new));
    }

    public static void registerForJasperDb() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(JasperDbBuilder.class, JasperDbBuilder::new));
    }
}
