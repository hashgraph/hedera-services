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

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asHexedAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Paused;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Unpaused;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class PauseUnpauseTokenAccountPrecompileV1SecurityModelSuite extends HapiSuite {

    private static final Logger log =
            LogManager.getLogger(PauseUnpauseTokenAccountPrecompileV1SecurityModelSuite.class);
    public static final String PAUSE_UNPAUSE_CONTRACT = "PauseUnpauseTokenAccount";

    private static final String UNPAUSE_KEY = "UNPAUSE_KEY";

    private static final String PAUSE_KEY = "PAUSE_KEY";

    private static final String ACCOUNT = "account";

    public static final long INITIAL_BALANCE = 1_000_000_000L;
    private static final long GAS_TO_OFFER = 4_000_000L;
    public static final String PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME = "pauseTokenAccount";
    public static final String UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME = "unpauseTokenAccount";
    private static final String PAUSE_FUNGIBLE_TXN = "pauseFungibleTxn";
    private static final String UNPAUSE_FUNGIBLE_TXN = "unpauseFungibleTxn";
    private static final String PAUSE_NONFUNGIBLE_TXN = "pauseNonFungibleTxn";
    private static final String UNPAUSE_NONFUNGIBLE_TXN = "unpauseNonFungibleTxn";
    public static final String UNPAUSE_TX = "UnpauseTx";
    public static final String PAUSE_TX = "PauseTx";

    public static void main(String... args) {
        new PauseUnpauseTokenAccountPrecompileV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                pauseFungibleTokenHappyPath(),
                unpauseFungibleTokenHappyPath(),
                pauseNonFungibleTokenHappyPath(),
                unpauseNonFungibleTokenHappyPath());
    }

    final Stream<DynamicTest> pauseFungibleTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return propertyPreservingHapiSpec("PauseFungibleTokenHappyPath")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,TokenCreate,TokenDelete,TokenPause,TokenUnpause",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(INITIAL_BALANCE),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .pauseKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("pauseFungibleAccountDoesNotOwnPauseKeyFailingTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        cryptoUpdate(ACCOUNT).key(MULTI_KEY),
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via(PAUSE_FUNGIBLE_TXN)
                                .gas(GAS_TO_OFFER),
                        getTokenInfo(VANILLA_TOKEN).hasPauseStatus(Paused),
                        tokenUnpause(VANILLA_TOKEN),
                        tokenDelete(VANILLA_TOKEN),
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("pauseFungibleAccountIsDeletedFailingTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        childRecordsCheck(
                                "pauseFungibleAccountDoesNotOwnPauseKeyFailingTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_SIGNATURE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(INVALID_SIGNATURE)))),
                        childRecordsCheck(
                                "pauseFungibleAccountIsDeletedFailingTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_WAS_DELETED)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(TOKEN_WAS_DELETED)))));
    }

    final Stream<DynamicTest> unpauseFungibleTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return propertyPreservingHapiSpec("UnpauseFungibleTokenHappyPath")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,TokenCreate,TokenDelete,TokenPause,TokenUnpause",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(UNPAUSE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(INITIAL_BALANCE),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .pauseKey(UNPAUSE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("unpauseFungibleAccountDoesNotOwnPauseKeyFailingTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        cryptoUpdate(ACCOUNT).key(UNPAUSE_KEY),
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via(UNPAUSE_FUNGIBLE_TXN)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                "unpauseFungibleAccountDoesNotOwnPauseKeyFailingTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_SIGNATURE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(INVALID_SIGNATURE)))),
                        getTokenInfo(VANILLA_TOKEN).hasPauseStatus(Unpaused));
    }

    final Stream<DynamicTest> pauseNonFungibleTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return propertyPreservingHapiSpec("PauseNonFungibleTokenHappyPath")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,TokenCreate,TokenDelete,TokenPause,TokenUnpause",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(PAUSE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(INITIAL_BALANCE),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .pauseKey(PAUSE_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("pauseNonFungibleAccountDoesNotOwnPauseKeyFailingTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        cryptoUpdate(ACCOUNT).key(MULTI_KEY).key(PAUSE_KEY),
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via(PAUSE_NONFUNGIBLE_TXN)
                                .gas(GAS_TO_OFFER),
                        getTokenInfo(VANILLA_TOKEN).hasPauseStatus(Paused),
                        tokenUnpause(VANILLA_TOKEN),
                        tokenDelete(VANILLA_TOKEN),
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("pauseNonFungibleAccountIsDeletedFailingTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        childRecordsCheck(
                                "pauseNonFungibleAccountDoesNotOwnPauseKeyFailingTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_SIGNATURE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(INVALID_SIGNATURE)))),
                        childRecordsCheck(
                                "pauseNonFungibleAccountIsDeletedFailingTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_WAS_DELETED)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(TOKEN_WAS_DELETED)))));
    }

    final Stream<DynamicTest> unpauseNonFungibleTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return propertyPreservingHapiSpec("UnpauseNonFungibleTokenHappyPath")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,TokenCreate,TokenDelete,TokenPause,TokenUnpause",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(UNPAUSE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(INITIAL_BALANCE),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .pauseKey(UNPAUSE_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("unpauseNonFungibleAccountDoesNotOwnPauseKeyFailingTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        cryptoUpdate(ACCOUNT).key(UNPAUSE_KEY),
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via(UNPAUSE_NONFUNGIBLE_TXN)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                "unpauseNonFungibleAccountDoesNotOwnPauseKeyFailingTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_SIGNATURE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(INVALID_SIGNATURE)))),
                        getTokenInfo(VANILLA_TOKEN).hasPauseStatus(Unpaused));
    }
}
