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
package com.hedera.services.ledger;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.setup.InfrastructureManager.loadOrCreateBundle;
import static com.hedera.services.setup.InfrastructureType.ACCOUNTS_LEDGER;

import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.setup.Constructables;
import com.hedera.services.setup.InfrastructureBundle;
import com.hedera.services.setup.InfrastructureType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
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
public class SimpleTransfersBench {
    private static final int NUM_NODES = 39;
    private static final int FIRST_NODE_I = 3;

    @Param("100000")
    int userAccounts;

    @Param("10000")
    int transfersPerRound;

    private int i;
    private int n;
    private AccountID[] ids;
    private InfrastructureBundle bundle;
    private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

    // --- Fixtures ---
    @Setup(Level.Trial)
    public void setupInfrastructure() {
        Constructables.registerForAccounts();
        Constructables.registerForMerkleMap();
        bundle = loadOrCreateBundle(activeConfig(), requiredInfra());
        ledger = bundle.get(ACCOUNTS_LEDGER);
        ids = new AccountID[userAccounts + 1001];
        for (int j = 1; j < userAccounts + 1001; j++) {
            ids[j] = AccountID.newBuilder().setAccountNum(j).build();
        }
        i = n = 0;
    }

    @Setup(Level.Invocation)
    public void simulateRoundBoundary() {
        if (n > 0 && n % transfersPerRound == 0) {
            bundle.newRound();
        }
    }

    // --- Benchmarks ---
    @Benchmark
    public void simpleTransfers() {
        i = i * Constructables.MULTIPLIER + Constructables.ADDEND;
        final var nodeId = ids[FIRST_NODE_I + Math.floorMod(i, NUM_NODES)];
        i = i * Constructables.MULTIPLIER + Constructables.ADDEND;
        final var senderId = ids[Constructables.FIRST_USER_I + Math.floorMod(i, userAccounts)];
        i = i * Constructables.MULTIPLIER + Constructables.ADDEND;
        final var receiverId = ids[Constructables.FIRST_USER_I + Math.floorMod(i, userAccounts)];

        ledger.begin();
        ledger.set(
                Constructables.FUNDING_ID,
                BALANCE,
                (long) ledger.get(Constructables.FUNDING_ID, BALANCE) + 69_000);
        ledger.set(nodeId, BALANCE, (long) ledger.get(nodeId, BALANCE) + 420);
        ledger.set(senderId, BALANCE, (long) ledger.get(senderId, BALANCE) - 69_421);
        ledger.set(receiverId, BALANCE, (long) ledger.get(receiverId, BALANCE) + 1);
        ledger.commit();

        n++;
    }

    // --- Helpers ---
    private Map<String, Object> activeConfig() {
        return Map.of("userAccounts", userAccounts);
    }

    private List<InfrastructureType> requiredInfra() {
        return List.of(ACCOUNTS_LEDGER);
    }
}
