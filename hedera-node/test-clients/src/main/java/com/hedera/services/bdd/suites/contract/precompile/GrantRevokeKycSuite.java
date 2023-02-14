/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GrantRevokeKycSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(GrantRevokeKycSuite.class);
    private static final String GRANT_REVOKE_KYC_CONTRACT = "GrantRevokeKyc";
    private static final String IS_KYC_GRANTED = "isKycGranted";
    private static final String TOKEN_GRANT_KYC = "tokenGrantKyc";
    private static final String TOKEN_REVOKE_KYC = "tokenRevokeKyc";

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String ACCOUNT = "anybody";
    private static final String SECOND_ACCOUNT = "anybodySecond";
    private static final String KYC_KEY = "kycKey";
    private static final String NON_KYC_KEY = "nonKycKey";
    private static final String TOKEN_WITHOUT_KEY = "withoutKey";

    public static void main(String... args) {
        new GrantRevokeKycSuite().runSuiteAsync();
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
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(grantRevokeKycFail());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(grantRevokeKycSpec(), grantRevokeKycSpecWithAliasLocalCall());
    }

    private HapiSpec grantRevokeKycFail() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> secondAccountID = new AtomicReference<>();
        final AtomicReference<TokenID> tokenWithoutKeyID = new AtomicReference<>();
        final var invalidTokenID = TokenID.newBuilder().build();

        return defaultHapiSpec("GrantRevokeKycFail")
                .given(
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(NON_KYC_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(SECOND_ACCOUNT).exposingCreatedIdTo(secondAccountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN_WITHOUT_KEY)
                                .exposingCreatedIdTo(id -> tokenWithoutKeyID.set(asToken(id))),
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
                .when(
                        withOpContext(
                                (spec, log) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_GRANT_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via("GrantKycAccountWithoutKeyTx")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_REVOKE_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via("RevokeKycAccountWithoutKeyTx")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                cryptoUpdate(ACCOUNT).key(NON_KYC_KEY),
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_GRANT_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via(
                                                                "GrantKycAccountKeyNotMatchingTokenKeyTx")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_REVOKE_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via(
                                                                "RevokeKycAccountKeyNotMatchingTokenKeyTx")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                cryptoUpdate(ACCOUNT).key(KYC_KEY),
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_GRANT_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                tokenWithoutKeyID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via("GrantKycTokenWithoutKeyTx")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_REVOKE_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                tokenWithoutKeyID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via("RevokeKycTokenWithoutKeyTx")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_REVOKE_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(invalidTokenID)),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via("RevokeKycWrongTokenTx")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_GRANT_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(invalidTokenID)),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via("GrantKycWrongTokenTx")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        childRecordsCheck(
                                "RevokeKycAccountWithoutKeyTx",
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
                                "GrantKycAccountWithoutKeyTx",
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
                                "GrantKycAccountKeyNotMatchingTokenKeyTx",
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
                                "RevokeKycAccountKeyNotMatchingTokenKeyTx",
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
                                "GrantKycTokenWithoutKeyTx",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_HAS_NO_KYC_KEY)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                TOKEN_HAS_NO_KYC_KEY)))),
                        childRecordsCheck(
                                "RevokeKycTokenWithoutKeyTx",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(TOKEN_HAS_NO_KYC_KEY)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                TOKEN_HAS_NO_KYC_KEY)))),
                        childRecordsCheck(
                                "RevokeKycWrongTokenTx",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_TOKEN_ID)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                INVALID_TOKEN_ID)))),
                        childRecordsCheck(
                                "GrantKycWrongTokenTx",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_TOKEN_ID)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                INVALID_TOKEN_ID)))));
    }

    private HapiSpec grantRevokeKycSpec() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> secondAccountID = new AtomicReference<>();

        return defaultHapiSpec("GrantRevokeKycSpec")
                .given(
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
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_GRANT_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via("GrantKycTx")
                                                        .gas(GAS_TO_OFFER),
                                                getAccountDetails(SECOND_ACCOUNT)
                                                        .hasToken(
                                                                ExpectedTokenRel.relationshipWith(
                                                                                VANILLA_TOKEN)
                                                                        .kyc(
                                                                                TokenKycStatus
                                                                                        .Granted)),
                                                contractCallLocal(
                                                        GRANT_REVOKE_KYC_CONTRACT,
                                                        IS_KYC_GRANTED,
                                                        HapiParserUtil.asHeadlongAddress(
                                                                asAddress(vanillaTokenID.get())),
                                                        HapiParserUtil.asHeadlongAddress(
                                                                asAddress(secondAccountID.get()))),
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                TOKEN_REVOKE_KYC,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via("RevokeKycTx")
                                                        .gas(GAS_TO_OFFER),
                                                contractCall(
                                                                GRANT_REVOKE_KYC_CONTRACT,
                                                                IS_KYC_GRANTED,
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                secondAccountID
                                                                                        .get())))
                                                        .payingWith(ACCOUNT)
                                                        .via("IsKycTx")
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                "GrantKycTx",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))),
                        childRecordsCheck(
                                "RevokeKycTx",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))),
                        childRecordsCheck(
                                "IsKycTx",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .HAPI_IS_KYC)
                                                                        .withIsKyc(false)
                                                                        .withStatus(SUCCESS)))));
    }

    private HapiSpec grantRevokeKycSpecWithAliasLocalCall() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<String> autoCreatedAccountId = new AtomicReference<>();
        final String accountAlias = "accountAlias";

        return defaultHapiSpec("grantRevokeKycSpecWithAliasLocalCall")
                .given(
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(accountAlias).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, accountAlias, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getAliasedAccountInfo(accountAlias)
                                .exposingContractAccountIdTo(autoCreatedAccountId::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .kycKey(KYC_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(GRANT_REVOKE_KYC_CONTRACT),
                        contractCreate(GRANT_REVOKE_KYC_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCallLocal(
                                                        GRANT_REVOKE_KYC_CONTRACT,
                                                        IS_KYC_GRANTED,
                                                        HapiParserUtil.asHeadlongAddress(
                                                                asAddress(vanillaTokenID.get())),
                                                        HapiParserUtil.asHeadlongAddress(
                                                                autoCreatedAccountId.get())))))
                .then();
    }
}
