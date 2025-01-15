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

import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoCreateMeta;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiAtomicBatch extends HapiTxnOp<HapiAtomicBatch> {
    static final Logger log = LogManager.getLogger(HapiAtomicBatch.class);

    private List<HapiTxnOp<?>> operationsToBatch;

    public HapiAtomicBatch() {}

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
        // TODO: Implement proper estimate for AtomicBatch
        return 20_000_000L; // spec.fees().forActivityBasedOp(HederaFunctionality.AtomicBatch, this::usageEstimate, txn,
        // numPayerKeys);
    }

    private FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo) {
        // TODO: check for correct estimation of the batch
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
                                    b.addTransactions(op.signedTxnFor(spec));
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
        return b -> b.setAtomicBatch(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return Arrays.asList(spec -> spec.registry().getKey(effectivePayer(spec)));
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("range", operationsToBatch);
    }
}
