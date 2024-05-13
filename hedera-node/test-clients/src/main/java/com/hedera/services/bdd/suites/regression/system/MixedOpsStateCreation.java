/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression.system;

import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToFreeze;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.scheduleOpsEnablement;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.ADMIN_KEY;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.NFT;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.PAYER;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.RECEIVER;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.SENDER;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.SOME_BYTE_CODE;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.SUBMIT_KEY;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.TOPIC;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.TREASURY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class MixedOpsStateCreation extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MixedOpsStateCreation.class);

    private static final int NUM_SUBMISSIONS = 20;
    private static final String KV_CONTRACT = "Create2MultipleCreates";
    private static final String CONTRACT_ADMIN_KEY = "Create2MultipleCreates";
    public static final String GET_BYTECODE = "getBytecode";
    public static final String DEPLOY = "deploy";

    public static void main(String... args) {
        new MixedOpsStateCreation().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(createState());
    }

    @HapiTest
    final Stream<DynamicTest> createState() {
        AtomicInteger tokenId = new AtomicInteger(0);
        AtomicInteger nftId = new AtomicInteger(0);
        AtomicInteger scheduleId = new AtomicInteger(0);
        AtomicInteger contractId = new AtomicInteger(0);
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();
        Random r = new Random(38582L);
        Supplier<HapiSpecOperation[]> mixedOpsBurst =
                new MixedOperations(NUM_SUBMISSIONS).mixedOps(tokenId, nftId, scheduleId, contractId, r);
        return defaultHapiSpec("RestartMixedOps")
                .given(
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        newKeyNamed(SUBMIT_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(ADMIN_KEY),
                        tokenOpsEnablement(),
                        scheduleOpsEnablement(),
                        cryptoCreate(PAYER).balance(100 * ONE_MILLION_HBARS),
                        cryptoCreate(TREASURY).payingWith(PAYER),
                        cryptoCreate(SENDER).payingWith(PAYER),
                        cryptoCreate(RECEIVER).payingWith(PAYER),
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY).payingWith(PAYER),
                        fileCreate(SOME_BYTE_CODE)
                                .path(HapiSpecSetup.getDefaultInstance().defaultContractPath()),
                        // Create a contract with some KV Pairs
                        uploadInitCode(KV_CONTRACT),
                        contractCreate(KV_CONTRACT)
                                .payingWith(GENESIS)
                                .adminKey(CONTRACT_ADMIN_KEY)
                                .entityMemo("Test contract")
                                .gas(10_000_000L)
                                .exposingNumTo(num -> factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num))),
                        sourcing(() -> contractCallLocal(
                                        KV_CONTRACT,
                                        GET_BYTECODE,
                                        asHeadlongAddress(factoryEvmAddress.get()),
                                        BigInteger.valueOf(42))
                                .exposingTypedResultsTo(results -> {
                                    final var tcInitcode = (byte[]) results[0];
                                    testContractInitcode.set(tcInitcode);
                                })
                                .payingWith(GENESIS)
                                .nodePayment(ONE_HBAR)),
                        sourcing(() -> contractCall(
                                        KV_CONTRACT, DEPLOY, testContractInitcode.get(), BigInteger.valueOf(42))
                                .payingWith(GENESIS)
                                .gas(10_000_000L)
                                .sending(1_234L)),
                        inParallel(mixedOpsBurst.get()),
                        sleepFor(10000),
                        inParallel(IntStream.range(0, NUM_SUBMISSIONS)
                                .mapToObj(ignore -> mintToken(
                                                NFT + nftId.getAndDecrement(),
                                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b")))
                                        .logging())
                                .toArray(HapiSpecOperation[]::new)))
                .when(
                        sleepFor(60000),
                        // freeze nodes
                        freezeOnly().startingIn(10).payingWith(GENESIS),
                        // wait for all nodes to be in FREEZE status
                        waitForNodesToFreeze(75).logged())
                .then();
    }
}
