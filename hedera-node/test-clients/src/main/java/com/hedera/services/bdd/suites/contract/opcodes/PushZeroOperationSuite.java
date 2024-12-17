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
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class PushZeroOperationSuite {
    private static final long GAS_TO_OFFER = 400_000L;
    private static final String CONTRACT = "OpcodesContract";
    private static final String BOB = "bob";
    private static final String OP_PUSH_ZERO = "opPush0";

    @HapiTest
    final Stream<DynamicTest> pushZeroHappyPathWorks() {
        final var pushZeroContract = CONTRACT;
        final var pushResult = "pushResult";
        return hapiTest(
                cryptoCreate(BOB),
                uploadInitCode(pushZeroContract),
                contractCreate(pushZeroContract),
                contractCall(pushZeroContract, OP_PUSH_ZERO)
                        .gas(GAS_TO_OFFER)
                        .payingWith(BOB)
                        .via(pushResult),
                getTxnRecord(pushResult)
                        .hasPriority(recordWith()
                                .contractCallResult(resultWith()
                                        .resultViaFunctionName(
                                                OP_PUSH_ZERO,
                                                pushZeroContract,
                                                isLiteralResult((new Object[] {BigInteger.valueOf(0x5f)}))))));
    }

    @LeakyHapiTest(overrides = {"contracts.evm.version"})
    final Stream<DynamicTest> pushZeroDisabledInV034() {
        final var pushZeroContract = CONTRACT;
        final var pushResult = "pushResult";
        return hapiTest(
                overriding("contracts.evm.version", "v0.34"),
                cryptoCreate(BOB),
                uploadInitCode(pushZeroContract),
                contractCreate(pushZeroContract),
                sourcing(() -> contractCall(pushZeroContract, OP_PUSH_ZERO)
                        .gas(GAS_TO_OFFER)
                        .payingWith(BOB)
                        .via(pushResult)
                        .hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION)
                        .logged()));
    }
}
