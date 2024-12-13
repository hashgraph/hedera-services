/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera.utils;

import static com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssLibrary.FAKE_LEDGER_ID;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.workingDirFor;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.node.app.tss.api.FakeGroupElement;
import com.hedera.node.app.tss.handlers.TssUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.hedera.services.bdd.junit.hedera.TssKeyMaterial;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssLibrary;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Utility class for generating an address book configuration file.
 */
public class AddressBookUtils {
    private static Map<Long, Bytes> TEST_GOSSIP_X509_CERTS;

    public static final long CLASSIC_FIRST_NODE_ACCOUNT_NUM = 3;
    public static final String[] CLASSIC_NODE_NAMES =
            new String[] {"node1", "node2", "node3", "node4", "node5", "node6", "node7", "node8"};
    // TODO - replace with real encryption keys
    public static final Map<Long, Bytes> CLASSIC_ENCRYPTION_KEYS = LongStream.range(0, CLASSIC_NODE_NAMES.length)
            .boxed()
            .collect(toMap(Function.identity(), i -> Bytes.fromHex("aa".repeat(i.intValue() + 1))));
    // TODO - make this parameterizable
    public static final int CLASSIC_MAX_SHARES_PER_NODE = 3;
    // TODO - generate real shares, encode message using the real encryption keys
    public static final Function<Roster, TssKeyMaterial> CLASSIC_KEY_MATERIAL_GENERATOR = roster -> {
        final var directory = TssUtils.computeParticipantDirectory(
                roster,
                CLASSIC_MAX_SHARES_PER_NODE,
                nodeId -> new BlsPublicKey(
                        new FakeGroupElement(new BigInteger(
                                CLASSIC_ENCRYPTION_KEYS.get(nodeId).toByteArray())),
                        TssUtils.SIGNATURE_SCHEMA));
        final var rosterHash = RosterUtils.hash(roster).getBytes();
        final var tssMessageOps = IntStream.range(0, directory.getThreshold())
                .mapToObj(i -> TssMessageTransactionBody.newBuilder()
                        .shareIndex(i + 1L)
                        .sourceRosterHash(Bytes.EMPTY)
                        .targetRosterHash(rosterHash)
                        .tssMessage(Bytes.wrap(FakeTssLibrary.validMessage(i).toBytes()))
                        .build())
                .toList();
        return new TssKeyMaterial(Bytes.wrap(FAKE_LEDGER_ID.toBytes()), tssMessageOps);
    };

    /**
     * Returns the ASN.1 DER encoding of the X.509 certificate the platform generates for the given node id
     * in test environments.
     * @param nodeId the node id
     * @return the ASN.1 DER encoding of the X.509 certificate
     */
    @SuppressWarnings("unchecked")
    public static Bytes testCertFor(final long nodeId) {
        if (TEST_GOSSIP_X509_CERTS == null) {
            try {
                TEST_GOSSIP_X509_CERTS = ((Map<Long, String>) new ObjectMapper()
                                .readValue(
                                        AddressBookUtils.class
                                                .getClassLoader()
                                                .getResourceAsStream("hapi-test-gossip-certs.json"),
                                        Map.class))
                        .entrySet().stream().collect(toMap(Map.Entry::getKey, e -> Bytes.fromBase64(e.getValue())));
            } catch (IOException e) {
                throw new IllegalStateException("Could not load gossip certs", e);
            }
        }
        return TEST_GOSSIP_X509_CERTS.get(nodeId);
    }

