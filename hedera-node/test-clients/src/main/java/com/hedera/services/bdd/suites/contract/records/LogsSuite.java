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

package com.hedera.services.bdd.suites.contract.records;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class LogsSuite extends HapiSuite {

    private static final long GAS_TO_OFFER = 25_000L;

    private static final Logger log = LogManager.getLogger(LogsSuite.class);
    private static final String CONTRACT = "Logs";

    public static void main(String... args) {
        new LogsSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(log0Works(), log1Works(), log2Works(), log3Works(), log4Works());
    }

    @HapiTest
    private HapiSpec log0Works() {
        return defaultHapiSpec("log0Works")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, "log0", BigInteger.valueOf(15))
                        .via("log0")
                        .gas(GAS_TO_OFFER))
                .then(getTxnRecord("log0")
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .logs(inOrder(logWith().noTopics().longValue(15)))
                                        .gasUsed(22_285))));
    }

    @HapiTest
    private HapiSpec log1Works() {
        return defaultHapiSpec("log1Works")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, "log1", BigInteger.valueOf(15))
                        .via("log1")
                        .gas(GAS_TO_OFFER))
                .then(getTxnRecord("log1")
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .logs(inOrder(logWith()
                                                .noData()
                                                .withTopicsInOrder(List.of(
                                                        eventSignatureOf("Log1(uint256)"), parsedToByteString(15)))))
                                        .gasUsed(22_583))));
    }

    @HapiTest
    private HapiSpec log2Works() {
        return defaultHapiSpec("log2Works")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, "log2", BigInteger.ONE, BigInteger.TWO)
                        .gas(GAS_TO_OFFER)
                        .via("log2"))
                .then(getTxnRecord("log2")
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .logs(inOrder(logWith()
                                                .noData()
                                                .withTopicsInOrder(List.of(
                                                        eventSignatureOf("Log2(uint256,uint256)"),
                                                        parsedToByteString(1),
                                                        parsedToByteString(2)))))
                                        .gasUsed(23_112))));
    }

    @HapiTest
    private HapiSpec log3Works() {
        return defaultHapiSpec("log3Works")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, "log3", BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(3))
                        .gas(GAS_TO_OFFER)
                        .via("log3"))
                .then(getTxnRecord("log3")
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .logs(inOrder(logWith()
                                                .noData()
                                                .withTopicsInOrder(List.of(
                                                        eventSignatureOf("Log3(uint256,uint256,uint256)"),
                                                        parsedToByteString(1),
                                                        parsedToByteString(2),
                                                        parsedToByteString(3)))))
                                        .gasUsed(23_638))));
    }

    @HapiTest
    private HapiSpec log4Works() {
        return defaultHapiSpec("log4Works")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(contractCall(
                                CONTRACT,
                                "log4",
                                BigInteger.ONE,
                                BigInteger.TWO,
                                BigInteger.valueOf(3),
                                BigInteger.valueOf(4))
                        .gas(GAS_TO_OFFER)
                        .via("log4"))
                .then(getTxnRecord("log4")
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .logs(inOrder(logWith()
                                                .longValue(4)
                                                .withTopicsInOrder(List.of(
                                                        eventSignatureOf("Log4(uint256,uint256,uint256," + "uint256)"),
                                                        parsedToByteString(1),
                                                        parsedToByteString(2),
                                                        parsedToByteString(3)))))
                                        .gasUsed(24_294))));
    }
}
