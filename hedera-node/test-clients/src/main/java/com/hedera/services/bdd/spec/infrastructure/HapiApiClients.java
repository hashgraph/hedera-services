/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc.ConsensusServiceBlockingStub;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc.FreezeServiceBlockingStub;
import com.hederahashgraph.service.proto.java.NetworkServiceGrpc.NetworkServiceBlockingStub;
import com.hederahashgraph.service.proto.java.ScheduleServiceGrpc.ScheduleServiceBlockingStub;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc.SmartContractServiceBlockingStub;
import com.hederahashgraph.service.proto.java.TokenServiceGrpc.TokenServiceBlockingStub;
import com.hederahashgraph.service.proto.java.UtilServiceGrpc;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiApiClients {
    static final Logger log = LogManager.getLogger(HapiApiClients.class);

    private final AccountID defaultNode;
    private final List<NodeConnectInfo> nodes;
    /**
     * Id of node-{host, port} pairs to use for non-workflow operations
     */
    private final Map<AccountID, String> stubIds;
    /**
     * Id of node-{host, port} pairs to use for non-workflow operations using TLS ports
     */
    private final Map<AccountID, String> tlsStubIds;

    /**
     * Maps from a node connection URI to a pool of {@link ChannelStubs} that have been created for that URI.
     * Many specs can run in parallel against the same nodes, so we use a concurrent map and copy-on-write
     * lists to avoid concurrent modification exceptions.
     *
     * <p>When a {@link HapiSpec} asks for stubs to a particular node's services, we return one of
     * the channels in the pool via round-robin selection.
     */
    private static final Map<String, List<ChannelStubs>> channelPools = new ConcurrentSkipListMap<>();
    /**
     * Maps from a channel stub ID (a URI string) to a sequence counter.
     * This map is used for selecting channels from the channel pool for that stub ID, supporting round-robin
     * channel stub selection, which minimizes the probability of a channel being used simultaneously.
     * This helps to work around issues in the GRPC client code.
     */
    private static final Map<String, AtomicInteger> stubSequences = new ConcurrentSkipListMap<>();

    private static final Function<String, List<ChannelStubs>> COPY_ON_WRITE_LIST_SUPPLIER =
            ignore -> new CopyOnWriteArrayList<>();

    /**
     * Given a specific node connection URI, we continue creating new {@link ChannelStubs} as new {@link HapiSpec}'s
     * initialize until we have reached thd number.
     */
    private static final int MAX_DESIRED_CHANNELS_PER_NODE = 50;

    private ManagedChannel createNettyChannel(boolean useTls, final String host, final int port, final int tlsPort) {
        try {
            ManagedChannel channel;
            String[] protocols = new String[] {"TLSv1.2", "TLSv1.3"};
            List<String> ciphers = Arrays.asList(
                    "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                    "TLS_AES_256_GCM_SHA384");
            SslContextBuilder contextBuilder = GrpcSslContexts.configure(SslContextBuilder.forClient());
            contextBuilder.protocols(protocols).ciphers(ciphers, SupportedCipherSuiteFilter.INSTANCE);

            if (useTls) {
                channel = NettyChannelBuilder.forAddress(host, tlsPort)
                        .negotiationType(NegotiationType.TLS)
                        .sslContext(contextBuilder.build())
                        .overrideAuthority("127.0.0.1")
                        .build();
            } else {
                channel = NettyChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .build();
            }
            return channel;
        } catch (Exception e) {
            log.error("Error creating Netty channel", e);
        }
        return null;
    }

    private void ensureChannelStubsInPool(final NodeConnectInfo node, final String uri, final boolean useTls) {
        final var existingPool = channelPools.computeIfAbsent(uri, COPY_ON_WRITE_LIST_SUPPLIER);
        if (existingPool.size() < MAX_DESIRED_CHANNELS_PER_NODE) {
            final var channel = createNettyChannel(useTls, node.getHost(), node.getPort(), node.getTlsPort());
            existingPool.add(ChannelStubs.from(channel));
        }
        stubSequences.putIfAbsent(uri, new AtomicInteger());
    }

    private HapiApiClients(final List<NodeConnectInfo> nodes, final AccountID defaultNode) {
        this.nodes = nodes;
        stubIds = nodes.stream().collect(Collectors.toMap(NodeConnectInfo::getAccount, NodeConnectInfo::uri));
        tlsStubIds = nodes.stream().collect(Collectors.toMap(NodeConnectInfo::getAccount, NodeConnectInfo::tlsUri));
        nodes.forEach(node -> {
            ensureChannelStubsInPool(node, node.uri(), false);
            ensureChannelStubsInPool(node, node.tlsUri(), true);
        });
        this.defaultNode = defaultNode;
    }

    public static HapiApiClients clientsFor(HapiSpecSetup setup) {
        return new HapiApiClients(setup.nodes(), setup.defaultNode());
    }

    public FileServiceBlockingStub getFileSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls)).fileSvcStubs();
    }

    public TokenServiceBlockingStub getTokenSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls)).tokenSvcStubs();
    }

    public CryptoServiceBlockingStub getCryptoSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls)).cryptoSvcStubs();
    }

    public FreezeServiceBlockingStub getFreezeSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls)).freezeSvcStubs();
    }

    public SmartContractServiceBlockingStub getScSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls)).scSvcStubs();
    }

    public ConsensusServiceBlockingStub getConsSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls)).consSvcStubs();
    }

    public NetworkServiceBlockingStub getNetworkSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls)).networkSvcStubs();
    }

    public ScheduleServiceBlockingStub getScheduleSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls)).scheduleSvcStubs();
    }

    public UtilServiceGrpc.UtilServiceBlockingStub getUtilSvcStub(AccountID nodeId, boolean useTls) {
        return nextStubsFromPool(stubId(nodeId, useTls)).utilSvcStubs();
    }

    private String stubId(AccountID nodeId, boolean useTls) {
        return useTls ? tlsStubIds.get(nodeId) : stubIds.get(nodeId);
    }

    private static ChannelStubs nextStubsFromPool(@NonNull final String stubId) {
        Objects.requireNonNull(stubId);
        final List<ChannelStubs> stubs = channelPools.get(stubId);
        if (stubs == null || stubs.isEmpty()) {
            throw new IllegalArgumentException("Should have ensured at least one channel in pool");
        }
        final AtomicInteger currentSequence = stubSequences.get(stubId);
        if (currentSequence == null) {
            throw new IllegalArgumentException("Should have assigned a sequence counter to the pool");
        }
        return stubs.get(currentSequence.getAndIncrement() % stubs.size());
    }

    @Override
    public String toString() {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
        for (int i = 0; i < nodes.size(); i++) {
            helper.add(String.format("node%d", i), nodes.get(i).toString());
        }
        return helper.add("default", HapiPropertySource.asAccountString(defaultNode))
                .toString();
    }

    /** Close all netty channels that are opened for clients */
    private static void closeChannels() {
        if (channelPools.isEmpty()) {
            return;
        }
        channelPools.forEach((uri, channelPool) -> channelPool.forEach(ChannelStubs::shutdown));
        channelPools.clear();
    }

    /**
     * Should only be called once after all tests are done.
     */
    public static void tearDown() {
        closeChannels();
    }
}
