/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.google.protobuf.ByteString.EMPTY;
import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.node.app.hapi.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.ContextRequirement.FEE_SCHEDULE_OVERRIDES;
import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createWellKnownFungibleToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createWellKnownNonFungibleToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wellKnownTokenEntities;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFeeNetOfTransfers;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeNoFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.SidecarVerbs.expectContractActionSidecarFor;
import static com.hedera.services.bdd.spec.utilops.SidecarVerbs.sidecarValidation;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.reduceFeeFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.EMPTY_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.FIVE_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.RELAYER;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCreateSuite.EMPTY_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.CRYPTO_TRANSFER_RECEIVER;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_CREATE_SPONSOR;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_MEMO;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.VALID_ALIAS;
import static com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite.INITIAL_BALANCE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite.TRANSFER_TXN_2;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.ANOTHER_SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FUNGIBLE_TOKEN_MINT_TXN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.NFT_TOKEN_MINT_TXN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.NON_FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.OWNER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.SECOND_SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.THIRD_SPENDER;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.UNIQUE;
import static com.hedera.services.stream.proto.ContractActionType.CALL;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractAction;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransferList;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@OrderedInIsolation
public class LeakyCryptoTestsSuite {
    private static final Logger log = LogManager.getLogger(LeakyCryptoTestsSuite.class);

    private static final String FACTORY_MIRROR_CONTRACT = "FactoryMirror";
    public static final String AUTO_ACCOUNT = "autoAccount";
    public static final String LAZY_ACCOUNT_RECIPIENT = "lazyAccountRecipient";
    public static final String PAY_TXN = "payTxn";
    public static final String CREATE_TX = "createTX";
    private static final String ERC20_ABI = "ERC20ABI";

    @Order(16)
    @LeakyHapiTest(overrides = {"ledger.maxAutoAssociations", "ledger.autoRenewPeriod.minDuration"})
    final Stream<DynamicTest> autoAssociationPropertiesWorkAsExpected() {
        final var shortLivedAutoAssocUser = "shortLivedAutoAssocUser";
        final var longLivedAutoAssocUser = "longLivedAutoAssocUser";
        final var payerBalance = 100 * ONE_HUNDRED_HBARS;
        final var updateWithExpiredAccount = "updateWithExpiredAccount";
        final var baseFee = 0.000214;
        return hapiTest(
                overridingTwo(
                        "ledger.maxAutoAssociations", "100",
                        "ledger.autoRenewPeriod.minDuration", "1"),
                cryptoCreate(longLivedAutoAssocUser).balance(payerBalance).autoRenewSecs(THREE_MONTHS_IN_SECONDS),
                cryptoCreate(shortLivedAutoAssocUser).balance(payerBalance).autoRenewSecs(1),
                cryptoUpdate(longLivedAutoAssocUser)
                        .payingWith(longLivedAutoAssocUser)
                        .maxAutomaticAssociations(101)
                        .hasKnownStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                cryptoUpdate(shortLivedAutoAssocUser)
                        .payingWith(shortLivedAutoAssocUser)
                        .maxAutomaticAssociations(10)
                        .via(updateWithExpiredAccount),
                validateChargedUsd(updateWithExpiredAccount, baseFee));
    }

