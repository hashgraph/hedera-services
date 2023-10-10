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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDeleteAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoApproveAllowance.MISSING_OWNER;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.allowanceTinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUniqueWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class CryptoApproveAllowanceSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(CryptoApproveAllowanceSuite.class);

    public static final String OWNER = "owner";
    public static final String SPENDER = "spender";
    private static final String RECEIVER = "receiver";
    public static final String OTHER_RECEIVER = "otherReceiver";
    public static final String FUNGIBLE_TOKEN = "fungible";
    public static final String NON_FUNGIBLE_TOKEN = "nonFungible";
    public static final String TOKEN_WITH_CUSTOM_FEE = "tokenWithCustomFee";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String SENDER_TXN = "senderTxn";
    public static final String SCHEDULED_TXN = "scheduledTxn";
    public static final String NFT_TOKEN_MINT_TXN = "nftTokenMint";
    public static final String FUNGIBLE_TOKEN_MINT_TXN = "tokenMint";
    public static final String BASE_APPROVE_TXN = "baseApproveTxn";
    public static final String PAYER = "payer";
    public static final String APPROVE_TXN = "approveTxn";
    public static final String ANOTHER_SPENDER = "spender1";
    public static final String SECOND_OWNER = "owner2";
    public static final String SECOND_SPENDER = "spender2";
    public static final String THIRD_SPENDER = "spender3";
    public static final String HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT = "hedera.allowances.maxTransactionLimit";
    public static final String HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT = "hedera.allowances.maxAccountLimit";
    public static final String ADMIN_KEY = "adminKey";
    public static final String FREEZE_KEY = "freezeKey";
    public static final String KYC_KEY = "kycKey";
    public static final String PAUSE_KEY = "pauseKey";

    public static void main(String... args) {
        new CryptoApproveAllowanceSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    @SuppressWarnings("java:S3878")
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                canHaveMultipleOwners(),
                noOwnerDefaultsToPayer(),
                invalidSpenderFails(),
                invalidOwnerFails(),
                happyPathWorks(),
                emptyAllowancesRejected(),
                negativeAmountFailsForFungible(),
                tokenNotAssociatedToAccountFails(),
                invalidTokenTypeFails(),
                validatesSerialNums(),
                tokenExceedsMaxSupplyFails(),
                succeedsWhenTokenPausedFrozenKycRevoked(),
                serialsInAscendingOrder(),
                feesAsExpected(),
                cannotHaveMultipleAllowedSpendersForTheSameNFTSerial(),
                approveForAllDoesNotSetExplicitNFTSpender(),
                canGrantNftAllowancesWithTreasuryOwner(),
                canGrantFungibleAllowancesWithTreasuryOwner(),
                approveForAllSpenderCanDelegateOnNFT(),
                duplicateEntriesGetsReplacedWithDifferentTxn(),
                duplicateKeysAndSerialsInSameTxnDoesntThrow(),
                scheduledCryptoApproveAllowanceWorks(),
                canDeleteAllowanceFromDeletedSpender(),
                cannotPayForAnyTransactionWithContractAccount(),
                transferringMissingNftViaApprovalFailsWithInvalidNftId());
    }

    @HapiTest
    private HapiSpec cannotPayForAnyTransactionWithContractAccount() {
        final var cryptoAdminKey = "cryptoAdminKey";
        final var contractNum = new AtomicLong();
        final var contract = "PayableConstructor";
        return defaultHapiSpec("cannotPayForAnyTransactionWithContractAccount")
                .given(
                        newKeyNamed(cryptoAdminKey),
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .adminKey(cryptoAdminKey)
                                .balance(ONE_HUNDRED_HBARS)
                                .exposingNumTo(contractNum::set))
                .when()
                .then(sourcing(() -> cryptoTransfer(tinyBarsFromTo(contract, FUNDING, 1))
                        .signedBy(cryptoAdminKey)
                        .fee(ONE_HBAR)
                        .payingWith("0.0." + contractNum.longValue())
                        .hasPrecheck(PAYER_ACCOUNT_NOT_FOUND)));
    }

    @HapiTest
    private HapiSpec transferringMissingNftViaApprovalFailsWithInvalidNftId() {
        return defaultHapiSpec("TransferringMissingNftViaApprovalFailsWithInvalidNftId")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"))))
                .when()
                .then(cryptoTransfer((spec, builder) -> builder.addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))
                                .addNftTransfers(NftTransfer.newBuilder()
                                        // Doesn't exist
                                        .setSerialNumber(4L)
                                        .setSenderAccountID(spec.registry().getAccountID(OWNER))
                                        .setReceiverAccountID(spec.registry().getAccountID(RECEIVER))
                                        .setIsApproval(true)
                                        .build())))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER, OWNER)
                        .hasKnownStatus(INVALID_NFT_ID));
    }

    @HapiTest
    private HapiSpec canDeleteAllowanceFromDeletedSpender() {
        return defaultHapiSpec("canDeleteAllowanceFromDeletedSpender")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"))))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 1)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of()),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(1)
                                        .tokenAllowancesCount(1)
                                        .nftApprovedForAllAllowancesCount(1)
                                        .cryptoAllowancesContaining(SPENDER, 100L)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 1)),
                        cryptoDelete(SPENDER),
                        // removing fungible allowances should be possible even if the
                        // spender is deleted
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 0)
                                .blankMemo(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(0)
                                        .tokenAllowancesCount(1)
                                        .nftApprovedForAllAllowancesCount(1)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 1)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 0)
                                .blankMemo(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(0)
                                        .tokenAllowancesCount(0)
                                        .nftApprovedForAllAllowancesCount(1)),
                        // It should not be possible to remove approveForAllNftAllowance
                        // and also add allowance to serials
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L, 2L))
                                .hasKnownStatus(INVALID_ALLOWANCE_SPENDER_ID),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(0)
                                        .tokenAllowancesCount(0)
                                        .nftApprovedForAllAllowancesCount(1)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasNoSpender(),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasNoSpender(),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of()),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(0)
                                        .tokenAllowancesCount(0)
                                        .nftApprovedForAllAllowancesCount(0)))
                .then();
    }

    @HapiTest
    private HapiSpec duplicateKeysAndSerialsInSameTxnDoesntThrow() {
        return defaultHapiSpec("duplicateKeysAndSerialsInSameTxnDoesntThrow")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SPENDER, 200L)
                                .blankMemo()
                                .logged(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(1)
                                        .cryptoAllowancesContaining(SPENDER, 200L)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 300L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 300L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 500L)
                                .blankMemo()
                                .logged(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(1)
                                        .cryptoAllowancesContaining(SPENDER, 300L)
                                        .tokenAllowancesCount(1)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 500L)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 500L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 600L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L, 2L, 2L, 2L, 2L))
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of(1L))
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(2L))
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of(3L))
                                .blankMemo()
                                .logged(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(1)
                                        .cryptoAllowancesContaining(SPENDER, 500L)
                                        .tokenAllowancesCount(1)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 600L)
                                        .nftApprovedForAllAllowancesCount(1)))
                .then(
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(SPENDER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(SPENDER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 3L).hasSpenderID(SPENDER));
    }

    @HapiTest
    private HapiSpec approveForAllSpenderCanDelegateOnNFT() {
        final String delegatingSpender = "delegatingSpender";
        final String newSpender = "newSpender";
        return defaultHapiSpec("approveForAllSpenderCanDelegateOnNFT")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(delegatingSpender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(newSpender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b")))
                                .via(NFT_TOKEN_MINT_TXN),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L).between(TOKEN_TREASURY, OWNER)))
                .when(cryptoApproveAllowance()
                        .payingWith(DEFAULT_PAYER)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, delegatingSpender, true, List.of(1L))
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, newSpender, false, List.of(2L))
                        .signedBy(DEFAULT_PAYER, OWNER))
                .then(
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addDelegatedNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, newSpender, delegatingSpender, false, List.of(1L))
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addDelegatedNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, delegatingSpender, newSpender, false, List.of(2L))
                                .signedBy(DEFAULT_PAYER, newSpender)
                                .hasPrecheck(DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL),
                        // NOTE: This test in mono-service was failing in pre-check before signing requirements
                        // (the signing requirements are wrong too).
                        // In new code since we moved these checks to handle, it failed at signature verification.
                        // To make it fail with same status code, we need to sign with owner.
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addDelegatedNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, newSpender, delegatingSpender, true, List.of())
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .hasPrecheck(DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(newSpender),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(delegatingSpender),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addDelegatedNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, newSpender, delegatingSpender, false, List.of(1L))
                                .signedBy(DEFAULT_PAYER, delegatingSpender),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(newSpender));
    }

    @HapiTest
    private HapiSpec canGrantFungibleAllowancesWithTreasuryOwner() {
        return defaultHapiSpec("canGrantFungibleAllowancesWithTreasuryOwner")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SPENDER),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(10000)
                                .initialSupply(5000),
                        cryptoCreate(OTHER_RECEIVER).balance(ONE_HBAR).maxAutomaticTokenAssociations(1))
                .when(
                        cryptoApproveAllowance()
                                .addTokenAllowance(TOKEN_TREASURY, FUNGIBLE_TOKEN, SPENDER, 10)
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER),
                        cryptoApproveAllowance()
                                .addTokenAllowance(TOKEN_TREASURY, FUNGIBLE_TOKEN, SPENDER, 110)
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER))
                .then(
                        cryptoTransfer(movingWithAllowance(30, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OTHER_RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER),
                        getAccountDetails(TOKEN_TREASURY)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 80))
                                .logged(),
                        getAccountDetails(TOKEN_TREASURY)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 80))
                                .logged());
    }

    @HapiTest
    private HapiSpec canGrantNftAllowancesWithTreasuryOwner() {
        return defaultHapiSpec("canGrantNftAllowancesWithTreasuryOwner")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SPENDER),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.INFINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"))),
                        cryptoCreate(OTHER_RECEIVER).balance(ONE_HBAR).maxAutomaticTokenAssociations(1))
                .when(
                        cryptoApproveAllowance()
                                .addNftAllowance(TOKEN_TREASURY, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(4L))
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER)
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        cryptoApproveAllowance()
                                .addNftAllowance(TOKEN_TREASURY, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L, 3L))
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER),
                        cryptoDeleteAllowance()
                                .addNftDeleteAllowance(TOKEN_TREASURY, NON_FUNGIBLE_TOKEN, List.of(4L))
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER)
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER))
                .then(
                        getAccountDetails(TOKEN_TREASURY).payingWith(GENESIS).logged(),
                        cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1L)
                                        .between(TOKEN_TREASURY, OTHER_RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER),
                        getAccountDetails(TOKEN_TREASURY)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(0))
                                .logged());
    }

    @HapiTest
    private HapiSpec invalidOwnerFails() {
        return defaultHapiSpec("invalidOwnerFails")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(PAYER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .signedBy(PAYER, OWNER)
                                .blankMemo(),
                        cryptoDelete(OWNER),
                        cryptoApproveAllowance()
                                .payingWith(PAYER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .signedBy(PAYER, OWNER)
                                .blankMemo()
                                .hasPrecheck(INVALID_ALLOWANCE_OWNER_ID),
                        cryptoApproveAllowance()
                                .payingWith(PAYER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .signedBy(PAYER, OWNER)
                                .blankMemo()
                                .hasPrecheck(INVALID_ALLOWANCE_OWNER_ID),
                        cryptoApproveAllowance()
                                .payingWith(PAYER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .signedBy(PAYER, OWNER)
                                .via(BASE_APPROVE_TXN)
                                .blankMemo()
                                .hasPrecheck(INVALID_ALLOWANCE_OWNER_ID))
                .then(getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .hasCostAnswerPrecheck(ACCOUNT_DELETED)
                        .hasAnswerOnlyPrecheck(ACCOUNT_DELETED));
    }

    @HapiTest
    private HapiSpec invalidSpenderFails() {
        return defaultHapiSpec("invalidSpenderFails")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoDelete(SPENDER),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .blankMemo()
                                .hasKnownStatus(INVALID_ALLOWANCE_SPENDER_ID),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .blankMemo()
                                .hasKnownStatus(INVALID_ALLOWANCE_SPENDER_ID),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .via(BASE_APPROVE_TXN)
                                .blankMemo()
                                .hasKnownStatus(INVALID_ALLOWANCE_SPENDER_ID))
                .then();
    }

    @HapiTest
    private HapiSpec noOwnerDefaultsToPayer() {
        return defaultHapiSpec("noOwnerDefaultsToPayer")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(PAYER, FUNGIBLE_TOKEN),
                        tokenAssociate(PAYER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, PAYER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(PAYER)
                                .addCryptoAllowance(MISSING_OWNER, ANOTHER_SPENDER, 100L)
                                .addTokenAllowance(MISSING_OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(MISSING_OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .via(APPROVE_TXN)
                                .blankMemo()
                                .logged(),
                        getTxnRecord(APPROVE_TXN).logged())
                .then(
                        validateChargedUsdWithin(APPROVE_TXN, 0.05238, 0.01),
                        getAccountDetails(PAYER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(1)
                                        .nftApprovedForAllAllowancesCount(0)
                                        .tokenAllowancesCount(1)
                                        .cryptoAllowancesContaining(ANOTHER_SPENDER, 100L)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 100L)));
    }

    @HapiTest
    private HapiSpec canHaveMultipleOwners() {
        return defaultHapiSpec("canHaveMultipleOwners")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SECOND_OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(10_000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SECOND_OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c"),
                                                ByteString.copyFromUtf8("d"),
                                                ByteString.copyFromUtf8("e"),
                                                ByteString.copyFromUtf8("f")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 1000L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                moving(500, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER),
                                moving(500, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SECOND_OWNER),
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER),
                                movingUnique(NON_FUNGIBLE_TOKEN, 4L, 5L, 6L).between(TOKEN_TREASURY, SECOND_OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addCryptoAllowance(OWNER, SPENDER, ONE_HBAR)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .addCryptoAllowance(SECOND_OWNER, SPENDER, ONE_HBAR)
                                .addTokenAllowance(SECOND_OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(SECOND_OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(4L))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addCryptoAllowance(OWNER, SPENDER, ONE_HBAR)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .addCryptoAllowance(SECOND_OWNER, SPENDER, ONE_HBAR)
                                .addTokenAllowance(SECOND_OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(SECOND_OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(4L))
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addCryptoAllowance(OWNER, SPENDER, ONE_HBAR)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .addCryptoAllowance(SECOND_OWNER, SPENDER, ONE_HBAR)
                                .addTokenAllowance(SECOND_OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(SECOND_OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(4L))
                                .signedBy(DEFAULT_PAYER, SECOND_OWNER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addCryptoAllowance(OWNER, SPENDER, ONE_HBAR)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .addCryptoAllowance(SECOND_OWNER, SPENDER, 2 * ONE_HBAR)
                                .addTokenAllowance(SECOND_OWNER, FUNGIBLE_TOKEN, SPENDER, 300L)
                                .addNftAllowance(SECOND_OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(4L, 5L))
                                .signedBy(DEFAULT_PAYER, OWNER, SECOND_OWNER))
                .then(
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 100L)
                                        .cryptoAllowancesContaining(SPENDER, ONE_HBAR)),
                        getAccountDetails(SECOND_OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 300L)
                                        .cryptoAllowancesContaining(SPENDER, 2 * ONE_HBAR)));
    }

    @HapiTest
    private HapiSpec feesAsExpected() {
        return defaultHapiSpec("feesAsExpected")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .via("approve")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approve", 0.05, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .via("approveTokenTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveTokenTxn", 0.05012, 0.01))
                .then(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .via("approveNftTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveNftTxn", 0.050101, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, ANOTHER_SPENDER, true, List.of())
                                .via("approveForAllNftTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveForAllNftTxn", 0.05, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SECOND_SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SECOND_SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SECOND_SPENDER, false, List.of(1L))
                                .via(APPROVE_TXN)
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin(APPROVE_TXN, 0.05238, 0.01),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(2)
                                        .nftApprovedForAllAllowancesCount(1)
                                        .tokenAllowancesCount(2)
                                        .cryptoAllowancesContaining(SECOND_SPENDER, 100L)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SECOND_SPENDER, 100L)),
                        /* edit existing allowances */
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SECOND_SPENDER, 200L)
                                .via("approveModifyCryptoTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveModifyCryptoTxn", 0.049375, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SECOND_SPENDER, 200L)
                                .via("approveModifyTokenTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveModifyTokenTxn", 0.04943, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, ANOTHER_SPENDER, false, List.of())
                                .via("approveModifyNftTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveModifyNftTxn", 0.049375, 0.01),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(2)
                                        .nftApprovedForAllAllowancesCount(0)
                                        .tokenAllowancesCount(2)
                                        .cryptoAllowancesContaining(SECOND_SPENDER, 200L)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SECOND_SPENDER, 200L)));
    }

    @HapiTest
    private HapiSpec serialsInAscendingOrder() {
        return defaultHapiSpec("serialsInAscendingOrder")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c"),
                                                ByteString.copyFromUtf8("d")))
                                .via(NFT_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L, 4L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of(1L))
                                .fee(ONE_HBAR),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, ANOTHER_SPENDER, false, List.of(4L, 2L, 3L))
                                .fee(ONE_HBAR))
                .then(getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .logged()
                        .has(accountDetailsWith()
                                .nftApprovedForAllAllowancesCount(1)
                                .nftApprovedAllowancesContaining(NON_FUNGIBLE_TOKEN, SPENDER)));
    }

    @HapiTest
    private HapiSpec succeedsWhenTokenPausedFrozenKycRevoked() {
        return defaultHapiSpec("succeedsWhenTokenPausedFrozenKycRevoked")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(PAUSE_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(THIRD_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .kycKey(KYC_KEY)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .kycKey(KYC_KEY)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        grantTokenKyc(FUNGIBLE_TOKEN, OWNER),
                        grantTokenKyc(NON_FUNGIBLE_TOKEN, OWNER),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .fee(ONE_HBAR),
                        revokeTokenKyc(FUNGIBLE_TOKEN, OWNER),
                        revokeTokenKyc(NON_FUNGIBLE_TOKEN, OWNER),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, ANOTHER_SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, ANOTHER_SPENDER, false, List.of(1L))
                                .fee(ONE_HBAR))
                .then(
                        tokenPause(FUNGIBLE_TOKEN),
                        tokenPause(NON_FUNGIBLE_TOKEN),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SECOND_SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SECOND_SPENDER, false, List.of(2L))
                                .fee(ONE_HBAR),
                        tokenUnpause(FUNGIBLE_TOKEN),
                        tokenUnpause(NON_FUNGIBLE_TOKEN),
                        tokenFreeze(FUNGIBLE_TOKEN, OWNER),
                        tokenFreeze(NON_FUNGIBLE_TOKEN, OWNER),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, THIRD_SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, THIRD_SPENDER, false, List.of(3L))
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(0)
                                        .nftApprovedForAllAllowancesCount(0)
                                        .tokenAllowancesCount(4)));
    }

    @HapiTest
    private HapiSpec tokenExceedsMaxSupplyFails() {
        return defaultHapiSpec("tokenExceedsMaxSupplyFails")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN))
                .when(cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 5000L)
                        .fee(ONE_HUNDRED_HBARS)
                        .hasPrecheck(AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY))
                .then();
    }

    @HapiTest
    private HapiSpec validatesSerialNums() {
        return defaultHapiSpec("validatesSerialNums")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1000L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(-1000L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(3L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasKnownStatus(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(2L, 2L, 2L))
                                .fee(ONE_HUNDRED_HBARS))
                .then();
    }

    @HapiTest
    private HapiSpec invalidTokenTypeFails() {
        return defaultHapiSpec("invalidTokenTypeFails")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, 100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES))
                .then();
    }

    @HapiTest
    private HapiSpec emptyAllowancesRejected() {
        return defaultHapiSpec("emptyAllowancesRejected")
                .given(cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10))
                .when(cryptoApproveAllowance().hasPrecheck(EMPTY_ALLOWANCES).fee(ONE_HUNDRED_HBARS))
                .then();
    }

    @HapiTest
    private HapiSpec tokenNotAssociatedToAccountFails() {
        return defaultHapiSpec("tokenNotAssociatedToAccountFails")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT))
                .then(getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .has(accountDetailsWith()
                                .cryptoAllowancesCount(0)
                                .nftApprovedForAllAllowancesCount(0)
                                .tokenAllowancesCount(0)));
    }

    @HapiTest
    private HapiSpec negativeAmountFailsForFungible() {
        return defaultHapiSpec("negativeAmountFailsForFungible")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, -100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(NEGATIVE_ALLOWANCE_AMOUNT),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, -100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(NEGATIVE_ALLOWANCE_AMOUNT))
                .then(getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .has(accountDetailsWith()
                                .cryptoAllowancesCount(0)
                                .nftApprovedForAllAllowancesCount(0)
                                .tokenAllowancesCount(0)));
    }

    @HapiTest
    private HapiSpec happyPathWorks() {
        return defaultHapiSpec("happyPathWorks")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .via(BASE_APPROVE_TXN)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin(BASE_APPROVE_TXN, 0.05, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, ANOTHER_SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .via(APPROVE_TXN)
                                .blankMemo()
                                .logged())
                .then(
                        validateChargedUsdWithin(APPROVE_TXN, 0.05238, 0.01),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(2)
                                        .nftApprovedForAllAllowancesCount(0)
                                        .tokenAllowancesCount(1)
                                        .cryptoAllowancesContaining(SPENDER, 100L)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 100L)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(SPENDER));
    }

    @HapiTest
    private HapiSpec duplicateEntriesGetsReplacedWithDifferentTxn() {
        return defaultHapiSpec("duplicateEntriesGetsReplacedWithDifferentTxn")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                        NON_FUNGIBLE_TOKEN,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of(1L, 2L))
                                .via(BASE_APPROVE_TXN)
                                .blankMemo()
                                .logged(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(1)
                                        .nftApprovedForAllAllowancesCount(1)
                                        .tokenAllowancesCount(1)
                                        .cryptoAllowancesContaining(SPENDER, 100L)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 100L)
                                        .nftApprovedAllowancesContaining(NON_FUNGIBLE_TOKEN, SPENDER)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(SPENDER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(SPENDER),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 200L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 300L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(3L))
                                .via("duplicateAllowances"),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(SPENDER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(SPENDER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 3L).hasSpenderID(SPENDER),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(1)
                                        .nftApprovedForAllAllowancesCount(0)
                                        .tokenAllowancesCount(1)
                                        .cryptoAllowancesContaining(SPENDER, 200L)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 300L)))
                .then(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 0L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 0L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of())
                                .via("removeAllowances"),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(0)
                                        .nftApprovedForAllAllowancesCount(1)
                                        .tokenAllowancesCount(0)
                                        .nftApprovedAllowancesContaining(NON_FUNGIBLE_TOKEN, SPENDER)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(SPENDER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(SPENDER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 3L).hasSpenderID(SPENDER));
    }

    @HapiTest
    private HapiSpec cannotHaveMultipleAllowedSpendersForTheSameNFTSerial() {
        return defaultHapiSpec("CannotHaveMultipleAllowedSpendersForTheSameNFTSerial")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("a")))
                                .via(NFT_TOKEN_MINT_TXN),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of(1L))
                                .signedBy(DEFAULT_PAYER, OWNER),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SECOND_SPENDER, true, List.of(1L))
                                .signedBy(DEFAULT_PAYER, OWNER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L)
                                .hasSpenderID(SECOND_SPENDER)
                                .logged(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(2)))
                .then(
                        cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                        .between(OWNER, RECEIVER))
                                .payingWith(SECOND_SPENDER)
                                .signedBy(SECOND_SPENDER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasNoSpender().logged(),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(RECEIVER, OWNER)),
                        cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                        .between(OWNER, RECEIVER))
                                .payingWith(SECOND_SPENDER)
                                .signedBy(SECOND_SPENDER),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SECOND_SPENDER, false, List.of())
                                .signedBy(DEFAULT_PAYER, OWNER),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(1)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(RECEIVER, OWNER)),
                        cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                        .between(OWNER, RECEIVER))
                                .payingWith(SECOND_SPENDER)
                                .signedBy(SECOND_SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SECOND_SPENDER, false, List.of(1L))
                                .signedBy(DEFAULT_PAYER, OWNER),
                        cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                        .between(OWNER, RECEIVER))
                                .payingWith(SECOND_SPENDER)
                                .signedBy(SECOND_SPENDER),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(RECEIVER, OWNER)),
                        cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                        .between(OWNER, RECEIVER))
                                .payingWith(SECOND_SPENDER)
                                .signedBy(SECOND_SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE));
    }

    @HapiTest
    private HapiSpec approveForAllDoesNotSetExplicitNFTSpender() {
        return defaultHapiSpec("approveForAllDoesNotSetExplicitNFTSpender")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("a")))
                                .via(NFT_TOKEN_MINT_TXN),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of())
                                .signedBy(DEFAULT_PAYER, OWNER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasNoSpender().logged(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(1)))
                .then(
                        cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                        .between(OWNER, RECEIVER))
                                .payingWith(SPENDER),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasNoSpender().logged());
    }

    @HapiTest
    private HapiSpec scheduledCryptoApproveAllowanceWorks() {
        return defaultHapiSpec("ScheduledCryptoApproveAllowanceWorks")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(OTHER_RECEIVER).balance(ONE_HBAR).maxAutomaticTokenAssociations(1),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(10000)
                                .initialSupply(5000),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0L),
                        tokenCreate(TOKEN_WITH_CUSTOM_FEE)
                                .treasury(TOKEN_TREASURY)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(1000)
                                .maxSupply(5000)
                                .withCustom(fixedHtsFee(10, "0.0.0", TOKEN_TREASURY)),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"))))
                .when(
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN, TOKEN_WITH_CUSTOM_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN, TOKEN_WITH_CUSTOM_FEE),
                        cryptoTransfer(
                                moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER),
                                moving(15, TOKEN_WITH_CUSTOM_FEE).between(TOKEN_TREASURY, OWNER),
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)),
                        scheduleCreate(
                                        SCHEDULED_TXN,
                                        cryptoApproveAllowance()
                                                .addCryptoAllowance(OWNER, SPENDER, 10 * ONE_HBAR)
                                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 1500)
                                                .addTokenAllowance(OWNER, TOKEN_WITH_CUSTOM_FEE, SPENDER, 100)
                                                .addNftAllowance(
                                                        OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L, 2L, 3L))
                                                .fee(ONE_HUNDRED_HBARS))
                                .via("successTx"))
                .then(
                        cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 3)
                                        .between(OWNER, OTHER_RECEIVER))
                                .payingWith(SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        cryptoTransfer(movingWithAllowance(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR))
                                .payingWith(SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        scheduleSign(SCHEDULED_TXN).alsoSigningWith(OWNER),
                        cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 3)
                                        .between(OWNER, OTHER_RECEIVER))
                                .payingWith(SPENDER),
                        getAccountBalance(OTHER_RECEIVER).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                        cryptoTransfer(movingWithAllowance(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER))
                                .payingWith(SPENDER),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 50),
                        cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR))
                                .payingWith(SPENDER),
                        getAccountBalance(RECEIVER).hasTinyBars(15 * ONE_HBAR));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
