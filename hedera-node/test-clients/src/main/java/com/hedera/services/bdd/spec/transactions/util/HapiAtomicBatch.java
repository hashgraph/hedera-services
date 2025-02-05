/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.util;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoCreateMeta;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiAtomicBatch extends HapiTxnOp<HapiAtomicBatch> {
    static final Logger log = LogManager.getLogger(HapiAtomicBatch.class);

    private static final String DEFAULT_NODE_ACCOUNT_ID = "0.0.0";
    private final List<HapiTxnOp<?>> operationsToBatch;
    private final Map<TransactionID, HapiTxnOp<?>> operationsMap = new HashMap<>();

    public HapiAtomicBatch(HapiTxnOp<?>... ops) {
        this.operationsToBatch = Arrays.stream(ops).toList();
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.AtomicBatch;
    }

    @Override
    protected HapiAtomicBatch self() {
        return this;
    }

    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys) throws Throwable {
        return spec.fees().forActivityBasedOp(HederaFunctionality.AtomicBatch, this::usageEstimate, txn, numPayerKeys);
    }

    private FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo) {
        final var baseMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
        final var opMeta = new CryptoCreateMeta(txn.getCryptoCreateAccount());
        final var accumulator = new UsageAccumulator();
        cryptoOpsUsage.cryptoCreateUsage(suFrom(svo), baseMeta, opMeta, accumulator);
        return AdapterUtils.feeDataFrom(accumulator);
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        final AtomicBatchTransactionBody opBody = spec.txns()
                .<AtomicBatchTransactionBody, AtomicBatchTransactionBody.Builder>body(
                        AtomicBatchTransactionBody.class, b -> {
                            for (HapiTxnOp<?> op : operationsToBatch) {
                                try {
                                    // set node account id to 0.0.0 if not set
                                    if (op.getNode().isEmpty()) {
                                        op.setNode(DEFAULT_NODE_ACCOUNT_ID);
                                    }
                                    // create a transaction for each operation
                                    final var transaction = op.signedTxnFor(spec);
                                    // save transaction id
                                    final var txnId = extractTxnId(transaction);
                                    operationsMap.put(txnId, op);
                                    // add the transaction to the batch
                                    b.addTransactions(transaction);
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
        return b -> b.setAtomicBatch(opBody);
    }

    @Override
    public void updateStateOf(HapiSpec spec) throws Throwable {
        if (actualStatus == SUCCESS) {
            for (Map.Entry<TransactionID, HapiTxnOp<?>> entry : operationsMap.entrySet()) {
                TransactionID txnId = entry.getKey();
                HapiTxnOp<?> op = entry.getValue();

                final HapiGetTxnRecord recordQuery =
                        getTxnRecord(txnId).noLogging().assertingNothing();
                final Optional<Throwable> error = recordQuery.execFor(spec);
                if (error.isPresent()) {
                    throw error.get();
                }
                op.updateStateFromRecord(recordQuery.getResponseRecord(), spec);
            }
        }
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return List.of(spec -> spec.registry().getKey(effectivePayer(spec)));
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("range", operationsToBatch);
    }
}
