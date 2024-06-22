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

package com.hedera.services.bdd.spec.queries.node;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NodeGetInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class HapiGetNodeInfo extends HapiQueryOp<HapiGetNodeInfo> {
    private static final Logger LOG = LogManager.getLogger(HapiGetNodeInfo.class);

    private static final String MISSING_NODE = "<n/a>";

    private String node = MISSING_NODE;

    private boolean immutable = false;
    private Optional<String> saveNodeInfoToReg = Optional.empty();
    private Optional<Boolean> expectedDeleted = Optional.empty();
    private Optional<String> expectedDescription = Optional.empty();

    private Optional<Supplier<String>> nodeSupplier = Optional.empty();

    public HapiGetNodeInfo(String node) {
        this.node = node;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.NodeGetInfo;
    }

    @Override
    protected HapiGetNodeInfo self() {
        return this;
    }

    public HapiGetNodeInfo hasDesc(String v) {
        expectedDescription = Optional.of(v);
        return this;
    }

    public HapiGetNodeInfo hasDeleted(boolean expected) {
        expectedDeleted = Optional.of(expected);
        return this;
    }

    public HapiGetNodeInfo saveToRegistry(String name) {
        saveNodeInfoToReg = Optional.of(name);
        return this;
    }

    public HapiGetNodeInfo(Supplier<String> supplier) {
        nodeSupplier = Optional.of(supplier);
    }

    @Override
    protected void processAnswerOnlyResponse(@NonNull final HapiSpec spec) {
        if (verboseLoggingOn) {
            LOG.info("Info for node '{}': {}", node, response.getNodeGetInfo());
        }
        if (saveNodeInfoToReg.isPresent()) {
            spec.registry().saveNodeInfo(saveNodeInfoToReg.get(), response.getNodeGetInfo());
        }
    }

    @Override
    @SuppressWarnings("java:S5960")
    protected void assertExpectationsGiven(HapiSpec spec) throws Throwable {
        var info = response.getNodeGetInfo().getNodeInfo();
        Assertions.assertEquals(TxnUtils.asNodeId(node, spec), info.getNodeId(), "Wrong node id!");
        expectedDeleted.ifPresent(f -> Assertions.assertEquals(f, info.getDeleted(), "Bad deletion status!"));
        expectedDescription.ifPresent(e -> Assertions.assertEquals(e, info.getDescription()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Query queryFor(
            @NonNull final HapiSpec spec,
            @NonNull final Transaction payment,
            @NonNull final ResponseType responseType) {
        return getNodeInfoQuery(spec, payment, responseType == ResponseType.COST_ANSWER);
    }

    private Query getNodeInfoQuery(HapiSpec spec, Transaction payment, boolean costOnly) {
        node = nodeSupplier.isPresent() ? nodeSupplier.get().get() : node;
        var id = TxnUtils.asNodeId(node, spec);
        NodeGetInfoQuery infoQuery = NodeGetInfoQuery.newBuilder()
                .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                .setNodeId(id)
                .build();
        return Query.newBuilder().setNodeGetInfo(infoQuery).build();
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("node id", node);
    }
}
