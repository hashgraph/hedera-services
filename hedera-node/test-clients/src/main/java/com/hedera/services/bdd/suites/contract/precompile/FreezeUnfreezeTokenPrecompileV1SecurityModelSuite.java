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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.node.app.hapi.utils.contracts.ParsingConstants;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class FreezeUnfreezeTokenPrecompileV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(FreezeUnfreezeTokenPrecompileV1SecurityModelSuite.class);
    public static final String FREEZE_CONTRACT = "FreezeUnfreezeContract";
    private static final String IS_FROZEN_FUNC = "isTokenFrozen";
    public static final String TOKEN_FREEZE_FUNC = "tokenFreeze";
    public static final String TOKEN_UNFREEZE_FUNC = "tokenUnfreeze";
    private static final String IS_FROZEN_TXN = "isFrozenTxn";
    private static final String ACCOUNT_HAS_NO_KEY_TXN = "accountHasNoFreezeKey";
    private static final String NO_KEY_FREEZE_TXN = "noKeyFreezeTxn";
    private static final String NO_KEY_UNFREEZE_TXN = "noKeyUnfreezeTxn";
    private static final String ACCOUNT = "anybody";
    private static final String ACCOUNT_WITHOUT_KEY = "accountWithoutKey";
    private static final String TOKEN_WITHOUT_KEY = "withoutKey";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String MULTI_KEY = "purpose";
    private static final long GAS_TO_OFFER = 4_000_000L;

    public static void main(String... args) {
        new FreezeUnfreezeTokenPrecompileV1SecurityModelSuite().runSuiteSync();
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
                freezeUnfreezeFungibleWithNegativeCases(),
                freezeUnfreezeNftsWithNegativeCases(),
                isFrozenHappyPathWithLocalCall());
    }

    final Stream<DynamicTest> freezeUnfreezeFungibleWithNegativeCases() {
        final AtomicReference<TokenID> withoutKeyID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        return propertyPreservingHapiSpec("freezeUnfreezeFungibleWithNegativeCases")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenFreezeAccount,TokenUnfreezeAccount",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HBAR).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(ACCOUNT_WITHOUT_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN_WITHOUT_KEY).exposingCreatedIdTo(id -> withoutKeyID.set(asToken(id))),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_FREEZE_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .logged()
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via(ACCOUNT_HAS_NO_KEY_TXN)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_FREEZE_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(withoutKeyID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .logged()
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via(NO_KEY_FREEZE_TXN)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_UNFREEZE_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(withoutKeyID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via(NO_KEY_UNFREEZE_TXN),
                        cryptoUpdate(ACCOUNT).key(FREEZE_KEY),
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_FREEZE_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .logged()
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .gas(GAS_TO_OFFER),
                        getAccountDetails(ACCOUNT)
                                .hasToken(ExpectedTokenRel.relationshipWith(VANILLA_TOKEN)
                                        .freeze(TokenFreezeStatus.Frozen)),
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_UNFREEZE_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .logged()
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .gas(GAS_TO_OFFER),
                        getAccountDetails(ACCOUNT)
                                .hasToken(ExpectedTokenRel.relationshipWith(VANILLA_TOKEN)
                                        .freeze(TokenFreezeStatus.Unfrozen)))))
                .then(
                        childRecordsCheck(
                                ACCOUNT_HAS_NO_KEY_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_SIGNATURE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(INVALID_SIGNATURE)))),
                        childRecordsCheck(
                                NO_KEY_FREEZE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_HAS_NO_FREEZE_KEY)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(TOKEN_HAS_NO_FREEZE_KEY)))),
                        childRecordsCheck(
                                NO_KEY_UNFREEZE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_HAS_NO_FREEZE_KEY)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(TOKEN_HAS_NO_FREEZE_KEY)))));
    }

    final Stream<DynamicTest> freezeUnfreezeNftsWithNegativeCases() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        return propertyPreservingHapiSpec("freezeUnfreezeNftsWithNegativeCases")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenFreezeAccount,TokenUnfreezeAccount",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HBAR).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(KNOWABLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        mintToken(KNOWABLE_TOKEN, List.of(copyFromUtf8("First!"))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, KNOWABLE_TOKEN),
                        cryptoTransfer(movingUnique(KNOWABLE_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_UNFREEZE_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(ACCOUNT_HAS_NO_KEY_TXN)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        cryptoUpdate(ACCOUNT).key(FREEZE_KEY),
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_FREEZE_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .gas(GAS_TO_OFFER),
                        getAccountDetails(ACCOUNT)
                                .hasToken(ExpectedTokenRel.relationshipWith(KNOWABLE_TOKEN)
                                        .freeze(TokenFreezeStatus.Frozen)),
                        contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_UNFREEZE_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .gas(GAS_TO_OFFER),
                        contractCall(
                                        FREEZE_CONTRACT,
                                        IS_FROZEN_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                                .logged()
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via(IS_FROZEN_TXN)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                ACCOUNT_HAS_NO_KEY_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_SIGNATURE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(INVALID_SIGNATURE)))),
                        childRecordsCheck(
                                IS_FROZEN_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(ParsingConstants.FunctionType.HAPI_IS_FROZEN)
                                                        .withStatus(SUCCESS)
                                                        .withIsFrozen(false)))));
    }

    final Stream<DynamicTest> isFrozenHappyPathWithLocalCall() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return propertyPreservingHapiSpec("isFrozenHappyPathWithLocalCall")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenFreezeAccount",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .key(FREEZE_KEY)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(assertionsHold((spec, ctxLog) -> {
                    final var freezeCall = contractCall(
                                    FREEZE_CONTRACT,
                                    TOKEN_FREEZE_FUNC,
                                    HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                    HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                            .logged()
                            .signedBy(GENESIS, ACCOUNT)
                            .alsoSigningWithFullPrefix(ACCOUNT)
                            .gas(GAS_TO_OFFER);
                    final var isFrozenLocalCall = contractCallLocal(
                                    FREEZE_CONTRACT,
                                    IS_FROZEN_FUNC,
                                    HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                    HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())))
                            .has(resultWith()
                                    .resultViaFunctionName(
                                            IS_FROZEN_FUNC, FREEZE_CONTRACT, isLiteralResult(new Object[] {Boolean.TRUE
                                            })));
                    allRunFor(spec, freezeCall, isFrozenLocalCall);
                }))
                .then();
    }
}
