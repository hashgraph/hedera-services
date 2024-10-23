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

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.utilops.RunnableOp;
import com.hedera.services.bdd.suites.utils.contracts.precompile.TokenKeyType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@HapiTestLifecycle
@DisplayName("updateTokenExpiryInfo")
public class TokenExpiryInfoSuite {
    private static final Address ZERO_ADDRESS = asHeadlongAddress(new byte[20]);
    private static final Address MISSING_LONG_ZERO_ADDRESS = asHeadlongAddress(Long.toHexString(Integer.MAX_VALUE));
    private static final String TOKEN_EXPIRY_CONTRACT = "TokenExpiryContract";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String UPDATED_AUTO_RENEW_ACCOUNT = "updatedAutoRenewAccount";
    private static final KeyShape THRESHOLD_KEY_SHAPE = KeyShape.threshOf(1, ED25519, CONTRACT);
    private static final String ADMIN_KEY = TokenKeyType.ADMIN_KEY.name();
    private static final String CONTRACT_KEY = "contractKey";
    public static final String GET_EXPIRY_INFO_FOR_TOKEN = "getExpiryInfoForToken";
    public static final String UPDATE_EXPIRY_INFO_FOR_TOKEN_AND_READ_LATEST_INFO =
            "updateExpiryInfoForTokenAndReadLatestInfo";
    public static final long MONTH_IN_SECONDS = 7_000_000L;
    public static final int GAS_TO_OFFER = 1_000_000;

    @Contract(contract = "TokenExpiryContract", creationGas = 1_000_000L)
    static SpecContract tokenExpiryContract;

    @Account
    static SpecAccount newAutoRenewAccount;

    @FungibleToken(name = "mutableToken", useAutoRenewAccount = true)
    static SpecFungibleToken mutableToken;

