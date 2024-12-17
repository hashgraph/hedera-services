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

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.endpointFor;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.netOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.junit.hedera.subprocess.SubProcessNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NodeCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiNodeCreate extends HapiTxnOp<HapiNodeCreate> {
    private static final Logger LOG = LogManager.getLogger(HapiNodeCreate.class);

    private boolean advertiseCreation = false;
    private boolean useAvailableSubProcessPorts = false;
    private final String nodeName;
    private Optional<AccountID> accountId = Optional.empty();
    private Optional<String> description = Optional.empty();
    private List<ServiceEndpoint> gossipEndpoints =
            Arrays.asList(endpointFor("192.168.1.200", 123), endpointFor("192.168.1.201", 123));
    private List<ServiceEndpoint> grpcEndpoints = Arrays.asList(
            ServiceEndpoint.newBuilder().setDomainName("test.com").setPort(123).build());
    private Optional<byte[]> gossipCaCertificate = Optional.empty();
    private Optional<byte[]> grpcCertificateHash = Optional.empty();
    private Optional<String> adminKeyName = Optional.empty();
    private Optional<KeyShape> adminKeyShape = Optional.empty();

    @Nullable
    private LongConsumer nodeIdObserver;

    @Nullable
    private Key adminKey;

    public HapiNodeCreate(@NonNull final String nodeName) {
        this.nodeName = nodeName;
    }

    @Override
    protected Key lookupKey(final HapiSpec spec, final String name) {
        return name.equals(nodeName) ? adminKey : spec.registry().getKey(name);
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.NodeCreate;
    }

    public HapiNodeCreate advertisingCreation() {
        advertiseCreation = true;
        return this;
    }

    public HapiNodeCreate exposingCreatedIdTo(@NonNull final LongConsumer nodeIdObserver) {
        this.nodeIdObserver = requireNonNull(nodeIdObserver);
        return this;
    }

    public HapiNodeCreate accountId(final AccountID accountID) {
        this.accountId = Optional.of(accountID);
        return this;
    }

    public HapiNodeCreate description(final String description) {
        this.description = Optional.of(description);
        return this;
    }

    public HapiNodeCreate withAvailableSubProcessPorts() {
        useAvailableSubProcessPorts = true;
        return this;
    }

    public HapiNodeCreate gossipEndpoint(final List<ServiceEndpoint> gossipEndpoint) {
        this.gossipEndpoints = gossipEndpoint;
        return this;
    }

    public HapiNodeCreate serviceEndpoint(final List<ServiceEndpoint> serviceEndpoint) {
        this.grpcEndpoints = serviceEndpoint;
        return this;
    }

    public HapiNodeCreate gossipCaCertificate(final byte[] gossipCaCertificate) {
        this.gossipCaCertificate = Optional.of(gossipCaCertificate);
        return this;
    }

    public HapiNodeCreate grpcCertificateHash(final byte[] grpcCertificateHash) {
        this.grpcCertificateHash = Optional.of(grpcCertificateHash);
        return this;
    }

    public HapiNodeCreate adminKey(final String name) {
        adminKeyName = Optional.of(name);
        return this;
    }

    public HapiNodeCreate adminKey(final Key key) {
        adminKey = key;
        return this;
    }

    private void genKeysFor(final HapiSpec spec) {
        adminKey = adminKey == null ? netOf(spec, adminKeyName, adminKeyShape) : adminKey;
    }

    @Override
    protected HapiNodeCreate self() {
        return this;
    }

    @Override
    protected long feeFor(@NonNull final HapiSpec spec, @NonNull final Transaction txn, final int numPayerKeys)
            throws Throwable {
        return spec.fees().forActivityBasedOp(HederaFunctionality.NodeCreate, this::usageEstimate, txn, numPayerKeys);
    }

    private FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo) {
        final UsageAccumulator accumulator = new UsageAccumulator();
        accumulator.addVpt(Math.max(0, svo.getTotalSigCount() - 1));
        return AdapterUtils.feeDataFrom(accumulator);
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(@NonNull final HapiSpec spec) throws Throwable {
        genKeysFor(spec);
        if (useAvailableSubProcessPorts) {
            if (!(spec.targetNetworkOrThrow() instanceof SubProcessNetwork subProcessNetwork)) {
                throw new IllegalStateException("Target is not a SubProcessNetwork");
            }
            gossipEndpoints = subProcessNetwork.gossipEndpointsForNextNodeId();
            grpcEndpoints = List.of(subProcessNetwork.grpcEndpointForNextNodeId());
        }
        final NodeCreateTransactionBody opBody = spec.txns()
                .<NodeCreateTransactionBody, NodeCreateTransactionBody.Builder>body(
                        NodeCreateTransactionBody.class, builder -> {
                            accountId.ifPresent(builder::setAccountId);
                            description.ifPresent(builder::setDescription);
                            builder.setAdminKey(adminKey);
                            builder.clearGossipEndpoint().addAllGossipEndpoint(gossipEndpoints);
                            builder.clearServiceEndpoint().addAllServiceEndpoint(grpcEndpoints);
                            gossipCaCertificate.ifPresent(s -> builder.setGossipCaCertificate(ByteString.copyFrom(s)));
                            grpcCertificateHash.ifPresent(s -> builder.setGrpcCertificateHash(ByteString.copyFrom(s)));
                        });
        return b -> b.setNodeCreate(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return List.of(spec -> spec.registry().getKey(effectivePayer(spec)), ignore -> adminKey);
    }

    @Override
    protected void updateStateOf(@NonNull final HapiSpec spec) {
        if (actualStatus != SUCCESS) {
            return;
        }
        final var newId = lastReceipt.getNodeId();
        spec.registry()
                .saveNodeId(
                        nodeName,
                        fromPbj(EntityNumber.newBuilder().number(newId).build()));

        if (verboseLoggingOn) {
            LOG.info("Created node {} with ID {}.", nodeName, lastReceipt.getNodeId());
        }

        if (advertiseCreation) {
            final String banner = "\n\n"
                    + bannerWith(String.format(
                            "Created node '%s' with id '%d'.", description.orElse(nodeName), lastReceipt.getNodeId()));
            LOG.info(banner);
        }
        if (nodeIdObserver != null) {
            nodeIdObserver.accept(newId);
        }
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = super.toStringHelper();
        Optional.ofNullable(lastReceipt).ifPresent(receipt -> helper.add("created", receipt.getNodeId()));
        return helper;
    }

    public Key getAdminKey() {
        return adminKey;
    }
}
