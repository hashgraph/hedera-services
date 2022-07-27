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
package com.hedera.services.setup;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.setup.InfrastructureType.ACCOUNTS_LEDGER;
import static com.hedera.services.setup.InfrastructureType.ACCOUNTS_MM;
import static com.hedera.services.setup.InfrastructureType.CONTRACT_STORAGE_VM;
import static com.hedera.services.state.virtual.IterableStorageUtils.overwritingUpsertMapping;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.Map;

public class InfrastructureInitializer {
    public static void initializeBundle(
            final Map<String, Object> config, final InfrastructureBundle bundle) {
        if (config.containsKey("initContracts") && config.containsKey("initKvPairs")) {
            initSomeContractStorage(
                    bundle.get(ACCOUNTS_MM),
                    bundle.get(CONTRACT_STORAGE_VM),
                    (int) config.get("initKvPairs"),
                    (int) config.get("initContracts"));
        } else if (config.containsKey("userAccounts")) {
            initSomeAccounts(bundle.get(ACCOUNTS_LEDGER), (int) config.get("userAccounts"));
        }
    }

    private static void initSomeAccounts(
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger,
            final int userAccounts) {
        ledger.begin();
        final var initialBalance = 50_000_000_000L * 100_000_000L / (userAccounts + 1000);
        for (int i = 1; i <= 1000; i++) {
            final var id = AccountID.newBuilder().setAccountNum(i).build();
            ledger.create(id);
            ledger.set(id, BALANCE, initialBalance);
        }
        ledger.commit();

        final var createsPerSession = 100;
        final var perCreationPrint = userAccounts / 10;
        ledger.begin();
        for (int i = 0; i < userAccounts; i++) {
            final var id = AccountID.newBuilder().setAccountNum(1001 + i).build();
            ledger.create(id);
            ledger.set(id, BALANCE, initialBalance);
            final var created = i + 1;
            if (created % createsPerSession == 0) {
                ledger.commit();
                ledger.begin();
            }
            if (created % perCreationPrint == 0) {
                System.out.println("  -> " + created + " user accounts now created");
            }
        }
        if (ledger.isInTransaction()) {
            ledger.commit();
        }
    }

    private static void initSomeContractStorage(
            final MerkleMap<EntityNum, MerkleAccount> accounts,
            final VirtualMap<ContractKey, IterableContractValue> contractStorage,
            final int initKvPairs,
            final int initContracts) {
        // Uniform distribution of K/V pairs across contracts
        final var perContractKvPairs = initKvPairs / initContracts;
        System.out.println("  -> Using " + perContractKvPairs + " K/V pairs-per-contract");

        final var perCreationPrint = initContracts / 10;
        for (int i = 0; i < initContracts; i++) {
            final var contractId = AccountID.newBuilder().setAccountNum(i + 1L).build();
            ContractKey firstKey = null;
            IterableContractValue firstValue = null;
            for (int j = 0; j < perContractKvPairs; j++) {
                final var evmKey = EvmKeyValueSource.uniqueKey(j);
                final var vmKey = ContractKey.from(contractId, evmKey);
                final var vmValue = IterableContractValue.from(evmKey);
                firstKey =
                        overwritingUpsertMapping(
                                vmKey, vmValue, firstKey, firstValue, contractStorage);
                firstValue = vmValue;
            }

            final var contract = new MerkleAccount();
            contract.setSmartContract(true);
            contract.setNumContractKvPairs(perContractKvPairs);
            assert firstKey != null;
            contract.setFirstUint256StorageKey(firstKey.getKey());
            accounts.put(EntityNum.fromAccountId(contractId), contract);

            final var created = i + 1;
            if (created % perCreationPrint == 0) {
                System.out.println(
                        "  -> "
                                + created
                                + " contracts now created ("
                                + (created * perContractKvPairs)
                                + " K/V pairs)");
            }
        }
    }

    private static MerkleAccount accountWith(final long balance) {
        final var account = new MerkleAccount();
        account.setBalanceUnchecked(balance);
        return account;
    }
}