    @HapiTest
    @DisplayName("cannot update a missing token's expiry info")
    final Stream<DynamicTest> cannotUpdateMissingToken() {
        return hapiTest(
                // This function takes four arguments---a token address, an expiry second, an auto-renew account
                // address, and an auto-renew period---and tries to update the token at that address with the given
                // metadata; when expiry second is zero like here, it is ignored
                tokenExpiryContract
                        .call("updateExpiryInfoForToken", ZERO_ADDRESS, 0L, newAutoRenewAccount, MONTH_IN_SECONDS)
                        .andAssert(txn -> txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED, INVALID_TOKEN_ID)));
    }

    @HapiTest
    @DisplayName("cannot update expiry metadata without authorization")
    final Stream<DynamicTest> cannotUpdateWithoutAuthorization() {
        return hapiTest(
                // This function takes four arguments---a token address, an expiry second, an auto-renew account
                // address, and an auto-renew period---and tries to update the token at that address with the given
                // metadata; when expiry second is zero like here, it is ignored
                tokenExpiryContract
                        .call("updateExpiryInfoForToken", mutableToken, 0L, newAutoRenewAccount, MONTH_IN_SECONDS)
                        .andAssert(txn -> txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED, INVALID_SIGNATURE)));
    }

    @Nested
    @DisplayName("when authorized")
    class WhenAuthorized {
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(
                    newAutoRenewAccount.authorizeContract(tokenExpiryContract),
                    mutableToken.authorizeContracts(tokenExpiryContract));
        }

        @HapiTest
        @DisplayName("still cannot set an invalid expiry")
        final Stream<DynamicTest> cannotSetInvalidExpiry() {
            return hapiTest(
                    // This function takes four arguments---a token address, an expiry second, an auto-renew account
                    // address, and an auto-renew period---and tries to update the token at that address with the given
                    // metadata; here we set an invalid expiration time in 1970
                    tokenExpiryContract
                            .call("updateExpiryInfoForToken", mutableToken, 1L, newAutoRenewAccount, MONTH_IN_SECONDS)
                            .andAssert(txn -> txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED, INVALID_EXPIRATION_TIME)));
        }

        @HapiTest
        @DisplayName("still cannot set an invalid auto-renew account")
        final Stream<DynamicTest> cannotSetInvalidAutoRenewAccount() {
            return hapiTest(
                    // This function takes four arguments---a token address, an expiry second, an auto-renew account
                    // address, and an auto-renew period---and tries to update the token at that address with the given
                    // metadata; here we set an invalid auto-renew account address
                    tokenExpiryContract
                            .call(
                                    "updateExpiryInfoForToken",
                                    mutableToken,
                                    0L,
                                    MISSING_LONG_ZERO_ADDRESS,
                                    MONTH_IN_SECONDS)
                            .andAssert(
                                    txn -> txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED, INVALID_AUTORENEW_ACCOUNT)));
        }

        @HapiTest
        @DisplayName("still cannot set an invalid auto-renew period")
        final Stream<DynamicTest> cannotSetInvalidAutoRenewPeriod() {
            return hapiTest(
                    // This function takes four arguments---a token address, an expiry second, an auto-renew account
                    // address, and an auto-renew period---and tries to update the token at that address with the given
                    // metadata; here we pass 0 and 0x00 to leave the expiration and auto-renew account untouched, but
                    // set an invalid auto-renew account period of 1 second
                    tokenExpiryContract
                            .call("updateExpiryInfoForToken", mutableToken, 0L, ZERO_ADDRESS, 1L)
                            .andAssert(txn -> txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED, INVALID_RENEWAL_PERIOD)));
        }

        @HapiTest
        @DisplayName("can update expiry metadata")
        final Stream<DynamicTest> canUpdateExpiryMetadata() {
            return hapiTest(mutableToken
                    .getInfo()
                    .andDo(info -> blockingOrder(
                            // This function takes four arguments---a token address, an expiry second, an auto-renew
                            // account
                            // address, and an auto-renew period---and tries to update the token at that address with
                            // the given
                            // metadata; here we pass 0 and 0x00 to leave the expiration and auto-renew account
                            // untouched, but
                            // set an invalid auto-renew account period of 1 second
                            tokenExpiryContract.call(
                                    "updateExpiryInfoForToken",
                                    mutableToken,
                                    info.getExpiry().getSeconds() + 1L,
                                    newAutoRenewAccount,
                                    THREE_MONTHS_IN_SECONDS + 2L),
                            mutableToken.getInfo().andAssert(op -> op.logged()
                                    .hasExpiry(info.getExpiry().getSeconds() + 1L)
                                    .hasAutoRenewPeriod(THREE_MONTHS_IN_SECONDS + 2L)
                                    .hasAutoRenewAccount(newAutoRenewAccount.name())))));
        }
    }

    @HapiTest
    @SuppressWarnings("java:S1192") // "use already defined const instead of copying its value here" - not this time
    final Stream<DynamicTest> updateExpiryInfoForTokenAndReadLatestInfo() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> updatedAutoRenewAccountID = new AtomicReference<>();
        final var someValidExpiry = new AtomicLong();
        return defaultHapiSpec("updateExpiryInfoForTokenAndReadLatestInfo")
                .given(
                        new RunnableOp(() ->
                                someValidExpiry.set(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS + 123L)),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(UPDATED_AUTO_RENEW_ACCOUNT)
                                .keyShape(SigControl.ED25519_ON)
                                .balance(0L)
                                .exposingCreatedIdTo(updatedAutoRenewAccountID::set),
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(TOKEN_EXPIRY_CONTRACT),
                        contractCreate(TOKEN_EXPIRY_CONTRACT).gas(1_000_000L),
                        tokenCreate(VANILLA_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .treasury(TOKEN_TREASURY)
                                .expiry(100)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(1000)
                                .initialSupply(500L)
                                .adminKey(ADMIN_KEY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        newKeyNamed(CONTRACT_KEY)
                                .shape(THRESHOLD_KEY_SHAPE.signedWith(sigs(ON, TOKEN_EXPIRY_CONTRACT))),
                        tokenUpdate(VANILLA_TOKEN).adminKey(CONTRACT_KEY).signedByPayerAnd(ADMIN_KEY, CONTRACT_KEY),
                        cryptoUpdate(UPDATED_AUTO_RENEW_ACCOUNT).key(CONTRACT_KEY),
                        contractCall(
                                        TOKEN_EXPIRY_CONTRACT,
                                        UPDATE_EXPIRY_INFO_FOR_TOKEN_AND_READ_LATEST_INFO,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        someValidExpiry.get(),
                                        HapiParserUtil.asHeadlongAddress(asAddress(updatedAutoRenewAccountID.get())),
                                        MONTH_IN_SECONDS)
                                .alsoSigningWithFullPrefix(ADMIN_KEY, UPDATED_AUTO_RENEW_ACCOUNT)
                                .via("updateExpiryAndReadLatestInfoTxn")
                                .gas(GAS_TO_OFFER)
                                .payingWith(GENESIS))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                "updateExpiryAndReadLatestInfoTxn",
                                SUCCESS,
                                recordWith().status(SUCCESS),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_GET_TOKEN_EXPIRY_INFO)
                                                        .withStatus(SUCCESS)
                                                        .withExpiry(
                                                                someValidExpiry.get(),
                                                                updatedAutoRenewAccountID.get(),
                                                                MONTH_IN_SECONDS)))))));
    }

    @HapiTest
    final Stream<DynamicTest> getExpiryInfoForToken() {

        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return hapiTest(
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                newKeyNamed(ADMIN_KEY),
                uploadInitCode(TOKEN_EXPIRY_CONTRACT),
                contractCreate(TOKEN_EXPIRY_CONTRACT).gas(1_000_000L),
                tokenCreate(VANILLA_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .treasury(TOKEN_TREASURY)
                        .expiry(100)
                        .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .maxSupply(1000)
                        .initialSupply(500L)
                        .adminKey(ADMIN_KEY)
                        .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_EXPIRY_CONTRACT,
                                        GET_EXPIRY_INFO_FOR_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(TokenID.newBuilder().build())))
                                .via("expiryForInvalidTokenIDTxn")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .payingWith(GENESIS),
                        contractCall(
                                        TOKEN_EXPIRY_CONTRACT,
                                        GET_EXPIRY_INFO_FOR_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .via("expiryTxn")
                                .gas(GAS_TO_OFFER)
                                .payingWith(GENESIS),
                        contractCallLocal(
                                TOKEN_EXPIRY_CONTRACT,
                                GET_EXPIRY_INFO_FOR_TOKEN,
                                HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get()))))),
                childRecordsCheck(
                        "expiryForInvalidTokenIDTxn",
                        CONTRACT_REVERT_EXECUTED,
                        recordWith().status(INVALID_TOKEN_ID)),
                withOpContext((spec, opLog) -> {
                    final var getTokenInfoQuery = getTokenInfo(VANILLA_TOKEN);
                    allRunFor(spec, getTokenInfoQuery);
                    final var expirySecond = getTokenInfoQuery
                            .getResponse()
                            .getTokenGetInfo()
                            .getTokenInfo()
                            .getExpiry()
                            .getSeconds();
                    allRunFor(
                            spec,
                            childRecordsCheck(
                                    "expiryTxn",
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_GET_TOKEN_EXPIRY_INFO)
                                                            .withStatus(SUCCESS)
                                                            .withExpiry(
                                                                    expirySecond,
                                                                    spec.registry()
                                                                            .getAccountID(AUTO_RENEW_ACCOUNT),
                                                                    THREE_MONTHS_IN_SECONDS)))));
                }));
    }
}
