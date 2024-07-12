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
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.anyResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.redirectCallResult;
import static com.hedera.services.bdd.spec.dsl.contracts.TokenRedirectContract.HRC;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.annotations.AccountSpec;
import com.hedera.services.bdd.spec.dsl.annotations.ContractSpec;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleTokenSpec;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleTokenSpec;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

/**
 * Asserts expected behavior for the {@code isAuthorized()} HRC token redirect for both
 * contract and EOA {@code msg.sender} types and both fungible and non-fungible token types.
 */
@Tag(SMART_CONTRACT)
@DisplayName("isAssociated")
@SuppressWarnings("java:S1192")
public class IsAssociatedSystemContractTest {
    @FungibleTokenSpec(name = "fungibleToken")
    static SpecFungibleToken fungibleToken;

    @NonFungibleTokenSpec(name = "nonFungibleToken")
    static SpecNonFungibleToken nonFungibleToken;

    @ContractSpec(contract = "HRCContract", creationGas = 4_000_000L)
    static SpecContract senderContract;

    @AccountSpec(name = "senderAccount", balance = ONE_HUNDRED_HBARS)
    static SpecAccount senderAccount;

    @HapiTest
    @DisplayName("returns true for EOA msg.sender exactly when associated")
    public Stream<DynamicTest> returnsTrueIffEoaMsgSenderIsAssociated() {
        return hapiTest(
                assertEoaGetsResultForBothTokens(false),
                senderAccount.associateTokens(fungibleToken, nonFungibleToken),
                assertEoaGetsResultForBothTokens(true),
                senderAccount.dissociateTokens(fungibleToken, nonFungibleToken),
                assertEoaGetsResultForBothTokens(false));
    }

    @HapiTest
    @DisplayName("returns true for contract msg.sender exactly when associated")
    public Stream<DynamicTest> returnsTrueIffContractMsgSenderIsAssociated() {
        return hapiTest(
                assertContractGetsResultForBothTokens(false),
                senderContract.associateTokens(fungibleToken, nonFungibleToken),
                assertContractGetsResultForBothTokens(true),
                senderContract.dissociateTokens(fungibleToken, nonFungibleToken),
                assertContractGetsResultForBothTokens(false));
    }

    /**
     * Returns an operation asserting the EOA {@code msg.sender} gets an expected {@code isAssociated()}
     * result for both token types.
     * @param isAssociated the expected result
     * @return the operation
     */
    private SpecOperation assertEoaGetsResultForBothTokens(final boolean isAssociated) {
        return blockingOrder(
                fungibleToken
                        .call(HRC, "isAssociated")
                        .payingWith(senderAccount)
                        .andAssert(txn ->
                                txn.hasResults(anyResult(), redirectCallResult(HRC, "isAssociated", isAssociated))),
                nonFungibleToken
                        .call(HRC, "isAssociated")
                        .payingWith(senderAccount)
                        .andAssert(txn ->
                                txn.hasResults(anyResult(), redirectCallResult(HRC, "isAssociated", isAssociated))));
    }

    /**
     * Returns an operation asserting the contract {@code msg.sender} gets an expected {@code isAssociated()}
     * result for both token types.
     * @param isAssociated the expected result
     * @return the operation
     */
    private SpecOperation assertContractGetsResultForBothTokens(final boolean isAssociated) {
        return blockingOrder(
                senderContract
                        .call("isAssociated", fungibleToken)
                        .andAssert(txn ->
                                txn.hasResults(anyResult(), redirectCallResult(HRC, "isAssociated", isAssociated))),
                senderContract
                        .call("isAssociated", nonFungibleToken)
                        .andAssert(txn ->
                                txn.hasResults(anyResult(), redirectCallResult(HRC, "isAssociated", isAssociated))));
    }
}
