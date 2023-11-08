/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_NOT_PROVIDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class TokenUpdatePrecompileSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(TokenUpdatePrecompileSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long AUTO_RENEW_PERIOD = 8_000_000L;
    private static final String ACCOUNT = "account";
    private static final String VANILLA_TOKEN = "TokenD";
    private static final String NFT_TOKEN = "TokenD";
    private static final String MULTI_KEY = "multiKey";
    private static final String UPDATE_KEY_FUNC = "tokenUpdateKeys";
    private static final String GET_KEY_FUNC = "getKeyFromToken";
    public static final String TOKEN_UPDATE_CONTRACT = "UpdateTokenInfoContract";
    private static final String UPDATE_TXN = "updateTxn";
    private static final String NO_ADMIN_KEY = "noAdminKeyTxn";
    private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    private static final String ED25519KEY = "ed25519key";
    private static final String ECDSA_KEY = "ecdsa";
    public static final String TOKEN_UPDATE_AS_KEY = "tokenCreateContractAsKey";
    private static final String DELEGATE_KEY = "tokenUpdateAsKeyDelegate";
    private static final String ACCOUNT_TO_ASSOCIATE = "account3";
    private static final String ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";
    public static final String CUSTOM_NAME = "customName";
    public static final String CUSTOM_SYMBOL = "Ω";
    public static final String CUSTOM_MEMO = "Omega";
    private static final long ADMIN_KEY_TYPE = 1L;
    private static final long SUPPLY_KEY_TYPE = 16L;

    public static void main(String... args) {
        new TokenUpdatePrecompileSuite().runSuiteAsync();
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
        return allOf(negativeCases());
    }

    List<HapiSpec> negativeCases() {
        return List.of(
                updateTokenWithInvalidKeyValues(),
                updateNftTokenKeysWithWrongTokenIdAndMissingAdminKey(),
                getTokenKeyForNonFungibleNegative());
    }

    @HapiTest
    private HapiSpec updateTokenWithInvalidKeyValues() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return defaultHapiSpec("updateTokenWithInvalidKeyValues")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(MULTI_KEY).shape(ED25519_ON),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(MULTI_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT).gas(GAS_TO_OFFER),
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
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        "updateTokenWithInvalidKeyValues",
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(UPDATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT.signedWith(TOKEN_UPDATE_CONTRACT)),
                        newKeyNamed(TOKEN_UPDATE_AS_KEY).shape(CONTRACT.signedWith(TOKEN_UPDATE_CONTRACT)))))
                .then(sourcing(() -> emptyChildRecordsCheck(UPDATE_TXN, CONTRACT_REVERT_EXECUTED)));
    }

    @HapiTest
    public HapiSpec updateNftTokenKeysWithWrongTokenIdAndMissingAdminKey() {
        final AtomicReference<TokenID> nftToken = new AtomicReference<>();
        return defaultHapiSpec("updateNftTokenKeysWithWrongTokenIdAndMissingAdminKey")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(MULTI_KEY).shape(ED25519_ON),
                        cryptoCreate(ACCOUNT).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> nftToken.set(asToken(id))),
                        mintToken(NFT_TOKEN, List.of(ByteString.copyFromUtf8("nft4"))),
                        tokenAssociate(ACCOUNT, NFT_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        UPDATE_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(new byte[20]),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_UPDATE_CONTRACT))))
                                .via(UPDATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        UPDATE_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(nftToken.get())),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_UPDATE_CONTRACT))))
                                .via(NO_ADMIN_KEY)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                UPDATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                NO_ADMIN_KEY,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith().status(TOKEN_IS_IMMUTABLE)))));
    }

    @HapiTest
    public HapiSpec getTokenKeyForNonFungibleNegative() {
        final AtomicReference<TokenID> nftToken = new AtomicReference<>();
        return defaultHapiSpec("getTokenKeyForNonFungibleNegative")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        newKeyNamed(MULTI_KEY).shape(ED25519_ON),
                        cryptoCreate(ACCOUNT).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> nftToken.set(asToken(id))),
                        mintToken(NFT_TOKEN, List.of(ByteString.copyFromUtf8("nft5"))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCallLocal(
                                TOKEN_UPDATE_CONTRACT,
                                GET_KEY_FUNC,
                                HapiParserUtil.asHeadlongAddress(asAddress(nftToken.get())),
                                BigInteger.valueOf(SUPPLY_KEY_TYPE)),
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        GET_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(nftToken.get())),
                                        BigInteger.valueOf(89L))
                                .via("Invalid_Key_Type")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        GET_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(new byte[20]),
                                        BigInteger.valueOf(SUPPLY_KEY_TYPE))
                                .via("InvalidTokenId")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        GET_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(nftToken.get())),
                                        BigInteger.valueOf(ADMIN_KEY_TYPE))
                                .via(NO_ADMIN_KEY)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                "InvalidTokenId",
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                NO_ADMIN_KEY,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith().status(KEY_NOT_PROVIDED)))));
    }
}
