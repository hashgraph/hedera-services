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

package com.hedera.services.bdd.suites.contract.opcodes;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
public class PushZeroOperationSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(PushZeroOperationSuite.class);
    private static final long GAS_TO_OFFER = 400_000L;
    private static final String CONTRACT = "OpcodesContract";
    private static final String BOB = "bob";

    private static final String OP_PUSH_ZERO = "opPush0";

    public static final String CONTRACTS_DYNAMIC_EVM_VERSION = "contracts.evm.version.dynamic";
    public static final String CONTRACTS_EVM_VERSION = "contracts.evm.version";

    public static final String EVM_VERSION_0_34 = "v0.34";
    public static final String EVM_VERSION_0_38 = "v0.38";

    public static void main(String... args) {
        new PushZeroOperationSuite().runSuiteSync();
    }

    @Override
    public List<DynamicTest> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<DynamicTest> negativeSpecs() {
        return List.of();
    }

    List<DynamicTest> positiveSpecs() {
        return List.of(pushZeroHappyPathWorks(), pushZeroDisabledInV034());
    }

    @HapiTest
    final DynamicTest pushZeroHappyPathWorks() {
        final var pushZeroContract = CONTRACT;
        final var pushResult = "pushResult";
        return defaultHapiSpec("pushZeroHappyPathWorks", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        overriding(CONTRACTS_DYNAMIC_EVM_VERSION, TRUE_VALUE),
                        overriding(CONTRACTS_EVM_VERSION, EVM_VERSION_0_38),
                        cryptoCreate(BOB),
                        uploadInitCode(pushZeroContract),
                        contractCreate(pushZeroContract))
                .when(sourcing(() -> contractCall(pushZeroContract, OP_PUSH_ZERO)
                        .gas(GAS_TO_OFFER)
                        .payingWith(BOB)
                        .via(pushResult)
                        .logged()))
                .then(getTxnRecord(pushResult)
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .resultViaFunctionName(
                                                OP_PUSH_ZERO,
                                                pushZeroContract,
                                                isLiteralResult((new Object[] {BigInteger.valueOf(0x5f)})))))
                        .logged());
    }

    @HapiTest
    final DynamicTest pushZeroDisabledInV034() {
        final var pushZeroContract = CONTRACT;
        final var pushResult = "pushResult";
        return propertyPreservingHapiSpec("pushZeroDisabledInV034", NONDETERMINISTIC_TRANSACTION_FEES)
                .preserving(CONTRACTS_DYNAMIC_EVM_VERSION, CONTRACTS_EVM_VERSION)
                .given(
                        overriding(CONTRACTS_DYNAMIC_EVM_VERSION, TRUE_VALUE),
                        overriding(CONTRACTS_EVM_VERSION, EVM_VERSION_0_34),
                        cryptoCreate(BOB),
                        uploadInitCode(pushZeroContract),
                        contractCreate(pushZeroContract))
                .when()
                .then(sourcing(() -> contractCall(pushZeroContract, OP_PUSH_ZERO)
                        .gas(GAS_TO_OFFER)
                        .payingWith(BOB)
                        .via(pushResult)
                        .hasKnownStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION)
                        .logged()));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
