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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static java.util.stream.Collectors.toMap;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc.ConsensusServiceBlockingStub;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc.FreezeServiceBlockingStub;
import com.hederahashgraph.service.proto.java.NetworkServiceGrpc;
import com.hederahashgraph.service.proto.java.NetworkServiceGrpc.NetworkServiceBlockingStub;
import com.hederahashgraph.service.proto.java.ScheduleServiceGrpc;
import com.hederahashgraph.service.proto.java.ScheduleServiceGrpc.ScheduleServiceBlockingStub;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc.SmartContractServiceBlockingStub;
import com.hederahashgraph.service.proto.java.TokenServiceGrpc;
import com.hederahashgraph.service.proto.java.TokenServiceGrpc.TokenServiceBlockingStub;
import com.hederahashgraph.service.proto.java.UtilServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiApiClients {
    static final Logger log = LogManager.getLogger(HapiApiClients.class);

    private static Map<String, FileServiceBlockingStub> fileSvcStubs = new HashMap<>();
    private static Map<String, CryptoServiceBlockingStub> cryptoSvcStubs = new HashMap<>();
    private static Map<String, TokenServiceBlockingStub> tokenSvcStubs = new HashMap<>();
    private static Map<String, ScheduleServiceBlockingStub> scheduleSvcStubs = new HashMap<>();
    private static Map<String, FreezeServiceBlockingStub> freezeSvcStubs = new HashMap<>();
    private static Map<String, NetworkServiceBlockingStub> networkSvcStubs = new HashMap<>();
    private static Map<String, ScheduleServiceBlockingStub> schedSvcStubs = new HashMap<>();
    private static Map<String, ConsensusServiceBlockingStub> consSvcStubs = new HashMap<>();
    private static Map<String, SmartContractServiceBlockingStub> scSvcStubs = new HashMap<>();
    private static Map<String, UtilServiceGrpc.UtilServiceBlockingStub> utilSvcStubs = new HashMap<>();
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
     * Id of node-{host, port} pairs to use for workflow operations
     */
    private final Map<AccountID, String> workflowStubIds;
    /**
     * Id of node-{host, port} pairs to use for workflow operations using TLS ports
     */
    private final Map<AccountID, String> workflowTlsStubIds;

    private static Map<String, ManagedChannel> channels = new HashMap<>();

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

    private void addStubs(
            final NodeConnectInfo node,
            final String uri,
            final boolean useTls,
            final Set<HederaFunctionality> workflowOperations) {
        if (!channels.containsKey(uri)) {
            if (!workflowOperations.isEmpty()) {
                addNewNettyChannelForWorkflowOperations(node, uri, useTls, workflowOperations);
            }
            ManagedChannel channel = createNettyChannel(useTls, node.getHost(), node.getPort(), node.getTlsPort());
            channels.put(uri, channel);
            log.info("URI {}", uri);

            scSvcStubs.put(uri, SmartContractServiceGrpc.newBlockingStub(channel));
            consSvcStubs.put(uri, ConsensusServiceGrpc.newBlockingStub(channel));
            fileSvcStubs.put(uri, FileServiceGrpc.newBlockingStub(channel));
            schedSvcStubs.put(uri, ScheduleServiceGrpc.newBlockingStub(channel));
            tokenSvcStubs.put(uri, TokenServiceGrpc.newBlockingStub(channel));
            scheduleSvcStubs.put(uri, ScheduleServiceGrpc.newBlockingStub(channel));
            cryptoSvcStubs.put(uri, CryptoServiceGrpc.newBlockingStub(channel));
            freezeSvcStubs.put(uri, FreezeServiceGrpc.newBlockingStub(channel));
            networkSvcStubs.put(uri, NetworkServiceGrpc.newBlockingStub(channel));
            utilSvcStubs.put(uri, UtilServiceGrpc.newBlockingStub(channel));
        }
    }

    /**
     * Since we need to submit workflow operations to a different port, we need to create a channel for that port.
     * @param node Nodes to connect to
     * @param uri String specifying host, port
     * @param useTls true if tls is used, false otherwise
     * @param workflowOperations set of HAPI operations client should submit to a different port
     */
    private void addNewNettyChannelForWorkflowOperations(
            NodeConnectInfo node, String uri, boolean useTls, Set<HederaFunctionality> workflowOperations) {
        Set<HederaFunctionality> consensusOps = Set.of(
                ConsensusCreateTopic,
                ConsensusDeleteTopic,
                ConsensusGetTopicInfo,
                ConsensusSubmitMessage,
                ConsensusUpdateTopic);
        String workflowUri = "";
        if (uri.equals(node.uri())) {
            workflowUri = node.workflowUri();
        } else if (uri.equals(node.tlsUri())) {
            workflowUri = node.workflowTlsUri();
        }

        ManagedChannel workflowChannel =
                createNettyChannel(useTls, node.getHost(), node.getWorkflowPort(), node.getWorkflowTlsPort());
        channels.put(workflowUri, workflowChannel);

        log.info("New URI for workflows {}", workflowUri);

        if (workflowOperations.stream().anyMatch(consensusOps::contains)) {
            consSvcStubs.put(workflowUri, ConsensusServiceGrpc.newBlockingStub(workflowChannel));
        }
    }

    private HapiApiClients(
            final List<NodeConnectInfo> nodes,
            final AccountID defaultNode,
            final Set<HederaFunctionality> workflowOperations) {
        this.nodes = nodes;
        stubIds = nodes.stream().collect(toMap(NodeConnectInfo::getAccount, NodeConnectInfo::uri));
        tlsStubIds = nodes.stream().collect(toMap(NodeConnectInfo::getAccount, NodeConnectInfo::tlsUri));
        workflowStubIds = nodes.stream().collect(toMap(NodeConnectInfo::getAccount, NodeConnectInfo::workflowUri));
        workflowTlsStubIds =
                nodes.stream().collect(toMap(NodeConnectInfo::getAccount, NodeConnectInfo::workflowTlsUri));
        int before = stubCount();
        nodes.forEach(node -> {
            addStubs(node, node.uri(), false, workflowOperations);
            addStubs(node, node.tlsUri(), true, workflowOperations);
        });
        int after = stubCount();
        this.defaultNode = defaultNode;
        if (after > before) {
            log.info("Constructed " + (after - before) + " new stubs building clients for " + this);
        }
    }

    private int stubCount() {
        return scSvcStubs.size()
                + consSvcStubs.size()
                + fileSvcStubs.size()
                + schedSvcStubs.size()
                + tokenSvcStubs.size()
                + cryptoSvcStubs.size()
                + freezeSvcStubs.size()
                + networkSvcStubs.size()
                + scheduleSvcStubs.size()
                + utilSvcStubs.size();
    }

    public static HapiApiClients clientsFor(HapiSpecSetup setup) {
        return new HapiApiClients(setup.nodes(), setup.defaultNode(), setup.workflowOperations());
    }

    public FileServiceBlockingStub getFileSvcStub(AccountID nodeId, boolean useTls) {
        return fileSvcStubs.get(stubId(nodeId, useTls));
    }

    public TokenServiceBlockingStub getTokenSvcStub(AccountID nodeId, boolean useTls) {
        return tokenSvcStubs.get(stubId(nodeId, useTls));
    }

    public CryptoServiceBlockingStub getCryptoSvcStub(AccountID nodeId, boolean useTls) {
        return cryptoSvcStubs.get(stubId(nodeId, useTls));
    }

    public FreezeServiceBlockingStub getFreezeSvcStub(AccountID nodeId, boolean useTls) {
        return freezeSvcStubs.get(stubId(nodeId, useTls));
    }

    public SmartContractServiceBlockingStub getScSvcStub(AccountID nodeId, boolean useTls) {
        return scSvcStubs.get(stubId(nodeId, useTls));
    }

    public ConsensusServiceBlockingStub getConsSvcStub(
            AccountID nodeId, boolean useTls, Set<HederaFunctionality> operations) {
        final String id;
        if (hasConsensusOperations(operations)) {
            id = workflowStubId(nodeId, useTls);
        } else {
            id = stubId(nodeId, useTls);
        }
        log.info("Submitting to the stub with id: " + id);
        return consSvcStubs.get(id);
    }

    public NetworkServiceBlockingStub getNetworkSvcStub(AccountID nodeId, boolean useTls) {
        return networkSvcStubs.get(stubId(nodeId, useTls));
    }

    public ScheduleServiceBlockingStub getScheduleSvcStub(AccountID nodeId, boolean useTls) {
        return schedSvcStubs.get(stubId(nodeId, useTls));
    }

    public UtilServiceGrpc.UtilServiceBlockingStub getUtilSvcStub(AccountID nodeId, boolean useTls) {
        return utilSvcStubs.get(stubId(nodeId, useTls));
    }

    private String stubId(AccountID nodeId, boolean useTls) {
        return useTls ? tlsStubIds.get(nodeId) : stubIds.get(nodeId);
    }

    private String workflowStubId(AccountID nodeId, boolean useTls) {
        return useTls ? workflowTlsStubIds.get(nodeId) : workflowStubIds.get(nodeId);
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
        if (channels.isEmpty()) {
            return;
        }
        channels.forEach((uri, channel) -> channel.shutdown());
        channels.clear();
    }

    private static void clearStubs() {
        scSvcStubs.clear();
        consSvcStubs.clear();
        fileSvcStubs.clear();
        tokenSvcStubs.clear();
        scheduleSvcStubs.clear();
        cryptoSvcStubs.clear();
        freezeSvcStubs.clear();
        networkSvcStubs.clear();
        utilSvcStubs.clear();
    }

    public static void tearDown() {
        closeChannels();
        clearStubs();
    }

    private boolean hasConsensusOperations(Set<HederaFunctionality> operations) {
        return operations.contains(ConsensusCreateTopic)
                || operations.contains(ConsensusDeleteTopic)
                || operations.contains(ConsensusGetTopicInfo)
                || operations.contains(ConsensusSubmitMessage)
                || operations.contains(ConsensusUpdateTopic);
    }
}
