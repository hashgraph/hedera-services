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
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.utils.contracts.precompile.TokenKeyType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class TokenExpiryInfoSuite {
    private static final String TOKEN_EXPIRY_CONTRACT = "TokenExpiryContract";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String ADMIN_KEY = TokenKeyType.ADMIN_KEY.name();
    public static final String GET_EXPIRY_INFO_FOR_TOKEN = "getExpiryInfoForToken";
    public static final int GAS_TO_OFFER = 1_000_000;

    @HapiTest
    final Stream<DynamicTest> getExpiryInfoForToken() {

        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return defaultHapiSpec(
                        "GetExpiryInfoForToken",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
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
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))))
                .when(withOpContext((spec, opLog) -> allRunFor(
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
                                HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get()))))))
                .then(
                        childRecordsCheck(
                                "expiryForInvalidTokenIDTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)
                                //                                        .contractCallResult(resultWith()
                                //
                                // .contractCallResult(htsPrecompileResult()
                                //
                                // .forFunction(FunctionType.HAPI_GET_TOKEN_EXPIRY_INFO)
                                //                                                        .withStatus(INVALID_TOKEN_ID)
                                //                                                        .withExpiry(0,
                                // AccountID.getDefaultInstance(), 0)))
                                ),
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
                                                                    .forFunction(
                                                                            FunctionType.HAPI_GET_TOKEN_EXPIRY_INFO)
                                                                    .withStatus(SUCCESS)
                                                                    .withExpiry(
                                                                            expirySecond,
                                                                            spec.registry()
                                                                                    .getAccountID(AUTO_RENEW_ACCOUNT),
                                                                            THREE_MONTHS_IN_SECONDS)))));
                        }));
    }
}
