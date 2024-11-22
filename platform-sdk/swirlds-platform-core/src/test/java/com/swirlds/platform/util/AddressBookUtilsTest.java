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

package com.swirlds.platform.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.Pair;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBookUtils;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AddressBookUtilsTest {

    private static final String LOCALHOST = "localhost";
    private static final int PORT_1234 = 1234;
    private static final String EXTERNAL_HOST = "www.hashgraph.com";
    private static final int PORT_5678 = 5678;
    private static final byte[] BYTES_1_2_3_4 = {1, 2, 3, 4};
    private static final byte[] BYTES_5_6_7_8 = {5, 6, 7, 8};

    @Test
    @DisplayName("Maps endpoints for internal and external")
    void endpointsForInternalAndExternal() {
        final Address address = new Address()
                .copySetHostnameInternal(LOCALHOST)
                .copySetPortInternal(PORT_1234)
                .copySetHostnameExternal(EXTERNAL_HOST)
                .copySetPortExternal(PORT_5678);

        final List<ServiceEndpoint> result = AddressBookUtils.endpointsFor(address);
        assertEquals(2, result.size());
        assertEquals(LOCALHOST, result.get(0).domainName());
        assertEquals(PORT_1234, result.get(0).port());
        assertEquals(EXTERNAL_HOST, result.get(1).domainName());
        assertEquals(PORT_5678, result.get(1).port());
    }

    @Test
    @DisplayName("Maps only non-null internal endpoint")
    void endpointsForInternalOnly() {
        final Address address = new Address().copySetHostnameInternal(LOCALHOST).copySetPortInternal(PORT_1234);

        final List<ServiceEndpoint> result = AddressBookUtils.endpointsFor(address);
        assertEquals(1, result.size());
        assertEquals(LOCALHOST, result.getFirst().domainName());
        assertEquals(PORT_1234, result.getFirst().port());
    }

    @Test
    @DisplayName("Maps only non-null external endpoint")
    void endpointsForExternalOnly() {
        final Address address =
                new Address().copySetHostnameExternal(EXTERNAL_HOST).copySetPortExternal(PORT_5678);

        final List<ServiceEndpoint> result = AddressBookUtils.endpointsFor(address);
        assertEquals(1, result.size());
        assertEquals(EXTERNAL_HOST, result.getFirst().domainName());
        assertEquals(PORT_5678, result.getFirst().port());
    }

    @Test
    @DisplayName("Maps no endpoints for for empty address")
    void endpointsForEmpty() {
        final List<ServiceEndpoint> result = AddressBookUtils.endpointsFor(new Address());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Proper signing cert is returned as expected")
    void validSigCertToBytes() throws CertificateEncodingException {
        final X509Certificate certificate = Mockito.mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(BYTES_1_2_3_4);

        final Bytes result = AddressBookUtils.extractSigCertBytes(certificate);
        assertArrayEquals(BYTES_1_2_3_4, result.toByteArray());
    }

    @Test
    @DisplayName("Invalid signing cert returns empty")
    void invalidSigCertToBytes() throws CertificateEncodingException {
        final X509Certificate certificate = Mockito.mock(X509Certificate.class);
        doThrow(new CertificateEncodingException()).when(certificate).getEncoded();

        final Bytes result = AddressBookUtils.extractSigCertBytes(certificate);
        assertEquals(Bytes.EMPTY, result);
    }

    @Test
    @DisplayName("Null signing cert returns empty")
    void nullSigCert() {
        final Bytes result = AddressBookUtils.extractSigCertBytes(null);
        assertEquals(Bytes.EMPTY, result);
    }

    @Test
    @DisplayName("Valid endpoint pair is created as expected")
    void endpointPairForValidEndpoint() {
        final ServiceEndpoint endpoint = new ServiceEndpoint(null, PORT_1234, LOCALHOST);
        final Pair<String, Integer> result = AddressBookUtils.endpointPairFor(endpoint);

        assertEquals(LOCALHOST, result.left());
        assertEquals(PORT_1234, result.right());
    }

    @Test
    @DisplayName("Endpoint pair throws NPE for null endpoint")
    void endpointPairForNullEndpoint() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> AddressBookUtils.endpointPairFor(null));
    }

    @Test
    @DisplayName("Network node information converts to a correct address book")
    void fromNetwork() {
        final NodeMetadata nodeOneMetadata =
                NodeMetadata.newBuilder().node(NODE_1).build();
        final NodeMetadata nodeTwoMetadata =
                NodeMetadata.newBuilder().node(NODE_2).build();
        final Network network = mock(Network.class);
        when(network.nodeMetadata()).thenReturn(List.of(nodeOneMetadata, nodeTwoMetadata));

        final Roster result = AddressBookUtils.fromNetwork(network);
        assertEquals(EXPECTED_ROSTER, result);
    }

    @Test
    @DisplayName("Throws an NPE for null network input")
    void fromNetworkNull() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> AddressBookUtils.fromNetwork(null));
    }

    @Test
    @DisplayName("Endpoints from metadata converts all service endpoints in correct order")
    void validEndpointsFromMetadata() {
        final NodeMetadata nodeOneMetadata =
                NodeMetadata.newBuilder().node(NODE_1).build();
        final NodeMetadata nodeTwoMetadata =
                NodeMetadata.newBuilder().node(NODE_2).build();

        final Roster result = AddressBookUtils.fromMetadata(List.of(nodeOneMetadata, nodeTwoMetadata));
        assertEquals(EXPECTED_ROSTER, result);
    }

    @Test
    @DisplayName("Endpoints from metadata only converts non-null service endpoints")
    void validAndNullEndpointsFromMetadata() {
        final NodeMetadata nodeOneMetadata =
                NodeMetadata.newBuilder().node(NODE_1).build();
        final NodeMetadata nodeTwoMetadata =
                NodeMetadata.newBuilder().node(NODE_2).build();
        final List<NodeMetadata> metadata = new ArrayList<>();
        metadata.add(nodeOneMetadata);
        metadata.add(null); // Null entry intentionally inserted; should be ignored
        metadata.add(nodeTwoMetadata);
        metadata.add(null); // Null entry intentionally inserted; should be ignored

        final Roster result = AddressBookUtils.fromMetadata(metadata);
        assertEquals(2, result.rosterEntries().size());
    }

    @Test
    @DisplayName("Empty roster returned for empty metadata")
    void emptyRosterFromMetadata() {
        final Roster result = AddressBookUtils.fromMetadata(List.of());
        assertTrue(result.rosterEntries().isEmpty());
    }

    @Test
    @DisplayName("Throws an NPE for null metadata input")
    void endpointsFromMetadataThrowsOnNull() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> AddressBookUtils.endpointsFromMetadata(null));
    }

    // The following test data is placed here due to its length
    private static final Node.Builder NODE_1 = Node.newBuilder()
            .nodeId(5)
            .weight(15)
            .gossipCaCertificate(Bytes.wrap(BYTES_1_2_3_4))
            .serviceEndpoint(List.of(
                    ServiceEndpoint.newBuilder()
                            .domainName(LOCALHOST)
                            .port(PORT_1234)
                            .build(),
                    ServiceEndpoint.newBuilder()
                            .domainName(EXTERNAL_HOST)
                            .port(PORT_5678)
                            .build()));
    private static final Node NODE_2 = Node.newBuilder()
            .nodeId(6)
            .weight(16)
            .gossipCaCertificate(Bytes.wrap(BYTES_5_6_7_8))
            .serviceEndpoint(List.of(
                    ServiceEndpoint.newBuilder()
                            .domainName(LOCALHOST + "2")
                            .port(4321)
                            .build(),
                    ServiceEndpoint.newBuilder()
                            .domainName(EXTERNAL_HOST + "2")
                            .port(8765)
                            .build()))
            .build();
    private static final Roster EXPECTED_ROSTER = Roster.newBuilder()
            .rosterEntries(
                    RosterEntry.newBuilder()
                            .nodeId(5)
                            .weight(15)
                            .gossipCaCertificate(Bytes.wrap(BYTES_1_2_3_4))
                            .gossipEndpoint(List.of(
                                    // External endpoint should be ordered first
                                    ServiceEndpoint.newBuilder()
                                            .domainName(EXTERNAL_HOST)
                                            .port(PORT_5678)
                                            .build(),
                                    ServiceEndpoint.newBuilder()
                                            .domainName(LOCALHOST)
                                            .port(PORT_1234)
                                            .build()))
                            .build(),
                    RosterEntry.newBuilder()
                            .nodeId(6)
                            .weight(16)
                            .gossipCaCertificate(Bytes.wrap(BYTES_5_6_7_8))
                            .gossipEndpoint(List.of(
                                    ServiceEndpoint.newBuilder()
                                            .domainName(EXTERNAL_HOST + "2")
                                            .port(8765)
                                            .build(),
                                    ServiceEndpoint.newBuilder()
                                            .domainName(LOCALHOST + "2")
                                            .port(4321)
                                            .build()))
                            .build())
            .build();
}
