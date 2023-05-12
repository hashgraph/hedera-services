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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asHexedAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Paused;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Unpaused;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PauseUnpauseTokenAccountPrecompileSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PauseUnpauseTokenAccountPrecompileSuite.class);
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
    private static final String INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";
    public static final String UNPAUSE_TX = "UnpauseTx";
    public static final String PAUSE_TX = "PauseTx";

    public static void main(String... args) {
        new PauseUnpauseTokenAccountPrecompileSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                pauseFungibleTokenHappyPath(),
                unpauseFungibleTokenHappyPath(),
                pauseNonFungibleTokenHappyPath(),
                unpauseNonFungibleTokenHappyPath(),
                noTokenIdReverts(),
                noAccountKeyReverts());
    }

    private HapiSpec noTokenIdReverts() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return defaultHapiSpec("noTokenIdReverts")
                .given(
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
                                        asHeadlongAddress(INVALID_ADDRESS))
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(PAUSE_TX)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(INVALID_ADDRESS))
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(UNPAUSE_TX))))
                .then(
                        childRecordsCheck(
                                PAUSE_TX, CONTRACT_REVERT_EXECUTED, recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                UNPAUSE_TX,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)));
    }

    private HapiSpec noAccountKeyReverts() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return defaultHapiSpec("noKeyReverts")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HBAR).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(MULTI_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via(PAUSE_TX),
                        contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via(UNPAUSE_TX))))
                .then(
                        childRecordsCheck(
                                PAUSE_TX,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_HAS_NO_PAUSE_KEY)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(TOKEN_HAS_NO_PAUSE_KEY)))),
                        childRecordsCheck(
                                UNPAUSE_TX,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_HAS_NO_PAUSE_KEY)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(TOKEN_HAS_NO_PAUSE_KEY)))));
    }

    HapiSpec pauseFungibleTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return defaultHapiSpec("PauseFungibleTokenHappyPath")
                .given(
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

    HapiSpec unpauseFungibleTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return defaultHapiSpec("UnpauseFungibleTokenHappyPath")
                .given(
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

    HapiSpec pauseNonFungibleTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return defaultHapiSpec("PauseNonFungibleTokenHappyPath")
                .given(
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

    HapiSpec unpauseNonFungibleTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return defaultHapiSpec("UnpauseNonFungibleTokenHappyPath")
                .given(
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
