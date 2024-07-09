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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.BytesValue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiNodeUpdate extends HapiTxnOp<HapiNodeUpdate> {
    private static final Logger LOG = LogManager.getLogger(HapiNodeUpdate.class);

    private final String nodeName;
    private Optional<AccountID> accountId = Optional.empty();
    private Optional<String> description = Optional.empty();
    private List<ServiceEndpoint> gossipEndpoints = Collections.emptyList();
    private List<ServiceEndpoint> serviceEndpoints = Collections.emptyList();
    private Optional<byte[]> gossipCaCertificate = Optional.empty();
    private Optional<byte[]> grpcCertificateHash = Optional.empty();

    private Optional<Consumer<Long>> preUpdateCb = Optional.empty();
    private Optional<Consumer<ResponseCodeEnum>> postUpdateCb = Optional.empty();

    public HapiNodeUpdate(@NonNull final String nodeName) {
        this.nodeName = nodeName;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.NodeUpdate;
    }

    public HapiNodeUpdate accountId(@NonNull final AccountID accountId) {
        this.accountId = Optional.of(accountId);
        return this;
    }

    public HapiNodeUpdate description(@NonNull final String description) {
        this.description = Optional.of(description);
        return this;
    }

    public HapiNodeUpdate gossipEndpoint(final List<ServiceEndpoint> gossipEndpoint) {
        this.gossipEndpoints = gossipEndpoint;
        return this;
    }

    public HapiNodeUpdate serviceEndpoint(final List<ServiceEndpoint> serviceEndpoint) {
        this.serviceEndpoints = serviceEndpoint;
        return this;
    }

    public HapiNodeUpdate gossipCaCertificate(@NonNull final byte[] gossipCaCertificate) {
        this.gossipCaCertificate = Optional.of(gossipCaCertificate);
        return this;
    }

    public HapiNodeUpdate grpcCertificateHash(@NonNull final byte[] grpcCertificateHash) {
        this.grpcCertificateHash = Optional.of(grpcCertificateHash);
        return this;
    }

    private static Setting asSetting(final String name, final String value) {
        return Setting.newBuilder().setName(name).setValue(value).build();
    }

    public HapiNodeUpdate alertingPre(@NonNull final Consumer<Long> preCb) {
        preUpdateCb = Optional.of(preCb);
        return this;
    }

    public HapiNodeUpdate alertingPost(@NonNull final Consumer<ResponseCodeEnum> postCb) {
        postUpdateCb = Optional.of(postCb);
        return this;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(@NonNull final HapiSpec spec) throws Throwable {
        var nodeId = TxnUtils.asNodeIdLong(nodeName, spec);
        NodeUpdateTransactionBody opBody = spec.txns()
                .<NodeUpdateTransactionBody, NodeUpdateTransactionBody.Builder>body(
                        NodeUpdateTransactionBody.class, builder -> {
                            builder.setNodeId(nodeId);
                            accountId.ifPresent(builder::setAccountId);
                            description.ifPresent(s -> builder.setDescription(StringValue.of(s)));
                            builder.addAllGossipEndpoint(gossipEndpoints);
                            builder.addAllServiceEndpoint(serviceEndpoints);

                            gossipCaCertificate.ifPresent(s -> {
                                try {
                                    builder.setGossipCaCertificate(BytesValue.parseFrom(s));
                                } catch (InvalidProtocolBufferException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            grpcCertificateHash.ifPresent(s -> {
                                try {
                                    builder.setGrpcCertificateHash(BytesValue.parseFrom(s));
                                } catch (InvalidProtocolBufferException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        });
        preUpdateCb.ifPresent(cb -> cb.accept(nodeId));
        return builder -> builder.setNodeUpdate(opBody);
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees().forActivityBasedOp(HederaFunctionality.NodeUpdate, this::usageEstimate, txn, numPayerKeys);
    }

    private FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo) {
        final UsageAccumulator accumulator = new UsageAccumulator();
        accumulator.addVpt(Math.max(0, svo.getTotalSigCount() - 1));
        return AdapterUtils.feeDataFrom(accumulator);
    }

    @Override
    protected HapiNodeUpdate self() {
        return this;
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) {
        if (actualStatus != SUCCESS) {
            return;
        }

        if (verboseLoggingOn) {
            LOG.info("Updated node {} with ID {}.", nodeName, lastReceipt.getNodeId());
        }
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = super.toStringHelper().add("nodeId", nodeName);
        accountId.ifPresent(a -> helper.add("accountId", a));
        description.ifPresent(d -> helper.add("description", d));
        return helper;
    }
}
