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

package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.junit.ContextRequirement.SYSTEM_ACCOUNT_BALANCES;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.includingDeduction;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class RecordCreationSuite {
    private static final long SLEEP_MS = 1_000L;
    private static final String BEFORE = "before";
    private static final String FUNDING_BEFORE = "fundingBefore";
    private static final String STAKING_REWARD1 = "stakingReward";
    private static final String NODE_REWARD1 = "nodeReward";
    private static final String FOR_ACCOUNT_FUNDING = "0.0.98";
    private static final String FOR_ACCOUNT_STAKING_REWARDS = "0.0.800";
    private static final String FOR_ACCOUNT_NODE_REWARD = "0.0.801";
    private static final String PAYER = "payer";
    private static final String THIS_IS_OK_IT_S_FINE_IT_S_WHATEVER = "This is ok, it's fine, it's whatever.";
    private static final String TO_ACCOUNT = "0.0.3";
    private static final String TXN_ID = "txnId";
    public static final String STAKING_FEES_NODE_REWARD_PERCENTAGE = "staking.fees.nodeRewardPercentage";
    public static final String STAKING_FEES_STAKING_REWARD_PERCENTAGE = "staking.fees.stakingRewardPercentage";

    @LeakyHapiTest(requirement = SYSTEM_ACCOUNT_BALANCES)
    final Stream<DynamicTest> submittingNodeStillPaidIfServiceFeesOmitted() {
        final String comfortingMemo = THIS_IS_OK_IT_S_FINE_IT_S_WHATEVER;
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

        return hapiTest(
                cryptoTransfer(tinyBarsFromTo(GENESIS, TO_ACCOUNT, ONE_HBAR)).payingWith(GENESIS),
                cryptoCreate(PAYER),
                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                        .memo(comfortingMemo)
                        .exposingFeesTo(feeObs)
                        .payingWith(PAYER),
                balanceSnapshot(BEFORE, TO_ACCOUNT),
                balanceSnapshot(FUNDING_BEFORE, FOR_ACCOUNT_FUNDING),
                balanceSnapshot(STAKING_REWARD1, FOR_ACCOUNT_STAKING_REWARDS),
                balanceSnapshot(NODE_REWARD1, FOR_ACCOUNT_NODE_REWARD),
                sourcing(() -> cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                        .memo(comfortingMemo)
                        .fee(feeObs.get().networkFee() + feeObs.get().nodeFee())
                        .payingWith(PAYER)
                        .via(TXN_ID)
                        .hasKnownStatus(INSUFFICIENT_TX_FEE)
                        .logged()),
                sourcing(() -> getAccountBalance(TO_ACCOUNT)
                        .hasTinyBars(changeFromSnapshot(BEFORE, +feeObs.get().nodeFee()))
                        .logged()),
                sourcing(() -> getAccountBalance(FOR_ACCOUNT_FUNDING)
                        .hasTinyBars(changeFromSnapshot(
                                FUNDING_BEFORE, (long) (+feeObs.get().networkFee() * 0.8 + 1)))
                        .logged()),
                sourcing(() -> getAccountBalance(FOR_ACCOUNT_STAKING_REWARDS)
                        .hasTinyBars(changeFromSnapshot(
                                STAKING_REWARD1, (long) (+feeObs.get().networkFee() * 0.1)))
                        .logged()),
                sourcing(() -> getAccountBalance(FOR_ACCOUNT_NODE_REWARD)
                        .hasTinyBars(changeFromSnapshot(
                                NODE_REWARD1, (long) (+feeObs.get().networkFee() * 0.1)))
                        .logged()),
                sourcing(() -> getTxnRecord(TXN_ID)
                        .assertingNothingAboutHashes()
                        .hasPriority(recordWith()
                                .transfers(includingDeduction(
                                        PAYER,
                                        feeObs.get().networkFee() + feeObs.get().nodeFee()))
                                .status(INSUFFICIENT_TX_FEE))
                        .logged()));
    }

    @LeakyHapiTest(requirement = SYSTEM_ACCOUNT_BALANCES)
    final Stream<DynamicTest> submittingNodeChargedNetworkFeeForLackOfDueDiligence() {
        final String comfortingMemo = THIS_IS_OK_IT_S_FINE_IT_S_WHATEVER;
        final String disquietingMemo = "\u0000his is ok, it's fine, it's whatever.";
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

        return hapiTest(
                cryptoTransfer(tinyBarsFromTo(GENESIS, TO_ACCOUNT, ONE_HBAR)).payingWith(GENESIS),
                cryptoCreate(PAYER),
                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                        .memo(comfortingMemo)
                        .exposingFeesTo(feeObs)
                        .payingWith(PAYER),
                usableTxnIdNamed(TXN_ID).payerId(PAYER),
                balanceSnapshot(BEFORE, TO_ACCOUNT),
                balanceSnapshot(FUNDING_BEFORE, FOR_ACCOUNT_FUNDING),
                balanceSnapshot(STAKING_REWARD1, FOR_ACCOUNT_STAKING_REWARDS),
                balanceSnapshot(NODE_REWARD1, FOR_ACCOUNT_NODE_REWARD),
                uncheckedSubmit(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .memo(disquietingMemo)
                                .payingWith(PAYER)
                                .txnId(TXN_ID))
                        .payingWith(GENESIS),
                sleepFor(SLEEP_MS),
                sourcing(() -> getAccountBalance(TO_ACCOUNT)
                        .hasTinyBars(changeFromSnapshot(BEFORE, -feeObs.get().networkFee()))),
                sourcing(() -> getAccountBalance(FOR_ACCOUNT_FUNDING)
                        .hasTinyBars(changeFromSnapshot(
                                FUNDING_BEFORE, (long) (+feeObs.get().networkFee() * 0.8 + 1)))
                        .logged()),
                sourcing(() -> getAccountBalance(FOR_ACCOUNT_STAKING_REWARDS)
                        .hasTinyBars(changeFromSnapshot(
                                STAKING_REWARD1, (long) (+feeObs.get().networkFee() * 0.1)))
                        .logged()),
                sourcing(() -> getAccountBalance(FOR_ACCOUNT_NODE_REWARD)
                        .hasTinyBars(changeFromSnapshot(
                                NODE_REWARD1, (long) (+feeObs.get().networkFee() * 0.1)))
                        .logged()),
                sourcing(() -> getTxnRecord(TXN_ID)
                        .assertingNothingAboutHashes()
                        .hasPriority(recordWith()
                                .transfers(includingDeduction(
                                        () -> 3L, feeObs.get().networkFee()))
                                .status(INVALID_ZERO_BYTE_IN_STRING))
                        .logged()));
    }

    @LeakyHapiTest(requirement = SYSTEM_ACCOUNT_BALANCES)
    final Stream<DynamicTest> submittingNodeChargedNetworkFeeForIgnoringPayerUnwillingness() {
        final String comfortingMemo = THIS_IS_OK_IT_S_FINE_IT_S_WHATEVER;
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

        return hapiTest(
                cryptoTransfer(tinyBarsFromTo(GENESIS, TO_ACCOUNT, ONE_HBAR)).payingWith(GENESIS),
                cryptoCreate(PAYER),
                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                        .memo(comfortingMemo)
                        .exposingFeesTo(feeObs)
                        .payingWith(PAYER),
                usableTxnIdNamed(TXN_ID).payerId(PAYER),
                balanceSnapshot(BEFORE, TO_ACCOUNT),
                balanceSnapshot(FUNDING_BEFORE, FOR_ACCOUNT_FUNDING),
                balanceSnapshot(STAKING_REWARD1, FOR_ACCOUNT_STAKING_REWARDS),
                balanceSnapshot(NODE_REWARD1, FOR_ACCOUNT_NODE_REWARD),
                sourcing(() -> uncheckedSubmit(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .memo(comfortingMemo)
                                .fee(feeObs.get().networkFee() - 1L)
                                .payingWith(PAYER)
                                .txnId(TXN_ID))
                        .payingWith(GENESIS)),
                sleepFor(SLEEP_MS),
                sourcing(() -> getAccountBalance(TO_ACCOUNT)
                        .hasTinyBars(changeFromSnapshot(BEFORE, -feeObs.get().networkFee()))),
                sourcing(() -> getAccountBalance(FOR_ACCOUNT_FUNDING)
                        .hasTinyBars(changeFromSnapshot(
                                FUNDING_BEFORE, (long) (+feeObs.get().networkFee() * 0.8 + 1)))
                        .logged()),
                sourcing(() -> getAccountBalance(FOR_ACCOUNT_STAKING_REWARDS)
                        .hasTinyBars(changeFromSnapshot(
                                STAKING_REWARD1, (long) (+feeObs.get().networkFee() * 0.1)))
                        .logged()),
                sourcing(() -> getAccountBalance(FOR_ACCOUNT_NODE_REWARD)
                        .hasTinyBars(changeFromSnapshot(
                                NODE_REWARD1, (long) (+feeObs.get().networkFee() * 0.1)))
                        .logged()),
                sourcing(() -> getTxnRecord(TXN_ID)
                        .assertingNothingAboutHashes()
                        .hasPriority(recordWith()
                                .transfers(includingDeduction(
                                        () -> 3L, feeObs.get().networkFee()))
                                .status(INSUFFICIENT_TX_FEE))
                        .logged()));
    }

    @HapiTest
    final Stream<DynamicTest> accountsGetPayerRecordsIfSoConfigured() {
        final var txn = "ofRecord";

        return defaultHapiSpec("AccountsGetPayerRecordsIfSoConfigured")
                .given(cryptoCreate(PAYER))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_000L))
                        .payingWith(PAYER)
                        .via(txn))
                .then(getAccountRecords(PAYER).has(inOrder(recordWith().txnId(txn))));
    }
}
