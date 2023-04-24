/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.includingDeduction;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecordCreationSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(RecordCreationSuite.class);

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

    public static void main(String... args) {
        new RecordCreationSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                payerRecordCreationSanityChecks(),
                accountsGetPayerRecordsIfSoConfigured(),
                submittingNodeChargedNetworkFeeForLackOfDueDiligence(),
                submittingNodeChargedNetworkFeeForIgnoringPayerUnwillingness(),
                submittingNodeStillPaidIfServiceFeesOmitted());
    }

    private HapiSpec submittingNodeStillPaidIfServiceFeesOmitted() {
        final String comfortingMemo = THIS_IS_OK_IT_S_FINE_IT_S_WHATEVER;
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

        return defaultHapiSpec("submittingNodeStillPaidIfServiceFeesOmitted")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, TO_ACCOUNT, ONE_HBAR))
                                .payingWith(GENESIS),
                        cryptoCreate(PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .memo(comfortingMemo)
                                .exposingFeesTo(feeObs)
                                .payingWith(PAYER))
                .when(
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
                                .logged()))
                .then(
                        sourcing(() -> getAccountBalance(TO_ACCOUNT)
                                .hasTinyBars(
                                        changeFromSnapshot(BEFORE, +feeObs.get().nodeFee()))
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
                                                feeObs.get().networkFee()
                                                        + feeObs.get().nodeFee()))
                                        .status(INSUFFICIENT_TX_FEE))
                                .logged()));
    }

    private HapiSpec submittingNodeChargedNetworkFeeForLackOfDueDiligence() {
        final String comfortingMemo = THIS_IS_OK_IT_S_FINE_IT_S_WHATEVER;
        final String disquietingMemo = "\u0000his is ok, it's fine, it's whatever.";
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

        return defaultHapiSpec("SubmittingNodeChargedNetworkFeeForLackOfDueDiligence")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, TO_ACCOUNT, ONE_HBAR))
                                .payingWith(GENESIS),
                        cryptoCreate(PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .memo(comfortingMemo)
                                .exposingFeesTo(feeObs)
                                .payingWith(PAYER),
                        usableTxnIdNamed(TXN_ID).payerId(PAYER))
                .when(
                        balanceSnapshot(BEFORE, TO_ACCOUNT),
                        balanceSnapshot(FUNDING_BEFORE, FOR_ACCOUNT_FUNDING),
                        balanceSnapshot(STAKING_REWARD1, FOR_ACCOUNT_STAKING_REWARDS),
                        balanceSnapshot(NODE_REWARD1, FOR_ACCOUNT_NODE_REWARD),
                        uncheckedSubmit(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                        .memo(disquietingMemo)
                                        .payingWith(PAYER)
                                        .txnId(TXN_ID))
                                .payingWith(GENESIS),
                        sleepFor(SLEEP_MS))
                .then(
                        sourcing(() -> getAccountBalance(TO_ACCOUNT)
                                .hasTinyBars(
                                        changeFromSnapshot(BEFORE, -feeObs.get().networkFee()))),
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

    private HapiSpec submittingNodeChargedNetworkFeeForIgnoringPayerUnwillingness() {
        final String comfortingMemo = THIS_IS_OK_IT_S_FINE_IT_S_WHATEVER;
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

        return defaultHapiSpec("SubmittingNodeChargedNetworkFeeForIgnoringPayerUnwillingness")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, TO_ACCOUNT, ONE_HBAR))
                                .payingWith(GENESIS),
                        cryptoCreate(PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .memo(comfortingMemo)
                                .exposingFeesTo(feeObs)
                                .payingWith(PAYER),
                        usableTxnIdNamed(TXN_ID).payerId(PAYER))
                .when(
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
                        sleepFor(SLEEP_MS))
                .then(
                        sourcing(() -> getAccountBalance(TO_ACCOUNT)
                                .hasTinyBars(
                                        changeFromSnapshot(BEFORE, -feeObs.get().networkFee()))),
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

    private HapiSpec payerRecordCreationSanityChecks() {
        return defaultHapiSpec("PayerRecordCreationSanityChecks")
                .given(cryptoCreate(PAYER))
                .when(
                        createTopic("ofGeneralInterest").payingWith(PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_000L)).payingWith(PAYER),
                        submitMessageTo("ofGeneralInterest").message("I say!").payingWith(PAYER))
                .then(assertionsHold((spec, opLog) -> {
                    final var payerId = spec.registry().getAccountID(PAYER);
                    final var subOp = getAccountRecords(PAYER).logged();
                    allRunFor(spec, subOp);
                    final var records =
                            subOp.getResponse().getCryptoGetAccountRecords().getRecordsList();
                    assertEquals(3, records.size());
                    for (var record : records) {
                        assertEquals(record.getTransactionFee(), -netChangeIn(record, payerId));
                    }
                }));
    }

    private long netChangeIn(TransactionRecord record, AccountID id) {
        return record.getTransferList().getAccountAmountsList().stream()
                .filter(aa -> id.equals(aa.getAccountID()))
                .mapToLong(AccountAmount::getAmount)
                .sum();
    }

    private HapiSpec accountsGetPayerRecordsIfSoConfigured() {
        final var txn = "ofRecord";

        return defaultHapiSpec("AccountsGetPayerRecordsIfSoConfigured")
                .given(cryptoCreate(PAYER))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_000L))
                        .payingWith(PAYER)
                        .via(txn))
                .then(getAccountRecords(PAYER).has(inOrder(recordWith().txnId(txn))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
