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

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode;
import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode.TAKE;
import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CreateAndUpdateOps extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CreateAndUpdateOps.class);

    long feeToOffer = ONE_HUNDRED_HBARS;
    long paymentToOffer = ONE_HBAR;
    long payerBalance = feeToOffer * 1000;
    CostSnapshotMode costSnapshotMode = TAKE;
    //	CostSnapshotMode costSnapshotMode = COMPARE;

    public static void main(String... args) {
        new CreateAndUpdateOps().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            //				variousCryptoMutations(),
            variousFileMutations(),
        });
    }

    HapiSpec variousFileMutations() {
        KeyShape smallWacl = listOf(1);
        KeyShape largeWacl = listOf(3);
        byte[] smallContents = TxnUtils.randomUtf8Bytes(1_024);
        byte[] mediumContents = TxnUtils.randomUtf8Bytes(2_048);
        byte[] largeContents = TxnUtils.randomUtf8Bytes(4_096);
        long shortExpiry = 100_000L;
        long mediumExpiry = 10 * shortExpiry;
        long eternalExpiry = 10 * mediumExpiry;
        AtomicLong consensusNow = new AtomicLong();

        return customHapiSpec("VariousFileMutations")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(
                        newKeyNamed("sk").shape(smallWacl),
                        newKeyNamed("lk").shape(largeWacl),
                        cryptoCreate("payer")
                                .via("payerCreation")
                                .fee(feeToOffer)
                                .balance(payerBalance),
                        withOpContext((spec, opLog) -> {
                            var lookup = getTxnRecord("payerCreation").nodePayment(paymentToOffer);
                            allRunFor(spec, lookup);
                            var record = lookup.getResponseRecord();
                            consensusNow.set(record.getConsensusTimestamp().getSeconds());
                        }))
                .when(
                        sourcing(() -> fileCreate("sksc")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .key("sk")
                                .contents(smallContents)
                                .expiry(consensusNow.get() + shortExpiry)),
                        sourcing(() -> fileCreate("skmc")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .key("sk")
                                .contents(mediumContents)
                                .expiry(consensusNow.get() + mediumExpiry)),
                        sourcing(() -> fileCreate("sklc")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .key("sk")
                                .contents(largeContents)
                                .expiry(consensusNow.get() + eternalExpiry)))
                .then(
                        fileUpdate("sksc")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .wacl("lk")
                                .extendingExpiryBy(mediumExpiry - shortExpiry),
                        fileUpdate("skmc")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .wacl("lk")
                                .extendingExpiryBy(eternalExpiry - mediumExpiry),
                        getFileInfo("sksc"),
                        getFileInfo("skmc"),
                        getFileInfo("sklc"));
    }

    HapiSpec variousCryptoMutations() {
        KeyShape smallKey = SIMPLE;
        KeyShape largeKey = listOf(3);
        long shortExpiry = 100_000L;
        long mediumExpiry = 10 * shortExpiry;
        long eternalExpiry = 10 * mediumExpiry;
        AtomicLong consensusNow = new AtomicLong();

        return customHapiSpec("VariousCryptoMutations")
                .withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
                .given(
                        newKeyNamed("sk").shape(smallKey),
                        newKeyNamed("lk").shape(largeKey),
                        cryptoCreate("payer")
                                .via("payerCreation")
                                .fee(feeToOffer)
                                .balance(payerBalance),
                        withOpContext((spec, opLog) -> {
                            var lookup = getTxnRecord("payerCreation").nodePayment(paymentToOffer);
                            allRunFor(spec, lookup);
                            var record = lookup.getResponseRecord();
                            consensusNow.set(record.getConsensusTimestamp().getSeconds());
                        }),
                        cryptoCreate("proxy").fee(feeToOffer))
                .when(
                        cryptoCreate("sksenp")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .key("sk")
                                .autoRenewSecs(shortExpiry),
                        cryptoCreate("sksep")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .proxy("0.0.2")
                                .key("sk")
                                .autoRenewSecs(shortExpiry),
                        cryptoCreate("skmenp")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .key("sk")
                                .autoRenewSecs(mediumExpiry),
                        cryptoCreate("skmep")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .proxy("0.0.2")
                                .key("sk")
                                .autoRenewSecs(mediumExpiry),
                        cryptoCreate("skeenp")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .key("sk")
                                .autoRenewSecs(eternalExpiry),
                        cryptoCreate("skeep")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .proxy("0.0.2")
                                .key("sk")
                                .autoRenewSecs(eternalExpiry))
                .then(
                        sourcing(() -> cryptoUpdate("sksenp")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .newProxy("proxy")
                                .key("lk")
                                .expiring(consensusNow.get() + mediumExpiry)),
                        sourcing(() -> cryptoUpdate("skmenp")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .newProxy("proxy")
                                .key("lk")
                                .expiring(consensusNow.get() + eternalExpiry)),
                        sourcing(() -> cryptoUpdate("skeenp")
                                .fee(feeToOffer)
                                .payingWith("payer")
                                .newProxy("proxy")
                                .key("lk")),
                        getAccountInfo("sksenp"),
                        getAccountInfo("skmenp"),
                        getAccountInfo("skeenp"));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
