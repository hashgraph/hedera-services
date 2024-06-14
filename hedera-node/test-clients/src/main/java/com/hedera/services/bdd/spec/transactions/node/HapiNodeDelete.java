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
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.NodeDeleteTransactionBody;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiNodeDelete extends HapiTxnOp<HapiNodeDelete> {
    static final Logger log = LogManager.getLogger(HapiNodeDelete.class);

    private static final String DEFAULT_NODE_ID = "0";

    private String node = DEFAULT_NODE_ID;
    private Optional<Supplier<String>> nodeSupplier = Optional.empty();

    public HapiNodeDelete(String node) {
        this.node = node;
    }

    public HapiNodeDelete(Supplier<String> supplier) {
        this.nodeSupplier = Optional.of(supplier);
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.FileDelete;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        node = nodeSupplier.isPresent() ? nodeSupplier.get().get() : node;
        var nodeId = TxnUtils.asNodeId(node, spec);
        NodeDeleteTransactionBody opBody = spec.txns()
                .<NodeDeleteTransactionBody, NodeDeleteTransactionBody.Builder>body(
                        NodeDeleteTransactionBody.class, builder -> builder.setNodeId(nodeId));
        return builder -> builder.setNodeDelete(opBody);
    }

    @Override
    protected void updateStateOf(HapiSpec spec) throws Throwable {
        if (verboseLoggingOn) {
            log.info("Actual status was {}", actualStatus);
            log.info("Deleted node {} with ID {} ", node, spec.registry().getNodeId(node));
        }
        if (actualStatus != ResponseCodeEnum.SUCCESS) {
            return;
        }
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        //temp till we decide about the logic
        return FeeData.newBuilder().setNodedata(FeeComponents.newBuilder().setBpr(0)).setNetworkdata(FeeComponents.newBuilder().setBpr(0)).setServicedata(FeeComponents.newBuilder().setBpr(0)).build().getSerializedSize();
    }

    @Override
    protected HapiNodeDelete self() {
        return this;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("NodeID", node);
    }
}
