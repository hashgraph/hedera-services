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

import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NodeCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiNodeCreate extends HapiTxnOp<HapiNodeCreate> {
    static final Logger log = LogManager.getLogger(HapiNodeCreate.class);

    private boolean advertiseCreation = false;
    private String nodeName;
    private Optional<AccountID> accountId = Optional.empty();
    private Optional<String> description = Optional.empty();
    private List<ServiceEndpoint> gossipEndpoint = Collections.emptyList();
    private List<ServiceEndpoint> serviceEndpoint = Collections.emptyList();
    private byte[] gossipCaCertificate = new byte[0];
    private byte[] grpcCertificateHash = new byte[0];
    Optional<LongConsumer> newNumObserver = Optional.empty();

    public HapiNodeCreate(String nodeName) {
        this.nodeName = nodeName;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.NodeCreate;
    }

    public HapiNodeCreate exposingNumTo(LongConsumer obs) {
        newNumObserver = Optional.of(obs);
        return this;
    }

    public HapiNodeCreate advertisingCreation() {
        advertiseCreation = true;
        return this;
    }

    public HapiNodeCreate addAccount(final AccountID accountID) {
        this.accountId = Optional.of(accountID);
        return this;
    }

    public HapiNodeCreate addDesc(final String description) {
        this.description = Optional.of(description);
        return this;
    }

    public HapiNodeCreate addGossipEndpoint(final List<ServiceEndpoint> gossipEndpoint) {
        this.gossipEndpoint = gossipEndpoint;
        return this;
    }

    public HapiNodeCreate addServiceEndpoint(final List<ServiceEndpoint> serviceEndpoint) {
        this.serviceEndpoint = serviceEndpoint;
        return this;
    }

    public HapiNodeCreate addGossipCaCertificate(final byte[] gossipCaCertificate) {
        this.gossipCaCertificate = gossipCaCertificate;
        return this;
    }

    public HapiNodeCreate addGrpcCertificateHash(final byte[] grpcCertificateHash) {
        this.grpcCertificateHash = grpcCertificateHash;
        return this;
    }

    @Override
    protected HapiNodeCreate self() {
        return this;
    }

    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys) throws Throwable {
        return spec.fees().forActivityBasedOp(HederaFunctionality.CryptoCreate, this::usageEstimate, txn, numPayerKeys);
    }

    private FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo) {
        // temp till we decide about the logic
        return FeeData.newBuilder()
                .setNodedata(FeeComponents.newBuilder().setBpr(0))
                .setNetworkdata(FeeComponents.newBuilder().setBpr(0))
                .setServicedata(FeeComponents.newBuilder().setBpr(0))
                .build();
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        final NodeCreateTransactionBody opBody = spec.txns()
                .<NodeCreateTransactionBody, NodeCreateTransactionBody.Builder>body(
                        NodeCreateTransactionBody.class, b -> {
                            if (accountId.isPresent()) b.setAccountId(accountId.get());
                            if (description.isPresent()) b.setDescription(description.get());
                            b.addAllGossipEndpoint(gossipEndpoint);
                            b.addAllServiceEndpoint(serviceEndpoint);
                            b.setGossipCaCertificate(ByteString.copyFrom(gossipCaCertificate));
                            b.setGrpcCertificateHash(ByteString.copyFrom(grpcCertificateHash));
                        });
        return b -> b.setNodeCreate(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return Arrays.asList(spec -> spec.registry().getKey(effectivePayer(spec)));
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) {
        if (actualStatus != SUCCESS) {
            return;
        }
        final var newId = lastReceipt.getNodeId();
        spec.registry().saveNodeId(nodeName, newId);

        if (verboseLoggingOn) {
            log.info("Created node {} with ID {}.", nodeName, lastReceipt.getNodeId());
        }

        if (advertiseCreation) {
            String banner = "\n\n"
                    + bannerWith(String.format("Created node '%s' with id '%d'.", nodeName, lastReceipt.getNodeId()));
            log.info(banner);
        }
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper();
        Optional.ofNullable(lastReceipt).ifPresent(receipt -> helper.add("created", receipt.getNodeId()));
        return helper;
    }
}
