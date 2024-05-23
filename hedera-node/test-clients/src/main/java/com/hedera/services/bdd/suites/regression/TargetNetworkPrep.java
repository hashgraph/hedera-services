/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.node.app.hapi.utils.keys.Ed25519Utils.relocatedIfNotPresentInWorkingDir;
import static com.hedera.services.bdd.junit.ContextRequirement.PROPERTY_OVERRIDES;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadDefaultFeeSchedules;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.API_PERMISSIONS;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.NODE_REWARD;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.STAKING_REWARD;
import static com.hedera.services.bdd.suites.records.RecordCreationSuite.STAKING_FEES_NODE_REWARD_PERCENTAGE;
import static com.hedera.services.bdd.suites.records.RecordCreationSuite.STAKING_FEES_STAKING_REWARD_PERCENTAGE;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.StandardSerdes;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class TargetNetworkPrep {
    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> ensureSystemStateAsExpectedWithSystemDefaultFiles() {
        final var emptyKey =
                Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
        final var snapshot800 = "800startBalance";
        final var snapshot801 = "801startBalance";
        final var civilian = "civilian";
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();
        try {
            final var defaultPermissionsLoc = "src/main/resource/api-permission.properties";
            final var stylized121 =
                    Files.readString(relocatedIfNotPresentInWorkingDir(Paths.get(defaultPermissionsLoc)));
            final var serde = StandardSerdes.SYS_FILE_SERDES.get(122L);

            return propertyPreservingHapiSpec("ensureSystemStateAsExpectedWithSystemDefaultFiles")
                    .preserving(STAKING_FEES_NODE_REWARD_PERCENTAGE, STAKING_FEES_STAKING_REWARD_PERCENTAGE)
                    .given(
                            uploadDefaultFeeSchedules(GENESIS),
                            fileUpdate(API_PERMISSIONS)
                                    .payingWith(GENESIS)
                                    .contents(serde.toValidatedRawFile(stylized121, null)),
                            overridingTwo(
                                    STAKING_FEES_NODE_REWARD_PERCENTAGE,
                                    "10",
                                    STAKING_FEES_STAKING_REWARD_PERCENTAGE,
                                    "10"))
                    .when(
                            cryptoCreate(civilian),
                            balanceSnapshot(snapshot800, STAKING_REWARD),
                            cryptoTransfer(tinyBarsFromTo(civilian, STAKING_REWARD, ONE_HBAR))
                                    .payingWith(civilian)
                                    .signedBy(civilian)
                                    .exposingFeesTo(feeObs)
                                    .logged(),
                            sourcing(() -> getAccountBalance(STAKING_REWARD)
                                    .hasTinyBars(changeFromSnapshot(snapshot800, (long) (ONE_HBAR
                                            + ((feeObs.get().networkFee()
                                                            + feeObs.get().serviceFee())
                                                    * 0.1))))),
                            balanceSnapshot(snapshot801, NODE_REWARD),
                            cryptoTransfer(tinyBarsFromTo(civilian, NODE_REWARD, ONE_HBAR))
                                    .payingWith(civilian)
                                    .signedBy(civilian)
                                    .logged(),
                            sourcing(() -> getAccountBalance(NODE_REWARD)
                                    .hasTinyBars(changeFromSnapshot(snapshot801, (long) (ONE_HBAR
                                            + ((feeObs.get().networkFee()
                                                            + feeObs.get().serviceFee())
                                                    * 0.1))))))
                    .then(
                            getAccountDetails(STAKING_REWARD)
                                    .payingWith(GENESIS)
                                    .has(accountDetailsWith()
                                            .expiry(33197904000L, 0)
                                            .key(emptyKey)
                                            .memo("")
                                            .noAlias()
                                            .noAllowances()),
                            getAccountDetails(NODE_REWARD)
                                    .payingWith(GENESIS)
                                    .has(accountDetailsWith()
                                            .expiry(33197904000L, 0)
                                            .key(emptyKey)
                                            .memo("")
                                            .noAlias()
                                            .noAllowances()),
                            withOpContext((spec, opLog) -> {
                                final var genesisInfo = getAccountInfo("0.0.2");
                                allRunFor(spec, genesisInfo);
                                final var key = genesisInfo
                                        .getResponse()
                                        .getCryptoGetInfo()
                                        .getAccountInfo()
                                        .getKey();
                                final var cloneConfirmations = inParallel(IntStream.rangeClosed(200, 750)
                                        .filter(i -> i < 350 || i >= 400)
                                        .mapToObj(i -> getAccountInfo("0.0." + i)
                                                .noLogging()
                                                .payingWith(GENESIS)
                                                .has(AccountInfoAsserts.accountWith()
                                                        .key(key)))
                                        .toArray(HapiSpecOperation[]::new));
                                allRunFor(spec, cloneConfirmations);
                            }));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
