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
package com.hedera.services.bdd.suites.autorenew;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.assertTinybarAmountIsApproxUsd;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.literalInitcodeFor;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.defaultMinAutoRenewPeriod;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.enableContractAutoRenewWith;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.*;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.INSERT_ABI;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractAutoExpirySpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ContractAutoExpirySpecs.class);

    public static void main(String... args) {
        new ContractAutoExpirySpecs().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    renewsUsingContractFundsIfNoAutoRenewAccount(),
                    renewalFeeDistributedToStakingAccounts(),
                    renewsUsingAutoRenewAccountIfSet(),
                    chargesContractFundsWhenAutoRenewAccountHasZeroBalance(),
                    storageExpiryWorksAtTheExpectedInterval(),
                    autoRenewWorksAsExpected(),
                    autoRenewInGracePeriodIfEnoughBalance(),
                    storageRentChargedOnlyAfterInitialFreePeriodIsComplete(),
                });
    }

    private HapiSpec storageRentChargedOnlyAfterInitialFreePeriodIsComplete() {
        final var contract = "User";
        final var contract2 = "User";
        final var gasToOffer = 1_000_000;
        final var minimalLifetime = 4;
        final var initBalance = 100 * ONE_HBAR;
        final var autoRenewAccount = "autoRenewAccount";
        final var canonicalUsdFee = 0.026;

        final var renewalFeeWithoutStorage = new AtomicLong();
        final var renewalFeeWithStorage = new AtomicLong();

        return defaultHapiSpec("storageRentChargedOnlyAfterInitialFreePeriodIsComplete")
                .given(
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(contract),
                        cryptoCreate(autoRenewAccount).balance(initBalance),
                        /* This contract has 0 key/value mappings at creation */
                        contractCreate(contract)
                                .autoRenewSecs(minimalLifetime)
                                .autoRenewAccountId(autoRenewAccount),

                        /* Now we update the per-contract limit to 10 mappings */
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                INDIVIDUAL_KV_LIMIT_PROP,
                                                "10",
                                                CONS_MAX_GAS_PROP,
                                                "100_000_000",
                                                STORAGE_PRICE_TIERS_PROP,
                                                "0til20",
                                                STAKING_FEES_NODE_REWARD_PERCENTAGE,
                                                "0",
                                                STAKING_FEES_STAKING_REWARD_PERCENTAGE,
                                                "0",
                                                "contracts.freeStorageTierLimit",
                                                "10")))
                .when(
                        /* The first call to insert adds 5 mappings */
                        contractCall(contract, INSERT_ABI, BigInteger.ONE, BigInteger.ONE)
                                .payingWith(GENESIS)
                                .gas(gasToOffer),
                        /* Each subsequent call to adds 3 mappings; so 8 total after this */
                        contractCall(contract, INSERT_ABI, BigInteger.TWO, BigInteger.valueOf(4))
                                .payingWith(GENESIS)
                                .gas(gasToOffer),

                        /* Confirm the storage size didn't change */
                        getContractInfo(contract).has(contractWith().numKvPairs(8)),
                        getAccountBalance(autoRenewAccount).hasTinyBars(initBalance),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(2_000L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var lookup = getAccountBalance(autoRenewAccount);
                                    allRunFor(spec, lookup);
                                    renewalFeeWithoutStorage.set(
                                            initBalance
                                                    - lookup.getResponse()
                                                            .getCryptogetAccountBalance()
                                                            .getBalance());
                                    opLog.info(
                                            "Renewal fee without storage: {}",
                                            renewalFeeWithoutStorage.get());
                                }))
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(STORAGE_PRICE_TIERS_PROP, "100til20")),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(2_000L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var lookup = getAccountBalance(autoRenewAccount);
                                    allRunFor(spec, lookup);
                                    renewalFeeWithStorage.set(
                                            initBalance
                                                    - lookup.getResponse()
                                                            .getCryptogetAccountBalance()
                                                            .getBalance()
                                                    - renewalFeeWithoutStorage.get());
                                    opLog.info(
                                            "Renewal fee with storage: {}",
                                            renewalFeeWithStorage.get());
                                    // TODO: this is intentionally false so a test will fail! 🔥🔥🔥
                                    assertFalse(
                                            renewalFeeWithStorage.get()
                                                    > renewalFeeWithoutStorage.get());
                                }));
    }

    private HapiSpec autoRenewWorksAsExpected() {
        final var initcode = "initcode";
        final var contractToRenew = "InstantStorageHog";
        final var minimalLifetime = 3;
        final var creation = "creation";
        final var autoRenewAccount = "autoRenewAccount";

        return defaultHapiSpec("autoRenewWorksAsExpected")
                .given(
                        createLargeFile(GENESIS, initcode, literalInitcodeFor("InstantStorageHog")),
                        enableContractAutoRenewWith(minimalLifetime, minimalLifetime),
                        uploadInitCode(contractToRenew),
                        cryptoCreate(autoRenewAccount).balance(0L),
                        getAccountBalance(autoRenewAccount),
                        contractCreate(contractToRenew, new BigInteger("63"))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(initcode)
                                .autoRenewSecs(minimalLifetime)
                                .autoRenewAccountId(autoRenewAccount)
                                .balance(0L)
                                .via(creation),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        /*
                         * Contract is expired and doesn't have enough to auto-renew.
                         * But it is in grace period , so not deleted
                         */
                        getContractInfo(contractToRenew).has(contractWith().isNotDeleted()),
                        /*
                         * Contract is expired and tries to auto-renew at this trigger,
                         * but no balance. So does nothing.
                         */
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        /*
                         * Contract's grace period completed, for the next
                         * trigger contract is deleted.
                         */
                        getContractInfo(contractToRenew).has(contractWith().isNotDeleted()),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        getContractInfo(contractToRenew).has(contractWith().isDeleted()),
                        overriding(
                                "ledger.autoRenewPeriod.minDuration", defaultMinAutoRenewPeriod));
    }

    private HapiSpec autoRenewInGracePeriodIfEnoughBalance() {
        final var initcode = "initcode";
        final var contractToRenew = "InstantStorageHog";
        final var minimalLifetime = 3;
        final var creation = "creation";
        final var autoRenewAccount = "autoRenewAccount";
        final var expectedExpiryPostRenew = new AtomicLong();
        final var currentExpiry = new AtomicLong();

        return defaultHapiSpec("autoRenewInGracePeriodIfEnoughBalance")
                .given(
                        createLargeFile(GENESIS, initcode, literalInitcodeFor("InstantStorageHog")),
                        enableContractAutoRenewWith(minimalLifetime, minimalLifetime),
                        uploadInitCode(contractToRenew),
                        cryptoCreate(autoRenewAccount).balance(0L),
                        getAccountBalance(autoRenewAccount),
                        contractCreate(contractToRenew, new BigInteger("63"))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(initcode)
                                .autoRenewSecs(minimalLifetime)
                                .autoRenewAccountId(autoRenewAccount)
                                .balance(0L)
                                .via(creation),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var lookup = getTxnRecord(creation);
                                    allRunFor(spec, lookup);

                                    final var record = lookup.getResponseRecord();
                                    final var birth = record.getConsensusTimestamp().getSeconds();
                                    currentExpiry.set(birth + minimalLifetime);
                                    expectedExpiryPostRenew.set(
                                            birth
                                                    + minimalLifetime
                                                    + minimalLifetime
                                                    + minimalLifetime);
                                    opLog.info(
                                            "Expecting post-renewal expiry of {}, current expiry"
                                                    + " {}",
                                            expectedExpiryPostRenew.get(),
                                            currentExpiry.get());
                                    final var info =
                                            getContractInfo(contractToRenew)
                                                    .has(
                                                            contractWith()
                                                                    .isNotDeleted()
                                                                    .approxExpiry(
                                                                            currentExpiry.get(),
                                                                            1));

                                    allRunFor(spec, info);
                                }),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        /*
                         * Funding for auto-renew account with enough balance. So when
                         * contract successfully auto-renews on next trigger
                         */
                        cryptoTransfer(tinyBarsFromTo(GENESIS, autoRenewAccount, 100 * ONE_HBAR)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        getAccountBalance(autoRenewAccount),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var lookup =
                                            getContractInfo(contractToRenew)
                                                    .has(
                                                            contractWith()
                                                                    .approxExpiry(
                                                                            expectedExpiryPostRenew
                                                                                    .get(),
                                                                            2));

                                    allRunFor(spec, lookup);
                                }),
                        overriding(
                                "ledger.autoRenewPeriod.minDuration", defaultMinAutoRenewPeriod));
    }

    private HapiSpec renewalFeeDistributedToStakingAccounts() {
        final var initcode = "initcode";
        final var contractToRenew = "InstantStorageHog";
        final var initBalance = ONE_HBAR;
        final var minimalLifetime = 3;
        final var standardLifetime = 7776000L;
        final var creation = "creation";
        final var expectedExpiryPostRenew = new AtomicLong();

        return defaultHapiSpec("renewalFeeDistributedToStakingAccounts")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                "staking.fees.stakingRewardPercentage", "10",
                                                "staking.fees.nodeRewardPercentage", "20")),
                        createLargeFile(GENESIS, initcode, literalInitcodeFor("InstantStorageHog")),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(contractToRenew),
                        contractCreate(contractToRenew, 63)
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(initcode)
                                .autoRenewSecs(minimalLifetime)
                                .balance(initBalance)
                                .via(creation),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var lookup = getTxnRecord(creation);
                                    allRunFor(spec, lookup);
                                    final var record = lookup.getResponseRecord();
                                    final var birth = record.getConsensusTimestamp().getSeconds();
                                    expectedExpiryPostRenew.set(
                                            birth + minimalLifetime + standardLifetime);
                                    opLog.info(
                                            "Expecting post-renewal expiry of {}",
                                            expectedExpiryPostRenew.get());
                                }),
                        contractUpdate(contractToRenew).newAutoRenew(7776000L),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(
                        balanceSnapshot("before", "0.0.3"),
                        balanceSnapshot("fundingBefore", "0.0.98"),
                        balanceSnapshot("stakingReward", "0.0.800"),
                        balanceSnapshot("nodeReward", "0.0.801"),

                        // Any transaction will do
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)).via("trigger"),
                        getTxnRecord("trigger").andAllChildRecords())
                .then(
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var lookup =
                                            getContractInfo(contractToRenew)
                                                    .has(
                                                            contractWith()
                                                                    .approxExpiry(
                                                                            expectedExpiryPostRenew
                                                                                    .get(),
                                                                            5));

                                    allRunFor(spec, lookup);
                                    final var balance =
                                            lookup.getResponse()
                                                    .getContractGetInfo()
                                                    .getContractInfo()
                                                    .getBalance();
                                    final var renewalFee = initBalance - balance;
                                    final long stakingAccountFee = (long) (renewalFee * 0.1);
                                    final long nodeAccountFee = (long) (renewalFee * 0.3);
                                    final long fundingAccountFee = (long) (renewalFee * 0.7);
                                    final var canonicalUsdFee = 0.026;
                                    assertTinybarAmountIsApproxUsd(
                                            spec, canonicalUsdFee, renewalFee, 5.0);
                                    getAccountBalance("0.0.98")
                                            .hasTinyBars(
                                                    changeFromSnapshot(
                                                            "fundingBefore", fundingAccountFee));
                                    getAccountBalance("0.0.800")
                                            .hasTinyBars(
                                                    changeFromSnapshot(
                                                            "stakingReward", stakingAccountFee));
                                    getAccountBalance("0.0.801")
                                            .hasTinyBars(
                                                    changeFromSnapshot(
                                                            "nodeReward", nodeAccountFee));
                                }),
                        overriding(
                                "ledger.autoRenewPeriod.minDuration", defaultMinAutoRenewPeriod));
    }

    private HapiSpec chargesContractFundsWhenAutoRenewAccountHasZeroBalance() {
        final var initcode = "initcode";
        final var contractToRenew = "InstantStorageHog";
        final var initBalance = ONE_HBAR;
        final var minimalLifetime = 3;
        final var standardLifetime = 7776000L;
        final var creation = "creation";
        final var expectedExpiryPostRenew = new AtomicLong();
        final var autoRenewAccount = "autoRenewAccount";
        final var autoRenewAccountBalance = 0;

        return defaultHapiSpec("chargesContractFundsWhenAutoRenewAccountHasZeroBalance")
                .given(
                        createLargeFile(GENESIS, initcode, literalInitcodeFor("InstantStorageHog")),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(contractToRenew),
                        cryptoCreate(autoRenewAccount).balance((long) autoRenewAccountBalance),
                        getAccountBalance(autoRenewAccount),
                        contractCreate(contractToRenew, 63)
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(initcode)
                                .autoRenewSecs(minimalLifetime)
                                .autoRenewAccountId(autoRenewAccount)
                                .balance(initBalance)
                                .via(creation),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var lookup = getTxnRecord(creation);
                                    allRunFor(spec, lookup);
                                    final var responseRecord = lookup.getResponseRecord();
                                    final var birth =
                                            responseRecord.getConsensusTimestamp().getSeconds();
                                    expectedExpiryPostRenew.set(
                                            birth + minimalLifetime + standardLifetime);
                                    opLog.info(
                                            "Expecting post-renewal expiry of {}",
                                            expectedExpiryPostRenew.get());
                                }),
                        contractUpdate(contractToRenew).newAutoRenew(7776000L).via("updateTxn"),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        getTxnRecord("updateTxn"))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var lookupContract =
                                            getContractInfo(contractToRenew)
                                                    .has(
                                                            contractWith()
                                                                    .approxExpiry(
                                                                            expectedExpiryPostRenew
                                                                                    .get(),
                                                                            5));
                                    final var lookupAccount = getAccountBalance(autoRenewAccount);
                                    allRunFor(spec, lookupContract, lookupAccount);

                                    final var contractBalance =
                                            lookupContract
                                                    .getResponse()
                                                    .getContractGetInfo()
                                                    .getContractInfo()
                                                    .getBalance();
                                    final var accountBalance =
                                            lookupAccount
                                                    .getResponse()
                                                    .getCryptogetAccountBalance()
                                                    .getBalance();
                                    opLog.info(
                                            "AutoRenew account balance {}, contract balance {}",
                                            accountBalance,
                                            contractBalance);

                                    assertEquals(0, accountBalance);
                                    assertTrue(contractBalance < initBalance);
                                    final var renewalFee = initBalance - contractBalance;
                                    opLog.info("Renewal fees actual {}", renewalFee);
                                    final var canonicalUsdFee = 0.026;
                                    assertTinybarAmountIsApproxUsd(
                                            spec, canonicalUsdFee, renewalFee, 5.0);
                                }),
                        overriding(
                                "ledger.autoRenewPeriod.minDuration", defaultMinAutoRenewPeriod));
    }

    private HapiSpec renewsUsingAutoRenewAccountIfSet() {
        final var initcode = "initcode";
        final var contractToRenew = "InstantStorageHog";
        final var initBalance = ONE_HBAR;
        final var minimalLifetime = 3;
        final var standardLifetime = 7776000L;
        final var creation = "creation";
        final var expectedExpiryPostRenew = new AtomicLong();
        final var autoRenewAccount = "autoRenewAccount";
        final var renewAccountBalance = initBalance;

        return defaultHapiSpec("renewsUsingAutoRenewAccountIfSet")
                .given(
                        createLargeFile(GENESIS, initcode, literalInitcodeFor("InstantStorageHog")),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(contractToRenew),
                        cryptoCreate(autoRenewAccount).balance(renewAccountBalance),
                        getAccountBalance(autoRenewAccount),
                        contractCreate(contractToRenew, 63)
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(initcode)
                                .autoRenewSecs(minimalLifetime)
                                .autoRenewAccountId(autoRenewAccount)
                                .balance(initBalance)
                                .via(creation),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var lookup = getTxnRecord(creation);
                                    allRunFor(spec, lookup);
                                    final var record = lookup.getResponseRecord();
                                    final var birth = record.getConsensusTimestamp().getSeconds();
                                    expectedExpiryPostRenew.set(
                                            birth + minimalLifetime + standardLifetime);
                                    opLog.info(
                                            "Expecting post-renewal expiry of {}",
                                            expectedExpiryPostRenew.get());
                                }),
                        contractUpdate(contractToRenew).newAutoRenew(7776000L).via("updateTxn"),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        getTxnRecord("updateTxn"))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var lookupContract =
                                            getContractInfo(contractToRenew)
                                                    .has(
                                                            contractWith()
                                                                    .approxExpiry(
                                                                            expectedExpiryPostRenew
                                                                                    .get(),
                                                                            5));
                                    final var lookupAccount = getAccountBalance(autoRenewAccount);
                                    allRunFor(spec, lookupContract, lookupAccount);

                                    final var contractBalance =
                                            lookupContract
                                                    .getResponse()
                                                    .getContractGetInfo()
                                                    .getContractInfo()
                                                    .getBalance();
                                    final var accountBalance =
                                            lookupAccount
                                                    .getResponse()
                                                    .getCryptogetAccountBalance()
                                                    .getBalance();
                                    opLog.info(
                                            "AutoRenew account balance {}, contract balance {}",
                                            accountBalance,
                                            contractBalance);

                                    assertEquals(initBalance, contractBalance);
                                    assertTrue(accountBalance < renewAccountBalance);
                                    final var renewalFee = renewAccountBalance - accountBalance;
                                    opLog.info("Renewal fees actual {}", renewalFee);
                                    final var canonicalUsdFee = 0.026;
                                    assertTinybarAmountIsApproxUsd(
                                            spec, canonicalUsdFee, renewalFee, 5.0);
                                }),
                        overriding(
                                "ledger.autoRenewPeriod.minDuration", defaultMinAutoRenewPeriod));
    }

    private HapiSpec storageExpiryWorksAtTheExpectedInterval() {
        final var initcode = "initcode";
        final var contractToRemove = "InstantStorageHog";
        final var minimalLifetime = 4;
        final var aFungibleToken = "aFT";
        final var bFungibleToken = "bFT";
        final var nonFungibleToken = "NFT";
        final var supplyKey = "multi";
        final var aFungibleAmount = 1_000_000L;
        final var bFungibleAmount = 666L;

        return defaultHapiSpec("StorageExpiryWorksAtTheExpectedInterval")
                .given(
                        newKeyNamed(supplyKey),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(aFungibleToken)
                                .initialSupply(aFungibleAmount)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(bFungibleToken)
                                .initialSupply(bFungibleAmount)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(nonFungibleToken)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(supplyKey)
                                .treasury(TOKEN_TREASURY),
                        mintToken(
                                nonFungibleToken,
                                List.of(
                                        ByteString.copyFromUtf8("Time moved, yet seemed to stop"),
                                        ByteString.copyFromUtf8("As 'twere a spinning-top"))),
                        createLargeFile(GENESIS, initcode, literalInitcodeFor("InstantStorageHog")),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        contractCreate(contractToRemove, 63)
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(initcode)
                                .balance(0)
                                .autoRenewSecs(minimalLifetime),
                        tokenAssociate(
                                contractToRemove,
                                List.of(aFungibleToken, bFungibleToken, nonFungibleToken)),
                        cryptoTransfer(
                                moving(aFungibleAmount, aFungibleToken)
                                        .between(TOKEN_TREASURY, contractToRemove),
                                moving(bFungibleAmount, bFungibleToken)
                                        .between(TOKEN_TREASURY, contractToRemove),
                                movingUnique(nonFungibleToken, 1L, 2L)
                                        .between(TOKEN_TREASURY, contractToRemove)),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(2_000L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        // Now the contract is gone
                        getContractInfo(contractToRemove)
                                .hasCostAnswerPrecheck(INVALID_CONTRACT_ID),
                        // And the fungible units were returned to the treasury
                        getAccountBalance(TOKEN_TREASURY)
                                .hasTokenBalance(aFungibleToken, aFungibleAmount)
                                .hasTokenBalance(bFungibleToken, bFungibleAmount)
                                .hasTokenBalance(nonFungibleToken, 2),
                        // And the NFTs are now owned by the treasury
                        getTokenNftInfo(nonFungibleToken, 1L).hasAccountID(TOKEN_TREASURY),
                        getTokenNftInfo(nonFungibleToken, 2L).hasAccountID(TOKEN_TREASURY));
    }

    private HapiSpec renewsUsingContractFundsIfNoAutoRenewAccount() {
        final var initcode = "initcode";
        final var contractToRenew = "InstantStorageHog";
        final var initBalance = ONE_HBAR;
        final var minimalLifetime = 3;
        final var standardLifetime = 7776000L;
        final var creation = "creation";
        final var expectedExpiryPostRenew = new AtomicLong();
        final var consTimeRepro = "ConsTimeRepro";
        final var failingCall = "FailingCall";
        final AtomicReference<Timestamp> parentConsTime = new AtomicReference<>();

        return defaultHapiSpec("RenewsUsingContractFundsIfNoAutoRenewAccount")
                .given(
                        uploadInitCode(consTimeRepro),
                        contractCreate(consTimeRepro),
                        createLargeFile(GENESIS, initcode, literalInitcodeFor("InstantStorageHog")),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(contractToRenew),
                        contractCreate(contractToRenew, BigInteger.valueOf(63))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(initcode)
                                .autoRenewSecs(minimalLifetime)
                                .balance(initBalance)
                                .via(creation),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var lookup = getTxnRecord(creation);
                                    allRunFor(spec, lookup);
                                    final var record = lookup.getResponseRecord();
                                    final var birth = record.getConsensusTimestamp().getSeconds();
                                    expectedExpiryPostRenew.set(
                                            birth + minimalLifetime + standardLifetime);
                                    opLog.info(
                                            "Expecting post-renewal expiry of {}",
                                            expectedExpiryPostRenew.get());
                                }),
                        contractUpdate(contractToRenew).newAutoRenew(7776000L),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(
                        // Any transaction will do; we choose a contract call that has
                        // a child record to validate consensus time assignment
                        contractCall(
                                        consTimeRepro,
                                        "createChildThenFailToAssociate",
                                        asHeadlongAddress(new byte[20]),
                                        asHeadlongAddress(new byte[20]))
                                .via(failingCall)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(
                        getTxnRecord(failingCall)
                                .exposingTo(
                                        failureRecord ->
                                                parentConsTime.set(
                                                        failureRecord.getConsensusTimestamp())),
                        sourcing(
                                () ->
                                        childRecordsCheck(
                                                failingCall,
                                                CONTRACT_REVERT_EXECUTED,
                                                recordWith()
                                                        .status(ResponseCodeEnum.INSUFFICIENT_GAS)
                                                        .consensusTimeImpliedByNonce(
                                                                parentConsTime.get(), 1))),
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var lookup =
                                            getContractInfo(contractToRenew)
                                                    .has(
                                                            contractWith()
                                                                    .approxExpiry(
                                                                            expectedExpiryPostRenew
                                                                                    .get(),
                                                                            5));
                                    allRunFor(spec, lookup);
                                    final var balance =
                                            lookup.getResponse()
                                                    .getContractGetInfo()
                                                    .getContractInfo()
                                                    .getBalance();
                                    final var renewalFee = initBalance - balance;
                                    final var canonicalUsdFee = 0.026;
                                    assertTinybarAmountIsApproxUsd(
                                            spec, canonicalUsdFee, renewalFee, 5.0);
                                }),
                        overriding(
                                "ledger.autoRenewPeriod.minDuration", defaultMinAutoRenewPeriod));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
