/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.dsl.annotations.ContractSpec;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleTokenSpec;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("isAssociated")
@SuppressWarnings("java:S1192")
@HapiTestLifecycle
public class IsAssociatedSystemContractTest {

    @FungibleTokenSpec(name = "immutableToken")
    static SpecFungibleToken immutableToken;

    @ContractSpec(contract = "HRCContract", creationGas = 4_000_000L)
    static SpecContract hrcContract;

    @HapiTest
    @DisplayName("check token is not associated with an account with static call")
    public Stream<DynamicTest> checkTokenIsNotAssociatedWithAnAccountWithStaticCall() {
        return hapiTest(hrcContract
                .staticCall("isAssociated", immutableToken)
                .andAssert(query -> query.has(ContractFnResultAsserts.resultWith()
                        .resultThruAbi(
                                getABIFor(FUNCTION, "isAssociated", "HRCContract"),
                                isLiteralResult(new Object[] {false})))));
    }

    @HapiTest
    @DisplayName("check token is not associated with an account")
    public Stream<DynamicTest> checkTokenIsNotAssociatedWithAnAccount() {
        return hapiTest(hrcContract.call("isAssociated", immutableToken).andAssert(txn -> txn.hasKnownStatus(SUCCESS)));
    }
}
