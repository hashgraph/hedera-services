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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.regression.factories.IdFuzzingProviderFactory.FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.regression.factories.IdFuzzingProviderFactory.NON_FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
public class FreezeUnfreezeTokenPrecompileSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(FreezeUnfreezeTokenPrecompileSuite.class);
    public static final String FREEZE_CONTRACT = "FreezeUnfreezeContract";
    private static final String IS_FROZEN_FUNC = "isTokenFrozen";
    public static final String TOKEN_FREEZE_FUNC = "tokenFreeze";
    public static final String TOKEN_UNFREEZE_FUNC = "tokenUnfreeze";
    private static final String ACCOUNT = "anybody";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String MULTI_KEY = "purpose";
    private static final String TREASURY = "treasury";
    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";
    public static final String PARTY = "party";
    public static final String TOKEN = "token";

    public static void main(String... args) {
        new FreezeUnfreezeTokenPrecompileSuite().runSuiteAsync();
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
                isFrozenHappyPathWithAliasLocalCall(),
                noTokenIdReverts(),
                createFungibleTokenFreezeKeyFromHollowAccountAlias(),
                createNFTTokenFreezeKeyFromHollowAccountAlias());
    }

    @HapiTest
    final HapiSpec noTokenIdReverts() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        return defaultHapiSpec("noTokenIdReverts", NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HBAR).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_UNFREEZE_FUNC,
                                        asHeadlongAddress(INVALID_ADDRESS),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via("UnfreezeTx")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        cryptoUpdate(ACCOUNT).key(FREEZE_KEY),
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_FREEZE_FUNC,
                                        asHeadlongAddress(INVALID_ADDRESS),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via("FreezeTx"))))
                .then(
                        childRecordsCheck(
                                "UnfreezeTx",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                "FreezeTx",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)));
    }

    @HapiTest
    final HapiSpec isFrozenHappyPathWithAliasLocalCall() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<String> autoCreatedAccountId = new AtomicReference<>();
        final String accountAlias = "accountAlias";
        final var notAnAddress = new byte[20];

        return defaultHapiSpec("isFrozenHappyPathWithAliasLocalCall", NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(accountAlias).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, accountAlias, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getAliasedAccountInfo(accountAlias).exposingContractAccountIdTo(autoCreatedAccountId::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCallLocal(
                                        FREEZE_CONTRACT,
                                        IS_FROZEN_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(autoCreatedAccountId.get()))))
                        //                ,contractCall(
                        //                        FREEZE_CONTRACT,
                        //                                IS_FROZEN_FUNC,
                        //                                HapiParserUtil.asHeadlongAddress(notAnAddress),
                        //                                HapiParserUtil.asHeadlongAddress(notAnAddress))
                        //                                .payingWith(GENESIS)
                        //                                .gas(GAS_TO_OFFER)
                        //                                .via("fakeAddressIsFrozen")
                        //                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)

                        )
                .then(
                        //                        withOpContext(((spec, assertLog) -> allRunFor(
                        //                                spec,
                        //                                childRecordsCheck(
                        //                                        "fakeAddressIsFrozen",
                        //                                        CONTRACT_REVERT_EXECUTED,
                        //                                        recordWith()
                        //                                                .status(INVALID_TOKEN_ID)
                        //                                                .contractCallResult(resultWith()
                        //
                        // .contractCallResult(htsPrecompileResult()
                        //
                        // .forFunction(FunctionType.HAPI_IS_FROZEN)
                        //                                                                .withStatus(INVALID_TOKEN_ID)
                        //                                                                .withIsFrozen(false)))))))
                        );
    }

    @HapiTest
    public HapiSpec createFungibleTokenFreezeKeyFromHollowAccountAlias() {
        return defaultHapiSpec("CreateFungibleTokenFreezeKeyFromHollowAccountAlias")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS * 5000L).maxAutomaticTokenAssociations(2),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        cryptoCreate(TREASURY).balance(ONE_MILLION_HBARS * 5000L))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    spec.registry()
                            .saveAccountAlias(
                                    SECP_256K1_SOURCE_KEY,
                                    AccountID.newBuilder().setAlias(evmAddress).build());

                    allRunFor(
                            spec,
                            // Transfer money to the alias --> creates HOLLOW ACCOUNT
                            cryptoTransfer(movingHbar(ONE_HUNDRED_HBARS).distributing(TREASURY, SECP_256K1_SOURCE_KEY))
                                    .logged(),
                            // Verify that the account is created and is hollow
                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                    .has(accountWith().hasEmptyKey())
                                    .logged(),
                            // Create a token with the ECDSA alias key as FREEZE key
                            tokenCreate(FUNGIBLE_TOKEN)
                                    .tokenType(FUNGIBLE_COMMON)
                                    .freezeKey(SECP_256K1_SOURCE_KEY)
                                    .initialSupply(100L)
                                    .treasury(TREASURY)
                                    .logged(),
                            // Transfer the created token to a completed account and auto associate it
                            cryptoTransfer(moving(1L, FUNGIBLE_TOKEN).between(TREASURY, ACCOUNT)));
                }))
                .then(withOpContext((spec, opLog) -> {
                    allRunFor(
                            spec,
                            // Freeze the token using the ECDSA key
                            tokenFreeze(FUNGIBLE_TOKEN, ACCOUNT)
                                    .signedBy(ACCOUNT, SECP_256K1_SOURCE_KEY)
                                    .payingWith(ACCOUNT)
                                    .logged(),
                            contractCall(
                                            FREEZE_CONTRACT,
                                            IS_FROZEN_FUNC,
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(ACCOUNT))))
                                    .via("isTokenFrozenTx"),
                            childRecordsCheck(
                                    "isTokenFrozenTx",
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_IS_FROZEN)
                                                            .withIsFrozen(true)
                                                            .withStatus(SUCCESS)))),
                            // Unfreeze the token using the ECDSA key
                            tokenUnfreeze(FUNGIBLE_TOKEN, ACCOUNT)
                                    .signedBy(ACCOUNT, SECP_256K1_SOURCE_KEY)
                                    .payingWith(ACCOUNT)
                                    .logged(),
                            contractCall(
                                            FREEZE_CONTRACT,
                                            IS_FROZEN_FUNC,
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(ACCOUNT))))
                                    .via("isTokenUnfrozenTx"),
                            childRecordsCheck(
                                    "isTokenUnfrozenTx",
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_IS_FROZEN)
                                                            .withIsFrozen(false)
                                                            .withStatus(SUCCESS)))));
                }));
    }

    @HapiTest
    public HapiSpec createNFTTokenFreezeKeyFromHollowAccountAlias() {
        return defaultHapiSpec("CreateNFTTokenFreezeKeyFromHollowAccountAlias")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS * 5000L),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        cryptoCreate(TREASURY).balance(ONE_MILLION_HBARS * 5000L))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    spec.registry()
                            .saveAccountAlias(
                                    SECP_256K1_SOURCE_KEY,
                                    AccountID.newBuilder().setAlias(evmAddress).build());

                    allRunFor(
                            spec,
                            // Transfer money to the alias --> creates HOLLOW ACCOUNT
                            cryptoTransfer(movingHbar(ONE_HUNDRED_HBARS).distributing(TREASURY, SECP_256K1_SOURCE_KEY))
                                    .logged(),
                            // Verify that the account is created and is hollow
                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                    .has(accountWith().hasEmptyKey())
                                    .logged(),
                            // Create a token with the ECDSA alias key as FREEZE key
                            tokenCreate(NON_FUNGIBLE_TOKEN)
                                    .tokenType(NON_FUNGIBLE_UNIQUE)
                                    .freezeKey(SECP_256K1_SOURCE_KEY)
                                    .supplyKey(SECP_256K1_SOURCE_KEY)
                                    .initialSupply(0L)
                                    .treasury(TREASURY)
                                    .logged(),
                            // Mint the NFT
                            mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("metadata1")))
                                    .signedBy(ACCOUNT, SECP_256K1_SOURCE_KEY)
                                    .payingWith(ACCOUNT)
                                    .logged(),
                            // Associate the token to the completed account
                            tokenAssociate(ACCOUNT, NON_FUNGIBLE_TOKEN));
                }))
                .then(withOpContext((spec, opLog) -> {
                    allRunFor(
                            spec,
                            // Freeze the token using the ECDSA key
                            tokenFreeze(NON_FUNGIBLE_TOKEN, ACCOUNT)
                                    .signedBy(ACCOUNT, SECP_256K1_SOURCE_KEY)
                                    .payingWith(ACCOUNT)
                                    .logged(),
                            contractCall(
                                            FREEZE_CONTRACT,
                                            IS_FROZEN_FUNC,
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(ACCOUNT))))
                                    .via("isTokenFrozenTx"),
                            childRecordsCheck(
                                    "isTokenFrozenTx",
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_IS_FROZEN)
                                                            .withIsFrozen(true)
                                                            .withStatus(SUCCESS)))),
                            // Unfreeze the token using the ECDSA key
                            tokenUnfreeze(NON_FUNGIBLE_TOKEN, ACCOUNT)
                                    .signedBy(ACCOUNT, SECP_256K1_SOURCE_KEY)
                                    .payingWith(ACCOUNT)
                                    .logged(),
                            contractCall(
                                            FREEZE_CONTRACT,
                                            IS_FROZEN_FUNC,
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(ACCOUNT))))
                                    .via("isTokenUnfrozenTx"),
                            childRecordsCheck(
                                    "isTokenUnfrozenTx",
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_IS_FROZEN)
                                                            .withIsFrozen(false)
                                                            .withStatus(SUCCESS)))));
                }));
    }
}
