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

import static com.swirlds.platform.util.TestRosterValues.EXPECTED_ROSTER;
import static com.swirlds.platform.util.TestRosterValues.NODE_1;
import static com.swirlds.platform.util.TestRosterValues.NODE_2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.roster.RosterUtils;
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
    @DisplayName("Network node information converts to a correct address book")
    void fromNetwork() {
        final NodeMetadata nodeOneMetadata =
                NodeMetadata.newBuilder().node(NODE_1).build();
        final NodeMetadata nodeTwoMetadata =
                NodeMetadata.newBuilder().node(NODE_2).build();
        final Network network = mock(Network.class);
        when(network.nodeMetadata()).thenReturn(List.of(nodeOneMetadata, nodeTwoMetadata));

        final Roster result = RosterUtils.fromNetwork(network);
        assertEquals(EXPECTED_ROSTER, result);
    }

    @Test
    @DisplayName("Throws an NPE for null network input")
    void fromNetworkNull() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> RosterUtils.fromNetwork(null));
    }

    @Test
    @DisplayName("Endpoints from metadata converts all service endpoints in correct order")
    void validEndpointsFromMetadata() {
        final NodeMetadata nodeOneMetadata =
                NodeMetadata.newBuilder().node(NODE_1).build();
        final NodeMetadata nodeTwoMetadata =
                NodeMetadata.newBuilder().node(NODE_2).build();

        final Roster result = RosterUtils.fromMetadata(List.of(nodeOneMetadata, nodeTwoMetadata));
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

        final Roster result = RosterUtils.fromMetadata(metadata);
        assertEquals(2, result.rosterEntries().size());
    }

    @Test
    @DisplayName("Empty roster returned for empty metadata")
    void emptyRosterFromMetadata() {
        final Roster result = RosterUtils.fromMetadata(List.of());
        assertTrue(result.rosterEntries().isEmpty());
    }
}
