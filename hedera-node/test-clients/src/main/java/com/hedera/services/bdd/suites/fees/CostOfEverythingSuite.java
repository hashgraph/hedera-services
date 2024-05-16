/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.keys.KeyShape;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class CostOfEverythingSuite {
    private static final String CIVILIAN = "civilian";
    private static final String COST_SNAPSHOT_MODE = "cost.snapshot.mode";

    CostSnapshotMode costSnapshotMode = TAKE;

    @HapiTest
    final Stream<DynamicTest> miscContractCreatesAndCalls() {
        // Note that contracts are prohibited to sending value to system
        // accounts below 0.0.750
        Object[] donationArgs = new Object[] {800L, "Hey, Ma!"};
        final var multipurposeContract = "Multipurpose";
        final var lookupContract = "BalanceLookup";

        return customHapiSpec("MiscContractCreatesAndCalls")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        cryptoCreate(CIVILIAN).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(multipurposeContract, lookupContract))
                .when(
                        contractCreate(multipurposeContract)
                                .payingWith(CIVILIAN)
                                .balance(652),
                        contractCreate(lookupContract).payingWith(CIVILIAN).balance(256))
                .then(
                        contractCall(multipurposeContract, "believeIn", 256L).payingWith(CIVILIAN),
                        contractCallLocal(multipurposeContract, "pick")
                                .payingWith(CIVILIAN)
                                .logged()
                                .has(resultWith()
                                        .resultThruAbi(
                                                getABIFor(FUNCTION, "pick", multipurposeContract),
                                                isLiteralResult(new Object[] {256L}))),
                        contractCall(multipurposeContract, "donate", donationArgs)
                                .payingWith(CIVILIAN),
                        contractCallLocal(lookupContract, "lookup", spec -> new Object[] {
                                    BigInteger.valueOf(spec.registry()
                                            .getAccountID(CIVILIAN)
                                            .getAccountNum())
                                })
                                .payingWith(CIVILIAN)
                                .logged());
    }

    @HapiTest
    final Stream<DynamicTest> cryptoCreateSimpleKey() {
        KeyShape shape = SIMPLE;

        return customHapiSpec("SuccessfulCryptoCreate")
                .withProperties(Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(newKeyNamed("key").shape(shape))
                .when()
                .then(cryptoCreate("a").key("key"));
    }
}
