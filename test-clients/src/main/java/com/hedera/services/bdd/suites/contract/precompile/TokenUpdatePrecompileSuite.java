/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenUpdatePrecompileSuite extends HapiApiSuite {

    private static final Logger log = LogManager.getLogger(TokenUpdatePrecompileSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long AUTO_RENEW_PERIOD = 8_000_000L;
    private static final String ACCOUNT = "account";
    private static final String TOKEN_UPDATE_CONTRACT = "UpdateTokenInfoContract";
    private static final String UPDATE_TXN = "updateTxn";
    private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    private static final String ED25519KEY = "ed25519key";
    private static final String ECDSA_KEY = "ecdsa";
    private static final String TOKEN_UPDATE_AS_KEY = "tokenCreateContractAsKey";
    private static final String DELEGATE_KEY = "tokenUpdateAsKeyDelegate";
    private static final String ACCOUNT_TO_ASSOCIATE = "account3";
    private static final String ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";
    private final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
    private static final String CUSTOM_NAME = "customName";
    private static final String CUSTOM_SYMBOL = "Ω";
    private static final String CUSTOM_MEMO = "Omega";

    public static void main(String... args) {
        new TokenUpdatePrecompileSuite().runSuiteSync();
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
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                updateTokenWithKeysHappyPath(),
                updateNftTreasuryWithAndWithoutAdminKey(),
                updateWithTooLongNameAndSymbol(),
                updateTokenWithKeysNegative(),
                updateTokenWithKeyWithMultipleValues());
    }

    private HapiApiSpec updateTokenWithKeysHappyPath() {

        return defaultHapiSpec("updateTokenWithKeysHappyPath")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(MULTI_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        grantTokenKyc(VANILLA_TOKEN, ACCOUNT),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                "updateTokenWithAllFields",
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray(),
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getContractId(
                                                                                        TOKEN_UPDATE_CONTRACT)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                AUTO_RENEW_PERIOD,
                                                                CUSTOM_NAME,
                                                                CUSTOM_SYMBOL,
                                                                CUSTOM_MEMO)
                                                        .via(UPDATE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT),
                                                newKeyNamed(DELEGATE_KEY)
                                                        .shape(
                                                                DELEGATE_CONTRACT.signedWith(
                                                                        TOKEN_UPDATE_CONTRACT)),
                                                newKeyNamed(TOKEN_UPDATE_AS_KEY)
                                                        .shape(
                                                                CONTRACT.signedWith(
                                                                        TOKEN_UPDATE_CONTRACT)))))
                .then(
                        sourcing(
                                () ->
                                        getTokenInfo(VANILLA_TOKEN)
                                                .logged()
                                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                                .hasSymbol(CUSTOM_SYMBOL)
                                                .hasName(CUSTOM_NAME)
                                                .hasEntityMemo(CUSTOM_MEMO)
                                                .hasTreasury(ACCOUNT)
                                                .hasAutoRenewAccount(ACCOUNT)
                                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                                .hasSupplyType(TokenSupplyType.INFINITE)
                                                .searchKeysGlobally()
                                                .hasAdminKey(ED25519KEY)
                                                .hasPauseKey(MULTI_KEY)
                                                .hasKycKey(ED25519KEY)
                                                .hasFreezeKey(ECDSA_KEY)
                                                .hasWipeKey(ECDSA_KEY)
                                                .hasFeeScheduleKey(DELEGATE_KEY)
                                                .hasSupplyKey(TOKEN_UPDATE_AS_KEY)
                                                .hasPauseKey(TOKEN_UPDATE_AS_KEY)));
    }

    public HapiApiSpec updateNftTreasuryWithAndWithoutAdminKey() {
        final var newTokenTreasury = "newTokenTreasury";
        final var NO_ADMIN_TOKEN = "noAdminKeyToken";
        final AtomicReference<TokenID> noAdminKeyToken = new AtomicReference<>();
        return defaultHapiSpec("updateNftTreasuryWithAndWithoutAdminKey")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(newTokenTreasury).maxAutomaticTokenAssociations(6),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT),
                        tokenCreate(NO_ADMIN_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> noAdminKeyToken.set(asToken(id))),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(ByteString.copyFromUtf8("memo1"))),
                        tokenAssociate(newTokenTreasury, VANILLA_TOKEN),
                        mintToken(NO_ADMIN_TOKEN, List.of(ByteString.copyFromUtf8("memo2"))))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                "updateTokenTreasury",
                                                                asAddress(noAdminKeyToken.get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        newTokenTreasury)))
                                                        .via("noAdminKey")
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                "updateTokenTreasury",
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        newTokenTreasury)))
                                                        .via("tokenUpdateTxn")
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT))))
                .then(
                        childRecordsCheck(
                                "noAdminKey",
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith().status(TOKEN_IS_IMMUTABLE)),
                        getTokenNftInfo(VANILLA_TOKEN, 1).hasAccountID(newTokenTreasury).logged(),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 0),
                        getAccountBalance(newTokenTreasury).hasTokenBalance(VANILLA_TOKEN, 1),
                        getTokenInfo(VANILLA_TOKEN)
                                .hasTreasury(newTokenTreasury)
                                .hasPauseStatus(TokenPauseStatus.Unpaused)
                                .logged(),
                        getTokenNftInfo(VANILLA_TOKEN, 1).hasAccountID(newTokenTreasury).logged());
    }

    public HapiApiSpec updateWithTooLongNameAndSymbol() {
        final var tooLongString = "ORIGINAL" + TxnUtils.randomUppercase(101);
        final var tooLongSymbolTxn = "tooLongSymbolTxn";
        return defaultHapiSpec("updateWithTooLongNameAndSymbol")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                "checkNameAndSymbolLenght",
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                tooLongString,
                                                                CUSTOM_SYMBOL)
                                                        .via(UPDATE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                "checkNameAndSymbolLenght",
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                CUSTOM_NAME,
                                                                tooLongString)
                                                        .via(tooLongSymbolTxn)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                childRecordsCheck(
                                                        UPDATE_TXN,
                                                        CONTRACT_REVERT_EXECUTED,
                                                        TransactionRecordAsserts.recordWith()
                                                                .status(TOKEN_NAME_TOO_LONG)),
                                                childRecordsCheck(
                                                        tooLongSymbolTxn,
                                                        CONTRACT_REVERT_EXECUTED,
                                                        TransactionRecordAsserts.recordWith()
                                                                .status(TOKEN_SYMBOL_TOO_LONG)))));
    }

    private HapiApiSpec updateTokenWithKeysNegative() {
        final var updateTokenWithKeysFunc = "updateTokenWithKeys";
        final var NO_FEE_SCEDULE_KEY_TXN = "NO_FEE_SCEDULE_KEY_TXN";
        final var NO_PAUSE_KEY_TXN = "NO_PAUSE_KEY_TXN";
        final var NO_KYC_KEY_TXN = "NO_KYC_KEY_TXN";
        final var NO_WIPE_KEY_TXN = "NO_WIPE_KEY_TXN";
        final var NO_FREEZE_KEY_TXN = "NO_FREEZE_KEY_TXN";
        final var NO_SUPPLY_KEY_TXN = "NO_SUPPLY_KEY_TXN";
        final List<AtomicReference<TokenID>> tokenList = new ArrayList<>();

        return defaultHapiSpec("updateTokenWithKeysNegative")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT)
                                .balance(ONE_MILLION_HBARS)
                                .key(MULTI_KEY)
                                .maxAutomaticTokenAssociations(100),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenList.add(new AtomicReference<>(asToken(id)))),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenList.add(new AtomicReference<>(asToken(id)))),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenList.add(new AtomicReference<>(asToken(id)))),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenList.add(new AtomicReference<>(asToken(id)))),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenList.add(new AtomicReference<>(asToken(id)))))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                updateTokenWithKeysFunc,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray(),
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getContractId(
                                                                                        TOKEN_UPDATE_CONTRACT)))
                                                        .via(NO_FEE_SCEDULE_KEY_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                updateTokenWithKeysFunc,
                                                                asAddress(tokenList.get(0).get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray(),
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getContractId(
                                                                                        TOKEN_UPDATE_CONTRACT)))
                                                        .via(NO_SUPPLY_KEY_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                updateTokenWithKeysFunc,
                                                                asAddress(tokenList.get(1).get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray(),
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getContractId(
                                                                                        TOKEN_UPDATE_CONTRACT)))
                                                        .via(NO_WIPE_KEY_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                updateTokenWithKeysFunc,
                                                                asAddress(tokenList.get(2).get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray(),
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getContractId(
                                                                                        TOKEN_UPDATE_CONTRACT)))
                                                        .via(NO_PAUSE_KEY_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                updateTokenWithKeysFunc,
                                                                asAddress(tokenList.get(3).get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray(),
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getContractId(
                                                                                        TOKEN_UPDATE_CONTRACT)))
                                                        .via(NO_FREEZE_KEY_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                updateTokenWithKeysFunc,
                                                                asAddress(tokenList.get(4).get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray(),
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getContractId(
                                                                                        TOKEN_UPDATE_CONTRACT)))
                                                        .via(NO_KYC_KEY_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        withOpContext(
                                (spec, ignore) ->
                                        allRunFor(
                                                spec,
                                                childRecordsCheck(
                                                        NO_FEE_SCEDULE_KEY_TXN,
                                                        CONTRACT_REVERT_EXECUTED,
                                                        TransactionRecordAsserts.recordWith()
                                                                .status(
                                                                        TOKEN_HAS_NO_FEE_SCHEDULE_KEY)),
                                                childRecordsCheck(
                                                        NO_SUPPLY_KEY_TXN,
                                                        CONTRACT_REVERT_EXECUTED,
                                                        TransactionRecordAsserts.recordWith()
                                                                .status(TOKEN_HAS_NO_SUPPLY_KEY)),
                                                childRecordsCheck(
                                                        NO_WIPE_KEY_TXN,
                                                        CONTRACT_REVERT_EXECUTED,
                                                        TransactionRecordAsserts.recordWith()
                                                                .status(TOKEN_HAS_NO_WIPE_KEY)),
                                                childRecordsCheck(
                                                        NO_PAUSE_KEY_TXN,
                                                        CONTRACT_REVERT_EXECUTED,
                                                        TransactionRecordAsserts.recordWith()
                                                                .status(TOKEN_HAS_NO_PAUSE_KEY)),
                                                childRecordsCheck(
                                                        NO_FREEZE_KEY_TXN,
                                                        CONTRACT_REVERT_EXECUTED,
                                                        TransactionRecordAsserts.recordWith()
                                                                .status(TOKEN_HAS_NO_FREEZE_KEY)),
                                                childRecordsCheck(
                                                        NO_KYC_KEY_TXN,
                                                        CONTRACT_REVERT_EXECUTED,
                                                        TransactionRecordAsserts.recordWith()
                                                                .status(TOKEN_HAS_NO_KYC_KEY)))));
    }

    private HapiApiSpec updateTokenWithKeyWithMultipleValues() {

        return defaultHapiSpec("updateTokenWithKeyWithMultipleValues")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(MULTI_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        grantTokenKyc(VANILLA_TOKEN, ACCOUNT),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                "updateTokenWithKeyWithMultipleValues",
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                AUTO_RENEW_PERIOD)
                                                        .via(UPDATE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                newKeyNamed(DELEGATE_KEY)
                                                        .shape(
                                                                DELEGATE_CONTRACT.signedWith(
                                                                        TOKEN_UPDATE_CONTRACT)),
                                                newKeyNamed(TOKEN_UPDATE_AS_KEY)
                                                        .shape(
                                                                CONTRACT.signedWith(
                                                                        TOKEN_UPDATE_CONTRACT)))))
                .then(sourcing(() -> emptyChildRecordsCheck(UPDATE_TXN, CONTRACT_REVERT_EXECUTED)));
    }
}
