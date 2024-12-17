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

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asPosNodeId;
import static com.hedera.services.bdd.suites.HapiSuite.EMPTY_KEY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.StringValue;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiNodeUpdate extends HapiTxnOp<HapiNodeUpdate> {
    private static final Logger LOG = LogManager.getLogger(HapiNodeUpdate.class);

    private final String nodeName;
    private Optional<String> newAccountId = Optional.empty();

    private Optional<AccountID> newAccountAlias = Optional.empty();
    private Optional<String> newDescription = Optional.empty();
    private List<ServiceEndpoint> newGossipEndpoints = Collections.emptyList();
    private List<ServiceEndpoint> newServiceEndpoints = Collections.emptyList();

    @Nullable
    private byte[] newGossipCaCertificate;

    @Nullable
    private byte[] newGrpcCertificateHash;

    private Optional<Key> newAdminKey = Optional.empty();
    private Optional<String> newAdminKeyName = Optional.empty();

    public HapiNodeUpdate(@NonNull final String nodeName) {
        this.nodeName = nodeName;
    }

    public HapiNodeUpdate accountId(@NonNull final String accountId) {
        this.newAccountId = Optional.of(accountId);
        return this;
    }

    public HapiNodeUpdate aliasAccountId(@NonNull final String alias) {
        this.newAccountAlias = Optional.of(
                AccountID.newBuilder().setAlias(ByteString.copyFromUtf8(alias)).build());
        return this;
    }

    public HapiNodeUpdate description(@NonNull final String description) {
        this.newDescription = Optional.of(description);
        return this;
    }

    public HapiNodeUpdate gossipEndpoint(final List<ServiceEndpoint> gossipEndpoint) {
        this.newGossipEndpoints = gossipEndpoint;
        return this;
    }

    public HapiNodeUpdate serviceEndpoint(final List<ServiceEndpoint> serviceEndpoint) {
        this.newServiceEndpoints = serviceEndpoint;
        return this;
    }

    public HapiNodeUpdate gossipCaCertificate(@NonNull final byte[] gossipCaCertificate) {
        this.newGossipCaCertificate = gossipCaCertificate;
        return this;
    }

    public HapiNodeUpdate grpcCertificateHash(@NonNull final byte[] grpcCertificateHash) {
        this.newGrpcCertificateHash = grpcCertificateHash;
        return this;
    }

    public HapiNodeUpdate adminKey(final String name) {
        newAdminKeyName = Optional.of(name);
        return this;
    }

    public HapiNodeUpdate adminKey(final Key key) {
        newAdminKey = Optional.of(key);
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return ConsensusUpdateTopic;
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
        newAdminKey.ifPresent(k -> {
            if (newAdminKey.get() == EMPTY_KEY) {
                spec.registry().removeKey(nodeName);
            } else {
                spec.registry().saveKey(nodeName, k);
            }
        });
        try {
            final TransactionBody txn = CommonUtils.extractTransactionBody(txnSubmitted);
            spec.registry().saveNodeMeta(nodeName, txn.getNodeUpdate());
        } catch (final Exception impossible) {
            throw new IllegalStateException(impossible);
        }

        if (verboseLoggingOn) {
            LOG.info("Updated node {} with ID {}.", nodeName, lastReceipt.getNodeId());
        }
    }

    private void setNewAccountId(
            @NonNull String accountIdStr, @NonNull final HapiSpec spec, NodeUpdateTransactionBody.Builder builder) {
        if (accountIdStr.isEmpty()) {
            builder.setAccountId(AccountID.getDefaultInstance());
        } else builder.setAccountId(asId(accountIdStr, spec));
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(@NonNull final HapiSpec spec) throws Throwable {
        newAdminKeyName.ifPresent(
                name -> newAdminKey = Optional.of(spec.registry().getKey(name)));
        NodeUpdateTransactionBody opBody = spec.txns()
                .<NodeUpdateTransactionBody, NodeUpdateTransactionBody.Builder>body(
                        NodeUpdateTransactionBody.class, builder -> {
                            builder.setNodeId(asPosNodeId(nodeName, spec));
                            newAccountId.ifPresent(id -> setNewAccountId(id, spec, builder));
                            newAccountAlias.ifPresent(builder::setAccountId);
                            newDescription.ifPresent(s -> builder.setDescription(StringValue.of(s)));
                            newAdminKey.ifPresent(builder::setAdminKey);
                            builder.addAllGossipEndpoint(newGossipEndpoints.stream()
                                    .map(CommonPbjConverters::fromPbj)
                                    .toList());
                            builder.addAllServiceEndpoint(newServiceEndpoints.stream()
                                    .map(CommonPbjConverters::fromPbj)
                                    .toList());
                            if (newGossipCaCertificate != null) {
                                builder.setGossipCaCertificate(
                                        BytesValue.of(ByteString.copyFrom(newGossipCaCertificate)));
                            }
                            if (newGrpcCertificateHash != null) {
                                builder.setGrpcCertificateHash(
                                        BytesValue.of(ByteString.copyFrom(newGrpcCertificateHash)));
                            }
                        });
        return builder -> builder.setNodeUpdate(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        final List<Function<HapiSpec, Key>> signers = new ArrayList<>();
        signers.add(spec -> spec.registry().getKey(effectivePayer(spec)));
        signers.add(
                spec -> spec.registry().hasKey(nodeName)
                        ? spec.registry().getKey(nodeName)
                        : Key.getDefaultInstance() // same as no key
                );
        newAdminKey.ifPresent(key -> {
            if (key != EMPTY_KEY) {
                signers.add(ignored -> key);
            }
        });

        return signers;
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
    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = super.toStringHelper().add("nodeId", nodeName);
        newAccountId.ifPresent(a -> helper.add("newAccountId", a));
        newDescription.ifPresent(d -> helper.add("description", d));
        return helper;
    }

    public Key getAdminKey() {
        return newAdminKey.orElse(null);
    }
}