    private AddressBookUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the contents of a <i>config.txt</i> file for the given network.
     *
     * @param networkName the name of the network
     * @param nodes the nodes in the network
     * @param nextInternalGossipPort the next gossip port to use
     * @param nextExternalGossipPort the next gossip TLS port to use
     * @return the contents of the <i>config.txt</i> file
     */
    public static String configTxtForLocal(
            @NonNull final String networkName,
            @NonNull final List<HederaNode> nodes,
            final int nextInternalGossipPort,
            final int nextExternalGossipPort) {
        final var sb = new StringBuilder();
        sb.append("swirld, ")
                .append(networkName)
                .append("\n")
                .append("\n# This next line is, hopefully, ignored.\n")
                .append("app, HederaNode.jar\n\n#The following nodes make up this network\n");
        var maxNodeId = 0L;
        for (final var node : nodes) {
            sb.append("address, ")
                    .append(node.getNodeId())
                    .append(", ")
                    // For now only use the node id as its nickname
                    .append(node.getNodeId())
                    .append(", ")
                    .append(node.getName())
                    .append(", 1, 127.0.0.1, ")
                    .append(nextInternalGossipPort + (node.getNodeId() * 2))
                    .append(", 127.0.0.1, ")
                    .append(nextExternalGossipPort + (node.getNodeId() * 2))
                    .append(", ")
                    .append("0.0.")
                    .append(node.getAccountId().accountNumOrThrow())
                    .append('\n');
            maxNodeId = Math.max(node.getNodeId(), maxNodeId);
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Returns the "classic" metadata for a node in the network, matching the names
     * used by {@link #configTxtForLocal(String, List, int, int)} to generate the
     * <i>config.txt</i> file. The working directory is inferred from the node id
     * and the network scope.
     *
     * @param nodeId the ID of the node
     * @param networkName the name of the network
     * @param scope if non-null, an additional scope to use for the working directory
     * @param nextGrpcPort the next gRPC port to use
     * @param nextGossipPort the next gossip port to use
     * @param nextGossipTlsPort the next gossip TLS port to use
     * @param nextPrometheusPort the next Prometheus port to use
     * @return the metadata for the node
     */
    public static NodeMetadata classicMetadataFor(
            final int nodeId,
            @NonNull final String networkName,
            @NonNull final String host,
            @Nullable String scope,
            final int nextGrpcPort,
            final int nextNodeOperatorPort,
            final boolean nextNodeOperatorPortEnabled,
            final int nextGossipPort,
            final int nextGossipTlsPort,
            final int nextPrometheusPort) {
        requireNonNull(host);
        requireNonNull(networkName);
        return new NodeMetadata(
                nodeId,
                CLASSIC_NODE_NAMES[nodeId],
                AccountID.newBuilder()
                        .accountNum(CLASSIC_FIRST_NODE_ACCOUNT_NUM + nodeId)
                        .build(),
                host,
                nextGrpcPort + nodeId * 2,
                nextNodeOperatorPort + nodeId * 2,
                nextNodeOperatorPortEnabled,
                nextGossipPort + nodeId * 2,
                nextGossipTlsPort + nodeId * 2,
                nextPrometheusPort + nodeId,
                workingDirFor(nodeId, scope));
    }

    /**
     * Returns the "classic" metadata for a node in the network, matching the names
     * used by {@link #configTxtForLocal(String, List, int, int)} to generate the
     * <i>config.txt</i> file.
     * @param nodeId the ID of the node
     * @param networkName the name of the network
     * @param host the host name or IP address
     * @param nextGrpcPort the next gRPC port to use
     * @param nextNodeOperatorPort the next node operator port to use
     * @param nextGossipPort the next gossip port to use
     * @param nextGossipTlsPort the next gossip TLS port to use
     * @param nextPrometheusPort the next Prometheus port to use
     * @param workingDir the working directory for the node
     * @return the metadata for the node
     */
    public static NodeMetadata classicMetadataFor(
            final int nodeId,
            @NonNull final String networkName,
            @NonNull final String host,
            final int nextGrpcPort,
            final int nextNodeOperatorPort,
            final int nextGossipPort,
            final int nextGossipTlsPort,
            final int nextPrometheusPort,
            @NonNull final Path workingDir) {
        requireNonNull(host);
        requireNonNull(networkName);
        requireNonNull(workingDir);
        return new NodeMetadata(
                nodeId,
                CLASSIC_NODE_NAMES[nodeId],
                AccountID.newBuilder()
                        .accountNum(CLASSIC_FIRST_NODE_ACCOUNT_NUM + nodeId)
                        .build(),
                host,
                nextGrpcPort + nodeId * 2,
                nextNodeOperatorPort + nodeId,
                true,
                nextGossipPort + nodeId * 2,
                nextGossipTlsPort + nodeId * 2,
                nextPrometheusPort + nodeId,
                workingDir);
    }

    /**
     * Returns a stream of numeric node ids from the given address book.
     *
     * @param addressBook the address book
     * @return the stream of node ids
     */
    public static Stream<Long> nodeIdsFrom(AddressBook addressBook) {
        return stream(addressBook.spliterator(), false).map(Address::getNodeId).map(NodeId::id);
    }

    /**
     *  Returns service end point base on the host and port. - used for hapi path for ServiceEndPoint
     *
     * @param host is an ip or domain name, do not pass in an invalid ip such as "130.0.0.1", will set it as domain name otherwise.
     * @param port the port number
     * @return the service endpoint
     */
    public static ServiceEndpoint endpointFor(@NonNull final String host, final int port) {
        final Pattern IPV4_ADDRESS_PATTERN = Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$");
        final var builder = ServiceEndpoint.newBuilder().setPort(port);
        if (IPV4_ADDRESS_PATTERN.matcher(host).matches()) {
            final var octets = host.split("[.]");
            builder.setIpAddressV4(ByteString.copyFrom((new byte[] {
                (byte) Integer.parseInt(octets[0]),
                (byte) Integer.parseInt(octets[1]),
                (byte) Integer.parseInt(octets[2]),
                (byte) Integer.parseInt(octets[3])
            })));
        } else {
            builder.setDomainName(host);
        }
        return builder.build();
    }

    /**
     * Returns Address of the node id from the given address book.
     *
     * @param addressBook the address book
     * @return the stream of node ids
     */
    public static Address nodeAddressFrom(@NonNull final AddressBook addressBook, final long nodeId) {
        requireNonNull(addressBook);
        return addressBook.getAddress(NodeId.of(nodeId));
    }

    /**
     * Returns the classic fee collector account ID for a given node ID.
     * @param nodeId the node ID
     * @return the classic fee collector account ID
     */
    public static com.hederahashgraph.api.proto.java.AccountID classicFeeCollectorIdFor(final long nodeId) {
        return com.hederahashgraph.api.proto.java.AccountID.newBuilder()
                .setAccountNum(nodeId + CLASSIC_FIRST_NODE_ACCOUNT_NUM)
                .build();
    }
}
