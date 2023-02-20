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

import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MixedHTSPrecompileTestsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MixedHTSPrecompileTestsSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long TOTAL_SUPPLY = 1_000;
    private static final String ACCOUNT = "account";
    private static final String TOKEN_MISC_OPERATIONS_CONTRACT = "TokenMiscOperations";
    private static final String CREATE_AND_TRANSFER_TXN = "createAndTransferTxn";
    private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    private static final String ED25519KEY = "ed25519key";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String EXPLICIT_CREATE_RESULT = "Explicit create result is {}";

    public static void main(String... args) {
        new MixedHTSPrecompileTestsSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                hscsPrec021TryCatchConstructOnlyRollsBackTheFailedPrecompile(),
                createTokenWithFixedFeeThenTransferAndAssessFee());
    }

    private HapiSpec hscsPrec021TryCatchConstructOnlyRollsBackTheFailedPrecompile() {
        final var theAccount = "anybody";
        final var token = "Token";
        final var outerContract = "AssociateTryCatch";
        final var nestedContract = "CalledContract";

        return defaultHapiSpec("HSCS_PREC_021_try_catch_construct_only_rolls_back_the_failed_precompile")
                .given(
                        cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(token)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(outerContract, nestedContract),
                        contractCreate(nestedContract),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                                outerContract,
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getTokenID(token))))
                                        .via("associateTxn"),
                                cryptoTransfer(moving(200, token).between(TOKEN_TREASURY, theAccount))
                                        .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT))))
                .when(contractCall(outerContract, "associateToken")
                        .payingWith(theAccount)
                        .gas(GAS_TO_OFFER)
                        .via("associateMethodCall"))
                .then(
                        childRecordsCheck(
                                "associateMethodCall",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS))),
                                recordWith()
                                        .status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)))),
                        getAccountInfo(theAccount).hasToken(relationshipWith(token)),
                        cryptoTransfer(moving(200, token).between(TOKEN_TREASURY, theAccount))
                                .hasKnownStatus(SUCCESS));
    }

    private HapiSpec createTokenWithFixedFeeThenTransferAndAssessFee() {
        final var createTokenNum = new AtomicLong();
        final var CONTRACT_ADMIN_KEY = "contractAdminKey";
        final var FEE_COLLECTOR = "feeCollector";
        final var RECIPIENT = "recipient";
        final var SECOND_RECIPIENT = "secondRecipient";
        final var TREASURY = "treasury";
        final var FEE_COLLECTOR_KEY = "feeCollectorKey";
        final var TREASURY_KEY = "treasuryKey";
        final var RECIPIENT_KEY = "recipientKey";
        final var SECOND_RECIPIENT_KEY = "secondRecipientKey";
        return defaultHapiSpec("createTokenWithFixedFeeThenTransferAndAssessFee")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(FEE_COLLECTOR_KEY),
                        newKeyNamed(TREASURY_KEY),
                        newKeyNamed(RECIPIENT_KEY),
                        newKeyNamed(SECOND_RECIPIENT_KEY),
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(ED25519KEY),
                        cryptoCreate(RECIPIENT).balance(ONE_HUNDRED_HBARS).key(RECIPIENT_KEY),
                        cryptoCreate(SECOND_RECIPIENT).key(SECOND_RECIPIENT_KEY),
                        cryptoCreate(TREASURY).balance(ONE_MILLION_HBARS).key(TREASURY_KEY),
                        cryptoCreate(FEE_COLLECTOR).balance(0L).key(FEE_COLLECTOR_KEY),
                        uploadInitCode(TOKEN_MISC_OPERATIONS_CONTRACT),
                        contractCreate(TOKEN_MISC_OPERATIONS_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .adminKey(CONTRACT_ADMIN_KEY)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                .signedBy(CONTRACT_ADMIN_KEY, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
                        cryptoTransfer(TokenMovement.movingHbar(ONE_HUNDRED_HBARS)
                                .between(GENESIS, TOKEN_MISC_OPERATIONS_CONTRACT)),
                        getContractInfo(TOKEN_MISC_OPERATIONS_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
                                .logged())
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_MISC_OPERATIONS_CONTRACT,
                                        "createTokenWithHbarsFixedFeeAndTransferIt",
                                        10L,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(FEE_COLLECTOR))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SECOND_RECIPIENT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(TREASURY))))
                                .via(CREATE_AND_TRANSFER_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(
                                        TREASURY_KEY, FEE_COLLECTOR_KEY, RECIPIENT_KEY, SECOND_RECIPIENT_KEY)
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createTokenNum.set(res.value().longValueExact());
                                }))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        getTxnRecord(CREATE_AND_TRANSFER_TXN)
                                .andAllChildRecords()
                                .logged(),
                        getAccountBalance(RECIPIENT)
                                .hasTokenBalance(
                                        asTokenString(TokenID.newBuilder()
                                                .setTokenNum(createTokenNum.get())
                                                .build()),
                                        0L),
                        getAccountBalance(SECOND_RECIPIENT)
                                .hasTokenBalance(
                                        asTokenString(TokenID.newBuilder()
                                                .setTokenNum(createTokenNum.get())
                                                .build()),
                                        1L),
                        getAccountBalance(TREASURY)
                                .hasTokenBalance(
                                        asTokenString(TokenID.newBuilder()
                                                .setTokenNum(createTokenNum.get())
                                                .build()),
                                        199L),
                        getAccountBalance(FEE_COLLECTOR).hasTinyBars(10L))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
