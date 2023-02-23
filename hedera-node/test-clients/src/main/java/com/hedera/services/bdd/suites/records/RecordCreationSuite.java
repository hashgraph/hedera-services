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
        final String comfortingMemo = "This is ok, it's fine, it's whatever.";
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

        return defaultHapiSpec("submittingNodeStillPaidIfServiceFeesOmitted")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.3", ONE_HBAR))
                                .payingWith(GENESIS),
                        cryptoCreate("payer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .memo(comfortingMemo)
                                .exposingFeesTo(feeObs)
                                .payingWith("payer"))
                .when(
                        balanceSnapshot("before", "0.0.3"),
                        balanceSnapshot("fundingBefore", "0.0.98"),
                        balanceSnapshot("stakingReward", "0.0.800"),
                        balanceSnapshot("nodeReward", "0.0.801"),
                        sourcing(() -> cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .memo(comfortingMemo)
                                .fee(feeObs.get().getNetworkFee() + feeObs.get().getNodeFee())
                                .payingWith("payer")
                                .via("txnId")
                                .hasKnownStatus(INSUFFICIENT_TX_FEE)
                                .logged()))
                .then(
                        sourcing(() -> getAccountBalance("0.0.3")
                                .hasTinyBars(changeFromSnapshot(
                                        "before", +feeObs.get().getNodeFee()))
                                .logged()),
                        sourcing(() -> getAccountBalance("0.0.98")
                                .hasTinyBars(changeFromSnapshot(
                                        "fundingBefore", (long) (+feeObs.get().getNetworkFee() * 0.8 + 1)))
                                .logged()),
                        sourcing(() -> getAccountBalance("0.0.800")
                                .hasTinyBars(changeFromSnapshot(
                                        "stakingReward", (long) (+feeObs.get().getNetworkFee() * 0.1)))
                                .logged()),
                        sourcing(() -> getAccountBalance("0.0.801")
                                .hasTinyBars(changeFromSnapshot(
                                        "nodeReward", (long) (+feeObs.get().getNetworkFee() * 0.1)))
                                .logged()),
                        sourcing(() -> getTxnRecord("txnId")
                                .assertingNothingAboutHashes()
                                .hasPriority(recordWith()
                                        .transfers(includingDeduction(
                                                "payer",
                                                feeObs.get().getNetworkFee()
                                                        + feeObs.get().getNodeFee()))
                                        .status(INSUFFICIENT_TX_FEE))
                                .logged()));
    }

    private HapiSpec submittingNodeChargedNetworkFeeForLackOfDueDiligence() {
        final String comfortingMemo = "This is ok, it's fine, it's whatever.";
        final String disquietingMemo = "\u0000his is ok, it's fine, it's whatever.";
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

        return defaultHapiSpec("SubmittingNodeChargedNetworkFeeForLackOfDueDiligence")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.3", ONE_HBAR))
                                .payingWith(GENESIS),
                        cryptoCreate("payer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .memo(comfortingMemo)
                                .exposingFeesTo(feeObs)
                                .payingWith("payer"),
                        usableTxnIdNamed("txnId").payerId("payer"))
                .when(
                        balanceSnapshot("before", "0.0.3"),
                        balanceSnapshot("fundingBefore", "0.0.98"),
                        balanceSnapshot("stakingReward", "0.0.800"),
                        balanceSnapshot("nodeReward", "0.0.801"),
                        uncheckedSubmit(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                        .memo(disquietingMemo)
                                        .payingWith("payer")
                                        .txnId("txnId"))
                                .payingWith(GENESIS),
                        sleepFor(SLEEP_MS))
                .then(
                        sourcing(() -> getAccountBalance("0.0.3")
                                .hasTinyBars(changeFromSnapshot(
                                        "before", -feeObs.get().getNetworkFee()))),
                        sourcing(() -> getAccountBalance("0.0.98")
                                .hasTinyBars(changeFromSnapshot(
                                        "fundingBefore", (long) (+feeObs.get().getNetworkFee() * 0.8 + 1)))
                                .logged()),
                        sourcing(() -> getAccountBalance("0.0.800")
                                .hasTinyBars(changeFromSnapshot(
                                        "stakingReward", (long) (+feeObs.get().getNetworkFee() * 0.1)))
                                .logged()),
                        sourcing(() -> getAccountBalance("0.0.801")
                                .hasTinyBars(changeFromSnapshot(
                                        "nodeReward", (long) (+feeObs.get().getNetworkFee() * 0.1)))
                                .logged()),
                        sourcing(() -> getTxnRecord("txnId")
                                .assertingNothingAboutHashes()
                                .hasPriority(recordWith()
                                        .transfers(includingDeduction(
                                                () -> 3L, feeObs.get().getNetworkFee()))
                                        .status(INVALID_ZERO_BYTE_IN_STRING))
                                .logged()));
    }

    private HapiSpec submittingNodeChargedNetworkFeeForIgnoringPayerUnwillingness() {
        final String comfortingMemo = "This is ok, it's fine, it's whatever.";
        final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

        return defaultHapiSpec("SubmittingNodeChargedNetworkFeeForIgnoringPayerUnwillingness")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.3", ONE_HBAR))
                                .payingWith(GENESIS),
                        cryptoCreate("payer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                .memo(comfortingMemo)
                                .exposingFeesTo(feeObs)
                                .payingWith("payer"),
                        usableTxnIdNamed("txnId").payerId("payer"))
                .when(
                        balanceSnapshot("before", "0.0.3"),
                        balanceSnapshot("fundingBefore", "0.0.98"),
                        balanceSnapshot("stakingReward", "0.0.800"),
                        balanceSnapshot("nodeReward", "0.0.801"),
                        sourcing(() -> uncheckedSubmit(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
                                        .memo(comfortingMemo)
                                        .fee(feeObs.get().getNetworkFee() - 1L)
                                        .payingWith("payer")
                                        .txnId("txnId"))
                                .payingWith(GENESIS)),
                        sleepFor(SLEEP_MS))
                .then(
                        sourcing(() -> getAccountBalance("0.0.3")
                                .hasTinyBars(changeFromSnapshot(
                                        "before", -feeObs.get().getNetworkFee()))),
                        sourcing(() -> getAccountBalance("0.0.98")
                                .hasTinyBars(changeFromSnapshot(
                                        "fundingBefore", (long) (+feeObs.get().getNetworkFee() * 0.8 + 1)))
                                .logged()),
                        sourcing(() -> getAccountBalance("0.0.800")
                                .hasTinyBars(changeFromSnapshot(
                                        "stakingReward", (long) (+feeObs.get().getNetworkFee() * 0.1)))
                                .logged()),
                        sourcing(() -> getAccountBalance("0.0.801")
                                .hasTinyBars(changeFromSnapshot(
                                        "nodeReward", (long) (+feeObs.get().getNetworkFee() * 0.1)))
                                .logged()),
                        sourcing(() -> getTxnRecord("txnId")
                                .assertingNothingAboutHashes()
                                .hasPriority(recordWith()
                                        .transfers(includingDeduction(
                                                () -> 3L, feeObs.get().getNetworkFee()))
                                        .status(INSUFFICIENT_TX_FEE))
                                .logged()));
    }

    private HapiSpec payerRecordCreationSanityChecks() {
        return defaultHapiSpec("PayerRecordCreationSanityChecks")
                .given(cryptoCreate("payer"))
                .when(
                        createTopic("ofGeneralInterest").payingWith("payer"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_000L)).payingWith("payer"),
                        submitMessageTo("ofGeneralInterest").message("I say!").payingWith("payer"))
                .then(assertionsHold((spec, opLog) -> {
                    final var payerId = spec.registry().getAccountID("payer");
                    final var subOp = getAccountRecords("payer").logged();
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
                .given(cryptoCreate("payer"))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_000L))
                        .payingWith("payer")
                        .via(txn))
                .then(getAccountRecords("payer").has(inOrder(recordWith().txnId(txn))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
