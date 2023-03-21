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
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenInfo;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.TokenKeyType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

public class TokenInfoHTSSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(TokenInfoHTSSuite.class);

    private static final String TOKEN_INFO_CONTRACT = "TokenInfoContract";
    private static final String ADMIN_KEY = TokenKeyType.ADMIN_KEY.name();
    private static final String KYC_KEY = TokenKeyType.KYC_KEY.name();
    private static final String SUPPLY_KEY = TokenKeyType.SUPPLY_KEY.name();
    private static final String FREEZE_KEY = TokenKeyType.FREEZE_KEY.name();
    private static final String WIPE_KEY = TokenKeyType.WIPE_KEY.name();
    private static final String FEE_SCHEDULE_KEY = TokenKeyType.FEE_SCHEDULE_KEY.name();
    private static final String PAUSE_KEY = TokenKeyType.PAUSE_KEY.name();
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String FEE_DENOM = "denom";
    public static final String HTS_COLLECTOR = "denomFee";
    private static final String ACCOUNT = "Account";
    private static final String CREATE_TXN = "CreateTxn";
    private static final String TOKEN_INFO_TXN = "TokenInfoTxn";
    private static final String FUNGIBLE_TOKEN_INFO_TXN = "FungibleTokenInfoTxn";
    private static final String UPDATE_ANG_GET_TOKEN_INFO_TXN = "UpdateAndGetTokenInfoTxn";
    private static final String UPDATE_ANG_GET_FUNGIBLE_TOKEN_INFO_TXN = "UpdateAndGetFungibleTokenInfoTxn";
    private static final String UPDATE_ANG_GET_NON_FUNGIBLE_TOKEN_INFO_TXN = "UpdateAndGetNonFungibleTokenInfoTxn";
    private static final String NON_FUNGIBLE_TOKEN_INFO_TXN = "NonFungibleTokenInfoTxn";
    private static final String GET_TOKEN_INFO_TXN = "GetTokenInfo";
    private static final String APPROVE_TXN = "approveTxn";
    private static final String UPDATE_AND_GET_TOKEN_KEYS_INFO_TXN = "updateTokenKeysAndReadLatestInformation";
    private static final String SYMBOL = "T";
    private static final String FUNGIBLE_SYMBOL = "FT";
    private static final String FUNGIBLE_TOKEN_NAME = "FungibleToken";
    private static final String NON_FUNGIBLE_SYMBOL = "NFT";
    private static final String META = "First";
    private static final String MEMO = "JUMP";
    private static final String UPDATE_NAME = "NewName";
    private static final String UPDATE_SYMBOL = "NewSymbol";
    private static final String UPDATE_MEMO = "NewMemo";
    private static final String PRIMARY_TOKEN_NAME = "primary";
    private static final String NFT_OWNER = "NFT Owner";
    private static final String NFT_SPENDER = "NFT Spender";
    private static final String NON_FUNGIBLE_TOKEN_NAME = "NonFungibleToken";
    private static final String MULTI_KEY = "multiKey";
    private static final String GET_INFORMATION_FOR_TOKEN = "getInformationForToken";
    private static final String GET_INFORMATION_FOR_FUNGIBLE_TOKEN = "getInformationForFungibleToken";
    private static final String GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN = "getInformationForNonFungibleToken";

    private static final String UPDATE_INFORMATION_FOR_TOKEN_AND_GET_LATEST_INFORMATION =
            "updateInformationForTokenAndGetLatestInformation";
    private static final String UPDATE_INFORMATION_FOR_FUNGIBLE_TOKEN_AND_GET_LATEST_INFORMATION =
            "updateInformationForFungibleTokenAndGetLatestInformation";
    private static final String UPDATE_INFORMATION_FOR_NON_FUNGIBLE_TOKEN_AND_GET_LATEST_INFORMATION =
            "updateInformationForNonFungibleTokenAndGetLatestInformation";
    private static final int NUMERATOR = 1;
    private static final int DENOMINATOR = 2;
    private static final int MINIMUM_TO_COLLECT = 5;
    private static final int MAXIMUM_TO_COLLECT = 400;
    private static final int MAX_SUPPLY = 1000;
    public static final String GET_CUSTOM_FEES_FOR_TOKEN = "getCustomFeesForToken";

    public static void main(final String... args) {
        new TokenInfoHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(
                getInfoOnDeletedFungibleTokenWorks(),
                getInfoOnInvalidFungibleTokenFails(),
                getInfoOnDeletedNonFungibleTokenFails(),
                getInfoOnInvalidNonFungibleTokenFails());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(
                happyPathGetTokenInfo(),
                happyPathUpdateTokenInfoAndGetLatestInfo(),
                happyPathGetFungibleTokenInfo(),
                happyPathUpdateFungibleTokenInfoAndGetLatestInfo(),
                happyPathGetNonFungibleTokenInfo(),
                happyPathUpdateNonFungibleTokenInfoAndGetLatestInfo(),
                happyPathGetTokenCustomFees(),
                happyPathGetNonFungibleTokenCustomFees(),
                happyPathUpdateTokenKeysAndReadLatestInformation());
    }

    private HapiSpec happyPathGetTokenInfo() {
        return defaultHapiSpec("HappyPathGetTokenInfo")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(HTS_COLLECTOR),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        newKeyNamed(FEE_SCHEDULE_KEY),
                        newKeyNamed(PAUSE_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(PRIMARY_TOKEN_NAME)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .symbol(SYMBOL)
                                .name(PRIMARY_TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(MAX_SUPPLY)
                                .initialSupply(500L)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey(WIPE_KEY)
                                .feeScheduleKey(FEE_SCHEDULE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .withCustom(fixedHbarFee(500L, HTS_COLLECTOR))
                                .withCustom(fractionalFee(
                                        NUMERATOR,
                                        DENOMINATOR,
                                        MINIMUM_TO_COLLECT,
                                        OptionalLong.of(MAXIMUM_TO_COLLECT),
                                        TOKEN_TREASURY))
                                .via(CREATE_TXN),
                        getTokenInfo(PRIMARY_TOKEN_NAME).via(GET_TOKEN_INFO_TXN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(PRIMARY_TOKEN_NAME))))
                                .via(TOKEN_INFO_TXN)
                                .gas(1_000_000L),
                        contractCallLocal(
                                TOKEN_INFO_CONTRACT,
                                GET_INFORMATION_FOR_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(PRIMARY_TOKEN_NAME)))))))
                .then(withOpContext((spec, opLog) -> {
                    final var getTokenInfoQuery = getTokenInfo(PRIMARY_TOKEN_NAME);
                    allRunFor(spec, getTokenInfoQuery);
                    final var expirySecond = getTokenInfoQuery
                            .getResponse()
                            .getTokenGetInfo()
                            .getTokenInfo()
                            .getExpiry()
                            .getSeconds();

                    allRunFor(
                            spec,
                            getTxnRecord(TOKEN_INFO_TXN).andAllChildRecords().logged(),
                            childRecordsCheck(
                                    TOKEN_INFO_TXN,
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_GET_TOKEN_INFO)
                                                            .withStatus(SUCCESS)
                                                            .withTokenInfo(getTokenInfoStructForFungibleToken(
                                                                    spec,
                                                                    PRIMARY_TOKEN_NAME,
                                                                    SYMBOL,
                                                                    MEMO,
                                                                    spec.registry()
                                                                            .getAccountID(TOKEN_TREASURY),
                                                                    expirySecond))))));
                }));
    }

    private HapiSpec happyPathUpdateTokenInfoAndGetLatestInfo() {
        final int decimals = 1;
        return defaultHapiSpec("HappyPathUpdateTokenInfoAndGetLatestInfo")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(UPDATED_TREASURY)
                                .keyShape(ED25519_ON)
                                .balance(0L)
                                .maxAutomaticTokenAssociations(3),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(HTS_COLLECTOR),
                        cryptoCreate(ACCOUNT),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        newKeyNamed(FEE_SCHEDULE_KEY),
                        newKeyNamed(PAUSE_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(FUNGIBLE_TOKEN_NAME)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(FUNGIBLE_TOKEN_NAME)
                                .symbol(FUNGIBLE_SYMBOL)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(MAX_SUPPLY)
                                .initialSupply(500)
                                .decimals(decimals)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey(WIPE_KEY)
                                .feeScheduleKey(FEE_SCHEDULE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .withCustom(fixedHbarFee(500L, HTS_COLLECTOR))
                                .withCustom(fractionalFee(
                                        NUMERATOR,
                                        DENOMINATOR,
                                        MINIMUM_TO_COLLECT,
                                        OptionalLong.of(MAXIMUM_TO_COLLECT),
                                        TOKEN_TREASURY))
                                .via(CREATE_TXN),
                        tokenAssociate(ACCOUNT, FUNGIBLE_TOKEN_NAME))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        UPDATE_INFORMATION_FOR_TOKEN_AND_GET_LATEST_INFORMATION,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN_NAME))),
                                        UPDATE_NAME,
                                        UPDATE_SYMBOL,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(UPDATED_TREASURY))),
                                        UPDATE_MEMO)
                                .alsoSigningWithFullPrefix(ADMIN_KEY, UPDATED_TREASURY)
                                .payingWith(ACCOUNT)
                                .via(UPDATE_ANG_GET_TOKEN_INFO_TXN)
                                .gas(1_000_000L))))
                .then(withOpContext((spec, opLog) -> {
                    final var getTokenInfoQuery = getTokenInfo(FUNGIBLE_TOKEN_NAME);
                    allRunFor(spec, getTokenInfoQuery);
                    final var expirySecond = getTokenInfoQuery
                            .getResponse()
                            .getTokenGetInfo()
                            .getTokenInfo()
                            .getExpiry()
                            .getSeconds();
                    allRunFor(
                            spec,
                            getTxnRecord(UPDATE_ANG_GET_TOKEN_INFO_TXN)
                                    .andAllChildRecords()
                                    .logged(),
                            childRecordsCheck(
                                    UPDATE_ANG_GET_TOKEN_INFO_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_GET_TOKEN_INFO)
                                                            .withStatus(SUCCESS)
                                                            .withDecimals(decimals)
                                                            .withTokenInfo(getTokenInfoStructForFungibleToken(
                                                                    spec,
                                                                    UPDATE_NAME,
                                                                    UPDATE_SYMBOL,
                                                                    UPDATE_MEMO,
                                                                    spec.registry()
                                                                            .getAccountID(UPDATED_TREASURY),
                                                                    expirySecond))))));
                }));
    }

    private HapiSpec happyPathGetFungibleTokenInfo() {
        final int decimals = 1;
        return defaultHapiSpec("HappyPathGetFungibleTokenInfo")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(HTS_COLLECTOR),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        newKeyNamed(FEE_SCHEDULE_KEY),
                        newKeyNamed(PAUSE_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(FUNGIBLE_TOKEN_NAME)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(FUNGIBLE_TOKEN_NAME)
                                .symbol(FUNGIBLE_SYMBOL)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(MAX_SUPPLY)
                                .initialSupply(500)
                                .decimals(decimals)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey(WIPE_KEY)
                                .feeScheduleKey(FEE_SCHEDULE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .withCustom(fixedHbarFee(500L, HTS_COLLECTOR))
                                .withCustom(fractionalFee(
                                        NUMERATOR,
                                        DENOMINATOR,
                                        MINIMUM_TO_COLLECT,
                                        OptionalLong.of(MAXIMUM_TO_COLLECT),
                                        TOKEN_TREASURY))
                                .via(CREATE_TXN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_FUNGIBLE_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN_NAME))))
                                .via(FUNGIBLE_TOKEN_INFO_TXN)
                                .gas(1_000_000L),
                        contractCallLocal(
                                TOKEN_INFO_CONTRACT,
                                GET_INFORMATION_FOR_FUNGIBLE_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN_NAME)))))))
                .then(withOpContext((spec, opLog) -> {
                    final var getTokenInfoQuery = getTokenInfo(FUNGIBLE_TOKEN_NAME);
                    allRunFor(spec, getTokenInfoQuery);
                    final var expirySecond = getTokenInfoQuery
                            .getResponse()
                            .getTokenGetInfo()
                            .getTokenInfo()
                            .getExpiry()
                            .getSeconds();

                    allRunFor(
                            spec,
                            getTxnRecord(FUNGIBLE_TOKEN_INFO_TXN)
                                    .andAllChildRecords()
                                    .logged(),
                            childRecordsCheck(
                                    FUNGIBLE_TOKEN_INFO_TXN,
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_GET_FUNGIBLE_TOKEN_INFO)
                                                            .withStatus(SUCCESS)
                                                            .withDecimals(decimals)
                                                            .withTokenInfo(getTokenInfoStructForFungibleToken(
                                                                    spec,
                                                                    FUNGIBLE_TOKEN_NAME,
                                                                    FUNGIBLE_SYMBOL,
                                                                    MEMO,
                                                                    spec.registry()
                                                                            .getAccountID(TOKEN_TREASURY),
                                                                    expirySecond))))));
                }));
    }

    private HapiSpec happyPathUpdateFungibleTokenInfoAndGetLatestInfo() {
        final int decimals = 1;
        return defaultHapiSpec("HappyPathUpdateFungibleTokenInfoAndGetLatestInfo")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(UPDATED_TREASURY).balance(0L).maxAutomaticTokenAssociations(3),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(HTS_COLLECTOR),
                        cryptoCreate(ACCOUNT),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        newKeyNamed(FEE_SCHEDULE_KEY),
                        newKeyNamed(PAUSE_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(FUNGIBLE_TOKEN_NAME)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(FUNGIBLE_TOKEN_NAME)
                                .symbol(FUNGIBLE_SYMBOL)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(MAX_SUPPLY)
                                .initialSupply(500)
                                .decimals(decimals)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey(WIPE_KEY)
                                .feeScheduleKey(FEE_SCHEDULE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .withCustom(fixedHbarFee(500L, HTS_COLLECTOR))
                                .withCustom(fractionalFee(
                                        NUMERATOR,
                                        DENOMINATOR,
                                        MINIMUM_TO_COLLECT,
                                        OptionalLong.of(MAXIMUM_TO_COLLECT),
                                        TOKEN_TREASURY))
                                .via(CREATE_TXN),
                        tokenAssociate(ACCOUNT, FUNGIBLE_TOKEN_NAME))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        UPDATE_INFORMATION_FOR_FUNGIBLE_TOKEN_AND_GET_LATEST_INFORMATION,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN_NAME))),
                                        UPDATE_NAME,
                                        UPDATE_SYMBOL,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(UPDATED_TREASURY))),
                                        UPDATE_MEMO)
                                .alsoSigningWithFullPrefix(ADMIN_KEY, UPDATED_TREASURY)
                                .payingWith(ACCOUNT)
                                .via(UPDATE_ANG_GET_FUNGIBLE_TOKEN_INFO_TXN)
                                .gas(1_000_000L))))
                .then(withOpContext((spec, opLog) -> {
                    final var getTokenInfoQuery = getTokenInfo(FUNGIBLE_TOKEN_NAME);
                    allRunFor(spec, getTokenInfoQuery);
                    final var expirySecond = getTokenInfoQuery
                            .getResponse()
                            .getTokenGetInfo()
                            .getTokenInfo()
                            .getExpiry()
                            .getSeconds();
                    allRunFor(
                            spec,
                            getTxnRecord(UPDATE_ANG_GET_FUNGIBLE_TOKEN_INFO_TXN)
                                    .andAllChildRecords()
                                    .logged(),
                            childRecordsCheck(
                                    UPDATE_ANG_GET_FUNGIBLE_TOKEN_INFO_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_GET_FUNGIBLE_TOKEN_INFO)
                                                            .withStatus(SUCCESS)
                                                            .withDecimals(decimals)
                                                            .withTokenInfo(getTokenInfoStructForFungibleToken(
                                                                    spec,
                                                                    UPDATE_NAME,
                                                                    UPDATE_SYMBOL,
                                                                    UPDATE_MEMO,
                                                                    spec.registry()
                                                                            .getAccountID(UPDATED_TREASURY),
                                                                    expirySecond))))));
                }));
    }

    private HapiSpec happyPathGetNonFungibleTokenInfo() {
        final int maxSupply = 10;
        final ByteString meta = ByteString.copyFrom(META.getBytes(StandardCharsets.UTF_8));
        return defaultHapiSpec("HappyPathGetNonFungibleTokenInfo")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(NFT_OWNER),
                        cryptoCreate(NFT_SPENDER),
                        cryptoCreate(HTS_COLLECTOR),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        newKeyNamed(FEE_SCHEDULE_KEY),
                        newKeyNamed(PAUSE_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(FEE_DENOM).treasury(HTS_COLLECTOR),
                        tokenCreate(NON_FUNGIBLE_TOKEN_NAME)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(NON_FUNGIBLE_TOKEN_NAME)
                                .symbol(NON_FUNGIBLE_SYMBOL)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(maxSupply)
                                .initialSupply(0)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey(WIPE_KEY)
                                .feeScheduleKey(FEE_SCHEDULE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(100, FEE_DENOM), HTS_COLLECTOR))
                                .via(CREATE_TXN),
                        mintToken(NON_FUNGIBLE_TOKEN_NAME, List.of(meta)),
                        tokenAssociate(NFT_OWNER, List.of(NON_FUNGIBLE_TOKEN_NAME)),
                        tokenAssociate(NFT_SPENDER, List.of(NON_FUNGIBLE_TOKEN_NAME)),
                        grantTokenKyc(NON_FUNGIBLE_TOKEN_NAME, NFT_OWNER),
                        cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN_NAME, 1L)
                                .between(TOKEN_TREASURY, NFT_OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(NFT_OWNER, NON_FUNGIBLE_TOKEN_NAME, NFT_SPENDER, false, List.of(1L))
                                .via(APPROVE_TXN)
                                .logged()
                                .signedBy(DEFAULT_PAYER, NFT_OWNER)
                                .fee(ONE_HBAR))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN_NAME))),
                                        1L)
                                .via(NON_FUNGIBLE_TOKEN_INFO_TXN)
                                .gas(1_000_000L),
                        contractCallLocal(
                                TOKEN_INFO_CONTRACT,
                                GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN_NAME))),
                                1L))))
                .then(withOpContext((spec, opLog) -> {
                    final var getTokenInfoQuery = getTokenInfo(NON_FUNGIBLE_TOKEN_NAME);
                    allRunFor(spec, getTokenInfoQuery);
                    final var expirySecond = getTokenInfoQuery
                            .getResponse()
                            .getTokenGetInfo()
                            .getTokenInfo()
                            .getExpiry()
                            .getSeconds();

                    final var nftTokenInfo = getTokenNftInfoForCheck(spec, getTokenInfoQuery, meta);

                    allRunFor(
                            spec,
                            getTxnRecord(NON_FUNGIBLE_TOKEN_INFO_TXN)
                                    .andAllChildRecords()
                                    .logged(),
                            childRecordsCheck(
                                    NON_FUNGIBLE_TOKEN_INFO_TXN,
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_GET_NON_FUNGIBLE_TOKEN_INFO)
                                                            .withStatus(SUCCESS)
                                                            .withTokenInfo(getTokenInfoStructForNonFungibleToken(
                                                                    spec,
                                                                    NON_FUNGIBLE_TOKEN_NAME,
                                                                    NON_FUNGIBLE_SYMBOL,
                                                                    MEMO,
                                                                    spec.registry()
                                                                            .getAccountID(TOKEN_TREASURY),
                                                                    expirySecond))
                                                            .withNftTokenInfo(nftTokenInfo)))));
                }));
    }

    private HapiSpec happyPathUpdateNonFungibleTokenInfoAndGetLatestInfo() {
        final int maxSupply = 10;
        final ByteString meta = ByteString.copyFrom(META.getBytes(StandardCharsets.UTF_8));
        return defaultHapiSpec("HappyPathUpdateNonFungibleTokenInfoAndGetLatestInfo")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(UPDATED_TREASURY)
                                .balance(0L)
                                .keyShape(ED25519_ON)
                                .maxAutomaticTokenAssociations(2),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(NFT_OWNER),
                        cryptoCreate(NFT_SPENDER),
                        cryptoCreate(HTS_COLLECTOR),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        newKeyNamed(FEE_SCHEDULE_KEY),
                        newKeyNamed(PAUSE_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(FEE_DENOM).treasury(HTS_COLLECTOR),
                        tokenCreate(NON_FUNGIBLE_TOKEN_NAME)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(NON_FUNGIBLE_TOKEN_NAME)
                                .symbol(NON_FUNGIBLE_SYMBOL)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(maxSupply)
                                .initialSupply(0)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey(WIPE_KEY)
                                .feeScheduleKey(FEE_SCHEDULE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(100, FEE_DENOM), HTS_COLLECTOR))
                                .via(CREATE_TXN),
                        mintToken(NON_FUNGIBLE_TOKEN_NAME, List.of(meta)),
                        tokenAssociate(NFT_OWNER, List.of(NON_FUNGIBLE_TOKEN_NAME)),
                        tokenAssociate(NFT_SPENDER, List.of(NON_FUNGIBLE_TOKEN_NAME)),
                        grantTokenKyc(NON_FUNGIBLE_TOKEN_NAME, NFT_OWNER),
                        cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN_NAME, 1L)
                                .between(TOKEN_TREASURY, NFT_OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(NFT_OWNER, NON_FUNGIBLE_TOKEN_NAME, NFT_SPENDER, false, List.of(1L))
                                .via(APPROVE_TXN)
                                .logged()
                                .signedBy(DEFAULT_PAYER, NFT_OWNER)
                                .fee(ONE_HBAR))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        UPDATE_INFORMATION_FOR_NON_FUNGIBLE_TOKEN_AND_GET_LATEST_INFORMATION,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN_NAME))),
                                        1L,
                                        UPDATE_NAME,
                                        UPDATE_SYMBOL,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(UPDATED_TREASURY))),
                                        UPDATE_MEMO)
                                .alsoSigningWithFullPrefix(ADMIN_KEY, UPDATED_TREASURY)
                                .via(UPDATE_ANG_GET_NON_FUNGIBLE_TOKEN_INFO_TXN)
                                .gas(1_000_000L))))
                .then(withOpContext((spec, opLog) -> {
                    final var getTokenInfoQuery = getTokenInfo(NON_FUNGIBLE_TOKEN_NAME);
                    allRunFor(spec, getTokenInfoQuery);
                    final var expirySecond = getTokenInfoQuery
                            .getResponse()
                            .getTokenGetInfo()
                            .getTokenInfo()
                            .getExpiry()
                            .getSeconds();

                    final var nftTokenInfo = getTokenNftInfoForCheck(spec, getTokenInfoQuery, meta);

                    allRunFor(
                            spec,
                            getTxnRecord(UPDATE_ANG_GET_NON_FUNGIBLE_TOKEN_INFO_TXN)
                                    .andAllChildRecords()
                                    .logged(),
                            childRecordsCheck(
                                    UPDATE_ANG_GET_NON_FUNGIBLE_TOKEN_INFO_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_GET_NON_FUNGIBLE_TOKEN_INFO)
                                                            .withStatus(SUCCESS)
                                                            .withTokenInfo(getTokenInfoStructForNonFungibleToken(
                                                                    spec,
                                                                    UPDATE_NAME,
                                                                    UPDATE_SYMBOL,
                                                                    UPDATE_MEMO,
                                                                    spec.registry()
                                                                            .getAccountID(UPDATED_TREASURY),
                                                                    expirySecond))
                                                            .withNftTokenInfo(nftTokenInfo)))));
                }));
    }

    private HapiSpec getInfoOnDeletedFungibleTokenWorks() {
        return defaultHapiSpec("GetInfoOnDeletedFungibleTokenFails")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(PRIMARY_TOKEN_NAME)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(PRIMARY_TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(1000)
                                .initialSupply(500)
                                .adminKey(ADMIN_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .via(CREATE_TXN),
                        tokenDelete(PRIMARY_TOKEN_NAME))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(PRIMARY_TOKEN_NAME))))
                                .via(TOKEN_INFO_TXN + 1)
                                .gas(1_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_FUNGIBLE_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(PRIMARY_TOKEN_NAME))))
                                .via(TOKEN_INFO_TXN + 2)
                                .gas(1_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))))
                .then(
                        getTxnRecord(TOKEN_INFO_TXN + 1).andAllChildRecords().logged(),
                        getTxnRecord(TOKEN_INFO_TXN + 2).andAllChildRecords().logged());
    }

    private HapiSpec getInfoOnInvalidFungibleTokenFails() {
        return defaultHapiSpec("GetInfoOnInvalidFungibleTokenFails")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(PRIMARY_TOKEN_NAME)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(PRIMARY_TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(1000)
                                .initialSupply(500)
                                .adminKey(ADMIN_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .via(CREATE_TXN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(new byte[20]))
                                .via(TOKEN_INFO_TXN + 1)
                                .gas(1_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_FUNGIBLE_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(new byte[20]))
                                .via(TOKEN_INFO_TXN + 2)
                                .gas(1_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(TOKEN_INFO_TXN + 1).andAllChildRecords().logged(),
                        getTxnRecord(TOKEN_INFO_TXN + 2).andAllChildRecords().logged());
    }

    private HapiSpec getInfoOnDeletedNonFungibleTokenFails() {
        final ByteString meta = ByteString.copyFrom(META.getBytes(StandardCharsets.UTF_8));
        return defaultHapiSpec("GetInfoOnDeletedNonFungibleTokenFails")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(NON_FUNGIBLE_TOKEN_NAME)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(NON_FUNGIBLE_TOKEN_NAME)
                                .symbol(NON_FUNGIBLE_SYMBOL)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(10)
                                .initialSupply(0)
                                .adminKey(ADMIN_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .via(CREATE_TXN),
                        mintToken(NON_FUNGIBLE_TOKEN_NAME, List.of(meta)),
                        tokenDelete(NON_FUNGIBLE_TOKEN_NAME))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN_NAME))),
                                        1L)
                                .via(NON_FUNGIBLE_TOKEN_INFO_TXN)
                                .gas(1_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))))
                .then(getTxnRecord(NON_FUNGIBLE_TOKEN_INFO_TXN)
                        .andAllChildRecords()
                        .logged());
    }

    private HapiSpec getInfoOnInvalidNonFungibleTokenFails() {
        final ByteString meta = ByteString.copyFrom(META.getBytes(StandardCharsets.UTF_8));
        return defaultHapiSpec("GetInfoOnDeletedNonFungibleTokenFails")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(NON_FUNGIBLE_TOKEN_NAME)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(NON_FUNGIBLE_TOKEN_NAME)
                                .symbol("NFT")
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(10)
                                .initialSupply(0)
                                .adminKey(ADMIN_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .via(CREATE_TXN),
                        mintToken(NON_FUNGIBLE_TOKEN_NAME, List.of(meta)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(new byte[20]),
                                        1L)
                                .via(NON_FUNGIBLE_TOKEN_INFO_TXN + 1)
                                .gas(1_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN_NAME))),
                                        2L)
                                .via(NON_FUNGIBLE_TOKEN_INFO_TXN + 2)
                                .gas(1_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(NON_FUNGIBLE_TOKEN_INFO_TXN + 1)
                                .andAllChildRecords()
                                .logged(),
                        getTxnRecord(NON_FUNGIBLE_TOKEN_INFO_TXN + 2)
                                .andAllChildRecords()
                                .logged());
    }

    private HapiSpec happyPathGetTokenCustomFees() {
        return defaultHapiSpec("HappyPathGetTokenCustomFees")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(HTS_COLLECTOR),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(PRIMARY_TOKEN_NAME)
                                .supplyType(TokenSupplyType.FINITE)
                                .name(PRIMARY_TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(MAX_SUPPLY)
                                .initialSupply(500L)
                                .withCustom(fixedHbarFee(500L, HTS_COLLECTOR))
                                .withCustom(fractionalFee(
                                        NUMERATOR,
                                        DENOMINATOR,
                                        MINIMUM_TO_COLLECT,
                                        OptionalLong.of(MAXIMUM_TO_COLLECT),
                                        TOKEN_TREASURY))
                                .via(CREATE_TXN),
                        getTokenInfo(PRIMARY_TOKEN_NAME).via(GET_TOKEN_INFO_TXN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_CUSTOM_FEES_FOR_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(PRIMARY_TOKEN_NAME))))
                                .via(TOKEN_INFO_TXN)
                                .gas(1_000_000L),
                        contractCallLocal(
                                TOKEN_INFO_CONTRACT,
                                GET_CUSTOM_FEES_FOR_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(PRIMARY_TOKEN_NAME)))))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        getTxnRecord(TOKEN_INFO_TXN).andAllChildRecords().logged(),
                        childRecordsCheck(
                                TOKEN_INFO_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_CUSTOM_FEES)
                                                        .withStatus(SUCCESS)
                                                        .withCustomFees(getCustomFees(spec))))))));
    }

    private HapiSpec happyPathGetNonFungibleTokenCustomFees() {
        final int maxSupply = 10;
        final ByteString meta = ByteString.copyFrom(META.getBytes(StandardCharsets.UTF_8));
        return defaultHapiSpec("HappyPathGetNonFungibleTokenCustomFees")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(NFT_OWNER),
                        cryptoCreate(NFT_SPENDER),
                        cryptoCreate(HTS_COLLECTOR),
                        newKeyNamed(SUPPLY_KEY),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        tokenCreate(FEE_DENOM).treasury(HTS_COLLECTOR),
                        tokenCreate(NON_FUNGIBLE_TOKEN_NAME)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(NON_FUNGIBLE_TOKEN_NAME)
                                .symbol(NON_FUNGIBLE_SYMBOL)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(maxSupply)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(100, FEE_DENOM), HTS_COLLECTOR))
                                .via(CREATE_TXN),
                        mintToken(NON_FUNGIBLE_TOKEN_NAME, List.of(meta)),
                        tokenAssociate(NFT_OWNER, List.of(NON_FUNGIBLE_TOKEN_NAME)),
                        tokenAssociate(NFT_SPENDER, List.of(NON_FUNGIBLE_TOKEN_NAME)),
                        cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN_NAME, 1L)
                                .between(TOKEN_TREASURY, NFT_OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(NFT_OWNER, NON_FUNGIBLE_TOKEN_NAME, NFT_SPENDER, false, List.of(1L))
                                .via(APPROVE_TXN)
                                .logged()
                                .signedBy(DEFAULT_PAYER, NFT_OWNER)
                                .fee(ONE_HBAR))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        GET_CUSTOM_FEES_FOR_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN_NAME))))
                                .via(NON_FUNGIBLE_TOKEN_INFO_TXN)
                                .gas(1_000_000L),
                        contractCallLocal(
                                TOKEN_INFO_CONTRACT,
                                GET_CUSTOM_FEES_FOR_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN_NAME)))))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        getTxnRecord(NON_FUNGIBLE_TOKEN_INFO_TXN)
                                .andAllChildRecords()
                                .logged(),
                        childRecordsCheck(
                                NON_FUNGIBLE_TOKEN_INFO_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_CUSTOM_FEES)
                                                        .withStatus(SUCCESS)
                                                        .withCustomFees(getCustomFeeForNFT(spec))))))));
    }

    private HapiSpec happyPathUpdateTokenKeysAndReadLatestInformation() {
        final String TOKEN_INFO_AS_KEY = "TOKEN_INFO_CONTRACT_KEY";
        return defaultHapiSpec("UpdateTokenKeysAndReadLatestInformation")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(HTS_COLLECTOR),
                        cryptoCreate(ACCOUNT),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(TOKEN_INFO_AS_KEY).shape(CONTRACT.signedWith(TOKEN_INFO_CONTRACT)),
                        tokenCreate(FUNGIBLE_TOKEN_NAME)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .initialSupply(1_000),
                        tokenAssociate(ACCOUNT, FUNGIBLE_TOKEN_NAME))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_INFO_CONTRACT,
                                        UPDATE_AND_GET_TOKEN_KEYS_INFO_TXN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN_NAME))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_INFO_CONTRACT))))
                                .gas(200_000)
                                .via(UPDATE_AND_GET_TOKEN_KEYS_INFO_TXN)
                                .alsoSigningWithFullPrefix(MULTI_KEY))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        getTxnRecord(UPDATE_AND_GET_TOKEN_KEYS_INFO_TXN)
                                .andAllChildRecords()
                                .logged(),
                        childRecordsCheck(
                                UPDATE_AND_GET_TOKEN_KEYS_INFO_TXN,
                                SUCCESS,
                                recordWith().status(SUCCESS),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_KEY)
                                                        .withStatus(SUCCESS)
                                                        .withTokenKeyValue(
                                                                //
                                                                //  spec.registry().getKey(TOKEN_INFO_AS_KEY)
                                                                Key.newBuilder()
                                                                        .setContractID(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TOKEN_INFO_CONTRACT))
                                                                        .build()))),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_KEY)
                                                        .withStatus(SUCCESS)
                                                        .withTokenKeyValue(Key.newBuilder()
                                                                .setContractID(spec.registry()
                                                                        .getContractId(TOKEN_INFO_CONTRACT))
                                                                .build()))),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_KEY)
                                                        .withStatus(SUCCESS)
                                                        .withTokenKeyValue(
                                                                spec.registry().getKey(TOKEN_INFO_AS_KEY)))),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_KEY)
                                                        .withStatus(SUCCESS)
                                                        .withTokenKeyValue(
                                                                spec.registry().getKey(TOKEN_INFO_AS_KEY)))),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_KEY)
                                                        .withStatus(SUCCESS)
                                                        .withTokenKeyValue(
                                                                spec.registry().getKey(TOKEN_INFO_AS_KEY)))),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_KEY)
                                                        .withStatus(SUCCESS)
                                                        .withTokenKeyValue(
                                                                spec.registry().getKey(TOKEN_INFO_AS_KEY)))),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_KEY)
                                                        .withStatus(SUCCESS)
                                                        .withTokenKeyValue(
                                                                spec.registry().getKey(TOKEN_INFO_AS_KEY))))))));
    }

    private TokenNftInfo getTokenNftInfoForCheck(
            final HapiSpec spec, final HapiGetTokenInfo getTokenInfoQuery, final ByteString meta) {
        final var tokenId =
                getTokenInfoQuery.getResponse().getTokenGetInfo().getTokenInfo().getTokenId();

        final var getNftTokenInfoQuery = getTokenNftInfo(NON_FUNGIBLE_TOKEN_NAME, 1L);
        allRunFor(spec, getNftTokenInfoQuery);
        final var creationTime =
                getNftTokenInfoQuery.getResponse().getTokenGetNftInfo().getNft().getCreationTime();

        final var ownerId = spec.registry().getAccountID(NFT_OWNER);
        final var spenderId = spec.registry().getAccountID(NFT_SPENDER);

        return TokenNftInfo.newBuilder()
                .setLedgerId(fromString("0x03"))
                .setNftID(NftID.newBuilder()
                        .setTokenID(tokenId)
                        .setSerialNumber(1L)
                        .build())
                .setAccountID(ownerId)
                .setCreationTime(creationTime)
                .setMetadata(meta)
                .setSpenderId(spenderId)
                .build();
    }

    private TokenInfo getTokenInfoStructForFungibleToken(
            final HapiSpec spec,
            final String tokenName,
            final String symbol,
            final String memo,
            final AccountID treasury,
            final long expirySecond) {
        final var autoRenewAccount = spec.registry().getAccountID(AUTO_RENEW_ACCOUNT);

        final ArrayList<CustomFee> customFees = getCustomFees(spec);

        return TokenInfo.newBuilder()
                .setLedgerId(fromString("0x03"))
                .setSupplyTypeValue(TokenSupplyType.FINITE_VALUE)
                .setExpiry(Timestamp.newBuilder().setSeconds(expirySecond))
                .setAutoRenewAccount(autoRenewAccount)
                .setAutoRenewPeriod(Duration.newBuilder()
                        .setSeconds(THREE_MONTHS_IN_SECONDS)
                        .build())
                .setSymbol(symbol)
                .setName(tokenName)
                .setMemo(memo)
                .setTreasury(treasury)
                .setTotalSupply(500L)
                .setMaxSupply(MAX_SUPPLY)
                .addAllCustomFees(customFees)
                .setAdminKey(getTokenKeyFromSpec(spec, TokenKeyType.ADMIN_KEY))
                .setKycKey(getTokenKeyFromSpec(spec, TokenKeyType.KYC_KEY))
                .setFreezeKey(getTokenKeyFromSpec(spec, TokenKeyType.FREEZE_KEY))
                .setWipeKey(getTokenKeyFromSpec(spec, TokenKeyType.WIPE_KEY))
                .setSupplyKey(getTokenKeyFromSpec(spec, TokenKeyType.SUPPLY_KEY))
                .setFeeScheduleKey(getTokenKeyFromSpec(spec, TokenKeyType.FEE_SCHEDULE_KEY))
                .setPauseKey(getTokenKeyFromSpec(spec, TokenKeyType.PAUSE_KEY))
                .build();
    }

    @NotNull
    private ArrayList<CustomFee> getCustomFees(final HapiSpec spec) {
        final var fixedFee = FixedFee.newBuilder().setAmount(500L).build();
        final var customFixedFee = CustomFee.newBuilder()
                .setFixedFee(fixedFee)
                .setFeeCollectorAccountId(spec.registry().getAccountID(HTS_COLLECTOR))
                .build();

        final var fraction = Fraction.newBuilder()
                .setNumerator(NUMERATOR)
                .setDenominator(DENOMINATOR)
                .build();
        final var fractionalFee = FractionalFee.newBuilder()
                .setFractionalAmount(fraction)
                .setMinimumAmount(MINIMUM_TO_COLLECT)
                .setMaximumAmount(MAXIMUM_TO_COLLECT)
                .build();
        final var customFractionalFee = CustomFee.newBuilder()
                .setFractionalFee(fractionalFee)
                .setFeeCollectorAccountId(spec.registry().getAccountID(TOKEN_TREASURY))
                .build();

        final var customFees = new ArrayList<CustomFee>();
        customFees.add(customFixedFee);
        customFees.add(customFractionalFee);
        return customFees;
    }

    private TokenInfo getTokenInfoStructForNonFungibleToken(
            final HapiSpec spec,
            final String tokenName,
            final String symbol,
            final String memo,
            final AccountID treasury,
            final long expirySecond) {
        final var autoRenewAccount = spec.registry().getAccountID(AUTO_RENEW_ACCOUNT);

        return TokenInfo.newBuilder()
                .setLedgerId(fromString("0x03"))
                .setSupplyTypeValue(TokenSupplyType.FINITE_VALUE)
                .setExpiry(Timestamp.newBuilder().setSeconds(expirySecond))
                .setAutoRenewAccount(autoRenewAccount)
                .setAutoRenewPeriod(Duration.newBuilder()
                        .setSeconds(THREE_MONTHS_IN_SECONDS)
                        .build())
                .setSymbol(symbol)
                .setName(tokenName)
                .setMemo(memo)
                .setTreasury(treasury)
                .setTotalSupply(1L)
                .setMaxSupply(10L)
                .addAllCustomFees(getCustomFeeForNFT(spec))
                .setAdminKey(getTokenKeyFromSpec(spec, TokenKeyType.ADMIN_KEY))
                .setKycKey(getTokenKeyFromSpec(spec, TokenKeyType.KYC_KEY))
                .setFreezeKey(getTokenKeyFromSpec(spec, TokenKeyType.FREEZE_KEY))
                .setWipeKey(getTokenKeyFromSpec(spec, TokenKeyType.WIPE_KEY))
                .setSupplyKey(getTokenKeyFromSpec(spec, TokenKeyType.SUPPLY_KEY))
                .setFeeScheduleKey(getTokenKeyFromSpec(spec, TokenKeyType.FEE_SCHEDULE_KEY))
                .setPauseKey(getTokenKeyFromSpec(spec, TokenKeyType.PAUSE_KEY))
                .build();
    }

    @NotNull
    private ArrayList<CustomFee> getCustomFeeForNFT(final HapiSpec spec) {
        final var fraction = Fraction.newBuilder()
                .setNumerator(NUMERATOR)
                .setDenominator(DENOMINATOR)
                .build();
        final var fallbackFee = FixedFee.newBuilder()
                .setAmount(100L)
                .setDenominatingTokenId(spec.registry().getTokenID(FEE_DENOM))
                .build();
        final var royaltyFee = RoyaltyFee.newBuilder()
                .setExchangeValueFraction(fraction)
                .setFallbackFee(fallbackFee)
                .build();

        final var customRoyaltyFee = CustomFee.newBuilder()
                .setRoyaltyFee(royaltyFee)
                .setFeeCollectorAccountId(spec.registry().getAccountID(HTS_COLLECTOR))
                .build();

        final var customFees = new ArrayList<CustomFee>();
        customFees.add(customRoyaltyFee);

        return customFees;
    }

    private Key getTokenKeyFromSpec(final HapiSpec spec, final TokenKeyType type) {
        final var key = spec.registry().getKey(type.name());

        final var keyBuilder = Key.newBuilder();

        if (key.getContractID().getContractNum() > 0) {
            keyBuilder.setContractID(key.getContractID());
        }
        if (key.getEd25519().toByteArray().length > 0) {
            keyBuilder.setEd25519(key.getEd25519());
        }
        if (key.getECDSASecp256K1().toByteArray().length > 0) {
            keyBuilder.setECDSASecp256K1(key.getECDSASecp256K1());
        }
        if (key.getDelegatableContractId().getContractNum() > 0) {
            keyBuilder.setDelegatableContractId(key.getDelegatableContractId());
        }

        return keyBuilder.build();
    }

    private ByteString fromString(final String value) {
        return ByteString.copyFrom(Bytes.fromHexString(value).toArray());
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
