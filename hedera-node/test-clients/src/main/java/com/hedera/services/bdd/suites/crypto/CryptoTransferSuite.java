/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.node.app.hapi.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.ADHOC;
import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiPropertySource.accountIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTopicString;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AutoAssocAsserts.accountTokenPairsInAnyOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNonfungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.allowanceTinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeNoFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbarWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUniqueWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithDecimals;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withTargetLedgerId;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FEE_COLLECTOR;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.NODE_REWARD;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.STAKING_REWARD;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.accountId;
import static com.hedera.services.bdd.suites.contract.Utils.captureOneChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.Utils.ocWith;
import static com.hedera.services.bdd.suites.contract.evm.Evm46ValidationSuite.existingSystemAccounts;
import static com.hedera.services.bdd.suites.contract.evm.Evm46ValidationSuite.nonExistingSystemAccounts;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.A_TOKEN;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_MAX_AUTO_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransferList;
import java.math.BigInteger;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@Tag(ADHOC)
public class CryptoTransferSuite {
    private static final String OWNER = "owner";
    private static final String OTHER_OWNER = "otherOwner";
    private static final String SPENDER = "spender";
    private static final String PAYER = "payer";
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";
    private static final String TREASURY = "treasury";
    private static final String OTHER_RECEIVER = "otherReceiver";
    private static final String ANOTHER_RECEIVER = "anotherReceiver";
    private static final String FUNGIBLE_TOKEN = "fungible";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungible";
    private static final String TOKEN_WITH_CUSTOM_FEE = "tokenWithCustomFee";
    private static final String ADMIN_KEY = "adminKey";
    private static final String KYC_KEY = "kycKey";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String WIPE_KEY = "wipeKey";
    private static final String PAUSE_KEY = "pauseKey";
    private static final String PARTY = "party";
    public static final String COUNTERPARTY = "counterparty";
    private static final String MULTI_KEY = "multi";
    private static final String NOT_TO_BE = "notToBe";
    private static final String TOKEN = "token";
    private static final String OWNING_PARTY = "owningParty";
    private static final String VALID_TXN = "validTxn";
    private static final String UNCHECKED_TXN = "uncheckedTxn";
    private static final String PAYEE_SIG_REQ = "payeeSigReq";
    private static final String TOKENS_INVOLVED_LOG_MESSAGE =
            """
                    0 tokens involved,
                      2 account adjustments: {} tb, ${}"
                    1 tokens involved,
                      2 account adjustments: {} tb, ${} (~{}x pure crypto)
                      3 account adjustments: {} tb, ${} (~{}x pure crypto)
                      4 account adjustments: {} tb, ${} (~{}x pure crypto)
                      5 account adjustments: {} tb, ${} (~{}x pure crypto)
                      6 account adjustments: {} tb, ${} (~{}x pure crypto)
                    2 tokens involved,
                      4 account adjustments: {} tb, ${} (~{}x pure crypto)
                      5 account adjustments: {} tb, ${} (~{}x pure crypto)
                                                              6 account adjustments: {} tb, ${} (~{}x pure crypto)
                                                            3 tokens involved,
                                                              6 account adjustments: {} tb, ${} (~{}x pure crypto)
                                                                                                                     \s""";
    public static final String HODL_XFER = "hodlXfer";
    public static final String PAYEE_NO_SIG_REQ = "payeeNoSigReq";
    private static final String HBAR_XFER = "hbarXfer";
    private static final String NFT_XFER = "nftXfer";
    private static final String FT_XFER = "ftXfer";
    private static final String OTHER_ACCOUNT = "otheraccount";
    private static final String TOKEN_METADATA = "Please mind the vase.";

