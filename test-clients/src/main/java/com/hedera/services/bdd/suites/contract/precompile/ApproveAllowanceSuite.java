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
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
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
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ApproveAllowanceSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ApproveAllowanceSuite.class);
    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String MULTI_KEY = "purpose";
    private static final String OWNER = "owner";
    private static final String ACCOUNT = "anybody";
    private static final String RECIPIENT = "recipient";
    private static final ByteString FIRST_META =
            ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
    private static final String HTS_APPROVE_ALLOWANCE_CONTRACT = "HtsApproveAllowance";

    public static void main(String... args) {
        new ApproveAllowanceSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                tokenAllowance(),
                tokenApprove(),
                nftApprove(),
                nftIsApprovedForAll(),
                nftGetApproved(),
                nftSetApprovalForAll());
    }

    private HapiApiSpec tokenAllowance() {
        final var theSpender = "spender";
        final var allowanceTxn = "allowanceTxn";

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
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        FUNGIBLE_TOKEN)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        OWNER)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        theSpender)))
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
                                                                                HTSPrecompileResult
                                                                                        .FunctionType
                                                                                        .HAPI_ALLOWANCE)
                                                                        .withStatus(SUCCESS)
                                                                        .withAllowance(2)))));
    }

    private HapiApiSpec tokenApprove() {
        final var approveTxn = "approveTxn";
        final var theSpender = "spender";

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
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        FUNGIBLE_TOKEN)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        theSpender)),
                                                                10)
                                                        .payingWith(OWNER)
                                                        .gas(4_000_000L)
                                                        .via(approveTxn)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(approveTxn, SUCCESS, recordWith().status(SUCCESS)),
                        getTxnRecord(approveTxn).andAllChildRecords().logged());
    }

    private HapiApiSpec nftApprove() {
        final var approveTxn = "approveTxn";
        final var theSpender = "spender";

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
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        NON_FUNGIBLE_TOKEN)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        theSpender)),
                                                                2L)
                                                        .payingWith(OWNER)
                                                        .gas(4_000_000L)
                                                        .via(approveTxn))))
                .then(
                        getTxnRecord(approveTxn).andAllChildRecords().logged(),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasNoSpender(),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(theSpender),
                        childRecordsCheck(approveTxn, SUCCESS, recordWith().status(SUCCESS)));
    }

    private HapiApiSpec nftIsApprovedForAll() {
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
                                        accountWith()
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
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        NON_FUNGIBLE_TOKEN)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        OWNER)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        RECIPIENT)))
                                                        .payingWith(OWNER)
                                                        .via(approvedForAllTxn)
                                                        .hasKnownStatus(SUCCESS)
                                                        .gas(GAS_TO_OFFER),
                                                contractCall(
                                                                HTS_APPROVE_ALLOWANCE_CONTRACT,
                                                                "htsIsApprovedForAll",
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        NON_FUNGIBLE_TOKEN)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        OWNER)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)))
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
                                                                                HTSPrecompileResult
                                                                                        .FunctionType
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
                                                                                HTSPrecompileResult
                                                                                        .FunctionType
                                                                                        .HAPI_IS_APPROVED_FOR_ALL)
                                                                        .withIsApprovedForAll(
                                                                                SUCCESS, false)))));
    }

    private HapiApiSpec nftGetApproved() {
        final var theSpender = "spender";
        final var theSpender2 = "spender2";
        final var allowanceTxn = "allowanceTxn";

        return defaultHapiSpec("HAPI_NFT_GET_APPROVED")
                .given(
                        UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        NON_FUNGIBLE_TOKEN)),
                                                                1)
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
                                                                                                        HTSPrecompileResult
                                                                                                                .FunctionType
                                                                                                                .HAPI_GET_APPROVED)
                                                                                                .withApproved(
                                                                                                        SUCCESS,
                                                                                                        asAddress(
                                                                                                                spec.registry()
                                                                                                                        .getAccountID(
                                                                                                                                theSpender)))))))),
                        getTxnRecord(allowanceTxn).andAllChildRecords().logged(),
                        UtilVerbs.resetToDefault("contracts.precompile.exportRecordResults"));
    }

    private HapiApiSpec nftSetApprovalForAll() {
        final var theSpender = "spender";
        final var theSpender2 = "spender2";
        final var allowanceTxn = "allowanceTxn";

        return defaultHapiSpec("HAPI_NFT_SET_APPROVAL_FOR_ALL")
                .given(
                        UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        NON_FUNGIBLE_TOKEN)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        theSpender)),
                                                                true)
                                                        .payingWith(OWNER)
                                                        .via(allowanceTxn)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        getTxnRecord(allowanceTxn).andAllChildRecords().logged(),
                        UtilVerbs.resetToDefault("contracts.precompile.exportRecordResults"));
    }

    //	private HapiApiSpec nftClearsApprovalAfterTransfer() {
    //		final var transferFromOwnerTxn = "transferFromToAccountTxn";
    //
    //		return defaultHapiSpec("ERC_721_CLEARS_APPROVAL_AFTER_TRANSFER")
    //				.given(
    //						newKeyNamed(MULTI_KEY),
    //						cryptoCreate(OWNER).balance(10 * ONE_MILLION_HBARS),
    //						cryptoCreate(RECIPIENT),
    //						cryptoCreate(TOKEN_TREASURY),
    //						tokenCreate(NON_FUNGIBLE_TOKEN)
    //								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
    //								.initialSupply(0)
    //								.treasury(TOKEN_TREASURY)
    //								.adminKey(MULTI_KEY)
    //								.supplyKey(MULTI_KEY),
    //						uploadInitCode(HTS_APPROVE_ALLOWANCE_CONTRACT),
    //						contractCreate(HTS_APPROVE_ALLOWANCE_CONTRACT),
    //						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META))
    //				).when(withOpContext(
    //								(spec, opLog) ->
    //										allRunFor(
    //												spec,
    //												tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
    //												tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
    //												cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1, 2).
    //														between(TOKEN_TREASURY, OWNER)).payingWith(OWNER),
    //												cryptoApproveAllowance()
    //														.payingWith(OWNER)
    //														.addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, RECIPIENT, false,
    //																List.of(1L))
    //														.via("otherAdjustTxn"),
    //												getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(RECIPIENT),
    //												getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasNoSpender(),
    //												contractCall(HTS_APPROVE_ALLOWANCE_CONTRACT, "transferFrom",
    //														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
    //														asAddress(spec.registry().getAccountID(OWNER)),
    //														asAddress(spec.registry().getAccountID(RECIPIENT)),
    //														1)
    //														.payingWith(OWNER)
    //														.via(transferFromOwnerTxn)
    //														.hasKnownStatus(SUCCESS)
    //										)
    //
    //						)
    //				).then(
    //						getAccountInfo(OWNER).logged(),
    //						getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasNoSpender(),
    //						getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasNoSpender()
    //				);
    //	}
    //
    //
    //	private HapiApiSpec erc721ApproveWithZeroAddressClearsPreviousApprovals() {
    //		final String owner = "owner";
    //		final String spender = "spender";
    //		final String spender1 = "spender1";
    //		final String nft = "nft";
    //		return defaultHapiSpec("ERC_721_APPROVE_WITH_ZERO_ADDRESS_CLEARS_PREVIOUS_APPROVALS")
    //				.given(
    //						cryptoCreate(owner)
    //								.balance(ONE_HUNDRED_HBARS)
    //								.maxAutomaticTokenAssociations(10),
    //						uploadInitCode(HTS_APPROVE_ALLOWANCE_CONTRACT),
    //						contractCreate(HTS_APPROVE_ALLOWANCE_CONTRACT),
    //						newKeyNamed("supplyKey"),
    //						cryptoCreate(spender)
    //								.balance(ONE_HUNDRED_HBARS),
    //						cryptoCreate(spender1)
    //								.balance(ONE_HUNDRED_HBARS),
    //						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
    //								.maxAutomaticTokenAssociations(10),
    //						tokenCreate(nft)
    //								.maxSupply(10L)
    //								.initialSupply(0)
    //								.supplyType(TokenSupplyType.FINITE)
    //								.tokenType(NON_FUNGIBLE_UNIQUE)
    //								.supplyKey("supplyKey")
    //								.treasury(TOKEN_TREASURY),
    //						tokenAssociate(owner, nft),
    //						mintToken(nft, List.of(
    //								ByteString.copyFromUtf8("a"),
    //								ByteString.copyFromUtf8("b"),
    //								ByteString.copyFromUtf8("c")
    //						)).via("nftTokenMint"),
    //						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
    //								.between(TOKEN_TREASURY, owner))
    //				)
    //				.when(
    //						cryptoApproveAllowance()
    //								.payingWith(owner)
    //								.addNftAllowance(owner, nft, spender, false, List.of(1L, 2L))
    //								.addNftAllowance(owner, nft, spender1, false, List.of(3L)),
    //						getTokenNftInfo(nft, 1L).hasSpenderID(spender),
    //						getTokenNftInfo(nft, 2L).hasSpenderID(spender),
    //						getTokenNftInfo(nft, 3L).hasSpenderID(spender1)
    //				)
    //				.then(
    //						withOpContext(
    //								(spec, opLog) ->
    //										allRunFor(
    //												spec,
    //												contractCall(HTS_APPROVE_ALLOWANCE_CONTRACT, "approve",
    //														asAddress(spec.registry().getTokenID(nft)),
    //														asAddress(AccountID.parseFrom(new byte[] { })),
    //														1)
    //														.payingWith(owner)
    //														.via("cryptoDeleteAllowanceTxn")
    //														.hasKnownStatus(SUCCESS)
    //														.gas(GAS_TO_OFFER)
    //										)
    //						),
    //						getTxnRecord("cryptoDeleteAllowanceTxn").logged(),
    //						getTokenNftInfo(nft, 1L).hasNoSpender(),
    //						getTokenNftInfo(nft, 2L).hasSpenderID(spender),
    //						getTokenNftInfo(nft, 3L).hasSpenderID(spender1)
    //				);
    //	}
    //
    //	private HapiApiSpec someERC721ApproveAndRemoveScenariosPass() {
    //		final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
    //		final var nfToken = "nfToken";
    //		final var multiKey = "multiKey";
    //		final var aCivilian = "aCivilian";
    //		final var bCivilian = "bCivilian";
    //		final var someERC721Scenarios = "someERC721Scenarios";
    //
    //		return defaultHapiSpec("SomeERC721ApproveAndRemoveScenariosPass")
    //				.given(
    //						newKeyNamed(multiKey),
    //						cryptoCreate(aCivilian).exposingCreatedIdTo(id ->
    //								aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
    //						cryptoCreate(bCivilian).exposingCreatedIdTo(id ->
    //								bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
    //						uploadInitCode(someERC721Scenarios),
    //						contractCreate(someERC721Scenarios)
    //								.adminKey(multiKey),
    //						tokenCreate(nfToken)
    //								.supplyKey(multiKey)
    //								.tokenType(NON_FUNGIBLE_UNIQUE)
    //								.treasury(someERC721Scenarios)
    //								.initialSupply(0)
    //								.exposingCreatedIdTo(idLit -> tokenMirrorAddr.set(
    //										asHexedSolidityAddress(
    //												HapiPropertySource.asToken(idLit)))),
    //						mintToken(nfToken, List.of(
    //								// 1
    //								ByteString.copyFromUtf8("I"),
    //								// 2
    //								ByteString.copyFromUtf8("turn"),
    //								// 3
    //								ByteString.copyFromUtf8("the"),
    //								// 4
    //								ByteString.copyFromUtf8("page"),
    //								// 5
    //								ByteString.copyFromUtf8("and"),
    //								// 6
    //								ByteString.copyFromUtf8("read"),
    //								// 7
    //								ByteString.copyFromUtf8("I dream of silent verses")
    //						)),
    //						tokenAssociate(aCivilian, nfToken),
    //						tokenAssociate(bCivilian, nfToken)
    //				).when(
    //						withOpContext((spec, opLog) -> {
    //							zCivilianMirrorAddr.set(asHexedSolidityAddress(
    //									AccountID.newBuilder().setAccountNum(666_666_666L).build()));
    //						}),
    //						// --- Negative cases for approve ---
    //						// * Can't approve a non-existent serial number
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), 666L
    //						)
    //								.via("MISSING_SERIAL_NO").gas(4_000_000).hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
    //						// * Can't approve a non-existent spender
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), zCivilianMirrorAddr.get(), 5L
    //						)
    //								.via("MISSING_TO").gas(4_000_000).hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
    //						getTokenNftInfo(nfToken, 5L).logged(),
    //						childRecordsCheck("MISSING_TO", CONTRACT_REVERT_EXECUTED,
    //								recordWith()
    //										.status(INVALID_ALLOWANCE_SPENDER_ID)
    //										.contractCallResult(
    //												resultWith()
    //														.contractCallResult(htsPrecompileResult()
    //																.withStatus(INVALID_ALLOWANCE_SPENDER_ID)))),
    //						// * Can't approve if msg.sender != owner and not an operator
    //						cryptoTransfer(movingUnique(nfToken, 1L, 2L).between(someERC721Scenarios, aCivilian)),
    //						cryptoTransfer(movingUnique(nfToken, 3L, 4L).between(someERC721Scenarios, bCivilian)),
    //						getTokenNftInfo(nfToken, 1L).hasAccountID(aCivilian),
    //						getTokenNftInfo(nfToken, 2L).hasAccountID(aCivilian),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), 3L
    //						)
    //								.via("NOT_AN_OPERATOR").gas(4_000_000).hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
    //						// * Can't revoke if not owner or approvedForAll
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "revokeSpecificApproval",
    //								tokenMirrorAddr.get(), 1L
    //						)
    //								.via("MISSING_REVOKE").gas(4_000_000).hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
    //						cryptoApproveAllowance()
    //								.payingWith(bCivilian)
    //								.addNftAllowance(bCivilian, nfToken, someERC721Scenarios, false, List.of(3L))
    //								.signedBy(DEFAULT_PAYER, bCivilian)
    //								.fee(ONE_HBAR),
    //						// * Still can't approve if msg.sender != owner and not an operator
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), 3L
    //						)
    //								.via("E").gas(4_000_000).hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
    //						// --- Positive cases for approve ---
    //						// * owner == msg.sender can approve
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), bCivilianMirrorAddr.get(), 6L
    //						)
    //								.via("EXTANT_TO").gas(4_000_000)),
    //						getTokenNftInfo(nfToken, 6L).hasSpenderID(bCivilian),
    //						// Approve the contract as an operator of aCivilian's NFTs
    //						cryptoApproveAllowance()
    //								.payingWith(aCivilian)
    //								.addNftAllowance(aCivilian, nfToken, someERC721Scenarios, true, List.of())
    //								.signedBy(DEFAULT_PAYER, aCivilian)
    //								.fee(ONE_HBAR),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "revokeSpecificApproval",
    //								tokenMirrorAddr.get(), 1L
    //						)
    //								.via("B").gas(4_000_000)),
    //						// These should work because the contract is an operator for aCivilian
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), bCivilianMirrorAddr.get(), 2L
    //						)
    //								.via("C").gas(4_000_000)),
    //						sourcing(() -> contractCall(
    //										someERC721Scenarios, "iMustOwnAfterReceiving",
    //										tokenMirrorAddr.get(), 5L
    //								)
    //										.payingWith(bCivilian).via("D")
    //						),
    //						getTxnRecord("D").andAllChildRecords().logged()
    //				).then(
    //						// Now make contract operator for bCivilian, approve aCivilian, have it grab serial
    // number 3
    //						cryptoApproveAllowance()
    //								.payingWith(bCivilian)
    //								.addNftAllowance(bCivilian, nfToken, someERC721Scenarios, true, List.of())
    //								.signedBy(DEFAULT_PAYER, bCivilian)
    //								.fee(ONE_HBAR),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), 3L
    //						)
    //								.gas(4_000_000)),
    //						cryptoTransfer(movingUniqueWithAllowance(nfToken, 3L)
    //								.between(bCivilian, aCivilian)
    //						).payingWith(aCivilian).fee(ONE_HBAR),
    //						getTokenNftInfo(nfToken, 3L).hasAccountID(aCivilian),
    //						cryptoApproveAllowance()
    //								.payingWith(bCivilian)
    //								.addNftAllowance(bCivilian, nfToken, aCivilian, false, List.of(5L))
    //								.signedBy(DEFAULT_PAYER, bCivilian)
    //								.fee(ONE_HBAR),
    //						getTokenNftInfo(nfToken, 5L).hasAccountID(bCivilian).hasSpenderID(aCivilian),
    //						// * Because contract is operator for bCivilian, it can revoke aCivilian as spender for
    // 5L
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "revokeSpecificApproval",
    //								tokenMirrorAddr.get(), 5L
    //						)
    //								.gas(4_000_000)),
    //						getTokenNftInfo(nfToken, 5L).hasAccountID(bCivilian).hasNoSpender()
    //				);
    //	}
    //
    //	private HapiApiSpec someERC20ApproveAllowanceScenariosPass() {
    //		final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
    //		final var token = "token";
    //		final var multiKey = "multiKey";
    //		final var aCivilian = "aCivilian";
    //		final var bCivilian = "bCivilian";
    //		final var someERC20Scenarios = "someERC20Scenarios";
    //
    //		return defaultHapiSpec("someERC20ApproveAllowanceScenariosPass")
    //				.given(
    //						newKeyNamed(multiKey),
    //						cryptoCreate(aCivilian).exposingCreatedIdTo(id ->
    //								aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
    //						cryptoCreate(bCivilian).exposingCreatedIdTo(id ->
    //								bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
    //						uploadInitCode(someERC20Scenarios),
    //						contractCreate(someERC20Scenarios)
    //								.adminKey(multiKey),
    //						tokenCreate(token)
    //								.supplyKey(multiKey)
    //								.tokenType(FUNGIBLE_COMMON)
    //								.treasury(bCivilian)
    //								.initialSupply(10)
    //								.exposingCreatedIdTo(idLit -> tokenMirrorAddr.set(
    //										asHexedSolidityAddress(
    //												HapiPropertySource.asToken(idLit))))
    //				).when(
    //						withOpContext((spec, opLog) -> {
    //							zCivilianMirrorAddr.set(asHexedSolidityAddress(
    //									AccountID.newBuilder().setAccountNum(666_666_666L).build()));
    //							contractMirrorAddr.set(asHexedSolidityAddress(
    //									spec.registry().getAccountID(someERC20Scenarios)));
    //						}),
    //						sourcing(() -> contractCall(
    //								someERC20Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), 0L
    //						)
    //								.via("ACCOUNT_NOT_ASSOCIATED_TXN").gas(4_000_000).hasKnownStatus(
    //										CONTRACT_REVERT_EXECUTED)),
    //						tokenAssociate(someERC20Scenarios, token),
    //						sourcing(() -> contractCall(
    //								someERC20Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), zCivilianMirrorAddr.get(), 5L
    //						)
    //								.via("MISSING_TO").gas(4_000_000).hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
    //						sourcing(() -> contractCall(
    //								someERC20Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), contractMirrorAddr.get(), 5L
    //						)
    //								.via("SPENDER_SAME_AS_OWNER_TXN").gas(4_000_000).hasKnownStatus(
    //										CONTRACT_REVERT_EXECUTED)),
    //						sourcing(() -> contractCall(
    //								someERC20Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), 5L
    //						)
    //								.via("SUCCESSFUL_APPROVE_TXN").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCall(
    //								someERC20Scenarios, "getAllowance",
    //								tokenMirrorAddr.get(), contractMirrorAddr.get(), aCivilianMirrorAddr.get()
    //						)
    //								.via("ALLOWANCE_TXN").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCallLocal(
    //								someERC20Scenarios, "getAllowance",
    //								tokenMirrorAddr.get(), contractMirrorAddr.get(), aCivilianMirrorAddr.get())),
    //						sourcing(() -> contractCall(
    //								someERC20Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), 0L
    //						)
    //								.via("SUCCESSFUL_REVOKE_TXN").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCall(
    //								someERC20Scenarios, "getAllowance",
    //								tokenMirrorAddr.get(), contractMirrorAddr.get(), aCivilianMirrorAddr.get()
    //						)
    //								.via("ALLOWANCE_AFTER_REVOKE_TXN").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCall(
    //								someERC20Scenarios, "getAllowance",
    //								tokenMirrorAddr.get(), zCivilianMirrorAddr.get(), aCivilianMirrorAddr.get()
    //						)
    //								.via("MISSING_OWNER_ID").gas(4_000_000).hasKnownStatus(CONTRACT_REVERT_EXECUTED))
    //				).then(
    //						childRecordsCheck("ACCOUNT_NOT_ASSOCIATED_TXN", CONTRACT_REVERT_EXECUTED,
    //								recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
    //						childRecordsCheck("MISSING_TO", CONTRACT_REVERT_EXECUTED,
    //								recordWith().status(INVALID_ALLOWANCE_SPENDER_ID)),
    //						childRecordsCheck("SPENDER_SAME_AS_OWNER_TXN", CONTRACT_REVERT_EXECUTED,
    //								recordWith().status(SPENDER_ACCOUNT_SAME_AS_OWNER)),
    //						childRecordsCheck("SUCCESSFUL_APPROVE_TXN", SUCCESS,
    //								recordWith().status(SUCCESS)),
    //						childRecordsCheck("SUCCESSFUL_REVOKE_TXN", SUCCESS,
    //								recordWith().status(SUCCESS)),
    //						childRecordsCheck("MISSING_OWNER_ID", CONTRACT_REVERT_EXECUTED,
    //								recordWith().status(INVALID_ALLOWANCE_OWNER_ID)),
    //						childRecordsCheck("ALLOWANCE_TXN", SUCCESS,
    //								recordWith()
    //										.status(SUCCESS)
    //										.contractCallResult(
    //												resultWith()
    //														.contractCallResult(htsPrecompileResult()
    //																.forFunction(
    //																		HTSPrecompileResult.FunctionType.ALLOWANCE)
    //																.withAllowance(5L)
    //														)
    //										)
    //						),
    //						childRecordsCheck("ALLOWANCE_AFTER_REVOKE_TXN", SUCCESS,
    //								recordWith()
    //										.status(SUCCESS)
    //										.contractCallResult(
    //												resultWith()
    //														.contractCallResult(htsPrecompileResult()
    //																.forFunction(
    //																		HTSPrecompileResult.FunctionType.ALLOWANCE)
    //																.withAllowance(0L)
    //														)
    //										)
    //						)
    //				);
    //	}
    //
    //
    //	private HapiApiSpec someERC20ApproveAllowanceScenarioInOneCall() {
    //		final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
    //		final var token = "token";
    //		final var multiKey = "multiKey";
    //		final var aCivilian = "aCivilian";
    //		final var bCivilian = "bCivilian";
    //		final var someERC20Scenarios = "someERC20Scenarios";
    //
    //		return defaultHapiSpec("someERC20ApproveAllowanceScenarioInOneCall")
    //				.given(
    //						newKeyNamed(multiKey),
    //						cryptoCreate(aCivilian).exposingCreatedIdTo(id ->
    //								aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
    //						cryptoCreate(bCivilian).exposingCreatedIdTo(id ->
    //								bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
    //						uploadInitCode(someERC20Scenarios),
    //						contractCreate(someERC20Scenarios)
    //								.adminKey(multiKey),
    //						tokenCreate(token)
    //								.supplyKey(multiKey)
    //								.tokenType(FUNGIBLE_COMMON)
    //								.treasury(bCivilian)
    //								.initialSupply(10)
    //								.exposingCreatedIdTo(idLit -> tokenMirrorAddr.set(
    //										asHexedSolidityAddress(
    //												HapiPropertySource.asToken(idLit)))),
    //						tokenAssociate(someERC20Scenarios, token)
    //				).when(
    //						withOpContext((spec, opLog) -> {
    //							zCivilianMirrorAddr.set(asHexedSolidityAddress(
    //									AccountID.newBuilder().setAccountNum(666_666_666L).build()));
    //							contractMirrorAddr.set(asHexedSolidityAddress(
    //									spec.registry().getAccountID(someERC20Scenarios)));
    //						}),
    //						sourcing(() -> contractCall(
    //								someERC20Scenarios, "approveAndGetAllowanceAmount",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), 5L
    //						)
    //								.via("APPROVE_AND_GET_ALLOWANCE_TXN").gas(4_000_000).hasKnownStatus(SUCCESS).logged())
    //				).then(
    //						childRecordsCheck("APPROVE_AND_GET_ALLOWANCE_TXN", SUCCESS,
    //								recordWith()
    //										.status(SUCCESS),
    //								recordWith()
    //										.status(SUCCESS)
    //										.contractCallResult(
    //												resultWith()
    //														.contractCallResult(htsPrecompileResult()
    //																.forFunction(
    //																		HTSPrecompileResult.FunctionType.ALLOWANCE)
    //																.withAllowance(5L)
    //														)
    //										)
    //						)
    //				);
    //	}
    //
    //	private HapiApiSpec someERC721GetApprovedScenariosPass() {
    //		final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> zTokenMirrorAddr = new AtomicReference<>();
    //		final var nfToken = "nfToken";
    //		final var multiKey = "multiKey";
    //		final var aCivilian = "aCivilian";
    //		final var someERC721Scenarios = "someERC721Scenarios";
    //
    //		return defaultHapiSpec("someERC721GetApprovedScenariosPass")
    //				.given(
    //						newKeyNamed(multiKey),
    //						cryptoCreate(aCivilian).exposingCreatedIdTo(id ->
    //								aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
    //						uploadInitCode(someERC721Scenarios),
    //						contractCreate(someERC721Scenarios)
    //								.adminKey(multiKey),
    //						tokenCreate(nfToken)
    //								.supplyKey(multiKey)
    //								.tokenType(NON_FUNGIBLE_UNIQUE)
    //								.treasury(someERC721Scenarios)
    //								.initialSupply(0)
    //								.exposingCreatedIdTo(idLit -> tokenMirrorAddr.set(
    //										asHexedSolidityAddress(
    //												HapiPropertySource.asToken(idLit)))),
    //						mintToken(nfToken, List.of(
    //								// 1
    //								ByteString.copyFromUtf8("A"),
    //								// 2
    //								ByteString.copyFromUtf8("B")
    //						)),
    //						tokenAssociate(aCivilian, nfToken)
    //				).when(
    //						withOpContext((spec, opLog) -> {
    //							zCivilianMirrorAddr.set(asHexedSolidityAddress(
    //									AccountID.newBuilder().setAccountNum(666_666_666L).build()));
    //							zTokenMirrorAddr.set(asHexedSolidityAddress(
    //									TokenID.newBuilder().setTokenNum(666_666L).build()));
    //						}),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "getApproved",
    //								zTokenMirrorAddr.get(), 1L
    //						)
    //								.via("MISSING_TOKEN").gas(4_000_000).hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "doSpecificApproval",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), 1L
    //						)
    //								.gas(4_000_000)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "getApproved",
    //								tokenMirrorAddr.get(), 55L
    //						)
    //								.via("MISSING_SERIAL").gas(4_000_000).hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
    //						getTokenNftInfo(nfToken, 1L).logged(),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "getApproved",
    //								tokenMirrorAddr.get(), 2L
    //						)
    //								.via("MISSING_SPENDER").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "getApproved",
    //								tokenMirrorAddr.get(), 1L
    //						)
    //								.via("WITH_SPENDER").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						getTxnRecord("WITH_SPENDER").andAllChildRecords().logged(),
    //						sourcing(() -> contractCallLocal(
    //								someERC721Scenarios, "getApproved",
    //								tokenMirrorAddr.get(), 1L
    //						)
    //								.logged()
    //								.gas(4_000_000).has(resultWith()
    //										.contractCallResult(hexedAddress(aCivilianMirrorAddr.get()))))
    //				).then(
    //						withOpContext(
    //								(spec, opLog) ->
    //										allRunFor(
    //												spec,
    //												childRecordsCheck("MISSING_SPENDER", SUCCESS,
    //														recordWith()
    //																.status(SUCCESS)
    //																.contractCallResult(
    //																		resultWith()
    //																				.contractCallResult
    //																						(htsPrecompileResult()
    //																								.forFunction(
    //																										HTSPrecompileResult
    //																												.FunctionType
    //																												.GET_APPROVED)
    //																								.withApproved(
    //																										new byte[0])
    //																						)
    //																)
    //												),
    //												childRecordsCheck("WITH_SPENDER", SUCCESS,
    //														recordWith()
    //																.status(SUCCESS)
    //																.contractCallResult(
    //																		resultWith()
    //																				.contractCallResult
    //																						(htsPrecompileResult()
    //																								.forFunction(
    //																										HTSPrecompileResult
    //																												.FunctionType
    //																												.GET_APPROVED)
    //																								.withApproved(asAddress(
    //																										spec.registry()
    //																												.getAccountID(
    //																														aCivilian)))
    //																						)
    //																)
    //												)
    //										)
    //						)
    //				);
    //	}
    //
    //	private HapiApiSpec someERC721IsApprovedForAllScenariosPass() {
    //		final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> zTokenMirrorAddr = new AtomicReference<>();
    //		final var nfToken = "nfToken";
    //		final var multiKey = "multiKey";
    //		final var aCivilian = "aCivilian";
    //		final var someERC721Scenarios = "someERC721Scenarios";
    //
    //		return defaultHapiSpec("someERC721IsApprovedForAllScenariosPass")
    //				.given(
    //						newKeyNamed(multiKey),
    //						cryptoCreate(aCivilian).exposingCreatedIdTo(id ->
    //								aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
    //						uploadInitCode(someERC721Scenarios),
    //						contractCreate(someERC721Scenarios)
    //								.adminKey(multiKey),
    //						tokenCreate(nfToken)
    //								.supplyKey(multiKey)
    //								.tokenType(NON_FUNGIBLE_UNIQUE)
    //								.treasury(someERC721Scenarios)
    //								.initialSupply(0)
    //								.exposingCreatedIdTo(idLit -> tokenMirrorAddr.set(
    //										asHexedSolidityAddress(
    //												HapiPropertySource.asToken(idLit)))),
    //						mintToken(nfToken, List.of(
    //								// 1
    //								ByteString.copyFromUtf8("A"),
    //								// 2
    //								ByteString.copyFromUtf8("B")
    //						)),
    //						tokenAssociate(aCivilian, nfToken),
    //						cryptoTransfer(movingUnique(nfToken, 1L, 2L).between(someERC721Scenarios, aCivilian))
    //				).when(
    //						withOpContext((spec, opLog) -> {
    //							zCivilianMirrorAddr.set(asHexedSolidityAddress(
    //									AccountID.newBuilder().setAccountNum(666_666_666L).build()));
    //							zTokenMirrorAddr.set(asHexedSolidityAddress(
    //									TokenID.newBuilder().setTokenNum(666_666L).build()));
    //							contractMirrorAddr.set(asHexedSolidityAddress(
    //									spec.registry().getAccountID(someERC721Scenarios)));
    //						}),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "isApprovedForAll",
    //								tokenMirrorAddr.get(), zCivilianMirrorAddr.get(), contractMirrorAddr.get()
    //						)
    //								.via("OWNER_DOES_NOT_EXISTS").gas(4_000_000)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "isApprovedForAll",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), zCivilianMirrorAddr.get()
    //						)
    //								.via("OPERATOR_DOES_NOT_EXISTS").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "isApprovedForAll",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), contractMirrorAddr.get()
    //						)
    //								.via("OPERATOR_IS_NOT_APPROVED").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						cryptoApproveAllowance()
    //								.payingWith(aCivilian)
    //								.addNftAllowance(aCivilian, nfToken, someERC721Scenarios, true, List.of())
    //								.signedBy(DEFAULT_PAYER, aCivilian)
    //								.fee(ONE_HBAR),
    //						getAccountDetails(aCivilian)
    //								.payingWith(GENESIS)
    //								.has(accountWith()
    //										.cryptoAllowancesCount(0)
    //										.nftApprovedForAllAllowancesCount(1)
    //										.tokenAllowancesCount(0)
    //										.nftApprovedAllowancesContaining(nfToken, someERC721Scenarios)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "isApprovedForAll",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), contractMirrorAddr.get()
    //						)
    //								.via("OPERATOR_IS_APPROVED_FOR_ALL").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCallLocal(
    //								someERC721Scenarios, "isApprovedForAll",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), contractMirrorAddr.get()
    //						)
    //								.gas(4_000_000).has(resultWith().contractCallResult(flag(true))))
    //
    //				).then(
    //						withOpContext(
    //								(spec, opLog) ->
    //										allRunFor(
    //												spec,
    //												childRecordsCheck("OPERATOR_DOES_NOT_EXISTS", SUCCESS,
    //														recordWith()
    //																.status(SUCCESS)
    //																.contractCallResult(
    //																		resultWith()
    //																				.contractCallResult(htsPrecompileResult()
    //																						.forFunction(
    //																								HTSPrecompileResult.FunctionType.IS_APPROVED_FOR_ALL)
    //																						.withIsApprovedForAll(false)
    //																				)
    //																)
    //												),
    //												childRecordsCheck("OPERATOR_IS_NOT_APPROVED", SUCCESS,
    //														recordWith()
    //																.status(SUCCESS)
    //																.contractCallResult(
    //																		resultWith()
    //																				.contractCallResult(htsPrecompileResult()
    //																						.forFunction(
    //																								HTSPrecompileResult.FunctionType.IS_APPROVED_FOR_ALL)
    //																						.withIsApprovedForAll(false)
    //																				)
    //																)
    //												),
    //												childRecordsCheck("OPERATOR_IS_APPROVED_FOR_ALL", SUCCESS,
    //														recordWith()
    //																.status(SUCCESS)
    //																.contractCallResult(
    //																		resultWith()
    //																				.contractCallResult(htsPrecompileResult()
    //																						.forFunction(
    //																								HTSPrecompileResult.FunctionType.IS_APPROVED_FOR_ALL)
    //																						.withIsApprovedForAll(true)
    //																				)
    //																)
    //												)
    //										)
    //						)
    //
    //				);
    //	}
    //
    //	private HapiApiSpec someERC721SetApprovedForAllScenariosPass() {
    //		final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
    //		final AtomicReference<String> zTokenMirrorAddr = new AtomicReference<>();
    //		final var nfToken = "nfToken";
    //		final var multiKey = "multiKey";
    //		final var aCivilian = "aCivilian";
    //		final var someERC721Scenarios = "someERC721Scenarios";
    //
    //		return defaultHapiSpec("someERC721SetApprovedForAllScenariosPass")
    //				.given(
    //						newKeyNamed(multiKey),
    //						cryptoCreate(aCivilian).exposingCreatedIdTo(id ->
    //								aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
    //						uploadInitCode(someERC721Scenarios),
    //						contractCreate(someERC721Scenarios)
    //								.adminKey(multiKey),
    //						tokenCreate(nfToken)
    //								.supplyKey(multiKey)
    //								.tokenType(NON_FUNGIBLE_UNIQUE)
    //								.treasury(someERC721Scenarios)
    //								.initialSupply(0)
    //								.exposingCreatedIdTo(idLit -> tokenMirrorAddr.set(
    //										asHexedSolidityAddress(
    //												HapiPropertySource.asToken(idLit)))),
    //						mintToken(nfToken, List.of(
    //								// 1
    //								ByteString.copyFromUtf8("A"),
    //								// 2
    //								ByteString.copyFromUtf8("B")
    //						)),
    //						tokenAssociate(aCivilian, nfToken)
    //				).when(
    //						withOpContext((spec, opLog) -> {
    //							zCivilianMirrorAddr.set(asHexedSolidityAddress(
    //									AccountID.newBuilder().setAccountNum(666_666_666L).build()));
    //							zTokenMirrorAddr.set(asHexedSolidityAddress(
    //									TokenID.newBuilder().setTokenNum(666_666L).build()));
    //							contractMirrorAddr.set(asHexedSolidityAddress(
    //									spec.registry().getAccountID(someERC721Scenarios)));
    //						}),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "setApprovalForAll",
    //								tokenMirrorAddr.get(), contractMirrorAddr.get(), true
    //						)
    //								.via("OPERATOR_SAME_AS_MSG_SENDER").gas(4_000_000)
    //								.hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "setApprovalForAll",
    //								tokenMirrorAddr.get(), zCivilianMirrorAddr.get(), true
    //						)
    //								.via("OPERATOR_DOES_NOT_EXISTS").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "setApprovalForAll",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), true
    //						)
    //								.via("OPERATOR_EXISTS").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "isApprovedForAll",
    //								tokenMirrorAddr.get(), contractMirrorAddr.get(), aCivilianMirrorAddr.get()
    //						)
    //								.via("SUCCESSFULLY_APPROVED_CHECK_TXN").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "setApprovalForAll",
    //								tokenMirrorAddr.get(), aCivilianMirrorAddr.get(), false
    //						)
    //
    //	.via("OPERATOR_EXISTS_REVOKE_APPROVE_FOR_ALL").gas(4_000_000).hasKnownStatus(SUCCESS)),
    //						sourcing(() -> contractCall(
    //								someERC721Scenarios, "isApprovedForAll",
    //								tokenMirrorAddr.get(), contractMirrorAddr.get(), aCivilianMirrorAddr.get()
    //						)
    //								.via("SUCCESSFULLY_REVOKED_CHECK_TXN").gas(4_000_000).hasKnownStatus(SUCCESS))
    //				).then(
    //						childRecordsCheck("OPERATOR_SAME_AS_MSG_SENDER", CONTRACT_REVERT_EXECUTED,
    //								recordWith().status(SPENDER_ACCOUNT_SAME_AS_OWNER)),
    //						childRecordsCheck("OPERATOR_DOES_NOT_EXISTS", SUCCESS,
    //								recordWith().status(INVALID_ALLOWANCE_SPENDER_ID)),
    //						childRecordsCheck("OPERATOR_EXISTS", SUCCESS,
    //								recordWith().status(SUCCESS)),
    //						childRecordsCheck("OPERATOR_EXISTS_REVOKE_APPROVE_FOR_ALL", SUCCESS, recordWith()
    //								.status(SUCCESS)),
    //						withOpContext(
    //								(spec, opLog) ->
    //										allRunFor(
    //												spec,
    //												childRecordsCheck("SUCCESSFULLY_APPROVED_CHECK_TXN", SUCCESS,
    //														recordWith()
    //																.status(SUCCESS)
    //																.contractCallResult(
    //																		resultWith()
    //																				.contractCallResult(htsPrecompileResult()
    //																						.forFunction(
    //																								HTSPrecompileResult.FunctionType.IS_APPROVED_FOR_ALL)
    //																						.withIsApprovedForAll(true)
    //																				)
    //																)
    //												),
    //												childRecordsCheck("SUCCESSFULLY_REVOKED_CHECK_TXN", SUCCESS,
    //														recordWith()
    //																.status(SUCCESS)
    //																.contractCallResult(
    //																		resultWith()
    //																				.contractCallResult(htsPrecompileResult()
    //																						.forFunction(
    //																								HTSPrecompileResult.FunctionType.IS_APPROVED_FOR_ALL)
    //																						.withIsApprovedForAll(false)
    //																				)
    //																)
    //												)
    //										)
    //						)
    //
    //				);
    //	}
    //
}
