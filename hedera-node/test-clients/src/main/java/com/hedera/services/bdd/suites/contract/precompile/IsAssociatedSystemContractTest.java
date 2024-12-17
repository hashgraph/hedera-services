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
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.redirectCallResult;
import static com.hedera.services.bdd.spec.dsl.contracts.TokenRedirectContract.HRC;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

/**
 * Asserts expected behavior for the {@code isAuthorized()} HRC token redirect for both
 * contract and EOA {@code msg.sender} types and both fungible and non-fungible token types.
 */
@Tag(SMART_CONTRACT)
@DisplayName("isAssociated")
@SuppressWarnings("java:S1192")
// For readability this test class shares a few accounts between test methods that it repeatedly associates and
// dissociates with tokens, so we can't run concurrently to avoid race conditions; it is very fast regardless
@OrderedInIsolation
public class IsAssociatedSystemContractTest {
    @FungibleToken(name = "fungibleToken")
    static SpecFungibleToken fungibleToken;

    @NonFungibleToken(name = "nonFungibleToken")
    static SpecNonFungibleToken nonFungibleToken;

    @FungibleToken(name = "fungibleTokenForStatic")
    static SpecFungibleToken fungibleTokenForStatic;

    @NonFungibleToken(name = "nonFungibleTokenForStatic")
    static SpecNonFungibleToken nonFungibleTokenForStatic;

    @Contract(contract = "HRCContract", creationGas = 4_000_000L)
    static SpecContract senderContract;

    @Account(name = "senderAccount", tinybarBalance = ONE_HUNDRED_HBARS)
    static SpecAccount senderAccount;

    @Order(0)
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

    @Order(1)
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

    @Order(2)
    @HapiTest
    @DisplayName("returns true for contract msg.sender exactly when associated static call")
    public Stream<DynamicTest> returnsTrueIffContractMsgSenderIsAssociatedStatic() {
        return hapiTest(
                assertContractGetsResultForBothTokensStatic(false),
                senderContract.associateTokens(fungibleTokenForStatic, nonFungibleTokenForStatic),
                assertContractGetsResultForBothTokensStatic(true),
                senderContract.dissociateTokens(fungibleTokenForStatic, nonFungibleTokenForStatic),
                assertContractGetsResultForBothTokensStatic(false));
    }

    @Order(3)
    @HapiTest
    @DisplayName("returns true for EOA msg.sender exactly when associated static call")
    public Stream<DynamicTest> returnsTrueIffEoaMsgSenderIsAssociatedStatic() {
        return hapiTest(
                assertEoaGetsResultForBothTokensStatic(false),
                senderAccount.associateTokens(fungibleTokenForStatic, nonFungibleTokenForStatic),
                assertEoaGetsResultForBothTokensStatic(true),
                senderAccount.dissociateTokens(fungibleTokenForStatic, nonFungibleTokenForStatic),
                assertEoaGetsResultForBothTokensStatic(false));
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
     * Returns an operation asserting the EOA {@code msg.sender} gets an expected {@code isAssociated()}
     * result for both token types.
     * @param isAssociated the expected result
     * @return the operation
     */
    private SpecOperation assertEoaGetsResultForBothTokensStatic(final boolean isAssociated) {
        return blockingOrder(
                fungibleTokenForStatic
                        .staticCall(HRC, "isAssociated")
                        .payingWith(senderAccount)
                        .andAssert(query -> query.has(ContractFnResultAsserts.resultWith()
                                .resultThruAbi(
                                        getABIFor(FUNCTION, "isAssociated", "HRC"),
                                        isLiteralResult(new Object[] {isAssociated})))),
                nonFungibleTokenForStatic
                        .staticCall(HRC, "isAssociated")
                        .payingWith(senderAccount)
                        .andAssert(query -> query.has(ContractFnResultAsserts.resultWith()
                                .resultThruAbi(
                                        getABIFor(FUNCTION, "isAssociated", "HRC"),
                                        isLiteralResult(new Object[] {isAssociated})))));
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

    /**
     * Returns an operation asserting the contract {@code msg.sender} gets an expected {@code isAssociated()}
     * result for both token types.
     * @param isAssociated the expected result
     * @return the operation
     */
    private SpecOperation assertContractGetsResultForBothTokensStatic(final boolean isAssociated) {
        return blockingOrder(
                senderContract
                        .staticCall("isAssociated", fungibleTokenForStatic)
                        .andAssert(query -> query.has(ContractFnResultAsserts.resultWith()
                                .resultThruAbi(
                                        getABIFor(FUNCTION, "isAssociated", "HRC"),
                                        isLiteralResult(new Object[] {isAssociated})))),
                senderContract
                        .staticCall("isAssociated", nonFungibleTokenForStatic)
                        .andAssert(query -> query.has(ContractFnResultAsserts.resultWith()
                                .resultThruAbi(
                                        getABIFor(FUNCTION, "isAssociated", "HRC"),
                                        isLiteralResult(new Object[] {isAssociated})))));
    }
}