    @HapiTest
    final Stream<DynamicTest> insufficientBalanceForCustomFeeFails() {
        final var operatorKey = "operatorKey";
        final var accountId1Key = "accountId1Key";
        final var accountId2Key = "accountId2Key";
        final var operator = "operator";
        final var accountId1 = "accountId1";
        final var accountId2 = "accountId2";
        final var tokenId = "tokenId";
        return hapiTest(
                newKeyNamed(operatorKey),
                newKeyNamed(accountId1Key),
                newKeyNamed(accountId2Key),
                cryptoCreate(accountId1).balance(2 * ONE_HBAR).key(accountId1Key),
                cryptoCreate(accountId2).balance(2 * ONE_HBAR).key(accountId2Key),
                cryptoCreate(operator).balance(0L).key(operatorKey),
                tokenCreate(tokenId)
                        .name("ffff")
                        .treasury(operator)
                        .adminKey(operatorKey)
                        .feeScheduleKey(operatorKey)
                        .symbol("F")
                        .initialSupply(1)
                        .withCustom(fixedHbarFee(5000_000_000L, "operator"))
                        .initialSupply(1)
                        .decimals(0),
                tokenAssociate(accountId1, tokenId),
                tokenAssociate(accountId2, tokenId),
                cryptoTransfer(moving(1L, tokenId).between(operator, accountId1)),
                cryptoTransfer(moving(1L, tokenId).between(accountId1, accountId2))
                        .hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE));
    }

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate("a").maxAutomaticTokenAssociations(2),
                        cryptoCreate("b").maxAutomaticTokenAssociations(2),
                        tokenCreate("fungible").initialSupply(1_234_567),
                        tokenCreate("nonfungible")
                                .supplyKey("supplyKey")
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L),
                        mintToken("nonfungible", List.of(ByteString.copyFromUtf8("memo"))))
                .when()
                .then(submitModified(
                        withSuccessivelyVariedBodyIds(),
                        () -> cryptoTransfer(
                                movingHbar(2 * ONE_HBAR).distributing(DEFAULT_PAYER, "a", "b"),
                                moving(100L, "fungible").between(DEFAULT_PAYER, "a"),
                                movingUnique("nonfungible", 1L).between(DEFAULT_PAYER, "b"))));
    }

    @HapiTest
    final Stream<DynamicTest> okToRepeatSerialNumbersInWipeList() {
        final var ownerWith4AutoAssoc = "ownerWith4AutoAssoc";
        return defaultHapiSpec("OkToRepeatSerialNumbersInWipeList")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(TREASURY),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(ownerWith4AutoAssoc).balance(0L).maxAutomaticTokenAssociations(4),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TREASURY)
                                .maxSupply(12L)
                                .wipeKey(WIPE_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0L),
                        tokenAssociate(RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        copyFromUtf8("a"),
                                        copyFromUtf8("b"),
                                        copyFromUtf8("c"),
                                        copyFromUtf8("d"),
                                        copyFromUtf8("e"),
                                        copyFromUtf8("f"),
                                        copyFromUtf8("g"))))
                .when(cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
                        .between(TREASURY, ownerWith4AutoAssoc)))
                .then(
                        wipeTokenAccount(NON_FUNGIBLE_TOKEN, ownerWith4AutoAssoc, List.of(1L, 1L, 2L, 3L, 4L, 5L, 6L)),
                        wipeTokenAccount(NON_FUNGIBLE_TOKEN, ownerWith4AutoAssoc, List.of(7L)),
                        getAccountBalance(ownerWith4AutoAssoc).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0L));
    }

    @HapiTest
    final Stream<DynamicTest> okToRepeatSerialNumbersInBurnList() {
        return defaultHapiSpec("okToRepeatSerialNumbersInBurnList")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TREASURY)
                                .maxSupply(12L)
                                .wipeKey(WIPE_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0L),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        copyFromUtf8("a"),
                                        copyFromUtf8("b"),
                                        copyFromUtf8("c"),
                                        copyFromUtf8("d"),
                                        copyFromUtf8("e"),
                                        copyFromUtf8("f"),
                                        copyFromUtf8("g"))))
                .when()
                .then(
                        burnToken(NON_FUNGIBLE_TOKEN, List.of(1L, 1L, 2L, 3L, 4L, 5L, 6L)),
                        burnToken(NON_FUNGIBLE_TOKEN, List.of(7L)),
                        getAccountBalance(TREASURY).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0L));
    }

    @HapiTest // fees differ expected 46889349 actual 46887567
    final Stream<DynamicTest> canUseAliasAndAccountCombinations() {
        final AtomicReference<TokenID> ftId = new AtomicReference<>();
        final AtomicReference<TokenID> nftId = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<AccountID> counterId = new AtomicReference<>();
        final AtomicReference<AccountID> otherAccountId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
        final AtomicReference<ByteString> counterAlias = new AtomicReference<>();
        final var collector = "collector";

        return hapiTest(
                newKeyNamed(MULTI_KEY),
                cryptoCreate(collector),
                cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                cryptoCreate(COUNTERPARTY).maxAutomaticTokenAssociations(2),
                cryptoCreate(OTHER_ACCOUNT).maxAutomaticTokenAssociations(2),
                tokenCreate(FUNGIBLE_TOKEN).treasury(PARTY).initialSupply(1_000_000),
                tokenCreate("FEE_DENOM").treasury(collector),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .initialSupply(0)
                        .treasury(PARTY)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .supplyKey(MULTI_KEY)
                        .withCustom(royaltyFeeWithFallback(
                                1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, "FEE_DENOM"), collector)),
                mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8(TOKEN_METADATA))),
                withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    ftId.set(registry.getTokenID(FUNGIBLE_TOKEN));
                    nftId.set(registry.getTokenID(NON_FUNGIBLE_TOKEN));
                    partyId.set(registry.getAccountID(PARTY));
                    counterId.set(registry.getAccountID(COUNTERPARTY));
                    partyAlias.set(ByteString.copyFrom(asSolidityAddress(partyId.get())));
                    counterAlias.set(ByteString.copyFrom(asSolidityAddress(counterId.get())));
                    otherAccountId.set(registry.getAccountID(OTHER_ACCOUNT));
                }),
                cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                        .setToken(nftId.get())
                                        .addNftTransfers(
                                                ocWith(accountId(partyAlias.get()), accountId(counterAlias.get()), 1L)))
                                .setTransfers(TransferList.newBuilder()
                                        .addAccountAmounts(aaWith(partyId.get(), +2))
                                        .addAccountAmounts(aaWith(otherAccountId.get(), -2))))
                        .signedBy(DEFAULT_PAYER, PARTY, OTHER_ACCOUNT)
                        .via(NFT_XFER),
                getTxnRecord(NFT_XFER));
    }

    @HapiTest
    final Stream<DynamicTest> aliasKeysAreValidated() {
        final var validAlias = "validAlias";
        final var invalidAlias = "invalidAlias";

        return hapiTest(
                newKeyNamed(validAlias).shape(ED25519),
                withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    final var validKey = registry.getKey(validAlias);
                    final var invalidBytes = new byte[validKey.getEd25519().size() + 8];
                    final var validBytes = validKey.getEd25519().toByteArray();
                    // Add noise bytes to the end of the otherwise valid key
                    System.arraycopy(validBytes, 0, invalidBytes, 0, validBytes.length);
                    final var noise = randomUtf8Bytes(8);
                    System.arraycopy(noise, 0, invalidBytes, validBytes.length, noise.length);
                    final var invalidKey = Key.newBuilder()
                            .setEd25519(ByteString.copyFrom(invalidBytes))
                            .build();
                    registry.saveKey(invalidAlias, invalidKey);
                }),
                cryptoCreate(CIVILIAN).balance(ONE_HUNDRED_HBARS),
                cryptoTransfer(tinyBarsFromToWithAlias(CIVILIAN, validAlias, ONE_HBAR)),
                cryptoTransfer(tinyBarsFromToWithAlias(CIVILIAN, invalidAlias, ONE_HBAR))
                        .hasKnownStatus(INVALID_ALIAS_KEY));
    }

    // https://github.com/hashgraph/hedera-services/issues/2875
    @HapiTest
    final Stream<DynamicTest> canUseMirrorAliasesForNonContractXfers() {
        final AtomicReference<TokenID> ftId = new AtomicReference<>();
        final AtomicReference<TokenID> nftId = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<AccountID> counterId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
        final AtomicReference<ByteString> counterAlias = new AtomicReference<>();

        return hapiTest(
                newKeyNamed(MULTI_KEY),
                cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                cryptoCreate(COUNTERPARTY).maxAutomaticTokenAssociations(2),
                tokenCreate(FUNGIBLE_TOKEN).treasury(PARTY).initialSupply(1_000_000),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .initialSupply(0)
                        .treasury(PARTY)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .supplyKey(MULTI_KEY),
                mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8(TOKEN_METADATA))),
                withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    ftId.set(registry.getTokenID(FUNGIBLE_TOKEN));
                    nftId.set(registry.getTokenID(NON_FUNGIBLE_TOKEN));
                    partyId.set(registry.getAccountID(PARTY));
                    counterId.set(registry.getAccountID(COUNTERPARTY));
                    partyAlias.set(ByteString.copyFrom(asSolidityAddress(partyId.get())));
                    counterAlias.set(ByteString.copyFrom(asSolidityAddress(counterId.get())));
                }),
                cryptoTransfer((spec, b) -> b.setTransfers(TransferList.newBuilder()
                                .addAccountAmounts(aaWith(partyAlias.get(), -1))
                                .addAccountAmounts(aaWith(partyId.get(), -1))
                                .addAccountAmounts(aaWith(counterId.get(), +2))))
                        .signedBy(DEFAULT_PAYER, PARTY)
                        .hasKnownStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                // Check signing requirements aren't distorted by aliases
                cryptoTransfer((spec, b) -> b.setTransfers(TransferList.newBuilder()
                                .addAccountAmounts(aaWith(partyAlias.get(), -2))
                                .addAccountAmounts(aaWith(counterId.get(), +2))))
                        .signedBy(DEFAULT_PAYER)
                        .hasKnownStatus(INVALID_SIGNATURE),
                cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(nftId.get())
                                .addNftTransfers(ocWith(accountId(partyAlias.get()), counterId.get(), 1L))))
                        .signedBy(DEFAULT_PAYER)
                        .hasKnownStatus(INVALID_SIGNATURE),
                cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(ftId.get())
                                .addTransfers(aaWith(partyAlias.get(), -500))
                                .addTransfers(aaWith(counterAlias.get(), +500))))
                        .signedBy(DEFAULT_PAYER)
                        .hasKnownStatus(INVALID_SIGNATURE),
                // Now do the actual transfers
                cryptoTransfer((spec, b) -> b.setTransfers(TransferList.newBuilder()
                                .addAccountAmounts(aaWith(partyAlias.get(), -2))
                                .addAccountAmounts(aaWith(counterAlias.get(), +2))))
                        .signedBy(DEFAULT_PAYER, PARTY)
                        .via(HBAR_XFER),
                cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(nftId.get())
                                .addNftTransfers(
                                        ocWith(accountId(partyAlias.get()), accountId(counterAlias.get()), 1L))))
                        .signedBy(DEFAULT_PAYER, PARTY)
                        .via(NFT_XFER),
                cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(ftId.get())
                                .addTransfers(aaWith(partyAlias.get(), -500))
                                .addTransfers(aaWith(counterAlias.get(), +500))))
                        .signedBy(DEFAULT_PAYER, PARTY)
                        .via(FT_XFER),
                getTxnRecord(HBAR_XFER),
                getTxnRecord(NFT_XFER),
                getTxnRecord(FT_XFER));
    }

    @SuppressWarnings("java:S5669")
    @HapiTest
    final Stream<DynamicTest> canUseEip1014AliasesForXfers() {
        final var partyCreation2 = "partyCreation2";
        final var counterCreation2 = "counterCreation2";
        final var contract = "CreateDonor";

        final AtomicReference<String> partyAliasAddr = new AtomicReference<>();
        final AtomicReference<String> partyMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> counterAliasAddr = new AtomicReference<>();
        final AtomicReference<String> counterMirrorAddr = new AtomicReference<>();
        final AtomicReference<TokenID> ftId = new AtomicReference<>();
        final AtomicReference<TokenID> nftId = new AtomicReference<>();
        final AtomicReference<String> partyLiteral = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<String> counterLiteral = new AtomicReference<>();
        final AtomicReference<AccountID> counterId = new AtomicReference<>();

        final byte[] salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");
        final byte[] otherSalt = unhex("aabbccddee880011aabbccddee880011aabbccddee880011aabbccddee880011");

        return defaultHapiSpec("CanUseEip1014AliasesForXfers")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(contract),
                        contractCreate(contract).adminKey(MULTI_KEY).payingWith(GENESIS),
                        contractCall(contract, "buildDonor", salt)
                                .sending(1000)
                                .payingWith(GENESIS)
                                .gas(2_000_000L)
                                .via(partyCreation2),
                        captureOneChildCreate2MetaFor(PARTY, partyCreation2, partyMirrorAddr, partyAliasAddr),
                        contractCall(contract, "buildDonor", otherSalt)
                                .sending(1000)
                                .payingWith(GENESIS)
                                .gas(2_000_000L)
                                .via(counterCreation2),
                        captureOneChildCreate2MetaFor(
                                COUNTERPARTY, counterCreation2, counterMirrorAddr, counterAliasAddr),
                        tokenCreate(FUNGIBLE_TOKEN).treasury(TOKEN_TREASURY).initialSupply(1_000_000),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8(TOKEN_METADATA))),
                        withOpContext((spec, opLog) -> {
                            final var registry = spec.registry();
                            ftId.set(registry.getTokenID(FUNGIBLE_TOKEN));
                            nftId.set(registry.getTokenID(NON_FUNGIBLE_TOKEN));
                            partyId.set(accountIdFromHexedMirrorAddress(partyMirrorAddr.get()));
                            partyLiteral.set(asAccountString(partyId.get()));
                            counterId.set(accountIdFromHexedMirrorAddress(counterMirrorAddr.get()));
                            counterLiteral.set(asAccountString(counterId.get()));
                        }))
                .when(
                        sourcing(() -> tokenAssociate(partyLiteral.get(), List.of(FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)),
                        sourcing(() -> tokenAssociate(counterLiteral.get(), List.of(FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)),
                        sourcing(() -> getContractInfo(partyLiteral.get()).logged()),
                        sourcing(() -> cryptoTransfer(
                                        moving(500_000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, partyLiteral.get()),
                                        movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                                .between(TOKEN_TREASURY, partyLiteral.get()))
                                .signedBy(DEFAULT_PAYER, TOKEN_TREASURY)),
                        cryptoTransfer((spec, b) -> b.setTransfers(TransferList.newBuilder()
                                        .addAccountAmounts(aaWith(partyAliasAddr.get(), -1))
                                        .addAccountAmounts(aaWith(partyId.get(), -1))
                                        .addAccountAmounts(aaWith(counterId.get(), +2))))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)
                                .hasKnownStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        // Check signing requirements aren't distorted by aliases
                        cryptoTransfer((spec, b) -> b.setTransfers(TransferList.newBuilder()
                                        .addAccountAmounts(aaWith(partyAliasAddr.get(), -2))
                                        .addAccountAmounts(aaWith(counterAliasAddr.get(), +2))))
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                        .setToken(nftId.get())
                                        .addNftTransfers(ocWith(accountId(partyAliasAddr.get()), counterId.get(), 1L))))
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                        .setToken(ftId.get())
                                        .addTransfers(aaWith(partyAliasAddr.get(), -500))
                                        .addTransfers(aaWith(counterAliasAddr.get(), +500))))
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        // Now do the actual transfers
                        cryptoTransfer((spec, b) -> b.setTransfers(TransferList.newBuilder()
                                        .addAccountAmounts(aaWith(partyAliasAddr.get(), -2))
                                        .addAccountAmounts(aaWith(counterAliasAddr.get(), +2))))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)
                                .via(HBAR_XFER),
                        cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                        .setToken(nftId.get())
                                        .addNftTransfers(ocWith(
                                                accountId(partyAliasAddr.get()),
                                                accountId(counterAliasAddr.get()),
                                                1L))))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)
                                .via(NFT_XFER),
                        cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                        .setToken(ftId.get())
                                        .addTransfers(aaWith(partyAliasAddr.get(), -500))
                                        .addTransfers(aaWith(counterAliasAddr.get(), +500))))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)
                                .via(FT_XFER))
                .then(
                        sourcing(() -> getTxnRecord(HBAR_XFER)
                                .hasPriority(recordWith()
                                        .transfers(including(
                                                tinyBarsFromTo(partyLiteral.get(), counterLiteral.get(), 2))))),
                        sourcing(() -> getTxnRecord(NFT_XFER)
                                .hasPriority(recordWith()
                                        .tokenTransfers(
                                                includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                                        .between(partyLiteral.get(), counterLiteral.get()))))),
                        sourcing(() -> getTxnRecord(FT_XFER)
                                .hasPriority(recordWith()
                                        .tokenTransfers(includingFungibleMovement(moving(500, FUNGIBLE_TOKEN)
                                                .between(partyLiteral.get(), counterLiteral.get()))))));
    }

    @HapiTest
    final Stream<DynamicTest> cannotTransferFromImmutableAccounts() {
        final var contract = "PayableConstructor";
        final var multiKey = "swiss";

        return hapiTest(
                newKeyNamed(multiKey),
                uploadInitCode(contract),
                // why is there transactionFee here ?
                contractCreate(contract).balance(ONE_HBAR).immutable().payingWith(GENESIS),
                // Even the treasury cannot withdraw from an immutable contract
                cryptoTransfer(tinyBarsFromTo(contract, FUNDING, ONE_HBAR))
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_SIGNATURE),
                // Even the treasury cannot withdraw staking funds
                cryptoTransfer(tinyBarsFromTo(STAKING_REWARD, FUNDING, ONE_HBAR))
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_ACCOUNT_ID),
                cryptoTransfer(tinyBarsFromTo(NODE_REWARD, FUNDING, ONE_HBAR))
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_ACCOUNT_ID),
                // Immutable accounts cannot be updated or deleted
                cryptoUpdate(STAKING_REWARD)
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_ACCOUNT_ID),
                cryptoDelete(STAKING_REWARD)
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_ACCOUNT_ID),
                // Immutable accounts cannot serve any role for tokens
                tokenCreate(TOKEN).adminKey(multiKey),
                tokenAssociate(NODE_REWARD, TOKEN)
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_ACCOUNT_ID),
                tokenUpdate(TOKEN)
                        .payingWith(GENESIS)
                        .signedBy(GENESIS, multiKey)
                        .fee(ONE_HBAR)
                        .treasury(STAKING_REWARD)
                        .hasKnownStatus(INVALID_ACCOUNT_ID),
                tokenCreate(NOT_TO_BE)
                        .treasury(STAKING_REWARD)
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_ACCOUNT_ID),
                tokenCreate(NOT_TO_BE)
                        .autoRenewAccount(NODE_REWARD)
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
                tokenCreate(NOT_TO_BE)
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .withCustom(fixedHbarFee(5 * ONE_HBAR, STAKING_REWARD))
                        .hasKnownStatus(INVALID_CUSTOM_FEE_COLLECTOR),
                // Immutable accounts cannot be topic auto-renew accounts
                createTopic(NOT_TO_BE)
                        .autoRenewAccountId(NODE_REWARD)
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
                // Immutable accounts cannot be schedule transaction payers
                scheduleCreate(NOT_TO_BE, cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1)))
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .designatingPayer(STAKING_REWARD)
                        .fee(ONE_HUNDRED_HBARS)
                        .hasKnownStatus(INVALID_ACCOUNT_ID),
                // Immutable accounts cannot approve or adjust allowances
                cryptoApproveAllowance()
                        .payingWith(GENESIS)
                        .signedBy(GENESIS)
                        .fee(ONE_HBAR)
                        .addCryptoAllowance(NODE_REWARD, FUNDING, 100L)
                        .hasKnownStatus(INVALID_ALLOWANCE_OWNER_ID));
    }

    @HapiTest
    final Stream<DynamicTest> allowanceTransfersWithComplexTransfersWork() {
        return hapiTest(
                newKeyNamed(ADMIN_KEY),
                newKeyNamed(FREEZE_KEY),
                newKeyNamed(KYC_KEY),
                newKeyNamed(SUPPLY_KEY),
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(OTHER_OWNER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(RECEIVER).balance(0L),
                cryptoCreate(OTHER_RECEIVER).balance(ONE_HBAR),
                cryptoCreate(ANOTHER_RECEIVER).balance(0L),
                cryptoCreate(TOKEN_TREASURY),
                tokenCreate(FUNGIBLE_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(FUNGIBLE_COMMON)
                        .treasury(TOKEN_TREASURY)
                        .maxSupply(10000)
                        .initialSupply(5000)
                        .adminKey(ADMIN_KEY)
                        .kycKey(KYC_KEY),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .maxSupply(12L)
                        .supplyKey(SUPPLY_KEY)
                        .adminKey(ADMIN_KEY)
                        .kycKey(KYC_KEY)
                        .initialSupply(0L),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                ByteString.copyFromUtf8("a"),
                                ByteString.copyFromUtf8("b"),
                                ByteString.copyFromUtf8("c"),
                                ByteString.copyFromUtf8("d"),
                                ByteString.copyFromUtf8("e"))),
                tokenAssociate(OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN),
                tokenAssociate(OTHER_OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN),
                tokenAssociate(RECEIVER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN),
                tokenAssociate(SPENDER, FUNGIBLE_TOKEN),
                tokenAssociate(ANOTHER_RECEIVER, FUNGIBLE_TOKEN),
                grantTokenKyc(FUNGIBLE_TOKEN, OWNER),
                grantTokenKyc(FUNGIBLE_TOKEN, OTHER_OWNER),
                grantTokenKyc(FUNGIBLE_TOKEN, RECEIVER),
                grantTokenKyc(FUNGIBLE_TOKEN, ANOTHER_RECEIVER),
                grantTokenKyc(FUNGIBLE_TOKEN, SPENDER),
                grantTokenKyc(NON_FUNGIBLE_TOKEN, OWNER),
                grantTokenKyc(NON_FUNGIBLE_TOKEN, OTHER_OWNER),
                grantTokenKyc(NON_FUNGIBLE_TOKEN, RECEIVER),
                cryptoTransfer(
                        moving(100, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SPENDER),
                        moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER),
                        movingUnique(NON_FUNGIBLE_TOKEN, 1, 2).between(TOKEN_TREASURY, OWNER),
                        moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OTHER_OWNER),
                        movingUnique(NON_FUNGIBLE_TOKEN, 3, 4).between(TOKEN_TREASURY, OTHER_OWNER)),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addCryptoAllowance(OWNER, SPENDER, 10 * ONE_HBAR)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 500)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L, 2L))
                        .fee(ONE_HUNDRED_HBARS),
                cryptoApproveAllowance()
                        .payingWith(OTHER_OWNER)
                        .addCryptoAllowance(OTHER_OWNER, SPENDER, 5 * ONE_HBAR)
                        .addTokenAllowance(OTHER_OWNER, FUNGIBLE_TOKEN, SPENDER, 100)
                        .addNftAllowance(OTHER_OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of(3L))
                        .fee(ONE_HUNDRED_HBARS),
                cryptoTransfer(
                                movingHbar(ONE_HBAR).between(SPENDER, RECEIVER),
                                movingHbar(ONE_HBAR).between(OTHER_RECEIVER, ANOTHER_RECEIVER),
                                movingHbar(ONE_HBAR).between(OWNER, RECEIVER),
                                movingHbar(ONE_HBAR).between(OTHER_OWNER, RECEIVER),
                                movingHbarWithAllowance(ONE_HBAR).between(OWNER, RECEIVER),
                                movingHbarWithAllowance(ONE_HBAR).between(OTHER_OWNER, RECEIVER),
                                moving(50, FUNGIBLE_TOKEN).between(RECEIVER, ANOTHER_RECEIVER),
                                moving(50, FUNGIBLE_TOKEN).between(SPENDER, RECEIVER),
                                moving(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER),
                                moving(15, FUNGIBLE_TOKEN).between(OTHER_OWNER, RECEIVER),
                                movingWithAllowance(30, FUNGIBLE_TOKEN).between(OWNER, RECEIVER),
                                movingWithAllowance(10, FUNGIBLE_TOKEN).between(OTHER_OWNER, RECEIVER),
                                movingWithAllowance(5, FUNGIBLE_TOKEN).between(OTHER_OWNER, OWNER),
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, RECEIVER),
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L)
                                        .between(OWNER, RECEIVER),
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 4L)
                                        .between(OTHER_OWNER, RECEIVER),
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 3L)
                                        .between(OTHER_OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER, OWNER, OTHER_RECEIVER, OTHER_OWNER)
                        .via("complexAllowanceTransfer"),
                getTxnRecord("complexAllowanceTransfer").logged(),
                getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(925))
                        .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN).balance(0))
                        .has(AccountDetailsAsserts.accountDetailsWith()
                                .balanceLessThan(98 * ONE_HBAR)
                                .cryptoAllowancesContaining(SPENDER, 9 * ONE_HBAR)
                                .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 475)),
                getAccountDetails(OTHER_OWNER)
                        .payingWith(GENESIS)
                        .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(970))
                        .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN).balance(0))
                        .has(AccountDetailsAsserts.accountDetailsWith()
                                .balanceLessThan(98 * ONE_HBAR)
                                .cryptoAllowancesContaining(SPENDER, 4 * ONE_HBAR)
                                .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 85)
                                .nftApprovedAllowancesContaining(NON_FUNGIBLE_TOKEN, SPENDER)),
                getAccountInfo(RECEIVER)
                        .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(105))
                        .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN).balance(4))
                        .has(accountWith().balance(5 * ONE_HBAR)),
                getAccountInfo(ANOTHER_RECEIVER)
                        .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(50))
                        .has(accountWith().balance(ONE_HBAR)));
    }

    @HapiTest
    final Stream<DynamicTest> allowanceTransfersWorkAsExpected() {
        return hapiTest(
                newKeyNamed(ADMIN_KEY),
                newKeyNamed(FREEZE_KEY),
                newKeyNamed(KYC_KEY),
                newKeyNamed(PAUSE_KEY),
                newKeyNamed(SUPPLY_KEY),
                newKeyNamed(WIPE_KEY),
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
                        .initialSupply(5000)
                        .adminKey(ADMIN_KEY)
                        .pauseKey(PAUSE_KEY)
                        .kycKey(KYC_KEY)
                        .freezeKey(FREEZE_KEY),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .maxSupply(12L)
                        .supplyKey(SUPPLY_KEY)
                        .adminKey(ADMIN_KEY)
                        .freezeKey(FREEZE_KEY)
                        .wipeKey(WIPE_KEY)
                        .pauseKey(PAUSE_KEY)
                        .initialSupply(0L),
                tokenCreate(TOKEN_WITH_CUSTOM_FEE)
                        .treasury(TOKEN_TREASURY)
                        .supplyType(TokenSupplyType.FINITE)
                        .initialSupply(1000)
                        .maxSupply(5000)
                        .adminKey(ADMIN_KEY)
                        .withCustom(fixedHtsFee(10, "0.0.0", TOKEN_TREASURY)),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                ByteString.copyFromUtf8("a"),
                                ByteString.copyFromUtf8("b"),
                                ByteString.copyFromUtf8("c"),
                                ByteString.copyFromUtf8("d"),
                                ByteString.copyFromUtf8("e"),
                                ByteString.copyFromUtf8("f"))),
                tokenAssociate(OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN, TOKEN_WITH_CUSTOM_FEE),
                tokenAssociate(RECEIVER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN, TOKEN_WITH_CUSTOM_FEE),
                grantTokenKyc(FUNGIBLE_TOKEN, OWNER),
                grantTokenKyc(FUNGIBLE_TOKEN, RECEIVER),
                cryptoTransfer(
                        moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER),
                        moving(15, TOKEN_WITH_CUSTOM_FEE).between(TOKEN_TREASURY, OWNER),
                        movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L, 4L, 5L, 6L).between(TOKEN_TREASURY, OWNER)),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addCryptoAllowance(OWNER, SPENDER, 10 * ONE_HBAR)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 1500)
                        .addTokenAllowance(OWNER, TOKEN_WITH_CUSTOM_FEE, SPENDER, 100)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L, 2L, 3L, 4L, 6L))
                        .fee(ONE_HUNDRED_HBARS),
                cryptoTransfer(movingWithAllowance(10, TOKEN_WITH_CUSTOM_FEE).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .fee(ONE_HBAR)
                        // INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE is the expected mono service
                        // outcome here, but the modularized implementation doesn't provide enough information
                        // to differentiate these error codes. Since this is an extreme edge case and a
                        // difficult issue to fix, we'll allow either error code here for now. However, we may
                        // need to revert back to only accepting the original error code in the future.
                        .hasKnownStatusFrom(
                                INSUFFICIENT_TOKEN_BALANCE, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE),
                cryptoTransfer(movingWithAllowance(100, FUNGIBLE_TOKEN).between(OWNER, OWNER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .dontFullyAggregateTokenTransfers()
                        .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 3).between(OWNER, OTHER_RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER),
                cryptoTransfer(movingWithAllowance(100, FUNGIBLE_TOKEN).between(OWNER, OTHER_RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS),
                cryptoUpdate(OTHER_RECEIVER).receiverSigRequired(true).maxAutomaticAssociations(2),
                cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 4).between(OWNER, OTHER_RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(INVALID_SIGNATURE),
                cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 4).between(OWNER, OTHER_RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER, OTHER_RECEIVER),
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 6).between(OWNER, RECEIVER)),
                cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 6).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 6).between(RECEIVER, OWNER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 6).between(RECEIVER, OWNER)),
                cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 6).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 6).between(OWNER, RECEIVER)),
                tokenAssociate(OTHER_RECEIVER, FUNGIBLE_TOKEN),
                grantTokenKyc(FUNGIBLE_TOKEN, OTHER_RECEIVER),
                cryptoTransfer(movingWithAllowance(1100, FUNGIBLE_TOKEN).between(OWNER, OTHER_RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER, OTHER_RECEIVER)
                        .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE),
                cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR))
                        .payingWith(DEFAULT_PAYER)
                        .signedBy(DEFAULT_PAYER)
                        .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                tokenPause(FUNGIBLE_TOKEN),
                cryptoTransfer(
                                movingWithAllowance(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER),
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(TOKEN_IS_PAUSED),
                tokenUnpause(FUNGIBLE_TOKEN),
                tokenFreeze(FUNGIBLE_TOKEN, OWNER),
                cryptoTransfer(
                                movingWithAllowance(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER),
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                tokenUnfreeze(FUNGIBLE_TOKEN, OWNER),
                revokeTokenKyc(FUNGIBLE_TOKEN, RECEIVER),
                cryptoTransfer(
                                movingWithAllowance(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER),
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
                grantTokenKyc(FUNGIBLE_TOKEN, RECEIVER),
                cryptoTransfer(
                                allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR),
                                tinyBarsFromTo(SPENDER, RECEIVER, ONE_HBAR))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER),
                cryptoTransfer(
                                movingWithAllowance(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER),
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER),
                cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR + 1))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(AMOUNT_EXCEEDS_ALLOWANCE),
                cryptoTransfer(
                                movingWithAllowance(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER),
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 5).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .has(AccountDetailsAsserts.accountDetailsWith()
                                .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 1450))
                        .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(950L)),
                cryptoTransfer(moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                cryptoTransfer(
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L)
                                        .between(OWNER, RECEIVER),
                                movingWithAllowance(1451, FUNGIBLE_TOKEN).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(AMOUNT_EXCEEDS_ALLOWANCE),
                getAccountInfo(OWNER)
                        .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN).balance(2)),
                cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER),
                cryptoTransfer(
                                movingWithAllowance(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER),
                                movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L)
                                        .between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER),
                cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 2L).between(RECEIVER, OWNER)),
                cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER)
                        .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of())
                        .fee(ONE_HUNDRED_HBARS),
                cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER),
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 2L).between(RECEIVER, OWNER)),
                cryptoTransfer(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L).between(OWNER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER),
                getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .has(AccountDetailsAsserts.accountDetailsWith()
                                .cryptoAllowancesCount(0)
                                .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 1400)
                                .nftApprovedAllowancesContaining(NON_FUNGIBLE_TOKEN, SPENDER)));
    }

    @HapiTest
    final Stream<DynamicTest> checksExpectedDecimalsForFungibleTokenTransferList() {
        return hapiTest(
                newKeyNamed(MULTI_KEY),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(OWNING_PARTY).maxAutomaticTokenAssociations(123),
                tokenCreate(FUNGIBLE_TOKEN)
                        .tokenType(FUNGIBLE_COMMON)
                        .treasury(TOKEN_TREASURY)
                        .decimals(2)
                        .initialSupply(1234)
                        .via("tokenCreate"),
                getTxnRecord("tokenCreate").hasNewTokenAssociation(FUNGIBLE_TOKEN, TOKEN_TREASURY),
                cryptoTransfer(moving(100, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNING_PARTY))
                        .via("initialXfer"),
                getTxnRecord("initialXfer").hasNewTokenAssociation(FUNGIBLE_TOKEN, OWNING_PARTY),
                getAccountInfo(OWNING_PARTY).savingSnapshot(OWNING_PARTY),
                cryptoTransfer(movingWithDecimals(10, FUNGIBLE_TOKEN, 4)
                                .betweenWithDecimals(TOKEN_TREASURY, OWNING_PARTY))
                        .signedBy(DEFAULT_PAYER, OWNING_PARTY, TOKEN_TREASURY)
                        .hasKnownStatus(UNEXPECTED_TOKEN_DECIMALS)
                        .via("failedTxn"),
                cryptoTransfer(movingWithDecimals(20, FUNGIBLE_TOKEN, 2)
                                .betweenWithDecimals(TOKEN_TREASURY, OWNING_PARTY))
                        .signedBy(DEFAULT_PAYER, OWNING_PARTY, TOKEN_TREASURY)
                        .hasKnownStatus(SUCCESS)
                        .via(VALID_TXN),
                usableTxnIdNamed(UNCHECKED_TXN).payerId(DEFAULT_PAYER),
                cryptoTransfer(movingWithDecimals(10, FUNGIBLE_TOKEN, 4)
                                .betweenWithDecimals(TOKEN_TREASURY, OWNING_PARTY))
                        .signedBy(DEFAULT_PAYER, OWNING_PARTY, TOKEN_TREASURY)
                        .txnId(UNCHECKED_TXN)
                        .hasKnownStatus(UNEXPECTED_TOKEN_DECIMALS),
                getReceipt(VALID_TXN).hasPriorityStatus(SUCCESS),
                getTxnRecord(VALID_TXN).logged(),
                getAccountInfo(OWNING_PARTY)
                        .hasAlreadyUsedAutomaticAssociations(1)
                        .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(120))
                        .logged());
    }

    @HapiTest
    final Stream<DynamicTest> nftTransfersCannotRepeatSerialNos() {
        final var aParty = "aParty";
        final var bParty = "bParty";
        final var cParty = "cParty";
        final var dParty = "dParty";
        final var multipurpose = MULTI_KEY;
        final var nftType = "nftType";
        final var hotTxn = "hotTxn";
        final var mintTxn = "mintTxn";

        return defaultHapiSpec("NftTransfersCannotRepeatSerialNos")
                .given(
                        newKeyNamed(multipurpose),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(aParty).maxAutomaticTokenAssociations(1),
                        cryptoCreate(bParty).maxAutomaticTokenAssociations(1),
                        cryptoCreate(cParty).maxAutomaticTokenAssociations(1),
                        cryptoCreate(dParty).maxAutomaticTokenAssociations(1),
                        tokenCreate(nftType)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(multipurpose)
                                .initialSupply(0),
                        mintToken(nftType, List.of(copyFromUtf8("Hot potato!"))).via(mintTxn),
                        getTxnRecord(mintTxn).logged())
                .when(cryptoTransfer(movingUnique(nftType, 1L).between(TOKEN_TREASURY, aParty)))
                .then(cryptoTransfer((spec, b) -> {
                            final var registry = spec.registry();
                            final var aId = registry.getAccountID(aParty);
                            final var bId = registry.getAccountID(bParty);
                            final var cId = registry.getAccountID(cParty);
                            final var dId = registry.getAccountID(dParty);
                            b.addTokenTransfers(TokenTransferList.newBuilder()
                                    .setToken(registry.getTokenID(nftType))
                                    .addNftTransfers(ocWith(aId, bId, 1))
                                    .addNftTransfers(ocWith(bId, cId, 1))
                                    .addNftTransfers(ocWith(cId, dId, 1)));
                        })
                        .via(hotTxn)
                        .signedBy(DEFAULT_PAYER, aParty, bParty, cParty)
                        .hasPrecheck(INVALID_ACCOUNT_AMOUNTS));
    }

    @HapiTest
    final Stream<DynamicTest> nftSelfTransfersRejectedBothInPrecheckAndHandle() {
        final var owningParty = OWNING_PARTY;
        final var multipurpose = MULTI_KEY;
        final var nftType = "nftType";
        final var uncheckedTxn = UNCHECKED_TXN;

        return hapiTest(
                newKeyNamed(multipurpose),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(owningParty).maxAutomaticTokenAssociations(123),
                tokenCreate(nftType)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .supplyKey(multipurpose)
                        .initialSupply(0),
                mintToken(nftType, List.of(copyFromUtf8("We"), copyFromUtf8("are"), copyFromUtf8("the"))),
                cryptoTransfer(movingUnique(nftType, 1L, 2L).between(TOKEN_TREASURY, owningParty)),
                getAccountInfo(owningParty).savingSnapshot(owningParty),
                cryptoTransfer(movingUnique(nftType, 1L).between(owningParty, owningParty))
                        .signedBy(DEFAULT_PAYER, owningParty)
                        .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                usableTxnIdNamed(uncheckedTxn).payerId(DEFAULT_PAYER),
                cryptoTransfer(movingUnique(nftType, 1L).between(owningParty, owningParty))
                        .signedBy(DEFAULT_PAYER, owningParty)
                        .txnId(uncheckedTxn)
                        .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                getAccountInfo(owningParty).has(accountWith().noChangesFromSnapshot(owningParty)));
    }

    @HapiTest
    final Stream<DynamicTest> hbarAndFungibleSelfTransfersRejectedBothInPrecheckAndHandle() {
        final var uncheckedHbarTxn = "uncheckedHbarTxn";
        final var uncheckedFtTxn = "uncheckedFtTxn";

        return defaultHapiSpec("HbarAndFungibleSelfTransfersRejectedBothInPrecheckAndHandle")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNING_PARTY).maxAutomaticTokenAssociations(123),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1234),
                        cryptoTransfer(moving(100, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNING_PARTY)))
                .when(
                        getAccountInfo(OWNING_PARTY).savingSnapshot(OWNING_PARTY),
                        cryptoTransfer(tinyBarsFromTo(OWNING_PARTY, OWNING_PARTY, 1))
                                .signedBy(DEFAULT_PAYER, OWNING_PARTY)
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        cryptoTransfer(moving(1, FUNGIBLE_TOKEN).between(OWNING_PARTY, OWNING_PARTY))
                                .signedBy(DEFAULT_PAYER, OWNING_PARTY)
                                .dontFullyAggregateTokenTransfers()
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        /* And bypassing precheck */
                        usableTxnIdNamed(uncheckedHbarTxn).payerId(DEFAULT_PAYER),
                        usableTxnIdNamed(uncheckedFtTxn).payerId(DEFAULT_PAYER),
                        cryptoTransfer(tinyBarsFromTo(OWNING_PARTY, OWNING_PARTY, 1))
                                .signedBy(DEFAULT_PAYER, OWNING_PARTY)
                                .txnId(uncheckedHbarTxn)
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        cryptoTransfer(moving(1, FUNGIBLE_TOKEN).between(OWNING_PARTY, OWNING_PARTY))
                                .signedBy(DEFAULT_PAYER, OWNING_PARTY)
                                .dontFullyAggregateTokenTransfers()
                                .txnId(uncheckedFtTxn)
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS))
                .then(
                        sleepFor(5_000),
                        getAccountInfo(OWNING_PARTY).has(accountWith().noChangesFromSnapshot(OWNING_PARTY)));
    }

    @HapiTest
    final Stream<DynamicTest> dissociatedRoyaltyCollectorsCanUseAutoAssociation() {
        final var commonWithCustomFees = "commonWithCustomFees";
        final var fractionalCollector = "fractionalCollector";
        final var selfDenominatedCollector = "selfDenominatedCollector";
        final var plentyOfSlots = 10;

        return hapiTest(
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(fractionalCollector).maxAutomaticTokenAssociations(plentyOfSlots),
                cryptoCreate(selfDenominatedCollector).maxAutomaticTokenAssociations(plentyOfSlots),
                cryptoCreate(PARTY).maxAutomaticTokenAssociations(plentyOfSlots),
                cryptoCreate(COUNTERPARTY).maxAutomaticTokenAssociations(plentyOfSlots),
                newKeyNamed(MULTI_KEY),
                getAccountInfo(PARTY).savingSnapshot(PARTY),
                getAccountInfo(COUNTERPARTY).savingSnapshot(COUNTERPARTY),
                getAccountInfo(fractionalCollector).savingSnapshot(fractionalCollector),
                getAccountInfo(selfDenominatedCollector).savingSnapshot(selfDenominatedCollector),
                tokenCreate(commonWithCustomFees)
                        .tokenType(FUNGIBLE_COMMON)
                        .treasury(TOKEN_TREASURY)
                        .withCustom(fractionalFee(1, 10, 0, OptionalLong.empty(), fractionalCollector))
                        .withCustom(fixedHtsFee(5, "0.0.0", selfDenominatedCollector))
                        .initialSupply(Long.MAX_VALUE)
                        .signedBy(DEFAULT_PAYER, TOKEN_TREASURY, fractionalCollector, selfDenominatedCollector),
                cryptoTransfer(moving(1_000_000, commonWithCustomFees).between(TOKEN_TREASURY, PARTY)),
                tokenDissociate(fractionalCollector, commonWithCustomFees),
                tokenDissociate(selfDenominatedCollector, commonWithCustomFees),
                cryptoTransfer(moving(1000, commonWithCustomFees).between(PARTY, COUNTERPARTY))
                        .fee(ONE_HBAR)
                        .via(HODL_XFER),
                getTxnRecord(HODL_XFER)
                        .hasPriority(recordWith()
                                .autoAssociated(accountTokenPairsInAnyOrder(List.of(
                                        /* The counterparty auto-associates to the fungible type */
                                        Pair.of(COUNTERPARTY, commonWithCustomFees),
                                        /* Both royalty collectors re-auto-associate */
                                        Pair.of(fractionalCollector, commonWithCustomFees),
                                        Pair.of(selfDenominatedCollector, commonWithCustomFees))))),
                getAccountInfo(fractionalCollector)
                        .has(accountWith()
                                .newAssociationsFromSnapshot(
                                        PARTY,
                                        List.of(relationshipWith(commonWithCustomFees)
                                                .balance(100)))),
                getAccountInfo(selfDenominatedCollector)
                        .has(accountWith()
                                .newAssociationsFromSnapshot(
                                        PARTY,
                                        List.of(relationshipWith(commonWithCustomFees)
                                                .balance(5)))));
    }

    @HapiTest
    final Stream<DynamicTest> royaltyCollectorsCannotUseAutoAssociationWithoutOpenSlots() {
        final var uniqueWithRoyalty = "uniqueWithRoyalty";
        final var someFungible = "firstFungible";
        final var royaltyCollectorNoSlots = "royaltyCollectorNoSlots";
        final var party = PARTY;
        final var counterparty = COUNTERPARTY;
        final var multipurpose = MULTI_KEY;
        final var hodlXfer = HODL_XFER;

        return hapiTest(
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(royaltyCollectorNoSlots),
                cryptoCreate(party).maxAutomaticTokenAssociations(123),
                cryptoCreate(counterparty).maxAutomaticTokenAssociations(123),
                newKeyNamed(multipurpose),
                getAccountInfo(party).savingSnapshot(party),
                getAccountInfo(counterparty).savingSnapshot(counterparty),
                getAccountInfo(royaltyCollectorNoSlots).savingSnapshot(royaltyCollectorNoSlots),
                tokenCreate(someFungible)
                        .treasury(TOKEN_TREASURY)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(123456789),
                cryptoTransfer(moving(1000, someFungible).between(TOKEN_TREASURY, counterparty)),
                tokenCreate(uniqueWithRoyalty)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .supplyKey(multipurpose)
                        .withCustom(royaltyFeeNoFallback(1, 12, royaltyCollectorNoSlots))
                        .initialSupply(0L),
                mintToken(uniqueWithRoyalty, List.of(copyFromUtf8("HODL"))),
                cryptoTransfer(movingUnique(uniqueWithRoyalty, 1L).between(TOKEN_TREASURY, party)),
                cryptoTransfer(
                                movingUnique(uniqueWithRoyalty, 1L).between(party, counterparty),
                                moving(123, someFungible).between(counterparty, party))
                        .fee(ONE_HBAR)
                        .via(hodlXfer)
                        .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                getTxnRecord(hodlXfer).hasPriority(recordWith().autoAssociated(accountTokenPairsInAnyOrder(List.of()))),
                getAccountInfo(party)
                        .has(accountWith()
                                .newAssociationsFromSnapshot(
                                        party,
                                        List.of(relationshipWith(uniqueWithRoyalty)
                                                .balance(1)))));
    }

    @HapiTest
    final Stream<DynamicTest> autoAssociationRequiresOpenSlots() {
        final String tokenA = "tokenA";
        final String tokenB = "tokenB";
        final String firstUser = "firstUser";
        final String secondUser = "secondUser";
        final String tokenAcreateTxn = "tokenACreate";
        final String tokenBcreateTxn = "tokenBCreate";
        final String transferToFU = "transferToFU";
        final String transferToSU = "transferToSU";

        return hapiTest(
                cryptoCreate(TREASURY).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(firstUser).balance(ONE_HBAR).maxAutomaticTokenAssociations(1),
                cryptoCreate(secondUser).balance(ONE_HBAR).maxAutomaticTokenAssociations(2),
                tokenCreate(tokenA)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(Long.MAX_VALUE)
                        .treasury(TREASURY)
                        .via(tokenAcreateTxn),
                getTxnRecord(tokenAcreateTxn)
                        .hasNewTokenAssociation(tokenA, TREASURY)
                        .logged(),
                tokenCreate(tokenB)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(Long.MAX_VALUE)
                        .treasury(TREASURY)
                        .via(tokenBcreateTxn),
                getTxnRecord(tokenBcreateTxn)
                        .hasNewTokenAssociation(tokenB, TREASURY)
                        .logged(),
                cryptoTransfer(moving(1, tokenA).between(TREASURY, firstUser)).via(transferToFU),
                getTxnRecord(transferToFU)
                        .hasNewTokenAssociation(tokenA, firstUser)
                        .logged(),
                cryptoTransfer(moving(1, tokenB).between(TREASURY, secondUser)).via(transferToSU),
                getTxnRecord(transferToSU).hasNewTokenAssociation(tokenB, secondUser),
                cryptoTransfer(moving(1, tokenB).between(TREASURY, firstUser))
                        .hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS)
                        .via("failedTransfer"),
                getAccountInfo(firstUser)
                        .hasAlreadyUsedAutomaticAssociations(1)
                        .hasMaxAutomaticAssociations(1)
                        .logged(),
                getAccountInfo(secondUser)
                        .hasAlreadyUsedAutomaticAssociations(1)
                        .hasMaxAutomaticAssociations(2)
                        .logged(),
                cryptoTransfer(moving(1, tokenA).between(TREASURY, secondUser)),
                getAccountInfo(secondUser)
                        .hasAlreadyUsedAutomaticAssociations(2)
                        .hasMaxAutomaticAssociations(2)
                        .logged(),
                cryptoTransfer(moving(1, tokenA).between(firstUser, TREASURY)),
                tokenDissociate(firstUser, tokenA),
                cryptoTransfer(moving(1, tokenB).between(TREASURY, firstUser)));
    }

    @HapiTest
    final Stream<DynamicTest> baseCryptoTransferFeeChargedAsExpected() {
        final var expectedHbarXferPriceUsd = 0.0001;
        final var expectedHtsXferPriceUsd = 0.001;
        final var expectedNftXferPriceUsd = 0.001;
        final var expectedHtsXferWithCustomFeePriceUsd = 0.002;
        final var expectedNftXferWithCustomFeePriceUsd = 0.002;
        final var transferAmount = 1L;
        final var customFeeCollector = "customFeeCollector";
        final var nonTreasurySender = "nonTreasurySender";
        final var hbarXferTxn = "hbarXferTxn";
        final var fungibleToken = "fungibleToken";
        final var fungibleTokenWithCustomFee = "fungibleTokenWithCustomFee";
        final var htsXferTxn = "htsXferTxn";
        final var htsXferTxnWithCustomFee = "htsXferTxnWithCustomFee";
        final var nonFungibleToken = "nonFungibleToken";
        final var nonFungibleTokenWithCustomFee = "nonFungibleTokenWithCustomFee";
        final var nftXferTxn = "nftXferTxn";
        final var nftXferTxnWithCustomFee = "nftXferTxnWithCustomFee";

        return hapiTest(
                cryptoCreate(nonTreasurySender).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(RECEIVER),
                cryptoCreate(customFeeCollector),
                tokenCreate(fungibleToken)
                        .treasury(SENDER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(100L),
                tokenCreate(fungibleTokenWithCustomFee)
                        .treasury(SENDER)
                        .tokenType(FUNGIBLE_COMMON)
                        .withCustom(fixedHbarFee(transferAmount, customFeeCollector))
                        .initialSupply(100L),
                tokenAssociate(RECEIVER, fungibleToken, fungibleTokenWithCustomFee),
                newKeyNamed(SUPPLY_KEY),
                tokenCreate(nonFungibleToken)
                        .initialSupply(0)
                        .supplyKey(SUPPLY_KEY)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(SENDER),
                tokenCreate(nonFungibleTokenWithCustomFee)
                        .initialSupply(0)
                        .supplyKey(SUPPLY_KEY)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .withCustom(fixedHbarFee(transferAmount, customFeeCollector))
                        .treasury(SENDER),
                tokenAssociate(nonTreasurySender, List.of(fungibleTokenWithCustomFee, nonFungibleTokenWithCustomFee)),
                mintToken(nonFungibleToken, List.of(copyFromUtf8("memo1"))),
                mintToken(nonFungibleTokenWithCustomFee, List.of(copyFromUtf8("memo2"))),
                tokenAssociate(RECEIVER, nonFungibleToken, nonFungibleTokenWithCustomFee),
                cryptoTransfer(movingUnique(nonFungibleTokenWithCustomFee, 1).between(SENDER, nonTreasurySender))
                        .payingWith(SENDER),
                cryptoTransfer(moving(1, fungibleTokenWithCustomFee).between(SENDER, nonTreasurySender))
                        .payingWith(SENDER),
                cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 100L))
                        .payingWith(SENDER)
                        .blankMemo()
                        .via(hbarXferTxn),
                cryptoTransfer(moving(1, fungibleToken).between(SENDER, RECEIVER))
                        .blankMemo()
                        .payingWith(SENDER)
                        .via(htsXferTxn),
                cryptoTransfer(movingUnique(nonFungibleToken, 1).between(SENDER, RECEIVER))
                        .blankMemo()
                        .payingWith(SENDER)
                        .via(nftXferTxn),
                cryptoTransfer(moving(1, fungibleTokenWithCustomFee).between(nonTreasurySender, RECEIVER))
                        .blankMemo()
                        .fee(ONE_HBAR)
                        .payingWith(nonTreasurySender)
                        .via(htsXferTxnWithCustomFee),
                cryptoTransfer(movingUnique(nonFungibleTokenWithCustomFee, 1).between(nonTreasurySender, RECEIVER))
                        .blankMemo()
                        .fee(ONE_HBAR)
                        .payingWith(nonTreasurySender)
                        .via(nftXferTxnWithCustomFee),
                validateChargedUsdWithin(hbarXferTxn, expectedHbarXferPriceUsd, 0.01),
                validateChargedUsdWithin(htsXferTxn, expectedHtsXferPriceUsd, 0.01),
                validateChargedUsdWithin(nftXferTxn, expectedNftXferPriceUsd, 0.01),
                validateChargedUsdWithin(htsXferTxnWithCustomFee, expectedHtsXferWithCustomFeePriceUsd, 0.1),
                validateChargedUsdWithin(nftXferTxnWithCustomFee, expectedNftXferWithCustomFeePriceUsd, 0.3));
    }

    @HapiTest
    final Stream<DynamicTest> okToSetInvalidPaymentHeaderForCostAnswer() {
        return defaultHapiSpec("OkToSetInvalidPaymentHeaderForCostAnswer")
                .given(cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
                        .via("misc"))
                .when()
                .then(
                        getTxnRecord("misc").useEmptyTxnAsCostPayment(),
                        getTxnRecord("misc").omittingAnyPaymentForCostAnswer());
    }

    @SuppressWarnings("java:S5960")
    @HapiTest
    final Stream<DynamicTest> tokenTransferFeesScaleAsExpected() {
        return hapiTest(
                cryptoCreate("a"),
                cryptoCreate("b"),
                cryptoCreate("c").balance(0L),
                cryptoCreate("d").balance(0L),
                cryptoCreate("e").balance(0L),
                cryptoCreate("f").balance(0L),
                tokenCreate("A").treasury("a"),
                tokenCreate("B").treasury("b"),
                tokenCreate("C").treasury("c"),
                tokenAssociate("b", "A", "C"),
                tokenAssociate("c", "A", "B"),
                tokenAssociate("d", "A", "B", "C"),
                tokenAssociate("e", "A", "B", "C"),
                tokenAssociate("f", "A", "B", "C"),
                cryptoTransfer(tinyBarsFromTo("a", "b", 1))
                        .via("pureCrypto")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                cryptoTransfer(moving(1, "A").between("a", "b"))
                        .via("oneTokenTwoAccounts")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                cryptoTransfer(moving(2, "A").distributing("a", "b", "c"))
                        .via("oneTokenThreeAccounts")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                cryptoTransfer(moving(3, "A").distributing("a", "b", "c", "d"))
                        .via("oneTokenFourAccounts")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                cryptoTransfer(moving(4, "A").distributing("a", "b", "c", "d", "e"))
                        .via("oneTokenFiveAccounts")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                cryptoTransfer(moving(5, "A").distributing("a", "b", "c", "d", "e", "f"))
                        .via("oneTokenSixAccounts")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                cryptoTransfer(moving(1, "A").between("a", "c"), moving(1, "B").between("b", "d"))
                        .via("twoTokensFourAccounts")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                cryptoTransfer(moving(1, "A").between("a", "c"), moving(2, "B").distributing("b", "d", "e"))
                        .via("twoTokensFiveAccounts")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                cryptoTransfer(moving(1, "A").between("a", "c"), moving(3, "B").distributing("b", "d", "e", "f"))
                        .via("twoTokensSixAccounts")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                cryptoTransfer(
                                moving(1, "A").between("a", "d"),
                                moving(1, "B").between("b", "e"),
                                moving(1, "C").between("c", "f"))
                        .via("threeTokensSixAccounts")
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith("a"),
                withOpContext((spec, opLog) -> {
                    var ref = getTxnRecord("pureCrypto");
                    var t1a2 = getTxnRecord("oneTokenTwoAccounts");
                    var t1a3 = getTxnRecord("oneTokenThreeAccounts");
                    var t1a4 = getTxnRecord("oneTokenFourAccounts");
                    var t1a5 = getTxnRecord("oneTokenFiveAccounts");
                    var t1a6 = getTxnRecord("oneTokenSixAccounts");
                    var t2a4 = getTxnRecord("twoTokensFourAccounts");
                    var t2a5 = getTxnRecord("twoTokensFiveAccounts");
                    var t2a6 = getTxnRecord("twoTokensSixAccounts");
                    var t3a6 = getTxnRecord("threeTokensSixAccounts");
                    allRunFor(spec, ref, t1a2, t1a3, t1a4, t1a5, t1a6, t2a4, t2a5, t2a6, t3a6);

                    var refFee = ref.getResponseRecord().getTransactionFee();
                    var t1a2Fee = t1a2.getResponseRecord().getTransactionFee();
                    var t1a3Fee = t1a3.getResponseRecord().getTransactionFee();
                    var t1a4Fee = t1a4.getResponseRecord().getTransactionFee();
                    var t1a5Fee = t1a5.getResponseRecord().getTransactionFee();
                    var t1a6Fee = t1a6.getResponseRecord().getTransactionFee();
                    var t2a4Fee = t2a4.getResponseRecord().getTransactionFee();
                    var t2a5Fee = t2a5.getResponseRecord().getTransactionFee();
                    var t2a6Fee = t2a6.getResponseRecord().getTransactionFee();
                    var t3a6Fee = t3a6.getResponseRecord().getTransactionFee();

                    var rates = spec.ratesProvider();
                    opLog.info(
                            TOKENS_INVOLVED_LOG_MESSAGE,
                            refFee,
                            sdec(rates.toUsdWithActiveRates(refFee), 4),
                            t1a2Fee,
                            sdec(rates.toUsdWithActiveRates(t1a2Fee), 4),
                            sdec((1.0 * t1a2Fee / refFee), 1),
                            t1a3Fee,
                            sdec(rates.toUsdWithActiveRates(t1a3Fee), 4),
                            sdec((1.0 * t1a3Fee / refFee), 1),
                            t1a4Fee,
                            sdec(rates.toUsdWithActiveRates(t1a4Fee), 4),
                            sdec((1.0 * t1a4Fee / refFee), 1),
                            t1a5Fee,
                            sdec(rates.toUsdWithActiveRates(t1a5Fee), 4),
                            sdec((1.0 * t1a5Fee / refFee), 1),
                            t1a6Fee,
                            sdec(rates.toUsdWithActiveRates(t1a6Fee), 4),
                            sdec((1.0 * t1a6Fee / refFee), 1),
                            t2a4Fee,
                            sdec(rates.toUsdWithActiveRates(t2a4Fee), 4),
                            sdec((1.0 * t2a4Fee / refFee), 1),
                            t2a5Fee,
                            sdec(rates.toUsdWithActiveRates(t2a5Fee), 4),
                            sdec((1.0 * t2a5Fee / refFee), 1),
                            t2a6Fee,
                            sdec(rates.toUsdWithActiveRates(t2a6Fee), 4),
                            sdec((1.0 * t2a6Fee / refFee), 1),
                            t3a6Fee,
                            sdec(rates.toUsdWithActiveRates(t3a6Fee), 4),
                            sdec((1.0 * t3a6Fee / refFee), 1));

                    double pureHbarUsd = rates.toUsdWithActiveRates(refFee);
                    double pureOneTokenTwoAccountsUsd = rates.toUsdWithActiveRates(t1a2Fee);
                    double pureTwoTokensFourAccountsUsd = rates.toUsdWithActiveRates(t2a4Fee);
                    double pureThreeTokensSixAccountsUsd = rates.toUsdWithActiveRates(t3a6Fee);
                    assertEquals(10.0, pureOneTokenTwoAccountsUsd / pureHbarUsd, 1.0);
                    assertEquals(20.0, pureTwoTokensFourAccountsUsd / pureHbarUsd, 2.0);
                    assertEquals(30.0, pureThreeTokensSixAccountsUsd / pureHbarUsd, 3.0);
                }));
    }

    public static String sdec(double d, int numDecimals) {
        var fmt = "%" + String.format(".0%df", numDecimals);
        return String.format(fmt, d);
    }

    @HapiTest
    final Stream<DynamicTest> transferToNonAccountEntitiesReturnsInvalidAccountId() {
        AtomicReference<String> invalidAccountId = new AtomicReference<>();

        return hapiTest(
                tokenCreate(TOKEN),
                createTopic("something"),
                withOpContext((spec, opLog) -> {
                    var topicId = spec.registry().getTopicID("something");
                    invalidAccountId.set(asTopicString(topicId));
                }),
                sourcing(() -> cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, invalidAccountId.get(), 1L))
                        .signedBy(DEFAULT_PAYER)
                        .hasKnownStatus(INVALID_ACCOUNT_ID)),
                sourcing(() -> cryptoTransfer(moving(1, TOKEN).between(DEFAULT_PAYER, invalidAccountId.get()))
                        .signedBy(DEFAULT_PAYER)
                        .hasKnownStatus(INVALID_ACCOUNT_ID)));
    }

    @HapiTest
    final Stream<DynamicTest> complexKeyAcctPaysForOwnTransfer() {
        SigControl enoughUniqueSigs = SigControl.threshSigs(
                2,
                SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
                SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
        String node = HapiSpecSetup.getDefaultInstance().defaultNodeName();

        return hapiTest(
                newKeyNamed("complexKey").shape(enoughUniqueSigs),
                cryptoCreate(PAYER).key("complexKey").balance(1_000_000_000L),
                cryptoTransfer(tinyBarsFromTo(PAYER, node, 1_000_000L))
                        .payingWith(PAYER)
                        .numPayerSigs(14)
                        .fee(ONE_HUNDRED_HBARS));
    }

    @HapiTest
    final Stream<DynamicTest> twoComplexKeysRequired() {
        SigControl payerShape = threshOf(2, threshOf(1, 7), threshOf(3, 7));
        SigControl receiverShape = SigControl.threshSigs(3, threshOf(2, 2), threshOf(3, 5), ON);

        SigControl payerSigs = SigControl.threshSigs(
                2,
                SigControl.threshSigs(1, ON, OFF, OFF, OFF, OFF, OFF, OFF),
                SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
        SigControl receiverSigs = SigControl.threshSigs(
                3, SigControl.threshSigs(2, ON, ON), SigControl.threshSigs(3, OFF, OFF, ON, ON, ON), ON);

        return hapiTest(
                newKeyNamed("payerKey").shape(payerShape),
                newKeyNamed("receiverKey").shape(receiverShape),
                cryptoCreate(PAYER).key("payerKey").balance(100_000_000_000L),
                cryptoCreate(RECEIVER)
                        .receiverSigRequired(true)
                        .key("receiverKey")
                        .payingWith(PAYER),
                cryptoTransfer(tinyBarsFromTo(GENESIS, RECEIVER, 1_000L))
                        .payingWith(PAYER)
                        .sigControl(forKey(PAYER, payerSigs), forKey(RECEIVER, receiverSigs))
                        .hasKnownStatus(SUCCESS)
                        .fee(ONE_HUNDRED_HBARS));
    }

    @HapiTest
    final Stream<DynamicTest> specialAccountsBalanceCheck() {
        return defaultHapiSpec("SpecialAccountsBalanceCheck")
                .given()
                .when()
                .then(IntStream.concat(IntStream.range(1, 101), IntStream.range(900, 1001))
                        .mapToObj(i -> getAccountBalance("0.0." + i).logged())
                        .toArray(HapiSpecOperation[]::new));
    }

    @HapiTest
    final Stream<DynamicTest> transferWithMissingAccountGetsInvalidAccountId() {
        return hapiTest(
                cryptoCreate(PAYEE_SIG_REQ).receiverSigRequired(true),
                cryptoTransfer(tinyBarsFromTo("1.2.3", PAYEE_SIG_REQ, 1_000L))
                        .signedBy(DEFAULT_PAYER, PAYEE_SIG_REQ)
                        .hasKnownStatus(INVALID_ACCOUNT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> vanillaTransferSucceeds() {
        long initialBalance = HapiSpecSetup.getDefaultInstance().defaultBalance();

        return hapiTest(
                cryptoCreate("somebody")
                        .maxAutomaticTokenAssociations(5001)
                        .hasKnownStatus(INVALID_MAX_AUTO_ASSOCIATIONS),
                inParallel(
                        cryptoCreate(PAYER),
                        cryptoCreate(PAYEE_SIG_REQ).receiverSigRequired(true),
                        cryptoCreate(PAYEE_NO_SIG_REQ)),
                cryptoTransfer(
                                tinyBarsFromTo(PAYER, PAYEE_SIG_REQ, 1_000L),
                                tinyBarsFromTo(PAYER, PAYEE_NO_SIG_REQ, 2_000L))
                        .via("transferTxn"),
                getTxnRecord("transferTxn"),
                withTargetLedgerId(ledgerId -> getAccountInfo(PAYER)
                        .logged()
                        .hasEncodedLedgerId(ledgerId)
                        .has(accountWith().balance(initialBalance - 3_000L))),
                getAccountInfo(PAYEE_SIG_REQ).has(accountWith().balance(initialBalance + 1_000L)),
                getAccountDetails(PAYEE_NO_SIG_REQ)
                        .payingWith(GENESIS)
                        .has(AccountDetailsAsserts.accountDetailsWith()
                                .balance(initialBalance + 2_000L)
                                .noAllowances()));
    }

    @HapiTest
    final Stream<DynamicTest> hapiTransferFromForNFTWithCustomFeesWithAllowance() {
        final var NFT_TOKEN_WITH_FIXED_HBAR_FEE = "nftTokenWithFixedHbarFee";
        final var NFT_TOKEN_WITH_FIXED_TOKEN_FEE = "nftTokenWithFixedTokenFee";
        final var NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK = "nftTokenWithRoyaltyFeeWithHbarFallback";
        final var NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK = "nftTokenWithRoyaltyFeeWithTokenFallback";
        final var FUNGIBLE_TOKEN_FEE = "fungibleTokenFee";
        final var RECEIVER_SIGNATURE = "receiverSignature";
        final var SPENDER_SIGNATURE = "spenderSignature";
        return hapiTest(
                newKeyNamed(MULTI_KEY),
                newKeyNamed(RECEIVER_SIGNATURE),
                newKeyNamed(SPENDER_SIGNATURE),
                cryptoCreate(TREASURY),
                cryptoCreate(OWNER)
                        .balance(ONE_HUNDRED_HBARS)
                        .maxAutomaticTokenAssociations(5)
                        .key(MULTI_KEY),
                cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS).key(SPENDER_SIGNATURE),
                tokenCreate(NFT_TOKEN_WITH_FIXED_HBAR_FEE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(OWNER)
                        .initialSupply(0L)
                        .supplyKey(MULTI_KEY)
                        .adminKey(MULTI_KEY)
                        .withCustom(fixedHbarFee(1, OWNER)),
                tokenCreate(FUNGIBLE_TOKEN_FEE)
                        .tokenType(FUNGIBLE_COMMON)
                        .treasury(TREASURY)
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
                        .withCustom(royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), OWNER)),
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
                tokenAssociate(
                        SPENDER,
                        List.of(
                                NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                mintToken(
                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                        List.of(
                                ByteStringUtils.wrapUnsafely("meta1".getBytes()),
                                ByteStringUtils.wrapUnsafely("meta2".getBytes()))),
                mintToken(
                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                        List.of(
                                ByteStringUtils.wrapUnsafely("meta3".getBytes()),
                                ByteStringUtils.wrapUnsafely("meta4".getBytes()))),
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
                cryptoTransfer(movingUnique(NFT_TOKEN_WITH_FIXED_HBAR_FEE, 1L).between(OWNER, SENDER)),
                cryptoTransfer(movingUnique(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, 1L).between(OWNER, SENDER)),
                cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, 1L)
                        .between(OWNER, SENDER)),
                cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, 1L)
                        .between(OWNER, SENDER)),
                cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TREASURY, SENDER)),
                cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TREASURY, RECEIVER)),
                cryptoApproveAllowance()
                        .payingWith(DEFAULT_PAYER)
                        .addNftAllowance(SENDER, NFT_TOKEN_WITH_FIXED_HBAR_FEE, SPENDER, true, List.of(1L))
                        .addNftAllowance(SENDER, NFT_TOKEN_WITH_FIXED_TOKEN_FEE, SPENDER, true, List.of(1L))
                        .addNftAllowance(
                                SENDER, NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, SPENDER, true, List.of(1L))
                        .addNftAllowance(
                                SENDER, NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, SPENDER, true, List.of(1L))
                        .via("approveTxn")
                        .signedBy(DEFAULT_PAYER, SENDER),
                cryptoTransfer(movingUniqueWithAllowance(NFT_TOKEN_WITH_FIXED_HBAR_FEE, 1L)
                                .between(SENDER, RECEIVER))
                        .payingWith(SPENDER)
                        .fee(ONE_HUNDRED_HBARS),
                cryptoTransfer(movingUniqueWithAllowance(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, 1L)
                                .between(SENDER, RECEIVER))
                        .payingWith(SPENDER)
                        .fee(ONE_HUNDRED_HBARS),
                cryptoTransfer(movingUniqueWithAllowance(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, 1L)
                                .between(SENDER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(RECEIVER_SIGNATURE, SPENDER_SIGNATURE)
                        .fee(ONE_HUNDRED_HBARS),
                cryptoTransfer(movingUniqueWithAllowance(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, 1L)
                                .between(SENDER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(RECEIVER_SIGNATURE, SPENDER_SIGNATURE)
                        .fee(ONE_HUNDRED_HBARS));
    }

    @HapiTest
    final Stream<DynamicTest> hapiTransferFromForFungibleTokenWithCustomFeesWithAllowance() {
        final var FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE = "fungibleTokenWithFixedHbarFee";
        final var FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE = "fungibleTokenWithFixedTokenFee";
        final var FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE = "fungibleTokenWithFractionalTokenFee";
        final var FUNGIBLE_TOKEN_FEE = "fungibleTokenFee";
        final var RECEIVER_SIGNATURE = "receiverSignature";
        final var SPENDER_SIGNATURE = "spenderSignature";
        return hapiTest(
                newKeyNamed(RECEIVER_SIGNATURE),
                newKeyNamed(SPENDER_SIGNATURE),
                cryptoCreate(TREASURY),
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS).key(SPENDER_SIGNATURE),
                tokenCreate(FUNGIBLE_TOKEN_FEE)
                        .tokenType(FUNGIBLE_COMMON)
                        .treasury(TREASURY)
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
                tokenAssociate(
                        SPENDER,
                        List.of(
                                FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE,
                                FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE,
                                FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE)),
                cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TREASURY, SENDER)),
                cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE).between(OWNER, SENDER)),
                cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE).between(OWNER, SENDER)),
                cryptoTransfer(moving(2L, FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE).between(OWNER, SENDER)),
                cryptoApproveAllowance()
                        .payingWith(DEFAULT_PAYER)
                        .addTokenAllowance(SENDER, FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE, SPENDER, 1L)
                        .addTokenAllowance(SENDER, FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE, SPENDER, 1L)
                        .addTokenAllowance(SENDER, FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE, SPENDER, 2L)
                        .via("approveTxn")
                        .signedBy(DEFAULT_PAYER, SENDER),
                cryptoTransfer(movingWithAllowance(1L, FUNGIBLE_TOKEN_WITH_FIXED_HBAR_FEE)
                                .between(SENDER, RECEIVER))
                        .payingWith(SPENDER)
                        .fee(ONE_HUNDRED_HBARS),
                cryptoTransfer(movingWithAllowance(1L, FUNGIBLE_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .between(SENDER, RECEIVER))
                        .payingWith(SPENDER)
                        .fee(ONE_HUNDRED_HBARS),
                cryptoTransfer(movingWithAllowance(2L, FUNGIBLE_TOKEN_WITH_FRACTIONAL_FEE)
                                .between(SENDER, RECEIVER))
                        .payingWith(SPENDER)
                        .signedBy(RECEIVER_SIGNATURE, SPENDER_SIGNATURE)
                        .fee(ONE_HUNDRED_HBARS));
    }

    @HapiTest
    final Stream<DynamicTest> testTransferToSystemAccounts() {
        final var contract = "CryptoTransfer";
        final var systemAccounts = List.of(359L, 360L, 361L);
        final var opsArray = new HapiSpecOperation[systemAccounts.size() * 3];

        for (int i = 0; i < systemAccounts.size(); i++) {
            opsArray[i] = contractCall(contract, "sendViaTransfer", mirrorAddrWith(systemAccounts.get(i)))
                    .payingWith(SENDER)
                    .sending(ONE_HBAR * 10)
                    .gas(100000)
                    .hasKnownStatus(INVALID_CONTRACT_ID);

            opsArray[systemAccounts.size() + i] = contractCall(
                            contract, "sendViaSend", mirrorAddrWith(systemAccounts.get(i)))
                    .payingWith(SENDER)
                    .sending(ONE_HBAR * 10)
                    .gas(100000)
                    .hasKnownStatus(INVALID_CONTRACT_ID);

            opsArray[systemAccounts.size() * 2 + i] = contractCall(
                            contract, "sendViaCall", mirrorAddrWith(systemAccounts.get(i)))
                    .payingWith(SENDER)
                    .sending(ONE_HBAR * 10)
                    .gas(100000)
                    .hasKnownStatus(INVALID_CONTRACT_ID);
        }

        return hapiTest(flattened(
                cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                uploadInitCode(contract),
                contractCreate(contract),
                opsArray));
    }

    @HapiTest
    final Stream<DynamicTest> testTransferToExistingSystemAccounts() {
        final var contract = "CryptoTransfer";
        final HapiSpecOperation[] opsArray = new HapiSpecOperation[existingSystemAccounts.size() * 3];

        for (int i = 0; i < existingSystemAccounts.size(); i++) {
            opsArray[i] = contractCall(contract, "sendViaTransfer", mirrorAddrWith(existingSystemAccounts.get(i)))
                    .payingWith(SENDER)
                    .sending(ONE_HBAR * 10)
                    .gas(100000)
                    .hasKnownStatus(SUCCESS);

            opsArray[existingSystemAccounts.size() + i] = contractCall(
                            contract, "sendViaSend", mirrorAddrWith(existingSystemAccounts.get(i)))
                    .payingWith(SENDER)
                    .sending(ONE_HBAR * 10)
                    .gas(100000)
                    .hasKnownStatus(SUCCESS);

            opsArray[existingSystemAccounts.size() * 2 + i] = contractCall(
                            contract, "sendViaCall", mirrorAddrWith(existingSystemAccounts.get(i)))
                    .payingWith(SENDER)
                    .sending(ONE_HBAR * 10)
                    .gas(100000)
                    .hasKnownStatus(SUCCESS);
        }

        return hapiTest(flattened(
                cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                uploadInitCode(contract),
                contractCreate(contract),
                opsArray));
    }

    @HapiTest
    final Stream<DynamicTest> testTransferToNonExistingSystemAccounts() {
        final var contract = "CryptoTransfer";
        final HapiSpecOperation[] opsArray = new HapiSpecOperation[nonExistingSystemAccounts.size() * 3];

        for (int i = 0; i < nonExistingSystemAccounts.size(); i++) {
            opsArray[i] = contractCall(contract, "sendViaTransfer", mirrorAddrWith(nonExistingSystemAccounts.get(i)))
                    .payingWith("sender")
                    .sending(ONE_HBAR * 10)
                    .via("sendViaTransfer" + i)
                    .gas(100000)
                    .hasKnownStatus(INVALID_CONTRACT_ID);

            opsArray[nonExistingSystemAccounts.size() + i] = contractCall(
                            contract, "sendViaSend", mirrorAddrWith(nonExistingSystemAccounts.get(i)))
                    .payingWith("sender")
                    .sending(ONE_HBAR * 10)
                    .via("sendViaSend" + i)
                    .gas(100000)
                    .hasKnownStatus(INVALID_CONTRACT_ID);

            opsArray[nonExistingSystemAccounts.size() * 2 + i] = contractCall(
                            contract, "sendViaCall", mirrorAddrWith(nonExistingSystemAccounts.get(i)))
                    .payingWith("sender")
                    .sending(ONE_HBAR * 10)
                    .via("sendViaCall" + i)
                    .gas(100000)
                    .hasKnownStatus(INVALID_CONTRACT_ID);
        }
        return hapiTest(flattened(
                cryptoCreate("sender").balance(ONE_HUNDRED_HBARS),
                uploadInitCode(contract),
                contractCreate(contract),
                opsArray));
    }

    @HapiTest
    final Stream<DynamicTest> testTransferToSystemAccountsAndCheckSenderBalance() {
        final var transferContract = "CryptoTransfer";
        final var balanceContract = "BalanceChecker46Version";
        final var senderAccount = "detachedSenderAccount";
        return hapiTest(
                cryptoCreate(senderAccount).balance(ONE_HUNDRED_HBARS),
                uploadInitCode(transferContract),
                contractCreate(transferContract).balance(ONE_HBAR),
                uploadInitCode(balanceContract),
                contractCreate(balanceContract),
                contractCall(
                                transferContract,
                                "sendViaTransferWithAmount",
                                mirrorAddrWith(359L),
                                BigInteger.valueOf(15L))
                        .payingWith(senderAccount)
                        .hasKnownStatus(INVALID_CONTRACT_ID),
                getAccountBalance(transferContract, true).hasTinyBars(ONE_HBAR));
    }

    @HapiTest
    final Stream<DynamicTest> transferInvalidTokenIdWithDecimals() {
        return hapiTest(
                cryptoCreate(TREASURY),
                withOpContext((spec, opLog) -> {
                    final var acctCreate = cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS);
                    allRunFor(spec, acctCreate);
                    // Here we take an account ID and store it as a token ID in the registry, so that when the "token
                    // number" is submitted by the test client, it will recreate the bug scenario:
                    final var bogusTokenId = TokenID.newBuilder().setTokenNum(acctCreate.numOfCreatedAccount());
                    spec.registry().saveTokenId("nonexistent", bogusTokenId.build());
                }),
                sourcing(() -> cryptoTransfer(
                                movingWithDecimals(1L, "nonexistent", 2).betweenWithDecimals(PAYER, TREASURY))
                        .hasKnownStatus(INVALID_TOKEN_ID)));
    }

    @HapiTest
    final Stream<DynamicTest> balancesChangeOnTransfer() {
        return defaultHapiSpec("BalancesChangeOnTransfer")
                .given(
                        cryptoCreate("sponsor"),
                        cryptoCreate("beneficiary"),
                        balanceSnapshot("sponsorBefore", "sponsor"),
                        balanceSnapshot("beneficiaryBefore", "beneficiary"))
                .when(cryptoTransfer(tinyBarsFromTo("sponsor", "beneficiary", 1L))
                        .payingWith(GENESIS)
                        .memo("Hello World!"))
                .then(
                        getAccountBalance("sponsor").hasTinyBars(changeFromSnapshot("sponsorBefore", -1L)),
                        getAccountBalance("beneficiary").hasTinyBars(changeFromSnapshot("beneficiaryBefore", +1L)));
    }

    @HapiTest
    final Stream<DynamicTest> netAdjustmentsMustBeZero() {
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<AccountID> counterId = new AtomicReference<>();
        final AtomicReference<AccountID> otherAccountId = new AtomicReference<>();

        return defaultHapiSpec("netAdjustmentsMustBeZero")
                .given(
                        cryptoCreate(PARTY).balance(0L),
                        cryptoCreate(COUNTERPARTY).balance(0L),
                        cryptoCreate(OTHER_ACCOUNT).balance(0L),
                        withOpContext((spec, opLog) -> {
                            final var registry = spec.registry();
                            partyId.set(registry.getAccountID(PARTY));
                            counterId.set(registry.getAccountID(COUNTERPARTY));
                            otherAccountId.set(registry.getAccountID(OTHER_ACCOUNT));
                        }),
                        tokenCreate("ft").initialSupply(Long.MAX_VALUE))
                .when(
                        // Pure checks detect overflow in hbar adjustments
                        cryptoTransfer((spec, b) -> b.setTransfers(TransferList.newBuilder()
                                        .addAccountAmounts(aaWith(partyId.get(), +Long.MAX_VALUE))
                                        .addAccountAmounts(aaWith(otherAccountId.get(), +Long.MAX_VALUE))
                                        .addAccountAmounts(aaWith(counterId.get(), +2))))
                                .signedBy(DEFAULT_PAYER)
                                .hasPrecheck(INVALID_ACCOUNT_AMOUNTS))
                .then(
                        // Pure checks detect overflow in fungible token adjustments
                        cryptoTransfer((spec, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                        .setToken(spec.registry().getTokenID("ft"))
                                        .addAllTransfers(List.of(
                                                aaWith(partyId.get(), +Long.MAX_VALUE),
                                                aaWith(otherAccountId.get(), +Long.MAX_VALUE),
                                                aaWith(counterId.get(), +2)))))
                                .signedBy(DEFAULT_PAYER)
                                .hasPrecheck(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN));
    }

    @HapiTest
    final Stream<DynamicTest> customFeesCannotCauseOverflow() {
        final var secondFeeCollector = "secondFeeCollector";
        return defaultHapiSpec("fixedFeesCannotCauseOverflow")
                .given(
                        cryptoCreate(PARTY).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(COUNTERPARTY)
                                .maxAutomaticTokenAssociations(1)
                                .balance(0L),
                        cryptoCreate(FEE_COLLECTOR).balance(0L),
                        cryptoCreate(secondFeeCollector).balance(0L),
                        tokenCreate("ft")
                                .treasury(TOKEN_TREASURY)
                                .withCustom(fixedHbarFee(Long.MAX_VALUE, FEE_COLLECTOR))
                                .withCustom(fixedHbarFee(Long.MAX_VALUE, secondFeeCollector))
                                .initialSupply(Long.MAX_VALUE),
                        tokenAssociate(PARTY, "ft"),
                        cryptoTransfer(moving(2, "ft").between(TOKEN_TREASURY, PARTY)))
                .when()
                .then(cryptoTransfer(moving(1L, "ft").between(PARTY, COUNTERPARTY))
                        .hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE));
    }

    @HapiTest
    final Stream<DynamicTest> createHollowAccountWithNftTransferAndCompleteIt() {
        final var tokenA = "tokenA";
        final var hollowAccountKey = "hollowAccountKey";
        final var transferTokenAAndBToHollowAccountTxn = "transferTokenAToHollowAccountTxn";
        final AtomicReference<TokenID> tokenIdA = new AtomicReference<>();
        final AtomicReference<ByteString> treasuryAlias = new AtomicReference<>();
        final AtomicReference<ByteString> hollowAccountAlias = new AtomicReference<>();

        return defaultHapiSpec("createHollowAccountWithNftTransferAndCompleteIt")
                .given(
                        newKeyNamed(hollowAccountKey).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TREASURY).balance(10_000 * ONE_MILLION_HBARS),
                        tokenCreate(tokenA)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .treasury(TREASURY),
                        // Mint NFTs
                        mintToken(tokenA, List.of(ByteString.copyFromUtf8("metadata1"))),
                        mintToken(tokenA, List.of(ByteString.copyFromUtf8("metadata2"))),
                        withOpContext((spec, opLog) -> {
                            final var registry = spec.registry();
                            final var treasuryAccountId = registry.getAccountID(TREASURY);
                            treasuryAlias.set(ByteString.copyFrom(asSolidityAddress(treasuryAccountId)));

                            // Save the alias for the hollow account
                            final var ecdsaKey = spec.registry()
                                    .getKey(hollowAccountKey)
                                    .getECDSASecp256K1()
                                    .toByteArray();
                            final var evmAddressBytes = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                            hollowAccountAlias.set(evmAddressBytes);
                            tokenIdA.set(registry.getTokenID(A_TOKEN));
                        }))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // Create hollow account
                        cryptoTransfer((s, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                        .setToken(tokenIdA.get())
                                        .addNftTransfers(ocWith(
                                                accountId(treasuryAlias.get()),
                                                accountId(hollowAccountAlias.get()),
                                                1L))))
                                .payingWith(TREASURY)
                                .signedBy(TREASURY)
                                .via(transferTokenAAndBToHollowAccountTxn),
                        // Verify hollow account creation
                        getAliasedAccountInfo(hollowAccountKey)
                                .hasToken(relationshipWith(tokenA))
                                .has(accountWith().hasEmptyKey())
                                .exposingIdTo(id -> spec.registry().saveAccountId(hollowAccountKey, id)),
                        // Transfer some hbars to the hollow account so that it could pay the next transaction
                        cryptoTransfer(movingHbar(ONE_MILLION_HBARS).between(TREASURY, hollowAccountKey)),
                        // Send transfer to complete the hollow account
                        cryptoTransfer(movingUnique(tokenA, 1L).between(hollowAccountKey, TREASURY))
                                .payingWith(hollowAccountKey)
                                .signedBy(hollowAccountKey, TREASURY)
                                .sigMapPrefixes(uniqueWithFullPrefixesFor(hollowAccountKey)))))
                .then(getAliasedAccountInfo(hollowAccountKey).has(accountWith().key(hollowAccountKey)));
    }
}
