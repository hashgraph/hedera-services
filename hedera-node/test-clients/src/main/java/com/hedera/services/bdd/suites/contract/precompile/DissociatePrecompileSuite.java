/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(SMART_CONTRACT)
public class DissociatePrecompileSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(DissociatePrecompileSuite.class);
    private static final String NEGATIVE_DISSOCIATIONS_CONTRACT = "NegativeDissociationsContract";
    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String ACCOUNT = "anybody";
    private static final String TOKEN = "Token";
    private static final String TOKEN1 = "Token1";
    private static final String CONTRACT_KEY = "ContractKey";
    private static final KeyShape KEY_SHAPE = KeyShape.threshOf(1, ED25519, CONTRACT);

    public static void main(String... args) {
        new DissociatePrecompileSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(dissociateTokensNegativeScenarios(), dissociateTokenNegativeScenarios());
    }

    @HapiTest
    final HapiSpec dissociateTokensNegativeScenarios() {
        final AtomicReference<Address> tokenAddress1 = new AtomicReference<>();
        final AtomicReference<Address> tokenAddress2 = new AtomicReference<>();
        final AtomicReference<Address> accountAddress = new AtomicReference<>();
        final var nonExistingAccount = "nonExistingAccount";
        final var nonExistingTokenArray = "nonExistingTokenArray";
        final var someNonExistingTokenArray = "someNonExistingTokenArray";
        final var zeroAccountAddress = "zeroAccountAddress";
        final var nullTokenArray = "nullTokens";
        final var nonExistingTokensInArray = "nonExistingTokensInArray";
        return defaultHapiSpec("dissociateTokensNegativeScenarios")
                .given(
                        uploadInitCode(NEGATIVE_DISSOCIATIONS_CONTRACT),
                        contractCreate(NEGATIVE_DISSOCIATIONS_CONTRACT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY)
                                .exposingAddressTo(tokenAddress1::set),
                        tokenCreate(TOKEN1)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY)
                                .exposingAddressTo(tokenAddress2::set),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(id -> accountAddress.set(idAsHeadlongAddress(id))),
                        tokenAssociate(ACCOUNT, List.of(TOKEN, TOKEN1)))
                .when(withOpContext((spec, custom) -> allRunFor(
                        spec,
                        contractCall(
                                        NEGATIVE_DISSOCIATIONS_CONTRACT,
                                        "dissociateTokensWithNonExistingAccountAddress",
                                        (Object) new Address[] {tokenAddress1.get(), tokenAddress2.get()})
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nonExistingAccount)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(TOKEN)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(TOKEN1)),
                        newKeyNamed(CONTRACT_KEY)
                                .shape(KEY_SHAPE.signedWith(sigs(ON, NEGATIVE_DISSOCIATIONS_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
                        contractCall(
                                        NEGATIVE_DISSOCIATIONS_CONTRACT,
                                        "dissociateTokensWithEmptyTokensArray",
                                        accountAddress.get())
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER)
                                .signingWith(ACCOUNT)
                                .via(nonExistingTokenArray)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(TOKEN)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(TOKEN1)),
                        contractCall(NEGATIVE_DISSOCIATIONS_CONTRACT, "dissociateTokensWithNullAccount", (Object)
                                        new Address[] {tokenAddress1.get(), tokenAddress2.get()})
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(zeroAccountAddress)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(TOKEN)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(TOKEN1)),
                        contractCall(
                                        NEGATIVE_DISSOCIATIONS_CONTRACT,
                                        "dissociateTokensWithNullTokensArray",
                                        accountAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .signingWith(ACCOUNT)
                                .via(nullTokenArray)
                                .logged(),
                        contractCall(
                                        NEGATIVE_DISSOCIATIONS_CONTRACT,
                                        "dissociateTokensWithNonExistingTokensArray",
                                        accountAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .signingWith(ACCOUNT)
                                .via(nonExistingTokensInArray)
                                .logged(),
                        contractCall(
                                        NEGATIVE_DISSOCIATIONS_CONTRACT,
                                        "dissociateTokensWithTokensArrayWithSomeNonExistingAddresses",
                                        accountAddress.get(),
                                        new Address[] {tokenAddress1.get(), tokenAddress2.get()})
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER)
                                .signingWith(ACCOUNT)
                                .via(someNonExistingTokenArray)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN1))))
                .then(
                        childRecordsCheck(
                                nonExistingAccount,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                nonExistingTokenArray, SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                zeroAccountAddress,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                nullTokenArray,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                nonExistingTokensInArray,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
                        childRecordsCheck(
                                someNonExistingTokenArray, SUCCESS, recordWith().status(SUCCESS)));
    }

    @HapiTest
    final HapiSpec dissociateTokenNegativeScenarios() {
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        final AtomicReference<Address> accountAddress = new AtomicReference<>();
        final var nonExistingAccount = "nonExistingAccount";
        final var nullAccount = "nullAccount";
        final var nonExistingToken = "nonExistingToken";
        final var nullToken = "nullToken";
        return defaultHapiSpec("dissociateTokenNegativeScenarios")
                .given(
                        uploadInitCode(NEGATIVE_DISSOCIATIONS_CONTRACT),
                        contractCreate(NEGATIVE_DISSOCIATIONS_CONTRACT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY)
                                .exposingAddressTo(tokenAddress::set),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(id -> accountAddress.set(idAsHeadlongAddress(id))))
                .when(withOpContext((spec, custom) -> allRunFor(
                        spec,
                        newKeyNamed(CONTRACT_KEY)
                                .shape(KEY_SHAPE.signedWith(sigs(ON, NEGATIVE_DISSOCIATIONS_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
                        contractCall(
                                        NEGATIVE_DISSOCIATIONS_CONTRACT,
                                        "dissociateTokenWithNonExistingAccount",
                                        tokenAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nonExistingAccount)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
                        contractCall(
                                        NEGATIVE_DISSOCIATIONS_CONTRACT,
                                        "dissociateTokenWithNullAccount",
                                        tokenAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nullAccount)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
                        contractCall(
                                        NEGATIVE_DISSOCIATIONS_CONTRACT,
                                        "dissociateTokenWithNonExistingTokenAddress",
                                        accountAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nonExistingToken)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
                        contractCall(
                                        NEGATIVE_DISSOCIATIONS_CONTRACT,
                                        "dissociateTokenWithNullTokenAddress",
                                        accountAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nullToken)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN))))
                .then(
                        childRecordsCheck(
                                nonExistingAccount,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                nullAccount,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                nonExistingToken,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
                        childRecordsCheck(
                                nullToken,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
