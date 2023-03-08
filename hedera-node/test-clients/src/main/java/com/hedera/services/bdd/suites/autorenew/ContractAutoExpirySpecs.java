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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
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
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.*;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.DEFAULT_MIN_AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.enableContractAutoRenewWith;
import static com.hedera.services.bdd.suites.contract.precompile.TokenInfoHTSSuite.HTS_COLLECTOR;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.*;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.INSERT_ABI;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.ExpiryRecordsValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractAutoExpirySpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ContractAutoExpirySpecs.class);
    private static final String INIT_CODE = "initcode";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String CREATION = "creation";
    private static final String CONTRACT_TO_RENEW = "InstantStorageHog";
    private static final String SUPPLY_KEY = "multi";
    private static final String UPDATE_TXN = "updateTxn";
    private static final String LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION = "ledger.autoRenewPeriod.minDuration";
    private static final String EXPECTING_POST_RENEWAL_EXPIRY_OF = "Expecting post-renewal expiry of {}";
    private static final String TIME_MOVED_YET_SEEMED_TO_STOP = "Time moved, yet seemed to stop";
    private static final String AS_TWERE_A_SPINNING_TOP = "As 'twere a spinning-top";

    public static void main(String... args) {
        new ContractAutoExpirySpecs().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                renewsUsingContractFundsIfNoAutoRenewAccount(),
                renewalFeeDistributedToStakingAccounts(),
                renewsUsingAutoRenewAccountIfSet(),
                chargesContractFundsWhenAutoRenewAccountHasZeroBalance(),
                storageExpiryWorksAtTheExpectedInterval(),
                autoRenewWorksAsExpected(),
                autoRenewInGracePeriodIfEnoughBalance(),
                storageRentChargedOnlyAfterInitialFreePeriodIsComplete(),
                renewalWithCustomFeesWorks(),
                receiverSigReqBypassedForTreasuryAtEndOfGracePeriod(),
                verifyNonFungibleTokenTransferredBackToTreasuryWithoutCharging());
    }

    private HapiSpec renewalWithCustomFeesWorks() {
        final var minimalLifetime = 4;
        final var aFungibleToken = "aFT";
        final var bFungibleToken = "bFT";
        final var nonFungibleToken = "NFT";
        final var aFungibleAmount = 1_000_000L;
        final var bFungibleAmount = 666L;
        final var feeDenom = "feeDenom";

        return defaultHapiSpec("RenewalWithCustomFeesWorks")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY).receiverSigRequired(true),
                        cryptoCreate(HTS_COLLECTOR).maxAutomaticTokenAssociations(5),
                        tokenCreate(feeDenom).treasury(TOKEN_TREASURY),
                        tokenAssociate(HTS_COLLECTOR, feeDenom),
                        tokenCreate(aFungibleToken)
                                .initialSupply(aFungibleAmount)
                                .withCustom(fixedHtsFee(1, feeDenom, HTS_COLLECTOR))
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(bFungibleToken)
                                .initialSupply(bFungibleAmount)
                                .withCustom(fixedHbarFee(1, HTS_COLLECTOR))
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(HTS_COLLECTOR, aFungibleToken, bFungibleToken),
                        tokenCreate(nonFungibleToken)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, aFungibleToken), HTS_COLLECTOR))
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        mintToken(
                                nonFungibleToken,
                                List.of(
                                        ByteString.copyFromUtf8(TIME_MOVED_YET_SEEMED_TO_STOP),
                                        ByteString.copyFromUtf8(AS_TWERE_A_SPINNING_TOP))),
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        contractCreate(CONTRACT_TO_RENEW, new BigInteger("63"))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .balance(0)
                                .autoRenewSecs(minimalLifetime),
                        tokenAssociate(CONTRACT_TO_RENEW, List.of(aFungibleToken, bFungibleToken, nonFungibleToken)),
                        cryptoTransfer(
                                moving(aFungibleAmount, aFungibleToken).between(TOKEN_TREASURY, CONTRACT_TO_RENEW),
                                moving(bFungibleAmount, bFungibleToken).between(TOKEN_TREASURY, CONTRACT_TO_RENEW),
                                movingUnique(nonFungibleToken, 1L, 2L).between(TOKEN_TREASURY, CONTRACT_TO_RENEW)),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(2_000L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        // Now the contract is gone
                        getContractInfo(CONTRACT_TO_RENEW).hasCostAnswerPrecheck(INVALID_CONTRACT_ID),
                        // And the fungible units were returned to the treasury
                        getAccountBalance(TOKEN_TREASURY)
                                .hasTokenBalance(aFungibleToken, aFungibleAmount)
                                .hasTokenBalance(bFungibleToken, bFungibleAmount)
                                .hasTokenBalance(nonFungibleToken, 2),
                        // And the NFTs are now owned by the treasury
                        getTokenNftInfo(nonFungibleToken, 1L).hasAccountID(TOKEN_TREASURY),
                        getTokenNftInfo(nonFungibleToken, 2L).hasAccountID(TOKEN_TREASURY));
    }

    private HapiSpec receiverSigReqBypassedForTreasuryAtEndOfGracePeriod() {
        final var minimalLifetime = 4;
        final var aFungibleToken = "aFT";
        final var nonFungibleToken = "NFT";
        final var aFungibleAmount = 1_000_000L;

        return defaultHapiSpec("receiverSigReqBypassedForTreasuryAtEndOfGracePeriod")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY).receiverSigRequired(true),
                        tokenCreate(aFungibleToken)
                                .initialSupply(aFungibleAmount)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(nonFungibleToken)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        mintToken(
                                nonFungibleToken,
                                List.of(
                                        ByteString.copyFromUtf8("My lovely NFT 1"),
                                        ByteString.copyFromUtf8("My lovely NFT 2"))),
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        contractCreate(CONTRACT_TO_RENEW, new BigInteger("63"))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .balance(0)
                                .autoRenewSecs(minimalLifetime),
                        tokenAssociate(CONTRACT_TO_RENEW, List.of(aFungibleToken, nonFungibleToken)),
                        cryptoTransfer(
                                moving(aFungibleAmount, aFungibleToken).between(TOKEN_TREASURY, CONTRACT_TO_RENEW),
                                movingUnique(nonFungibleToken, 1L, 2L).between(TOKEN_TREASURY, CONTRACT_TO_RENEW)),
                        getAccountBalance(CONTRACT_TO_RENEW)
                                .hasTokenBalance(aFungibleToken, aFungibleAmount)
                                .hasTokenBalance(nonFungibleToken, 2),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(aFungibleToken, 0),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(0),
                        /* sleep past the expiration:
                         * (minimalLifetimeMillis * 1 second) + 500 ms (500 ms for extra time)
                         */
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(2_000L), // wait for the record stream file to close
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        // Now the contract is gone
                        getContractInfo(CONTRACT_TO_RENEW)
                                .hasCostAnswerPrecheck(INVALID_CONTRACT_ID)
                                .logged(),
                        // And the fungible units were returned to the treasury
                        getAccountBalance(TOKEN_TREASURY)
                                .hasTokenBalance(aFungibleToken, aFungibleAmount)
                                .hasTokenBalance(nonFungibleToken, 2)
                                .logged(),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(2),
                        getAccountInfo(TOKEN_TREASURY).logged(),
                        // And the NFTs now list the treasury as the owner
                        getTokenNftInfo(nonFungibleToken, 1L).hasAccountID(TOKEN_TREASURY),
                        getTokenNftInfo(nonFungibleToken, 2L).hasAccountID(TOKEN_TREASURY));
    }

    private HapiSpec validateStreams() {
        return defaultHapiSpec("validateStreams")
                .given()
                .when()
                .then(sourcing(() -> assertEventuallyPasses(new ExpiryRecordsValidator(), Duration.ofMillis(2_100))));
    }

    private HapiSpec storageRentChargedOnlyAfterInitialFreePeriodIsComplete() {
        final var contract = "User";
        final var gasToOffer = 1_000_000;
        final var minimalLifetime = 4;
        final var initBalance = 100 * ONE_HBAR;

        final var renewalFeeWithoutStorage = new AtomicLong();
        final var renewalFeeWithStorage = new AtomicLong();

        return defaultHapiSpec("storageRentChargedOnlyAfterInitialFreePeriodIsComplete")
                .given(
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(contract),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(initBalance),
                        /* This contract has 0 key/value mappings at creation */
                        contractCreate(contract).autoRenewSecs(minimalLifetime).autoRenewAccountId(AUTO_RENEW_ACCOUNT),

                        /* Now we update the per-contract limit to 10 mappings */
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(
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
                        getContractInfo(contract)
                                .has(contractWith().numKvPairs(8))
                                .logged(),
                        getAccountBalance(AUTO_RENEW_ACCOUNT)
                                .hasTinyBars(initBalance)
                                .logged(),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(2_000L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        withOpContext((spec, opLog) -> {
                            final var lookup =
                                    getAccountBalance(AUTO_RENEW_ACCOUNT).logged();
                            allRunFor(spec, lookup);
                            renewalFeeWithoutStorage.set(initBalance
                                    - lookup.getResponse()
                                            .getCryptogetAccountBalance()
                                            .getBalance());
                            opLog.info("Renewal fee without storage: {}", renewalFeeWithoutStorage.get());
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
                        withOpContext((spec, opLog) -> {
                            final var lookup =
                                    getAccountBalance(AUTO_RENEW_ACCOUNT).logged();
                            allRunFor(spec, lookup);
                            renewalFeeWithStorage.set(initBalance
                                    - lookup.getResponse()
                                            .getCryptogetAccountBalance()
                                            .getBalance()
                                    - renewalFeeWithoutStorage.get());
                            opLog.info("Renewal fee with storage: {}", renewalFeeWithStorage.get());
                            assertTrue(renewalFeeWithStorage.get() > renewalFeeWithoutStorage.get());
                        }),
                        overriding(INDIVIDUAL_KV_LIMIT_PROP, String.valueOf(163_840)));
    }

    private HapiSpec autoRenewWorksAsExpected() {
        final var minimalLifetime = 3;

        return defaultHapiSpec("autoRenewWorksAsExpected")
                .given(
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, minimalLifetime),
                        uploadInitCode(CONTRACT_TO_RENEW),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        getAccountBalance(AUTO_RENEW_ACCOUNT).logged(),
                        contractCreate(CONTRACT_TO_RENEW, BigInteger.valueOf(63))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .autoRenewSecs(minimalLifetime)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                .balance(0L)
                                .via(CREATION),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        /*
                         * Contract is expired and doesn't have enough to auto-renew.
                         * But it is in grace period , so not deleted
                         */
                        getContractInfo(CONTRACT_TO_RENEW)
                                .has(contractWith().isNotDeleted())
                                .logged(),
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
                        getContractInfo(CONTRACT_TO_RENEW)
                                .has(contractWith().isNotDeleted())
                                .logged(),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        getContractInfo(CONTRACT_TO_RENEW)
                                .has(contractWith().isDeleted())
                                .logged(),
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, DEFAULT_MIN_AUTO_RENEW_PERIOD));
    }

    private HapiSpec autoRenewInGracePeriodIfEnoughBalance() {
        final var minimalLifetime = 3;
        final var expectedExpiryPostRenew = new AtomicLong();
        final var currentExpiry = new AtomicLong();

        return defaultHapiSpec("autoRenewInGracePeriodIfEnoughBalance")
                .given(
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, minimalLifetime),
                        uploadInitCode(CONTRACT_TO_RENEW),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        getAccountBalance(AUTO_RENEW_ACCOUNT).logged(),
                        contractCreate(CONTRACT_TO_RENEW, BigInteger.valueOf(63))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .autoRenewSecs(minimalLifetime)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                .balance(0L)
                                .via(CREATION),
                        withOpContext((spec, opLog) -> {
                            final var lookup = getTxnRecord(CREATION);
                            allRunFor(spec, lookup);

                            final var responseRecord = lookup.getResponseRecord();
                            final var birth =
                                    responseRecord.getConsensusTimestamp().getSeconds();
                            currentExpiry.set(birth + minimalLifetime);
                            expectedExpiryPostRenew.set(birth + minimalLifetime + minimalLifetime + minimalLifetime);
                            opLog.info(
                                    "Expecting post-renewal expiry of {}, current expiry" + " {}",
                                    expectedExpiryPostRenew.get(),
                                    currentExpiry.get());
                            final var info = getContractInfo(CONTRACT_TO_RENEW)
                                    .has(contractWith().isNotDeleted().approxExpiry(currentExpiry.get(), 1))
                                    .logged();

                            allRunFor(spec, info);
                        }),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        /*
                         * Funding for auto-renew account with enough balance. So when
                         * contract successfully auto-renews on next trigger
                         */
                        cryptoTransfer(tinyBarsFromTo(GENESIS, AUTO_RENEW_ACCOUNT, 100 * ONE_HBAR)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        getAccountBalance(AUTO_RENEW_ACCOUNT).logged(),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        assertionsHold((spec, opLog) -> {
                            final var lookup = getContractInfo(CONTRACT_TO_RENEW)
                                    .has(contractWith().approxExpiry(expectedExpiryPostRenew.get(), 2))
                                    .logged();

                            allRunFor(spec, lookup);
                        }),
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, DEFAULT_MIN_AUTO_RENEW_PERIOD));
    }

    private HapiSpec renewalFeeDistributedToStakingAccounts() {
        final var initBalance = ONE_HBAR;
        final var minimalLifetime = 3;
        final var standardLifetime = 7776000L;
        final var expectedExpiryPostRenew = new AtomicLong();

        return defaultHapiSpec("renewalFeeDistributedToStakingAccounts")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(
                                        "staking.fees.stakingRewardPercentage", "10",
                                        "staking.fees.nodeRewardPercentage", "20")),
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(CONTRACT_TO_RENEW),
                        contractCreate(CONTRACT_TO_RENEW, BigInteger.valueOf(63))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .autoRenewSecs(minimalLifetime)
                                .balance(initBalance)
                                .via(CREATION),
                        withOpContext((spec, opLog) -> {
                            final var lookup = getTxnRecord(CREATION);
                            allRunFor(spec, lookup);
                            final var responseRecord = lookup.getResponseRecord();
                            final var birth =
                                    responseRecord.getConsensusTimestamp().getSeconds();
                            expectedExpiryPostRenew.set(birth + minimalLifetime + standardLifetime);
                            opLog.info(EXPECTING_POST_RENEWAL_EXPIRY_OF, expectedExpiryPostRenew.get());
                        }),
                        contractUpdate(CONTRACT_TO_RENEW).newAutoRenew(7776000L),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(
                        balanceSnapshot("before", "0.0.3"),
                        balanceSnapshot("fundingBefore", "0.0.98"),
                        balanceSnapshot("stakingReward", "0.0.800"),
                        balanceSnapshot("nodeReward", "0.0.801"),

                        // Any transaction will do
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)).via("trigger"),
                        getTxnRecord("trigger").andAllChildRecords().logged())
                .then(
                        assertionsHold((spec, opLog) -> {
                            final var lookup = getContractInfo(CONTRACT_TO_RENEW)
                                    .has(contractWith().approxExpiry(expectedExpiryPostRenew.get(), 5))
                                    .logged();

                            allRunFor(spec, lookup);
                            final var balance = lookup.getResponse()
                                    .getContractGetInfo()
                                    .getContractInfo()
                                    .getBalance();
                            final var renewalFee = initBalance - balance;
                            final long stakingAccountFee = (long) (renewalFee * 0.1);
                            final long nodeAccountFee = (long) (renewalFee * 0.3);
                            final long fundingAccountFee = (long) (renewalFee * 0.7);
                            final var canonicalUsdFee = 0.026;
                            assertTinybarAmountIsApproxUsd(spec, canonicalUsdFee, renewalFee, 5.0);
                            getAccountBalance("0.0.98")
                                    .hasTinyBars(changeFromSnapshot("fundingBefore", fundingAccountFee))
                                    .logged();
                            getAccountBalance("0.0.800")
                                    .hasTinyBars(changeFromSnapshot("stakingReward", stakingAccountFee))
                                    .logged();
                            getAccountBalance("0.0.801")
                                    .hasTinyBars(changeFromSnapshot("nodeReward", nodeAccountFee))
                                    .logged();
                        }),
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, DEFAULT_MIN_AUTO_RENEW_PERIOD));
    }

    private HapiSpec chargesContractFundsWhenAutoRenewAccountHasZeroBalance() {
        final var initBalance = ONE_HBAR;
        final var minimalLifetime = 3;
        final var standardLifetime = 7776000L;
        final var expectedExpiryPostRenew = new AtomicLong();
        final var autoRenewAccountBalance = 0;

        return defaultHapiSpec("chargesContractFundsWhenAutoRenewAccountHasZeroBalance")
                .given(
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(CONTRACT_TO_RENEW),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance((long) autoRenewAccountBalance),
                        getAccountBalance(AUTO_RENEW_ACCOUNT).logged(),
                        contractCreate(CONTRACT_TO_RENEW, BigInteger.valueOf(63))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .autoRenewSecs(minimalLifetime)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                .balance(initBalance)
                                .via(CREATION),
                        withOpContext((spec, opLog) -> {
                            final var lookup = getTxnRecord(CREATION);
                            allRunFor(spec, lookup);
                            final var responseRecord = lookup.getResponseRecord();
                            final var birth =
                                    responseRecord.getConsensusTimestamp().getSeconds();
                            expectedExpiryPostRenew.set(birth + minimalLifetime + standardLifetime);
                            opLog.info(EXPECTING_POST_RENEWAL_EXPIRY_OF, expectedExpiryPostRenew.get());
                        }),
                        contractUpdate(CONTRACT_TO_RENEW).newAutoRenew(7776000L).via(UPDATE_TXN),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        getTxnRecord(UPDATE_TXN).logged())
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        assertionsHold((spec, opLog) -> {
                            final var lookupContract = getContractInfo(CONTRACT_TO_RENEW)
                                    .has(contractWith().approxExpiry(expectedExpiryPostRenew.get(), 5))
                                    .logged();
                            final var lookupAccount =
                                    getAccountBalance(AUTO_RENEW_ACCOUNT).logged();
                            allRunFor(spec, lookupContract, lookupAccount);

                            final var contractBalance = lookupContract
                                    .getResponse()
                                    .getContractGetInfo()
                                    .getContractInfo()
                                    .getBalance();
                            final var accountBalance = lookupAccount
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
                            assertTinybarAmountIsApproxUsd(spec, canonicalUsdFee, renewalFee, 5.0);
                        }),
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, DEFAULT_MIN_AUTO_RENEW_PERIOD));
    }

    private HapiSpec renewsUsingAutoRenewAccountIfSet() {
        final var initBalance = ONE_HBAR;
        final var minimalLifetime = 3;
        final var standardLifetime = 7776000L;
        final var expectedExpiryPostRenew = new AtomicLong();

        return defaultHapiSpec("renewsUsingAutoRenewAccountIfSet")
                .given(
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(CONTRACT_TO_RENEW),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(initBalance),
                        getAccountBalance(AUTO_RENEW_ACCOUNT).logged(),
                        contractCreate(CONTRACT_TO_RENEW, BigInteger.valueOf(63))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .autoRenewSecs(minimalLifetime)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                .balance(initBalance)
                                .via(CREATION),
                        withOpContext((spec, opLog) -> {
                            final var lookup = getTxnRecord(CREATION);
                            allRunFor(spec, lookup);
                            final var responseRecord = lookup.getResponseRecord();
                            final var birth =
                                    responseRecord.getConsensusTimestamp().getSeconds();
                            expectedExpiryPostRenew.set(birth + minimalLifetime + standardLifetime);
                            opLog.info(EXPECTING_POST_RENEWAL_EXPIRY_OF, expectedExpiryPostRenew.get());
                        }),
                        contractUpdate(CONTRACT_TO_RENEW).newAutoRenew(7776000L).via(UPDATE_TXN),
                        sleepFor(minimalLifetime * 1_000L + 500L),
                        getTxnRecord(UPDATE_TXN).logged())
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        assertionsHold((spec, opLog) -> {
                            final var lookupContract = getContractInfo(CONTRACT_TO_RENEW)
                                    .has(contractWith().approxExpiry(expectedExpiryPostRenew.get(), 5))
                                    .logged();
                            final var lookupAccount =
                                    getAccountBalance(AUTO_RENEW_ACCOUNT).logged();
                            allRunFor(spec, lookupContract, lookupAccount);

                            final var contractBalance = lookupContract
                                    .getResponse()
                                    .getContractGetInfo()
                                    .getContractInfo()
                                    .getBalance();
                            final var accountBalance = lookupAccount
                                    .getResponse()
                                    .getCryptogetAccountBalance()
                                    .getBalance();
                            opLog.info(
                                    "AutoRenew account balance {}, contract balance {}",
                                    accountBalance,
                                    contractBalance);

                            assertEquals(initBalance, contractBalance);
                            assertTrue(accountBalance < initBalance);
                            final var renewalFee = initBalance - accountBalance;
                            opLog.info("Renewal fees actual {}", renewalFee);
                            final var canonicalUsdFee = 0.026;
                            assertTinybarAmountIsApproxUsd(spec, canonicalUsdFee, renewalFee, 5.0);
                        }),
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, DEFAULT_MIN_AUTO_RENEW_PERIOD));
    }

    private HapiSpec storageExpiryWorksAtTheExpectedInterval() {
        final var minimalLifetime = 4;
        final var aFungibleToken = "aFT";
        final var bFungibleToken = "bFT";
        final var cFungibleTokenWithCustomFees = "cFT";
        final var nonFungibleToken = "NFT";
        final var aFungibleAmount = 1_000_000L;
        final var bFungibleAmount = 666L;
        final var cFungibleAmount = 1230L;
        final var initBalance = ONE_HBAR;

        return defaultHapiSpec("StorageExpiryWorksAtTheExpectedInterval")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(initBalance),
                        cryptoCreate(FEE_COLLECTOR).balance(initBalance),
                        tokenCreate(aFungibleToken)
                                .initialSupply(aFungibleAmount)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(bFungibleToken)
                                .initialSupply(bFungibleAmount)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(cFungibleTokenWithCustomFees)
                                .initialSupply(cFungibleAmount)
                                .tokenSubType(TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(fixedHbarFee(100L, FEE_COLLECTOR)),
                        tokenCreate(nonFungibleToken)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        mintToken(
                                nonFungibleToken,
                                List.of(
                                        ByteString.copyFromUtf8(TIME_MOVED_YET_SEEMED_TO_STOP),
                                        ByteString.copyFromUtf8(AS_TWERE_A_SPINNING_TOP))),
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        contractCreate(CONTRACT_TO_RENEW, BigInteger.valueOf(63))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .balance(0)
                                .autoRenewSecs(minimalLifetime),
                        tokenAssociate(
                                CONTRACT_TO_RENEW,
                                List.of(
                                        aFungibleToken,
                                        bFungibleToken,
                                        cFungibleTokenWithCustomFees,
                                        nonFungibleToken)),
                        cryptoTransfer(
                                moving(aFungibleAmount, aFungibleToken).between(TOKEN_TREASURY, CONTRACT_TO_RENEW),
                                moving(bFungibleAmount, bFungibleToken).between(TOKEN_TREASURY, CONTRACT_TO_RENEW),
                                moving(cFungibleAmount, cFungibleTokenWithCustomFees)
                                        .between(TOKEN_TREASURY, CONTRACT_TO_RENEW),
                                movingUnique(nonFungibleToken, 1L, 2L).between(TOKEN_TREASURY, CONTRACT_TO_RENEW)),

                        // verify that token move was successful
                        getAccountBalance(CONTRACT_TO_RENEW)
                                .hasTokenBalance(aFungibleToken, aFungibleAmount)
                                .hasTokenBalance(bFungibleToken, bFungibleAmount)
                                .hasTokenBalance(cFungibleTokenWithCustomFees, cFungibleAmount),

                        /* sleep past the contract expiration:
                         * (minimalLifetimeMillis * 1 second) + 500 ms (500 ms for extra time)
                         */
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(
                        // run transactions so the contract can be deleted
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(2_000L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        // Now the contract is gone
                        getContractInfo(CONTRACT_TO_RENEW).hasCostAnswerPrecheck(INVALID_CONTRACT_ID),
                        // And the fungible units were returned to the treasury
                        getAccountBalance(TOKEN_TREASURY)
                                .hasTokenBalance(aFungibleToken, aFungibleAmount)
                                .hasTokenBalance(bFungibleToken, bFungibleAmount)
                                .hasTokenBalance(cFungibleTokenWithCustomFees, cFungibleAmount)
                                .hasTokenBalance(nonFungibleToken, 0),

                        // And the NFTs are now owned by the treasury
                        getTokenNftInfo(nonFungibleToken, 1L).hasAccountID(TOKEN_TREASURY),
                        getTokenNftInfo(nonFungibleToken, 2L).hasAccountID(TOKEN_TREASURY),
                        // And the account was not charged for the transfer
                        getAccountBalance(FEE_COLLECTOR).hasTinyBars(ONE_HBAR),
                        getAccountBalance(TOKEN_TREASURY).hasTinyBars(ONE_HBAR));
    }

    private HapiSpec verifyNonFungibleTokenTransferredBackToTreasuryWithoutCharging() {
        final var minimalLifetime = 4;
        final var nonFungibleToken = "NFT";
        final var initBalance = ONE_HBAR;
        final var collector = "collector";

        return defaultHapiSpec("verifyNonFungibleTokenTransferredBackToTreasuryWithoutCharging")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(initBalance),
                        cryptoCreate(collector).balance(initBalance),
                        tokenCreate(nonFungibleToken)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 15, fixedHbarFeeInheritingRoyaltyCollector(1), collector)),
                        mintToken(
                                nonFungibleToken,
                                List.of(
                                        ByteString.copyFromUtf8(TIME_MOVED_YET_SEEMED_TO_STOP),
                                        ByteString.copyFromUtf8(AS_TWERE_A_SPINNING_TOP))),
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        contractCreate(CONTRACT_TO_RENEW, BigInteger.valueOf(63))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .balance(0)
                                .autoRenewSecs(minimalLifetime),
                        tokenAssociate(CONTRACT_TO_RENEW, List.of(nonFungibleToken)),
                        cryptoTransfer(
                                movingUnique(nonFungibleToken, 1L, 2L).between(TOKEN_TREASURY, CONTRACT_TO_RENEW)),
                        sleepFor(minimalLifetime * 1_000L + 500L))
                .when(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        sleepFor(2_000L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        // Now the contract is gone
                        getContractInfo(CONTRACT_TO_RENEW).hasCostAnswerPrecheck(INVALID_CONTRACT_ID),
                        // And the fungible units were returned to the treasury
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nonFungibleToken, 0),
                        // And the NFTs are now owned by the treasury
                        getTokenNftInfo(nonFungibleToken, 1L).hasAccountID(TOKEN_TREASURY),
                        getTokenNftInfo(nonFungibleToken, 2L).hasAccountID(TOKEN_TREASURY),
                        getAccountBalance(collector).hasTinyBars(ONE_HBAR),
                        getAccountBalance(TOKEN_TREASURY).hasTinyBars(ONE_HBAR));
    }

    private HapiSpec renewsUsingContractFundsIfNoAutoRenewAccount() {
        final var initBalance = ONE_HBAR;
        final var minimalLifetime = 3;
        final var standardLifetime = 7776000L;
        final var expectedExpiryPostRenew = new AtomicLong();
        final var consTimeRepro = "ConsTimeRepro";
        final var failingCall = "FailingCall";
        final AtomicReference<Timestamp> parentConsTime = new AtomicReference<>();

        return defaultHapiSpec("RenewsUsingContractFundsIfNoAutoRenewAccount")
                .given(
                        uploadInitCode(consTimeRepro),
                        contractCreate(consTimeRepro),
                        createLargeFile(GENESIS, INIT_CODE, literalInitcodeFor(CONTRACT_TO_RENEW)),
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        uploadInitCode(CONTRACT_TO_RENEW),
                        contractCreate(CONTRACT_TO_RENEW, BigInteger.valueOf(63))
                                .gas(2_000_000)
                                .entityMemo("")
                                .bytecode(INIT_CODE)
                                .autoRenewSecs(minimalLifetime)
                                .balance(initBalance)
                                .via(CREATION),
                        withOpContext((spec, opLog) -> {
                            final var lookup = getTxnRecord(CREATION);
                            allRunFor(spec, lookup);
                            final var responseRecord = lookup.getResponseRecord();
                            final var birth =
                                    responseRecord.getConsensusTimestamp().getSeconds();
                            expectedExpiryPostRenew.set(birth + minimalLifetime + standardLifetime);
                            opLog.info(EXPECTING_POST_RENEWAL_EXPIRY_OF, expectedExpiryPostRenew.get());
                        }),
                        contractUpdate(CONTRACT_TO_RENEW).newAutoRenew(7776000L),
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
                                .exposingTo(failureRecord -> parentConsTime.set(failureRecord.getConsensusTimestamp())),
                        sourcing(() -> childRecordsCheck(
                                failingCall,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(ResponseCodeEnum.INSUFFICIENT_GAS)
                                        .consensusTimeImpliedByNonce(parentConsTime.get(), 1))),
                        assertionsHold((spec, opLog) -> {
                            final var lookup = getContractInfo(CONTRACT_TO_RENEW)
                                    .has(contractWith().approxExpiry(expectedExpiryPostRenew.get(), 5))
                                    .logged();
                            allRunFor(spec, lookup);
                            final var balance = lookup.getResponse()
                                    .getContractGetInfo()
                                    .getContractInfo()
                                    .getBalance();
                            final var renewalFee = initBalance - balance;
                            final var canonicalUsdFee = 0.026;
                            assertTinybarAmountIsApproxUsd(spec, canonicalUsdFee, renewalFee, 5.0);
                        }),
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, DEFAULT_MIN_AUTO_RENEW_PERIOD));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
