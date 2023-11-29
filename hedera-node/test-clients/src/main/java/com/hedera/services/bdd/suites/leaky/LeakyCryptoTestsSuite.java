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

package com.hedera.services.bdd.suites.leaky;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createWellKnownFungibleToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createWellKnownNonFungibleToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wellKnownTokenEntities;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFeeNetOfTransfers;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeNoFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOfDeferred;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.reduceFeeFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.remembering;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadDefaultFeeSchedules;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCreateSuite.EMPTY_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.CRYPTO_TRANSFER_RECEIVER;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.FALSE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_CREATE_SPONSOR;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_MEMO;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.TRUE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.VALID_ALIAS;
import static com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite.INITIAL_BALANCE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite.TRANSFER_TXN_2;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.ANOTHER_SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FUNGIBLE_TOKEN_MINT_TXN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.NFT_TOKEN_MINT_TXN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.NON_FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.OTHER_RECEIVER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.OWNER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.SCHEDULED_TXN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.SECOND_SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.THIRD_SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.TOKEN_WITH_CUSTOM_FEE;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ANOTHER_ACCOUNT;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ED_25519_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.LAZY_CREATION_ENABLED;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.SENDER_TXN;
import static com.hedera.services.bdd.suites.token.TokenPauseSpecs.DEFAULT_MIN_AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.token.TokenPauseSpecs.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION;
import static com.hedera.services.bdd.suites.token.TokenPauseSpecs.TokenIdOrderingAsserts.withOrderedTokenIds;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.UNIQUE;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.RECEIVER;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ALIAS_ALREADY_ASSIGNED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class LeakyCryptoTestsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LeakyCryptoTestsSuite.class);
    private static final String ASSOCIATIONS_LIMIT_PROPERTY = "entities.limitTokenAssociations";
    private static final String DEFAULT_ASSOCIATIONS_LIMIT =
            HapiSpecSetup.getDefaultNodeProps().get(ASSOCIATIONS_LIMIT_PROPERTY);
    private static final String FACTORY_MIRROR_CONTRACT = "FactoryMirror";
    public static final String LAZY_CREATE_PROPERTY_NAME = "lazyCreation.enabled";
    public static final String CONTRACTS_EVM_VERSION_PROP = "contracts.evm.version";
    public static final String AUTO_ACCOUNT = "autoAccount";
    public static final String LAZY_ACCOUNT_RECIPIENT = "lazyAccountRecipient";
    public static final String PAY_TXN = "payTxn";
    public static final String CREATE_TX = "createTX";
    public static final String V_0_34 = "v0.34";

    public static void main(String... args) {
        new LeakyCryptoTestsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                maxAutoAssociationSpec(),
                canDissociateFromMultipleExpiredTokens(),
                cannotExceedAccountAllowanceLimit(),
                cannotExceedAllowancesTransactionLimit(),
                createAnAccountWithEVMAddressAliasAndECKey(),
                createAnAccountWithEVMAddress(),
                scheduledCryptoApproveAllowanceWaitForExpiryTrue(),
                txnsUsingHip583FunctionalitiesAreNotAcceptedWhenFlagsAreDisabled(),
                getsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee(),
                hollowAccountCompletionNotAcceptedWhenFlagIsDisabled(),
                hollowAccountCompletionWithEthereumTransaction(),
                hollowAccountCreationChargesExpectedFees(),
                lazyCreateViaEthereumCryptoTransfer(),
                hollowAccountCompletionWithSimultaniousPropertiesUpdate(),
                contractDeployAfterEthereumTransferLazyCreate(),
                contractCallAfterEthereumTransferLazyCreate(),
                autoAssociationPropertiesWorkAsExpected(),
                autoAssociationWorksForContracts(),
                // Interactions between HIP-18 and HIP-542
                customFeesHaveExpectedAutoCreateInteractions());
    }

    private HapiSpec autoAssociationPropertiesWorkAsExpected() {
        final var minAutoRenewPeriodPropertyName = "ledger.autoRenewPeriod.minDuration";
        final var maxAssociationsPropertyName = "ledger.maxAutoAssociations";
        final var shortLivedAutoAssocUser = "shortLivedAutoAssocUser";
        final var longLivedAutoAssocUser = "longLivedAutoAssocUser";
        final var payerBalance = 100 * ONE_HUNDRED_HBARS;
        final var updateWithExpiredAccount = "updateWithExpiredAccount";
        final var autoAssocSlotPrice = 0.0018;
        final var baseFee = 0.00022;
        double plusTenSlotsFee = baseFee + 10 * autoAssocSlotPrice;
        return propertyPreservingHapiSpec("AutoAssociationPropertiesWorkAsExpected")
                .preserving(maxAssociationsPropertyName, minAutoRenewPeriodPropertyName)
                .given(
                        overridingTwo(
                                maxAssociationsPropertyName, "100",
                                minAutoRenewPeriodPropertyName, "1"),
                        cryptoCreate(longLivedAutoAssocUser)
                                .balance(payerBalance)
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS),
                        cryptoCreate(shortLivedAutoAssocUser)
                                .balance(payerBalance)
                                .autoRenewSecs(1))
                .when()
                .then(
                        cryptoUpdate(longLivedAutoAssocUser)
                                .payingWith(longLivedAutoAssocUser)
                                .maxAutomaticAssociations(101)
                                .hasKnownStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                        cryptoUpdate(shortLivedAutoAssocUser)
                                .payingWith(shortLivedAutoAssocUser)
                                .maxAutomaticAssociations(10)
                                .via(updateWithExpiredAccount),
                        validateChargedUsd(updateWithExpiredAccount, plusTenSlotsFee));
    }

    private HapiSpec getsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee() {
        final var civilian = "civilian";
        final var creation = "creation";
        final var gasToOffer = 128_000L;
        final var civilianStartBalance = ONE_HUNDRED_HBARS;
        final AtomicLong gasFee = new AtomicLong();
        final AtomicLong offeredGasFee = new AtomicLong();
        final AtomicLong nodeAndNetworkFee = new AtomicLong();
        final AtomicLong maxSendable = new AtomicLong();

        return defaultHapiSpec("GetsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee")
                .given(cryptoCreate(civilian).balance(civilianStartBalance), uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .gas(gasToOffer)
                                .payingWith(civilian)
                                .balance(0L)
                                .via(creation),
                        withOpContext((spec, opLog) -> {
                            final var lookup = getTxnRecord(creation).logged();
                            allRunFor(spec, lookup);
                            final var creationRecord = lookup.getResponseRecord();
                            final var gasUsed =
                                    creationRecord.getContractCreateResult().getGasUsed();
                            gasFee.set(tinybarCostOfGas(spec, ContractCreate, gasUsed));
                            offeredGasFee.set(tinybarCostOfGas(spec, ContractCreate, gasToOffer));
                            nodeAndNetworkFee.set(creationRecord.getTransactionFee() - gasFee.get());
                            log.info(
                                    "Network + node fees were {}, gas fee was {} (sum to" + " {}, compare with {})",
                                    nodeAndNetworkFee::get,
                                    gasFee::get,
                                    () -> nodeAndNetworkFee.get() + gasFee.get(),
                                    creationRecord::getTransactionFee);
                            maxSendable.set(civilianStartBalance
                                    - 2 * nodeAndNetworkFee.get()
                                    - gasFee.get()
                                    - offeredGasFee.get());
                            log.info("Maximum amount send-able in precheck should be {}", maxSendable::get);
                        }))
                .then(
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
                                .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)));
    }

    private HapiSpec scheduledCryptoApproveAllowanceWaitForExpiryTrue() {
        return defaultHapiSpec("ScheduledCryptoApproveAllowanceWaitForExpiryTrue")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS).via(SENDER_TXN),
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
                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b"))))
                .when(
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN, TOKEN_WITH_CUSTOM_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN, TOKEN_WITH_CUSTOM_FEE),
                        cryptoTransfer(
                                moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER),
                                moving(15, TOKEN_WITH_CUSTOM_FEE).between(TOKEN_TREASURY, OWNER),
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L).between(TOKEN_TREASURY, OWNER)),
                        scheduleCreate(
                                        SCHEDULED_TXN,
                                        cryptoApproveAllowance()
                                                .addCryptoAllowance(OWNER, SPENDER, 10 * ONE_HBAR)
                                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 1500)
                                                .addTokenAllowance(OWNER, TOKEN_WITH_CUSTOM_FEE, SPENDER, 100)
                                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(2L))
                                                .fee(ONE_HUNDRED_HBARS))
                                .waitForExpiry()
                                .withRelativeExpiry(SENDER_TXN, 8)
                                .recordingScheduledTxn())
                .then(
                        scheduleSign(SCHEDULED_TXN).alsoSigningWith(OWNER),
                        getScheduleInfo(SCHEDULED_TXN)
                                .hasScheduleId(SCHEDULED_TXN)
                                .hasWaitForExpiry()
                                .isNotExecuted()
                                .isNotDeleted()
                                .hasRelativeExpiry(SENDER_TXN, 8)
                                .hasRecordedScheduledTxn(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(0)
                                        .tokenAllowancesCount(0)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasNoSpender(),
                        sleepFor(12_000L),
                        cryptoCreate("foo").via("TRIGGERING_TXN"),
                        getScheduleInfo(SCHEDULED_TXN).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(1)
                                        .cryptoAllowancesContaining(SPENDER, 10 * ONE_HBAR)
                                        .tokenAllowancesCount(2)
                                        .tokenAllowancesContaining(FUNGIBLE_TOKEN, SPENDER, 1500L)
                                        .tokenAllowancesContaining(TOKEN_WITH_CUSTOM_FEE, SPENDER, 100L)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(SPENDER));
    }

    private HapiSpec txnsUsingHip583FunctionalitiesAreNotAcceptedWhenFlagsAreDisabled() {
        final Map<String, String> startingProps = new HashMap<>();
        return defaultHapiSpec("txnsUsingHip583FunctionalitiesAreNotAcceptedWhenFlagsAreDisabled")
                .given(
                        remembering(startingProps, LAZY_CREATION_ENABLED, CRYPTO_CREATE_WITH_ALIAS_ENABLED),
                        overridingTwo(
                                LAZY_CREATION_ENABLED, FALSE_VALUE,
                                CRYPTO_CREATE_WITH_ALIAS_ENABLED, FALSE_VALUE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                    // create with ECDSA alias and no key
                    final var op =
                            cryptoCreate(ACCOUNT).alias(ecdsaKey.toByteString()).hasPrecheck(NOT_SUPPORTED);
                    // create with EVM address alias and no key
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(tmp));
                    final var op2 = cryptoCreate(ACCOUNT).alias(evmAddress).hasPrecheck(NOT_SUPPORTED);
                    // create with ED alias and no key
                    final var ed25519Key = spec.registry().getKey(ED_25519_KEY);
                    final var op3 = cryptoCreate(ACCOUNT)
                            .alias(ed25519Key.toByteString())
                            .hasPrecheck(NOT_SUPPORTED);
                    // create with evm address alias and ECDSA key
                    final var op4 = cryptoCreate(ACCOUNT)
                            .key(SECP_256K1_SOURCE_KEY)
                            .alias(evmAddress)
                            .hasPrecheck(NOT_SUPPORTED);
                    // create with ED alias and ED key
                    final var op5 = cryptoCreate(ACCOUNT)
                            .key(ED_25519_KEY)
                            .alias(ed25519Key.toByteString())
                            .hasPrecheck(NOT_SUPPORTED);
                    // create with ECDSA alias and key
                    final var op6 = cryptoCreate(ACCOUNT)
                            .key(SECP_256K1_SOURCE_KEY)
                            .alias(ecdsaKey.toByteString())
                            .hasPrecheck(NOT_SUPPORTED);
                    // assert that an account created with ECDSA key and no alias
                    // does not automagically set alias to evm address
                    final var op7 = cryptoCreate(ACCOUNT).key(SECP_256K1_SOURCE_KEY);
                    var hapiGetAccountInfo = getAccountInfo(ACCOUNT)
                            .has(accountWith().key(SECP_256K1_SOURCE_KEY).noAlias());
                    allRunFor(spec, op, op2, op3, op4, op5, op6, op7, hapiGetAccountInfo);
                }))
                .then(overridingAllOfDeferred(() -> startingProps));
    }

    private HapiSpec maxAutoAssociationSpec() {
        final int MONOGAMOUS_NETWORK = 1;
        final int maxAutoAssociations = 100;
        final int ADVENTUROUS_NETWORK = 1_000;
        final String user1 = "user1";

        return defaultHapiSpec("MaxAutoAssociationSpec")
                .given(overridingTwo(
                        ASSOCIATIONS_LIMIT_PROPERTY, TRUE_VALUE, "tokens.maxPerAccount", "" + MONOGAMOUS_NETWORK))
                .when()
                .then(
                        cryptoCreate(user1)
                                .balance(ONE_HBAR)
                                .maxAutomaticTokenAssociations(maxAutoAssociations)
                                .hasPrecheck(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                        // Default is NOT to limit associations
                        overriding(ASSOCIATIONS_LIMIT_PROPERTY, DEFAULT_ASSOCIATIONS_LIMIT),
                        cryptoCreate(user1).balance(ONE_HBAR).maxAutomaticTokenAssociations(maxAutoAssociations),
                        getAccountInfo(user1).hasMaxAutomaticAssociations(maxAutoAssociations),
                        // Restore default
                        overriding("tokens.maxPerAccount", "" + ADVENTUROUS_NETWORK));
    }

    public HapiSpec canDissociateFromMultipleExpiredTokens() {
        final var civilian = "civilian";
        final long initialSupply = 100L;
        final long nonZeroXfer = 10L;
        final var dissociateTxn = "dissociateTxn";
        final var numTokens = 10;
        final IntFunction<String> tokenNameFn = i -> "fungible" + i;
        final String[] assocOrder = new String[numTokens];
        Arrays.setAll(assocOrder, tokenNameFn);
        final String[] dissocOrder = new String[numTokens];
        Arrays.setAll(dissocOrder, i -> tokenNameFn.apply(numTokens - 1 - i));

        return defaultHapiSpec("CanDissociateFromMultipleExpiredTokens")
                .given(
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, "1"),
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
                                .mapToObj(i -> cryptoTransfer(moving(nonZeroXfer, tokenNameFn.apply(i))
                                        .between(TOKEN_TREASURY, civilian)))
                                .toArray(HapiSpecOperation[]::new)))
                .when(sleepFor(1_000L), tokenDissociate(civilian, dissocOrder).via(dissociateTxn))
                .then(
                        getTxnRecord(dissociateTxn)
                                .hasPriority(recordWith().tokenTransfers(withOrderedTokenIds(assocOrder))),
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, DEFAULT_MIN_AUTO_RENEW_PERIOD));
    }

    private HapiSpec cannotExceedAccountAllowanceLimit() {
        return defaultHapiSpec("CannotExceedAccountAllowanceLimit")
                .given(
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "3",
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "5"),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
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
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
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
                                        .nftApprovedForAllAllowancesCount(0)))
                .then(
                        cryptoCreate(THIRD_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, ANOTHER_SPENDER, 100L)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(MAX_ALLOWANCES_EXCEEDED),
                        // reset
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "20",
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "100"));
    }

    private HapiSpec createAnAccountWithEVMAddressAliasAndECKey() {
        final Map<String, String> startingProps = new HashMap<>();
        return defaultHapiSpec("CreateAnAccountWithEVMAddressAliasAndECKey")
                .given(
                        remembering(startingProps, LAZY_CREATION_ENABLED, CRYPTO_CREATE_WITH_ALIAS_ENABLED),
                        overridingTwo(LAZY_CREATION_ENABLED, TRUE_VALUE, CRYPTO_CREATE_WITH_ALIAS_ENABLED, TRUE_VALUE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    assert addressBytes.length > 0;
                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                    final var op = cryptoCreate(ACCOUNT)
                            .key(SECP_256K1_SOURCE_KEY)
                            .alias(evmAddressBytes)
                            .balance(100 * ONE_HBAR)
                            .via("createTxn");
                    final var op2 = cryptoCreate(ACCOUNT)
                            .alias(ecdsaKey.toByteString())
                            .hasPrecheck(INVALID_ALIAS_KEY)
                            .balance(100 * ONE_HBAR);
                    final var op3 = cryptoCreate(ACCOUNT)
                            .alias(evmAddressBytes)
                            .hasPrecheck(INVALID_ALIAS_KEY)
                            .balance(100 * ONE_HBAR);
                    final var op4 = cryptoCreate(ANOTHER_ACCOUNT)
                            .key(SECP_256K1_SOURCE_KEY)
                            .balance(100 * ONE_HBAR);
                    final var op5 = cryptoCreate(ACCOUNT)
                            .key(SECP_256K1_SOURCE_KEY)
                            .alias(ByteString.copyFromUtf8("Invalid alias"))
                            .hasPrecheck(INVALID_ALIAS_KEY)
                            .balance(100 * ONE_HBAR);
                    final var op6 = cryptoCreate(ACCOUNT)
                            .key(SECP_256K1_SOURCE_KEY)
                            .alias(evmAddressBytes)
                            .balance(100 * ONE_HBAR)
                            .hasPrecheck(ALIAS_ALREADY_ASSIGNED);

                    allRunFor(spec, op, op2, op3, op4, op5, op6);
                    var hapiGetAccountInfo = getAliasedAccountInfo(evmAddressBytes)
                            .has(accountWith()
                                    .key(SECP_256K1_SOURCE_KEY)
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false));
                    var hapiGetAnotherAccountInfo = getAccountInfo(ANOTHER_ACCOUNT)
                            .has(accountWith()
                                    .key(SECP_256K1_SOURCE_KEY)
                                    .noAlias()
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false));
                    final var getTxnRecord =
                            getTxnRecord("createTxn").hasPriority(recordWith().hasNoAlias());
                    allRunFor(spec, hapiGetAccountInfo, hapiGetAnotherAccountInfo, getTxnRecord);
                }))
                .then(overridingAllOfDeferred(() -> startingProps));
    }

    private HapiSpec createAnAccountWithEVMAddress() {
        return propertyPreservingHapiSpec("CreateAnAccountWithEVMAddress")
                .preserving(LAZY_CREATION_ENABLED, CRYPTO_CREATE_WITH_ALIAS_ENABLED)
                .given(
                        overridingTwo(LAZY_CREATION_ENABLED, TRUE_VALUE, CRYPTO_CREATE_WITH_ALIAS_ENABLED, TRUE_VALUE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    assert addressBytes.length > 0;
                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                    final var op = cryptoCreate(ACCOUNT)
                            .alias(evmAddressBytes)
                            .balance(100 * ONE_HBAR)
                            .hasPrecheck(INVALID_ALIAS_KEY);
                    allRunFor(spec, op);
                }))
                .then();
    }

    private HapiSpec cannotExceedAllowancesTransactionLimit() {
        return defaultHapiSpec("CannotExceedAllowancesTransactionLimit")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "4",
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "5"),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
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
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L).between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, ANOTHER_SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SECOND_SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L, 1L, 1L, 1L, 1L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SPENDER, 200L)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SPENDER, 200L)
                                .addCryptoAllowance(OWNER, SPENDER, 200L)
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED))
                .then(
                        // reset
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "20",
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "100"));
    }

    private HapiSpec hollowAccountCompletionNotAcceptedWhenFlagIsDisabled() {
        final Map<String, String> startingProps = new HashMap<>();
        return defaultHapiSpec("HollowAccountCompletionNotAcceptedWhenFlagIsDisabled")
                .given(
                        remembering(startingProps, LAZY_CREATION_ENABLED),
                        overriding(LAZY_CREATION_ENABLED, TRUE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate(CRYPTO_TRANSFER_RECEIVER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(withOpContext((spec, opLog) -> {
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

                    final AccountID newAccountID =
                            hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);
                }))
                .then(overriding(LAZY_CREATION_ENABLED, FALSE), withOpContext((spec, opLog) -> {
                    final var op3 = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, CRYPTO_TRANSFER_RECEIVER, ONE_HUNDRED_HBARS))
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasPrecheck(INVALID_SIGNATURE)
                            .via(TRANSFER_TXN_2);

                    allRunFor(spec, op3);
                }));
    }

    private HapiSpec hollowAccountCreationChargesExpectedFees() {
        final long REDUCED_NODE_FEE = 2L;
        final long REDUCED_NETWORK_FEE = 3L;
        final long REDUCED_SERVICE_FEE = 3L;
        final long REDUCED_TOTAL_FEE = REDUCED_NODE_FEE + REDUCED_NETWORK_FEE + REDUCED_SERVICE_FEE;
        final var payer = "payer";
        final var secondKey = "secondKey";
        return propertyPreservingHapiSpec("hollowAccountCreationChargesExpectedFees")
                .preserving(LAZY_CREATION_ENABLED, CRYPTO_CREATE_WITH_ALIAS_ENABLED)
                .given(
                        overridingTwo(LAZY_CREATION_ENABLED, "true", CRYPTO_CREATE_WITH_ALIAS_ENABLED, "true"),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(secondKey).shape(SECP_256K1_SHAPE),
                        cryptoCreate(payer).balance(0L),
                        reduceFeeFor(
                                List.of(CryptoTransfer, CryptoUpdate, CryptoCreate),
                                REDUCED_NODE_FEE,
                                REDUCED_NETWORK_FEE,
                                REDUCED_SERVICE_FEE))
                .when(withOpContext((spec, opLog) -> {
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
                            .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)
                            .via(TRANSFER_TXN);
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
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);
                    final var op8 = getAliasedAccountInfo(secondKey)
                            .has(accountWith()
                                    .hasEmptyKey()
                                    .expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0, 0)
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false)
                                    .memo(LAZY_MEMO));
                    final var op9 = getAccountBalance(payer).hasTinyBars(0).logged();
                    allRunFor(
                            spec,
                            transferToPayerAgain,
                            op5,
                            notExistingAccountInfo,
                            op6,
                            op7,
                            op8,
                            op9,
                            uploadDefaultFeeSchedules(GENESIS));
                }))
                .then(uploadDefaultFeeSchedules(GENESIS));
    }

    private HapiSpec hollowAccountCompletionWithEthereumTransaction() {
        final Map<String, String> startingProps = new HashMap<>();
        final String CONTRACT = "Fuse";
        return defaultHapiSpec("HollowAccountCompletionWithEthereumTransaction")
                .given(
                        remembering(startingProps, LAZY_CREATION_ENABLED, CHAIN_ID_PROP),
                        overridingTwo(LAZY_CREATION_ENABLED, TRUE, CHAIN_ID_PROP, "298"),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        uploadInitCode(CONTRACT))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, 2 * ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);

                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged();
                    allRunFor(spec, op, hapiGetTxnRecord);

                    final AccountID newAccountID =
                            hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);

                    final var op2 = ethereumContractCreate(CONTRACT)
                            .type(EthTxData.EthTransactionType.LEGACY_ETHEREUM)
                            .gasLimit(1_000_000)
                            .signingWith(SECP_256K1_SOURCE_KEY)
                            .payingWith(RELAYER)
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);

                    final var op3 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith().key(SECP_256K1_SOURCE_KEY).noAlias());

                    final HapiGetTxnRecord hapiGetSecondTxnRecord =
                            getTxnRecord(TRANSFER_TXN_2).andAllChildRecords().logged();

                    allRunFor(spec, op2, op3, hapiGetSecondTxnRecord);
                }));
    }

    private HapiSpec contractDeployAfterEthereumTransferLazyCreate() {
        final var RECIPIENT_KEY = LAZY_ACCOUNT_RECIPIENT;
        final var lazyCreateTxn = PAY_TXN;
        return propertyPreservingHapiSpec("contractDeployAfterEthereumTransferLazyCreate")
                .preserving(CHAIN_ID_PROP, LAZY_CREATE_PROPERTY_NAME, CONTRACTS_EVM_VERSION_PROP)
                .given(
                        overridingThree(
                                CHAIN_ID_PROP,
                                "298",
                                LAZY_CREATE_PROPERTY_NAME,
                                "true",
                                CONTRACTS_EVM_VERSION_PROP,
                                V_0_34),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(RECIPIENT_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT),
                        getTxnRecord(AUTO_ACCOUNT).andAllChildRecords(),
                        uploadInitCode(FACTORY_MIRROR_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
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
                        getTxnRecord(lazyCreateTxn).andAllChildRecords().logged())))
                .then(withOpContext((spec, opLog) -> {
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

    private HapiSpec contractCallAfterEthereumTransferLazyCreate() {
        final var RECIPIENT_KEY = LAZY_ACCOUNT_RECIPIENT;
        final var lazyCreateTxn = PAY_TXN;
        return propertyPreservingHapiSpec("contractCallAfterEthereumTransferLazyCreate")
                .preserving(CHAIN_ID_PROP, LAZY_CREATE_PROPERTY_NAME, CONTRACTS_EVM_VERSION_PROP)
                .given(
                        overridingThree(
                                CHAIN_ID_PROP,
                                "298",
                                LAZY_CREATE_PROPERTY_NAME,
                                "true",
                                CONTRACTS_EVM_VERSION_PROP,
                                V_0_34),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(RECIPIENT_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT),
                        getTxnRecord(AUTO_ACCOUNT).andAllChildRecords(),
                        uploadInitCode(FACTORY_MIRROR_CONTRACT),
                        contractCreate(FACTORY_MIRROR_CONTRACT).via(CREATE_TX).balance(20))
                .when(withOpContext((spec, opLog) -> allRunFor(
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
                        getTxnRecord(lazyCreateTxn).logged())))
                .then(withOpContext((spec, opLog) -> {
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

    private HapiSpec lazyCreateViaEthereumCryptoTransfer() {
        final var RECIPIENT_KEY = LAZY_ACCOUNT_RECIPIENT;
        final var lazyCreateTxn = PAY_TXN;
        final var failedLazyCreateTxn = "failedLazyCreateTxn";
        return propertyPreservingHapiSpec("lazyCreateViaEthereumCryptoTransfer")
                .preserving(CHAIN_ID_PROP, LAZY_CREATE_PROPERTY_NAME, CONTRACTS_EVM_VERSION_PROP)
                .given(
                        overridingThree(
                                CHAIN_ID_PROP,
                                "298",
                                LAZY_CREATE_PROPERTY_NAME,
                                "true",
                                CONTRACTS_EVM_VERSION_PROP,
                                V_0_34),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(RECIPIENT_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT),
                        getTxnRecord(AUTO_ACCOUNT).andAllChildRecords())
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        TxnVerbs.ethereumCryptoTransferToAlias(
                                        spec.registry().getKey(RECIPIENT_KEY).getECDSASecp256K1(), FIVE_HBARS)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxFeePerGas(0L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(200_000L)
                                .via(failedLazyCreateTxn)
                                .hasKnownStatus(INSUFFICIENT_GAS),
                        TxnVerbs.ethereumCryptoTransferToAlias(
                                        spec.registry().getKey(RECIPIENT_KEY).getECDSASecp256K1(), FIVE_HBARS)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(1)
                                .maxFeePerGas(0L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(2_000_000L)
                                .via(lazyCreateTxn)
                                .hasKnownStatus(SUCCESS))))
                .then(withOpContext((spec, opLog) -> {
                    final var failedLazyTxnRecord = getTxnRecord(failedLazyCreateTxn)
                            .hasPriority(recordWith()
                                    .targetedContractId(ContractID.newBuilder().getDefaultInstanceForType()))
                            .logged();
                    final var failedLazyTxnChildRecordsCheck =
                            emptyChildRecordsCheck(failedLazyCreateTxn, INSUFFICIENT_GAS);
                    allRunFor(spec, failedLazyTxnRecord, failedLazyTxnChildRecordsCheck);

                    final var ecdsaSecp256K1 =
                            spec.registry().getKey(RECIPIENT_KEY).getECDSASecp256K1();
                    final var aliasAsByteString =
                            ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(ecdsaSecp256K1.toByteArray()));
                    AtomicReference<AccountID> lazyAccountIdReference = new AtomicReference<>();
                    final var lazyAccountInfoCheck = getAliasedAccountInfo(aliasAsByteString)
                            .logged()
                            .has(accountWith().balance(FIVE_HBARS).key(EMPTY_KEY))
                            .exposingIdTo(lazyAccountIdReference::set);
                    allRunFor(spec, lazyAccountInfoCheck);
                    final var id = ContractID.newBuilder()
                            .setContractNum(lazyAccountIdReference.get().getAccountNum())
                            .setShardNum(lazyAccountIdReference.get().getShardNum())
                            .setRealmNum(lazyAccountIdReference.get().getRealmNum())
                            .build();
                    final var payTxn = getTxnRecord(lazyCreateTxn)
                            .hasPriority(recordWith()
                                    .targetedContractId(id)
                                    .contractCallResult(
                                            ContractFnResultAsserts.resultWith().contract(asContractString(id))))
                            .andAllChildRecords()
                            .logged();
                    final var childRecordsCheck = childRecordsCheck(
                            lazyCreateTxn,
                            SUCCESS,
                            recordWith().status(SUCCESS).memo(LAZY_MEMO).alias(ByteString.EMPTY));
                    allRunFor(spec, payTxn, childRecordsCheck);
                }));
    }

    private HapiSpec hollowAccountCompletionWithSimultaniousPropertiesUpdate() {
        return propertyPreservingHapiSpec("hollowAccountCompletionWithSimultaniousPropertiesUpdate")
                .preserving(LAZY_CREATION_ENABLED)
                .given(
                        overriding(LAZY_CREATION_ENABLED, TRUE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate(CRYPTO_TRANSFER_RECEIVER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(withOpContext((spec, opLog) -> {
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

                    final AccountID newAccountID =
                            hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);
                }))
                .then(withOpContext((spec, opLog) -> {
                    final var op2 = fileUpdate(APP_PROPERTIES)
                            .payingWith(ADDRESS_BOOK_CONTROL)
                            .overridingProps(Map.of(LAZY_CREATION_ENABLED, "" + FALSE))
                            .deferStatusResolution();

                    final var op3 = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, CRYPTO_TRANSFER_RECEIVER, ONE_HUNDRED_HBARS))
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasPrecheck(OK)
                            .hasKnownStatus(INVALID_PAYER_SIGNATURE)
                            .via(TRANSFER_TXN_2);

                    allRunFor(spec, op2, op3);
                }));
    }

    @HapiTest
    public HapiSpec autoAssociationWorksForContracts() {
        final var theContract = "CreateDonor";
        final String tokenA = "tokenA";
        final String tokenB = "tokenB";
        final String uniqueToken = UNIQUE;
        final String tokenAcreateTxn = "tokenACreate";
        final String tokenBcreateTxn = "tokenBCreate";
        final String transferToFU = "transferToFU";

        return propertyPreservingHapiSpec("autoAssociationWorksForContracts")
                .preserving("contracts.allowAutoAssociations")
                .given(
                        overriding("contracts.allowAutoAssociations", "true"),
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
                                        .maxAutoAssociations(2)))
                .when(
                        cryptoTransfer(movingUnique(uniqueToken, 1L).between(TOKEN_TREASURY, theContract)),
                        getContractInfo(theContract)
                                .has(ContractInfoAsserts.contractWith()
                                        .hasAlreadyUsedAutomaticAssociations(2)
                                        .maxAutoAssociations(2)))
                .then(
                        cryptoTransfer(moving(1, tokenB).between(TOKEN_TREASURY, theContract))
                                .hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS)
                                .via("failedTransfer"),
                        getContractInfo(theContract)
                                .has(ContractInfoAsserts.contractWith()
                                        .hasAlreadyUsedAutomaticAssociations(2)
                                        .maxAutoAssociations(2))
                                .logged());
    }

    @HapiTest
    private HapiSpec customFeesHaveExpectedAutoCreateInteractions() {
        final var nftWithRoyaltyNoFallback = "nftWithRoyaltyNoFallback";
        final var nftWithRoyaltyPlusHtsFallback = "nftWithRoyaltyPlusFallback";
        final var nftWithRoyaltyPlusHbarFallback = "nftWithRoyaltyPlusHbarFallback";
        final var ftWithNetOfTransfersFractional = "ftWithNetOfTransfersFractional";
        final var ftWithNonNetOfTransfersFractional = "ftWithNonNetOfTransfersFractional";
        final var finalReceiverKey = "finalReceiverKey";
        final var otherCollector = "otherCollector";
        final var finalTxn = "finalTxn";

        return propertyPreservingHapiSpec("CustomFeesHaveExpectedAutoCreateInteractions")
                .preserving("contracts.allowAutoAssociations")
                .given(
                        overriding("contracts.allowAutoAssociations", "true"),
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
                                        creation ->
                                                creation.withCustom(royaltyFeeNoFallback(1L, 100L, TOKEN_TREASURY))),
                                createWellKnownNonFungibleToken(
                                        nftWithRoyaltyPlusHbarFallback,
                                        1,
                                        creation -> creation.withCustom(royaltyFeeWithFallback(
                                                1L,
                                                100L,
                                                fixedHbarFeeInheritingRoyaltyCollector(ONE_HBAR),
                                                TOKEN_TREASURY)))),
                        tokenAssociate(otherCollector, ftWithNonNetOfTransfersFractional),
                        createWellKnownNonFungibleToken(
                                nftWithRoyaltyPlusHtsFallback,
                                1,
                                creation -> creation.withCustom(royaltyFeeWithFallback(
                                        1L,
                                        100L,
                                        fixedHtsFeeInheritingRoyaltyCollector(666, ftWithNonNetOfTransfersFractional),
                                        otherCollector))))
                .when(
                        autoCreateWithFungible(ftWithNetOfTransfersFractional),
                        autoCreateWithFungible(ftWithNonNetOfTransfersFractional),
                        autoCreateWithNonFungible(nftWithRoyaltyNoFallback, SUCCESS),
                        autoCreateWithNonFungible(
                                nftWithRoyaltyPlusHbarFallback, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE))
                .then(
                        newKeyNamed(finalReceiverKey),
                        cryptoTransfer(
                                moving(100_000, ftWithNonNetOfTransfersFractional)
                                        .between(TOKEN_TREASURY, CIVILIAN),
                                movingUnique(nftWithRoyaltyPlusHtsFallback, 1L).between(TOKEN_TREASURY, CIVILIAN)),
                        cryptoTransfer(
                                        moving(10_000, ftWithNonNetOfTransfersFractional)
                                                .between(CIVILIAN, finalReceiverKey),
                                        movingUnique(nftWithRoyaltyPlusHtsFallback, 1L)
                                                .between(CIVILIAN, finalReceiverKey))
                                .hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE)
                                .via(finalTxn));
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

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
