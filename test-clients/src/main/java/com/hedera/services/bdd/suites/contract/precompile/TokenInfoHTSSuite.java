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
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
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
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.expandByteArrayTo32Length;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.bdd.suites.utils.contracts.precompile.TokenKeyType;
import com.hedera.services.contracts.ParsingConstants.FunctionType;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class TokenInfoHTSSuite extends HapiApiSuite {

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
    private static final String HTS_COLLECTOR = "denomFee";
    private static final String CREATE_TXN = "CreateTxn";
    private static final String TOKEN_INFO_TXN = "TokenInfoTxn";
    private static final String FUNGIBLE_TOKEN_INFO_TXN = "FungibleTokenInfoTxn";
    private static final String NON_FUNGIBLE_TOKEN_INFO_TXN = "NonFungibleTokenInfoTxn";
    private static final String GET_TOKEN_INFO_TXN = "GetTokenInfo";
    private static final String LEDGER_ID = "0x03";
    private static final String SYMBOL = "T";
    private static final String FUNGIBLE_SYMBOL = "FT";
    private static final String NON_FUNGIBLE_SYMBOL = "NFT";
    private static final String META = "First";
    private static final String MEMO = "JUMP";
    private static final String PRIMARY_TOKEN_NAME = "primary";
    private static final String NON_FUNGIBLE_TOKEN_NAME = "NonFungibleToken";
    private static final String GET_INFORMATION_FOR_TOKEN = "getInformationForToken";
    private static final String GET_INFORMATION_FOR_FUNGIBLE_TOKEN =
            "getInformationForFungibleToken";
    private static final String GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN =
            "getInformationForNonFungibleToken";
    private static final int NUMERATOR = 1;
    private static final int DENOMINATOR = 2;
    private static final int MINIMUM_TO_COLLECT = 5;
    private static final int MAXIMUM_TO_COLLECT = 400;
    private static final int MAX_SUPPLY = 1000;
    private static final long MASK_INT_AS_UNSIGNED_LONG = (1L << 32) - 1;

    public static void main(String... args) {
        new TokenInfoHTSSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiApiSpec> negativeSpecs() {
        return List.of(
                //                getInfoOnDeletedFungibleTokenWorks(),
                //                getInfoOnInvalidFungibleTokenFails(),
                //                getInfoOnDeletedNonFungibleTokenFails(),
                //                getInfoOnInvalidNonFungibleTokenFails()
                );
    }

    List<HapiApiSpec> positiveSpecs() {
        return List.of(
                happyPathGetTokenInfo()
                //                happyPathGetFungibleTokenInfo(),
                //                happyPathGetNonFungibleTokenInfo()
                );
    }

    private HapiApiSpec happyPathGetTokenInfo() {
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
                                .withCustom(
                                        fractionalFee(
                                                NUMERATOR,
                                                DENOMINATOR,
                                                MINIMUM_TO_COLLECT,
                                                OptionalLong.of(MAXIMUM_TO_COLLECT),
                                                TOKEN_TREASURY))
                                .via(CREATE_TXN),
                        getTokenInfo(PRIMARY_TOKEN_NAME).via(GET_TOKEN_INFO_TXN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_TOKEN,
                                                                Tuple.singleton(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        PRIMARY_TOKEN_NAME)))))
                                                        .via(TOKEN_INFO_TXN)
                                                        .gas(1_000_000L),
                                                contractCallLocal(
                                                        TOKEN_INFO_CONTRACT,
                                                        GET_INFORMATION_FOR_TOKEN,
                                                        Tuple.singleton(
                                                                expandByteArrayTo32Length(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                PRIMARY_TOKEN_NAME))))))))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getTokenInfoQuery = getTokenInfo(PRIMARY_TOKEN_NAME);
                                    allRunFor(spec, getTokenInfoQuery);
                                    final var expirySecond =
                                            getTokenInfoQuery
                                                    .getResponse()
                                                    .getTokenGetInfo()
                                                    .getTokenInfo()
                                                    .getExpiry()
                                                    .getSeconds();

                                    allRunFor(
                                            spec,
                                            getTxnRecord(TOKEN_INFO_TXN)
                                                    .andAllChildRecords()
                                                    .logged(),
                                            childRecordsCheck(
                                                    TOKEN_INFO_TXN,
                                                    SUCCESS,
                                                    recordWith()
                                                            .status(SUCCESS)
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .contractCallResult(
                                                                                    htsPrecompileResult()
                                                                                            .forFunction(
                                                                                                    FunctionType
                                                                                                            .HAPI_GET_TOKEN_INFO)
                                                                                            .withStatus(
                                                                                                    SUCCESS)
                                                                                            .withTokenInfo(
                                                                                                    getTokenInfoStructForFungibleToken(
                                                                                                            spec,
                                                                                                            PRIMARY_TOKEN_NAME,
                                                                                                            SYMBOL,
                                                                                                            expirySecond))))));
                                }));
    }

    private HapiApiSpec happyPathGetFungibleTokenInfo() {
        final String tokenName = "FungibleToken";
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
                        tokenCreate(tokenName)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(MEMO)
                                .name(tokenName)
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
                                .withCustom(
                                        fractionalFee(
                                                NUMERATOR,
                                                DENOMINATOR,
                                                MINIMUM_TO_COLLECT,
                                                OptionalLong.of(MAXIMUM_TO_COLLECT),
                                                TOKEN_TREASURY))
                                .via(CREATE_TXN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_FUNGIBLE_TOKEN,
                                                                Tuple.singleton(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        tokenName)))))
                                                        .via(FUNGIBLE_TOKEN_INFO_TXN)
                                                        .gas(1_000_000L),
                                                contractCallLocal(
                                                        TOKEN_INFO_CONTRACT,
                                                        GET_INFORMATION_FOR_FUNGIBLE_TOKEN,
                                                        Tuple.singleton(
                                                                expandByteArrayTo32Length(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                tokenName))))))))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getTokenInfoQuery = getTokenInfo(tokenName);
                                    allRunFor(spec, getTokenInfoQuery);
                                    final var expirySecond =
                                            getTokenInfoQuery
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
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .contractCallResult(
                                                                                    htsPrecompileResult()
                                                                                            .forFunction(
                                                                                                    FunctionType
                                                                                                            .HAPI_GET_FUNGIBLE_TOKEN_INFO)
                                                                                            .withStatus(
                                                                                                    SUCCESS)
                                                                                            .withDecimals(
                                                                                                    decimals)
                                                                                            .withTokenInfo(
                                                                                                    getTokenInfoStructForFungibleToken(
                                                                                                            spec,
                                                                                                            tokenName,
                                                                                                            FUNGIBLE_SYMBOL,
                                                                                                            expirySecond))))));
                                }));
    }

    private HapiApiSpec happyPathGetNonFungibleTokenInfo() {
        final String owner = "NFT Owner";
        final String spender = "NFT Spender";
        final int maxSupply = 10;
        final ByteString meta = ByteString.copyFrom(META.getBytes(StandardCharsets.UTF_8));
        return defaultHapiSpec("HappyPathGetNonFungibleTokenInfo")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(owner),
                        cryptoCreate(spender),
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
                                .withCustom(
                                        royaltyFeeWithFallback(
                                                1,
                                                2,
                                                fixedHtsFeeInheritingRoyaltyCollector(
                                                        100, FEE_DENOM),
                                                HTS_COLLECTOR))
                                .via(CREATE_TXN),
                        mintToken(NON_FUNGIBLE_TOKEN_NAME, List.of(meta)),
                        tokenAssociate(owner, List.of(NON_FUNGIBLE_TOKEN_NAME)),
                        tokenAssociate(spender, List.of(NON_FUNGIBLE_TOKEN_NAME)),
                        grantTokenKyc(NON_FUNGIBLE_TOKEN_NAME, owner),
                        cryptoTransfer(
                                TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN_NAME, 1L)
                                        .between(TOKEN_TREASURY, owner)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(
                                        owner, NON_FUNGIBLE_TOKEN_NAME, spender, false, List.of(1L))
                                .via("approveTxn")
                                .logged()
                                .signedBy(DEFAULT_PAYER, owner)
                                .fee(ONE_HBAR))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                                                Tuple.of(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        NON_FUNGIBLE_TOKEN_NAME))),
                                                                        1L))
                                                        .via(NON_FUNGIBLE_TOKEN_INFO_TXN)
                                                        .gas(1_000_000L),
                                                contractCallLocal(
                                                        TOKEN_INFO_CONTRACT,
                                                        GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                                        Tuple.of(
                                                                expandByteArrayTo32Length(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                NON_FUNGIBLE_TOKEN_NAME))),
                                                                1L)))))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getTokenInfoQuery =
                                            getTokenInfo(NON_FUNGIBLE_TOKEN_NAME);
                                    allRunFor(spec, getTokenInfoQuery);
                                    final var expirySecond =
                                            getTokenInfoQuery
                                                    .getResponse()
                                                    .getTokenGetInfo()
                                                    .getTokenInfo()
                                                    .getExpiry()
                                                    .getSeconds();

                                    final var getNftTokenInfoQuery =
                                            getTokenNftInfo(NON_FUNGIBLE_TOKEN_NAME, 1L);
                                    allRunFor(spec, getNftTokenInfoQuery);
                                    final var creationTime =
                                            getNftTokenInfoQuery
                                                    .getResponse()
                                                    .getTokenGetNftInfo()
                                                    .getNft()
                                                    .getCreationTime();
                                    final var packedCreationTime =
                                            packedTime(
                                                    creationTime.getSeconds(),
                                                    creationTime.getNanos());

                                    final var ownerBytes =
                                            Utils.asAddress(spec.registry().getAccountID(owner));
                                    final var spenderBytes =
                                            Utils.asAddress(spec.registry().getAccountID(spender));

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
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .contractCallResult(
                                                                                    htsPrecompileResult()
                                                                                            .forFunction(
                                                                                                    FunctionType
                                                                                                            .HAPI_GET_NON_FUNGIBLE_TOKEN_INFO)
                                                                                            .withStatus(
                                                                                                    SUCCESS)
                                                                                            .withSerialNumber(
                                                                                                    1L)
                                                                                            .withCreationTime(
                                                                                                    packedCreationTime)
                                                                                            .withTokenUri(
                                                                                                    META)
                                                                                            .withOwner(
                                                                                                    ownerBytes)
                                                                                            .withSpender(
                                                                                                    spenderBytes)
                                                                                            .withTokenInfo(
                                                                                                    getTokenInfoStructForNonFungibleToken(
                                                                                                            spec,
                                                                                                            expirySecond))))));
                                }));
    }

    private HapiApiSpec getInfoOnDeletedFungibleTokenWorks() {
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
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_TOKEN,
                                                                Tuple.singleton(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        PRIMARY_TOKEN_NAME)))))
                                                        .via(TOKEN_INFO_TXN + 1)
                                                        .gas(1_000_000L)
                                                        .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_FUNGIBLE_TOKEN,
                                                                Tuple.singleton(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        PRIMARY_TOKEN_NAME)))))
                                                        .via(TOKEN_INFO_TXN + 2)
                                                        .gas(1_000_000L)
                                                        .hasKnownStatus(ResponseCodeEnum.SUCCESS))))
                .then(
                        getTxnRecord(TOKEN_INFO_TXN + 1).andAllChildRecords().logged(),
                        getTxnRecord(TOKEN_INFO_TXN + 2).andAllChildRecords().logged());
    }

    private HapiApiSpec getInfoOnInvalidFungibleTokenFails() {
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
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_TOKEN,
                                                                Tuple.singleton(new byte[32]))
                                                        .via(TOKEN_INFO_TXN + 1)
                                                        .gas(1_000_000L)
                                                        .hasKnownStatus(
                                                                ResponseCodeEnum
                                                                        .CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_FUNGIBLE_TOKEN,
                                                                Tuple.singleton(new byte[32]))
                                                        .via(TOKEN_INFO_TXN + 2)
                                                        .gas(1_000_000L)
                                                        .hasKnownStatus(
                                                                ResponseCodeEnum
                                                                        .CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(TOKEN_INFO_TXN + 1).andAllChildRecords().logged(),
                        getTxnRecord(TOKEN_INFO_TXN + 2).andAllChildRecords().logged());
    }

    private HapiApiSpec getInfoOnDeletedNonFungibleTokenFails() {
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
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                                                Tuple.of(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        NON_FUNGIBLE_TOKEN_NAME))),
                                                                        1L))
                                                        .via(NON_FUNGIBLE_TOKEN_INFO_TXN)
                                                        .gas(1_000_000L)
                                                        .hasKnownStatus(ResponseCodeEnum.SUCCESS))))
                .then(getTxnRecord(NON_FUNGIBLE_TOKEN_INFO_TXN).andAllChildRecords().logged());
    }

    private HapiApiSpec getInfoOnInvalidNonFungibleTokenFails() {
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
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                                                Tuple.of(new byte[32], 1L))
                                                        .via(NON_FUNGIBLE_TOKEN_INFO_TXN + 1)
                                                        .gas(1_000_000L)
                                                        .hasKnownStatus(
                                                                ResponseCodeEnum
                                                                        .CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN,
                                                                Tuple.of(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        NON_FUNGIBLE_TOKEN_NAME))),
                                                                        2L))
                                                        .via(NON_FUNGIBLE_TOKEN_INFO_TXN + 2)
                                                        .gas(1_000_000L)
                                                        .hasKnownStatus(
                                                                ResponseCodeEnum
                                                                        .CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(NON_FUNGIBLE_TOKEN_INFO_TXN + 1).andAllChildRecords().logged(),
                        getTxnRecord(NON_FUNGIBLE_TOKEN_INFO_TXN + 2)
                                .andAllChildRecords()
                                .logged());
    }

    private TokenInfo getTokenInfoStructForFungibleToken(
            final HapiApiSpec spec,
            final String tokenName,
            final String symbol,
            final long expirySecond) {
        final var autoRenewAccount = spec.registry().getAccountID(AUTO_RENEW_ACCOUNT);
        final var expiry =
                new Expiry(
                        expirySecond,
                        Bytes.wrap(expandByteArrayTo32Length(Utils.asAddress(autoRenewAccount))),
                        THREE_MONTHS_IN_SECONDS);
        final var treasury = spec.registry().getAccountID(TOKEN_TREASURY);
        final var token =
                new HederaToken(
                        tokenName,
                        symbol,
                        Bytes.wrap(expandByteArrayTo32Length(Utils.asAddress(treasury))),
                        MEMO,
                        true,
                        MAX_SUPPLY,
                        false,
                        getTokenKeys(spec),
                        expiry);

        final var fixedFees = new ArrayList<FixedFee>();
        final var fixedFeeCollector =
                Bytes.wrap(
                        expandByteArrayTo32Length(
                                Utils.asAddress(spec.registry().getAccountID(HTS_COLLECTOR))));
        final var fixedFee =
                new FixedFee(500L, Bytes.wrap(new byte[32]), true, false, fixedFeeCollector);
        fixedFees.add(fixedFee);

        final var fractionalFees = new ArrayList<FractionalFee>();
        final var fractionalFeeCollector =
                Bytes.wrap(
                        expandByteArrayTo32Length(
                                Utils.asAddress(spec.registry().getAccountID(TOKEN_TREASURY))));
        final var fractionalFee =
                new FractionalFee(
                        NUMERATOR,
                        DENOMINATOR,
                        MINIMUM_TO_COLLECT,
                        MAXIMUM_TO_COLLECT,
                        false,
                        fractionalFeeCollector);
        fractionalFees.add(fractionalFee);

        return new TokenInfo(
                token,
                500L,
                false,
                false,
                false,
                fixedFees,
                fractionalFees,
                new ArrayList<>(),
                LEDGER_ID);
    }

    private TokenInfo getTokenInfoStructForNonFungibleToken(
            final HapiApiSpec spec, final long expirySecond) {
        final var autoRenewAccount = spec.registry().getAccountID(AUTO_RENEW_ACCOUNT);
        final var expiry =
                new Expiry(
                        expirySecond,
                        Bytes.wrap(expandByteArrayTo32Length(Utils.asAddress(autoRenewAccount))),
                        THREE_MONTHS_IN_SECONDS);
        final var treasury = spec.registry().getAccountID(TOKEN_TREASURY);
        final var token =
                new HederaToken(
                        NON_FUNGIBLE_TOKEN_NAME,
                        NON_FUNGIBLE_SYMBOL,
                        Bytes.wrap(expandByteArrayTo32Length(Utils.asAddress(treasury))),
                        MEMO,
                        true,
                        10L,
                        false,
                        getTokenKeys(spec),
                        expiry);

        final var royaltyFees = new ArrayList<RoyaltyFee>();
        final var royaltyFeeCollector =
                Bytes.wrap(
                        expandByteArrayTo32Length(
                                Utils.asAddress(spec.registry().getAccountID(HTS_COLLECTOR))));
        final var tokenDenomAddress =
                Bytes.wrap(
                        expandByteArrayTo32Length(
                                Utils.asAddress(spec.registry().getTokenID(FEE_DENOM))));
        final var royaltyFee =
                new RoyaltyFee(
                        NUMERATOR, DENOMINATOR, 100, tokenDenomAddress, false, royaltyFeeCollector);
        royaltyFees.add(royaltyFee);

        return new TokenInfo(
                token,
                1L,
                false,
                false,
                false,
                new ArrayList<>(),
                new ArrayList<>(),
                royaltyFees,
                LEDGER_ID);
    }

    private List<TokenKey> getTokenKeys(final HapiApiSpec spec) {
        final var tokenKeys = new ArrayList<TokenKey>();
        tokenKeys.add(getTokenKeyFromSpec(spec, TokenKeyType.ADMIN_KEY));
        tokenKeys.add(getTokenKeyFromSpec(spec, TokenKeyType.KYC_KEY));
        tokenKeys.add(getTokenKeyFromSpec(spec, TokenKeyType.FREEZE_KEY));
        tokenKeys.add(getTokenKeyFromSpec(spec, TokenKeyType.WIPE_KEY));
        tokenKeys.add(getTokenKeyFromSpec(spec, TokenKeyType.SUPPLY_KEY));
        tokenKeys.add(getTokenKeyFromSpec(spec, TokenKeyType.FEE_SCHEDULE_KEY));
        tokenKeys.add(getTokenKeyFromSpec(spec, TokenKeyType.PAUSE_KEY));
        return tokenKeys;
    }

    private TokenKey getTokenKeyFromSpec(final HapiApiSpec spec, final TokenKeyType type) {
        final var key = spec.registry().getKey(type.name());
        final var keyValue =
                new KeyValue(
                        false,
                        key.getContractID().getContractNum() > 0
                                ? Bytes.wrap(
                                        expandByteArrayTo32Length(
                                                Utils.asAddress(key.getContractID())))
                                : null,
                        key.getEd25519(),
                        key.getECDSASecp256K1(),
                        key.getDelegatableContractId().getContractNum() > 0
                                ? Bytes.wrap(
                                        expandByteArrayTo32Length(
                                                Utils.asAddress(key.getDelegatableContractId())))
                                : null);
        return new TokenKey(type.value(), keyValue);
    }

    private static long packedTime(long seconds, int nanos) {
        return seconds << 32 | (nanos & MASK_INT_AS_UNSIGNED_LONG);
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
