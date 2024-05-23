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

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class GrantRevokeKycV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(GrantRevokeKycV1SecurityModelSuite.class);
    public static final String GRANT_REVOKE_KYC_CONTRACT = "GrantRevokeKyc";
    private static final String IS_KYC_GRANTED = "isKycGranted";
    public static final String TOKEN_GRANT_KYC = "tokenGrantKyc";
    public static final String TOKEN_REVOKE_KYC = "tokenRevokeKyc";

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String ACCOUNT = "anybody";
    public static final String SECOND_ACCOUNT = "anybodySecond";
    private static final String KYC_KEY = "kycKey";

    public static void main(String... args) {
        new GrantRevokeKycV1SecurityModelSuite().runSuiteSync();
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
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<Stream<DynamicTest>> negativeSpecs() {
        return List.of();
    }

    List<Stream<DynamicTest>> positiveSpecs() {
        return List.of(grantRevokeKycSpec());
    }

    final Stream<DynamicTest> grantRevokeKycSpec() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> secondAccountID = new AtomicReference<>();

        return propertyPreservingHapiSpec("grantRevokeKycSpec")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(KYC_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .key(KYC_KEY)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(SECOND_ACCOUNT).exposingCreatedIdTo(secondAccountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .kycKey(KYC_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(GRANT_REVOKE_KYC_CONTRACT),
                        contractCreate(GRANT_REVOKE_KYC_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(SECOND_ACCOUNT, VANILLA_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        GRANT_REVOKE_KYC_CONTRACT,
                                        TOKEN_GRANT_KYC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(secondAccountID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("GrantKycTx")
                                .gas(GAS_TO_OFFER),
                        getAccountDetails(SECOND_ACCOUNT)
                                .hasToken(ExpectedTokenRel.relationshipWith(VANILLA_TOKEN)
                                        .kyc(TokenKycStatus.Granted)),
                        contractCallLocal(
                                GRANT_REVOKE_KYC_CONTRACT,
                                IS_KYC_GRANTED,
                                HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                HapiParserUtil.asHeadlongAddress(asAddress(secondAccountID.get()))),
                        contractCall(
                                        GRANT_REVOKE_KYC_CONTRACT,
                                        TOKEN_REVOKE_KYC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(secondAccountID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("RevokeKycTx")
                                .gas(GAS_TO_OFFER),
                        contractCall(
                                        GRANT_REVOKE_KYC_CONTRACT,
                                        IS_KYC_GRANTED,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(secondAccountID.get())))
                                .signedBy(GENESIS, ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("IsKycTx")
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                "GrantKycTx",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                "RevokeKycTx",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                "IsKycTx",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_IS_KYC)
                                                        .withIsKyc(false)
                                                        .withStatus(SUCCESS)))));
    }
}
