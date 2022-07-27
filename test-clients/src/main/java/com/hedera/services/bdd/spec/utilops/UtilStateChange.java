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
package com.hedera.services.bdd.spec.utilops;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.suites.HapiApiSuite.DEFAULT_CONTRACT_RECEIVER;
import static com.hedera.services.bdd.suites.HapiApiSuite.DEFAULT_CONTRACT_SENDER;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiApiSuite.SECP_256K1_RECEIVER_SOURCE_KEY;
import static com.hedera.services.bdd.suites.HapiApiSuite.SECP_256K1_SOURCE_KEY;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.assertions.StorageChange;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.utilops.inventory.NewSpecKey;
import com.hedera.services.stream.proto.ContractStateChange;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UtilStateChange {
    static final Logger log = LogManager.getLogger(UtilStateChange.class);

    public static final KeyShape secp256k1Shape = KeyShape.SECP256K1;
    private static final Map<String, Boolean> specToInitializedEthereumAccount = new HashMap<>();
    private static final Map<String, Boolean> specToBeenExecuted = new HashMap<>();

    public static List<ContractStateChange> stateChangesToGrpc(
            List<StateChange> stateChanges, HapiApiSpec spec) {
        final List<ContractStateChange> additions = new ArrayList<>();

        for (StateChange stateChange : stateChanges) {
            final var addition =
                    ContractStateChange.newBuilder()
                            .setContractId(
                                    TxnUtils.asContractId(stateChange.getContractID(), spec));

            for (StorageChange storageChange : stateChange.getStorageChanges()) {
                var newStorageChange =
                        com.hedera.services.stream.proto.StorageChange.newBuilder()
                                .setSlot(storageChange.getSlot())
                                .setValueRead(storageChange.getValueRead());

                if (storageChange.getValueWritten() != null) {
                    newStorageChange.setValueWritten(storageChange.getValueWritten());
                }

                addition.addStorageChanges(newStorageChange.build());
            }

            additions.add(addition.build());
        }

        return additions;
    }

    public static void initializeEthereumAccountForSpec(final HapiApiSpec spec) {
        createEthereumAccount(
                spec, SECP_256K1_SOURCE_KEY, DEFAULT_CONTRACT_SENDER, "senderCreation");
        createEthereumAccount(
                spec,
                SECP_256K1_RECEIVER_SOURCE_KEY,
                DEFAULT_CONTRACT_RECEIVER,
                "receiverCreation");
        specToInitializedEthereumAccount.putIfAbsent(spec.getSuitePrefix() + spec.getName(), true);
    }

    private static void createEthereumAccount(
            final HapiApiSpec spec,
            final String secp256k1Key,
            final String accountName,
            final String txnName) {
        final var newSpecKey = new NewSpecKey(secp256k1Key).shape(secp256k1Shape);
        final var cryptoTransfer =
                new HapiCryptoTransfer(
                                tinyBarsFromAccountToAlias(
                                        GENESIS, secp256k1Key, 20 * ONE_MILLION_HBARS))
                        .via(txnName);
        final var idLookup = getTxnRecord(txnName).andAllChildRecords().assertingNothing();

        newSpecKey.execFor(spec);
        cryptoTransfer.execFor(spec);
        idLookup.execFor(spec);

        final var children = idLookup.getChildRecords();
        for (int i = 0, n = children.size(); i < n; i++) {
            final var childRecord = idLookup.getChildRecord(i);
            final var autoCreatedId = childRecord.getReceipt().getAccountID();
            if (autoCreatedId.getAccountNum() > 1000L) {
                log.info("Auto-created {} 0.0.{}", accountName, autoCreatedId.getAccountNum());
                final var registry = spec.registry();
                registry.saveAccountId(accountName, autoCreatedId);
                registry.saveKey(accountName, registry.getKey(secp256k1Key));
                break;
            }
        }
    }

    public static boolean isEthereumAccountCreatedForSpec(final HapiApiSpec spec) {
        return specToInitializedEthereumAccount.containsKey(spec.getSuitePrefix() + spec.getName());
    }

    public static void markSpecAsBeenExecuted(final HapiApiSpec spec) {
        specToBeenExecuted.putIfAbsent(spec.getSuitePrefix() + spec.getName(), true);
    }
}
