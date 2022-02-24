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

import com.hedera.hashgraph.setup.Constructables;
import com.hedera.hashgraph.setup.EvmKeyValueSource;
import com.hedera.hashgraph.setup.InfrastructureInitializer;
import com.hedera.hashgraph.setup.InfrastructureManager;
import com.hedera.hashgraph.setup.KvMutationBatch;
import com.hedera.hashgraph.setup.StorageInfrastructure;
import com.hedera.services.state.virtual.IterableStorageUtils;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.swirlds.common.constructable.ConstructableRegistryException;
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

    // Config for the starting database to load/create
    @Param("10")
    int initContracts;
    @Param("1000")
    int initKvPairs;

    // Config for mutation load profile
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
				IterableStorageUtils::upsertMapping,
				IterableStorageUtils::removeMapping,
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
		Constructables.registerForAccounts();
		Constructables.registerForJasperDb();
		Constructables.registerForMerkleMap();
		Constructables.registerForVirtualMap();
		Constructables.registerForContractStorage();
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
