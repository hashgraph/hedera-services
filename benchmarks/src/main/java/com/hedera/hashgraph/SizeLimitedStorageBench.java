package com.hedera.hashgraph;

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

import com.hedera.hashgraph.setup.EvmKeyValueSource;
import com.hedera.hashgraph.setup.InfrastructureInitializer;
import com.hedera.hashgraph.setup.InfrastructureManager;
import com.hedera.hashgraph.setup.KvMutationBatch;
import com.hedera.hashgraph.setup.StorageInfrastructure;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractKeySerializer;
import com.hedera.services.state.virtual.ContractKeySupplier;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.ContractValueSupplier;
import com.hedera.services.store.contracts.SizeLimitedStorage;
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
import com.swirlds.virtualmap.internal.merkle.VirtualInternalNode;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import com.swirlds.virtualmap.internal.merkle.VirtualMapState;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;

import static com.hedera.hashgraph.properties.MockDynamicProperties.mockPropertiesWith;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;

@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 1, time = 10)
public class SizeLimitedStorageBench {
	// Application-level config overrides
    @Param("163840")
	int maxContractKvPairs;
    @Param("500000000")
    int maxAggregateKvPairs;

    // Config specifying the initial database to load
    @Param("10")
    int initContracts;
    @Param("1000")
    int initKvPairs;
    @Param("true")
    boolean createInitStorageIfMissing;

    // Config for benchmark mutations
    @Param("20")
    int maxContractNum;
    @Param("3")
    int mutationsPerInvocation;
    @Param("300")
    int uniqueMutationsPerIteration;
    @Param("0.25")
    double removalProb;

    private int batchI;
    private KvMutationBatch mutationBatch;
    private StorageInfrastructure infrastructure;

    private SizeLimitedStorage subject;

    // --- Fixtures ---
    @Setup(Level.Trial)
    public void setupInfrastructure() throws ConstructableRegistryException, IOException {
    	registerConstructables();
    	initializeInfrastructure();

        subject = new SizeLimitedStorage(
                mockPropertiesWith(maxContractKvPairs, maxAggregateKvPairs),
                infrastructure.accounts()::get,
                infrastructure.storage()::get);
    }

	@Setup(Level.Iteration)
	public void generateMutationBatch() {
        mutationBatch = EvmKeyValueSource.randomMutationBatch(
                uniqueMutationsPerIteration, maxContractNum, maxContractKvPairs, removalProb);
        batchI = 0;
    }

    // --- Benchmarks ---
    @Benchmark
    public void simulateContractTransaction() {
    	final var ledger = infrastructure.ledger();
    	ledger.begin();

    	subject.beginSession();
    	for (int j = 0; j < mutationsPerInvocation; j++, batchI = (batchI + 1) % uniqueMutationsPerIteration) {
    	    final var contractId = mutationBatch.contracts()[batchI];
    	    if (!ledger.contains(contractId)) {
    	        ledger.create(contractId);
    	        ledger.set(contractId, IS_SMART_CONTRACT, true);
            }
    		subject.putStorage(contractId, mutationBatch.keys()[batchI], mutationBatch.values()[batchI]);
        }
    	subject.validateAndCommit();
    	subject.recordNewKvUsageTo(ledger);

        ledger.commit();
    }

    // --- Helpers ---
    private void registerConstructables() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccountState.class, MerkleAccountState::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccountTokens.class, MerkleAccountTokens::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ContractKey.class, ContractKey::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ContractKeySerializer.class, ContractKeySerializer::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ContractKeySupplier.class, ContractKeySupplier::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ContractValue.class, ContractValue::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ContractValueSupplier.class, ContractValueSupplier::new));

		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleMap.class, MerkleMap::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleBinaryTree.class, MerkleBinaryTree::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleTreeInternalNode.class, MerkleTreeInternalNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCQueue.class, FCQueue::new));

		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(Hash.class, Hash::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualMap.class, VirtualMap::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualMapState.class, VirtualMapState::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualInternalNode.class, VirtualInternalNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualLeafNode.class, VirtualLeafNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualRootNode.class, VirtualRootNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(VirtualNodeCache.class, VirtualNodeCache::new));

		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(JasperDbBuilder.class, JasperDbBuilder::new));
    }

	private void initializeInfrastructure() throws IOException {
		if (InfrastructureManager.hasSavedInfrastructureWith(initContracts, initKvPairs)) {
			infrastructure = InfrastructureManager.loadInfrastructureWith(initContracts, initKvPairs);
		} else {
			final var storageLoc = InfrastructureManager.storageLocFor(initContracts, initKvPairs);
			infrastructure = InfrastructureManager.newInfrastructureAt(storageLoc);
			final var initializer = new InfrastructureInitializer(initContracts, initKvPairs);
			initializer.setup(infrastructure);
			infrastructure.serializeTo(storageLoc);
		}
	}
}
