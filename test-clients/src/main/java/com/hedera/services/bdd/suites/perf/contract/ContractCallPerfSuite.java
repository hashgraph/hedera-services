/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractCallPerfSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ContractCallPerfSuite.class);

    public static void main(String... args) {
        /* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
        new org.ethereum.crypto.HashUtil();

        new ContractCallPerfSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(contractCallPerf(), recStream6InternalContractCreatesPerf());
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    HapiApiSpec contractCallPerf() {
        final int NUM_CALLS = 1_000;
        final long ENDING_BALANCE = NUM_CALLS * (NUM_CALLS + 1) / 2;
        final String DEPOSIT_MEMO = "So we out-danced thought, body perfection brought...";
        final var verboseDeposit = "VerboseDeposit";
        final var balanceLookup = "BalanceLookup";

        return defaultHapiSpec("ContractCallPerf")
                .given(
                        uploadInitCode(verboseDeposit, balanceLookup),
                        contractCreate(verboseDeposit),
                        contractCreate(balanceLookup).balance(1L))
                .when(
                        getContractInfo(verboseDeposit).hasExpectedInfo().logged(),
                        UtilVerbs.startThroughputObs("contractCall").msToSaturateQueues(50L))
                .then(
                        UtilVerbs.inParallel(
                                asOpArray(
                                        NUM_CALLS,
                                        i ->
                                                contractCall(
                                                                verboseDeposit,
                                                                "deposit",
                                                                i + 1,
                                                                0,
                                                                DEPOSIT_MEMO)
                                                        .sending(i + 1)
                                                        .deferStatusResolution())),
                        UtilVerbs.finishThroughputObs("contractCall")
                                .gatedByQuery(
                                        () ->
                                                contractCallLocal(
                                                                balanceLookup,
                                                                "lookup",
                                                                spec ->
                                                                        new Object[] {
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            verboseDeposit)
                                                                                    .getContractNum()
                                                                        })
                                                        .has(
                                                                resultWith()
                                                                        .resultThruAbi(
                                                                                getABIFor(
                                                                                        FUNCTION,
                                                                                        "lookup",
                                                                                        balanceLookup),
                                                                                isLiteralResult(
                                                                                        new Object
                                                                                                [] {
                                                                                            BigInteger
                                                                                                    .valueOf(
                                                                                                            ENDING_BALANCE)
                                                                                        })))
                                                        .noLogging()));
    }

    /* this spec can be used to perf test the node when
    a large number of contract creates occur in a small period and the node
    has to save to the state a large number of contracts and also create a large
    number of sidecar files ( > 1GB if deploying big contract)
     */
    private HapiApiSpec recStream6InternalContractCreatesPerf() {
        final var contract = "RecStream6Deploy";
        //        final var deploySmallContractFn = "deploySmallContract";
        //        final var deployAvgContractFn = "deployAverageContract";
        final var deployBigContractFn = "deployBigContract";
        final var deploySmallContractRec = "deploySmallContractRec";

        return defaultHapiSpec("recStream6InternalContractCreatesPerf")
                .given(cryptoCreate("payer"), uploadInitCode(contract), contractCreate(contract))
                .when(overriding("contracts.throttle.throttleByGas", "false"))
                .then(
                        UtilVerbs.inParallel(
                                asOpArray(
                                        1000,
                                        i ->
                                                contractCall(contract, deployBigContractFn, 15)
                                                        .via(deploySmallContractRec)
                                                        .gas(2000000)
                                                        .hasKnownStatus(SUCCESS))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
