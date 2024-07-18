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

package com.hedera.services.bdd.spec.transactions.node;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NodeDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiNodeDelete extends HapiTxnOp<HapiNodeDelete> {
    private static final Logger LOG = LogManager.getLogger(HapiNodeDelete.class);

    private static final String DEFAULT_NODE_ID = "0";

    private String nodeName = DEFAULT_NODE_ID;
    private Optional<Supplier<String>> nodeSupplier = Optional.empty();

    public HapiNodeDelete(@NonNull final String nodeName) {
        this.nodeName = nodeName;
    }

    public HapiNodeDelete(@NonNull final Supplier<String> supplier) {
        this.nodeSupplier = Optional.of(supplier);
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.NodeDelete;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(@NonNull final HapiSpec spec) throws Throwable {
        nodeName = nodeSupplier.isPresent() ? nodeSupplier.get().get() : nodeName;
        final var nodeId = TxnUtils.asNodeIdLong(nodeName, spec);
        final NodeDeleteTransactionBody opBody = spec.txns()
                .<NodeDeleteTransactionBody, NodeDeleteTransactionBody.Builder>body(
                        NodeDeleteTransactionBody.class, builder -> builder.setNodeId(nodeId));
        return builder -> builder.setNodeDelete(opBody);
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) throws Throwable {
        if (actualStatus != ResponseCodeEnum.SUCCESS) {
            return;
        }
        if (verboseLoggingOn) {
            LOG.info("Actual status was {}", actualStatus);
            LOG.info("Deleted node {} with ID {} ", nodeName, spec.registry().getNodeId(nodeName));
        }
    }

    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys) throws Throwable {
        return spec.fees().forActivityBasedOp(HederaFunctionality.NodeDelete, this::usageEstimate, txn, numPayerKeys);
    }

    private FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo) {
        final UsageAccumulator accumulator = new UsageAccumulator();
        accumulator.addVpt(Math.max(0, svo.getTotalSigCount() - 1));
        return AdapterUtils.feeDataFrom(accumulator);
    }

    @Override
    protected HapiNodeDelete self() {
        return this;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("NodeID", nodeName);
    }
}
