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

package com.hedera.services.bdd.spec.queries.meta;

import static com.hedera.services.bdd.spec.queries.QueryUtils.txnReceiptQueryFor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.Optional;

public class HapiGetReceipt extends HapiQueryOp<HapiGetReceipt> {
    static final Logger log = LogManager.getLogger(HapiGetReceipt.class);

    String txn;
    boolean forgetOp = false;
    boolean requestDuplicates = false;
    boolean useDefaultTxnId = false;
    boolean getChildReceipts = false;
    TransactionID defaultTxnId = TransactionID.getDefaultInstance();
    Optional<String> expectedSchedule = Optional.empty();
    Optional<String> expectedScheduledTxnId = Optional.empty();
    Optional<TransactionID> explicitTxnId = Optional.empty();
    Optional<ResponseCodeEnum> expectedPriorityStatus = Optional.empty();
    Optional<ResponseCodeEnum[]> expectedDuplicateStatuses = Optional.empty();
    Optional<Integer> hasChildAutoAccountCreations = Optional.empty();

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TransactionGetReceipt;
    }

    @Override
    protected HapiGetReceipt self() {
        return this;
    }

    public HapiGetReceipt(String txn) {
        this.txn = txn;
    }

    public HapiGetReceipt(TransactionID txnId) {
        explicitTxnId = Optional.of(txnId);
    }

    public HapiGetReceipt forgetOp() {
        forgetOp = true;
        return this;
    }

    public HapiGetReceipt andAnyDuplicates() {
        requestDuplicates = true;
        return this;
    }

    public HapiGetReceipt andAnyChildReceipts() {
        getChildReceipts = true;
        return this;
    }

    public HapiGetReceipt useDefaultTxnId() {
        useDefaultTxnId = true;
        return this;
    }

    public HapiGetReceipt hasChildAutoAccountCreations(int count) {
        hasChildAutoAccountCreations = Optional.of(count);
        return this;
    }

    public HapiGetReceipt hasPriorityStatus(ResponseCodeEnum status) {
        expectedPriorityStatus = Optional.of(status);
        return this;
    }

    public HapiGetReceipt hasDuplicateStatuses(ResponseCodeEnum... statuses) {
        expectedDuplicateStatuses = Optional.of(statuses);
        return this;
    }

    public HapiGetReceipt hasScheduledTxnId(String name) {
        expectedScheduledTxnId = Optional.of(HapiScheduleCreate.correspondingScheduledTxnId(name));
        return this;
    }

    public HapiGetReceipt hasSchedule(String name) {
        expectedSchedule = Optional.of(name);
        return this;
    }

    @Override
    protected void submitWith(HapiSpec spec, Transaction payment) {
        TransactionID txnId = explicitTxnId.orElseGet(
                () -> useDefaultTxnId ? defaultTxnId : spec.registry().getTxnId(txn));
        Query query =
                forgetOp ? Query.newBuilder().build() : txnReceiptQueryFor(txnId, requestDuplicates, getChildReceipts);
        response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getTransactionReceipts(query);
        childReceipts = response.getTransactionGetReceipt().getChildTransactionReceiptsList();
        if (verboseLoggingOn) {
            String message =
                    String.format("Receipt: %s", response.getTransactionGetReceipt().getReceipt());
            log.info(message);
            String message2 =
                    String.format(
                            "%s  And %d child receipts%s: %s",
                            spec.logPrefix(),
                            childReceipts.size(),
                            childReceipts.size() > 1 ? "s" : "",
                            childReceipts);

            log.info(message2);
        }
    }

    @Override
    protected void assertExpectationsGiven(HapiSpec spec) {
        var receipt = response.getTransactionGetReceipt().getReceipt();
        if (expectedPriorityStatus.isPresent()) {
            ResponseCodeEnum actualStatus = receipt.getStatus();
            assertEquals(expectedPriorityStatus.get(), actualStatus);
        }
        if (expectedDuplicateStatuses.isPresent()) {
            var duplicates = response.getTransactionGetReceipt().getDuplicateTransactionReceiptsList().stream()
                    .map(TransactionReceipt::getStatus)
                    .toArray(n -> new ResponseCodeEnum[n]);
            Assertions.assertArrayEquals(expectedDuplicateStatuses.get(), duplicates);
        }
        if (expectedScheduledTxnId.isPresent()) {
            var expected = spec.registry().getTxnId(expectedScheduledTxnId.get());
            var actual = response.getTransactionGetReceipt().getReceipt().getScheduledTransactionID();
            assertEquals(expected, actual, "Wrong scheduled transaction id!");
        }
        if (expectedSchedule.isPresent()) {
            var schedule = TxnUtils.asScheduleId(expectedSchedule.get(), spec);
            assertEquals(schedule, receipt.getScheduleID(), "Wrong/missing schedule id!");
        }
        if (hasChildAutoAccountCreations.isPresent()) {
            int count = hasChildAutoAccountCreations.get();
            for (var childReceipt : childReceipts) {
                if (childReceipt.hasAccountID()) {
                    count--;
                }
            }
            assertEquals(0, count);
        }
    }

    @Override
    protected boolean needsPayment() {
        return false;
    }

    @Override
    protected long costOnlyNodePayment(HapiSpec spec) {
        return 0L;
    }

    @Override
    protected long lookupCostWith(HapiSpec spec, Transaction payment) {
        return 0L;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("txn", txn).add("explicit Txn :", explicitTxnId);
    }
}
