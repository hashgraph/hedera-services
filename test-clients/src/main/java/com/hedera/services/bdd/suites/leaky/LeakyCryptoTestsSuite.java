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
package com.hedera.services.bdd.suites.leaky;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOfDeferred;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.remembering;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCreateSuite.EMPTY_CONSTRUCTOR_CONTRACT;
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
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ED_25519_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.LAZY_CREATION_ENABLED;
import static com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs.SENDER_TXN;
import static com.hedera.services.bdd.suites.token.TokenPauseSpecs.DEFAULT_MIN_AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.token.TokenPauseSpecs.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION;
import static com.hedera.services.bdd.suites.token.TokenPauseSpecs.TokenIdOrderingAsserts.withOrderedTokenIds;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.RECEIVER;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LeakyCryptoTestsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LeakyCryptoTestsSuite.class);
    private static final String ASSOCIATIONS_LIMIT_PROPERTY = "entities.limitTokenAssociations";
    private static final String DEFAULT_ASSOCIATIONS_LIMIT =
            HapiSpecSetup.getDefaultNodeProps().get(ASSOCIATIONS_LIMIT_PROPERTY);

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
                scheduledCryptoApproveAllowanceWaitForExpiryTrue(),
                txnsUsingHip583FunctionalitiesAreNotAcceptedWhenFlagsAreDisabled(),
                getsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee());
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

        return defaultHapiSpec(
                        "GetsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee")
                .given(
                        cryptoCreate(civilian).balance(civilianStartBalance),
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .gas(gasToOffer)
                                .payingWith(civilian)
                                .balance(0L)
                                .via(creation),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var lookup = getTxnRecord(creation).logged();
                                    allRunFor(spec, lookup);
                                    final var creationRecord = lookup.getResponseRecord();
                                    final var gasUsed =
                                            creationRecord.getContractCreateResult().getGasUsed();
                                    gasFee.set(tinybarCostOfGas(spec, ContractCreate, gasUsed));
                                    offeredGasFee.set(
                                            tinybarCostOfGas(spec, ContractCreate, gasToOffer));
                                    nodeAndNetworkFee.set(
                                            creationRecord.getTransactionFee() - gasFee.get());
                                    log.info(
                                            "Network + node fees were {}, gas fee was {} (sum to"
                                                    + " {}, compare with {})",
                                            nodeAndNetworkFee::get,
                                            gasFee::get,
                                            () -> nodeAndNetworkFee.get() + gasFee.get(),
                                            creationRecord::getTransactionFee);
                                    maxSendable.set(
                                            civilianStartBalance
                                                    - 2 * nodeAndNetworkFee.get()
                                                    - gasFee.get()
                                                    - offeredGasFee.get());
                                    log.info(
                                            "Maximum amount send-able in precheck should be {}",
                                            maxSendable::get);
                                }))
                .then(
                        sourcing(
                                () ->
                                        getAccountBalance(civilian)
                                                .hasTinyBars(
                                                        civilianStartBalance
                                                                - nodeAndNetworkFee.get()
                                                                - gasFee.get())),
                        // Fire-and-forget a txn that will leave the civilian payer with 1 too few
                        // tinybars at consensus
                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(civilian, FUNDING, 1))
                                .payingWith(GENESIS)
                                .deferStatusResolution(),
                        sourcing(
                                () ->
                                        contractCustomCreate(EMPTY_CONSTRUCTOR_CONTRACT, "Clone")
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
                        cryptoCreate(OTHER_RECEIVER)
                                .balance(ONE_HBAR)
                                .maxAutomaticTokenAssociations(1),
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
                                        ByteString.copyFromUtf8("b"))))
                .when(
                        tokenAssociate(
                                OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN, TOKEN_WITH_CUSTOM_FEE),
                        tokenAssociate(
                                RECEIVER,
                                FUNGIBLE_TOKEN,
                                NON_FUNGIBLE_TOKEN,
                                TOKEN_WITH_CUSTOM_FEE),
                        cryptoTransfer(
                                moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER),
                                moving(15, TOKEN_WITH_CUSTOM_FEE).between(TOKEN_TREASURY, OWNER),
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, OWNER)),
                        scheduleCreate(
                                        SCHEDULED_TXN,
                                        cryptoApproveAllowance()
                                                .addCryptoAllowance(OWNER, SPENDER, 10 * ONE_HBAR)
                                                .addTokenAllowance(
                                                        OWNER, FUNGIBLE_TOKEN, SPENDER, 1500)
                                                .addTokenAllowance(
                                                        OWNER, TOKEN_WITH_CUSTOM_FEE, SPENDER, 100)
                                                .addNftAllowance(
                                                        OWNER,
                                                        NON_FUNGIBLE_TOKEN,
                                                        SPENDER,
                                                        false,
                                                        List.of(2L))
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
                                .has(
                                        accountDetailsWith()
                                                .cryptoAllowancesCount(0)
                                                .tokenAllowancesCount(0)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasNoSpender(),
                        sleepFor(12_000L),
                        cryptoCreate("foo").via("TRIGGERING_TXN"),
                        getScheduleInfo(SCHEDULED_TXN).hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .cryptoAllowancesCount(1)
                                                .cryptoAllowancesContaining(SPENDER, 10 * ONE_HBAR)
                                                .tokenAllowancesCount(2)
                                                .tokenAllowancesContaining(
                                                        FUNGIBLE_TOKEN, SPENDER, 1500L)
                                                .tokenAllowancesContaining(
                                                        TOKEN_WITH_CUSTOM_FEE, SPENDER, 100L)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(SPENDER));
    }

    private HapiSpec txnsUsingHip583FunctionalitiesAreNotAcceptedWhenFlagsAreDisabled() {
        final Map<String, String> startingProps = new HashMap<>();
        return defaultHapiSpec("txnsUsingHip583FunctionalitiesAreNotAcceptedWhenFlagsAreDisabled")
                .given(
                        remembering(
                                startingProps,
                                LAZY_CREATION_ENABLED,
                                CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED),
                        overridingTwo(
                                LAZY_CREATION_ENABLED, FALSE_VALUE,
                                CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED, FALSE_VALUE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    // create with ECDSA alias and no key
                                    final var op =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ecdsaKey.toByteString())
                                                    .hasPrecheck(NOT_SUPPORTED);
                                    // create with EVM address alias and no key
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var evmAddress =
                                            ByteString.copyFrom(recoverAddressFromPubKey(tmp));
                                    final var op2 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(evmAddress)
                                                    .hasPrecheck(NOT_SUPPORTED);
                                    // create with ED alias and no key
                                    final var ed25519Key = spec.registry().getKey(ED_25519_KEY);
                                    final var op3 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ed25519Key.toByteString())
                                                    .hasPrecheck(NOT_SUPPORTED);
                                    // create with evm address alias and ECDSA key
                                    final var op4 =
                                            cryptoCreate(ACCOUNT)
                                                    .key(SECP_256K1_SOURCE_KEY)
                                                    .alias(evmAddress)
                                                    .hasPrecheck(NOT_SUPPORTED);
                                    // create with ED alias and ED key
                                    final var op5 =
                                            cryptoCreate(ACCOUNT)
                                                    .key(ED_25519_KEY)
                                                    .alias(ed25519Key.toByteString())
                                                    .hasPrecheck(NOT_SUPPORTED);
                                    // create with ECDSA alias and key
                                    final var op6 =
                                            cryptoCreate(ACCOUNT)
                                                    .key(SECP_256K1_SOURCE_KEY)
                                                    .alias(ecdsaKey.toByteString())
                                                    .hasPrecheck(NOT_SUPPORTED);
                                    // assert that an account created with ECDSA key and no alias
                                    // does not automagically set alias to evm address
                                    final var op7 =
                                            cryptoCreate(ACCOUNT).key(SECP_256K1_SOURCE_KEY);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(SECP_256K1_SOURCE_KEY)
                                                                    .noAlias());
                                    allRunFor(
                                            spec,
                                            op,
                                            op2,
                                            op3,
                                            op4,
                                            op5,
                                            op6,
                                            op7,
                                            hapiGetAccountInfo);
                                }))
                .then(overridingAllOfDeferred(() -> startingProps));
    }

    private HapiSpec maxAutoAssociationSpec() {
        final int MONOGAMOUS_NETWORK = 1;
        final int maxAutoAssociations = 100;
        final int ADVENTUROUS_NETWORK = 1_000;
        final String user1 = "user1";

        return defaultHapiSpec("MaxAutoAssociationSpec")
                .given(
                        overridingTwo(
                                ASSOCIATIONS_LIMIT_PROPERTY,
                                TRUE_VALUE,
                                "tokens.maxPerAccount",
                                "" + MONOGAMOUS_NETWORK))
                .when()
                .then(
                        cryptoCreate(user1)
                                .balance(ONE_HBAR)
                                .maxAutomaticTokenAssociations(maxAutoAssociations)
                                .hasPrecheck(
                                        REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                        // Default is NOT to limit associations
                        overriding(ASSOCIATIONS_LIMIT_PROPERTY, DEFAULT_ASSOCIATIONS_LIMIT),
                        cryptoCreate(user1)
                                .balance(ONE_HBAR)
                                .maxAutomaticTokenAssociations(maxAutoAssociations),
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
                        blockingOrder(
                                IntStream.range(0, numTokens)
                                        .mapToObj(
                                                i ->
                                                        tokenCreate(tokenNameFn.apply(i))
                                                                .autoRenewAccount(DEFAULT_PAYER)
                                                                .autoRenewPeriod(1L)
                                                                .initialSupply(initialSupply)
                                                                .treasury(TOKEN_TREASURY))
                                        .toArray(HapiSpecOperation[]::new)),
                        tokenAssociate(civilian, List.of(assocOrder)),
                        blockingOrder(
                                IntStream.range(0, numTokens)
                                        .mapToObj(
                                                i ->
                                                        cryptoTransfer(
                                                                moving(
                                                                                nonZeroXfer,
                                                                                tokenNameFn.apply(
                                                                                        i))
                                                                        .between(
                                                                                TOKEN_TREASURY,
                                                                                civilian)))
                                        .toArray(HapiSpecOperation[]::new)))
                .when(sleepFor(1_000L), tokenDissociate(civilian, dissocOrder).via(dissociateTxn))
                .then(
                        getTxnRecord(dissociateTxn)
                                .hasPriority(
                                        recordWith()
                                                .tokenTransfers(withOrderedTokenIds(assocOrder))),
                        overriding(
                                LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION,
                                DEFAULT_MIN_AUTO_RENEW_PERIOD));
    }

    private HapiSpec cannotExceedAccountAllowanceLimit() {
        return defaultHapiSpec("CannotExceedAccountAllowanceLimit")
                .given(
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "3",
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "5"),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
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
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L)
                                        .between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SECOND_SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
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
                        remembering(
                                startingProps,
                                LAZY_CREATION_ENABLED,
                                CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED),
                        overridingTwo(
                                LAZY_CREATION_ENABLED,
                                FALSE_VALUE,
                                CRYPTO_CREATE_WITH_ALIAS_AND_EVM_ADDRESS_ENABLED,
                                TRUE_VALUE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    assert addressBytes.length > 0;
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    final var op =
                                            cryptoCreate(ACCOUNT)
                                                    .key(SECP_256K1_SOURCE_KEY)
                                                    .evmAddress(evmAddressBytes)
                                                    .balance(100 * ONE_HBAR)
                                                    .via("createTxn");
                                    final var op2 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ecdsaKey.toByteString())
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op3 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(evmAddressBytes)
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op4 =
                                            cryptoCreate(ACCOUNT)
                                                    .key(SECP_256K1_SOURCE_KEY)
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op5 =
                                            cryptoCreate(ACCOUNT)
                                                    .key(SECP_256K1_SOURCE_KEY)
                                                    .alias(ByteString.copyFromUtf8("Invalid alias"))
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op6 =
                                            cryptoCreate(ACCOUNT)
                                                    .key(SECP_256K1_SOURCE_KEY)
                                                    .alias(evmAddressBytes)
                                                    .balance(100 * ONE_HBAR)
                                                    .hasPrecheck(INVALID_ALIAS_KEY);

                                    allRunFor(spec, op, op2, op3, op4, op5, op6);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(SECP_256K1_SOURCE_KEY)
                                                                    .evmAddressAlias(
                                                                            evmAddressBytes)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    final var getTxnRecord =
                                            getTxnRecord("createTxn")
                                                    .hasPriority(recordWith().hasNoAlias());
                                    allRunFor(spec, hapiGetAccountInfo, getTxnRecord);
                                }))
                .then(overridingAllOfDeferred(() -> startingProps));
    }

    private HapiSpec cannotExceedAllowancesTransactionLimit() {
        return defaultHapiSpec("CannotExceedAllowancesTransactionLimit")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "4",
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "5"),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
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
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L)
                                        .between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, ANOTHER_SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SECOND_SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(
                                        OWNER,
                                        NON_FUNGIBLE_TOKEN,
                                        SPENDER,
                                        false,
                                        List.of(1L, 1L, 1L, 1L, 1L))
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

    private long tinybarCostOfGas(
            final HapiSpec spec, final HederaFunctionality function, final long gasAmount) {
        final var gasThousandthsOfTinycentPrice =
                spec.fees()
                        .getCurrentOpFeeData()
                        .get(function)
                        .get(DEFAULT)
                        .getServicedata()
                        .getGas();
        final var rates = spec.ratesProvider().rates();
        return (gasThousandthsOfTinycentPrice / 1000 * rates.getHbarEquiv())
                / rates.getCentEquiv()
                * gasAmount;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
