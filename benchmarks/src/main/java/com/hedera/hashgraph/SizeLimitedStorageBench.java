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
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import static com.hedera.hashgraph.properties.MockDynamicProperties.mockPropertiesWith;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;

@State(Scope.Benchmark)
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
    private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

    private SizeLimitedStorage subject;

    // --- Fixtures ---
    @Setup(Level.Trial)
    public void setupInfrastructure() throws ConstructableRegistryException {
    	registerConstructables();

        if (InfrastructureManager.hasSavedStorageWith(initContracts, initKvPairs)) {
           infrastructure = InfrastructureManager.infrastructureWith(initContracts, initKvPairs);
        } else {
            infrastructure = InfrastructureManager.newInfrastructure();
            final var initializer = new InfrastructureInitializer(initContracts, initKvPairs);
            initializer.setup(infrastructure);
            infrastructure.serializeTo();
        }
        ledger = infrastructure.ledger();

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
    }
}
