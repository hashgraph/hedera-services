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

package com.hedera.services.bdd.suites.perf.contract;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getExecTime;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.google.common.io.Files;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractPerformanceSuite extends HapiSuite {
    private static final Logger LOG = LogManager.getLogger(ContractPerformanceSuite.class);

    private static final String PERF_RESOURCES = "src/main/resource/contract/performance/";
    private static final String LOADER_TEMPLATE = "608060405234801561001057600080fd5b5061%04x806100206000396000f3fe%s";
    private static final String RETURN_PROGRAM = "3360005260206000F3";
    private static final String REVERT_PROGRAM = "6055605555604360A052600160A0FD";

    private static final String EXTERNAL_CONTRACT_MARKER = "7465737420636f6e7472616374";
    private static final String RETURN_CONTRACT = "returnContract";
    private static final String RETURN_CONTRACT_ADDRESS = "72657475726e207465737420636f6e7472616374";
    private static final String REVERT_CONTRACT = "revertContract";
    private static final String REVERT_CONTRACT_ADDRESS = "726576657274207465737420636f6e7472616374";

    public static void main(String... args) {
        new ContractPerformanceSuite().runSuiteAsync();
    }

    static HapiFileCreate createProgramFile(String name, String program) {
        return fileCreate(name).contents(String.format(LOADER_TEMPLATE, program.length(), program));
    }

    static HapiFileCreate createTestProgram(
            String test, ContractID returnAccountAddress, ContractID revertAccountAddress) {
        String path = PERF_RESOURCES + test;
        try {
            var contentString = new String(Files.toByteArray(new File(path)), StandardCharsets.US_ASCII)
                    .replace(RETURN_CONTRACT_ADDRESS, asSolidityAddress(returnAccountAddress))
                    .replace(REVERT_CONTRACT_ADDRESS, asSolidityAddress(revertAccountAddress));
            return fileCreate(test + "bytecode").contents(contentString.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            LOG.warn("createTestProgram for " + test + " failed to read bytes from '" + path + "'!", e);
            return fileCreate(test);
        }
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        List<String> perfTests;
        try {
            perfTests =
                    Files.readLines(new File(PERF_RESOURCES + "performanceContracts.csv"), Charset.defaultCharset())
                            .stream()
                            .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                            .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
        List<HapiSpec> hapiSpecs = new ArrayList<>();
        for (String line : perfTests) {
            String[] values = line.split(",", 2);
            String test = values[0];
            long gasCost = Long.parseLong(values[1]);
            String path = PERF_RESOURCES + test;
            String via = test.substring(0, test.length() - 4);
            String contractCode;
            try {
                contractCode = new String(Files.toByteArray(new File(path)), StandardCharsets.US_ASCII);
            } catch (IOException e) {
                LOG.warn("createTestProgram for " + test + " failed to read bytes from '" + path + "'!", e);
                contractCode = "FE";
            }
            HapiSpecOperation[] givenBlock;
            if (contractCode.contains(EXTERNAL_CONTRACT_MARKER)) {
                givenBlock = new HapiSpecOperation[] {
                    createProgramFile(RETURN_CONTRACT + "bytecode", RETURN_PROGRAM),
                    contractCreate(RETURN_CONTRACT).bytecode(RETURN_CONTRACT + "bytecode"),
                    createProgramFile(REVERT_CONTRACT + "bytecode", REVERT_PROGRAM),
                    contractCreate(REVERT_CONTRACT).bytecode(REVERT_CONTRACT + "bytecode"),
                    withOpContext((spec, opLog) -> allRunFor(
                            spec,
                            createTestProgram(
                                    test,
                                    spec.registry().getContractId(RETURN_CONTRACT),
                                    spec.registry().getContractId(REVERT_CONTRACT)))),
                    contractCreate(test).bytecode(test + "bytecode")
                };
            } else {
                givenBlock = new HapiSpecOperation[] {
                    fileCreate("bytecode").path(PERF_RESOURCES + test),
                    contractCreate(test).bytecode("bytecode")
                };
            }
            hapiSpecs.add(defaultHapiSpec("Perf_" + test)
                    .given(givenBlock)
                    .when(contractCall(test, "<empty>").gas(35000000).via(via))
                    .then(
                            getExecTime(via)
                                    .payingWith(GENESIS)
                                    .logged()
                                    .assertingNoneLongerThan(20, ChronoUnit.SECONDS),
                            getReceipt(via).hasPriorityStatus(ResponseCodeEnum.SUCCESS),
                            getTxnRecord(via)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith().gasUsed(gasCost)))));
        }
        return hapiSpecs;
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    public static String asSolidityAddress(final ContractID id) {
        return hex(HapiPropertySource.asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getContractNum()));
    }
}
