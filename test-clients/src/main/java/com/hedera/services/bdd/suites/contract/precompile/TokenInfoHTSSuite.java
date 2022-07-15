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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.expandByteArrayTo32Length;

import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenInfoHTSSuite extends HapiApiSuite {

    private static final Logger LOG = LogManager.getLogger(TokenInfoHTSSuite.class);

    private static final String TOKEN_INFO_CONTRACT = "TokenInfoContract";
    private static final String ADMIN_KEY = "adminKey";
    private static final String KYC_KEY = "kycKey";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String WIPE_KEY = "wipeKey";
    private static final String FEE_SCHEDULE_KEY = "feeScheduleKey";
    private static final String PAUSE_KEY = "pauseKey";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String FEE_DENOM = "denom";
    private static final String HTS_COLLECTOR = "denomFee";
    private static final int NUMERATOR = 1;
    private static final int DENOMINATOR = 2;
    private static final int MINIMUM_TO_COLLECT = 5;
    private static final int MAXIMUM_TO_COLLECT = 400;

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
        return List.of();
    }

    List<HapiApiSpec> positiveSpecs() {
        return List.of(
                happyPathGetTokenInfo(),
                happyPathGetFungibleTokenInfo(),
                happyPathGetNonFungibleTokenInfo());
    }

    private HapiApiSpec happyPathGetTokenInfo() {
        final String tokenInfoTxn = "TokenInfoTxn";
        final String memo = "JUMP";
        final String tokenName = "primary";
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
                        tokenCreate(tokenName)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(memo)
                                .name(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(1000)
                                .initialSupply(500)
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
                                .via("createTxn"))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                "getInformationForToken",
                                                                Tuple.singleton(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        tokenName)))))
                                                        .via(tokenInfoTxn)
                                                        .gas(1_000_000L))))
                .then(getTxnRecord(tokenInfoTxn).andAllChildRecords().logged());
    }

    private HapiApiSpec happyPathGetFungibleTokenInfo() {
        final String fungibleTokenInfoTxn = "FungibleTokenInfoTxn";
        final String memo = "JUMP";
        final String tokenName = "FungibleToken";
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
                                .entityMemo(memo)
                                .name(tokenName)
                                .symbol("FT")
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(1000)
                                .initialSupply(500)
                                .decimals(1)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey(WIPE_KEY)
                                .feeScheduleKey(FEE_SCHEDULE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .withCustom(fixedHbarFee(654L, HTS_COLLECTOR))
                                .withCustom(
                                        fractionalFee(
                                                NUMERATOR,
                                                DENOMINATOR,
                                                MINIMUM_TO_COLLECT,
                                                OptionalLong.of(MAXIMUM_TO_COLLECT),
                                                TOKEN_TREASURY))
                                .via("createTxn"))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                "getInformationForFungibleToken",
                                                                Tuple.singleton(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        tokenName)))))
                                                        .via(fungibleTokenInfoTxn)
                                                        .gas(1_000_000L))))
                .then(getTxnRecord(fungibleTokenInfoTxn).andAllChildRecords().logged());
    }

    private HapiApiSpec happyPathGetNonFungibleTokenInfo() {
        final String nonFungibleTokenInfoTxn = "NonFungibleTokenInfoTxn";
        final String memo = "JUMP";
        final String tokenName = "NonFungibleToken";
        final String owner = "NFT Owner";
        final String spender = "NFT Spender";
        final ByteString meta = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
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
                        tokenCreate(tokenName)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(memo)
                                .name(tokenName)
                                .symbol("NFT")
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(10)
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
                                .via("createTxn"),
                        mintToken(tokenName, List.of(meta)),
                        tokenAssociate(owner, List.of(tokenName)),
                        tokenAssociate(spender, List.of(tokenName)),
                        grantTokenKyc(tokenName, owner),
                        cryptoTransfer(
                                TokenMovement.movingUnique(tokenName, 1L)
                                        .between(TOKEN_TREASURY, owner)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(owner, tokenName, spender, false, List.of(1L))
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
                                                                "getInformationForNonFungibleToken",
                                                                Tuple.of(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        spec.registry()
                                                                                                .getTokenID(
                                                                                                        tokenName))),
                                                                        1L))
                                                        .via(nonFungibleTokenInfoTxn)
                                                        .gas(1_000_000L))))
                .then(getTxnRecord(nonFungibleTokenInfoTxn).andAllChildRecords().logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
