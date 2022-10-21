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
package com.hedera.services.store.contracts;

import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.setup.InfrastructureManager.loadOrCreateBundle;
import static com.hedera.services.setup.InfrastructureType.ACCOUNTS_LEDGER;
import static com.hedera.services.setup.InfrastructureType.ACCOUNTS_MM;
import static com.hedera.services.setup.InfrastructureType.CONTRACT_STORAGE_VM;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.mocks.MockStorageLimits;
import com.hedera.services.mocks.NoopStorageFeeCharging;
import com.hedera.services.setup.Constructables;
import com.hedera.services.setup.EvmKeyValueSource;
import com.hedera.services.setup.InfrastructureBundle;
import com.hedera.services.setup.InfrastructureType;
import com.hedera.services.setup.KvMutationBatch;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.virtual.IterableStorageUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.constructable.ConstructableRegistryException;
import java.util.List;
import java.util.Map;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 3, time = 30)
public class SizeLimitedStorageBench {
    // Application-level config overrides
    @Param("163840")
    int maxContractKvPairs;

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

    @Param("1000")
    int uniqueMutationsPerIteration;

    @Param("0.25")
    double removalProb;

    private int batchI;
    private KvMutationBatch mutationBatch;
    private InfrastructureBundle bundle;

    private SizeLimitedStorage subject;

    // --- Fixtures ---
    @Setup(Level.Trial)
    public void setupInfrastructure() {
        registerConstructables();
        bundle = loadOrCreateBundle(activeConfig(), requiredInfra());
        subject =
                new SizeLimitedStorage(
                        new NoopStorageFeeCharging(),
                        new MockStorageLimits(),
                        IterableStorageUtils::overwritingUpsertMapping,
                        IterableStorageUtils::removeMapping,
                        bundle.getterFor(ACCOUNTS_MM),
                        bundle.getterFor(CONTRACT_STORAGE_VM));
    }

    @Setup(Level.Iteration)
    public void generateMutationBatch() {
        mutationBatch =
                EvmKeyValueSource.randomMutationBatch(
                        uniqueMutationsPerIteration,
                        maxContractNum,
                        maxContractKvPairs,
                        removalProb);
        batchI = 0;
    }

    // --- Benchmarks ---
    @Benchmark
    public void simulateContractTransaction() {
        final TransactionalLedger<AccountID, AccountProperty, HederaAccount> ledger =
                bundle.get(ACCOUNTS_LEDGER);
        ledger.begin();

        subject.beginSession();
        for (int j = 0;
                j < mutationsPerInvocation;
                j++, batchI = (batchI + 1) % uniqueMutationsPerIteration) {
            final var contractId = mutationBatch.contracts()[batchI];
            if (!ledger.contains(contractId)) {
                ledger.create(contractId);
                ledger.set(contractId, IS_SMART_CONTRACT, true);
            }
            subject.putStorage(
                    contractId, mutationBatch.keys()[batchI], mutationBatch.values()[batchI]);
        }
        subject.validateAndCommit(ledger);
        subject.recordNewKvUsageTo(ledger);

        ledger.commit();
    }

    // --- Helpers ---
    private void registerConstructables() {
        try {
            Constructables.registerForAccounts();
            Constructables.registerForJasperDb();
            Constructables.registerForMerkleMap();
            Constructables.registerForVirtualMap();
            Constructables.registerForContractStorage();
        } catch (ConstructableRegistryException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> activeConfig() {
        return Map.of("initContracts", initContracts, "initKvPairs", initKvPairs);
    }

    private List<InfrastructureType> requiredInfra() {
        return List.of(ACCOUNTS_MM, CONTRACT_STORAGE_VM, ACCOUNTS_LEDGER);
    }
}
