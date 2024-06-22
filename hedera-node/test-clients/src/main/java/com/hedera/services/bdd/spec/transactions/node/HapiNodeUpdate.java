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
import com.google.protobuf.BytesValue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiNodeUpdate extends HapiTxnOp<HapiNodeUpdate> {
    static final Logger LOG = LogManager.getLogger(HapiNodeUpdate.class);

    private final String node;
    private Optional<AccountID> accountId = Optional.empty();
    private Optional<String> description = Optional.empty();
    private Optional<List<ServiceEndpoint>> gossipEndpoint = Optional.empty();
    private Optional<List<ServiceEndpoint>> serviceEndpoint = Optional.empty();
    private Optional<byte[]> gossipCaCertificate = Optional.empty();
    private Optional<byte[]> grpcCertificateHash = Optional.empty();

    Optional<Consumer<Long>> preUpdateCb = Optional.empty();
    Optional<Consumer<ResponseCodeEnum>> postUpdateCb = Optional.empty();

    public HapiNodeUpdate(String node) {
        this.node = node;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.NodeUpdate;
    }

    public HapiNodeUpdate accountId(AccountID accountId) {
        this.accountId = Optional.of(accountId);
        return this;
    }

    public HapiNodeUpdate description(String description) {
        this.description = Optional.of(description);
        return this;
    }

    public HapiNodeUpdate gossipEndpoint(List<ServiceEndpoint> gossipEndpoint) {
        this.gossipEndpoint = Optional.of(gossipEndpoint);
        return this;
    }

    public HapiNodeUpdate serviceEndpoint(List<ServiceEndpoint> serviceEndpoint) {
        this.serviceEndpoint = Optional.of(serviceEndpoint);
        return this;
    }

    public HapiNodeUpdate gossipCaCertificate(byte[] gossipCaCertificate) {
        this.gossipCaCertificate = Optional.of(gossipCaCertificate);
        return this;
    }

    public HapiNodeUpdate grpcCertificateHash(byte[] grpcCertificateHash) {
        this.grpcCertificateHash = Optional.of(grpcCertificateHash);
        return this;
    }

    private static Setting asSetting(String name, String value) {
        return Setting.newBuilder().setName(name).setValue(value).build();
    }

    public HapiNodeUpdate alertingPre(Consumer<Long> preCb) {
        preUpdateCb = Optional.of(preCb);
        return this;
    }

    public HapiNodeUpdate alertingPost(Consumer<ResponseCodeEnum> postCb) {
        postUpdateCb = Optional.of(postCb);
        return this;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        var nid = TxnUtils.asNodeIdLong(node, spec);
        NodeUpdateTransactionBody opBody = spec.txns()
                .<NodeUpdateTransactionBody, NodeUpdateTransactionBody.Builder>body(
                        NodeUpdateTransactionBody.class, builder -> {
                            builder.setNodeId(nid);
                            accountId.ifPresent(builder::setAccountId);
                            description.ifPresent(s -> builder.setDescription(
                                    StringValue.newBuilder().setValue(s).build()));
                            if (gossipEndpoint.isPresent()) {
                                builder.addAllGossipEndpoint(gossipEndpoint.get());
                            }
                            if (serviceEndpoint.isPresent()) {
                                builder.addAllServiceEndpoint(serviceEndpoint.get());
                            }
                            if (gossipCaCertificate.isPresent()) {
                                try {
                                    builder.setGossipCaCertificate(BytesValue.parseFrom(gossipCaCertificate.get()));
                                } catch (InvalidProtocolBufferException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            if (grpcCertificateHash.isPresent()) {
                                try {
                                    builder.setGrpcCertificateHash(BytesValue.parseFrom(grpcCertificateHash.get()));
                                } catch (InvalidProtocolBufferException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
        preUpdateCb.ifPresent(cb -> cb.accept(nid));
        return builder -> builder.setNodeUpdate(opBody);
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        // temp till we decide about the logic
        return FeeData.newBuilder()
                .setNodedata(FeeComponents.newBuilder().setBpr(0))
                .setNetworkdata(FeeComponents.newBuilder().setBpr(0))
                .setServicedata(FeeComponents.newBuilder().setBpr(0))
                .build()
                .getSerializedSize();
    }

    @Override
    protected HapiNodeUpdate self() {
        return this;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper().add("nodeId", node);
        accountId.ifPresent(a -> helper.add("accountId", a));
        description.ifPresent(d -> helper.add("description", d));
        return helper;
    }
}
