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
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nftTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER_SIGNATURE;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(SMART_CONTRACT)
public class CryptoTransferHTSSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(CryptoTransferHTSSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    public static final long TOTAL_SUPPLY = 1_000;
    private static final String FUNGIBLE_TOKEN = "TokenA";
    private static final String NFT_TOKEN = "Token_NFT";

    private static final String RECEIVER = "receiver";
    private static final String RECEIVER2 = "receiver2";
    private static final String SENDER = "sender";
    private static final String SENDER2 = "sender2";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);

    public static final String DELEGATE_KEY = "contractKey";
    private static final String CONTRACT = "CryptoTransfer";
    private static final String MULTI_KEY = "purpose";
    private static final String HTS_TRANSFER_FROM_CONTRACT = "HtsTransferFrom";
    private static final String OWNER = "Owner";
    private static final String HTS_TRANSFER_FROM = "htsTransferFrom";
    private static final String HTS_TRANSFER_FROM_NFT = "htsTransferFromNFT";
    public static final String TRANSFER_MULTIPLE_TOKENS = "transferMultipleTokens";
    private static final ByteString META1 = ByteStringUtils.wrapUnsafely("meta1".getBytes());
    private static final ByteString META2 = ByteStringUtils.wrapUnsafely("meta2".getBytes());
    private static final ByteString META3 = ByteStringUtils.wrapUnsafely("meta3".getBytes());
    private static final ByteString META4 = ByteStringUtils.wrapUnsafely("meta4".getBytes());
    private static final String NFT_TOKEN_WITH_FIXED_HBAR_FEE = "nftTokenWithFixedHbarFee";
    private static final String NFT_TOKEN_WITH_FIXED_TOKEN_FEE = "nftTokenWithFixedTokenFee";
    private static final String NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK =
            "nftTokenWithRoyaltyFeeWithHbarFallback";
    private static final String NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK =
            "nftTokenWithRoyaltyFeeWithTokenFallback";
    private static final String FUNGIBLE_TOKEN_FEE = "fungibleTokenFee";
    private static final String RECEIVER_SIGNATURE = "receiverSignature";
    private static final String APPROVE_TXN = "approveTxn";
    private static final String FIRST_MEMO = "firstMemo";
    private static final String SECOND_MEMO = "secondMemo";
    private static final String CRYPTO_TRANSFER_TXN = "cryptoTransferTxn";
    private static final String SPENDER = "spender";

    public static void main(final String... args) {
        new CryptoTransferHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                nonNestedCryptoTransferForFungibleTokenWithMultipleReceivers(),
                nonNestedCryptoTransferForNonFungibleToken(),
                nonNestedCryptoTransferForMultipleNonFungibleTokens(),
                nonNestedCryptoTransferForFungibleAndNonFungibleToken(),
                nonNestedCryptoTransferForFungibleTokenWithMultipleSendersAndReceiversAndNonFungibleTokens(),
                repeatedTokenIdsAreAutomaticallyConsolidated(),
                hapiTransferFromForFungibleToken(),
                hapiTransferFromForFungibleTokenToSystemAccountsFails(),
                hapiTransferFromForNFT(),
                hapiTransferFromForNFTWithCustomFeesWithoutApproveFails(),
                hapiTransferFromForFungibleTokenWithCustomFeesWithoutApproveFails(),
                hapiTransferFromForFungibleTokenWithCustomFeesWithBothApproveForAllAndAssignedSpender());
    }

    @HapiTest
    final HapiSpec hapiTransferFromForFungibleToken() {
        final var allowance = 10L;
        final var successfulTransferFromTxn = "txn";
        final var successfulTransferFromTxn2 = "txn2";
        final var revertingTransferFromTxn = "revertWhenMoreThanAllowance";
        final var revertingTransferFromTxn2 = "revertingTxn";
        return defaultHapiSpec(
                        "hapiTransferFromForFungibleToken",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(5),
                        cryptoCreate(SPENDER).maxAutomaticTokenAssociations(5),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(5),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .supplyKey(MULTI_KEY)
                                .treasury(OWNER),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, HTS_TRANSFER_FROM_CONTRACT, allowance)
                                .via("baseApproveTxn")
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .tokenAllowancesContaining(
                                                FUNGIBLE_TOKEN, HTS_TRANSFER_FROM_CONTRACT, allowance)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // trying to transfer more than allowance should
                        // revert
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(allowance + 1))
                                .via(revertingTransferFromTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        // transfer allowance/2 amount
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(allowance / 2))
                                .via(successfulTransferFromTxn)
                                .hasKnownStatus(SUCCESS),
                        // transfer the rest of the allowance
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(allowance / 2))
                                .via(successfulTransferFromTxn2)
                                .hasKnownStatus(SUCCESS),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().noAllowances()),
                        // no allowance left, should fail
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.ONE)
                                .via(revertingTransferFromTxn2)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        childRecordsCheck(
                                revertingTransferFromTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(AMOUNT_EXCEEDS_ALLOWANCE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_TRANSFER_FROM)
                                                        .withStatus(AMOUNT_EXCEEDS_ALLOWANCE)))),
                        childRecordsCheck(
                                successfulTransferFromTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_TRANSFER_FROM)
                                                        .withStatus(SUCCESS)))),
                        withOpContext((spec, log) -> {
                            final var idOfToken = "0.0."
                                    + (spec.registry()
                                            .getTokenID(FUNGIBLE_TOKEN)
                                            .getTokenNum());
                            final var txnRecord = getTxnRecord(successfulTransferFromTxn)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .contract(idOfToken)
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(
                                                                            spec.registry()
                                                                                    .getAccountID(OWNER)
                                                                                    .getAccountNum()),
                                                                    parsedToByteString(
                                                                            spec.registry()
                                                                                    .getAccountID(RECEIVER)
                                                                                    .getAccountNum())))
                                                            .longValue(allowance / 2)))))
                                    .andAllChildRecords()
                                    .logged();
                            allRunFor(spec, txnRecord);
                        }),
                        childRecordsCheck(
                                successfulTransferFromTxn2,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_TRANSFER_FROM)
                                                        .withStatus(SUCCESS)))),
                        withOpContext((spec, log) -> {
                            final var idOfToken = "0.0."
                                    + (spec.registry()
                                            .getTokenID(FUNGIBLE_TOKEN)
                                            .getTokenNum());
                            final var txnRecord = getTxnRecord(successfulTransferFromTxn2)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .contract(idOfToken)
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(
                                                                            spec.registry()
                                                                                    .getAccountID(OWNER)
                                                                                    .getAccountNum()),
                                                                    parsedToByteString(
                                                                            spec.registry()
                                                                                    .getAccountID(RECEIVER)
                                                                                    .getAccountNum())))
                                                            .longValue(allowance / 2)))))
                                    .andAllChildRecords()
                                    .logged();
                            allRunFor(spec, txnRecord);
                        }),
                        childRecordsCheck(
                                revertingTransferFromTxn2,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_TRANSFER_FROM)
                                                        .withStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE)))));
    }

    @HapiTest
    final HapiSpec hapiTransferFromForFungibleTokenToSystemAccountsFails() {
        final var UPPER_BOUND_SYSTEM_ADDRESS = 750L;
        final var ADDRESS_ONE = 1L;
        final var NON_EXISTING_SYSTEM_ADDRESS = 345L;
        final var NON_EXISTING_SYSTEM_ADDRESS_360 = 360L;
        final var TXN_TO_FIRST_ADDRESS = "TXN_TO_FIRST_ADDRESS";
        final var TXN_TO_NON_EXISTING_ADDRESS = "TXN_TO_NON_EXISTING_ADDRESS";
        final var TXN_TO_SYSTEM_ADDRESS_360 = "TXN_TO_SYSTEM_ADDRESS_360";
        final var TXN_TO_UPPER_BOUND_ADDRESS = "TXN_TO_UPPER_BOUND_ADDRESS";

        final var allowance = 10L;
        return defaultHapiSpec(
                        "hapiTransferFromForFungibleTokenToSystemAccountsFails",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(5),
                        cryptoCreate(SPENDER).maxAutomaticTokenAssociations(5),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(5),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .supplyKey(MULTI_KEY)
                                .treasury(OWNER),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, HTS_TRANSFER_FROM_CONTRACT, allowance)
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .tokenAllowancesContaining(
                                                FUNGIBLE_TOKEN, HTS_TRANSFER_FROM_CONTRACT, allowance)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // transfer to system account 0.0.750 upper bound
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(asAddress(AccountID.newBuilder()
                                                .setAccountNum(UPPER_BOUND_SYSTEM_ADDRESS)
                                                .build())),
                                        BigInteger.valueOf(allowance / 2))
                                .gas(100_000_00L)
                                .via(TXN_TO_UPPER_BOUND_ADDRESS)
                                .payingWith(GENESIS)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        // transfer to system account 0.0.451
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(asAddress(AccountID.newBuilder()
                                                .setAccountNum(NON_EXISTING_SYSTEM_ADDRESS)
                                                .build())),
                                        BigInteger.valueOf(allowance / 2))
                                .via(TXN_TO_NON_EXISTING_ADDRESS)
                                .gas(100_000_00L)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        // transfer to system account 0.0.360
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(asAddress(AccountID.newBuilder()
                                                .setAccountNum(NON_EXISTING_SYSTEM_ADDRESS_360)
                                                .build())),
                                        BigInteger.valueOf(allowance / 2))
                                .via(TXN_TO_SYSTEM_ADDRESS_360)
                                .gas(100_000_00L)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        // transfer to system account 0.0.0 lower bound
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(asAddress(AccountID.newBuilder()
                                                .setAccountNum(ADDRESS_ONE)
                                                .build())),
                                        BigInteger.valueOf(allowance / 2))
                                .gas(100_000_00L)
                                .via(TXN_TO_FIRST_ADDRESS)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        childRecordsCheck(
                                TXN_TO_UPPER_BOUND_ADDRESS,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_RECEIVING_NODE_ACCOUNT)),
                        childRecordsCheck(
                                TXN_TO_FIRST_ADDRESS,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_RECEIVING_NODE_ACCOUNT)),
                        childRecordsCheck(
                                TXN_TO_SYSTEM_ADDRESS_360,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_RECEIVING_NODE_ACCOUNT)),
                        childRecordsCheck(
                                TXN_TO_NON_EXISTING_ADDRESS,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_RECEIVING_NODE_ACCOUNT)));
    }

    @HapiTest
    final HapiSpec hapiTransferFromForNFT() {
        final var successfulTransferFromTxn = "txn";
        final var revertingTransferFromTxn = "revertWhenMoreThanAllowance";
        return defaultHapiSpec(
                        "hapiTransferFromForNFT",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(5),
                        cryptoCreate(SPENDER).maxAutomaticTokenAssociations(5),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(5),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        mintToken(NFT_TOKEN, List.of(META1, META2)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NFT_TOKEN, HTS_TRANSFER_FROM_CONTRACT, false, List.of(2L))
                                .via("baseApproveTxn")
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // trying to transfer NFT that is not approved
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.ONE)
                                .via(revertingTransferFromTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        // transfer allowed NFT
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.TWO)
                                .via(successfulTransferFromTxn)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(
                                revertingTransferFromTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_TRANSFER_FROM_NFT)
                                                        .withStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE)))),
                        childRecordsCheck(
                                successfulTransferFromTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_TRANSFER_FROM_NFT)
                                                        .withStatus(SUCCESS)))),
                        withOpContext((spec, log) -> {
                            final var idOfToken = "0.0."
                                    + (spec.registry().getTokenID(NFT_TOKEN).getTokenNum());
                            final var txnRecord = getTxnRecord(successfulTransferFromTxn)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .contract(idOfToken)
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(
                                                                            spec.registry()
                                                                                    .getAccountID(OWNER)
                                                                                    .getAccountNum()),
                                                                    parsedToByteString(
                                                                            spec.registry()
                                                                                    .getAccountID(RECEIVER)
                                                                                    .getAccountNum()),
                                                                    parsedToByteString(2L)))))))
                                    .andAllChildRecords()
                                    .logged();
                            allRunFor(spec, txnRecord);
                        }));
    }

    @HapiTest
    final HapiSpec repeatedTokenIdsAreAutomaticallyConsolidated() {
        final var repeatedIdsPrecompileXferTxn = "repeatedIdsPrecompileXfer";
        final var senderStartBalance = 200L;
        final var receiverStartBalance = 0L;
        final var toSendEachTuple = 50L;

        return defaultHapiSpec(
                        "RepeatedTokenIdsAreAutomaticallyConsolidated",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        // For some reason mono-service calculates gas based on the unconsolidated
                        // transfer lists, but we have no reason to keep doing that in mod-service
                        ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE)
                .given(
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(
                                moving(senderStartBalance, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                            final var sender = spec.registry().getAccountID(SENDER);
                            final var receiver = spec.registry().getAccountID(RECEIVER);

                            allRunFor(
                                    spec,
                                    newKeyNamed(DELEGATE_KEY)
                                            .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, CONTRACT))),
                                    cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                    contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                                tokenTransferList()
                                                        .forToken(token)
                                                        .withAccountAmounts(
                                                                accountAmount(sender, -toSendEachTuple),
                                                                accountAmount(receiver, toSendEachTuple))
                                                        .build(),
                                                tokenTransferList()
                                                        .forToken(token)
                                                        .withAccountAmounts(
                                                                accountAmount(sender, -toSendEachTuple),
                                                                accountAmount(receiver, toSendEachTuple))
                                                        .build()
                                            })
                                            .payingWith(GENESIS)
                                            .via(repeatedIdsPrecompileXferTxn)
                                            .gas(GAS_TO_OFFER));
                        }),
                        getTxnRecord(repeatedIdsPrecompileXferTxn)
                                .andAllChildRecords()
                                .logged())
                .then(
                        getAccountBalance(RECEIVER)
                                .hasTokenBalance(FUNGIBLE_TOKEN, receiverStartBalance + 2 * toSendEachTuple),
                        getAccountBalance(SENDER)
                                .hasTokenBalance(FUNGIBLE_TOKEN, senderStartBalance - 2 * toSendEachTuple),
                        childRecordsCheck(
                                repeatedIdsPrecompileXferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(FUNGIBLE_TOKEN, SENDER, -2 * toSendEachTuple)
                                                .including(FUNGIBLE_TOKEN, RECEIVER, 2 * toSendEachTuple))));
    }

    @HapiTest
    final HapiSpec nonNestedCryptoTransferForFungibleTokenWithMultipleReceivers() {
        final var cryptoTransferTxn = CRYPTO_TRANSFER_TXN;

        return defaultHapiSpec(
                        "NonNestedCryptoTransferForFungibleTokenWithMultipleReceivers",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS).receiverSigRequired(true),
                        cryptoCreate(RECEIVER2).balance(ONE_HUNDRED_HBARS).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECEIVER2, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER))
                                .payingWith(SENDER),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                            final var sender = spec.registry().getAccountID(SENDER);
                            final var receiver = spec.registry().getAccountID(RECEIVER);
                            final var receiver2 = spec.registry().getAccountID(RECEIVER2);

                            allRunFor(
                                    spec,
                                    newKeyNamed(DELEGATE_KEY)
                                            .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, CONTRACT))),
                                    cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
                                    contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                                tokenTransferList()
                                                        .forToken(token)
                                                        .withAccountAmounts(
                                                                accountAmount(sender, -50L),
                                                                accountAmount(receiver, 30L),
                                                                accountAmount(receiver2, 20L))
                                                        .build()
                                            })
                                            .gas(GAS_TO_OFFER)
                                            .payingWith(GENESIS)
                                            .via(cryptoTransferTxn));
                        }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged())
                .then(
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 30),
                        getAccountBalance(RECEIVER2).hasTokenBalance(FUNGIBLE_TOKEN, 20),
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 150),
                        getTokenInfo(FUNGIBLE_TOKEN).logged(),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(FUNGIBLE_TOKEN, SENDER, -50)
                                                .including(FUNGIBLE_TOKEN, RECEIVER, 30)
                                                .including(FUNGIBLE_TOKEN, RECEIVER2, 20))));
    }

    @HapiTest
    final HapiSpec nonNestedCryptoTransferForNonFungibleToken() {
        final var cryptoTransferTxn = CRYPTO_TRANSFER_TXN;

        return defaultHapiSpec(
                        "NonNestedCryptoTransferForNonFungibleToken",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(NFT_TOKEN)),
                        mintToken(NFT_TOKEN, List.of(metadata(FIRST_MEMO), metadata(SECOND_MEMO))),
                        tokenAssociate(RECEIVER, List.of(NFT_TOKEN)),
                        cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER))
                                .payingWith(SENDER),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var token = spec.registry().getTokenID(NFT_TOKEN);
                            final var sender = spec.registry().getAccountID(SENDER);
                            final var receiver = spec.registry().getAccountID(RECEIVER);

                            allRunFor(
                                    spec,
                                    newKeyNamed(DELEGATE_KEY)
                                            .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, CONTRACT))),
                                    cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                    contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                                tokenTransferList()
                                                        .forToken(token)
                                                        .withNftTransfers(nftTransfer(sender, receiver, 1L))
                                                        .build()
                                            })
                                            .payingWith(GENESIS)
                                            .via(cryptoTransferTxn)
                                            .gas(GAS_TO_OFFER));
                        }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged())
                .then(
                        getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
                        getAccountInfo(SENDER).hasOwnedNfts(0),
                        getAccountBalance(SENDER).hasTokenBalance(NFT_TOKEN, 0),
                        getTokenInfo(NFT_TOKEN).logged(),
                        childRecordsCheck(
                                CRYPTO_TRANSFER_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT_TOKEN, SENDER, RECEIVER, 1L))));
    }

    @HapiTest
    final HapiSpec nonNestedCryptoTransferForMultipleNonFungibleTokens() {
        final var cryptoTransferTxn = CRYPTO_TRANSFER_TXN;

        return defaultHapiSpec(
                        "NonNestedCryptoTransferForMultipleNonFungibleTokens",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SENDER2).balance(5 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        cryptoCreate(RECEIVER2).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(NFT_TOKEN)),
                        tokenAssociate(SENDER2, List.of(NFT_TOKEN)),
                        mintToken(NFT_TOKEN, List.of(metadata(FIRST_MEMO), metadata(SECOND_MEMO))),
                        tokenAssociate(RECEIVER, List.of(NFT_TOKEN)),
                        tokenAssociate(RECEIVER2, List.of(NFT_TOKEN)),
                        cryptoTransfer(movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER))
                                .payingWith(SENDER),
                        cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 2).between(TOKEN_TREASURY, SENDER2))
                                .payingWith(SENDER2),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var token = spec.registry().getTokenID(NFT_TOKEN);
                            final var sender = spec.registry().getAccountID(SENDER);
                            final var sender2 = spec.registry().getAccountID(SENDER2);
                            final var receiver = spec.registry().getAccountID(RECEIVER);
                            final var receiver2 = spec.registry().getAccountID(RECEIVER2);

                            allRunFor(
                                    spec,
                                    newKeyNamed(DELEGATE_KEY)
                                            .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, CONTRACT))),
                                    cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                    cryptoUpdate(SENDER2).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
                                    contractCall(CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                                tokenTransferList()
                                                        .forToken(token)
                                                        .withNftTransfers(
                                                                nftTransfer(sender, receiver, 1L),
                                                                nftTransfer(sender2, receiver2, 2L))
                                                        .build()
                                            })
                                            .payingWith(GENESIS)
                                            .via(cryptoTransferTxn)
                                            .gas(GAS_TO_OFFER));
                        }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged())
                .then(
                        getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
                        getAccountInfo(SENDER).hasOwnedNfts(0),
                        getAccountBalance(SENDER).hasTokenBalance(NFT_TOKEN, 0),
                        getAccountInfo(RECEIVER2).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER2).hasTokenBalance(NFT_TOKEN, 1),
                        getAccountInfo(SENDER2).hasOwnedNfts(0),
                        getAccountBalance(SENDER2).hasTokenBalance(NFT_TOKEN, 0),
                        getTokenInfo(NFT_TOKEN).logged(),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT_TOKEN, SENDER, RECEIVER, 1L)
                                                .including(NFT_TOKEN, SENDER2, RECEIVER2, 2L))));
    }

    @HapiTest
    final HapiSpec nonNestedCryptoTransferForFungibleAndNonFungibleToken() {
        final var cryptoTransferTxn = CRYPTO_TRANSFER_TXN;

        return defaultHapiSpec(
                        "NonNestedCryptoTransferForFungibleAndNonFungibleToken",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        cryptoCreate(SENDER2).balance(5 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER2).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(SENDER2, List.of(NFT_TOKEN)),
                        mintToken(NFT_TOKEN, List.of(metadata(FIRST_MEMO), metadata(SECOND_MEMO))),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECEIVER2, List.of(NFT_TOKEN)),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER))
                                .payingWith(SENDER),
                        cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER2))
                                .payingWith(SENDER2),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var fungibleToken = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                            final var nonFungibleToken = spec.registry().getTokenID(NFT_TOKEN);
                            final var fungibleTokenSender = spec.registry().getAccountID(SENDER);
                            final var fungibleTokenReceiver = spec.registry().getAccountID(RECEIVER);
                            final var nonFungibleTokenSender = spec.registry().getAccountID(SENDER2);
                            final var nonFungibleTokenReceiver = spec.registry().getAccountID(RECEIVER2);

                            allRunFor(
                                    spec,
                                    newKeyNamed(DELEGATE_KEY)
                                            .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, CONTRACT))),
                                    cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                    cryptoUpdate(SENDER2).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
                                    contractCall(
                                                    CONTRACT,
                                                    TRANSFER_MULTIPLE_TOKENS,
                                                    tokenTransferLists()
                                                            .withTokenTransferList(
                                                                    tokenTransferList()
                                                                            .forToken(fungibleToken)
                                                                            .withAccountAmounts(
                                                                                    accountAmount(
                                                                                            fungibleTokenSender, -45L),
                                                                                    accountAmount(
                                                                                            fungibleTokenReceiver, 45L))
                                                                            .build(),
                                                                    tokenTransferList()
                                                                            .forToken(nonFungibleToken)
                                                                            .withNftTransfers(
                                                                                    nftTransfer(
                                                                                            nonFungibleTokenSender,
                                                                                            nonFungibleTokenReceiver,
                                                                                            1L))
                                                                            .build())
                                                            .build())
                                            .payingWith(GENESIS)
                                            .via(cryptoTransferTxn)
                                            .gas(GAS_TO_OFFER));
                        }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged())
                .then(
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 45),
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 155),
                        getTokenInfo(FUNGIBLE_TOKEN).logged(),
                        getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
                        getAccountInfo(RECEIVER2).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER2).hasTokenBalance(NFT_TOKEN, 1),
                        getAccountInfo(SENDER2).hasOwnedNfts(0),
                        getAccountBalance(SENDER2).hasTokenBalance(NFT_TOKEN, 0),
                        getTokenInfo(NFT_TOKEN).logged(),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(FUNGIBLE_TOKEN, SENDER, -45L)
                                                .including(FUNGIBLE_TOKEN, RECEIVER, 45L))
                                        .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT_TOKEN, SENDER2, RECEIVER2, 1L))));
    }

    @HapiTest
    final HapiSpec nonNestedCryptoTransferForFungibleTokenWithMultipleSendersAndReceiversAndNonFungibleTokens() {
        final var cryptoTransferTxn = CRYPTO_TRANSFER_TXN;

        return defaultHapiSpec(
                        "NonNestedCryptoTransferForFungibleTokenWithMultipleSendersAndReceiversAndNonFungibleTokens",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        cryptoCreate(SENDER2).balance(5 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER2).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        mintToken(NFT_TOKEN, List.of(metadata(FIRST_MEMO), metadata(SECOND_MEMO))),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        tokenAssociate(SENDER2, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        tokenAssociate(RECEIVER2, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER))
                                .payingWith(SENDER),
                        cryptoTransfer(moving(100, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER2))
                                .payingWith(SENDER2),
                        cryptoTransfer(movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER))
                                .payingWith(SENDER),
                        cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 2).between(TOKEN_TREASURY, SENDER2))
                                .payingWith(SENDER2),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var fungibleToken = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                            final var nonFungibleToken = spec.registry().getTokenID(NFT_TOKEN);
                            final var firstSender = spec.registry().getAccountID(SENDER);
                            final var firstReceiver = spec.registry().getAccountID(RECEIVER);
                            final var secondSender = spec.registry().getAccountID(SENDER2);
                            final var secondReceiver = spec.registry().getAccountID(RECEIVER2);

                            allRunFor(
                                    spec,
                                    newKeyNamed(DELEGATE_KEY)
                                            .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, CONTRACT))),
                                    cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                    cryptoUpdate(SENDER2).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
                                    contractCall(
                                                    CONTRACT,
                                                    TRANSFER_MULTIPLE_TOKENS,
                                                    tokenTransferLists()
                                                            .withTokenTransferList(
                                                                    tokenTransferList()
                                                                            .forToken(fungibleToken)
                                                                            .withAccountAmounts(
                                                                                    accountAmount(firstSender, -45L),
                                                                                    accountAmount(firstReceiver, 45L),
                                                                                    accountAmount(secondSender, -32L),
                                                                                    accountAmount(secondReceiver, 32L))
                                                                            .build(),
                                                                    tokenTransferList()
                                                                            .forToken(nonFungibleToken)
                                                                            .withNftTransfers(
                                                                                    nftTransfer(
                                                                                            firstSender,
                                                                                            firstReceiver,
                                                                                            1L),
                                                                                    nftTransfer(
                                                                                            secondSender,
                                                                                            secondReceiver,
                                                                                            2L))
                                                                            .build())
                                                            .build())
                                            .payingWith(GENESIS)
                                            .via(cryptoTransferTxn)
                                            .gas(GAS_TO_OFFER));
                        }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged())
                .then(
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 45),
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 155),
                        getAccountBalance(RECEIVER2).hasTokenBalance(FUNGIBLE_TOKEN, 32),
                        getAccountBalance(SENDER2).hasTokenBalance(FUNGIBLE_TOKEN, 68),
                        getTokenInfo(FUNGIBLE_TOKEN).logged(),
                        getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
                        getAccountInfo(SENDER).hasOwnedNfts(0),
                        getAccountBalance(SENDER).hasTokenBalance(NFT_TOKEN, 0),
                        getAccountInfo(RECEIVER2).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER2).hasTokenBalance(NFT_TOKEN, 1),
                        getAccountInfo(SENDER2).hasOwnedNfts(0),
                        getAccountBalance(SENDER2).hasTokenBalance(NFT_TOKEN, 0),
                        getTokenInfo(NFT_TOKEN).logged(),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(FUNGIBLE_TOKEN, SENDER, -45L)
                                                .including(FUNGIBLE_TOKEN, RECEIVER, 45L)
                                                .including(FUNGIBLE_TOKEN, SENDER2, -32L)
                                                .including(FUNGIBLE_TOKEN, RECEIVER2, 32L))
                                        .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT_TOKEN, SENDER, RECEIVER, 1L)
                                                .including(NFT_TOKEN, SENDER2, RECEIVER2, 2L))));
    }

    @HapiTest
    final HapiSpec hapiTransferFromForNFTWithCustomFeesWithoutApproveFails() {
        return defaultHapiSpec(
                        "HapiTransferFromForNFTWithCustomFeesWithoutApproveFails",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RECEIVER_SIGNATURE),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5)
                                .key(MULTI_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_HBAR_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHbarFee(1, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000L),
                        tokenAssociate(SENDER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN_FEE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHtsFee(1, FUNGIBLE_TOKEN_FEE, OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(
                                        royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, FUNGIBLE_TOKEN_FEE), OWNER)),
                        tokenAssociate(
                                SENDER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        tokenAssociate(
                                RECEIVER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        mintToken(NFT_TOKEN_WITH_FIXED_HBAR_FEE, List.of(META1, META2)),
                        mintToken(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, List.of(META3, META4)),
                        mintToken(
                                NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                List.of(
                                        ByteStringUtils.wrapUnsafely("meta5".getBytes()),
                                        ByteStringUtils.wrapUnsafely("meta6".getBytes()))),
                        mintToken(
                                NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK,
                                List.of(
                                        ByteStringUtils.wrapUnsafely("meta7".getBytes()),
                                        ByteStringUtils.wrapUnsafely("meta8".getBytes()))),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_HBAR_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, RECEIVER)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_HBAR_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_TOKEN_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then();
    }

    @HapiTest
    final HapiSpec hapiTransferFromForFungibleTokenWithCustomFeesWithoutApproveFails() {
        final var FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE = "fungibleTokenWithFixedHbarFee";
        final var FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE = "fungibleTokenWithFixedTokenFee";
        final var FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE = "fungibleTokenWithFractionalTokenFee";
        return defaultHapiSpec(
                        "HapiTransferFromForFungibleTokenWithCustomFeesWithoutApproveFails",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(RECEIVER_SIGNATURE),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                        tokenCreate(FUNGIBLE_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000L),
                        tokenAssociate(SENDER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN_FEE),
                        tokenCreate(FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(OWNER)
                                .initialSupply(1000L)
                                .withCustom(fixedHbarFee(1, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(OWNER)
                                .initialSupply(1000L)
                                .withCustom(fixedHtsFee(1, FUNGIBLE_TOKEN_FEE, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(OWNER)
                                .initialSupply(1000L)
                                .withCustom(fractionalFee(1, 2, 1, OptionalLong.of(10), OWNER)),
                        tokenAssociate(
                                SENDER,
                                List.of(
                                        FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE,
                                        FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE)),
                        tokenAssociate(
                                RECEIVER,
                                List.of(
                                        FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE,
                                        FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(
                                moving(1L, FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE).between(OWNER, SENDER)),
                        cryptoTransfer(
                                moving(1L, FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE).between(OWNER, SENDER)),
                        cryptoTransfer(
                                moving(2L, FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE).between(OWNER, SENDER)),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(asAddress(
                                                spec.registry().getTokenID(FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(asAddress(
                                                spec.registry().getTokenID(FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(asAddress(
                                                spec.registry().getTokenID(FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then();
    }

    @HapiTest
    final HapiSpec hapiTransferFromForFungibleTokenWithCustomFeesWithBothApproveForAllAndAssignedSpender() {
        final var FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE = "fungibleTokenWithFixedHbarFee";
        final var FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE = "fungibleTokenWithFixedTokenFee";
        final var FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE = "fungibleTokenWithFractionalTokenFee";
        return defaultHapiSpec(
                        "HapiTransferFromForFungibleTokenWithCustomFeesWithBothApproveForAllAndAssignedSpender",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(RECEIVER_SIGNATURE),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                        tokenCreate(FUNGIBLE_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000L),
                        tokenAssociate(SENDER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN_FEE),
                        tokenCreate(FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(OWNER)
                                .initialSupply(1000L)
                                .withCustom(fixedHbarFee(1, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(OWNER)
                                .initialSupply(1000L)
                                .withCustom(fixedHtsFee(1, FUNGIBLE_TOKEN_FEE, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(OWNER)
                                .initialSupply(1000L)
                                .withCustom(fractionalFee(1, 2, 1, OptionalLong.of(10), OWNER)),
                        tokenAssociate(
                                SENDER,
                                List.of(
                                        FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE,
                                        FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE)),
                        tokenAssociate(
                                RECEIVER,
                                List.of(
                                        FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE,
                                        FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(
                                moving(1L, FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE).between(OWNER, SENDER)),
                        cryptoTransfer(
                                moving(1L, FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE).between(OWNER, SENDER)),
                        cryptoTransfer(
                                moving(2L, FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE).between(OWNER, SENDER)),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addTokenAllowance(
                                        SENDER, FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE, HTS_TRANSFER_FROM_CONTRACT, 1L)
                                .addTokenAllowance(
                                        SENDER, FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE, HTS_TRANSFER_FROM_CONTRACT, 1L)
                                .addTokenAllowance(
                                        SENDER, FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE, HTS_TRANSFER_FROM_CONTRACT, 2L)
                                .via(APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, SENDER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(asAddress(
                                                spec.registry().getTokenID(FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(asAddress(
                                                spec.registry().getTokenID(FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(asAddress(
                                                spec.registry().getTokenID(FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE))))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
