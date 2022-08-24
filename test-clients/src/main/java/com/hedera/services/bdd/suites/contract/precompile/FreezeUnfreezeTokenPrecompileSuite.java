/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.contracts.ParsingConstants;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FreezeUnfreezeTokenPrecompileSuite extends HapiApiSuite {
    private static final Logger log =
            LogManager.getLogger(FreezeUnfreezeTokenPrecompileSuite.class);
    private static final String FREEZE_CONTRACT = "FreezeUnfreezeContract";
    private static final String IS_FROZEN_FUNC = "isTokenFrozen";
    private static final String TOKEN_FREEZE_FUNC = "tokenFreeze";
    private static final String TOKEN_UNFREEZE_FUNC = "tokenUnfreeze";
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

    private final AtomicReference<AccountID> accountID = new AtomicReference<>();
    private final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
    private final Object invalidAddress = "0x0000000000000000000000000000000000123456";

    public static void main(String... args) {
        new FreezeUnfreezeTokenPrecompileSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                freezeUnfreezeFungibleWithNegativeCases(),
                freezeUnfreezeNftsWithNegativeCases(),
                isFrozenHappyPathWithLocalCall(),
                noTokenIdReverts());
    }

    private HapiApiSpec noTokenIdReverts() {
        return defaultHapiSpec("noTokenIdReverts")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
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
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_UNFREEZE_FUNC,
                                                                invalidAddress,
                                                                asAddress(accountID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .gas(GAS_TO_OFFER)
                                                        .via("UnfreezeTx")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                cryptoUpdate(ACCOUNT).key(FREEZE_KEY),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_FREEZE_FUNC,
                                                                invalidAddress,
                                                                asAddress(accountID.get()))
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

    private HapiApiSpec freezeUnfreezeFungibleWithNegativeCases() {
        final AtomicReference<TokenID> withoutKeyID = new AtomicReference<>();

        return defaultHapiSpec("freezeUnfreezeFungibleWithNegativeCases")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(ACCOUNT_WITHOUT_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN_WITHOUT_KEY)
                                .exposingCreatedIdTo(id -> withoutKeyID.set(asToken(id))),
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
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_FREEZE_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via(ACCOUNT_HAS_NO_KEY_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_FREEZE_FUNC,
                                                                asAddress(withoutKeyID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via(NO_KEY_FREEZE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_UNFREEZE_FUNC,
                                                                asAddress(withoutKeyID.get()),
                                                                asAddress(accountID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .via(NO_KEY_UNFREEZE_TXN),
                                                cryptoUpdate(ACCOUNT).key(FREEZE_KEY),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_FREEZE_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .gas(GAS_TO_OFFER),
                                                getAccountDetails(ACCOUNT)
                                                        .hasToken(
                                                                ExpectedTokenRel.relationshipWith(
                                                                                VANILLA_TOKEN)
                                                                        .freeze(
                                                                                TokenFreezeStatus
                                                                                        .Frozen)),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_UNFREEZE_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .gas(GAS_TO_OFFER),
                                                getAccountDetails(ACCOUNT)
                                                        .hasToken(
                                                                ExpectedTokenRel.relationshipWith(
                                                                                VANILLA_TOKEN)
                                                                        .freeze(
                                                                                TokenFreezeStatus
                                                                                        .Unfrozen)))))
                .then(
                        childRecordsCheck(
                                ACCOUNT_HAS_NO_KEY_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_SIGNATURE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                INVALID_SIGNATURE)))),
                        childRecordsCheck(
                                NO_KEY_FREEZE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_HAS_NO_FREEZE_KEY)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                TOKEN_HAS_NO_FREEZE_KEY)))),
                        childRecordsCheck(
                                NO_KEY_UNFREEZE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_HAS_NO_FREEZE_KEY)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                TOKEN_HAS_NO_FREEZE_KEY)))));
    }

    private HapiApiSpec freezeUnfreezeNftsWithNegativeCases() {
        return defaultHapiSpec("freezeUnfreezeNftsWithNegativeCases")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set),
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
                        cryptoTransfer(
                                movingUnique(KNOWABLE_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_UNFREEZE_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .gas(GAS_TO_OFFER)
                                                        .via(ACCOUNT_HAS_NO_KEY_TXN)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                cryptoUpdate(ACCOUNT).key(FREEZE_KEY),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_FREEZE_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .gas(GAS_TO_OFFER),
                                                getAccountDetails(ACCOUNT)
                                                        .hasToken(
                                                                ExpectedTokenRel.relationshipWith(
                                                                                KNOWABLE_TOKEN)
                                                                        .freeze(
                                                                                TokenFreezeStatus
                                                                                        .Frozen)),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_UNFREEZE_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .gas(GAS_TO_OFFER),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                IS_FROZEN_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via(IS_FROZEN_TXN)
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                ACCOUNT_HAS_NO_KEY_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_SIGNATURE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                INVALID_SIGNATURE)))),
                        childRecordsCheck(
                                IS_FROZEN_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                ParsingConstants
                                                                                        .FunctionType
                                                                                        .HAPI_IS_FROZEN)
                                                                        .withStatus(SUCCESS)
                                                                        .withIsFrozen(false)))));
    }

    private HapiApiSpec isFrozenHappyPathWithLocalCall() {
        return defaultHapiSpec("isFrozenHappyPathWithLocalCall")
                .given(
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
                .when(
                        assertionsHold(
                                (spec, ctxLog) -> {
                                    final var freezeCall =
                                            contractCall(
                                                            FREEZE_CONTRACT,
                                                            TOKEN_FREEZE_FUNC,
                                                            asAddress(vanillaTokenID.get()),
                                                            asAddress(accountID.get()))
                                                    .logged()
                                                    .payingWith(ACCOUNT)
                                                    .gas(GAS_TO_OFFER);
                                    final var isFrozenLocalCall =
                                            contractCallLocal(
                                                            FREEZE_CONTRACT,
                                                            IS_FROZEN_FUNC,
                                                            asAddress(vanillaTokenID.get()),
                                                            asAddress(accountID.get()))
                                                    .has(
                                                            resultWith()
                                                                    .resultViaFunctionName(
                                                                            IS_FROZEN_FUNC,
                                                                            FREEZE_CONTRACT,
                                                                            isLiteralResult(
                                                                                    new Object[] {
                                                                                        Boolean.TRUE
                                                                                    })));
                                    allRunFor(spec, freezeCall, isFrozenLocalCall);
                                }))
                .then();
    }
}