    @HapiTest
    @Order(8)
    final Stream<DynamicTest> getsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee() {
        final var civilian = "civilian";
        final var creation = "creation";
        final var gasToOffer = 128_000L;
        final var civilianStartBalance = ONE_HUNDRED_HBARS;
        final AtomicLong gasFee = new AtomicLong();
        final AtomicLong offeredGasFee = new AtomicLong();
        final AtomicLong nodeAndNetworkFee = new AtomicLong();
        final AtomicLong maxSendable = new AtomicLong();

        return hapiTest(
                cryptoCreate(civilian).balance(civilianStartBalance),
                uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT),
                contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                        .gas(gasToOffer)
                        .payingWith(civilian)
                        .balance(0L)
                        .via(creation),
                withOpContext((spec, opLog) -> {
                    final var lookup = getTxnRecord(creation).logged();
                    allRunFor(spec, lookup);
                    final var creationRecord = lookup.getResponseRecord();
                    final var gasUsed = creationRecord.getContractCreateResult().getGasUsed();
                    gasFee.set(tinybarCostOfGas(spec, ContractCreate, gasUsed));
                    offeredGasFee.set(tinybarCostOfGas(spec, ContractCreate, gasToOffer));
                    nodeAndNetworkFee.set(creationRecord.getTransactionFee() - gasFee.get());
                    log.info(
                            "Network + node fees were {}, gas fee was {} (sum to" + " {}, compare with {})",
                            nodeAndNetworkFee::get,
                            gasFee::get,
                            () -> nodeAndNetworkFee.get() + gasFee.get(),
                            creationRecord::getTransactionFee);
                    maxSendable.set(
                            civilianStartBalance - 2 * nodeAndNetworkFee.get() - gasFee.get() - offeredGasFee.get());
                    log.info("Maximum amount send-able in precheck should be {}", maxSendable::get);
                }),
                sourcing(() -> getAccountBalance(civilian)
                        .hasTinyBars(civilianStartBalance - nodeAndNetworkFee.get() - gasFee.get())),
                // Fire-and-forget a txn that will leave the civilian payer with 1 too few
                // tinybars at consensus
                cryptoTransfer(tinyBarsFromTo(civilian, FUNDING, 1))
                        .payingWith(GENESIS)
                        .deferStatusResolution(),
                sourcing(() -> contractCustomCreate(EMPTY_CONSTRUCTOR_CONTRACT, "Clone")
                        .gas(gasToOffer)
                        .payingWith(civilian)
                        .balance(maxSendable.get())
                        // because this fails depending on the previous operation reaching
                        // consensus before the current operation or after, since we have added
                        // deferStatusResolution
                        .hasPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE, INSUFFICIENT_ACCOUNT_BALANCE)
                        .hasKnownStatusFrom(INSUFFICIENT_PAYER_BALANCE, INSUFFICIENT_ACCOUNT_BALANCE)));
    }

    @Order(1)
    @LeakyHapiTest(overrides = {"ledger.autoRenewPeriod.minDuration"})
    final Stream<DynamicTest> cannotDissociateFromExpiredTokenWithNonZeroBalance() {
        final var civilian = "civilian";
        final long initialSupply = 100L;
        final long nonZeroXfer = 10L;
        final var numTokens = 10;
        final IntFunction<String> tokenNameFn = i -> "fungible" + i;
        final String[] assocOrder = new String[numTokens];
        Arrays.setAll(assocOrder, tokenNameFn);
        final String[] dissocOrder = new String[numTokens];
        Arrays.setAll(dissocOrder, i -> tokenNameFn.apply(numTokens - 1 - i));

        return hapiTest(
                overriding("ledger.autoRenewPeriod.minDuration", "1"),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(civilian).balance(0L),
                blockingOrder(IntStream.range(0, numTokens)
                        .mapToObj(i -> tokenCreate(tokenNameFn.apply(i))
                                .autoRenewAccount(DEFAULT_PAYER)
                                .autoRenewPeriod(1L)
                                .initialSupply(initialSupply)
                                .treasury(TOKEN_TREASURY))
                        .toArray(HapiSpecOperation[]::new)),
                tokenAssociate(civilian, List.of(assocOrder)),
                blockingOrder(IntStream.range(0, numTokens)
                        .mapToObj(i -> cryptoTransfer(
                                moving(nonZeroXfer, tokenNameFn.apply(i)).between(TOKEN_TREASURY, civilian)))
                        .toArray(HapiSpecOperation[]::new)),
                sleepFor(2_000L),
                tokenDissociate(civilian, dissocOrder).hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES));
    }

    @Order(2)
    @LeakyHapiTest(overrides = {"hedera.allowances.maxTransactionLimit", "hedera.allowances.maxAccountLimit"})
    final Stream<DynamicTest> cannotExceedAccountAllowanceLimit() {
        return hapiTest(
                overridingTwo(
                        "hedera.allowances.maxAccountLimit", "3",
                        "hedera.allowances.maxTransactionLimit", "5"),
                newKeyNamed(SUPPLY_KEY),
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                tokenCreate(FUNGIBLE_TOKEN)
                        .tokenType(FUNGIBLE_COMMON)
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
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addCryptoAllowance(OWNER, SPENDER, 100L)
                        .addCryptoAllowance(OWNER, SECOND_SPENDER, 100L)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                        .fee(ONE_HBAR),
                getAccountDetails(OWNER)
                        .payingWith(GENESIS)
                        .has(accountDetailsWith()
                                .cryptoAllowancesCount(2)
                                .tokenAllowancesCount(1)
                                .nftApprovedForAllAllowancesCount(0)),
                cryptoCreate(THIRD_SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addCryptoAllowance(OWNER, ANOTHER_SPENDER, 100L)
                        .fee(ONE_HBAR)
                        .hasKnownStatus(MAX_ALLOWANCES_EXCEEDED));
    }

    @Order(3)
    @LeakyHapiTest(overrides = {"hedera.allowances.maxTransactionLimit", "hedera.allowances.maxAccountLimit"})
    final Stream<DynamicTest> cannotExceedAllowancesTransactionLimit() {
        return hapiTest(
                newKeyNamed(SUPPLY_KEY),
                overridingTwo(
                        "hedera.allowances.maxTransactionLimit", "4",
                        "hedera.allowances.maxAccountLimit", "5"),
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                tokenCreate(FUNGIBLE_TOKEN)
                        .tokenType(FUNGIBLE_COMMON)
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
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addCryptoAllowance(OWNER, SPENDER, 100L)
                        .addCryptoAllowance(OWNER, ANOTHER_SPENDER, 100L)
                        .addCryptoAllowance(OWNER, SECOND_SPENDER, 100L)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                        .hasPrecheckFrom(OK, MAX_ALLOWANCES_EXCEEDED)
                        .hasKnownStatus(MAX_ALLOWANCES_EXCEEDED),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L, 1L, 1L, 1L, 1L))
                        .hasPrecheckFrom(OK, MAX_ALLOWANCES_EXCEEDED)
                        .hasKnownStatus(MAX_ALLOWANCES_EXCEEDED),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addCryptoAllowance(OWNER, SPENDER, 100L)
                        .addCryptoAllowance(OWNER, SPENDER, 200L)
                        .addCryptoAllowance(OWNER, SPENDER, 100L)
                        .addCryptoAllowance(OWNER, SPENDER, 200L)
                        .addCryptoAllowance(OWNER, SPENDER, 200L)
                        .hasPrecheckFrom(OK, MAX_ALLOWANCES_EXCEEDED)
                        .hasKnownStatus(MAX_ALLOWANCES_EXCEEDED),
                cryptoApproveAllowance()
                        .payingWith(OWNER)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                        .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                        .hasPrecheckFrom(OK, MAX_ALLOWANCES_EXCEEDED)
                        .hasKnownStatus(MAX_ALLOWANCES_EXCEEDED));
    }

    @Order(9)
    @LeakyHapiTest(overrides = {"lazyCreation.enabled"})
    final Stream<DynamicTest> hollowAccountCompletionNotAcceptedWhenFlagIsDisabled() {
        return hapiTest(
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                cryptoCreate(CRYPTO_TRANSFER_RECEIVER).balance(INITIAL_BALANCE * ONE_HBAR),
                withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);
                    final var op2 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith()
                                    .hasEmptyKey()
                                    .noAlias()
                                    .expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0, 0)
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false)
                                    .memo(LAZY_MEMO));
                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged();
                    allRunFor(spec, op, op2, hapiGetTxnRecord);

                    final AccountID newAccountID = hapiGetTxnRecord
                            .getFirstNonStakingChildRecord()
                            .getReceipt()
                            .getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);
                }),
                overriding("lazyCreation.enabled", "false"),
                withOpContext((spec, opLog) -> {
                    final var op3 = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, CRYPTO_TRANSFER_RECEIVER, ONE_HUNDRED_HBARS))
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasPrecheck(INVALID_SIGNATURE)
                            .via(TRANSFER_TXN_2);

                    allRunFor(spec, op3);
                }));
    }

    @LeakyHapiTest(requirement = FEE_SCHEDULE_OVERRIDES)
    final Stream<DynamicTest> hollowAccountCreationChargesExpectedFees() {
        final long REDUCED_NODE_FEE = 2L;
        final long REDUCED_NETWORK_FEE = 3L;
        final long REDUCED_SERVICE_FEE = 3L;
        final long REDUCED_TOTAL_FEE = REDUCED_NODE_FEE + REDUCED_NETWORK_FEE + REDUCED_SERVICE_FEE;
        final var payer = "payer";
        final var secondKey = "secondKey";
        return hapiTest(
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                newKeyNamed(secondKey).shape(SECP_256K1_SHAPE),
                cryptoCreate(payer).balance(0L),
                reduceFeeFor(
                        List.of(CryptoTransfer, CryptoUpdate, CryptoCreate),
                        REDUCED_NODE_FEE,
                        REDUCED_NETWORK_FEE,
                        REDUCED_SERVICE_FEE),
                withOpContext((spec, opLog) -> {
                    // crypto transfer fees check
                    final HapiCryptoTransfer transferToPayerAgain =
                            cryptoTransfer(tinyBarsFromTo(GENESIS, payer, ONE_HUNDRED_HBARS + 2 * REDUCED_TOTAL_FEE));
                    final var secondEvmAddress = ByteString.copyFrom(recoverAddressFromPubKey(spec.registry()
                            .getKey(secondKey)
                            .getECDSASecp256K1()
                            .toByteArray()));
                    // try to create the hollow account without having enough
                    // balance to pay for the finalization (CryptoUpdate) fee
                    final var op5 = cryptoTransfer(tinyBarsFromTo(payer, secondEvmAddress, ONE_HUNDRED_HBARS))
                            .payingWith(payer)
                            .hasKnownStatusFrom(INSUFFICIENT_PAYER_BALANCE, INSUFFICIENT_ACCOUNT_BALANCE)
                            .via(TRANSFER_TXN);
                    final var op5FeeAssertion = getTxnRecord(TRANSFER_TXN)
                            .logged()
                            .exposingTo(record -> {
                                Assertions.assertEquals(REDUCED_TOTAL_FEE, record.getTransactionFee());
                            });
                    final var notExistingAccountInfo =
                            getAliasedAccountInfo(secondKey).hasCostAnswerPrecheck(INVALID_ACCOUNT_ID);
                    // transfer the needed balance for the finalization fee to the
                    // sponsor; we need + 2 * TOTAL_FEE, not 1, since we paid for
                    // the
                    // failed crypto transfer
                    final var op6 = cryptoTransfer(tinyBarsFromTo(GENESIS, payer, 2 * REDUCED_TOTAL_FEE));
                    // now the sponsor can successfully create the hollow account
                    final var op7 = cryptoTransfer(tinyBarsFromTo(payer, secondEvmAddress, ONE_HUNDRED_HBARS))
                            .payingWith(payer)
                            .via(TRANSFER_TXN);
                    final var op7FeeAssertion = getTxnRecord(TRANSFER_TXN)
                            .logged()
                            .andAllChildRecords()
                            .exposingTo(record -> {
                                Assertions.assertEquals(REDUCED_TOTAL_FEE, record.getTransactionFee());
                            });
                    final var op8 = getAliasedAccountInfo(secondKey)
                            .has(accountWith()
                                    .hasEmptyKey()
                                    .expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0, 0)
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false)
                                    .memo(LAZY_MEMO));
                    final var op9 = getAccountBalance(payer).hasTinyBars(0);
                    allRunFor(
                            spec,
                            transferToPayerAgain,
                            op5,
                            op5FeeAssertion,
                            notExistingAccountInfo,
                            op6,
                            op7,
                            op7FeeAssertion,
                            op8,
                            op9);
                }));
    }

    @Order(14)
    @LeakyHapiTest(overrides = {"contracts.evm.version"})
    final Stream<DynamicTest> contractDeployAfterEthereumTransferLazyCreate() {
        final var RECIPIENT_KEY = LAZY_ACCOUNT_RECIPIENT;
        final var lazyCreateTxn = PAY_TXN;
        return hapiTest(
                overriding("contracts.evm.version", "v0.34"),
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                newKeyNamed(RECIPIENT_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                        .via(AUTO_ACCOUNT),
                getTxnRecord(AUTO_ACCOUNT).andAllChildRecords(),
                uploadInitCode(FACTORY_MIRROR_CONTRACT),
                withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        TxnVerbs.ethereumCryptoTransferToAlias(
                                        spec.registry().getKey(RECIPIENT_KEY).getECDSASecp256K1(), FIVE_HBARS)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0L)
                                .maxFeePerGas(0L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(2_000_000L)
                                .via(lazyCreateTxn)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord(lazyCreateTxn).andAllChildRecords().logged())),
                withOpContext((spec, opLog) -> {
                    final var contractCreateTxn = contractCreate(FACTORY_MIRROR_CONTRACT)
                            .via(CREATE_TX)
                            .balance(20);

                    final var expectedTxnRecord = getTxnRecord(CREATE_TX)
                            .hasPriority(recordWith()
                                    .contractCreateResult(
                                            ContractFnResultAsserts.resultWith().createdContractIdsCount(2)))
                            .logged();

                    allRunFor(spec, contractCreateTxn, expectedTxnRecord);
                }));
    }

    @LeakyHapiTest(overrides = {"contracts.evm.version"})
    final Stream<DynamicTest> contractCallAfterEthereumTransferLazyCreate() {
        final var RECIPIENT_KEY = LAZY_ACCOUNT_RECIPIENT;
        final var lazyCreateTxn = PAY_TXN;
        return hapiTest(
                overriding("contracts.evm.version", "v0.34"),
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                newKeyNamed(RECIPIENT_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                        .via(AUTO_ACCOUNT),
                getTxnRecord(AUTO_ACCOUNT).andAllChildRecords(),
                uploadInitCode(FACTORY_MIRROR_CONTRACT),
                contractCreate(FACTORY_MIRROR_CONTRACT).via(CREATE_TX).balance(20),
                withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        TxnVerbs.ethereumCryptoTransferToAlias(
                                        spec.registry().getKey(RECIPIENT_KEY).getECDSASecp256K1(), FIVE_HBARS)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0L)
                                .maxFeePerGas(0L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(2_000_000L)
                                .via(lazyCreateTxn)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord(lazyCreateTxn).logged())),
                withOpContext((spec, opLog) -> {
                    final var contractCallTxn = contractCall(FACTORY_MIRROR_CONTRACT, "createChild", BigInteger.TEN)
                            .via("callTX");

                    final var expectedContractCallRecord = getTxnRecord("callTX")
                            .hasPriority(recordWith()
                                    .contractCallResult(
                                            ContractFnResultAsserts.resultWith().createdContractIdsCount(1)))
                            .logged();

                    allRunFor(spec, contractCallTxn, expectedContractCallRecord);
                }));
    }

    @Order(12)
    @LeakyHapiTest(overrides = {"contracts.evm.version"})
    final Stream<DynamicTest> lazyCreateViaEthereumCryptoTransfer() {
        final var RECIPIENT_KEY = LAZY_ACCOUNT_RECIPIENT;
        final var lazyCreateTxn = PAY_TXN;
        final var failedLazyCreateTxn = "failedLazyCreateTxn";
        return hapiTest(
                sidecarValidation(),
                overriding("contracts.evm.version", "v0.34"),
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                newKeyNamed(RECIPIENT_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                        .via(AUTO_ACCOUNT),
                getTxnRecord(AUTO_ACCOUNT).andAllChildRecords(),
                withOpContext((spec, opLog) -> {
                    final var ecdsaSecp256K1 =
                            spec.registry().getKey(RECIPIENT_KEY).getECDSASecp256K1();
                    final var aliasAsByteString =
                            ByteString.copyFrom(recoverAddressFromPubKey(ecdsaSecp256K1.toByteArray()));
                    allRunFor(
                            spec,
                            // given a non-existing public address and not enough gas,
                            // should fail with INSUFFICIENT_GAS,
                            // should not create a Hollow account,
                            // should export a contract action with targeted_address = tx.to
                            TxnVerbs.ethereumCryptoTransferToAlias(ecdsaSecp256K1, FIVE_HBARS)
                                    .type(EthTxData.EthTransactionType.EIP1559)
                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                    .payingWith(RELAYER)
                                    .nonce(0)
                                    .maxFeePerGas(0L)
                                    .maxGasAllowance(FIVE_HBARS)
                                    .gasLimit(200_000L)
                                    .via(failedLazyCreateTxn)
                                    .hasKnownStatus(INSUFFICIENT_GAS),
                            assertionsHold((assertSpec, assertLog) -> {
                                final var senderAccountIdReference = new AtomicReference<AccountID>();
                                allRunFor(
                                        assertSpec,
                                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                .exposingIdTo(senderAccountIdReference::set),
                                        getAliasedAccountInfo(aliasAsByteString)
                                                .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID));
                                allRunFor(
                                        assertSpec,
                                        emptyChildRecordsCheck(failedLazyCreateTxn, INSUFFICIENT_GAS),
                                        getTxnRecord(failedLazyCreateTxn)
                                                .hasPriority(recordWith()
                                                        .status(INSUFFICIENT_GAS)
                                                        .targetedContractId(
                                                                ContractID.newBuilder()
                                                                        .getDefaultInstanceForType()))
                                                .andAllChildRecords()
                                                .logged(),
                                        expectContractActionSidecarFor(
                                                failedLazyCreateTxn,
                                                List.of(
                                                        ContractAction.newBuilder()
                                                                .setCallType(CALL)
                                                                .setCallOperationType(CallOperationType.OP_CALL)
                                                                .setCallingAccount(senderAccountIdReference.get())
                                                                .setTargetedAddress(aliasAsByteString)
                                                                .setGas(179_000L)
                                                                .setGasUsed(179_000L)
                                                                .setValue(FIVE_HBARS)
                                                                .setOutput(EMPTY)
                                                                .setError(
                                                                        ByteString.copyFromUtf8(
                                                                                INSUFFICIENT_GAS.name()))
                                                                .build())));
                            }),
                            // given a non-existing public address and enough gas,
                            // should respond with SUCCESS
                            // should create a Hollow account with alias = tx.to and balance = tx.value
                            // should export a contract action with the AccountID of the hollow account as recipient
                            TxnVerbs.ethereumCryptoTransferToAlias(ecdsaSecp256K1, FIVE_HBARS)
                                    .type(EthTxData.EthTransactionType.EIP1559)
                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                    .payingWith(RELAYER)
                                    .nonce(1)
                                    .maxFeePerGas(0L)
                                    .maxGasAllowance(FIVE_HBARS)
                                    .gasLimit(650_000L)
                                    .via(lazyCreateTxn)
                                    .hasKnownStatus(SUCCESS),
                            assertionsHold((assertSpec, assertLog) -> {
                                final var senderAccountIdReference = new AtomicReference<AccountID>();
                                final var lazyAccountIdReference = new AtomicReference<AccountID>();
                                final var contractIdReference = new AtomicReference<ContractID>();
                                allRunFor(
                                        assertSpec,
                                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                .exposingIdTo(senderAccountIdReference::set),
                                        getAliasedAccountInfo(aliasAsByteString)
                                                .has(accountWith()
                                                        .balance(FIVE_HBARS)
                                                        .key(EMPTY_KEY)
                                                        .memo(LAZY_MEMO))
                                                .exposingIdTo(accountId -> {
                                                    lazyAccountIdReference.set(accountId);
                                                    contractIdReference.set(
                                                            ContractID.newBuilder()
                                                                    .setContractNum(accountId.getAccountNum())
                                                                    .setShardNum(accountId.getShardNum())
                                                                    .setRealmNum(accountId.getRealmNum())
                                                                    .build());
                                                }));
                                allRunFor(
                                        assertSpec,
                                        childRecordsCheck(
                                                lazyCreateTxn,
                                                SUCCESS,
                                                recordWith()
                                                        .status(SUCCESS)
                                                        .memo(LAZY_MEMO)
                                                        .alias(EMPTY)),
                                        getTxnRecord(lazyCreateTxn)
                                                .hasPriority(recordWith()
                                                        .targetedContractId(contractIdReference.get())
                                                        .contractCallResult(
                                                                ContractFnResultAsserts.resultWith()
                                                                        .contract(
                                                                                asContractString(
                                                                                        contractIdReference.get()))))
                                                .andAllChildRecords()
                                                .logged(),
                                        expectContractActionSidecarFor(
                                                lazyCreateTxn,
                                                List.of(
                                                        ContractAction.newBuilder()
                                                                .setCallType(CALL)
                                                                .setCallOperationType(CallOperationType.OP_CALL)
                                                                .setCallingAccount(senderAccountIdReference.get())
                                                                .setRecipientAccount(lazyAccountIdReference.get())
                                                                .setGas(629_000L)
                                                                .setGasUsed(555_112L)
                                                                .setValue(FIVE_HBARS)
                                                                .setOutput(EMPTY)
                                                                .build())));
                            }));
                }));
    }

    @LeakyHapiTest(overrides = {"lazyCreation.enabled"})
    final Stream<DynamicTest> hollowAccountCompletionWithSimultaneousPropertiesUpdate() {
        return hapiTest(
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                cryptoCreate(CRYPTO_TRANSFER_RECEIVER).balance(INITIAL_BALANCE * ONE_HBAR),
                withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);

                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged();

                    allRunFor(spec, op, hapiGetTxnRecord);

                    final AccountID newAccountID = hapiGetTxnRecord
                            .getFirstNonStakingChildRecord()
                            .getReceipt()
                            .getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);
                }),
                withOpContext((spec, opLog) -> {
                    final var op2 = overriding("lazyCreation.enabled", "false");
                    final var op3 = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, CRYPTO_TRANSFER_RECEIVER, ONE_HUNDRED_HBARS))
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasPrecheckFrom(OK, INVALID_SIGNATURE)
                            .hasKnownStatus(INVALID_PAYER_SIGNATURE)
                            .via(TRANSFER_TXN_2);

                    allRunFor(spec, op2, op3);
                }));
    }

    @HapiTest
    @Order(17)
    final Stream<DynamicTest> autoAssociationWorksForContracts() {
        final var theContract = "CreateDonor";
        final String tokenA = "tokenA";
        final String tokenB = "tokenB";
        final String uniqueToken = UNIQUE;
        final String tokenAcreateTxn = "tokenACreate";
        final String tokenBcreateTxn = "tokenBCreate";
        final String transferToFU = "transferToFU";

        return hapiTest(
                newKeyNamed(SUPPLY_KEY),
                uploadInitCode(theContract),
                contractCreate(theContract).maxAutomaticTokenAssociations(2),
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(tokenA)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(Long.MAX_VALUE)
                        .treasury(TOKEN_TREASURY)
                        .via(tokenAcreateTxn),
                tokenCreate(tokenB)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(Long.MAX_VALUE)
                        .treasury(TOKEN_TREASURY)
                        .via(tokenBcreateTxn),
                tokenCreate(uniqueToken)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .supplyKey(SUPPLY_KEY)
                        .treasury(TOKEN_TREASURY),
                mintToken(uniqueToken, List.of(copyFromUtf8("ONE"), copyFromUtf8("TWO"))),
                getTxnRecord(tokenAcreateTxn)
                        .hasNewTokenAssociation(tokenA, TOKEN_TREASURY)
                        .logged(),
                getTxnRecord(tokenBcreateTxn)
                        .hasNewTokenAssociation(tokenB, TOKEN_TREASURY)
                        .logged(),
                cryptoTransfer(moving(1, tokenA).between(TOKEN_TREASURY, theContract))
                        .via(transferToFU)
                        .logged(),
                getTxnRecord(transferToFU)
                        .hasNewTokenAssociation(tokenA, theContract)
                        .logged(),
                getContractInfo(theContract)
                        .has(ContractInfoAsserts.contractWith()
                                .hasAlreadyUsedAutomaticAssociations(1)
                                .maxAutoAssociations(2)),
                cryptoTransfer(movingUnique(uniqueToken, 1L).between(TOKEN_TREASURY, theContract)),
                getContractInfo(theContract)
                        .has(ContractInfoAsserts.contractWith()
                                .hasAlreadyUsedAutomaticAssociations(2)
                                .maxAutoAssociations(2)),
                cryptoTransfer(moving(1, tokenB).between(TOKEN_TREASURY, theContract))
                        .hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS)
                        .via("failedTransfer"),
                getContractInfo(theContract)
                        .has(ContractInfoAsserts.contractWith()
                                .hasAlreadyUsedAutomaticAssociations(2)
                                .maxAutoAssociations(2)));
    }

    @HapiTest
    @Order(18)
    final Stream<DynamicTest> customFeesHaveExpectedAutoCreateInteractions() {
        final var nftWithRoyaltyNoFallback = "nftWithRoyaltyNoFallback";
        final var nftWithRoyaltyPlusHtsFallback = "nftWithRoyaltyPlusFallback";
        final var nftWithRoyaltyPlusHbarFallback = "nftWithRoyaltyPlusHbarFallback";
        final var ftWithNetOfTransfersFractional = "ftWithNetOfTransfersFractional";
        final var ftWithNonNetOfTransfersFractional = "ftWithNonNetOfTransfersFractional";
        final var finalReceiverKey = "finalReceiverKey";
        final var otherCollector = "otherCollector";
        final var finalTxn = "finalTxn";

        return hapiTest(
                wellKnownTokenEntities(),
                cryptoCreate(otherCollector),
                cryptoCreate(CIVILIAN).maxAutomaticTokenAssociations(42),
                inParallel(
                        createWellKnownFungibleToken(
                                ftWithNetOfTransfersFractional,
                                creation -> creation.withCustom(fractionalFeeNetOfTransfers(
                                        1L, 100L, 1L, OptionalLong.of(5L), TOKEN_TREASURY))),
                        createWellKnownFungibleToken(
                                ftWithNonNetOfTransfersFractional,
                                creation -> creation.withCustom(
                                        fractionalFee(1L, 100L, 1L, OptionalLong.of(5L), TOKEN_TREASURY))),
                        createWellKnownNonFungibleToken(
                                nftWithRoyaltyNoFallback,
                                1,
                                creation -> creation.withCustom(royaltyFeeNoFallback(1L, 100L, TOKEN_TREASURY))),
                        createWellKnownNonFungibleToken(
                                nftWithRoyaltyPlusHbarFallback,
                                1,
                                creation -> creation.withCustom(royaltyFeeWithFallback(
                                        1L, 100L, fixedHbarFeeInheritingRoyaltyCollector(ONE_HBAR), TOKEN_TREASURY)))),
                tokenAssociate(otherCollector, ftWithNonNetOfTransfersFractional),
                createWellKnownNonFungibleToken(
                        nftWithRoyaltyPlusHtsFallback,
                        1,
                        creation -> creation.withCustom(royaltyFeeWithFallback(
                                1L,
                                100L,
                                fixedHtsFeeInheritingRoyaltyCollector(666, ftWithNonNetOfTransfersFractional),
                                otherCollector))),
                autoCreateWithFungible(ftWithNetOfTransfersFractional),
                autoCreateWithFungible(ftWithNonNetOfTransfersFractional),
                autoCreateWithNonFungible(nftWithRoyaltyNoFallback, SUCCESS),
                autoCreateWithNonFungible(
                        nftWithRoyaltyPlusHbarFallback, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE),
                newKeyNamed(finalReceiverKey),
                cryptoTransfer(
                        moving(100_000, ftWithNonNetOfTransfersFractional).between(TOKEN_TREASURY, CIVILIAN),
                        movingUnique(nftWithRoyaltyPlusHtsFallback, 1L).between(TOKEN_TREASURY, CIVILIAN)),
                cryptoTransfer(
                                moving(10_000, ftWithNonNetOfTransfersFractional)
                                        .between(CIVILIAN, finalReceiverKey),
                                movingUnique(nftWithRoyaltyPlusHtsFallback, 1L).between(CIVILIAN, finalReceiverKey))
                        .hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE)
                        .via(finalTxn));
    }

    @LeakyHapiTest(overrides = {"accounts.releaseAliasAfterDeletion"})
    final Stream<DynamicTest> accountDeletionDoesNotReleaseAliasWithDisabledFF() {
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
        final AtomicReference<ByteString> counterAlias = new AtomicReference<>();
        final AtomicReference<AccountID> aliasedAccountId = new AtomicReference<>();
        final AtomicReference<String> tokenNum = new AtomicReference<>();
        final var totalSupply = 50;
        final var ercUser = "ercUser";
        final var HBAR_XFER = "hbarXfer";

        return hapiTest(
                overriding("accounts.releaseAliasAfterDeletion", "false"),
                cryptoCreate(AUTO_ACCOUNT).maxAutomaticTokenAssociations(2),
                cryptoCreate(TOKEN_TREASURY),
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    final var ecdsaKey = registry.getKey(SECP_256K1_SOURCE_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                    partyId.set(registry.getAccountID(AUTO_ACCOUNT));
                    partyAlias.set(ByteString.copyFrom(asSolidityAddress(partyId.get())));
                    counterAlias.set(evmAddressBytes);
                }),
                tokenCreate("token")
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(totalSupply)
                        .treasury(TOKEN_TREASURY)
                        .adminKey(SECP_256K1_SOURCE_KEY)
                        .supplyKey(SECP_256K1_SOURCE_KEY)
                        .exposingCreatedIdTo(tokenNum::set),
                withOpContext((spec, opLog) -> {
                    var op1 = cryptoTransfer((s, b) -> b.setTransfers(TransferList.newBuilder()
                                    .addAccountAmounts(aaWith(partyAlias.get(), -2 * ONE_HBAR))
                                    .addAccountAmounts(aaWith(counterAlias.get(), +2 * ONE_HBAR))))
                            .signedBy(DEFAULT_PAYER, AUTO_ACCOUNT)
                            .via(HBAR_XFER);

                    var op2 = getAliasedAccountInfo(counterAlias.get())
                            .logged()
                            .exposingIdTo(aliasedAccountId::set)
                            .has(accountWith()
                                    .hasEmptyKey()
                                    .noAlias()
                                    .nonce(0)
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false)
                                    .memo(LAZY_MEMO));

                    // send eth transaction signed by the ecdsa key
                    var op3 = ethereumCallWithFunctionAbi(
                                    true, "token", getABIFor(Utils.FunctionType.FUNCTION, "totalSupply", ERC20_ABI))
                            .type(EthTxData.EthTransactionType.EIP1559)
                            .signingWith(SECP_256K1_SOURCE_KEY)
                            .payingWith(GENESIS)
                            .nonce(0)
                            .gasPrice(50L)
                            .maxGasAllowance(FIVE_HBARS)
                            .maxPriorityGas(2L)
                            .gasLimit(1_000_000L)
                            .hasKnownStatus(ResponseCodeEnum.SUCCESS);

                    // assert account nonce is increased to 1
                    var op4 = getAliasedAccountInfo(counterAlias.get())
                            .logged()
                            .has(accountWith().nonce(1));

                    allRunFor(spec, op1, op2, op3, op4);

                    spec.registry().saveAccountId(ercUser, aliasedAccountId.get());
                    spec.registry().saveKey(ercUser, spec.registry().getKey(SECP_256K1_SOURCE_KEY));
                }),
                // delete the account currently holding the alias
                cryptoDelete(ercUser),
                // try to create a new account with the same alias
                withOpContext((spec, opLog) -> {
                    var op1 = cryptoTransfer((s, b) -> b.setTransfers(TransferList.newBuilder()
                                    .addAccountAmounts(aaWith(partyAlias.get(), -2 * ONE_HBAR))
                                    .addAccountAmounts(aaWith(counterAlias.get(), +2 * ONE_HBAR))))
                            .signedBy(DEFAULT_PAYER, AUTO_ACCOUNT)
                            .hasKnownStatus(ACCOUNT_DELETED);

                    allRunFor(spec, op1);
                }));
    }

    private long tinybarCostOfGas(final HapiSpec spec, final HederaFunctionality function, final long gasAmount) {
        final var gasThousandthsOfTinycentPrice = spec.fees()
                .getCurrentOpFeeData()
                .get(function)
                .get(DEFAULT)
                .getServicedata()
                .getGas();
        final var rates = spec.ratesProvider().rates();
        return (gasThousandthsOfTinycentPrice / 1000 * rates.getHbarEquiv()) / rates.getCentEquiv() * gasAmount;
    }

    private HapiSpecOperation autoCreateWithFungible(final String token) {
        final var keyName = VALID_ALIAS + "-" + token;
        final var txn = "autoCreationVia" + token;
        return blockingOrder(
                newKeyNamed(keyName),
                cryptoTransfer(moving(100_000, token).between(TOKEN_TREASURY, CIVILIAN)),
                cryptoTransfer(moving(10_000, token).between(CIVILIAN, keyName)).via(txn),
                getTxnRecord(txn).assertingKnownEffectivePayers());
    }

    private HapiSpecOperation autoCreateWithNonFungible(final String token, final ResponseCodeEnum expectedStatus) {
        final var keyName = VALID_ALIAS + "-" + token;
        final var txn = "autoCreationVia" + token;
        return blockingOrder(
                newKeyNamed(keyName),
                cryptoTransfer(movingUnique(token, 1L).between(TOKEN_TREASURY, CIVILIAN)),
                cryptoTransfer(movingUnique(token, 1L).between(CIVILIAN, keyName))
                        .via(txn)
                        .hasKnownStatus(expectedStatus),
                getTxnRecord(txn).assertingKnownEffectivePayers());
    }
}
