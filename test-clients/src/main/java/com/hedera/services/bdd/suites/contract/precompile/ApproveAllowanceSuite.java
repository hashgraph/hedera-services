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
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ApproveAllowanceSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ApproveAllowanceSuite.class);
    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String MULTI_KEY = "purpose";
    private static final String OWNER = "owner";
    private static final String ACCOUNT = "anybody";
    private static final String RECIPIENT = "recipient";
    private static final String HTS_APPROVE_ALLOWANCE_CONTRACT = "HtsApproveAllowance";
    private static final String SPENDER = "spender";
    private static final String ALLOWANCE_TX = "allowanceTxn";
    public static final String APPROVE_SIGNATURE = "Approval(address,address,uint256)";
    private static final String APPROVE_FOR_ALL_SIGNATURE = "ApprovalForAll(address,address,bool)";

    public static void main(String... args) {
        new ApproveAllowanceSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                tokenAllowance(),
                tokenApprove(),
                nftApprove(),
                nftIsApprovedForAll(),
                nftGetApproved(),
                nftSetApprovalForAll());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    private HapiSpec tokenAllowance() {
        final var theSpender = SPENDER;
        final var allowanceTxn = ALLOWANCE_TX;

        return defaultHapiSpec("HTS_TOKEN_ALLOWANCE")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(theSpender),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        contractCreate(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, theSpender, 2L)
                                .via("baseApproveTxn")
                                .logged()
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                HTS_APPROVE_ALLOWANCE_CONTRACT,
                                                                "htsAllowance",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                theSpender))))
                                                        .payingWith(OWNER)
                                                        .via(allowanceTxn)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        getTxnRecord(allowanceTxn).andAllChildRecords().logged(),
                        childRecordsCheck(
                                allowanceTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .HAPI_ALLOWANCE)
                                                                        .withStatus(SUCCESS)
                                                                        .withAllowance(2)))));
    }

    private HapiSpec tokenApprove() {
        final var approveTxn = "approveTxn";
        final var theSpender = SPENDER;

        return defaultHapiSpec("HTS_TOKEN_APPROVE")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(theSpender),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        contractCreate(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(HTS_APPROVE_ALLOWANCE_CONTRACT, FUNGIBLE_TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                HTS_APPROVE_ALLOWANCE_CONTRACT,
                                                                "htsApprove",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                theSpender))),
                                                                BigInteger.valueOf(10))
                                                        .payingWith(OWNER)
                                                        .gas(4_000_000L)
                                                        .via(approveTxn)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(approveTxn, SUCCESS, recordWith().status(SUCCESS)),
                        getTxnRecord(approveTxn).andAllChildRecords().logged(),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var sender =
                                            spec.registry()
                                                    .getContractId(HTS_APPROVE_ALLOWANCE_CONTRACT);
                                    final var receiver = spec.registry().getAccountID(theSpender);
                                    final var idOfToken =
                                            "0.0."
                                                    + (spec.registry()
                                                            .getTokenID(FUNGIBLE_TOKEN)
                                                            .getTokenNum());
                                    var txnRecord =
                                            getTxnRecord(approveTxn)
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCallResult(
                                                                            resultWith()
                                                                                    .logs(
                                                                                            inOrder(
                                                                                                    logWith()
                                                                                                            .contract(
                                                                                                                    idOfToken)
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            APPROVE_SIGNATURE),
                                                                                                                                    parsedToByteString(
                                                                                                                                            sender
                                                                                                                                                    .getContractNum()),
                                                                                                                                    parsedToByteString(
                                                                                                                                            receiver
                                                                                                                                                    .getAccountNum())))
                                                                                                            .longValue(
                                                                                                                    10)))))
                                                    .andAllChildRecords()
                                                    .logged();
                                    allRunFor(spec, txnRecord);
                                }));
    }

    private HapiSpec nftApprove() {
        final var approveTxn = "approveTxn";
        final var theSpender = SPENDER;

        return defaultHapiSpec("HTS_NFT_APPROVE")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(theSpender),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("A"),
                                        ByteString.copyFromUtf8("B"))),
                        uploadInitCode(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        contractCreate(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(HTS_APPROVE_ALLOWANCE_CONTRACT, NON_FUNGIBLE_TOKEN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, OWNER)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                HTS_APPROVE_ALLOWANCE_CONTRACT,
                                                                "htsApproveNFT",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                NON_FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                theSpender))),
                                                                BigInteger.valueOf(2L))
                                                        .payingWith(OWNER)
                                                        .gas(4_000_000L)
                                                        /* Don't run with Ethereum calls, since txn payer
                                                         * keys are revoked in Ethereum transactions and sender is the wrapped
                                                         * ethereum txn sender . Analogous test with appropriate setup is present in EthereumSuite  */
                                                        .refusingEthConversion()
                                                        .via(approveTxn))))
                .then(
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasNoSpender(),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(theSpender),
                        childRecordsCheck(approveTxn, SUCCESS, recordWith().status(SUCCESS)),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var sender = spec.registry().getAccountID(OWNER);
                                    final var receiver = spec.registry().getAccountID(theSpender);
                                    final var idOfToken =
                                            "0.0."
                                                    + (spec.registry()
                                                            .getTokenID(NON_FUNGIBLE_TOKEN)
                                                            .getTokenNum());
                                    var txnRecord =
                                            getTxnRecord(approveTxn)
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCallResult(
                                                                            resultWith()
                                                                                    .logs(
                                                                                            inOrder(
                                                                                                    logWith()
                                                                                                            .contract(
                                                                                                                    idOfToken)
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            APPROVE_SIGNATURE),
                                                                                                                                    parsedToByteString(
                                                                                                                                            sender
                                                                                                                                                    .getAccountNum()),
                                                                                                                                    parsedToByteString(
                                                                                                                                            receiver
                                                                                                                                                    .getAccountNum()),
                                                                                                                                    parsedToByteString(
                                                                                                                                            2L)))))))
                                                    .andAllChildRecords()
                                                    .logged();
                                    allRunFor(spec, txnRecord);
                                }));
    }

    private HapiSpec nftIsApprovedForAll() {
        final var notApprovedTxn = "notApprovedTxn";
        final var approvedForAllTxn = "approvedForAllTxn";

        return defaultHapiSpec("HAPI_NFT_IS_APPROVED_FOR_ALL")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("A"),
                                        ByteString.copyFromUtf8("B"))),
                        uploadInitCode(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        contractCreate(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, RECIPIENT, true, List.of(1L, 2L))
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .cryptoAllowancesCount(0)
                                                .nftApprovedForAllAllowancesCount(1)
                                                .tokenAllowancesCount(0)
                                                .nftApprovedAllowancesContaining(
                                                        NON_FUNGIBLE_TOKEN, RECIPIENT)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(RECIPIENT),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(RECIPIENT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                HTS_APPROVE_ALLOWANCE_CONTRACT,
                                                                "htsIsApprovedForAll",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                NON_FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                RECIPIENT))))
                                                        .payingWith(OWNER)
                                                        .via(approvedForAllTxn)
                                                        .hasKnownStatus(SUCCESS)
                                                        .gas(GAS_TO_OFFER),
                                                contractCall(
                                                                HTS_APPROVE_ALLOWANCE_CONTRACT,
                                                                "htsIsApprovedForAll",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                NON_FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                ACCOUNT))))
                                                        .payingWith(OWNER)
                                                        .via(notApprovedTxn)
                                                        .hasKnownStatus(SUCCESS)
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                approvedForAllTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .HAPI_IS_APPROVED_FOR_ALL)
                                                                        .withIsApprovedForAll(
                                                                                SUCCESS, true)))),
                        childRecordsCheck(
                                notApprovedTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .HAPI_IS_APPROVED_FOR_ALL)
                                                                        .withIsApprovedForAll(
                                                                                SUCCESS, false)))));
    }

    private HapiSpec nftGetApproved() {
        final var theSpender = SPENDER;
        final var theSpender2 = "spender2";
        final var allowanceTxn = ALLOWANCE_TX;

        return defaultHapiSpec("HAPI_NFT_GET_APPROVED")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(theSpender),
                        cryptoCreate(theSpender2),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        contractCreate(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("a")))
                                .via("nftTokenMint"),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                        .between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, theSpender, false, List.of(1L))
                                .via("baseApproveTxn")
                                .logged()
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                HTS_APPROVE_ALLOWANCE_CONTRACT,
                                                                "htsGetApproved",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                NON_FUNGIBLE_TOKEN))),
                                                                BigInteger.ONE)
                                                        .payingWith(OWNER)
                                                        .via(allowanceTxn)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                childRecordsCheck(
                                                        allowanceTxn,
                                                        SUCCESS,
                                                        recordWith()
                                                                .status(SUCCESS)
                                                                .contractCallResult(
                                                                        resultWith()
                                                                                .contractCallResult(
                                                                                        htsPrecompileResult()
                                                                                                .forFunction(
                                                                                                        FunctionType
                                                                                                                .HAPI_GET_APPROVED)
                                                                                                .withApproved(
                                                                                                        SUCCESS,
                                                                                                        asAddress(
                                                                                                                spec.registry()
                                                                                                                        .getAccountID(
                                                                                                                                theSpender)))))))));
    }

    private HapiSpec nftSetApprovalForAll() {
        final var theSpender = SPENDER;
        final var theSpender2 = "spender2";
        final var allowanceTxn = ALLOWANCE_TX;

        return defaultHapiSpec("HAPI_NFT_SET_APPROVAL_FOR_ALL")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(theSpender),
                        cryptoCreate(theSpender2),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        contractCreate(HTS_APPROVE_ALLOWANCE_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(HTS_APPROVE_ALLOWANCE_CONTRACT, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("a")))
                                .via("nftTokenMint"),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("b"))),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("c"))),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, OWNER)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                HTS_APPROVE_ALLOWANCE_CONTRACT,
                                                                "htsSetApprovalForAll",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                NON_FUNGIBLE_TOKEN))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                theSpender))),
                                                                true)
                                                        .payingWith(OWNER)
                                                        .gas(5_000_000L)
                                                        .via(allowanceTxn)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(allowanceTxn, SUCCESS, recordWith().status(SUCCESS)),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var sender =
                                            spec.registry()
                                                    .getContractId(HTS_APPROVE_ALLOWANCE_CONTRACT);
                                    final var receiver = spec.registry().getAccountID(theSpender);
                                    final var idOfToken =
                                            "0.0."
                                                    + (spec.registry()
                                                            .getTokenID(NON_FUNGIBLE_TOKEN)
                                                            .getTokenNum());
                                    var txnRecord =
                                            getTxnRecord(allowanceTxn)
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCallResult(
                                                                            resultWith()
                                                                                    .logs(
                                                                                            inOrder(
                                                                                                    logWith()
                                                                                                            .contract(
                                                                                                                    idOfToken)
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            APPROVE_FOR_ALL_SIGNATURE),
                                                                                                                                    parsedToByteString(
                                                                                                                                            sender
                                                                                                                                                    .getContractNum()),
                                                                                                                                    parsedToByteString(
                                                                                                                                            receiver
                                                                                                                                                    .getAccountNum())))
                                                                                                            .booleanValue(
                                                                                                                    true)))))
                                                    .andAllChildRecords()
                                                    .logged();
                                    allRunFor(spec, txnRecord);
                                }));
    }
}
